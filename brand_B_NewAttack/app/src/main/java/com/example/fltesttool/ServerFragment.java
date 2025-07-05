package com.example.fltesttool;

import android.bluetooth.BluetoothGattServerCallback;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.fltesttool.GATTServerInterface;

import java.util.UUID;
import android.util.Log;

import org.eclipse.paho.client.mqttv3.MqttException;

import java.util.Base64;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class ServerFragment extends Fragment implements GATTServerInterface {

    private BluetoothGattServer bluetoothGattServer;
    private boolean isServerRunning = false;
    private TextView logTextView;
    BluetoothGattCharacteristic cha1;
    BluetoothGattDescriptor Des1;
    BluetoothGattDescriptor Des2;
    BluetoothGattCharacteristic cha2;
    BluetoothGattDescriptor Des3;
    private MqttTest mqtttest;

    public interface OnBleDataReceivedListener {
        void onBleDataReceived(String uuid, byte[] value);
    }

    private OnBleDataReceivedListener listener;



    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof OnBleDataReceivedListener) {
            listener = (OnBleDataReceivedListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnBleDataReceivedListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_server, container, false);


        logTextView = view.findViewById(R.id.log);

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            @SuppressLint("HardwareIds") String bluetoothAddress = bluetoothAdapter.getAddress();
            log("Bluetooth Address: " + bluetoothAddress);
        } else {
            log("Bluetooth is not supported on this device");
        }

        startGattServer();

        return view;
    }

    @Override
    public void startGattServer() {
        if (bluetoothGattServer == null) {

            bluetoothGattServer = ((BluetoothManager) getContext().getSystemService(Context.BLUETOOTH_SERVICE))
                    .openGattServer(getContext(), gattServerCallback);

            if (bluetoothGattServer != null) {
                isServerRunning = true;
                log("GATT Server started");
            } else {
                isServerRunning = false;
                log("Failed to start GATT Server");
            }
            addService(UUID.fromString("0000fffe-0000-1000-8000-00805f9b34fb"), true);

        }
    }

    @Override
    public void stopGattServer() {
        if (bluetoothGattServer != null && isServerRunning) {
            bluetoothGattServer.close();
            isServerRunning = false;
            log("GATT Server stopped");
        }
    }

    @Override
    public BluetoothGattService addService(UUID serviceUUID, boolean isPrimary) {
        if (bluetoothGattServer != null && isServerRunning) {
            BluetoothGattService service = new BluetoothGattService(serviceUUID,
                    isPrimary ? BluetoothGattService.SERVICE_TYPE_PRIMARY : BluetoothGattService.SERVICE_TYPE_SECONDARY);
//            addCharacteristic(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
//                    UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb"),
//                    BluetoothGattCharacteristic.PROPERTY_READ,
//                    BluetoothGattCharacteristic.PERMISSION_READ);
//
            cha2 = new BluetoothGattCharacteristic(UUID.fromString("0000fff4-0000-1000-8000-00805f9b34fb"),BluetoothGattCharacteristic.PROPERTY_READ|BluetoothGattCharacteristic.PROPERTY_NOTIFY|BluetoothGattCharacteristic.PROPERTY_WRITE,BluetoothGattCharacteristic.PERMISSION_READ|BluetoothGattCharacteristic.PERMISSION_WRITE);
            Des3 = new BluetoothGattDescriptor(UUID.fromString("00002901-0000-1000-8000-00805f9b34fb"), BluetoothGattDescriptor.PERMISSION_READ|BluetoothGattDescriptor.PERMISSION_WRITE);
            Des1 = new BluetoothGattDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"), BluetoothGattDescriptor.PERMISSION_READ|BluetoothGattDescriptor.PERMISSION_WRITE);
            Des2 = new BluetoothGattDescriptor(UUID.fromString("00002901-0000-1000-8000-00805f9b34fb"), BluetoothGattDescriptor.PERMISSION_READ|BluetoothGattDescriptor.PERMISSION_WRITE);
            cha1 = new BluetoothGattCharacteristic(UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb"),BluetoothGattCharacteristic.PROPERTY_READ|BluetoothGattCharacteristic.PROPERTY_NOTIFY|BluetoothGattCharacteristic.PROPERTY_WRITE,BluetoothGattCharacteristic.PERMISSION_READ|BluetoothGattCharacteristic.PERMISSION_WRITE);
//            Des1.setValue(new byte[] {0x01, 0x00});
            cha1.addDescriptor(Des1);
            cha1.addDescriptor(Des2);
            cha2.addDescriptor(Des3);

//            cha1.setValue(new byte[] {0x00, 0x00, 0x00});
            service.addCharacteristic(cha2);
            service.addCharacteristic(cha1);

            bluetoothGattServer.addService(service);
            log("Service added: " + serviceUUID);
            return service;
        }

        return null;
    }

    @Override
    public BluetoothGattCharacteristic addCharacteristic(UUID serviceUUID, UUID characteristicUUID,
                                                         int properties, int permissions) {
        if (bluetoothGattServer != null && isServerRunning) {
            BluetoothGattService service = bluetoothGattServer.getService(serviceUUID);
            if (service != null) {
                BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(
                        characteristicUUID, properties, permissions);
                service.addCharacteristic(characteristic);
                log("Characteristic added: " + characteristicUUID);
                return characteristic;
            }else{
                log("h");
            }
        }
        return null;
    }

    @Override
    public void notifyCharacteristicChanged(BluetoothDevice device,
                                            BluetoothGattCharacteristic characteristic,
                                            boolean confirm) {
        if (bluetoothGattServer != null && isServerRunning) {
            bluetoothGattServer.notifyCharacteristicChanged(device, characteristic, confirm);
            log("Notification sent: " + characteristic.getUuid());
        }
    }

    @Override
    public void handleReadRequest(BluetoothDevice device, int requestId,
                                  BluetoothGattCharacteristic characteristic, int offset) {
        if (bluetoothGattServer != null && isServerRunning) {

            bluetoothGattServer.sendResponse(device, requestId,
                    BluetoothGatt.GATT_SUCCESS, offset, characteristic.getValue());
            log("Read request handled: " + characteristic.getUuid());
        }
    }

    @Override
    public void handleWriteRequest(BluetoothDevice device, int requestId,
                                   BluetoothGattCharacteristic characteristic,
                                   boolean preparedWrite, boolean responseNeeded, int offset,
                                   byte[] value) {
        if (bluetoothGattServer != null && isServerRunning) {

            characteristic.setValue(value);
            logCharacteristicValue(characteristic);

            bluetoothGattServer.sendResponse(device, requestId,
                    BluetoothGatt.GATT_SUCCESS, offset, null);
            if(value.length > 0){
                log("Write key!");
                if (listener != null) {
                    listener.onBleDataReceived(characteristic.getUuid().toString(), value);
                    log("The data has been sent to the Activity via an interface callback!UUID:" + characteristic.getUuid().toString() + "Data: " + bytesToHex(value));
                } else {
                    Log.e("ServerFragment", "listener is null, MainActivity might not be attached or didn't implement OnBleDataReceivedListener");
                }
            }
            log("Write request handled: " + characteristic.getUuid());
        }
    }

    @Override
    public boolean isGattServerRunning() {
        return isServerRunning;
    }


    private BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
            super.onServiceAdded(status, service);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                log("Service added successfully: " + service.getUuid());




                    
            } else {
                log("Service addition failed");
            }
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            handleReadRequest(device, requestId, characteristic, offset);
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
                                                 BluetoothGattCharacteristic characteristic,
                                                 boolean preparedWrite, boolean responseNeeded, int offset,
                                                 byte[] value) {
            handleWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
        }
        @Override
        public void onMtuChanged(BluetoothDevice device, int mtu) {
            log("MTU requested by client: " + mtu);
        }

        @Override
        public void onNotificationSent(BluetoothDevice device, int status) {
            super.onNotificationSent(device, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                log("Notification sent successfully to device: " + device.getAddress());
            } else {
                log("Failed to send notification to device: " + device.getAddress());
            }
        }
        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId,
                                             BluetoothGattDescriptor descriptor,
                                             boolean preparedWrite, boolean responseNeeded,
                                             int offset, byte[] value) {
            if (bluetoothGattServer != null && isServerRunning) {
                descriptor.setValue(value);
                log("Descriptor write: " + descriptor.getUuid() + " value: " + bytesToHex(value));

                if (responseNeeded) {
                    bluetoothGattServer.sendResponse(device, requestId,
                            BluetoothGatt.GATT_SUCCESS, offset, null);
                }


                if (descriptor.getUuid().equals(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))) {
                    if (value != null && value.length > 0) {
                        if (value[0] == 0x01) {
                            log("Client enabled notifications/indications for characteristic");
                        } else if (value[0] == 0x00) {
                            log("Client disabled notifications/indications for characteristic");
                        }
                    }
                }
            }
        }


    };


    private void log(String message) {
        if (logTextView != null) {
            logTextView.append(message + "\n");
//            logTextView.scrollTo(0, logTextView.getBottom());
        }
    }


    public void logCharacteristicValue(BluetoothGattCharacteristic characteristic) {
        byte[] value = characteristic.getValue();
        StringBuilder hexString = new StringBuilder();
        for (byte b : value) {

            hexString.append(String.format("%02X ", b));
        }
        if (value != null) {
            String strValue = new String(value);
            Log.d("Characteristic Value", "Value: " + hexString.toString());
        } else {
            Log.d("Characteristic Value", "Value is null");
        }
    }
    public static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {

            hexString.append(String.format("%02X", b));
        }
        return hexString.toString();
    }

}
