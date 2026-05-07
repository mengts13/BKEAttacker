// cmd/kcp-client/main.go
package main

/*
#include <jni.h>
*/
import "C"
import (
	"bytes"
	"compress/gzip"
	"encoding/base64"
	"encoding/binary"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log"
	"math/rand"
	"regexp"
	"strings"
	"sync"
	"time"
)

var globalStreamClient *DuplexClient
var globalMessageClient *DuplexClient
var globalWaitLockQueue *WaitLockQueue

var supportedOpcodes = []byte{
	0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F,
	0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17,
	0x20, 0x21, 0x22, 0x23, 0x24,
	0x30,
	0x40, 0x41,
	0x50,
	0xAA,
}

const MaxOpCodeNumber = uint8(32)

const ResponseDelayTime = 5 * time.Second
const PairTokenSeparator = "+++"

// getCurrentTimestamp returns the current timestamp in nanoseconds (uint64)
func getCurrentTimestamp() uint64 {
	if useNTP {
		return Now()
	}
	return uint64(time.Now().UnixNano())
}

func getCurrentTimestampInt64() int64 {
	if useNTP {
		return NowInt64()
	}
	return time.Now().UnixNano()
}

func decompress(compressed []byte) ([]byte, error) {
	buf := bytes.NewReader(compressed)
	gz, err := gzip.NewReader(buf)
	if err != nil {
		return nil, err
	}
	defer gz.Close()

	return io.ReadAll(gz) // Read all decompressed data
}

func compress(data []byte) ([]byte, error) {
	var buf bytes.Buffer
	gz := gzip.NewWriter(&buf)
	_, err := gz.Write(data)
	if err != nil {
		return nil, err
	}
	gz.Close()
	return buf.Bytes(), nil
}

// DuplexClient represents a full-duplex client
type DuplexClient struct {
	conn      *KCPConnection
	tempToken string // Local temp token (used for refreshing, etc.)
	longToken string

	sendChan chan []byte

	onReceive func([]byte)

	stopChan chan struct{}
	wg       sync.WaitGroup

	clientName string
	tokenMutex sync.Mutex

	opcodeQueues         map[byte]*ResponseQueue
	TargetResponseQueues map[byte]chan []byte
	isStartFreshToken    bool
	isStreamMode         bool
	supportedOpcodeSet   [256]bool
}

// NewDuplexClient creates a full-duplex client
func NewDuplexClient(serverAddr string, clientName string, isStream bool) (*DuplexClient, error) {
	var conn *KCPConnection
	var err error
	if isStream {
		conn, err = NewKCPConnection(serverAddr)
	} else {
		conn, err = NewKCPMessageConnection(serverAddr)
	}
	if err != nil {
		return nil, fmt.Errorf("failed to create KCP connection: %v", err)
	}
	opcodeQueues := make(map[byte]*ResponseQueue)
	TargetResponseQueues := make(map[byte]chan []byte)

	client := &DuplexClient{
		conn:                 conn,
		sendChan:             make(chan []byte, 100),
		stopChan:             make(chan struct{}),
		clientName:           clientName,
		opcodeQueues:         opcodeQueues,
		TargetResponseQueues: TargetResponseQueues,
		isStartFreshToken:    false,
		isStreamMode:         isStream,
	}

	for _, op := range supportedOpcodes {
		if isStream {
			opcodeQueues[op] = NewResponseQueue(int(MaxOpCodeNumber), 100*ResponseDelayTime)
		} else {
			opcodeQueues[op] = NewResponseQueue(int(MaxOpCodeNumber), ResponseDelayTime)
		}
		TargetResponseQueues[op] = make(chan []byte, 20)
		client.supportedOpcodeSet[op] = true
	}

	// Start goroutines
	client.wg.Add(1)
	// go client.sendLoop()
	go client.receiveLoop()

	return client, nil
}

// register registers in full-duplex mode (new protocol: dual tokens + role)
func (c *DuplexClient) register(authToken, pairToken string, role byte) error {
	LogInfo("authToken: " + authToken)
	LogInfo("pairToken: " + pairToken)
	opcode := byte(0x01)
	msg := []byte{0xAA, 0x01}
	msg_id := c.opcodeQueues[opcode].Pop()
	msg = append(msg, msg_id)

	authBytes := []byte(authToken)
	pairBytes := []byte(pairToken)

	// Write auth token length + content
	authLen := make([]byte, 2)
	binary.BigEndian.PutUint16(authLen, uint16(len(authBytes)))
	msg = append(msg, authLen...)
	msg = append(msg, authBytes...)

	// Write pair token length + content
	pairLen := make([]byte, 2)
	binary.BigEndian.PutUint16(pairLen, uint16(len(pairBytes)))
	msg = append(msg, pairLen...)
	msg = append(msg, pairBytes...)

	// Write role byte
	msg = append(msg, role, MaxOpCodeNumber)
	err := c.Send(msg)
	if err != nil {
		return fmt.Errorf("failed to write registration request: %v", err)
	}

	response := c.opcodeQueues[opcode].Read(msg_id)
	if response[0] == byte(0x09) {
		return fmt.Errorf("pairing timed out")
	}
	// Read response
	if len(response) < 4 || response[0] != 0xAA || response[1] != 0x01 {
		return fmt.Errorf("failed to read registration response")
	}
	if response[3] != 0x01 {
		return fmt.Errorf("server rejected registration (error code 0x%02x)", response[3])
	}

	return nil
}

