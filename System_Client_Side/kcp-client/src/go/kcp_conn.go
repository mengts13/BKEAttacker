package main

import (
	"bytes"
	"encoding/binary"
	"errors"
	"fmt"
	"io"
	"log"
	"sync"
	"time"

	"github.com/xtaci/kcp-go/v5"
)

type KCPConnection struct {
	addr         string
	conn         *kcp.UDPSession
	mu           sync.RWMutex
	heartbeat    int
	tempToken    string
	stopChan     chan struct{}
	wg           sync.WaitGroup
	isStart      bool
	isStreamMode bool
}

// NewKCPConnection establishes a base KCP connection immediately (used for login).
func NewKCPConnection(serverAddr string) (*KCPConnection, error) {
	conn, err := kcp.DialWithOptions(serverAddr, nil, 10, 3)
	if err != nil {
		return nil, fmt.Errorf("failed to connect to server: %w", err)
	}
	conn.SetNoDelay(1, 10, 2, 1)
	conn.SetStreamMode(true)
	conn.SetWriteDelay(false)
	// During login, we do not set deadlines (or set a longer one; controlled by the caller).

	k := &KCPConnection{
		addr:         serverAddr,
		conn:         conn,
		stopChan:     make(chan struct{}),
		heartbeat:    10, // -1 indicates "login connection" and does not participate in heartbeat.
		isStart:      false,
		isStreamMode: true,
	}
	log.Printf("KCPConnection: Established login connection to %s", serverAddr)
	return k, nil
}

func NewKCPMessageConnection(serverAddr string) (*KCPConnection, error) {
	conn, err := kcp.DialWithOptions(serverAddr, nil, 10, 3)
	if err != nil {
		return nil, fmt.Errorf("failed to connect to server: %w", err)
	}
	conn.SetNoDelay(1, 10, 2, 1)
	conn.SetStreamMode(false)
	conn.SetWriteDelay(false)
	// During login, we do not set deadlines (or set a longer one; controlled by the caller).
	k := &KCPConnection{
		addr:         serverAddr,
		conn:         conn,
		stopChan:     make(chan struct{}),
		heartbeat:    10, // -1 indicates "login connection" and does not participate in heartbeat.
		isStart:      false,
		isStreamMode: false,
	}
	log.Printf("KCPConnection: Established login connection to %s", serverAddr)
	return k, nil
}

// SetTempToken sets the temporary token (called after successful login).
func (k *KCPConnection) SetTempToken(token string) {
	k.mu.Lock()
	k.tempToken = token
	k.mu.Unlock()
}

// Start launches the background monitor loop (called after successful login).
// It closes the current login connection and switches to a long-lived authenticated connection mode.
func (k *KCPConnection) Start() {

	k.wg.Add(1)
	go k.monitorLoop()
	k.isStart = true
}

// monitorLoop manages the long-lived connection lifecycle.
func (k *KCPConnection) monitorLoop() {
	defer k.wg.Done()

	ticker := time.NewTicker(500 * time.Millisecond)
	defer ticker.Stop()

	for {
		select {
		case <-ticker.C:
			k.mu.RLock()
			token := k.tempToken
			heartbeat := k.heartbeat
			k.heartbeat -= 1
			k.mu.RUnlock()

			// If we haven't switched to long-lived mode yet (heartbeat == -1), switch first.
			if heartbeat == -1 {
				k.mu.Lock()
				if k.conn != nil {
					k.conn.Close()
					k.conn = nil
				}
				k.heartbeat = 0 // Mark as long-lived mode, but not connected yet.
				k.mu.Unlock()
				log.Println("KCPConnection: Closed login connection; preparing long-lived connection...")
			}

			if token != "" && heartbeat == 0 {
				log.Println("KCPConnection: Attempting authenticated connection...")
				k.mu.RLock()
				k.heartbeat += 1
				k.mu.RUnlock()
				if err := k.reconnectWithAuth(); err != nil {
					log.Printf("authenticated connection failed: %v", err)
				}
			}

		case <-k.stopChan:
			k.mu.Lock()
			if k.conn != nil {
				k.conn.Close()
				k.conn = nil
			}
			k.mu.Unlock()
			return
		}
	}
}


