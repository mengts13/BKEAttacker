// BleScannerManager.java

package com.example.bkeattacker;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressLint("MissingPermission")
public class BleScannerManager {

    private static final String TAG = "BleScannerManager";
    private static final int MAX_SEGMENT_LENGTH = 32; // 32-byte constraint
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private boolean isScanning = false;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Context context;
    private ScanListener listener;

    // (from your ClientFragment)
    // Full advertising packet "database" used to store all unique advertising packets
    private HashMap<String, HashMap<String, ScanRecord>> results = new HashMap<>();
    
    // Data source for the UI device list, used for display in p4.html
    private List<ScannedDevice> allDevicesList = new ArrayList<>();
    private ScanCallback currentScanCallback;

    /**
     * Stores the native BluetoothDevice and the *latest* ScanRecord
     */
    public static class ScannedDevice {
        private BluetoothDevice device;
        private int rssi;
        private ScanRecord scanRecord;

        public ScannedDevice(BluetoothDevice device, int rssi, ScanRecord scanRecord) {
            this.device = device;
            this.rssi = rssi;
            this.scanRecord = scanRecord;
        }

        public BluetoothDevice getDevice() { return device; }
        public int getRssi() { return rssi; }
        public String getAddress() { return device.getAddress(); }
        public ScanRecord getScanRecord() { return scanRecord; }

        public String getName() {
            String name = device.getName();
            if ((name == null || name.isEmpty()) && scanRecord != null) {
                name = scanRecord.getDeviceName();
            }
            return (name != null && !name.isEmpty()) ? name : "Unknown Device";
        }

        public void setRssi(int rssi) { this.rssi = rssi; }
        public void setScanRecord(ScanRecord record) { this.scanRecord = record; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ScannedDevice that = (ScannedDevice) o;
            return device.getAddress().equals(that.device.getAddress());
        }

        @Override
        public int hashCode() {
            return device.getAddress().hashCode();
        }
    }

    // JSON-friendly "Pair" storage object
    public static class SimplePacketPair {
        public String adv_data; // <-- Renamed from segment1
        public String adv_resp; // <-- Renamed from segment2

        public SimplePacketPair(byte[] seg1, byte[] seg2) {
            this.adv_data = bytesToHex(seg1); // Use new name
            this.adv_resp = (seg2 != null && seg2.length > 0) ? bytesToHex(seg2) : null; // Use new name
        }

        // (Optional: Update toString if you use it for logging)
        @Override
        public String toString() {
            return "SimplePacketPair {" +
                    " adv_data=" + adv_data +
                    ", adv_resp=" + adv_resp +
                    " }";
        }
    }

    /**
     * Serializable (JSON) device class
     */
    public static class SimpleDevice {
        public String name;
        public String address;
        public int rssi;
        public int broadcastCount;
        public List<SimplePacketPair> packedPackets; // Contains all packed/split advertising packets

        public SimpleDevice(ScannedDevice device, List<SimplePacketPair> packedPackets) {
            this.name = device.getName();
            this.address = device.getAddress();
            this.rssi = device.getRssi();
            this.packedPackets = packedPackets;
            this.broadcastCount = (packedPackets != null) ? packedPackets.size() : 0;
        }
    }

    /**
     * Callback interface
     */
    public interface ScanListener {
        void onScanStarted();
        void onScanFinished(List<SimpleDevice> filteredSimpleDevices);
        void onScanFailed(String error);
        void onPermissionNeeded(String[] permissions);
    }