// receiveLoop receive goroutine
func (c *DuplexClient) receiveLoop() {
	defer c.wg.Done()
	buf := make([]byte, 4096)
	for {
		select {
		case <-c.stopChan:
			return
		default:
			n, err := c.conn.Read(buf)
			//	LogInfo(fmt.Sprintf("%s Receive Length %d Bytes,channel: %v", c.clientName, n, buf[2]))
			if err != nil {
				log.Printf("%s receive error: %v", c.clientName, err)
				return
			}
			if n > 0 && buf[0] == 0xAA {
				if n == 1 { // heartbeat
					continue
				}
				dataCopy := make([]byte, n)
				copy(dataCopy, buf[:n])
				c.handleReceivedData(dataCopy)
			}
		}
	}
}

// handleReceivedData handles received data in a unified way
func (c *DuplexClient) handleReceivedData(data []byte) {
	if len(data) < 2 {
		return
	}
	msgType := data[1]
	msg_id := data[2]
	if msg_id < MaxOpCodeNumber {
		c.opcodeQueues[msgType].SubmitResponse(msg_id, data)
	} else {
		LogInfo(fmt.Sprintf("Insert Rpc Data : 0x %02X", data))
		if c.isStreamMode {
			LogInfo(fmt.Sprintf("Insert Rpc Data : 0x %02X - insertrpc", data))
			InsertRpc(data)
		} else {
			if ch, ok := c.TargetResponseQueues[msgType]; ok {
				ch <- data // ⚠️ may block, but this is acceptable
			}
		}
	}
}

func (c *DuplexClient) ReadResponseData(msgType byte) []byte {
	if ch, ok := c.TargetResponseQueues[msgType]; ok {
		data := <-ch // Block until data is available
		return data
	}
	// If opcode is not registered, return nil (or panic, depending on your strategy)
	return nil
}

// tokenRefreshLoop token auto-refresh goroutine
func (c *DuplexClient) tokenRefreshLoop() {
	defer c.wg.Done()
	ticker := time.NewTicker(30 * time.Second)
	defer ticker.Stop()
	for {
		select {
		case <-ticker.C:
			c.tokenMutex.Lock()
			tempToken := c.tempToken
			longToken := c.longToken
			c.tokenMutex.Unlock()
			if tempToken != "" && longToken != "" {
				exp := uint64(getExpTime(tempToken))
				now := getCurrentTimestamp() / 1e9
				if exp-now < 300 {
					log.Printf("%s Token is about to expire, auto-refreshing...", c.clientName)
					if err := c.RefreshToken(); err != nil {
						log.Printf("%s Token auto-refresh failed: %v, streamMode: %v", c.clientName, err, c.isStreamMode)
					}
				}
			}
		case <-c.stopChan:
			return
		}
	}
}

// Send sends data (public interface)
//
//	func (c *DuplexClient) Send(data []byte) error {
//		_, err := c.conn.Write(data)
//		return err
//	}
func (c *DuplexClient) Send(data []byte) error {
	frame := make([]byte, 3+len(data))
	frame[0] = 0xAA
	binary.BigEndian.PutUint16(frame[1:3], uint16(len(data)))
	copy(frame[3:], data)
	_, err := c.conn.Write(frame)
	return err
}

// SetReceiveHandler sets the receive handler (public interface)
func (c *DuplexClient) SetReceiveHandler(handler func([]byte)) { c.onReceive = handler }

// handleTokenRefresh handles the token refresh response
func (c *DuplexClient) handleTokenRefresh(data []byte) bool {
	if len(data) < 3 {
		LogInfo(fmt.Sprintf("%s Token refresh response format error", c.clientName))
		return false
	}
	if data[3] != 0x01 {
		LogInfo(fmt.Sprintf("%s Server rejected token refresh request", c.clientName))
		return false
	}
	if len(data) < 5 {
		LogInfo(fmt.Sprintf("%s Token refresh response data incomplete", c.clientName))
		return false
	}
	jwtLen := binary.BigEndian.Uint16(data[4:6])
	if len(data) < 6+int(jwtLen) {
		LogInfo(fmt.Sprintf("%s Token refresh JWT data incomplete", c.clientName))
		return false
	}
	newTempTokenCompress := data[6 : 6+jwtLen]
	newTempTokenBytes, err := decompress(newTempTokenCompress)
	if err != nil {
		LogInfo(fmt.Sprintf("%s Token refresh decompress failed: %v", c.clientName, err))
		return false
	}
	newTempToken := string(newTempTokenBytes)

	c.conn.SetTempToken(newTempToken)
	// Save the refreshed token to a file
	c.tokenMutex.Lock()
	c.tempToken = newTempToken
	c.tokenMutex.Unlock()

	log.Printf("%s Token auto-refreshed successfully", c.clientName)
	return true

}