func (k *KCPConnection) reconnectWithAuth() error {
	k.mu.RLock()
	token := k.tempToken
	streamMode := k.isStreamMode
	k.mu.RUnlock()

	if token == "" {
		return errors.New("tempToken is empty")
	}

	conn, err := kcp.DialWithOptions(k.addr, nil, 10, 3)
	if err != nil {
		return fmt.Errorf("dial failed: %w", err)
	}
	defer func() {
		if conn != nil {
			conn.Close()
		}
	}()

	conn.SetNoDelay(1, 10, 2, 1)
	conn.SetStreamMode(streamMode)
	conn.SetWriteDelay(false)
	conn.SetWriteDeadline(time.Now().Add(5 * time.Second))

	// Build payload: [msgType=0x07][flag=MaxOpCodeNumber][token_len_be][token]
	payload := make([]byte, 0, 2+2+len(token))
	payload = append(payload, 0x07)
	payload = append(payload, MaxOpCodeNumber) // flag

	tokenLenBuf := make([]byte, 2)
	binary.BigEndian.PutUint16(tokenLenBuf, uint16(len(token)))
	payload = append(payload, tokenLenBuf...)
	payload = append(payload, token...)

	// Build full frame: 0xAA + len(payload) + payload
	frame := make([]byte, 3+len(payload))
	frame[0] = 0xAA
	binary.BigEndian.PutUint16(frame[1:3], uint16(len(payload)))
	copy(frame[3:], payload)

	// Send auth request.
	if _, err = conn.Write(frame); err != nil {
		return fmt.Errorf("send auth failed: %w", err)
	}

	respHeader := make([]byte, 3)
	if _, err = io.ReadFull(conn, respHeader); err != nil {
		return fmt.Errorf("read auth response header failed: %w", err)
	}

	if respHeader[0] != 0xAA {
		return fmt.Errorf("invalid response magic: 0x%02X", respHeader[0])
	}

	respLen := int(binary.BigEndian.Uint16(respHeader[1:3]))
	if respLen > 1024 {
		return fmt.Errorf("response payload too large: %d", respLen)
	}

	respPayload := make([]byte, respLen)
	if _, err = io.ReadFull(conn, respPayload); err != nil {
		return fmt.Errorf("read auth response payload failed: %w", err)
	}

	// Expected response payload = [0x07][0x01].
	if len(respPayload) != 2 || respPayload[0] != 0x07 || respPayload[1] != 0x01 {
		return fmt.Errorf("auth rejected: payload=%v", respPayload)
	}

	k.mu.Lock()
	k.conn = conn
	k.heartbeat = 8
	conn = nil // Prevent defer from closing the connection
	k.mu.Unlock()

	log.Printf("KCPConnection: Long-lived authenticated connection established")
	return nil
}

// reconnectWithAuth establishes a connection authenticated with the long token.
func (k *KCPConnection) reconnectThroughLongToken(token string, isStreamMode bool) error {

	if token == "" {
		return errors.New("longToken is empty")
	}

	conn, err := kcp.DialWithOptions(k.addr, nil, 10, 3)
	if err != nil {
		return fmt.Errorf("dial failed: %w", err)
	}
	defer func() {
		if conn != nil {
			conn.Close()
		}
	}()

	conn.SetNoDelay(1, 10, 2, 1)
	conn.SetStreamMode(isStreamMode)
	conn.SetWriteDelay(false)
	conn.SetWriteDeadline(time.Now().Add(5 * time.Second))

	// Build auth request: 0xAA 0x07 [len][token]
	req := make([]byte, 0, 4+len(token))
	req = append(req, 0xAA, 0x07)
	lenBuf := make([]byte, 2)
	binary.BigEndian.PutUint16(lenBuf, uint16(len(token)))
	req = append(req, lenBuf...)
	req = append(req, token...)

	if _, err = conn.Write(req); err != nil {
		return fmt.Errorf("send auth failed: %w", err)
	}

	resp := make([]byte, 3)
	if _, err = io.ReadFull(conn, resp); err != nil {
		return fmt.Errorf("read auth response failed: %w", err)
	}

	if !bytes.Equal(resp, []byte{0xAA, 0x07, 0x01}) {
		return fmt.Errorf("auth rejected: %02X%02X%02X", resp[0], resp[1], resp[2])
	}
	k.mu.Lock()
	k.conn = conn
	k.heartbeat = 8
	conn = nil // Prevent defer from closing the connection
	k.mu.Unlock()

	log.Printf("KCPConnection: Long-lived authenticated connection established")
	return nil
}



