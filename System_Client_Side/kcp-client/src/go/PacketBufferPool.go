package main

const (
	MaxBLEPacketSize = 2048
	BLEPoolSize      = 256
	BLEMsgID         = uint8(MaxOpCodeNumber + 10)
)

var globalBLEBufferPool *PacketBufferPool

// PacketBufferPool manages a preallocated pool of []byte buffers.
type PacketBufferPool struct {
	pool chan []byte
}

// NewPacketBufferPool creates a new buffer pool
func NewPacketBufferPool() *PacketBufferPool {
	p := &PacketBufferPool{
		pool: make(chan []byte, BLEPoolSize),
	}

	for i := 0; i < BLEPoolSize; i++ {
		p.pool <- make([]byte, MaxBLEPacketSize)
	}
	return p
}

func (p *PacketBufferPool) Acquire() []byte {
	return <-p.pool
}

// Release returns a buffer to the pool.
// Requirement: buf's capacity must equal MaxBLEPacketSize, otherwise it will be discarded to avoid polluting the pool.
func (p *PacketBufferPool) Release(buf []byte) {
	if cap(buf) == MaxBLEPacketSize {
		p.pool <- buf[:MaxBLEPacketSize]
	}
}