func (c *DuplexClient) RefreshToken() error {
	// use TempToken to refresh
	c.tokenMutex.Lock()
	tempToken := c.tempToken
	c.tokenMutex.Unlock()

	if tempToken != "" {
		if err := c.doRefreshToken(tempToken, false); err == nil {
			return nil // Success
		} else {
			LogInfo("TempToken refresh failed, trying to use LongToken, streamMode:" + fmt.Sprint(c.isStreamMode) + " err: " + err.Error())
		}

	}

	//  LongToken
	c.tokenMutex.Lock()
	longToken := c.longToken
	c.tokenMutex.Unlock()

	if longToken == "" {
		return fmt.Errorf("no long token available")
	}

	return c.doRefreshToken(longToken, true)
}

func (c *DuplexClient) doRefreshToken(token string, isLongToken bool) error {
	tokenBytes := []byte(token)
	tokenLen := len(tokenBytes)

	if tokenLen > 0xFFFF {
		return fmt.Errorf("token is too long, exceeds 65535 bytes")
	}

	msg := []byte{0xAA, 0x04}
	msg_id := c.opcodeQueues[0x04].Pop()
	msg = append(msg, msg_id)

	// Add 2-byte length (big-endian)
	lenBuf := make([]byte, 2)
	binary.BigEndian.PutUint16(lenBuf, uint16(tokenLen))
	msg = append(msg, lenBuf...)
	msg = append(msg, tokenBytes...)

	if err := c.Send(msg); err != nil {
		return fmt.Errorf("failed to send token refresh request: %v", err)
	}

	response := c.opcodeQueues[0x04].Read(msg_id)
	if len(response) == 0 {
		return fmt.Errorf("no refresh response received")
	}
	if response[0] == 0x09 {
		return fmt.Errorf("refresh token timeout")
	}


	if !c.handleTokenRefresh(response) {
		tokenType := "LongToken"
		if !isLongToken {
			tokenType = "TempToken"
		}
		return fmt.Errorf("%s refresh failed", tokenType)
	}

	return nil
}

// Close client
func (c *DuplexClient) Close() {
	close(c.stopChan)
	c.wg.Wait()
	c.conn.Close()
	log.Printf("%s closed", c.clientName)
}

func validatePin(pin string) error {
	matched, err := regexp.MatchString(`^[0-9]{6}$`, pin)
	if err != nil {
		return err
	}
	if !matched {
		return errors.New("PIN must be a 6-digit number")
	}
	return nil
}

func parsePinShareResponse(resp []byte) (channelID byte, err error, stat byte) {
	if len(resp) < 4 {
		return 0, errors.New("PIN report response is too short"), 0xff
	}
	if resp[0] != 0xAA || resp[1] != 0x05 {
		return 0, errors.New("PIN report response format error"), 0xff
	}
	channelID = resp[2] // The 3rd byte is the channel ID
	status := resp[3]   // The 4th byte is the status code

	switch status {
	case 0x01:
		return channelID, nil, 0x01 // Success
	case 0x03:
		return channelID, errors.New("pin conflict"), 0x03
	default:
		return channelID, errors.New("PIN report failed"), 0xff
	}
}

// SendPinShare reports the PIN -> temp mapping
func (c *DuplexClient) SendPinShare(pin, temp string) (error, byte) {
	if err := validatePin(pin); err != nil {
		return err, 0xfe
	}

	b := []byte{0xAA, 0x05}
	msg_id := c.opcodeQueues[0x05].Pop()

	b = append(b, msg_id)

	b = append(b, []byte(pin)...)
	jwt := []byte(temp)
	lenBuf := make([]byte, 2)
	binary.BigEndian.PutUint16(lenBuf, uint16(len(jwt)))
	b = append(b, lenBuf...)
	b = append(b, jwt...)

	err := c.Send(b)
	if err != nil {
		return fmt.Errorf("failed to write PIN share: %w", err), 0xff
	}

	resp := c.opcodeQueues[0x05].Read(msg_id)

	if resp[0] == byte(0x09) {
		return fmt.Errorf("PIN report timeout"), 0x09
	}

	_, err, s := parsePinShareResponse(resp)

	return err, s
}

func parseFetchPinTokenResponse(data []byte) (channelID byte, token string, err error) {
	if len(data) < 6 {
		return 0, "", errors.New("response data is too short")
	}

	// Check magic number
	if data[0] != 0xAA || data[1] != 0x06 {
		return 0, "", errors.New("PIN query response format error")
	}

	channelID = data[2] // The 3rd byte is the channel ID

	// The 4th byte is the status code
	if data[3] != 0x01 {
		return channelID, "", errors.New("PIN pairing failed or does not exist")
	}

	// Bytes 5-6 are the token length (big-endian)
	tokenLen := binary.BigEndian.Uint16(data[4:6])
	expectedTotalLen := 6 + int(tokenLen)

	if len(data) < expectedTotalLen {
		return channelID, "", errors.New("response data length is insufficient to read the full token")
	}

	token = string(data[6:expectedTotalLen])
	tokenUnComp, err := decompress([]byte(token))
	return channelID, string(tokenUnComp), nil
}

