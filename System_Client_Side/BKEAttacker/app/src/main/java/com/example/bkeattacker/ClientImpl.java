package com.example.bkeattacker;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.content.Context;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

/**
 * Client-side implementation that consumes GATT RPC commands from Go layer via Consumer.PopGattRpc(),
 * and executes corresponding BluetoothGatt client operations (e.g., connect, read, write, notify).
 *
 * This class:
 * - Runs a background thread to poll Go-layer RPC events (blocking call).
 * - Parses JSON-formatted RPC packets.
 * - Performs BLE client operations using BluetoothGatt.
 * - Relies on GattClientCallback for service discovery and event handling.
 */


public class ClientImpl {
    private static final String TAG = "ClientImpl";

    private Context context;
    private BluetoothAdapter bluetoothAdapter;
    private ClientCallback gattCallback;
    private BluetoothGatt bluetoothGatt = null;
    private Map<String, BluetoothGattCharacteristic> characteristicMap = new HashMap<>();
    private volatile boolean isConnected = false;
    private volatile boolean servicesDiscovered = false;

    // Target device address (can be overridden)
    private String targetDeviceAddress = null;

    // Background thread control
    private volatile boolean running = false;
    private Thread rpcConsumerThread;

    @SuppressLint("MissingPermission")
    public ClientImpl(@NonNull Context context, @NonNull ClientCallback gattCallback) {
        this.context = context;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.gattCallback = gattCallback;
        this.gattCallback.setGattcharacteristicMap(characteristicMap);
    }

    // Optional: override target device address (e.g., for fixed target)
    public void setTargetDeviceAddress(String address) {
        this.targetDeviceAddress = address;
    }

