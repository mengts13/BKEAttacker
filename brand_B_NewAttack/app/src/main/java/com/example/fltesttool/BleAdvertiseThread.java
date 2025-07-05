package com.example.fltesttool;

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

import com.example.fltesttool.utils.ConfigManager;
import com.example.fltesttool.utils.Conversion;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BleAdvertiseThread extends Thread {
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeAdvertiser bluetoothLeAdvertiser;
    private boolean isRunning = false;
    private Context context;
    private final List<AdvertisingSetCallback> advertisingCallbacks = new ArrayList<>();
    public BleAdvertiseThread(BluetoothAdapter bluetoothAdapter, Context context) {
        this.bluetoothAdapter = bluetoothAdapter;
        this.context = context;
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

//            final byte[] broadcastData = Conversion.hexStringToBytes("021574278BDAB64445208F0C720EAF05993501006265C5");
//
//            AdvertiseData data = new AdvertiseData.Builder()
//                    .addManufacturerData(0x004C, new byte[]{0x02, 0x15, 0x74, 0x27, (byte) 0x8B, (byte) 0xDA, (byte) 0xB6, 0x44, 0x45, 0x20, (byte) 0x8F, 0x0C, 0x72, 0x0E, (byte) 0xAF, 0x05, (byte) 0x99, 0x35, 0x01, 0x00, 0x62, 0x65, (byte) 0xC5})
//                    .build();
//
//            AdvertiseData adv_resp_data = new AdvertiseData.Builder()
//                    .addServiceUuid(new ParcelUuid(UUID.fromString("00001122-0000-1000-8000-00805f9b34fb")))
//                    .setIncludeDeviceName(true)
//                    .build();

            //geely
            final byte[] broadcastData1 = Conversion.hexStringToBytes("090602043532C5AF0EC1A3FA4BFF7E8FB0FF");
            final byte[] broadcastData2 = Conversion.hexStringToBytes("02153654373950344E31505630303032353200000000C1");

            AdvertiseData data = new AdvertiseData.Builder()
                    .addManufacturerData(0x01FE, broadcastData1)
                    .addServiceData(new ParcelUuid(UUID.fromString("00000600-0000-1000-8000-00805f9b34fb")), Conversion.hexStringToBytes("0000"))
//                    .addManufacturerData(0x004C, broadcastData2)
//                    .setIncludeDeviceName(true)
                    .build();

            AdvertiseData adv_resp_data = new AdvertiseData.Builder()
//                    .addManufacturerData(0x01FE, broadcastData1)
//                    .addManufacturerData(0x004C, broadcastData2)
                    .addServiceUuid(new ParcelUuid(UUID.fromString("0000fdfd-0000-1000-8000-00805f9b34fb")))
//                    .addServiceData(new ParcelUuid(UUID.fromString("00000900-0000-1000-8000-00805f9b34fb")), Conversion.hexStringToBytes("0000"))
                    .setIncludeDeviceName(true)
                    .build();

            //geely2
            final byte[] broadcastData = Conversion.hexStringToBytes("02153654373950344E31505630303032353200000000C1");
            byte[] serviceData = new byte[] {(byte) 0x00, (byte) 0x00};

            AdvertiseData data2 = new AdvertiseData.Builder()
                    .addManufacturerData(0x004C, broadcastData)
                    .build();

            AdvertiseData adv_resp_data2 = new AdvertiseData.Builder()
                    .addServiceData(new ParcelUuid(UUID.fromString("00000610-0000-1000-8000-00805f9b34fb")), serviceData)
                    .setIncludeDeviceName(true)
                    .build();

            AdvertiseData data3 = new AdvertiseData.Builder()
                    .addManufacturerData(0x004C, broadcastData)
                    .build();

            AdvertiseData adv_resp_data3 = new AdvertiseData.Builder()
                    .addServiceData(new ParcelUuid(UUID.fromString("00000610-0000-1000-8000-00805f9b34fb")), serviceData)
                    .setIncludeDeviceName(true)
                    .build();

            //lixiang
//            final byte[] broadcastData1 = Conversion.hexStringToBytes("01000192B5C1");
//            AdvertiseData data = new AdvertiseData.Builder()
//                    .addManufacturerData(0x000D, broadcastData1)
//                    .setIncludeDeviceName(true)
//                    .build();
//
//            AdvertiseData adv_resp_data = new AdvertiseData.Builder()
//                    .addServiceUuid(new ParcelUuid(UUID.fromString("30383033-3933-3152-3534-31423233584C")))
//                    .build();


            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {

//                showToast("Bug1");

//                return;
            }
            try {
//                bluetoothLeAdvertiser.startAdvertising(settings, data, adv_resp_data,advertiseCallback);
                AdvertisingSetParameters advertisingSetParameters=new AdvertisingSetParameters.Builder()
                        .setLegacyMode(true)
                        .setConnectable(true)
                        .setScannable(true)
                        .setInterval(AdvertisingSetParameters.INTERVAL_LOW)
                        .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_HIGH)
                        .build();


//                int broadcastNum = (int)configManager.get("broadcast_num");
                int broadcastNum = 3;
                for (int i = 0; i < broadcastNum; i++) {
                    final int setNumber = i + 1;

                    AdvertisingSetCallback callback = new AdvertisingSetCallback() {
                        @Override
                        public void onAdvertisingSetStarted(AdvertisingSet advertisingSet, int txPower, int status) {
                            super.onAdvertisingSetStarted(advertisingSet, txPower, status);
                            if (status == AdvertisingSetCallback.ADVERTISE_SUCCESS) {
                                showToast("radio broadcasting collection" + setNumber + "Successful startup");
                            } else {
                                showToast("radio broadcasting collection" + setNumber + "Startup failed with error code: " + status);
                            }
                        }

                        @Override
                        public void onAdvertisingSetStopped(AdvertisingSet advertisingSet) {
                            super.onAdvertisingSetStopped(advertisingSet);
                            showToast("radio broadcasting collection" + setNumber + "ceased");
                        }
                    };

                    try {
                        bluetoothLeAdvertiser.startAdvertisingSet(
                                advertisingSetParameters,
                                data,
                                adv_resp_data,
                                null,
                                null,
                                callback
                        );
                        advertisingCallbacks.add(callback);
                    } catch (Exception e) {
                        showToast("radio broadcasting collection" + setNumber + "activation anomaly");
                    }
                }

//                bluetoothLeAdvertiser.startAdvertisingSet(advertisingSetParameters, data, adv_resp_data, null,null ,advertiseCallback1);
//
//
//                bluetoothLeAdvertiser.startAdvertisingSet(advertisingSetParameters2, data2, adv_resp_data2, null, null,advertiseCallback2);


//                bluetoothLeAdvertiser.startAdvertisingSet(advertisingSetParameters3, data3, adv_resp_data3, null, null,advertiseCallback3);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            isRunning = true;
//            showToast("Started advertising in thread");
        } else {
            showToast("BluetoothLeAdvertiser is null");
        }
    }


    public void stopAdvertising() {
        if (bluetoothLeAdvertiser != null && isRunning) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {

//                showToast("Bug2");
//                return;
            }
//            bluetoothLeAdvertiser.stopAdvertising(advertiseCallback);
//            bluetoothLeAdvertiser.stopAdvertisingSet(advertiseCallback1);
//            bluetoothLeAdvertiser.stopAdvertisingSet(advertiseCallback2);

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
                showToast( "Successful launch of Radio Episode 1");
            } else {
                showToast("Broadcast set 1 failed to start with error code:" + status);
            }
        }

    };
    AdvertisingSetCallback advertiseCallback2= new AdvertisingSetCallback() {

        @Override
        public void onAdvertisingSetStarted(AdvertisingSet advertisingSet, int txPower, int status) {
            super.onAdvertisingSetStarted(advertisingSet, txPower, status);
            if (status == AdvertisingSetCallback.ADVERTISE_SUCCESS) {
                showToast("Successful launch of Radio Episode 2");
            } else {
                showToast("Broadcast Set 2 failed to start with error code: " + status);
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