// FetchPinToken gets the other party's temp token via PIN (requires providing one's own temp token for authentication)
func (c *DuplexClient) FetchPinToken(pin, callerTemp string) (string, error) {
	if err := validatePin(pin); err != nil {
		return "", err
	}

	// Construct request: 0xAA 0x06 + msg_id + pin(6) + len(callerTemp) + callerTemp
	req := []byte{0xAA, 0x06}
	req_id := c.opcodeQueues[0x06].Pop()
	req = append(req, req_id)
	req = append(req, []byte(pin)...)

	tempBytes := []byte(callerTemp)
	lenBuf := make([]byte, 2)
	binary.BigEndian.PutUint16(lenBuf, uint16(len(tempBytes)))
	req = append(req, lenBuf...)
	req = append(req, tempBytes...)

	err := c.Send(req)
	if err != nil {
		return "", fmt.Errorf("failed to send PIN token request: %w", err)
	}

	response := c.opcodeQueues[0x06].Read(req_id)
	if response[0] == byte(0x09) {
		return "", fmt.Errorf("PIN query timeout")
	}

	_, pairToken, err := parseFetchPinTokenResponse(response)

	if err != nil {
		return "", err
	}
	LogInfo("client Fetch Split")

	return pairToken, nil
}

func parseLoginResponse(data []byte) (bool, string, string, error) {
	if len(data) < 3 {
		return false, "", "", fmt.Errorf("response data is too short to read header")
	}

	// Check header
	if data[0] != 0xAA || data[1] != 0x03 {
		return false, "", "", fmt.Errorf("login response format error: invalid magic number")
	}

	// Check status code
	if data[3] != 0x01 {
		return false, "", "", fmt.Errorf("login failed: incorrect account/password or request expired")
	}

	if len(data) < 6 {
		return false, "", "", fmt.Errorf("response data is too short to read JWT length")
	}

	// Read JWT length (big-endian, 2 bytes)
	jwtLen := binary.BigEndian.Uint16(data[4:6])
	expectedLen := 6 + jwtLen
	if uint16(len(data)) < expectedLen {
		return false, "", "", fmt.Errorf("response data length is insufficient, expected %d, got %d", expectedLen, len(data))
	}

	// Extract JWT
	jwtGipBytes := data[6 : 6+jwtLen]
	// Decompress JWT
	jwtBuf, err := decompress(jwtGipBytes)
	if err != nil {
		return false, "", "", fmt.Errorf("failed to decompress JWT: %w", err)
	}

	longToken := string(jwtBuf)

	// Verify and extract temp token (assuming you already have the extractAndVerifyTempFromLong function)
	tempToken, err := extractAndVerifyTempFromLong(longToken)
	if err != nil {
		return true, longToken, "", fmt.Errorf("JWT verification failed: %w", err)
	}

	return true, longToken, tempToken, nil
}

func (dc *DuplexClient) Login(account, password string) error {
	if dc.conn == nil {
		return fmt.Errorf("KCP connection (conn) is nil, must be initialized before Login")
	}

	initKeys()

	// 1. Encrypt login data
	encrypted, err := encryptLoginJSON(account, password)
	if err != nil {
		LogInfo(fmt.Sprintf("Failed to encrypt login data: %v", err))
		return fmt.Errorf("encrypt failed: %w", err)
	}

	// 2. Send login request: 0xAA 0x03 + msg_id + encrypted
	msg := []byte{0xAA, 0x03}
	msg_id := dc.opcodeQueues[0x03].Pop()
	msg = append(msg, msg_id)

	req := append(msg, encrypted...)
	err = dc.Send(req)

	if err != nil {
		LogInfo(fmt.Sprintf("Failed to send login request: %v", err))
		return fmt.Errorf("write login request failed: %w", err)
	}

	// 3. Read and parse the response
	response := dc.opcodeQueues[0x03].Read(msg_id)
	if response[0] == byte(0x09) {
		return fmt.Errorf("login timeout")
	}

	success, longToken, tempToken, err := parseLoginResponse(response)
	if err != nil {
		LogInfo(fmt.Sprintf("Failed to parse login response: %v", err))
		return fmt.Errorf("parse login response failed: %w", err)
	}
	if !success {
		return fmt.Errorf("login failed")
	}

	// 4. Save the token to the instance
	dc.tokenMutex.Lock()
	dc.longToken = longToken
	dc.tempToken = tempToken
	dc.tokenMutex.Unlock()

	// 5. Optional: save to file
	saveTokens(longToken, dc.isStreamMode)

	// 6. Start the token refresh goroutine
	if !dc.isStartFreshToken {
		dc.wg.Add(1)
		go dc.tokenRefreshLoop()
		dc.conn.SetTempToken(tempToken)
		dc.conn.Start()
		dc.isStartFreshToken = true
	}

	LogInfo("Login successful, token updated")
	return nil
}

