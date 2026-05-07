package main

import (
	"fmt"
	"sync"
)

const emptyValue = 0xFF // Backward-compat sentinel for callers; not used internally.

var globalWaitLockQueueCounts *CounterMap

type WaitLockQueue struct {
	chans []chan uint8
	once  sync.Once
}

func NewWaitLockQueue() *WaitLockQueue {
	chans := make([]chan uint8, 256)
	for i := range chans {
		chans[i] = make(chan uint8, 1) // Buffered to avoid blocking Set().
	}

	return &WaitLockQueue{
		chans: chans,
	}
}

// Set sets the value at position pos.
// If pos is out of bounds, it does nothing.
// If a value is already present (not yet Get), it will be replaced.
func (wq *WaitLockQueue) Set(pos, value uint8) {
	if int(pos) >= len(wq.chans) {
		return
	}

	ch := wq.chans[pos]

	// Drain the channel in a non-blocking way (drop the previous value if any).
	select {
	case <-ch:
	default:
		// Channel is already empty.
	}

	// Now the channel is guaranteed to be empty, so sending will not block.
	ch <- value
}

// Get blocks until a value is available at pos, then returns it.
// If pos is out of bounds, it returns emptyValue immediately (or you could panic/error).
func (wq *WaitLockQueue) Get(pos uint8) uint8 {
	value := <-wq.chans[pos]
	LogInfo(fmt.Sprintf("value:%d\n", value))
	return value
}

type CounterMap struct {
	mu     [256]sync.Mutex
	values [256]uint8 // Lock counter: >0 means "locked".
	msgids [256]uint8 // msgid associated with the current lock state.
}

func NewCounterMap() *CounterMap {
	return &CounterMap{}
}

// Add increments the counter and sets the msgid (thread-safe).
func (m *CounterMap) Add(key uint8, msgid uint8) {
	m.mu[key].Lock()
	if m.values[key] < 255 {
		m.values[key]++
	}
	m.msgids[key] = msgid
	m.mu[key].Unlock()
}

func (m *CounterMap) Get(key uint8) uint8 {
	m.mu[key].Lock()
	v := m.values[key]
	m.mu[key].Unlock()
	return v
}

// Clear resets the counter (i.e. releases the lock).
func (m *CounterMap) Clear(key uint8) {
	m.mu[key].Lock()
	m.values[key] = 0
	// msgids[key] is left as-is; IsLocked only uses it when values[key] != 0.
	m.mu[key].Unlock()
}

func (m *CounterMap) GetAndClear(key uint8) uint8 {
	m.mu[key].Lock()
	v := m.values[key]
	m.values[key] = 0
	m.msgids[key] = 0xff
	m.mu[key].Unlock()
	return v
}

func (m *CounterMap) IsLocked(key uint8) uint8 {
	m.mu[key].Lock()
	if m.values[key] != 0 {
		msgid := m.msgids[key]
		if m.values[key] < 255 {
			m.values[key]++
		}
		m.mu[key].Unlock()
		return msgid
	}
	m.mu[key].Unlock()
	return 0xFF
}
