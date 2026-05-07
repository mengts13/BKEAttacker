package com.example.bkeattacker;

public class ServerStub {

    // Load native library (assuming the .so file compiled by Go is libgo_kcp_client.so)
    static {
        System.loadLibrary("go_kcp_client"); // Note: remove the "lib" prefix and ".so"
        System.loadLibrary("kcp_client");
    }

    /**
     * Send the response for reading a characteristic value
     */
    public static native void sendReadCharacteristicResponse(
            int status,
            byte[] value
    );

    /**
     * Send the response for writing a characteristic value
     */
    public static native void sendWriteCharacteristicResponse(
            int status,
            byte[] value
    );

    /**
     * Send the response for reading a descriptor
     */
    public static native void sendReadDescriptorResponse(
            int status,
            byte[] value
    );

    /**
     * Send the response for writing a descriptor
     */
    public static native void sendWriteDescriptorResponse(
            int status,
            byte[] value
    );

    /**
     * Notify the client that the characteristic value has changed (Notify/Indicate)
     */
    public static native void notifyCharacteristicChanged(
            String serviceUuid,
            String characteristicUuid,
            byte[] data
    );

    /**
     * Get the MTU size of the current connection
     *
     * @return MTU value (e.g., 23, 517, etc.)
     */
    public static native int getMTU();
}