func SplitPairToken(token string) (string, string) {
	tokens := strings.Split(token, PairTokenSeparator)
	if len(tokens) != 2 {
		LogInfo("SplitPairToken length: " + fmt.Sprint(len(tokens)))

		return "", ""
	}
	return tokens[0], tokens[1]
}

//------------------------------------------------------------------------------
// cgo 
//------------------------------------------------------------------------------

//export startClient
func startClient() C.int {
	var err error

	globalStreamClient, err = NewDuplexClient(StreamServerAddr, "123", true)
	globalMessageClient, err = NewDuplexClient(MessageServerAddr, "456", false)
	globalWaitLockQueue = NewWaitLockQueue()
	globalWaitLockQueueCounts = NewCounterMap()

	globalBLEBufferPool = NewPacketBufferPool()

	if err != nil {
		LogInfo(fmt.Sprintf("failed to create full-duplex client: %v", err))
		return -1
	}

	return 0
}

//export stopClient
func stopClient() {
	if globalStreamClient != nil {
		globalStreamClient.Close()
		globalStreamClient = nil
		fmt.Println("client stopped")
	}
	if globalMessageClient != nil {
		globalMessageClient.Close()
		globalMessageClient = nil
		fmt.Println("client stopped")
	}
}

//export registerPair
func registerPair(cRoleByte C.char) C.int {
	roleByte := byte(cRoleByte)
	spair, mpair := globalPairToken["s"], globalPairToken["m"]

	if spair == "" || mpair == "" {
		LogInfo(fmt.Sprintf("register: invalid global pair token format, expected 'temp%stemp'", PairTokenSeparator))
		return C.int(-3)
	}

	// Load token
	SAuthToken := loadTempToken(true)
	MAuthToken := loadTempToken(false)

	if SAuthToken == "" || MAuthToken == "" {
		LogInfo(fmt.Sprintf("register: failed to read auth token, please ensure you are logged in"))
		return C.int(-4)
	}
	if globalStreamClient == nil || globalMessageClient == nil {
		LogInfo(fmt.Sprintf("register: global client not initialized, please call startclient first"))
		return C.int(-1)
	}

	err := globalStreamClient.register(SAuthToken, spair, roleByte)
	if err != nil {
		LogInfo(fmt.Sprintf("stream register failed: %v", err))
		return C.int(-2)
	}

	LogInfo(fmt.Sprintf("stream register succeeded (role: %c)", roleByte))

	err = globalMessageClient.register(MAuthToken, mpair, roleByte)
	if err != nil {
		LogInfo(fmt.Sprintf("message register failed: %v", err))
		return C.int(-2)
	}

	LogInfo(fmt.Sprintf("message register succeeded (role: %c)", roleByte))

	return C.int(0)
}

//export registerSelf
func registerSelf(cRoleByte C.char) C.int {
	roleByte := byte(cRoleByte)

	// Load token
	SAuthToken := loadTempToken(true)
	MAuthToken := loadTempToken(false)

	if SAuthToken == "" || MAuthToken == "" {
		LogInfo(fmt.Sprintf("register: failed to read auth token, please ensure you are logged in"))
		return C.int(-4)
	}

	if globalStreamClient == nil || globalMessageClient == nil {
		LogInfo(fmt.Sprintf("register: global client not initialized, please call startclient first"))
		return C.int(-1)
	}

	err := globalStreamClient.register(SAuthToken, SAuthToken, roleByte)
	if err != nil {
		LogInfo(fmt.Sprintf("stream register failed: %v", err))
		return C.int(-2)
	}

	LogInfo(fmt.Sprintf("stream register succeeded (role: %c)", roleByte))

	err = globalMessageClient.register(MAuthToken, MAuthToken, roleByte)
	if err != nil {
		LogInfo(fmt.Sprintf("message register failed: %v", err))
		return C.int(-2)
	}

	LogInfo(fmt.Sprintf("message register succeeded (role: %c)", roleByte))

	return C.int(0)
}

//export login
func login(accountC, passwordC *C.char) C.int {
	account := C.GoString(accountC)
	password := C.GoString(passwordC)
	if globalStreamClient == nil || globalMessageClient == nil {
		LogError("client not initialized, please call startClient first")
		return -1
	}
	if err := globalStreamClient.Login(account, password); err != nil {
		LogError(fmt.Sprintf("S login error: %v", err))
		return -1
	}

	if err := globalMessageClient.Login(account, password); err != nil {
		LogError(fmt.Sprintf("M login error: %v", err))
		return -1
	}

	return 0 // success
}