    public BleScannerManager(Context context, ScanListener listener) {
        this.context = context;
        this.listener = listener;
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        }
    }

    /**
     * Starts a new BLE scan.
     */
    public void startScan(String mode, String query, long durationMillis) {
        // (Unchanged) Check isScanning, permissions, and Bluetooth status
        if (isScanning) {
            Log.w(TAG, "Warning: Previous scan is still in progress. Forcibly stopping it...");
            stopActiveScan();
        }
        if (!checkScanPermissions()) { listener.onPermissionNeeded(getRequiredPermissions()); return; }
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) { listener.onScanFailed("Bluetooth is not enabled"); return; }
        if (bluetoothLeScanner == null) bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (bluetoothLeScanner == null) { listener.onScanFailed("Failed to get BluetoothLeScanner"); return; }

        // (Unchanged) Clear the two data structures
        allDevicesList.clear();
        results.clear();

        this.currentScanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                // (Unchanged) Populate 'results' (HashMap) and 'allDevicesList' (List)
                super.onScanResult(callbackType, result);
                if (!isScanning) return;
                BluetoothDevice device = result.getDevice();
                ScanRecord record = result.getScanRecord();
                if (device == null || record == null) return;

                String macAddress = device.getAddress();
                int rssi = result.getRssi();

                // 1. Populate the "results" database
                byte[] rawBytes = record.getBytes();
                String hexKey = bytesToHex(rawBytes);
                HashMap<String, ScanRecord> devicePackets = results.computeIfAbsent(macAddress, k -> new HashMap<>());
                devicePackets.putIfAbsent(hexKey, record);

                // 2. Populate/update the "allDevicesList" UI list
                ScannedDevice newDevice = new ScannedDevice(device, rssi, record);
                int index = allDevicesList.indexOf(newDevice);
                if (index == -1) {
                    allDevicesList.add(newDevice);
                } else {
                    allDevicesList.get(index).setRssi(rssi);
                    allDevicesList.get(index).setScanRecord(record);
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                // (Unchanged)
                super.onScanFailed(errorCode);
                Log.e(TAG, "Scan failed with error code " + errorCode);
                isScanning = false;
                listener.onScanFailed("Error code: " + errorCode);
            }
        };

        // (Unchanged) Start scanning and set a timeout
        ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        bluetoothLeScanner.startScan(null, settings, this.currentScanCallback);
        isScanning = true;
        listener.onScanStarted();
        Log.d(TAG, "Scan started for " + durationMillis + "ms");
        handler.postDelayed(() -> {
            stopScanAndFilter(mode, query);
        }, durationMillis);
    }

    /** (Unchanged) Filter allDevicesList by name */
    public List<ScannedDevice> filterByName(String nameQuery) {
        List<ScannedDevice> filteredList = new ArrayList<>();
        if (nameQuery == null || nameQuery.trim().isEmpty()) {
            filteredList.addAll(allDevicesList);
        } else {
            String lowerCaseQuery = nameQuery.toLowerCase();
            for (ScannedDevice device : allDevicesList) {
                if (device.getName().toLowerCase().contains(lowerCaseQuery)) {
                    filteredList.add(device);
                }
            }
        }
        return filteredList;
    }

    /** (Unchanged) Filter allDevicesList by MAC address */
    public List<ScannedDevice> filterByMacAddress(String macAddress) {
        List<ScannedDevice> filteredList = new ArrayList<>();
        if (macAddress == null || macAddress.trim().isEmpty()) {
            filteredList.addAll(allDevicesList);
        } else {
            String lowerCaseQuery = macAddress.toLowerCase();
            for (ScannedDevice device : allDevicesList) {
                if (device.getAddress().toLowerCase().contains(lowerCaseQuery)) {
                    filteredList.add(device);
                }
            }
        }
        return filteredList;
    }

    /**
     * (Unchanged) Stops the scan, processes the results, and notifies the listener.
     */
    private void stopScanAndFilter(String mode, String query) {
        if (!isScanning) { return; }
        Log.d(TAG, "Scan stopped. Processing results...");
        stopActiveScan();

        // 1. (Unchanged) Use the filtering method
        List<ScannedDevice> filteredDevices;
        if (mode.equals("mac")) {
            filteredDevices = filterByMacAddress(query);
        } else if (mode.equals("name")) {
            filteredDevices = filterByName(query);
        } else {
            filteredDevices = filterByName(""); // "manual" = return all
        }

        // 2. (Unchanged) Convert ScannedDevice to SimpleDevice
        List<SimpleDevice> simpleList = new ArrayList<>();
        for (ScannedDevice device : filteredDevices) {
            // (Unchanged) Get *all* unique ScanRecords for this device from 'results'
            HashMap<String, ScanRecord> devicePackets = results.getOrDefault(device.getAddress(), new HashMap<>());
            // (Unchanged) Execute the packing/splitting logic for *each* unique ScanRecord
            List<SimplePacketPair> packedPairsList = new ArrayList<>();
            for (ScanRecord uniqueRecord : devicePackets.values()) {
                // (Unchanged) Call your packing/splitting function
                SimplePacketPair packedPair = assembleAndSplitPacket(uniqueRecord.getBytes());
                packedPairsList.add(packedPair);
            }
            // (Unchanged) Create a detailed object containing "all packed advertising packets"
            simpleList.add(new SimpleDevice(device, packedPairsList));
        }

        // 3. (Unchanged) Send the SimpleDevice list
        listener.onScanFinished(simpleList);
    }

    /**
     * (Unchanged) Immediately stops any ongoing scan and resets the state.
     */
    public void stopActiveScan() {
        Log.d(TAG, "stopActiveScan: Stopping scan and cleaning up...");
        handler.removeCallbacksAndMessages(null);
        if (isScanning && this.currentScanCallback != null) {
            try {
                bluetoothLeScanner.stopScan(this.currentScanCallback);
            } catch (Exception e) {
                Log.e(TAG, "Error during stopScan: " + e.getMessage());
            }
        }
        this.currentScanCallback = null;
        this.isScanning = false;
    }

    /**
     * (Unchanged) Retrieves the complete BluetoothDevice object by MAC address.
     */
    public BluetoothDevice getDeviceByMac(String macAddress) {
        if (macAddress == null) return null;
        for (ScannedDevice device : allDevicesList) {
            if (device.getAddress().equalsIgnoreCase(macAddress)) {
                return device.getDevice();
            }
        }
        Log.w(TAG, "getDeviceByMac: Failed to find " + macAddress + " in allDevicesList");
        return null;
    }

    /**
     * (Unchanged) Checks permissions.
     */
    private boolean checkScanPermissions() {
        for (String permission : getRequiredPermissions()) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Permission check failed: " + permission + " is not granted.");
                return false;
            }
        }
        return true;
    }

    /**
     * (Unchanged) Gets the list of required permissions.
     */
    private String[] getRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
        } else {
            return new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
        }
    }

    // --- (Core modification) Your packing/splitting logic ---
    
    /**
     * (Modified)
     * Core function: Executes the "split -> pack (opportunistically) -> split" logic
     * @param singleRawPacket A single raw advertising packet (e.g., 60 bytes)
     * @return A SimplePacketPair (seg1, seg2)
     */
    private SimplePacketPair assembleAndSplitPacket(byte[] singleRawPacket) {
        // --- Step 1: (Splitting) ---
        // Use your L-T-V logic to split this packet into AD structures (removing padding)
        List<byte[]> adStructures = parseSinglePacketToAdStructures(singleRawPacket);
        if (adStructures.isEmpty()) {
            // If splitting fails or the packet is empty, try a "hard" 32-byte split of the raw packet
            return splitRawPacket(singleRawPacket);
        }

        // --- Steps 2 & 3: (Packing & Splitting) ---
        // (Modified)
        // Execute according to your "opportunistic packing" logic
        List<byte[]> seg1Buffer = new ArrayList<>();
        int seg1Len = 0;
        List<byte[]> seg2Buffer = new ArrayList<>();
        int seg2Len = 0;

        for (byte[] ad : adStructures) {
            int adLen = ad.length;

            // 1. Try to fit into Segment 1
            if (seg1Len + adLen <= MAX_SEGMENT_LENGTH) {
                seg1Buffer.add(ad);
                seg1Len += adLen;
            }
            // 2. If seg1 fails, try to fit into Segment 2
            else if (seg2Len + adLen <= MAX_SEGMENT_LENGTH) {
                seg2Buffer.add(ad);
                seg2Len += adLen;
            }
            // 3. If both seg1 and seg2 fail, discard this AD structure
            else {
                Log.w(TAG, "assembleAndSplitPacket: AD structure " + bytesToHex(ad) + " (len " + adLen + ") was too large to fit in either remaining segment and was dropped.");
            }
        } // End of loop

        // --- Step 4: (Concatenation) ---
        // Concatenate a List<byte[]> into a single byte[]
        byte[] finalSegment1 = concatenateByteArrays(seg1Buffer);
        byte[] finalSegment2 = concatenateByteArrays(seg2Buffer);
        return new SimplePacketPair(finalSegment1, finalSegment2);
    }
    
    /**
     * Helper function - (Step 1)
     * (From your ClientFragment)
     * Splits *one* raw advertising packet into AD structures (removing padding)
     */
    private List<byte[]> parseSinglePacketToAdStructures(byte[] packetBytes) {
        List<byte[]> allAdStructures = new ArrayList<>();
        if (packetBytes == null || packetBytes.length == 0) {
            return allAdStructures;
        }

        int ptr = 0;
        while (ptr < packetBytes.length) {
            int L_val = packetBytes[ptr] & 0xFF; // Read the length byte
            if (L_val == 0) {
                break; // End of data (padding)
            }

            int numBytesInStructElement = 1 + L_val; // Length byte + data
            if (ptr + numBytesInStructElement > packetBytes.length) {
                // Packet is corrupted
                Log.e(TAG, String.format(
                        "parseToAd: Error: Declared length of %d bytes at byte index %d, but data is incomplete.",
                        ptr, L_val));
                break;
            }

            byte[] adStructure = new byte[numBytesInStructElement];
            System.arraycopy(
                    packetBytes,
                    ptr,
                    adStructure,
                    0,
                    numBytesInStructElement
            );
            allAdStructures.add(adStructure);
            ptr += numBytesInStructElement;
        }
        return allAdStructures;
    }

    /**
     * (New) Helper function
     * Performs a "hard" 32-byte split on the raw packet when L-T-V parsing fails.
     */
    private SimplePacketPair splitRawPacket(byte[] rawPacket) {
        if (rawPacket == null || rawPacket.length == 0) {
            return new SimplePacketPair(new byte[0], null);
        }

        int segment1Length = Math.min(rawPacket.length, MAX_SEGMENT_LENGTH);
        byte[] segment1 = new byte[segment1Length];
        System.arraycopy(rawPacket, 0, segment1, 0, segment1Length);

        byte[] segment2 = null;
        if (rawPacket.length > MAX_SEGMENT_LENGTH) {
            int segment2Length = rawPacket.length - MAX_SEGMENT_LENGTH;
            segment2 = new byte[segment2Length];
            System.arraycopy(rawPacket, MAX_SEGMENT_LENGTH, segment2, 0, segment2Length);
        }
        return new SimplePacketPair(segment1, segment2);
    }
    
    /**
     * Helper function - (Step 4)
     * Concatenates a List of byte[] into a single byte[]
     */
    private byte[] concatenateByteArrays(List<byte[]> arrays) {
        if (arrays == null || arrays.isEmpty()) {
            return new byte[0]; // Return an empty array, not null
        }

        int totalLength = 0;
        for (byte[] arr : arrays) {
            if (arr != null) totalLength += arr.length;
        }

        byte[] result = new byte[totalLength];
        int currentPos = 0;
        for (byte[] arr : arrays) {
            if (arr != null) {
                System.arraycopy(arr, 0, result, currentPos, arr.length);
                currentPos += arr.length;
            }
        }
        return result;
    }

    // --- (Helper Functions) ---

    /** (Made public static for use by SimpleDevice) */
    public static String bytesToHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString().trim();
    }
}
