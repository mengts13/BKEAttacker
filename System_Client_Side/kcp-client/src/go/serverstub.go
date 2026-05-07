// serverstub.go
package main

import (
	"C"
	"encoding/binary"
	"encoding/hex"
	"log"
	"unsafe"
)

var mtuSize int = 250 // Default MTU size

// ===============
// Packet builders (status as int8, 1 byte)
// ===============

func buildReadCharResponsePacket(status int32, data []byte) ([]byte, uint16) {
	buf := globalBLEBufferPool.Acquire()
	buf[0] = 0xAA
	payloadLen := 4 + len(data) // 1(AA) + 1(cmd) + 1(msgID) + 1(status) + data
	binary.BigEndian.PutUint16(buf[1:3], uint16(payloadLen))
	buf[3] = 0xAA
	buf[4] = 0x20
	buf[5] = BLEMsgID
	buf[6] = byte(status) // int32 → int8 (low byte)
	copy(buf[7:], data)
	return buf, uint16(payloadLen + 3)
}

func buildWriteCharResponsePacket(status int32, data []byte) ([]byte, uint16) {
	buf := globalBLEBufferPool.Acquire()
	buf[0] = 0xAA
	payloadLen := 4 + len(data)
	binary.BigEndian.PutUint16(buf[1:3], uint16(payloadLen))
	buf[3] = 0xAA
	buf[4] = 0x21
	buf[5] = BLEMsgID
	buf[6] = byte(status)
	copy(buf[7:], data)
	return buf, uint16(payloadLen + 3)
}

func buildReadDescResponsePacket(status int32, data []byte) ([]byte, uint16) {
	buf := globalBLEBufferPool.Acquire()
	buf[0] = 0xAA
	payloadLen := 4 + len(data)
	binary.BigEndian.PutUint16(buf[1:3], uint16(payloadLen))
	buf[3] = 0xAA
	buf[4] = 0x22
	buf[5] = BLEMsgID
	buf[6] = byte(status)
	copy(buf[7:], data)
	return buf, uint16(payloadLen + 3)
}

func buildWriteDescResponsePacket(status int32, data []byte) ([]byte, uint16) {
	buf := globalBLEBufferPool.Acquire()
	buf[0] = 0xAA
	payloadLen := 4 + len(data)
	binary.BigEndian.PutUint16(buf[1:3], uint16(payloadLen))
	buf[3] = 0xAA
	buf[4] = 0x23
	buf[5] = BLEMsgID
	buf[6] = byte(status)
	copy(buf[7:], data)
	return buf, uint16(payloadLen + 3)
}

func buildNotifyPacket(svcId, charId byte, data []byte) ([]byte, uint16) {
	buf := globalBLEBufferPool.Acquire()
	buf[0] = 0xAA
	payloadLen := 5 + len(data) // 1(AA)+1(cmd)+1(msgID)+1(svc)+1(char) + data
	binary.BigEndian.PutUint16(buf[1:3], uint16(payloadLen))
	buf[3] = 0xAA
	buf[4] = 0x24
	buf[5] = BLEMsgID
	buf[6] = svcId
	buf[7] = charId
	copy(buf[8:], data)
	return buf, uint16(payloadLen + 3)
}

func buildRequestMTUFromCloudPacket() []byte {
	buf := make([]byte, 6)
	buf[0] = 0xAA
	binary.BigEndian.PutUint16(buf[1:3], 3) // payload: AA + 0x40 + BLEMsgID
	buf[3] = 0xAA
	buf[4] = 0x40
	buf[5] = BLEMsgID
	return buf
}


//export goSendReadCharacteristicResponse
func goSendReadCharacteristicResponse(status C.int, value *C.uchar, valueLen C.int) {
	goData := C.GoBytes(unsafe.Pointer(value), valueLen)
	packet, totalLen := buildReadCharResponsePacket(int32(status), goData)
	defer globalBLEBufferPool.Release(packet)
	globalStreamClient.conn.Write(packet[:totalLen])
	LogInfo(hex.EncodeToString(packet[:totalLen]))
}

//export goSendWriteCharacteristicResponse
func goSendWriteCharacteristicResponse(status C.int, value *C.uchar, valueLen C.int) {
	goData := C.GoBytes(unsafe.Pointer(value), valueLen)
	packet, totalLen := buildWriteCharResponsePacket(int32(status), goData)
	defer globalBLEBufferPool.Release(packet)
	globalStreamClient.conn.Write(packet[:totalLen])
	LogInfo(hex.EncodeToString(packet[:totalLen]))
}

//export goSendReadDescriptorResponse
func goSendReadDescriptorResponse(status C.int, value *C.uchar, valueLen C.int) {
	goData := C.GoBytes(unsafe.Pointer(value), valueLen)
	packet, totalLen := buildReadDescResponsePacket(int32(status), goData)
	defer globalBLEBufferPool.Release(packet)
	globalStreamClient.conn.Write(packet[:totalLen])
	LogInfo(hex.EncodeToString(packet[:totalLen]))
}

//export goSendWriteDescriptorResponse
func goSendWriteDescriptorResponse(status C.int, value *C.uchar, valueLen C.int) {
	goData := C.GoBytes(unsafe.Pointer(value), valueLen)
	packet, totalLen := buildWriteDescResponsePacket(int32(status), goData)
	defer globalBLEBufferPool.Release(packet)
	globalStreamClient.conn.Write(packet[:totalLen])
	LogInfo(hex.EncodeToString(packet[:totalLen]))
}

//export goNotifyCharacteristicChanged
func goNotifyCharacteristicChanged(svc *C.char, charac *C.char, data *C.uchar, dataLen C.int) {
	goSvc := C.GoString(svc)
	goChar := C.GoString(charac)
	goData := C.GoBytes(unsafe.Pointer(data), dataLen)

	svcId := globalUUIDMap.GetServiceByte(goSvc)
	charId := globalUUIDMap.GetCharacteristicByte(goSvc, goChar)

	if svcId == 0 || charId == 0 {
		log.Printf("Unknown UUID in notify: %s, %s", goSvc, goChar)
		return
	}

	packet, totalLen := buildNotifyPacket(svcId, charId, goData)
	defer globalBLEBufferPool.Release(packet)
	globalStreamClient.conn.Write(packet[:totalLen])
	LogInfo(hex.EncodeToString(packet[:totalLen]))
}

//export GetMTU
func GetMTU() C.int {
	return C.int(mtuSize)
}

func RequestMTUFromCloud() {
	packet := buildRequestMTUFromCloudPacket()
	globalStreamClient.conn.Write(packet)
	LogInfo(hex.EncodeToString(packet))
}
