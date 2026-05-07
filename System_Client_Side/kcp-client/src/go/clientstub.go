package main

/*
#include <jni.h>
*/
import "C"
import (
	"encoding/binary"
	"encoding/hex"
	"log"
	"unsafe"
)

// ===============
// Packet builders (serialization)
// ===============

func buildWriteDescriptorPacket(svcId, charId, descId byte, data []byte) ([]byte, uint16) {
	buf := globalBLEBufferPool.Acquire()
	copy(buf, []byte{0xAA, 0x00, 0x00, 0xAA, 0x13, BLEMsgID, svcId, charId, descId})
	copy(buf[9:], data)
	len_data := uint16(len(data) + 6)
	binary.BigEndian.PutUint16(buf[1:3], len_data)
	return buf, len_data + 3
}

func buildWriteCharacteristicPacket(svcId, charId byte, writeType int, data []byte) ([]byte, uint16) {
	buf := globalBLEBufferPool.Acquire()
	copy(buf, []byte{0xAA, 0x00, 0x00, 0xAA, 0x11, BLEMsgID, svcId, charId, uint8(writeType)})
	copy(buf[9:], data)
	len_data := uint16(len(data) + 6)
	binary.BigEndian.PutUint16(buf[1:3], len_data)
	return buf, len_data + 3
}

func buildReadCharacteristicPacket(svcId, charId byte) []byte {
	return []byte{0xAA, 0x00, 0x05, 0xAA, 0x12, BLEMsgID, svcId, charId}
}

func buildReadDescriptorPacket(svcId, charId, descId byte) []byte {
	return []byte{0xAA, 0x00, 0x06, 0xAA, 0x14, BLEMsgID, svcId, charId, descId}
}

func buildConnectGattPacket(deviceAddr string) ([]byte, uint16) {
	buf := globalBLEBufferPool.Acquire()
	copy(buf, []byte{0xAA, 0x00, 0x00, 0xAA, 0x10, BLEMsgID})
	copy(buf[6:], []byte(deviceAddr))
	len_data := uint16(len(deviceAddr) + 3)
	binary.BigEndian.PutUint16(buf[1:3], len_data)
	return buf, len_data + 3
}

func buildSetNotificationPacket(svcId, charId byte, enable bool) []byte {
	if enable {
		return []byte{0xAA, 0x00, 0x06, 0xAA, 0x16, BLEMsgID, svcId, charId, 1}
	}
	return []byte{0xAA, 0x00, 0x06, 0xAA, 0x16, BLEMsgID, svcId, charId, 0}
}

func buildDiscoverServicePacket() []byte {
	return []byte{0xAA, 0x00, 0x03, 0xAA, 0x17, BLEMsgID}
}

// ===============
// Exported functions for C/JNI calls (no JNIEnv / jstring here)
// ===============

//export goWriteDescriptor
func goWriteDescriptor(
	svc *C.char,
	charac *C.char,
	desc *C.char,
	data *C.uchar,
	dataLen C.int,
) {
	goSvc := C.GoString(svc)
	goChar := C.GoString(charac)
	goDesc := C.GoString(desc)
	goData := C.GoBytes(unsafe.Pointer(data), dataLen)

	svcId := globalUUIDMap.GetServiceByte(goSvc)
	charId := globalUUIDMap.GetCharacteristicByte(goSvc, goChar)
	descId := globalUUIDMap.GetDescriptorByte(goSvc, goChar, goDesc)

	if svcId == 0 || charId == 0 || descId == 0 {
		log.Printf("Unknown UUID in writeDescriptor: %s, %s, %s", goSvc, goChar, goDesc)
		return
	}
	packet, len_data := buildWriteDescriptorPacket(svcId, charId, descId, goData)
	defer globalBLEBufferPool.Release(packet)
	globalStreamClient.conn.Write(packet[:len_data])
	LogInfo("clientstub:" + hex.EncodeToString(packet[:len_data]))
}

//export goWriteCharacteristic
func goWriteCharacteristic(
	svc *C.char,
	charac *C.char,
	writeType C.int,
	data *C.uchar,
	dataLen C.int,
) {
	goSvc := C.GoString(svc)
	goChar := C.GoString(charac)
	goData := C.GoBytes(unsafe.Pointer(data), dataLen)
	goWriteType := int(writeType)

	svcId := globalUUIDMap.GetServiceByte(goSvc)
	charId := globalUUIDMap.GetCharacteristicByte(goSvc, goChar)

	if svcId == 0 || charId == 0 {
		log.Printf("Unknown UUID in writeDescriptor: %s, %s", goSvc, goChar)
		return
	}

	packet, len_data := buildWriteCharacteristicPacket(svcId, charId, goWriteType, goData)
	defer globalBLEBufferPool.Release(packet)
	globalStreamClient.conn.Write(packet[:len_data])
	LogInfo(hex.EncodeToString(packet[:len_data]))
}

//export goReadCharacteristic
func goReadCharacteristic(svc *C.char, charac *C.char) {
	goSvc := C.GoString(svc)
	goChar := C.GoString(charac)

	svcId := globalUUIDMap.GetServiceByte(goSvc)
	charId := globalUUIDMap.GetCharacteristicByte(goSvc, goChar)

	if svcId == 0 || charId == 0 {
		log.Printf("Unknown UUID in writeDescriptor: %s, %s", goSvc, goChar)
		return
	}

	packet := buildReadCharacteristicPacket(svcId, charId)
	globalStreamClient.conn.Write(packet)
	LogInfo(hex.EncodeToString(packet))
}

//export goReadDescriptor
func goReadDescriptor(svc *C.char, charac *C.char, desc *C.char) {
	goSvc := C.GoString(svc)
	goChar := C.GoString(charac)
	goDesc := C.GoString(desc)

	svcId := globalUUIDMap.GetServiceByte(goSvc)
	charId := globalUUIDMap.GetCharacteristicByte(goSvc, goChar)
	descId := globalUUIDMap.GetDescriptorByte(goSvc, goChar, goDesc)

	if svcId == 0 || charId == 0 || descId == 0 {
		log.Printf("Unknown UUID in writeDescriptor: %s, %s, %s", goSvc, goChar, goDesc)
		return
	}

	packet := buildReadDescriptorPacket(svcId, charId, descId)
	globalStreamClient.conn.Write(packet)
	LogInfo(hex.EncodeToString(packet))
}

//export goConnectGatt
func goConnectGatt(deviceAddr *C.char) {
	addr := C.GoString(deviceAddr)
	packet, len_data := buildConnectGattPacket(addr)
	defer globalBLEBufferPool.Release(packet)
	globalStreamClient.conn.Write(packet[:len_data])
	LogInfo(hex.EncodeToString(packet[:len_data]))
}

//export goSetCharacteristicNotification
func goSetCharacteristicNotification(svc *C.char, charac *C.char, enable C.int) {
	goSvc := C.GoString(svc)
	goChar := C.GoString(charac)

	svcId := globalUUIDMap.GetServiceByte(goSvc)
	charId := globalUUIDMap.GetCharacteristicByte(goSvc, goChar)

	if svcId == 0 || charId == 0 {
		log.Printf("Unknown UUID in writeDescriptor: %s, %s", goSvc, goChar)
		return
	}

	packet := buildSetNotificationPacket(svcId, charId, enable != 0)
	globalStreamClient.conn.Write(packet)
	LogInfo(hex.EncodeToString(packet))
}

//export goDiscoverService
func goDiscoverService() {
	packet := buildDiscoverServicePacket()
	globalStreamClient.conn.Write(packet)
	LogInfo(hex.EncodeToString(packet))
}
