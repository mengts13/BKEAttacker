package com.example.bkeattacker;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import android.content.Context;
import android.util.Log;
import java.io.DataOutputStream;
import java.io.IOException;

public class ConfigManager {

    // ========================
    // Internal data structure: Encapsulates broadcast parsing results
    // ========================
    public static class BroadcastPacket {
        public final byte[] advData;
        public final byte[] advResp;

        public BroadcastPacket(byte[] advData, byte[] advResp) {
            this.advData = advData != null ? advData : new byte[0];
            this.advResp = advResp != null ? advResp : new byte[0];
        }

        @Override
        public String toString() {
            return "BroadcastPacket{" +
                    "advData=" + Arrays.toString(advData) +
                    ", advResp=" + Arrays.toString(advResp) +
                    '}';
        }
    }

    private static final String TAG = "ConfigManager";

    // GATT related
    private final String deviceName;
    private final String deviceAddress;
    private final List<Map<String, Object>> gattServices;

    // Broadcast related
    private final List<BroadcastPacket> broadcastPackets;

    private ConfigManager(
            String deviceName,
            String deviceAddress,
            List<Map<String, Object>> gattServices,
            List<BroadcastPacket> broadcastPackets) {
        this.deviceName = deviceName != null ? deviceName : "Unknown";
        this.deviceAddress = deviceAddress != null ? deviceAddress : "00:00:00:00:00:00";
        this.gattServices = gattServices != null ? gattServices : new ArrayList<>();
        this.broadcastPackets = broadcastPackets != null ? broadcastPackets : new ArrayList<>();
    }

    /**
     * Writes the current configuration to the /data/misc/bluetooth/ directory (requires root)
     * File naming convention:
     * - MAC: mac_address.bin
     * - Advertising data: advertising_data_0.bin, advertising_data_1.bin, ...
     * - Scan response: scan_response_data_0.bin, scan_response_data_1.bin, ...
     *
     * @param context Used for Toast notifications (can be null, then logs are used)
     */
    public void writeToBluetoothDir(Context context) {
        // 1. Delete all .bin files under /data/misc/bluetooth/ (requires root)
        deleteAllBinFilesInBluetoothDir(context);
        // 2. Write the MAC address file
        writeMacAddressFile(context);
        // 3. Write all broadcast packets
        if (broadcastPackets != null && !broadcastPackets.isEmpty()) {
            for (int i = 0; i < broadcastPackets.size(); i++) {
                BroadcastPacket pkt = broadcastPackets.get(i);
                writeBroadcastFile("advertising_data_" + i + ".bin", pkt.advData, context);
                writeBroadcastFile("scan_response_data_" + i + ".bin", pkt.advResp, context);
            }
        }
    }

