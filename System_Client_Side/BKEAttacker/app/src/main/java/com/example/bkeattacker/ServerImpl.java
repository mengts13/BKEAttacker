package com.example.bkeattacker;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattDescriptor;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.example.bkeattacker.responseData.*;

/**
 * Server-side implementation that consumes GATT RPC commands from Go layer via Consumer.PopGattRpc(),
 * and executes corresponding BluetoothGattServer responses or notifications.
 *
 * This class:
 * - Runs a background thread to poll Go-layer RPC events (blocking call).
 * - Parses JSON-formatted RPC packets.
 * - Uses pre-stored request context (e.g., device, requestId, offset) to send BLE responses.
 * - Handles characteristic notifications to subscribed clients.
 */
public class ServerImpl {
    private static final String TAG = "ServerImpl";

    // BLE server and state
    private BluetoothGattServer bluetoothGattServer;
    private Map<String, BluetoothDevice> connectedDevices; // Map<DeviceAddress, BluetoothDevice>
    private Map<String, BluetoothGattCharacteristic> characteristicMap; // Map<ServiceUUID_CharacteristicUUID, BluetoothGattCharacteristic>
    private HashMap<BluetoothDevice, byte[]> clientSubscriptions; // Notification/indication state per device

    // Pending request contexts (set when original GATT request arrives)
    private ReadCharacteristicResponse readCharacteristicResponse;
    private WriteCharacteristicResponse writeCharacteristicResponse;
    private ReadDescriptorResponse readDescriptorResponse;
    private WriteDescriptorResponse writeDescriptorResponse;

    // Background thread control
    private volatile boolean running = false;
    private Thread rpcConsumerThread;

    public ServerImpl(BluetoothGattServer gattServer) {
        this.bluetoothGattServer = gattServer;
    }

    // --- Setters for external state ---
    public void setConnectedDevices(Map<String, BluetoothDevice> connectedDevices) {
        this.connectedDevices = connectedDevices;
    }

    public void setCharacteristicMap(Map<String, BluetoothGattCharacteristic> characteristicMap) {
        this.characteristicMap = characteristicMap;
    }

    public void setClientSubscriptions(HashMap<BluetoothDevice, byte[]> clientSubscriptions) {
        this.clientSubscriptions = clientSubscriptions;
    }

    public void setReadCharacteristicResponse(ReadCharacteristicResponse readCharacteristicResponse) {
        this.readCharacteristicResponse = readCharacteristicResponse;
    }

    public void setWriteCharacteristicResponse(WriteCharacteristicResponse writeCharacteristicResponse) {
        this.writeCharacteristicResponse = writeCharacteristicResponse;
    }

    public void setReadDescriptorResponse(ReadDescriptorResponse readDescriptorResponse) {
        this.readDescriptorResponse = readDescriptorResponse;
    }

    public void setWriteDescriptorResponse(WriteDescriptorResponse writeDescriptorResponse) {
        this.writeDescriptorResponse = writeDescriptorResponse;
    }

