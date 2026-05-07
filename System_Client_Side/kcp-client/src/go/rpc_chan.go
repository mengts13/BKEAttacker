package main

import "io"

// Global channel used to deliver parsed GattRpc; unbuffered to keep producer/consumer synchronized.
var gattRpcChan = make(chan *GattRpc)

// InsertRpc parses raw bytes into a GattRpc and sends it to the global channel.
// It blocks on the unbuffered channel until a receiver is ready.
// Note: writing to a closed channel will panic; manage channel lifetime carefully.
func InsertRpc(data []byte) error {
	// Input format: AA opcode msgID payload.
	// ParseRpcPacket expects: AA opcode payload (msgID stripped).
	rpc, err := ParseRpcPacket(append(data[0:2], data[3:]...))
	if err != nil {
		LogInfo(err.Error())
		return err
	}

	// Send to the channel.
	// Note: sending on a closed channel will panic; manage channel lifetime carefully.
	gattRpcChan <- rpc
	return nil
}

// PopRpc receives one GattRpc from the global channel.
// It blocks until data arrives.
// If the channel is closed, it returns nil and io.EOF.
func PopRpc() (*GattRpc, error) {
	rpc, ok := <-gattRpcChan
	if !ok {
		// Channel closed.
		return nil, io.EOF // Or return a custom error, e.g. errors.New("channel closed")
	}
	return rpc, nil
}