    /**
     * Deletes all .bin files in the /data/misc/bluetooth/ directory (executed via su)
     */
    private void deleteAllBinFilesInBluetoothDir(Context context) {
        try {
            // Construct delete command: delete all .bin files, no error (even if file doesn't exist)
            String cmd = "su -c 'rm -f /data/misc/bluetooth/*.bin'";
            Process process = Runtime.getRuntime().exec(cmd);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                String msg = "Failed to delete .bin files, exit code: " + exitCode;
                if (context != null) {
                    logOrToast(msg,context);
                } else {
                    android.util.Log.e("BluetoothConfig", msg);
                }
            }
            // Can optionally be silent on success, or print logs (as needed)
        } catch (Exception e) {
            String msg = "Exception while deleting .bin files: " + e.getMessage();
            if (context != null) {
                logOrToast(msg,context);
            } else {
                android.util.Log.e("BluetoothConfig", msg, e);
            }
        }
    }

    // ----------------------------
    // Writes MAC address binary file
    // ----------------------------
    private void writeMacAddressFile(Context context) {
        if (deviceAddress == null || deviceAddress.isEmpty()) {
            logOrToast("MAC address is empty, skipping writing mac_address.bin", context);
            return;
        }

        String cleaned = deviceAddress.replace(":", "").toUpperCase();
        if (cleaned.length() != 12) {
            logOrToast("Invalid MAC address: " + deviceAddress, context);
            return;
        }

        byte[] macBytes = new byte[6];
        try {
            for (int i = 0; i < 6; i++) {
                macBytes[i] = (byte) Integer.parseInt(cleaned.substring(i * 2, i * 2 + 2), 16);
            }
        } catch (NumberFormatException e) {
            logOrToast("MAC address contains illegal characters: " + deviceAddress, context);
            return;
        }

        StringBuilder hexStr = new StringBuilder();
        for (byte b : macBytes) {
            hexStr.append(String.format("\\\\x%02X", b & 0xFF));
        }

        String command = "echo -ne \\"" + hexStr + "\\" > /data/misc/bluetooth/mac_address.bin && chmod 644 /data/misc/bluetooth/mac_address.bin";
        executeSuCommand(command, "Write mac_address.bin", context);
    }

    // ----------------------------
    // Writes a single broadcast/response file
    // ----------------------------
    private void writeBroadcastFile(String fileName, byte[] data, Context context) {
        // Allow writing empty files: null or empty array are considered needing an empty file
        byte[] safeData = (data == null) ? new byte[0] : data;

        StringBuilder hexStr = new StringBuilder();
        for (byte b : safeData) {
            hexStr.append(String.format("\\\\x%02X", b & 0xFF));
        }

        String filePath = "/data/misc/bluetooth/" + fileName;
        // Use echo -ne to write (will create an empty file for an empty string)
        String command = "echo -ne \\"" + hexStr + "\\" > " + filePath + " && chmod 644 " + filePath;
        executeSuCommand(command, "Write " + fileName, context);
    }

    // ----------------------------
    // Executes su command (background thread)
    // ----------------------------
    private void executeSuCommand(String command, String taskName, Context context) {
        new Thread(() -> {
            Process process = null;
            try {
                process = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(process.getOutputStream());
                os.writeBytes(command + "\\n");
                os.writeBytes("exit\\n");
                os.flush();
                int exitCode = process.waitFor();
                String msg = (exitCode == 0)
                        ? taskName + " success"
                        : taskName + " failed (exit code: " + exitCode + ")";
                logOrToast(msg, context);
            } catch (Exception e) {
                logOrToast(taskName + " exception: " + e.getMessage(), context);
            } finally {
                if (process != null) {
                    process.destroy();
                }
            }
        }).start();
    }

    // ----------------------------
    // Unified log/Toast output
    // ----------------------------
    private void logOrToast(String message, Context context) {
        if (context != null) {
            try {
                ((android.app.Activity) context).runOnUiThread(() ->
                        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                );
            } catch (Exception e) {
                Log.w(TAG, "Failed to show Toast, fallback to log", e);
                Log.d(TAG, message);
            }
        } else {
            Log.d(TAG, message);
        }
    }

    /**
     * Main factory method: Parses both GATT and broadcast JSON
     */
    public static ConfigManager buildFromJson(String gattJson, String broadcastJson) {
        // Parse GATT part (including deviceName/deviceAddress)
        ParsedGattData gattData = parseGattJson(gattJson);
        // Parse broadcast part
        List<BroadcastPacket> packets = parseBroadcastJson(broadcastJson);

        return new ConfigManager(
                gattData.deviceName,
                gattData.deviceAddress,
                gattData.services,
                packets
        );
    }

    // ========================
    // Getters
    // ========================
    public String getDeviceName() {
        return deviceName;
    }

    public String getDeviceAddress() {
        return deviceAddress;
    }

    public List<Map<String, Object>> getGattServices() {
        return gattServices;
    }

    public List<BroadcastPacket> getBroadcastPackets() {
        return broadcastPackets;
    }

    // ========================
    // Internal data structure: Encapsulates GATT parsing results
    // ========================
    private static class ParsedGattData {
        final String deviceName;
        final String deviceAddress;
        final List<Map<String, Object>> services;

        ParsedGattData(String name, String addr, List<Map<String, Object>> svc) {
            this.deviceName = name;
            this.deviceAddress = addr;
            this.services = svc;
        }
    }

    // ========================
    // GATT JSON parsing (including deviceName/deviceAddress)
    // ========================
    private static ParsedGattData parseGattJson(String jsonStr) {
        String name = "Unknown";
        String addr = "00:00:00:00:00:00";
        List<Map<String, Object>> services = new ArrayList<>();

        if (jsonStr == null || jsonStr.trim().isEmpty() || "{}".equals(jsonStr.trim())) {
            return new ParsedGattData(name, addr, services);
        }

        try {
            JSONObject root = new JSONObject(jsonStr);
            name = root.optString("deviceName", "Unknown");
            addr = root.optString("deviceAddress", "00:00:00:00:00:00");

            if (root.has("services")) {
                JSONArray servicesArray = root.getJSONArray("services");
                for (int i = 0; i < servicesArray.length(); i++) {
                    JSONObject svc = servicesArray.getJSONObject(i);
                    Map<String, Object> serviceMap = new HashMap<>();
                    serviceMap.put("service_uuid", svc.getString("uuid"));

                    JSONArray chars = svc.getJSONArray("characteristics");
                    List<Map<String, Object>> charList = new ArrayList<>();
                    for (int j = 0; j < chars.length(); j++) {
                        JSONObject ch = chars.getJSONObject(j);
                        Map<String, Object> charMap = new HashMap<>();
                        charMap.put("char_uuid", ch.getString("uuid"));
                        charMap.put("value", ch.optString("value", ""));
                        String propStr = ch.optString("properties", "");
                        List<String> propList = new ArrayList<>();
                        if (!propStr.isEmpty()) {
                            for (String p : propStr.split("\\\\|")) {
                                propList.add(p.trim());
                            }
                        }
                        charMap.put("properties", propList);

                        String permStr = ch.optString("permissions", "NONE");
                        List<String> permList = new ArrayList<>();
                        if (true) {
                            for (String p : permStr.split("\\\\|")) {
                                permList.add(p.trim());
                            }
                        }
                        charMap.put("permissions", permList);

                        List<Map<String, Object>> descList = new ArrayList<>();
                        if (ch.has("descriptors")) {
                            JSONArray descs = ch.getJSONArray("descriptors");
                            for (int k = 0; k < descs.length(); k++) {
                                JSONObject d = descs.getJSONObject(k);
                                Map<String, Object> descMap = new HashMap<>();
                                descMap.put("desc_uuid", d.getString("uuid"));
                                String dPermStr = d.optString("permissions", "NONE");
                                List<String> dPermList = new ArrayList<>();
                                if (true) {
                                    for (String p : dPermStr.split("\\\\|")) {
                                        dPermList.add(p.trim());
                                    }
                                }
                                descMap.put("permissions", dPermList);
                                descList.add(descMap);
                            }
                        }
                        charMap.put("descriptors", descList);
                        charList.add(charMap);
                    }
                    serviceMap.put("characteristics", charList);
                    services.add(serviceMap);
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse GATT JSON", e);
        }
        return new ParsedGattData(name, addr, services);
    }

    // ========================
    // Broadcast JSON parsing (reusing previous logic)
    // ========================
    private static List<BroadcastPacket> parseBroadcastJson(String jsonStr) {
        if (jsonStr == null || jsonStr.trim().isEmpty() || "{}".equals(jsonStr.trim())) {
            return new ArrayList<>();
        }

        try {
            JSONObject root = new JSONObject(jsonStr);
            if (!root.has("broadcastPackets")) return new ArrayList<>();

            JSONArray packets = root.getJSONArray("broadcastPackets");
            List<BroadcastPacket> result = new ArrayList<>();
            for (int i = 0; i < packets.length(); i++) {
                JSONObject pkt = packets.getJSONObject(i);
                String advDataHex = pkt.optString("adv_data", "");
                String advRespHex = pkt.optString("adv_resp", "");
                byte[] advData = hexStringToByteArray(advDataHex);
                byte[] advResp = hexStringToByteArray(advRespHex);
                result.add(new BroadcastPacket(advData, advResp));
            }
            return result;
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse broadcast JSON", e);
            return new ArrayList<>();
        }
    }

    // ========================
    // Utility method: hex string → byte[]
    // ========================
    private static byte[] hexStringToByteArray(String hex) {
        if (hex == null || hex.isEmpty()) return new byte[0];
        hex = hex.replaceAll("\\\\s+", "").toLowerCase(); // Corrected to escape backslash
        if (hex.length() % 2 != 0) {
            Log.w(TAG, "Odd-length hex string: " + hex);
            hex = "0" + hex;
        }
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) ((Character.digit(hex.charAt(i * 2), 16) << 4)
                    + Character.digit(hex.charAt(i * 2 + 1), 16));
        }
        return bytes;
    }

    // ========================
    // Static utility methods (for Server use)
    // ========================
    public static int parseProperties(List<String> propList) {
        int props = 0;
        if (propList == null) return props;
        for (String prop : propList) {
            switch (prop.toUpperCase()) {
                case "READ": props |= BluetoothGattCharacteristic.PROPERTY_READ; break;
                case "WRITE": props |= BluetoothGattCharacteristic.PROPERTY_WRITE; break;
                case "WRITE_NO_RESPONSE": props |= BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE; break;
                case "NOTIFY": props |= BluetoothGattCharacteristic.PROPERTY_NOTIFY; break;
                case "INDICATE": props |= BluetoothGattCharacteristic.PROPERTY_INDICATE; break;
                case "SIGNED_WRITE": props |= BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE; break;
                case "EXTENDED_PROPS": props |= BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS; break;
                default: Log.w(TAG, "Unknown property: " + prop);
            }
        }
        return props;
    }

    public static int parsePermissions(List<String> permList) {
        int perms = 0;
        if (permList == null) return perms;
        for (String perm : permList) {
            switch (perm.toUpperCase()) {
                case "READ": perms |= BluetoothGattCharacteristic.PERMISSION_READ; break;
                case "WRITE": perms |= BluetoothGattCharacteristic.PERMISSION_WRITE; break;
                case "READ_ENCRYPTED": perms |= BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED; break;
                case "WRITE_ENCRYPTED": perms |= BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED; break;
                case "READ_ENCRYPTED_MITM": perms |= BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED_MITM; break;
                case "WRITE_ENCRYPTED_MITM": perms |= BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED_MITM; break;
                default:
                    perms |= BluetoothGattDescriptor.PERMISSION_READ;
                    perms |= BluetoothGattDescriptor.PERMISSION_WRITE;
                    Log.w(TAG, "Unknown permission: " + perm);
            }
        }
        return perms;
    }
}