//export SendPin
func SendPin(pinC *C.char) C.int {
	pin := C.GoString(pinC) // NULL → ""

	SAuthToken := loadTempToken(true)
	MAuthToken := loadTempToken(false)

	if SAuthToken == "" || MAuthToken == "" {
		log.Println("register: failed to read auth token, please ensure you are logged in")
		return C.int(-4)
	}

	if globalStreamClient == nil || globalMessageClient == nil {
		log.Println("register: global client not initialized, please call startclient first")
		return C.int(-1)
	}

	if err, s := globalStreamClient.SendPinShare(pin, SAuthToken); err != nil {
		log.Printf("SendPin Stream Error: %v", err)
		return C.int(s)
	}

	if err, s := globalMessageClient.SendPinShare(pin, MAuthToken); err != nil {
		log.Printf("SendPin Message Error: %v", err)
		return C.int(s)
	}

	return 0 // success
}

//export FetchPinToken
func FetchPinToken(pinC *C.char) C.int {
	pin := C.GoString(pinC) // NULL → ""

	SAuthToken := loadTempToken(true)
	MAuthToken := loadTempToken(false)

	if SAuthToken == "" || MAuthToken == "" {
		log.Println("register: failed to read auth token, please ensure you are logged in")
		return C.int(-1)
	}

	if globalStreamClient == nil || globalMessageClient == nil {
		log.Println("register: global client not initialized, please call startclient first")
		return C.int(-1)
	}

	spairToken, err := globalStreamClient.FetchPinToken(pin, SAuthToken)
	LogInfo(fmt.Sprintf("FetchPinToken spairToken Len: %d", len(spairToken)))

	if err != nil {
		log.Printf("FetchPinToken Stream: failed to get pair token: %v", err)
		return C.int(-1)
	}

	mpairToken, err := globalMessageClient.FetchPinToken(pin, MAuthToken)
	if err != nil {
		log.Printf("FetchPinToken Message: failed to get pair token: %v", err)
		return C.int(-1)
	}

	globalPairToken["s"] = spairToken
	globalPairToken["m"] = mpairToken
	LogInfo("Fetch Token s: " + spairToken)
	LogInfo("Fetch Token m:" + mpairToken)

	return C.int(0) // Success
}

//export ReadPin
func ReadPin() *C.char {
	const maxRetries = 5 // Avoid infinite retries
	rand.Seed(time.Now().UnixNano())

	for i := 0; i < maxRetries; i++ {
		// Generate a 6-digit decimal string, padding with zeros if necessary
		pinNum := rand.Intn(1000000) // 0 - 999999
		pin := fmt.Sprintf("%06d", pinNum)

		SAuthToken := loadTempToken(true)
		MAuthToken := loadTempToken(false)

		if SAuthToken == "" || MAuthToken == "" {
			log.Println("register: failed to read auth token, please ensure you are logged in")
			return C.CString("")
		}

		if globalStreamClient == nil || globalMessageClient == nil {
			log.Println("register: global client not initialized, please call startclient first")
			return C.CString("")
		}

		err, ms := globalMessageClient.SendPinShare(pin, MAuthToken)
		if err != nil {
			log.Printf("ReadStreamPin Message Error: %v", err)
		}
		err, ss := globalStreamClient.SendPinShare(pin, SAuthToken)
		if err != nil {
			log.Printf("ReadMessagePin Message Error: %v", err)
		}
		if ms == 0x01 && ss == 0x01 {
			return C.CString(pin)
		} else if ms == 0x03 || ss == 0x03 {
			// Failure, continue retrying
			continue
		} else {
			// Other status codes are considered unrecoverable errors, optionally break or continue
			fmt.Printf("ReadPin Error: StreamStatus=%v, MessageStatus=%v", ss, ms)
			continue
		}
	}

	// All retries failed, return an empty string or an error identifier
	return C.CString("") 
}

//export ReadPinTempToken
func ReadPinTempToken() *C.char {

	if globalStreamClient == nil || globalMessageClient == nil {
		log.Println("register: global client not initialized, please call startclient first")
		return C.CString("")
	}
	SAuthToken := loadTempToken(true)
	MAuthToken := loadTempToken(false)
	if SAuthToken == "" || MAuthToken == "" {
		log.Println("register: failed to read auth token, please ensure you are logged in")
		return C.CString("")
	}
	globalPairToken["s"] = SAuthToken
	globalPairToken["m"] = MAuthToken
	jsonData, err := json.Marshal(globalPairToken)
	if err != nil {
		log.Printf("Failed to marshal globalPairToken to JSON: %v", err)
		return C.CString("")
	}
	compressData, err := compress(jsonData)
	if err != nil {
		log.Printf("Failed to compress JSON data: %v", err)
		return C.CString("")
	}
	encodedStr := base64.StdEncoding.EncodeToString(compressData)
	return C.CString(encodedStr)
}

