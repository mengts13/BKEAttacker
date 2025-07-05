package com.example.fltesttool;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;

import java.util.UUID;

/**
 * Interface for managing a GATT Server.
 */
public interface GATTServerInterface {

    /**
     * Initialize and start the GATT Server.
     */
    void startGattServer();

    /**
     * Stop the GATT Server and release resources.
     */
    void stopGattServer();

    /**
     * Add a new service to the GATT Server.
     *
     * @param serviceUUID The UUID of the service to be added.
     * @param isPrimary   True if the service is primary, false if secondary.
     * @return The created BluetoothGattService.
     */
    BluetoothGattService addService(UUID serviceUUID, boolean isPrimary);

    /**
     * Add a characteristic to a specific service.
     *
     * @param serviceUUID         The UUID of the service.
     * @param characteristicUUID  The UUID of the characteristic to be added.
     * @param properties          The properties of the characteristic (e.g., READ, WRITE).
     * @param permissions         The permissions of the characteristic (e.g., PERMISSION_READ, PERMISSION_WRITE).
     * @return The created BluetoothGattCharacteristic.
     */
    BluetoothGattCharacteristic addCharacteristic(
            UUID serviceUUID,
            UUID characteristicUUID,
            int properties,
            int permissions
    );

    /**
     * Send a notification or indication to a connected client.
     *
     * @param device           The Bluetooth device to notify.
     * @param characteristic   The characteristic containing the data to send.
     * @param confirm          True for indication (requires confirmation), false for notification.
     */
    void notifyCharacteristicChanged(
            BluetoothDevice device,
            BluetoothGattCharacteristic characteristic,
            boolean confirm
    );

    /**
     * Handle incoming read requests from clients.
     *
     * @param device           The Bluetooth device making the request.
     * @param requestId        The ID of the request.
     * @param characteristic   The characteristic being read.
     * @param offset           The offset of the read operation.
     */
    void handleReadRequest(
            BluetoothDevice device,
            int requestId,
            BluetoothGattCharacteristic characteristic,
            int offset
    );

    /**
     * Handle incoming write requests from clients.
     *
     * @param device           The Bluetooth device making the request.
     * @param requestId        The ID of the request.
     * @param characteristic   The characteristic being written.
     * @param preparedWrite    True if this is a prepared write, false otherwise.
     * @param responseNeeded   True if a response is required, false otherwise.
     * @param offset           The offset of the write operation.
     * @param value            The data being written.
     */
    void handleWriteRequest(
            BluetoothDevice device,
            int requestId,
            BluetoothGattCharacteristic characteristic,
            boolean preparedWrite,
            boolean responseNeeded,
            int offset,
            byte[] value
    );

    /**
     * Check if the GATT Server is running.
     *
     * @return True if the GATT Server is running, false otherwise.
     */
    boolean isGattServerRunning();
}