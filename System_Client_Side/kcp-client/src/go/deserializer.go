// deserializer.go
package main

import (
	"errors"
	"fmt"
)

// Opcode definitions
type Opcode byte

const (
	// Client → Server
	OpConnectGatt                   Opcode = 0x10
	OpWriteCharacteristic           Opcode = 0x11
	OpReadCharacteristic            Opcode = 0x12
	OpWriteDescriptor               Opcode = 0x13
	OpReadDescriptor                Opcode = 0x14
	OpSetCharacteristicNotification Opcode = 0x16
	OpDiscoverService               Opcode = 0x17

	// Server → Client
	OpSendReadCharacteristicResponse  Opcode = 0x20
	OpSendWriteCharacteristicResponse Opcode = 0x21
	OpSendReadDescriptorResponse      Opcode = 0x22
	OpSendWriteDescriptorResponse     Opcode = 0x23
	OpNotifyCharacteristicChanged     Opcode = 0x24

	// Common
	WaitLock Opcode = 0x50
)

func (op Opcode) String() string {
	switch op {
	case OpConnectGatt:
		return "OpConnectGatt"
	case OpWriteCharacteristic:
		return "OpWriteCharacteristic"
	case OpReadCharacteristic:
		return "OpReadCharacteristic"
	case OpWriteDescriptor:
		return "OpWriteDescriptor"
	case OpReadDescriptor:
		return "OpReadDescriptor"
	case OpSetCharacteristicNotification:
		return "OpSetCharacteristicNotification"
	case OpDiscoverService:
		return "OpDiscoverService"
	case OpSendReadCharacteristicResponse:
		return "OpSendReadCharacteristicResponse"
	case OpSendWriteCharacteristicResponse:
		return "OpSendWriteCharacteristicResponse"
	case OpSendReadDescriptorResponse:
		return "OpSendReadDescriptorResponse"
	case OpSendWriteDescriptorResponse:
		return "OpSendWriteDescriptorResponse"
	case OpNotifyCharacteristicChanged:
		return "OpNotifyCharacteristicChanged"
	default:
		return fmt.Sprintf("Unknown(0x%02X)", byte(op))
	}
}

// GattRpc represents a parsed GATT operation.
type GattRpc struct {
	Opcode Opcode
	Params map[string]interface{}
}

// ParseRpcPacket parses a full RPC packet (magic 0xAA + opcode + payload).
func ParseRpcPacket(data []byte) (*GattRpc, error) {
	if len(data) == 0 {
		return nil, errors.New("empty packet")
	}
	if data[0] != 0xAA {
		return nil, errors.New("invalid magic byte, expected 0xAA")
	}
	if len(data) < 2 {
		return nil, errors.New("packet too short")
	}

	opcode := Opcode(data[1])
	rpc := &GattRpc{
		Opcode: opcode,
		Params: make(map[string]interface{}),
	}

	switch opcode {
	// ===============
	// Client → Server
	// ===============
	case OpConnectGatt:
		datalen := len(data)
		if datalen < 3 {
			return nil, errors.New("connectGatt: missing addrLen")
		}
		rpc.Params["deviceAddress"] = string(data[2:])
		LogInfo(fmt.Sprintf("OpConnectGatt:%02X,addr: %s", data, rpc.Params["deviceAddress"]))

	case OpWriteCharacteristic, OpReadCharacteristic:
		minLen := 4 // for OpReadCharacteristic: AA 11 svcId charId
		if opcode == OpWriteCharacteristic {
			minLen = 10 // 2 (hdr) + 2 (svc/char) + 4 (writeType) + 2 (dataLen)
		}
		if len(data) < minLen {
			return nil, fmt.Errorf("%s: packet too short", opcode)
		}

		svcId, charId := data[2], data[3]
		rpc.Params["serviceUuid"], _, _, _ = globalUUIDMap.ByteToUUIDs(svcId)
		_, rpc.Params["characteristicUuid"], _, _ = globalUUIDMap.ByteToUUIDs(charId)

		if opcode == OpWriteCharacteristic {
			rpc.Params["writeType"] = int32(uint8(data[4]))
			rpc.Params["data"] = data[5:]
		}

	case OpWriteDescriptor, OpReadDescriptor:
		minLen := 5
		if opcode == OpWriteDescriptor {
			minLen = 7 // +2 for data len
		}
		if len(data) < minLen {
			return nil, fmt.Errorf("%s: packet too short", opcode)
		}
		svcId, charId, descId := data[2], data[3], data[4]
		rpc.Params["serviceUuid"], _, _, _ = globalUUIDMap.ByteToUUIDs(svcId)
		_, rpc.Params["characteristicUuid"], _, _ = globalUUIDMap.ByteToUUIDs(charId)
		_, _, rpc.Params["descriptorUuid"], _ = globalUUIDMap.ByteToUUIDs(descId)

		if opcode == OpWriteDescriptor {
			rpc.Params["data"] = data[5:]
		}

	case OpSetCharacteristicNotification:
		if len(data) < 5 {
			return nil, errors.New("setNotification: too short")
		}
		svcId, charId, flag := data[2], data[3], data[4]
		rpc.Params["serviceUuid"], _, _, _ = globalUUIDMap.ByteToUUIDs(svcId)
		_, rpc.Params["characteristicUuid"], _, _ = globalUUIDMap.ByteToUUIDs(charId)
		rpc.Params["enable"] = flag != 0

	case OpDiscoverService:

	// ===============
	// Server → Client
	// ===============
	case OpSendReadCharacteristicResponse, OpSendWriteCharacteristicResponse, OpSendReadDescriptorResponse, OpSendWriteDescriptorResponse:
		if len(data) < 3 {
			return nil, fmt.Errorf("%s: too short", opcode)
		}
		status := uint8(data[2])
		rpc.Params["status"] = int32(status)
		rpc.Params["data"] = data[3:]

	case OpNotifyCharacteristicChanged:
		if len(data) < 4 {
			return nil, errors.New("notify: too short")
		}
		svcId, charId := data[2], data[3]
		rpc.Params["serviceUuid"], _, _, _ = globalUUIDMap.ByteToUUIDs(svcId)
		_, rpc.Params["characteristicUuid"], _, _ = globalUUIDMap.ByteToUUIDs(charId)
		rpc.Params["data"] = data[4:]

	case WaitLock:
		WaitLockType, TargetOpID, TargetMsgId := data[2], data[3], data[4]
		if WaitLockType == uint8(1) { 
			globalWaitLockQueue.Set(TargetOpID, TargetMsgId)
		} else if WaitLockType == uint8(2) {
			globalStreamClient.opcodeQueues[TargetOpID].SubmitResponse(TargetMsgId, []byte{0x01})
		}

	default:
		return nil, fmt.Errorf("unknown opcode: 0x%02X", opcode)
	}

	return rpc, nil
}
