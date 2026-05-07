// Server.java
package com.example.bkeattacker;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import android.annotation.SuppressLint;

import com.example.bkeattacker.responseData.ReadCharacteristicResponse;
import com.example.bkeattacker.responseData.WriteCharacteristicResponse;
import com.example.bkeattacker.responseData.ReadDescriptorResponse;
import com.example.bkeattacker.responseData.WriteDescriptorResponse;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


/**
 * A unified BLE GATT server controller that:
 * - Manages BluetoothGattServer lifecycle
 * - Coordinates ServerCallback and ServerImpl
 * - Shares state between them
 * - Handles service/characteristic setup from config
 */
public class Server {
    private static final String TAG = "Server";

    private final Context context;
    private final ConfigManager configManager;
    private BluetoothGattServer bluetoothGattServer;
    private boolean isRunning = false;

    // Shared state
    private final Map<String, android.bluetooth.BluetoothDevice> connectedDevices = new HashMap<>();
    private final Map<String, android.bluetooth.BluetoothGattCharacteristic> characteristicMap = new HashMap<>();
    private final HashMap<android.bluetooth.BluetoothDevice, byte[]> clientSubscriptions = new HashMap<>();
    private final ReadCharacteristicResponse readCharacteristicResponse = new ReadCharacteristicResponse();
    private final WriteCharacteristicResponse writeCharacteristicResponse = new WriteCharacteristicResponse();
    private final ReadDescriptorResponse readDescriptorResponse = new ReadDescriptorResponse();
    private final WriteDescriptorResponse writeDescriptorResponse = new WriteDescriptorResponse();

    // Core components
    private final ServerCallback serverCallback;
    private final ServerImpl serverImpl;

    /**
     * Constructor: accepts Context and a complete ConfigManager
     */
    public Server(@NonNull Context context, @NonNull ConfigManager configManager,String mac) {
        this.context = context;
        this.configManager = configManager;


        this.serverCallback = new ServerCallback();
        this.SetTargetMac(mac);

        this.serverImpl = new ServerImpl(null); // GATT server will be set later

        // Wire shared state and response contexts
        serverCallback.setBluetoothGattServer(bluetoothGattServer);
        serverCallback.setReadCharacteristicResponse(readCharacteristicResponse);
        serverCallback.setWriteCharacteristicResponse(writeCharacteristicResponse);
        serverCallback.setReadDescriptorResponse(readDescriptorResponse);
        serverCallback.setWriteDescriptorResponse(writeDescriptorResponse);
        serverCallback.setClientSubscriptions(clientSubscriptions);
        serverCallback.setConnectedDevices(connectedDevices);
        serverCallback.setCharacteristicMap(characteristicMap);

        serverImpl.setConnectedDevices(connectedDevices);
        serverImpl.setCharacteristicMap(characteristicMap);
        serverImpl.setClientSubscriptions(clientSubscriptions);
        serverImpl.setReadCharacteristicResponse(readCharacteristicResponse);
        serverImpl.setWriteCharacteristicResponse(writeCharacteristicResponse);
        serverImpl.setReadDescriptorResponse(readDescriptorResponse);
        serverImpl.setWriteDescriptorResponse(writeDescriptorResponse);
    }

    /**
     * Start GATT Server and add services
     */
    @SuppressLint("MissingPermission")
    public boolean start() {
        if (isRunning) {
            Log.w(TAG, "Server already running");
            return false;
        }

        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null) {
            Log.e(TAG, "BluetoothManager not available");
            return false;
        }

        bluetoothGattServer = bluetoothManager.openGattServer(context, serverCallback);
        if (bluetoothGattServer == null) {
            Log.e(TAG, "Failed to open GATT server");
            return false;
        }

        // Inject into ServerImpl
        serverImpl.setBluetoothGattServer(bluetoothGattServer);

        // Add all GATT services from ConfigManager
        addServicesFromConfig();

        isRunning = true;
        serverImpl.startRpcConsumer();

        Log.d(TAG, "GATT Server started for device: " + configManager.getDeviceName()
                + " (" + configManager.getDeviceAddress() + ")");
        return true;
    }

    /**
     * Add services according to gattServices in ConfigManager
     */
    @SuppressLint("MissingPermission")
    private void addServicesFromConfig() {
        List<Map<String, Object>> serviceList = configManager.getGattServices();
        if (serviceList == null || serviceList.isEmpty()) {
            Log.w(TAG, "No GATT services to add");
            return;
        }

        characteristicMap.clear();

        for (Map<String, Object> serviceMap : serviceList) {
            String serviceUuid = ((String) serviceMap.get("service_uuid")).toLowerCase();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> chars = (List<Map<String, Object>>) serviceMap.get("characteristics");

            BluetoothGattService service = new BluetoothGattService(
                    UUID.fromString(serviceUuid),
                    BluetoothGattService.SERVICE_TYPE_PRIMARY
            );

            for (Map<String, Object> charMap : chars) {
                String chaUuid = ((String) charMap.get("char_uuid")).toLowerCase();
                @SuppressWarnings("unchecked")
                List<String> chaProperties = (List<String>) charMap.get("properties");
                @SuppressWarnings("unchecked")
                List<String> chaPermissions = (List<String>) charMap.get("permissions");
                String value = (String) charMap.get("value");

                int properties = ConfigManager.parseProperties(chaProperties);
                int permissions = ConfigManager.parsePermissions(chaPermissions);

                BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(
                        UUID.fromString(chaUuid), properties, permissions
                );

                if (value != null && !value.isEmpty()) {
                    characteristic.setValue(value.getBytes());
                }

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> descs = (List<Map<String, Object>>) charMap.get("descriptors");
                if (descs != null) {
                    for (Map<String, Object> desc : descs) {
                        String desUuid = ((String) desc.get("desc_uuid")).toLowerCase();
                        @SuppressWarnings("unchecked")
                        List<String> desPermissions = (List<String>) desc.get("permissions");
                        int descPerms = ConfigManager.parsePermissions(desPermissions);

                        BluetoothGattDescriptor descriptor = new BluetoothGattDescriptor(
                                UUID.fromString(desUuid), descPerms
                        );
                        characteristic.addDescriptor(descriptor);
                    }
                }

                String key = serviceUuid + "_" + chaUuid;
                characteristicMap.put(key, characteristic);
                service.addCharacteristic(characteristic);
            }

            if (bluetoothGattServer.addService(service)) {
                Log.d(TAG, "Added service: " + serviceUuid);
            } else {
                Log.w(TAG, "Failed to add service: " + serviceUuid);
            }
        }

        // Notify callback
        serverCallback.characteristicMap = characteristicMap;
    }

    /**
     * Stop GATT server and clean up.
     */
    @SuppressLint("MissingPermission")
    public void stop() {
        if (!isRunning) return;

        serverImpl.stopRpcConsumer();

        if (bluetoothGattServer != null) {
            bluetoothGattServer.close();
            bluetoothGattServer = null;
        }

        // Clear shared state
        connectedDevices.clear();
        characteristicMap.clear();
        clientSubscriptions.clear();

        readCharacteristicResponse.reset();
        writeCharacteristicResponse.reset();
        readDescriptorResponse.reset();
        writeDescriptorResponse.reset();

        isRunning = false;
        Log.d(TAG, "GATT Server stopped");
    }

    public boolean isRunning() {
        return isRunning;
    }

    public ServerCallback getServerCallback() {
        return serverCallback;
    }

    public void SetTargetMac(String mac){
        serverCallback.setTargetMac(mac);
    }
}