    // --- Lifecycle: Start/Stop RPC consumer thread ---
    public void startRpcConsumer() {
        if (running) return;
        running = true;
        rpcConsumerThread = new Thread(() -> {
            while (running) {
                try {
                    String json = Consumer.PopGattRpc(); // blocking native call
                    if (json == null || json.isEmpty()) {
                        continue;
                    }
                    handleRpcJson(json);
                } catch (Exception e) {
                    Log.e(TAG, "Error in RPC consumer thread", e);
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }, "ClientRpcConsumerThread");
        rpcConsumerThread.start();
    }

    @SuppressLint("MissingPermission")
    public void stopRpcConsumer() {
        running = false;
        if (rpcConsumerThread != null) {
            rpcConsumerThread.interrupt();
            try {
                rpcConsumerThread.join(1000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        // Optionally disconnect
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
            bluetoothGatt = null;
            isConnected = false;
            servicesDiscovered = false;
            characteristicMap.clear();
        }
    }

    // --- JSON Parsing and Dispatch ---
    @SuppressLint("MissingPermission")
    private void handleRpcJson(@NonNull String json) {
        try {
            JSONObject root = new JSONObject(json);
            if (root.has("error")) {
                Log.w(TAG, "Go RPC error: " + root.getString("error"));
                return;
            }

            int opcode = root.getInt("Opcode");
            JSONObject params = root.getJSONObject("Params");
            log("Go RPC json: "+ root.toString());

            switch (opcode) {
                case 0x10: // OpConnectGatt
                    handleConnectGatt(params);
                    break;
                case 0x11: // OpWriteCharacteristic
                    handleWriteCharacteristic(params);
                    break;
                case 0x12: // OpReadCharacteristic
                    handleReadCharacteristic(params);
                    break;
                case 0x13: // OpWriteDescriptor
                    handleWriteDescriptor(params);
                    break;
                case 0x14: // OpReadDescriptor
                    handleReadDescriptor(params);
                    break;
                case 0x16: // OpSetCharacteristicNotification
                    handleSetCharacteristicNotification(params);
                    break;
                case 0x17: // OpDiscoverService
                    handleDiscoverService(params);
                    break;
                default:
                    Log.w(TAG, "Unknown client opcode: 0x" + Integer.toHexString(opcode));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse RPC JSON: " + json, e);
        }
    }

    // --- Handlers for Client → Server RPCs ---

    @SuppressLint("MissingPermission")
    private void handleConnectGatt(JSONObject params) {
        String deviceAddress = params.optString("deviceAddress", null);
        if (deviceAddress == null) {
            Log.e(TAG, "OpConnectGatt: missing deviceAddress");
            return;
        }

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Log.e(TAG, "Bluetooth adapter not available or disabled");
            return;
        }

        if (isConnected && bluetoothGatt != null) {
            Log.w(TAG, "Already connected. Disconnecting first...");
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
            isConnected = false;
            servicesDiscovered = false;
            characteristicMap.clear();
        }

        BluetoothDevice device;
        try {
            device = bluetoothAdapter.getRemoteDevice(deviceAddress);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Invalid Bluetooth address: " + deviceAddress, e);
            return;
        }

        log("Connecting to " + device.getAddress());
        isConnected = false;
        servicesDiscovered = false;
        characteristicMap.clear();

        BluetoothGatt gatt = device.connectGatt(context, false, gattCallback);
        if (gatt != null) {
            this.bluetoothGatt = gatt;
            isConnected = true;
            // Note: servicesDiscovered will be set by GattClientCallback
        } else {
            Log.e(TAG, "connectGatt failed immediately for " + deviceAddress);
        }
    }

    @SuppressLint("MissingPermission")
    private void handleDiscoverService(JSONObject params) {
        // In Android, service discovery is automatic after connect.
        // But if needed, we can trigger it (though usually unnecessary).
        if (!ensureConnected("discoverServices")) return;
        Log.d(TAG, "Triggering service discovery (if not already done)");
        // Typically, GattClientCallback.onServicesDiscovered handles this.
        // No explicit action needed here unless re-discovery is required.
    }

    @SuppressLint("MissingPermission")
    private void handleReadCharacteristic(JSONObject params) {
        if (!ensureConnectedAndServicesDiscovered("readCharacteristic")) return;

        String serviceUuid = params.optString("serviceUuid");
        String charUuid = params.optString("characteristicUuid");

        if (serviceUuid == null || charUuid == null) {
            Log.e(TAG, "Missing serviceUuid or characteristicUuid in readCharacteristic");
            return;
        }

        BluetoothGattCharacteristic characteristic = characteristicMap.get(serviceUuid + "_" + charUuid);
        if (characteristic == null) {
            Log.e(TAG, "Characteristic not found: " + serviceUuid + "_" + charUuid);
            return;
        }

        if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) == 0) {
            Log.e(TAG, "Characteristic does not support READ: " + charUuid);
            return;
        }

        boolean success = bluetoothGatt.readCharacteristic(characteristic);
        log("readCharacteristic called, success=" + success);
    }

    @SuppressLint("MissingPermission")
    private void handleWriteCharacteristic(JSONObject params) {
        if (!ensureConnectedAndServicesDiscovered("writeCharacteristic")) return;

        String serviceUuid = params.optString("serviceUuid");
        String charUuid = params.optString("characteristicUuid");
        int writeType = params.optInt("writeType", BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        String dataB64 = params.optString("data", null);

        if (serviceUuid == null || charUuid == null || dataB64 == null) {
            Log.e(TAG, "Missing required fields in writeCharacteristic");
            return;
        }

        BluetoothGattCharacteristic characteristic = characteristicMap.get(serviceUuid + "_" + charUuid);
        if (characteristic == null) {
            Log.e(TAG, "Characteristic not found: " + serviceUuid + "_" + charUuid);
            return;
        }

        int properties = characteristic.getProperties();
        boolean supportsWrite = (properties & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0;
        boolean supportsWriteNoResponse = (properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0;

        if (writeType == BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT && !supportsWrite) {
            Log.e(TAG, "Characteristic does not support WRITE (with response): " + charUuid);
            return;
        }
        if (writeType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE && !supportsWriteNoResponse) {
            Log.e(TAG, "Characteristic does not support WRITE_NO_RESPONSE: " + charUuid);
            return;
        }

        byte[] data = Base64.decode(dataB64, Base64.NO_WRAP);
        characteristic.setValue(data);
        characteristic.setWriteType(writeType);

        boolean success = bluetoothGatt.writeCharacteristic(characteristic);
        log("writeCharacteristic called, type=" + writeType + ", success=" + success);
    }

    @SuppressLint("MissingPermission")
    private void handleReadDescriptor(JSONObject params) {
        if (!ensureConnectedAndServicesDiscovered("readDescriptor")) return;

        String serviceUuid = params.optString("serviceUuid");
        String charUuid = params.optString("characteristicUuid");
        String descUuidStr = params.optString("descriptorUuid");

        if (serviceUuid == null || charUuid == null || descUuidStr == null) {
            Log.e(TAG, "Missing UUIDs in readDescriptor");
            return;
        }

        BluetoothGattCharacteristic characteristic = characteristicMap.get(serviceUuid + "_" + charUuid);
        if (characteristic == null) {
            Log.e(TAG, "Parent characteristic not found: " + serviceUuid + "_" + charUuid);
            return;
        }

        UUID descUuid;
        try {
            descUuid = UUID.fromString(descUuidStr);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Invalid descriptor UUID: " + descUuidStr, e);
            return;
        }

        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(descUuid);
        if (descriptor == null) {
            Log.e(TAG, "Descriptor not found: " + descUuidStr);
            return;
        }

        boolean success = bluetoothGatt.readDescriptor(descriptor);
        log("readDescriptor called, success=" + success);
    }

    @SuppressLint("MissingPermission")
    private void handleWriteDescriptor(JSONObject params)  {
        if (!ensureConnectedAndServicesDiscovered("writeDescriptor")) return;

        String serviceUuid = params.optString("serviceUuid");
        String charUuid = params.optString("characteristicUuid");
        String descUuidStr = params.optString("descriptorUuid");
        String dataB64 = params.optString("data", null);

        if (serviceUuid == null || charUuid == null || descUuidStr == null || dataB64 == null) {
            Log.e(TAG, "Missing fields in writeDescriptor");
            return;
        }

        BluetoothGattCharacteristic characteristic = characteristicMap.get(serviceUuid + "_" + charUuid);
        if (characteristic == null) {
            Log.e(TAG, "Parent characteristic not found: " + serviceUuid + "_" + charUuid);
            return;
        }

        UUID descUuid;
        try {
            descUuid = UUID.fromString(descUuidStr);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Invalid descriptor UUID: " + descUuidStr, e);
            return;
        }

        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(descUuid);
        if (descriptor == null) {
            Log.e(TAG, "Descriptor not found: " + descUuidStr);
            return;
        }

        byte[] data = Base64.decode(dataB64, Base64.NO_WRAP);
        if (!descriptor.setValue(data)) {
            Log.w(TAG, "descriptor.setValue() failed");
        }

        boolean success = bluetoothGatt.writeDescriptor(descriptor);
        log("writeDescriptor called, success=" + success);
    }

    @SuppressLint("MissingPermission")
    private void handleSetCharacteristicNotification(JSONObject params) {
        if (!ensureConnectedAndServicesDiscovered("setCharacteristicNotification")) return;

        String serviceUuid = params.optString("serviceUuid");
        String charUuid = params.optString("characteristicUuid");
        boolean enable = params.optBoolean("enable");

        if (serviceUuid == null || charUuid == null) {
            Log.e(TAG, "Missing UUIDs in setCharacteristicNotification");
            return;
        }

        BluetoothGattCharacteristic characteristic = characteristicMap.get(serviceUuid + "_" + charUuid);
        if (characteristic == null) {
            Log.e(TAG, "Characteristic not found: " + serviceUuid + "_" + charUuid);
            return;
        }

        boolean success = bluetoothGatt.setCharacteristicNotification(characteristic, enable);
        log("setCharacteristicNotification(" + enable + ") called, success=" + success);
    }

    // --- Helpers ---
    private boolean ensureConnected(String operation) {
        if (!isConnected || bluetoothGatt == null) {
            Log.e(TAG, "Cannot perform " + operation + ": not connected");
            return false;
        }
        return true;
    }

    private boolean ensureConnectedAndServicesDiscovered(String operation) {
        if (!ensureConnected(operation)) return false;
        if (characteristicMap.isEmpty()) {
            Log.e(TAG, "Cannot perform " + operation + ": services not discovered yet");
            return false;
        }
        return true;
    }

    // —————— Logging ——————
    private void log(String message) {
        Log.d(TAG, message);
        WebLogManager.get().log(message, WebLogManager.LOG_SEND);
    }
}