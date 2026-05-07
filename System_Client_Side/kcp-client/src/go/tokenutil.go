// cmd/kcp-client/tokenutil.go
package main

/*
#include <stdlib.h>
*/
import "C"

import (
	"crypto/rand"
	"crypto/rsa"
	"encoding/json"
	"fmt"
	"log"
	"math/big"
	"os"
	"path/filepath"
	"strings"
	"sync"

	"github.com/golang-jwt/jwt/v5"
)

var publicKey *rsa.PublicKey
var (
	tokenFileDir string       = ""
	tokenDirMu   sync.RWMutex // Protects concurrent access to tokenFileDir.
)

const (
	longTokenStreamFilename  = "long-token-stream"
	longTokenMessageFilename = "long-token-message"
	tempTokenStreamFilename  = "temp-token-stream"
	tempTokenMessageFilename = "temp-token-message"
)

// getTokenDir safely reads tokenFileDir (guarded by a read lock).
func getTokenDir() string {
	tokenDirMu.RLock()
	defer tokenDirMu.RUnlock()
	return tokenFileDir
}

// loadLongToken loads the long-token from tokenFileDir (stream/message mode).
func loadLongToken(isStream bool) string {
	dir := getTokenDir()
	if dir == "" {
		return ""
	}
	if isStream {
		spath := filepath.Join(dir, longTokenStreamFilename)
		b, err := os.ReadFile(spath)
		if err != nil {
			return ""
		}
		return strings.TrimSpace(string(b))

	} else {
		mpath := filepath.Join(dir, longTokenMessageFilename)
		b, err := os.ReadFile(mpath)
		if err != nil {
			return ""
		}
		return strings.TrimSpace(string(b))
	}
}

// loadTempToken returns the current temp token from memory (no file I/O; reads global state).
func loadTempToken(isStream bool) string {
	// dir := getTokenDir()
	// if dir == "" {
	// 	return ""
	// }
	// path := filepath.Join(dir, tempTokenFilename)
	// b, err := os.ReadFile(path)
	// if err != nil {
	// 	return ""
	// }
	// Historical implementation: temp token was read from a file; current version reads from memory.
	if isStream {
		globalStreamClient.tokenMutex.Lock()
		temptoken := globalStreamClient.tempToken
		globalStreamClient.tokenMutex.Unlock()
		return temptoken
	} else {
		globalMessageClient.tokenMutex.Lock()
		temptoken := globalMessageClient.tempToken
		globalMessageClient.tokenMutex.Unlock()
		return temptoken
	}

}

// saveTokens saves the long token to a file in tokenFileDir (temp tokens are not saved).
func saveTokens(longJWT string, isStream bool) error {
	dir := getTokenDir()
	if dir == "" {
		return fmt.Errorf("tokenFileDir is not set; cannot save token")
	}
	var longPath string
	if isStream {
		longPath = filepath.Join(dir, longTokenStreamFilename)
	} else {
		longPath = filepath.Join(dir, longTokenMessageFilename)
	}

	// tempPath := filepath.Join(dir, tempTokenFilename)

	if err := os.WriteFile(longPath, []byte(longJWT), 0600); err != nil {
		return fmt.Errorf("failed to save long token: %w", err)
	}
	// if err := os.WriteFile(tempPath, []byte(tempJWT), 0600); err != nil {
	// 	return fmt.Errorf("failed to save temp token: %w", err)
	// }
	return nil
}

func initKeys() {
	// Hard-coded public key in PEM format (keep newlines as-is).
	publicPEM := `-----BEGIN PUBLIC KEY-----
-----END PUBLIC KEY-----`
	var err error
	publicKey, err = jwt.ParseRSAPublicKeyFromPEM([]byte(publicPEM))
	if err != nil {
		log.Fatal("failed to parse public key PEM:", err)
	}
	log.Printf("public key loaded, modulus first 8 bytes: %x", publicKey.N.Bytes()[:8])
}

func randomString(n int) string {
	const letters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
	ret := make([]byte, n)
	for i := range ret {
		num, _ := rand.Int(rand.Reader, big.NewInt(int64(len(letters))))
		ret[i] = letters[num.Int64()]
	}
	return string(ret)
}

func encryptLoginJSON(account, password string) ([]byte, error) {
	reqTime := Unix() // Uses NTP-adjusted time.
	validUntil := reqTime + 60

	data := map[string]interface{}{
		"account":     account,
		"password":    password,
		"req_time":    reqTime,
		"valid_until": validUntil,
		"field1":      randomString(10),
		"field2":      randomString(8),
		"field3":      randomString(12),
		"field4":      randomString(6),
		"field5":      randomString(7),
	}

	jsonBytes, _ := json.Marshal(data)
	encrypted, err := rsa.EncryptPKCS1v15(rand.Reader, publicKey, jsonBytes)
	return encrypted, err
}

func extractAndVerifyTempFromLong(longJWT string) (string, error) {
	longToken, err := jwt.Parse(longJWT, func(t *jwt.Token) (interface{}, error) {
		if _, ok := t.Method.(*jwt.SigningMethodRSA); !ok {
			return nil, fmt.Errorf("unexpected signing method: %v", t.Header["alg"])
		}
		return publicKey, nil
	})
	if err != nil {
		return "", fmt.Errorf("long-token signature is invalid: %v", err)
	}
	if !longToken.Valid {
		return "", fmt.Errorf("long-token is invalid (possibly expired)")
	}

	claims, ok := longToken.Claims.(jwt.MapClaims)
	if !ok {
		return "", fmt.Errorf("long-token claims have an unexpected format")
	}

	tempStr, exists := claims["temp_token"].(string)
	if !exists || tempStr == "" {
		return "", fmt.Errorf("long-token is missing the temp_token field")
	}

	tempToken, err := jwt.Parse(tempStr, func(t *jwt.Token) (interface{}, error) {
		if _, ok := t.Method.(*jwt.SigningMethodRSA); !ok {
			return nil, fmt.Errorf("temp-token has an unexpected signing method")
		}
		return publicKey, nil
	})
	if err != nil {
		return "", fmt.Errorf("temp-token signature is invalid: %v", err)
	}
	if !tempToken.Valid {
		return "", fmt.Errorf("temp-token is expired or invalid")
	}

	return tempStr, nil
}

func isTokenExpired(tokenStr string) bool {
	token, err := jwt.Parse(tokenStr, func(t *jwt.Token) (interface{}, error) { return publicKey, nil })
	if err != nil {
		return true
	}
	if claims, ok := token.Claims.(jwt.MapClaims); ok {
		if expFloat, ok := claims["exp"].(float64); ok {
			return Unix() > int64(expFloat)
		}
	}
	return true
}

func getExpTime(tokenStr string) int64 {
	token, _ := jwt.Parse(tokenStr, func(t *jwt.Token) (interface{}, error) { return publicKey, nil })
	if claims, ok := token.Claims.(jwt.MapClaims); ok {
		if expFloat, ok := claims["exp"].(float64); ok {
			return int64(expFloat)
		}
	}
	return 0
}

//export SetTokenFileDir
func SetTokenFileDir(cpath *C.char) {
	if cpath == nil {
		tokenFileDir = ""
		return
	}
	goPath := C.GoString(cpath)
	tokenFileDir = goPath
}
