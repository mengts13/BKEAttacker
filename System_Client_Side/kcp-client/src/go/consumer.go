package main

/*
#include <stdlib.h>
*/
import "C"
import (
	"encoding/json"
	"unsafe"
)

//export GoPopGattRpcJson
func GoPopGattRpcJson() *C.char {
	rpc, err := PopRpc() // May block until a RPC is available.
	if err != nil {
		// Return an empty JSON object or an error.
		return C.CString(`{"error":"channel closed or empty"}`)
	}

	b, err := json.Marshal(rpc)
	if err != nil {
		return C.CString(`{"error":"marshal failed"}`)
	}

	return C.CString(string(b))
}

//export GoPopServiceDataString
func GoPopServiceDataString() *C.char {
	data, err := PopServiceData() // Blocks until data is available.
	if err != nil {
		// The channel is closed or an error occurred; return an empty string (or an error marker).
		return C.CString("")
	}

	// Convert []byte directly to a Go string (assuming UTF-8 encoding).
	// Note: If the data is not text (e.g., binary), the conversion still succeeds, but it may appear garbled on the Java side.
	str := string(data)

	return C.CString(str)
}

//export GoFreeCString
func GoFreeCString(s *C.char) {
	C.free(unsafe.Pointer(s))
}
