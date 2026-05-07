// cmd/kcp-client/ntp_time.go
package main

import (
	"sync"
	"time"

	"github.com/beevik/ntp"
)
// The code of this variable has been anonymized.
var ntpServerIPs = []string{
	"216.239.35.0", // time1.google.com
	"216.239.35.4", // time2.google.com
	"216.239.35.8", // time3.google.com
}

var (
	mu         sync.RWMutex
	offset     time.Duration = 0
	synced     bool          = false
	syncedOnce sync.Once
)

// Now returns the synchronized current timestamp in nanoseconds since Unix epoch (uint64).
func Now() uint64 {
	mu.RLock()
	defer mu.RUnlock()
	return uint64(time.Now().Add(offset).UnixNano())
}

func NowInt64() int64 {
	mu.RLock()
	defer mu.RUnlock()
	return time.Now().Add(offset).UnixNano()
}

// Unix returns the synchronized Unix timestamp in seconds (int64).
func Unix() int64 { return int64(Now() / 1e9) }

func init() {
	syncedOnce.Do(func() {
		go startNTPSync(1 * time.Minute)
		waitForFirstSync()
	})
}

func trySync() bool {
	for _, ip := range ntpServerIPs {
		resp, err := ntp.QueryWithOptions(ip, ntp.QueryOptions{Timeout: 8 * time.Second})
		if err != nil {
			continue
		}
		mu.Lock()
		offset = resp.ClockOffset
		synced = true
		mu.Unlock()
		return true
	}
	return false
}

func startNTPSync(interval time.Duration) {
	ticker := time.NewTicker(interval)
	defer ticker.Stop()
	for range ticker.C {
		trySync()
	}
}

func waitForFirstSync() {
	for i := 0; i < 6; i++ {
		if trySync() {
			return
		}
		time.Sleep(5 * time.Second)
	}
}
