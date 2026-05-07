package main

import "io"

// Global channel used to deliver raw bytes (e.g. notifications from the BLE service); unbuffered for synchronization.
var serviceChan = make(chan []byte)

// InsertServiceData sends raw bytes into serviceChan.
// It blocks until a consumer receives the data.
// Note: sending on a closed channel will panic.
func InsertServiceData(data []byte) error {
	// Create a copy to prevent the caller from mutating the underlying array after send.
	// A slice header is passed through the channel; without copying, the backing array could be modified concurrently.
	dataCopy := make([]byte, len(data))
	copy(dataCopy, data)

	serviceChan <- dataCopy
	return nil
}

// PopServiceData receives one byte slice from serviceChan.
// It blocks until data arrives.
// If the channel is closed, it returns nil and io.EOF.
func PopServiceData() ([]byte, error) {
	data, ok := <-serviceChan
	if !ok {
		return nil, io.EOF
	}
	return data, nil
}