    // --- Setters for BluetoothGattServer ---
    public void setBluetoothGattServer(BluetoothGattServer bluetoothGattServer) {
        this.bluetoothGattServer = bluetoothGattServer;
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
                        Thread.sleep(100);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }, "RpcConsumerThread");
        rpcConsumerThread.start();
    }

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
            if (opcode==0x50){
                return;
            }
            JSONObject params = root.getJSONObject("Params");

            switch (opcode) {
                case 0x20: // OpSendReadCharacteristicResponse
                    handleSendReadCharacteristicResponse(params);
                    break;
                case 0x21: // OpSendWriteCharacteristicResponse
                    handleSendWriteCharacteristicResponse(params);
                    break;
                case 0x22: // OpSendReadDescriptorResponse
                    handleSendReadDescriptorResponse(params);
                    break;
                case 0x23: // OpSendWriteDescriptorResponse
                    handleSendWriteDescriptorResponse(params);
                    break;
                case 0x24: // OpNotifyCharacteristicChanged
                    handleNotifyCharacteristicChanged(params);
                    break;
                default:
                    Log.w(TAG, "Unknown opcode in RPC: 0x" + Integer.toHexString(opcode));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse RPC JSON: " + json, e);
        }
    }

    // --- Handlers for Server → Client RPCs ---
    @SuppressLint("MissingPermission")
    private void handleSendReadCharacteristicResponse(JSONObject params) throws JSONException {
        int status = params.getInt("status");
        byte[] value = Base64.decode(params.getString("data"), Base64.NO_WRAP);

        if (readCharacteristicResponse != null && readCharacteristicResponse.IsSet()) {
            boolean success = bluetoothGattServer.sendResponse(
                    readCharacteristicResponse.device,
                    readCharacteristicResponse.requestId,
                    status,
                    readCharacteristicResponse.offset,
                    value
            );
            log("Sent read characteristic response, success=" + success + ", status=" + status);
            readCharacteristicResponse.reset();
        } else {
            Log.w(TAG, "No pending read characteristic request to respond to");
        }
    }

    @SuppressLint("MissingPermission")
    private void handleSendWriteCharacteristicResponse(JSONObject params) throws JSONException {
        int status = params.getInt("status");
        byte[] value = Base64.decode(params.getString("data"), Base64.NO_WRAP);

        if (writeCharacteristicResponse != null && writeCharacteristicResponse.IsSet()) {
            boolean success = bluetoothGattServer.sendResponse(
                    writeCharacteristicResponse.device,
                    writeCharacteristicResponse.requestId,
                    status,
                    writeCharacteristicResponse.offset,
                    value
            );
            log("Sent write characteristic response, success=" + success + ", status=" + status);
            writeCharacteristicResponse.reset();
        } else {
            Log.w(TAG, "No pending write characteristic request to respond to");
        }
    }

    @SuppressLint("MissingPermission")
    private void handleSendReadDescriptorResponse(JSONObject params) throws JSONException {
        int status = params.getInt("status");
        byte[] value = Base64.decode(params.getString("data"), Base64.NO_WRAP);

        if (readDescriptorResponse != null && readDescriptorResponse.IsSet()) {
            boolean success = bluetoothGattServer.sendResponse(
                    readDescriptorResponse.device,
                    readDescriptorResponse.requestId,
                    status,
                    readDescriptorResponse.offset,
                    value
            );
            log("Sent read descriptor response, success=" + success + ", status=" + status);
            readDescriptorResponse.reset();
        } else {
            Log.w(TAG, "No pending read descriptor request to respond to");
        }
    }

    @SuppressLint("MissingPermission")
    private void handleSendWriteDescriptorResponse(JSONObject params) throws JSONException {
        int status = params.getInt("status");
        byte[] value = Base64.decode(params.getString("data"), Base64.NO_WRAP);

        if (writeDescriptorResponse != null && writeDescriptorResponse.IsSet()) {
            boolean success = bluetoothGattServer.sendResponse(
                    writeDescriptorResponse.device,
                    writeDescriptorResponse.requestId,
                    status,
                    writeDescriptorResponse.offset,
                    null
            );
            log("Sent write descriptor response, success=" + success + ", status=" + status);
            writeDescriptorResponse.reset();
        } else {
            Log.w(TAG, "No pending write descriptor request to respond to");
        }
    }

    @SuppressLint("MissingPermission")
    private void handleNotifyCharacteristicChanged(JSONObject params) throws JSONException {
        String serviceUuid = params.getString("serviceUuid");
        String characteristicUuid = params.getString("characteristicUuid");

        byte[] data = Base64.decode(params.getString("data"), Base64.NO_WRAP);

        String key = serviceUuid + "_" + characteristicUuid;
        BluetoothGattCharacteristic characteristic = characteristicMap.get(key);
        if (characteristic == null) {
            Log.w(TAG, "Characteristic not found for notification: " + key);
            return;
        }

        characteristic.setValue(data);

        boolean anySuccess = false;
        if (connectedDevices != null && clientSubscriptions != null) {
            for (BluetoothDevice device : connectedDevices.values()) {
                if (device == null) continue;
                byte[] subscription = clientSubscriptions.get(device);
                if (subscription != null &&
                        (Arrays.equals(subscription, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ||
                                Arrays.equals(subscription, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE))) {
                    boolean confirm = Arrays.equals(subscription, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                    boolean success = bluetoothGattServer.notifyCharacteristicChanged(device, characteristic, confirm);
                    if (success) anySuccess = true;
                    log("Notified device " + device.getAddress() + ", confirm=" + confirm + ", success=" + success);
                }
            }
        }

        if (!anySuccess) {
            Log.w(TAG, "No subscribed devices to notify for " + key);
        }
    }

    // —————— Logging ——————
    private void log(String message) {
        Log.d(TAG, message);
        WebLogManager.get().log(message, WebLogManager.LOG_SEND);
    }

}