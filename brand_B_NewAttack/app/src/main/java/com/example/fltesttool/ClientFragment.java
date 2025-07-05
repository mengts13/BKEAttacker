package com.example.fltesttool;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.core.app.ActivityCompat;
import android.util.Log;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ClientFragment extends Fragment implements GATTClientInterface {

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private BluetoothLeScanner bluetoothLeScanner;
    private boolean isScanning = false;
    private Handler handler = new Handler();
    private TextView logTextView;
    private ScanCallback scanCallback;
    private ListView deviceListView;
    private DeviceAdapter deviceAdapter;
    private List<BluetoothDevice> deviceList = new ArrayList<>();
    private byte[] key;


    public interface OnClientFragmentReadyListener {
        void onClientFragmentReady(ClientFragment clientFragment);
    }

    private OnClientFragmentReadyListener clientReadyListener;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof OnClientFragmentReadyListener) {
            clientReadyListener = (OnClientFragmentReadyListener) context;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        clientReadyListener = null;
    }


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {


        View view = inflater.inflate(R.layout.fragment_client, container, false);
        logTextView = view.findViewById(R.id.log);
        deviceListView = view.findViewById(R.id.device_list);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Log.e("Bluetooth", "Bluetooth is not supported on this device");
            return view;
        }

        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (bluetoothLeScanner == null) {
            Log.e("Bluetooth", "BluetoothLeScanner is null");
            return view;
        }

        deviceAdapter = new DeviceAdapter(getContext(), deviceList);
        deviceListView.setAdapter(deviceAdapter);


        Button scanButton = view.findViewById(R.id.scan_button);
        scanButton.setOnClickListener(v -> startScan());


        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                super.onScanResult(callbackType, result);
                BluetoothDevice device = result.getDevice();
                int rssi = result.getRssi();
                byte[] scanRecord = result.getScanRecord().getBytes();
                onLeScan(device, rssi, scanRecord);
            }

            @Override
            public void onScanFailed(int errorCode) {
                super.onScanFailed(errorCode);
                Log.e("Bluetooth", "Scan failed with error code " + errorCode);
            }
        };

        deviceListView.setOnItemClickListener((parent, view1, position, id) -> {
            BluetoothDevice selectedDevice = deviceList.get(position);
            connectToDevice(selectedDevice);
        });


        if (clientReadyListener != null) {
            clientReadyListener.onClientFragmentReady(this);
        }
        return view;
    }


    @Override
    public void startScan() {
        if (ActivityCompat.checkSelfPermission(getContext(), android.Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(getContext(), android.Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(getContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {


            requestPermissions(new String[]{
                    android.Manifest.permission.BLUETOOTH,
                    android.Manifest.permission.BLUETOOTH_ADMIN,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
            }, 100);
            return;
        }

        if (!isScanning) {
            isScanning = true;


            if (ActivityCompat.checkSelfPermission(getContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 100);
                return;
            }

            bluetoothLeScanner.startScan(scanCallback);

            handler.postDelayed(this::stopScan, 10000);
            log("Scanning started...");
        }
    }


    @Override
    public void stopScan() {
        if (isScanning) {
            bluetoothLeScanner.stopScan(scanCallback);
            isScanning = false;
            log("Scanning stopped.");
        }
    }


    private void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {

        String deviceName = device.getName();
        if (deviceName == null) {
            deviceName = "Unknown Device";
        }


        if (!deviceList.contains(device)) {
            deviceList.add(device);
            deviceAdapter.notifyDataSetChanged();
        }
    }



    @Override
    public void connectToDevice(BluetoothDevice device) {
        bluetoothGatt = device.connectGatt(getContext(), false, gattCallback);
        log("Connecting to " + device.getName());

    }


    @Override
    public void disconnect() {
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            log("Disconnected from device.");
        }
    }


    @Override
    public void discoverServices() {
        if (bluetoothGatt != null) {
            bluetoothGatt.discoverServices();
            log("Discovering services...");
        }
    }


    @Override
    public void readCharacteristic(UUID serviceUUID, UUID characteristicUUID) {
        if (bluetoothGatt != null) {
            BluetoothGattService service = bluetoothGatt.getService(serviceUUID);
            if (service != null) {
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUUID);
                if (characteristic != null) {
                    bluetoothGatt.readCharacteristic(characteristic);
                    log("Reading characteristic...");
                }
            }
        }
    }


    @Override
    public void writeCharacteristic(UUID serviceUUID, UUID characteristicUUID, byte[] value) {
        if (bluetoothGatt != null) {
            BluetoothGattService service = bluetoothGatt.getService(serviceUUID);
            if (service != null) {
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUUID);
                if (characteristic != null) {
                    characteristic.setValue(value);
                    bluetoothGatt.writeCharacteristic(characteristic);
                    log("Writing characteristic...");
                }
            }
        }
    }


    @Override
    public void enableNotifications(UUID serviceUUID, UUID characteristicUUID, boolean enable) {
        if (bluetoothGatt != null) {
            BluetoothGattService service = bluetoothGatt.getService(serviceUUID);
            if (service != null) {
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUUID);
                if (characteristic != null) {
                    bluetoothGatt.setCharacteristicNotification(characteristic, enable);
                    BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                    if (descriptor != null) {
                        descriptor.setValue(enable ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                        bluetoothGatt.writeDescriptor(descriptor);
                    }
                }
            }
        }
    }


    @Override
    public void readDescriptor(UUID serviceUUID, UUID characteristicUUID, UUID descriptorUUID) {
        if (bluetoothGatt != null) {
            BluetoothGattService service = bluetoothGatt.getService(serviceUUID);
            if (service != null) {
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUUID);
                if (characteristic != null) {
                    BluetoothGattDescriptor descriptor = characteristic.getDescriptor(descriptorUUID);
                    if (descriptor != null) {
                        bluetoothGatt.readDescriptor(descriptor);
                    }
                }
            }
        }
    }


    @Override
    public void writeDescriptor(UUID serviceUUID, UUID characteristicUUID, UUID descriptorUUID, byte[] value) {
        if (bluetoothGatt != null) {
            BluetoothGattService service = bluetoothGatt.getService(serviceUUID);
            if (service != null) {
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUUID);
                if (characteristic != null) {
                    BluetoothGattDescriptor descriptor = characteristic.getDescriptor(descriptorUUID);
                    if (descriptor != null) {
                        descriptor.setValue(value);
                        bluetoothGatt.writeDescriptor(descriptor);
                    }
                }
            }
        }
    }
    public void WriteDes(BluetoothGatt gatt){
        List<BluetoothGattService> services = gatt.getServices();
        for (BluetoothGattService service : services){
            if(service.getUuid().toString().equals("0000fffe-0000-1000-8000-00805f9b34fb")){
                log("Find target service!");
                UUID targetCharacteristicUuid = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb");
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(targetCharacteristicUuid);
                if (characteristic != null) {
                    log("Discovering Target Characteristics");

                    BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                    );
                    if (descriptor != null){
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        boolean success = bluetoothGatt.writeDescriptor(descriptor);
                        if(!success){
                            log("Failed to write Descriptor");
                        }else{
                            log("Write Des Successful");
                        }
                    }else{
                        log("Can't find the target des");
                    }

                } else {
                    log("Characteristic not found");
                }
            }
        }
    }


    @Override
    public boolean isConnected() {
        return bluetoothGatt != null && bluetoothGatt.connect();
    }


    private BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);

            if (newState == BluetoothProfile.STATE_CONNECTED) {

                log("Success:Connected to " + gatt.getDevice().getName());

                discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {

                log("Disconnected from " + gatt.getDevice().getName());
            }

            if (status != BluetoothGatt.GATT_SUCCESS) {

                log("Connection failed with status: " + status);
            }

        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {

            super.onServicesDiscovered(gatt, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                bluetoothGatt.requestMtu(200);
                log( "Discover Service Success");
            } else {
                log( "Discovery Service Failure");
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

        }
        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            if(status == BluetoothGatt.GATT_SUCCESS){
                WriteDes(gatt);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

            if (status == BluetoothGatt.GATT_SUCCESS) {
                log("Characteristic write was successful: " + characteristic.getUuid());



            } else {
                log("Feature write failed with status code: " + status);


            }
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                byte[] value = descriptor.getValue();
                log("read Descriptor: " + bytesToHex(value));


                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                boolean success = gatt.writeDescriptor(descriptor);
                log(success ? "write descriptor success" : "write descriptor fail");
            } else {
                log("read Descriptor fail，status code: " + status);
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {

            super.onDescriptorWrite(gatt, descriptor, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                log("Descriptor write successful: " + descriptor.getUuid());
            } else {
                log("Descriptor write failed with status: " + status);
            }
            writeKey(gatt, key);
        }
    };



    private void log(String message) {
        logTextView.append(message + "\n");
        logTextView.setMovementMethod(new ScrollingMovementMethod());
    }
    public static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {

            hexString.append(String.format("%02X", b));
        }
        return hexString.toString();
    }
    public void writeKey(BluetoothGatt gatt, byte[] value){
        Log.d("client", "value: " + bytesToHex(value));
        List<BluetoothGattService> services = gatt.getServices();
        for (BluetoothGattService service : services){
            if(service.getUuid().toString().equals("0000fffe-0000-1000-8000-00805f9b34fb")){
                log("Find target service!");
                UUID targetCharacteristicUuid = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb");
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(targetCharacteristicUuid);
                if (characteristic != null) {
                    log("Discovering Target Characteristics");

                            characteristic.setValue(value);


                            if (value.length > 0){
                                Log.d("Server", "Value length is valid");

                            }else{
                                Log.d("Server", "value length less than 0");
                            }
                            boolean success = bluetoothGatt.writeCharacteristic(characteristic);
                            if (success) {
                                log("Write characteristic successful");
                            } else {
                                log("Failed to write characteristic");
                            }
                } else {
                    log("Characteristic not found");
                }
            }
        }
    }
    public void write_Key(byte[] key) {
        byte[] trans_key = key;
        if (isConnected()) {
            if (bluetoothGatt != null) {
                BluetoothGattService service = bluetoothGatt.getService(UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb"));
                if (service != null) {
                    BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString("0000a001-0000-1000-8000-00805f9b34fb"));
                    if (characteristic != null) {
                        characteristic.setValue(trans_key);
                        boolean success = bluetoothGatt.writeCharacteristic(characteristic);
                    }
                }
            }
        }else{
            log("Currently not connected, writes are not allowed!");
        }
    }

    public void save_Key(byte[] key) {
        this.key = key;
    }
    public static byte[] hexToBytes(String hexString) {

        hexString = hexString.replaceAll("\\s", "");

        int len = hexString.length();
        if (len % 2 != 0) {
            throw new IllegalArgumentException("Hexadecimal string length must be an even number");
        }

        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4)
                    + Character.digit(hexString.charAt(i + 1), 16));
        }
        return data;
    }
}
