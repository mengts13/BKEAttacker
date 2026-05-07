package com.example.bkeattacker;

/**
 * JNI Wrapper Class: Interacts with the KCP client written in Go
 * <p>
 * All methods are static native and require loading libgo_kcp_client.so (or .dylib / .dll)
 */
public class DupClientManager {

// =============== Initialization and Configuration ===============

    /**
     * Sets the server address (e.g., "192.168.1.100:8000")
     */
    @Deprecated
    public static native void SetServerAddr(String addr);

    /**
     * Sets the token file directory (for reading long-token / temp-token)
     */
    public static native void setTokenFileDir(String path);

    /**
     * Sets the message server address
     */
    public static native void SetMessageServerAddr(String addr);

    /**
     * Sets the stream server address
     */
    public static native void SetStreamServerAddr(String addr);

// =============== Login and Pairing ===============

    /**
     * User login
     */
    public static native int Login(String account, String password);

    /**
     * Registers a pair (role is a single character, e.g., 'A' or 'B')
     */
    public static native int RegisterPair(String role);

    /**
     * Registers self role (single character)
     */
    public static native int RegisterSelf(String role);

    /**
     * Starts the client (**no parameters**, role should be set previously via RegisterPair or RegisterSelf)
     */
    public static native int StartClient();  // ← Note: No String parameter!

// =============== PIN Related Operations ===============

    /**
     * Sends a PIN upload request
     */
    public static native int sendPin(String pin);

    /**
     * Fetches the other party's temp token via PIN
     */
    public static native int fetchPinToken(String pin);

    /**
     * Reads the locally saved PIN; the backend will generate a PIN code
     */
    public static native String readPin();

    /**
     * Reads the temporary token obtained via PIN, used for scanning
     */
    public static native String readPinTempToken();

// =============== Global State Management ===============

    /**
     * Sets the global pair token, passing the scanned string to Go for decoding, returns 0 for success
     */
    // ⚠️ Correction: C returns jint, Java must be int (originally void)
    public static native int SetGlobalPairToken(String token);

    /**
     * Sets the global UUID map (Base64 encoded JSON string)
     */
    public static native int SetGlobalUUIDMap(String b64Str); // ← New

    /**
     * Cancels the current pairing
     */
    public static native int CancelPair(); // ← New

// =============== Connection Management ===============

    /**
     * Reconnects to the server
     */
    public static native int reconnectToServer();

    /**
     * Reads the timestamp (Base64 encoded NTP timestamp)
     */
    public static native String readTimeStamp(); // ← New

    /**
     * Reads the current timestamp
     */
    public static native long ReadCurrentTimestamp();

// =============== Advertising and Service Data Transfer ===============

    public static native int SendMessageData(String advData);

    /**
     * Reads received message data (Base64 or JSON string)
     */
    public static native String ReadMessageData();

    // Declare native methods
    public static native boolean WaitResponseLock(byte op);

    public static native void WaitResponseUnlock(byte op);

// =============== Static Initialization ===============

    static {
        System.loadLibrary("go_kcp_client");
        System.loadLibrary("kcp_client");
    }
}
