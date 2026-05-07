// response_queue.go

package main

import (
	"sync"
	"sync/atomic"
	"time"
)

// ResponseQueue is a cyclic response queue with timeout support.
// The slot ID is one byte (0~255).
type ResponseQueue struct {
	maxNum int
	delay  time.Duration

	states   []int32       // 0 = free, 1 = allocated
	channels []chan []byte // Per-slot channel.
	popTimes []int64       // Last Pop timestamp (UnixNano).

	mu     sync.Mutex
	stopCh chan struct{}
	wg     sync.WaitGroup
}

// NewResponseQueue creates a new ResponseQueue.
// maxNum must be > 0 and <= 256 (since the ID is a single byte).
func NewResponseQueue(maxNum int, delay time.Duration) *ResponseQueue {
	if maxNum <= 0 || maxNum > 256 {
		panic("maxNum must be in range (0, 256]")
	}
	rq := &ResponseQueue{
		maxNum:   maxNum,
		delay:    delay,
		states:   make([]int32, maxNum),
		channels: make([]chan []byte, maxNum),
		popTimes: make([]int64, maxNum),
		stopCh:   make(chan struct{}),
	}
	rq.wg.Add(1)
	go rq.timeoutChecker()
	return rq
}

// Pop blocks until it successfully allocates a slot and returns its 1-byte ID (uint8).
// If no slot is free, it sleeps briefly and retries (never returns an error).
func (rq *ResponseQueue) Pop() uint8 {
	startOffset := 0
	for {
		// Try a full scan once.
		for i := 0; i < rq.maxNum; i++ {
			idx := (startOffset + i) % rq.maxNum
			if atomic.CompareAndSwapInt32(&rq.states[idx], 0, 1) {
				ch := make(chan []byte, 1) // Buffered to avoid blocking on timeout injection.
				rq.mu.Lock()
				rq.channels[idx] = ch
				rq.popTimes[idx] = time.Now().UnixNano()
				rq.mu.Unlock()
				return uint8(idx)
			}
		}

		// No free slots in this round; sleep briefly to reduce CPU usage.
		time.Sleep(time.Millisecond)

		// Rotate the start offset to improve fairness.
		startOffset = (startOffset + 1) % rq.maxNum
	}
}

// Read blocks until data is available for the given ID, reads it, and frees the slot.
func (rq *ResponseQueue) Read(id uint8) []byte {
	idx := int(id)
	if idx >= rq.maxNum {
		panic("invalid ID: out of range [0, maxNum)")
	}

	rq.mu.Lock()
	ch := rq.channels[idx]
	rq.mu.Unlock()

	if ch == nil {
		panic("internal error: channel not initialized for allocated slot")
	}

	// Block until data is available.
	data := <-ch

	// Free the slot.
	atomic.StoreInt32(&rq.states[idx], 0)

	return data
}

// SubmitResponse submits response data to the channel for the given ID.
// It returns false if the ID is invalid, the slot is not allocated, or the slot has already been freed.
func (rq *ResponseQueue) SubmitResponse(id uint8, data []byte) bool {
	idx := int(id)
	if idx >= rq.maxNum {
		return false
	}

	if atomic.LoadInt32(&rq.states[idx]) != 1 {
		return false
	}

	rq.mu.Lock()
	ch := rq.channels[idx]
	rq.mu.Unlock()

	if ch == nil {
		return false
	}

	select {
	case ch <- data:
		return true
	default:
		return false // Channel is full (e.g. timeout signal has already been injected).
	}
}

// timeoutChecker runs in a background goroutine and periodically checks for timeouts.
func (rq *ResponseQueue) timeoutChecker() {
	defer rq.wg.Done()
	ticker := time.NewTicker(10 * time.Millisecond)
	defer ticker.Stop()

	for {
		select {
		case <-ticker.C:
			now := time.Now().UnixNano()
			for i := 0; i < rq.maxNum; i++ {
				if atomic.LoadInt32(&rq.states[i]) == 1 {
					rq.mu.Lock()
					popTime := rq.popTimes[i]
					ch := rq.channels[i]
					rq.mu.Unlock()

					if now-popTime > int64(rq.delay) {
						// Timed out: try injecting timeout signal 0x09.
						select {
						case ch <- []byte{0x09}:
							// Timeout signal injected successfully.
						default:
							// Channel is full (normal case; may have been filled by SubmitResponse).
						}
					}
				}
			}
		case <-rq.stopCh:
			return
		}
	}
}

// Stop stops the background goroutine for a graceful shutdown.
func (rq *ResponseQueue) Stop() {
	close(rq.stopCh)
	rq.wg.Wait()
}