func (k *KCPConnection) Write(data []byte) (int, error) {
	k.mu.RLock()
	conn := k.conn
	k.mu.RUnlock()

	if conn == nil {
		return 0, errors.New("connection closed")
	}
	return conn.Write(data)
}

// func (k *KCPConnection) Read(p []byte) (int, error) {
// 	k.mu.RLock()
// 	conn := k.conn
// 	k.mu.RUnlock()

// 	if k.isStreamMode{

// 	}
// 	if conn == nil {
// 		return 0, errors.New("connection closed")
// 	}
// 	if k.isStreamMode{

// 	}

// 	n, err := conn.Read(p)
// 	if err == nil && n > 0 && p[0] == 0xAA {
// 		k.mu.RLock()
// 		isLongTerm := k.heartbeat >= 0 // heartbeat == -1 is a login connection, don't update
// 		k.mu.RUnlock()
// 		if isLongTerm {
// 			k.mu.Lock()
// 			k.heartbeat = 8
// 			k.mu.Unlock()
// 		}
// 	}
// 	return n, err
// }

func (k *KCPConnection) Read(p []byte) (int, error) {
	if len(p) < 3 {
		return 0, errors.New("buffer too small for frame header (need at least 3 bytes)")
	}

	k.mu.RLock()
	conn := k.conn
	k.mu.RUnlock()

	if conn == nil {
		return 0, errors.New("connection closed")
	}

	// Step 1: Read magic byte 0xAA into p[0].
	if _, err := io.ReadFull(conn, p[:1]); err != nil {
		return 0, err
	}
	if p[0] != 0xAA {
		return 0, errors.New("invalid frame header: first byte is not 0xAA")
	}

	// Step 2: Read the 2-byte payload length into p[1:3].
	if _, err := io.ReadFull(conn, p[1:3]); err != nil {
		return 0, err
	}
	payloadLen := int(binary.BigEndian.Uint16(p[1:3]))

	// Step 3: Ensure total length does not exceed the provided buffer.
	totalLen := 1 + payloadLen // 0xAA + payload
	if totalLen > len(p) {
		return 0, fmt.Errorf("buffer too small for payload (need %d, have %d)", totalLen, len(p))
	}

	// Step 4: Read payload into p[1 : 1+payloadLen].
	if payloadLen > 0 {
		if _, err := io.ReadFull(conn, p[1:1+payloadLen]); err != nil {
			return 0, err
		}
	}

	// Step 5: Heartbeat handling (payloadLen == 0).
	if payloadLen == 0 {
		k.mu.RLock()
		isLongTerm := k.heartbeat >= 0
		k.mu.RUnlock()
		if isLongTerm {
			k.mu.Lock()
			k.heartbeat = 8
			k.mu.Unlock()
		}
		return 1, nil
	}

	// Note: at this point p[0] = 0xAA, p[1:1+payloadLen] = payload.
	// Bytes p[1:3] originally stored the length, but they have been overwritten by the payload
	// because we read the payload starting at p[1].
	// This is intentional: we strip the 2-byte length field and keep only 0xAA + payload.

	return totalLen, nil
}

func (k *KCPConnection) Close() {
	close(k.stopChan)
	k.wg.Wait()
	k.mu.Lock()
	if k.conn != nil {
		k.conn.Close()
		k.conn = nil
	}
	k.mu.Unlock()
}