//export ReconnectToServer
func ReconnectToServer() C.int {
	SAuthToken := loadLongToken(true)
	MAuthToken := loadLongToken(false)

	if SAuthToken == "" || MAuthToken == "" {
		log.Println("register: failed to read auth token, please ensure you are logged in")
		return C.int(-1)
	}

	if globalStreamClient == nil || globalMessageClient == nil {
		log.Println("register: global client not initialized, please call startclient first")
		return C.int(-1)
	}

	// Reconnect Stream client
	err := globalStreamClient.conn.reconnectThroughLongToken(SAuthToken, true)
	if err != nil {
		LogError("Reconnect Stream Failed: " + err.Error())
		return C.int(-1)
	}
	globalStreamClient.longToken = SAuthToken
	globalStreamClient.tempToken = ""

	// Refresh Token
	if globalStreamClient.RefreshToken() != nil {
		LogError("Reconnect Stream Refresh Token Failed")
		return C.int(-1)
	}
	tempToken := loadTempToken(true)
	globalStreamClient.conn.SetTempToken(tempToken)
	if !globalStreamClient.conn.isStart {
		globalStreamClient.conn.isStart = true
		globalStreamClient.conn.Start()
	}
	if !globalStreamClient.isStartFreshToken {
		globalStreamClient.wg.Add(1)
		go globalStreamClient.tokenRefreshLoop()
	}

	// Reconnect Message client
	err = globalMessageClient.conn.reconnectThroughLongToken(MAuthToken, false)
	if err != nil {
		LogError("Reconnect Message Failed: " + err.Error())
		return C.int(-1)
	}
	globalMessageClient.longToken = MAuthToken
	globalMessageClient.tempToken = ""


	if globalMessageClient.RefreshToken() != nil {
		LogError("Reconnect Message Refresh Token Failed")
		return C.int(-1)
	}
	tempToken = loadTempToken(false)
	globalMessageClient.conn.SetTempToken(tempToken)
	if !globalMessageClient.conn.isStart {
		globalMessageClient.conn.isStart = true
		globalMessageClient.conn.Start()
	}
	if !globalMessageClient.isStartFreshToken {
		globalMessageClient.wg.Add(1)
		go globalMessageClient.tokenRefreshLoop()
	}

	return C.int(1)
}

//export ReadTimeStamp
func ReadTimeStamp() *C.char {
	if globalMessageClient == nil {
		log.Println("register: global client not initialized, please call startclient first")
		return C.CString("")
	}
	tempToken := loadTempToken(false)
	if tempToken == "" {
		log.Println("register: failed to read auth token, please ensure you are logged in")
		return C.CString("")
	}
	msg_id := globalMessageClient.opcodeQueues[0x0A].Pop()
	tokenBytes := []byte(tempToken)
	tokenLen := len(tokenBytes)
	msg := []byte{0xAA, 0x0A}
	msg = append(msg, msg_id)
	// Add 2-byte length (big-endian)
	lenBuf := make([]byte, 2)
	binary.BigEndian.PutUint16(lenBuf, uint16(tokenLen))
	msg = append(msg, lenBuf...)
	msg = append(msg, tokenBytes...)
	if err := globalMessageClient.Send(msg); err != nil {
		LogInfo(fmt.Sprintf("failed to send delay refresh request: %v", err))
		return C.CString("")
	}
	response := globalMessageClient.opcodeQueues[0x0A].Read(msg_id)
	if len(response) == 0 {
		LogInfo(fmt.Sprintf("no refresh response received"))
		return C.CString("")
	}
	if response[0] == 0x09 {
		LogInfo(fmt.Sprintf("delay refresh timed out"))
		return C.CString("")
	}
	if len(response) < 3 {
		LogInfo(fmt.Sprintf("%s invalid delay refresh response format", globalMessageClient.clientName))
		return C.CString("")
	}
	if response[3] != 0x01 {
		LogInfo(fmt.Sprintf("%s server rejected delay refresh request", globalMessageClient.clientName))
		return C.CString("")
	}
	if len(response) < 5 {
		LogInfo(fmt.Sprintf("%s incomplete delay refresh response data", globalMessageClient.clientName))
		return C.CString("")
	}
	data := response[4:]
	if len(data) != 10 {
		LogInfo(fmt.Sprintf("%s invalid delay refresh response data length", globalMessageClient.clientName))
		return C.CString("")
	}
	ts := data[:8]
	delay := getCurrentTimestamp() - binary.BigEndian.Uint64(ts)
	binary.BigEndian.PutUint64(data[:8], delay)
	base64Data := base64.StdEncoding.EncodeToString(data)
	return C.CString(base64Data)
}

