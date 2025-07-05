package com.example.fltesttool;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;

import java.util.UUID;

/**
 * Interface for managing a GATT Client.
 */
public interface GATTClientInterface {

    /**
     * Start scanning for BLE devices.
     */
    void startScan();

    /**
     * Stop scanning for BLE devices.
     */
    void stopScan();

    /**
     * Connect to a specific BLE device.
     *
     * @param device The Bluetooth device to connect to.
     */
    void connectToDevice(BluetoothDevice device);

    /**
     * Disconnect from the currently connected BLE device.
     */
    void disconnect();

    /**
     * Discover services offered by the connected GATT Server.
     */
    void discoverServices();

    /**
     * Read the value of a characteristic.
     *
     * @param serviceUUID         The UUID of the service containing the characteristic.
     * @param characteristicUUID  The UUID of the characteristic to read.
     */
    void readCharacteristic(UUID serviceUUID, UUID characteristicUUID);

    /**
     * Write a value to a characteristic.
     *
     * @param serviceUUID         The UUID of the service containing the characteristic.
     * @param characteristicUUID  The UUID of the characteristic to write.
     * @param value               The data to write.
     */
    void writeCharacteristic(UUID serviceUUID, UUID characteristicUUID, byte[] value);

    /**
     * Enable notifications or indications for a characteristic.
     *
     * @param serviceUUID         The UUID of the service containing the characteristic.
     * @param characteristicUUID  The UUID of the characteristic to enable notifications for.
     * @param enable              True to enable notifications/indications, false to disable.
     */
    void enableNotifications(UUID serviceUUID, UUID characteristicUUID, boolean enable);

    /**
     * Read the value of a descriptor.
     *
     * @param serviceUUID         The UUID of the service containing the descriptor.
     * @param characteristicUUID  The UUID of the characteristic containing the descriptor.
     * @param descriptorUUID      The UUID of the descriptor to read.
     */
    void readDescriptor(UUID serviceUUID, UUID characteristicUUID, UUID descriptorUUID);

    /**
     * Write a value to a descriptor.
     *
     * @param serviceUUID         The UUID of the service containing the descriptor.
     * @param characteristicUUID  The UUID of the characteristic containing the descriptor.
     * @param descriptorUUID      The UUID of the descriptor to write.
     * @param value               The data to write.
     */
    void writeDescriptor(UUID serviceUUID, UUID characteristicUUID, UUID descriptorUUID, byte[] value);

    /**
     * Check if the GATT Client is connected to a device.
     *
     * @return True if connected, false otherwise.
     */
    boolean isConnected();
}