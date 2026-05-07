package com.example.bkeattacker;

public class ClientStub {

    static {
        System.loadLibrary("go_kcp_client");
        System.loadLibrary("kcp_client");
    }

    // Native method: writeDescriptor
    public static native void writeDescriptor(
            String serviceUuid,
            String characteristicUuid,
            String descriptorUuid,
            byte[] data
    );

    // Native method: writeCharacteristic
    public static native void writeCharacteristic(
            String serviceUuid,
            String characteristicUuid,
            int writeType,
            byte[] data
    );

    // Native method: readCharacteristic
    public static native void readCharacteristic(
            String serviceUuid,
            String characteristicUuid
    );

    // Native method: readDescriptor
    public static native void readDescriptor(
            String serviceUuid,
            String characteristicUuid,
            String descriptorUuid
    );

    // Native method: connectGatt
    public static native void connectGatt(String deviceAddress);

    // Native method: setCharacteristicNotification
    public static native void setCharacteristicNotification(
            String serviceUuid,
            String characteristicUuid,
            int enable  // 1 = enable, 0 = disable
    );

    // Native method: goDiscoverService
    public static native void goDiscoverService();
}