//export CancelPair
func CancelPair() C.int {
	if globalMessageClient == nil {
		log.Println("register: global client not initialized, please call startclient first")
		return C.int(-1)
	}
	tempToken := loadTempToken(false)
	if tempToken == "" {
		log.Println("register: failed to read auth token, please ensure you are logged in")
		return C.int(-2)
	}
	msg_id := globalMessageClient.opcodeQueues[0x0B].Pop()
	tokenBytes := []byte(tempToken)
	tokenLen := len(tokenBytes)
	msg := []byte{0xAA, 0x0B}
	msg = append(msg, msg_id)
	// Add 2-byte length (big-endian)
	lenBuf := make([]byte, 2)
	binary.BigEndian.PutUint16(lenBuf, uint16(tokenLen))
	msg = append(msg, lenBuf...)
	msg = append(msg, tokenBytes...)
	if err := globalMessageClient.Send(msg); err != nil {
		LogInfo(fmt.Sprintf("failed to send cancel pairing request: %v", err))
		return C.int(-3)
	}
	response := globalMessageClient.opcodeQueues[0x0A].Read(msg_id)
	if len(response) == 0 {
		LogInfo(fmt.Sprintf("no refresh response received"))
		return C.int(-4)
	}
	if response[0] == 0x09 {
		LogInfo(fmt.Sprintf("cancel pairing timed out"))
		return C.int(-5)
	}
	if len(response) < 3 {
		LogInfo(fmt.Sprintf("%s cancel pairing response format error", globalMessageClient.clientName))
		return C.int(-6)
	}
	if response[3] != 0x01 {
		LogInfo(fmt.Sprintf("%s server rejected cancel pairing request", globalMessageClient.clientName))
		return C.int(-7)
	}
	return C.int(1) // Success
}

//export SendMessageData
func SendMessageData(AdvDataC *C.char) C.int {
	if globalMessageClient == nil {
		log.Println("register: global client not initialized, please call startclient first")
		return C.int(-1)
	}
	// Base64 decode
	AdvData := C.GoString(AdvDataC)
	payload, err := base64.StdEncoding.DecodeString(AdvData)

	if err != nil {
		log.Printf("SetAdvData: Base64 decode failed: %v", err)
		return C.int(-2)
	}
	sendData, err := compress(payload)
	if err != nil {
		log.Printf("SetAdvData: compression failed: %v", err)
		return C.int(-3)
	}
	// Construct message header
	msg := []byte{0xAA, 0x08}
	msg_id := globalMessageClient.opcodeQueues[0x08].Pop()
	msg = append(msg, msg_id)

	msg = append(msg, sendData...)
	globalMessageClient.Send(msg)
	resp := globalMessageClient.opcodeQueues[0x08].Read(msg_id)
	if len(resp) == 0 {
		LogInfo(fmt.Sprintf("No response received"))
		return C.int(-4)
	}
	if resp[0] == byte(0x09) {
		LogInfo(fmt.Sprintf("SetMessageData timeout"))
		return C.int(-5)
	}
	if len(resp) < 4 {
		LogInfo(fmt.Sprintf("%s SetMessageData response format error", globalMessageClient.clientName))
		return C.int(-6)
	}
	if resp[3] != 0x01 {
		LogInfo(fmt.Sprintf("%s server rejected SetMessageData request", globalMessageClient.clientName))
		return C.int(uint8(resp[3]))
	}
	return C.int(1) // Success
}

//export ReadMessageData
func ReadMessageData() *C.char {
	if globalMessageClient == nil {
		LogInfo(fmt.Sprintf("ReadMessageData: global client not initialized, please call startclient first"))
		return C.CString("")
	}
	ResponseData := globalMessageClient.ReadResponseData(0x08)
	if ResponseData == nil {
		LogInfo(fmt.Sprintf("ReadMessageData: failed to read response, no data returned"))
		return C.CString("")
	}

	if len(ResponseData) < 4 {
		LogInfo(fmt.Sprintf("ReadMessageData: response data too short"))
		return C.CString("")
	}
	if ResponseData[3] != 0x01 {
		LogInfo(fmt.Sprintf("ReadMessageData: server rejected request, status code: %d", ResponseData[3]))
		return C.CString("")
	}
	payloadCompressed := ResponseData[4:]
	payload, err := decompress(payloadCompressed)
	if err != nil {
		LogInfo(fmt.Sprintf("ReadMessageData: failed to decompress response data: %v", err))
		return C.CString("")
	}
	encodedStr := base64.StdEncoding.EncodeToString(payload)
	return C.CString(encodedStr)
}

//export ReadCurrentTimestamp
func ReadCurrentTimestamp() int64 {
	return getCurrentTimestampInt64()
}

//export WaitResponseLock
func WaitResponseLock(op uint8) bool {

	msgid := globalStreamClient.opcodeQueues[op].Pop()
	buf := globalBLEBufferPool.Acquire()
	copy(buf, []byte{0xAA, 0x00, 0x06, 0xAA, 0x50, BLEMsgID, 0x01, op, msgid})
	globalStreamClient.conn.Write(buf[:9])

	resp := globalStreamClient.opcodeQueues[op].Read(msgid) 

	if bytes.Equal(resp, []byte{0x09}) {
		return false
	} else {
		return true
	}
}

//export WaitResponseUnlock
func WaitResponseUnlock(op uint8) {
	msg_id := globalWaitLockQueue.Get(op) 
	buf := globalBLEBufferPool.Acquire()
	copy(buf, []byte{0xAA, 0x00, 0x06, 0xAA, 0x50, BLEMsgID, 0x02, op, msg_id})
	globalStreamClient.conn.Write(buf[:9])
}
