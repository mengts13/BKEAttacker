package main

/*
#include <stdlib.h>
*/
import "C"
import (
	"encoding/base64"
	"encoding/json"
	"fmt"
)

var (
	role              string            = "U"
	MessageServerAddr string            = "127.0.0.1:12345"
	StreamServerAddr  string            = "127.0.0.1:12346"
	useNTP            bool              = true
	globalPairToken   map[string]string = map[string]string{}
	globalUUIDMap     *BLEMapper        = NewBLEMapper()
	pinCode           string            = ""
)

//export SetGlobalPairToken
func SetGlobalPairToken(token *C.char) C.int {
	if token == nil {
		LogInfo(fmt.Sprintf("SetGlobalPairToken: received nil token"))
		return C.int(-1)
	}

	encodedStr := C.GoString(token)
	if encodedStr == "" {
		LogInfo(fmt.Sprintf("SetGlobalPairToken: received empty token string"))
		return C.int(-2)
	}

	// Base64 decode
	compressedData, err := base64.StdEncoding.DecodeString(encodedStr)
	if err != nil {
		LogInfo(fmt.Sprintf("SetGlobalPairToken: failed to base64 decode: %v", err))
		return C.int(-3)
	}

	// Decompress
	jsonData, err := decompress(compressedData)
	if err != nil {
		LogInfo(fmt.Sprintf("SetGlobalPairToken: failed to decompress: %v", err))
		return C.int(-4)
	}

	// Unmarshal JSON
	var newTokenMap map[string]string
	if err := json.Unmarshal(jsonData, &newTokenMap); err != nil {
		LogInfo(fmt.Sprintf("SetGlobalPairToken: failed to unmarshal JSON: %v", err))
		return C.int(-5)
	}

	// Update global state
	// If thread-safety is needed, acquire lock here
	globalPairToken = newTokenMap

	LogInfo(fmt.Sprintf("SetGlobalPairToken: successfully updated globalPairToken"))
	return C.int(0) // success
}

//export SetMessageServerAddr
func SetMessageServerAddr(addr *C.char) {
	if addr == nil {
		MessageServerAddr = ""
	} else {
		MessageServerAddr = C.GoString(addr)
	}
}

//export SetStreamServerAddr
func SetStreamServerAddr(addr *C.char) {
	if addr == nil {
		StreamServerAddr = ""
	} else {
		StreamServerAddr = C.GoString(addr)
	}
}

//export SetGlobalUUIDMap
func SetGlobalUUIDMap(b64str *C.char) C.int {
	B64String := C.GoString(b64str)
	if err := globalUUIDMap.InitFromBase64JSON(B64String); err != nil {
		LogInfo(fmt.Sprintf("SetGlobalUUIDMap: failed to initialize UUID map: %v", err))
		return C.int(-1)
	}
	LogInfo("SetGlobalUUIDMap: successfully initialized UUID map")
	return C.int(0)
}
