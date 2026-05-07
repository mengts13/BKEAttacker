package com.example.blescanner;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;

// This is a utility class, not the full gRPC callback.
// It only contains the static method you wanted to test.
public class GattClientCallback {

    public interface DeviceInfoCallback {
        /**
         * Called when the device information is successfully obtained.
         * @param deviceInfo A JSONObject representing all services and characteristics of the device.
         */
        void onInfoReady(JSONObject deviceInfo);

        /**
         * Called if an error occurs during the information retrieval process.
         * @param errorMessage A string describing the error.
         */
        void onError(String errorMessage);
    }

    /**
     * Connects to a Bluetooth device, discovers its services, formats them into a JSON object,
     * returns the result via a callback, and then automatically disconnects.
     *
     * @param device The BluetoothDevice object to inspect.
     * @param context The application context.
     * @param callback The callback to receive the result or an error.
     */
    @SuppressLint("MissingPermission") // Permissions are checked in ClientFragment before calling
    public static void getDeviceInfoAsJson(BluetoothDevice device, Context context, final DeviceInfoCallback callback) {
        if (device == null || context == null || callback == null) {
            if (callback != null) {
                callback.onError("Invalid argument passed in (device, context, or callback is null).");
            }
            return;
        }

        // Use a temporary, one-time GattCallback for this specific operation
        final BluetoothGattCallback tempGattCallback = new BluetoothGattCallback() {

            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        // Connection successful, start discovering services immediately
                        Log.d("DeviceInfoJson", "Device connected, discovering services...");
                        gatt.discoverServices();
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        // Disconnection completed, cleaning up resources
                        Log.d("DeviceInfoJson", "Device disconnected, cleaning up resources.");
                        gatt.close();
                    }
                } else {
                    // Connection failed
                    callback.onError("Connection failed with status: " + status);
                    gatt.close();
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d("DeviceInfoJson", "Services discovered, formatting to JSON...");
                    try {
                        JSONObject root = new JSONObject();
                        root.put("deviceName", gatt.getDevice().getName());
                        root.put("deviceAddress", gatt.getDevice().getAddress());

                        JSONArray servicesArray = new JSONArray();
                        for (BluetoothGattService service : gatt.getServices()) {
                            JSONObject serviceJson = new JSONObject();
                            serviceJson.put("uuid", service.getUuid().toString());

                            JSONArray characteristicsArray = new JSONArray();
                            for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                                JSONObject charJson = new JSONObject();
                                charJson.put("uuid", characteristic.getUuid().toString());
                                charJson.put("properties", parseProperties(characteristic.getProperties())); // Parse properties
                                charJson.put("permissions", parsePermissions(characteristic.getPermissions())); // Parse permissions

                                JSONArray descriptorsArray = new JSONArray();
                                for (BluetoothGattDescriptor descriptor : characteristic.getDescriptors()) {
                                    JSONObject descJson = new JSONObject();
                                    descJson.put("uuid", descriptor.getUuid().toString());
                                    descJson.put("permissions", parsePermissions(descriptor.getPermissions()));
                                    descriptorsArray.put(descJson);
                                }
                                charJson.put("descriptors", descriptorsArray);
                                characteristicsArray.put(charJson);
                            }
                            serviceJson.put("characteristics", characteristicsArray);
                            servicesArray.put(serviceJson);
                        }
                        root.put("services", servicesArray);

                        // Successfully assembled JSON, deliver the result through callback
                        callback.onInfoReady(root);

                    } catch (JSONException e) {
                        callback.onError("Failed to create JSON object: " + e.getMessage());
                    }
                } else {
                    callback.onError("Service discovery failed with status: " + status);
                }

                // Regardless of success or failure, disconnect immediately after the operation is complete
                gatt.disconnect();
            }

            // Helper method: parse the "properties" of a characteristic into a readable string
            private String parseProperties(int properties) {
                ArrayList<String> props = new ArrayList<>();
                if ((properties & BluetoothGattCharacteristic.PROPERTY_READ) != 0) props.add("READ");
                if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) props.add("WRITE");
                if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) props.add("WRITE_NO_RESPONSE");
                if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) props.add("NOTIFY");
                if ((properties & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) props.add("INDICATE");
                if ((properties & BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE) != 0) props.add("SIGNED_WRITE");
                if (props.isEmpty()) return "NONE";
                // Use a simple loop for compatibility instead of String.join
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < props.size(); i++) {
                    sb.append(props.get(i));
                    if (i < props.size() - 1) {
                        sb.append(" | ");
                    }
                }
                return sb.toString();
            }

            // Helper method: parse the "permissions" of a characteristic into a readable string
            private String parsePermissions(int permissions) {
                ArrayList<String> perms = new ArrayList<>();
                if ((permissions & BluetoothGattCharacteristic.PERMISSION_READ) != 0) perms.add("READ");
                if ((permissions & BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED) != 0) perms.add("READ_ENCRYPTED");
                if ((permissions & BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED_MITM) != 0) perms.add("READ_ENCRYPTED_MITM");
                if ((permissions & BluetoothGattCharacteristic.PERMISSION_WRITE) != 0) perms.add("WRITE");
                if ((permissions & BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED) != 0) perms.add("WRITE_ENCRYPTED");
                if ((permissions & BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED_MITM) != 0) perms.add("WRITE_ENCRYPTED_MITM");
                if ((permissions & BluetoothGattCharacteristic.PERMISSION_WRITE_SIGNED) != 0) perms.add("WRITE_SIGNED");
                if ((permissions & BluetoothGattCharacteristic.PERMISSION_WRITE_SIGNED_MITM) != 0) perms.add("WRITE_SIGNED_MITM");
                if (perms.isEmpty()) return "NONE";
                // Use a simple loop for compatibility
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < perms.size(); i++) {
                    sb.append(perms.get(i));
                    if (i < perms.size() - 1) {
                        sb.append(" | ");
                    }
                }
                return sb.toString();
            }
        };

        // Initiate the connection request and start the whole process
        device.connectGatt(context, false, tempGattCallback);
    }
}