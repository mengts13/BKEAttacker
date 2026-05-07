package main

import (
	"encoding/base64"
	"encoding/json"
	"fmt"
	"sync"
)

// --- BLE structs ---
type BLEDevice struct {
	DeviceName    string    `json:"deviceName"`
	DeviceAddress string    `json:"deviceAddress"`
	Services      []Service `json:"services"`
}

type Service struct {
	UUID            string           `json:"uuid"`
	Characteristics []Characteristic `json:"characteristics"`
}

type Characteristic struct {
	UUID        string       `json:"uuid"`
	Properties  string       `json:"properties"`
	Permissions string       `json:"permissions"`
	Descriptors []Descriptor `json:"descriptors"`
}

type Descriptor struct {
	UUID        string `json:"uuid"`
	Permissions string `json:"permissions"`
}

// BLEMapper uses a single byte as the unique identifier for each element (service/characteristic/descriptor).
type BLEMapper struct {
	mu sync.RWMutex

	// Forward mappings.
	serviceByte map[string]byte
	charByte    map[string]map[string]byte
	descByte    map[string]map[string]map[string]byte

	// Reverse mapping: byte -> UUID path info.
	reverseMap map[byte]uuidInfo

	// Allocator state (used during initialization).
	nextByte byte

	// Full device snapshot used to build maps.
	Device        *BLEDevice
	isInitialized bool
}

type uuidInfo struct {
	ServiceUUID string
	CharUUID    string
	DescUUID    string
	Code        int // 1=service, 2=char, 3=desc
}

func NewBLEMapper() *BLEMapper {
	return &BLEMapper{
		serviceByte: make(map[string]byte),
		charByte:    make(map[string]map[string]byte),
		descByte:    make(map[string]map[string]map[string]byte),
		reverseMap:  make(map[byte]uuidInfo),
		nextByte:    1, // 0 is reserved as an invalid / "not found" value.
	}
}

// alloc returns the next unique byte ID.
// It panics on overflow because the ID space is limited to 255 usable values.
func (m *BLEMapper) alloc() byte {
	if m.nextByte == 0 {
		panic("BLEMapper: byte ID space exhausted (max 255 elements)")
	}
	b := m.nextByte
	m.nextByte++
	return b
}

// --- Get helpers (return byte IDs) ---

func (m *BLEMapper) GetServiceByte(serviceUUID string) byte {
	m.mu.RLock()
	if !m.isInitialized {
		m.mu.RUnlock()
		return 0
	}
	defer m.mu.RUnlock()

	if b, ok := m.serviceByte[serviceUUID]; ok {
		return b
	}
	return 0
}

func (m *BLEMapper) GetCharacteristicByte(serviceUUID, charUUID string) byte {
	m.mu.RLock()
	if !m.isInitialized {
		m.mu.RUnlock()
		return 0
	}
	defer m.mu.RUnlock()

	if svc, ok := m.charByte[serviceUUID]; ok {
		if b, ok := svc[charUUID]; ok {
			return b
		}
	}
	return 0
}

func (m *BLEMapper) GetDescriptorByte(serviceUUID, charUUID, descUUID string) byte {
	m.mu.RLock()
	if !m.isInitialized {
		m.mu.RUnlock()
		return 0
	}
	defer m.mu.RUnlock()

	if svc, ok := m.descByte[serviceUUID]; ok {
		if chr, ok := svc[charUUID]; ok {
			if b, ok := chr[descUUID]; ok {
				return b
			}
		}
	}
	return 0
}

// ByteToUUIDs converts a byte ID back to the UUID path and element type code.
func (m *BLEMapper) ByteToUUIDs(b byte) (service, char, desc string, code int) {
	m.mu.RLock()
	if !m.isInitialized {
		m.mu.RUnlock()
		return "", "", "", -1
	}
	defer m.mu.RUnlock()

	if info, ok := m.reverseMap[b]; ok {
		return info.ServiceUUID, info.CharUUID, info.DescUUID, info.Code
	}
	return "", "", "", -1
}

// InitFromBase64JSON initializes mappings from a base64-encoded JSON payload.
// It assigns a unique byte ID to each service/characteristic/descriptor.
func (m *BLEMapper) InitFromBase64JSON(b64str string) error {
	jsonBytes, err := base64.StdEncoding.DecodeString(b64str)
	if err != nil {
		return fmt.Errorf("base64 decode failed: %w", err)
	}

	var device BLEDevice
	if err := json.Unmarshal(jsonBytes, &device); err != nil {
		return fmt.Errorf("json unmarshal failed: %w", err)
	}

	m.mu.Lock()
	defer m.mu.Unlock()

	m.serviceByte = make(map[string]byte)
	m.charByte = make(map[string]map[string]byte)
	m.descByte = make(map[string]map[string]map[string]byte)
	m.reverseMap = make(map[byte]uuidInfo)
	m.nextByte = 1
	m.Device = &device

	for _, svc := range device.Services {
		svcUUID := svc.UUID
		b := m.alloc()
		m.serviceByte[svcUUID] = b
		m.reverseMap[b] = uuidInfo{ServiceUUID: svcUUID, Code: 1}

		if m.charByte[svcUUID] == nil {
			m.charByte[svcUUID] = make(map[string]byte)
		}

		for _, chr := range svc.Characteristics {
			charUUID := chr.UUID
			b := m.alloc()
			m.charByte[svcUUID][charUUID] = b
			m.reverseMap[b] = uuidInfo{
				ServiceUUID: svcUUID,
				CharUUID:    charUUID,
				Code:        2,
			}

			for _, desc := range chr.Descriptors {
				descUUID := desc.UUID
				b := m.alloc()
				if m.descByte[svcUUID] == nil {
					m.descByte[svcUUID] = make(map[string]map[string]byte)
				}
				if m.descByte[svcUUID][charUUID] == nil {
					m.descByte[svcUUID][charUUID] = make(map[string]byte)
				}
				m.descByte[svcUUID][charUUID][descUUID] = b
				m.reverseMap[b] = uuidInfo{
					ServiceUUID: svcUUID,
					CharUUID:    charUUID,
					DescUUID:    descUUID,
					Code:        3,
				}
			}
		}
	}
	m.isInitialized = true

	return nil
}
