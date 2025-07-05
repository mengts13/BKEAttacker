package com.example.fltesttool;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import java.nio.charset.StandardCharsets;
import android.Manifest;
//import com.example.fltesttool.ui.client.ClientFragment;
//import com.example.fltesttool.ui.server.ServerFragment;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.example.fltesttool.utils.ConfigManager;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.json.JSONException;

import java.io.IOException;

public class MainActivity extends AppCompatActivity implements MqttListenerManager.MqttMessageListener, ServerFragment.OnBleDataReceivedListener, ClientFragment.OnClientFragmentReadyListener {

    private Button clientButton;
    private Button serverButton;
    private Button mqttButton;
    private BleAdvertiseThread bleAdvertiseThread;
    private MqttTest mqtttest;
    private String message;
    private EditText editTextMessage;
    private Button buttonSave;
    private MqttListenerManager mqttListenerManager;
    private BroadcastReceiver bleDataReceiver;
    private byte[] rece_value;

    @Override
    protected void onCreate(Bundle savedInstanceState) {


        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        clientButton = findViewById(R.id.client_button);
        serverButton = findViewById(R.id.server_button);
        mqttButton = findViewById(R.id.mqtt_button);
        editTextMessage = findViewById(R.id.editTextMessage);
        buttonSave = findViewById(R.id.buttonSave);

        loadFragment(new ClientFragment());

        bleDataReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("com.yourpackage.BLE_DATA_RECEIVED".equals(intent.getAction())) {
                    String uuid = intent.getStringExtra("characteristic_uuid");
                    byte[] receivedValue = intent.getByteArrayExtra("received_value");
                    rece_value = receivedValue;
                    if (receivedValue != null) {
//                        String receivedString = new String(receivedValue, StandardCharsets.UTF_8);
                        Log.d("MainActivity", "Data received from BLE Server, UUID: " + uuid);
                        Toast.makeText(MainActivity.this, "Key saved", Toast.LENGTH_SHORT).show();


                        }
                    }
                }
        };


        mqttListenerManager = new MqttListenerManager(MainActivity.this, this);

//        try {
//
////            mqtttest = new MqttTest(MainActivity.this, message, false);
//
//
//        } catch (MqttException e) {
//            throw new RuntimeException(e);
//        }



        buttonSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                message = editTextMessage.getText().toString();



                Toast.makeText(MainActivity.this, "Message saved", Toast.LENGTH_SHORT).show();
            }
        });


        clientButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadFragment(new ClientFragment());
                stopBleAdvertising();

            }
        });

        mqttButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Toast.makeText(MainActivity.this, "MQTT TEST", Toast.LENGTH_SHORT).show();
//                try {
//
//                        mqtttest = new MqttTest(MainActivity.this, message, true);
//
//
//                } catch (MqttException e) {
//                    throw new RuntimeException(e);
//                }

            }
        });

        serverButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadFragment(new ServerFragment());
                startBleAdvertising();

            }
        });

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED ) {

            requestPermissions(new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.BLUETOOTH_ADVERTISE
            }, 100);
        }


    }

    @Override
    public void onBleDataReceived(String uuid, byte[] value) {
        if (value != null) {
            if (value.length > 0) Log.d("MainActivity", "value length greater than 0:" + bytesToHex(value));
            Log.d("MainActivity", "Data received via BLE interface callback UUID. " + uuid);

            rece_value = value;

        }
    }

    @Override
    public void onClientFragmentReady(ClientFragment clientFragment) {

        if (rece_value != null && rece_value.length > 0) {
            clientFragment.save_Key(rece_value);
            Log.d("MainActivity", "ClientFragment is ready and has passed the saved key.");
        } else {
            Log.d("MainActivity", "ClientFragment is ready, but there is no key to be passed.");
        }
    }



    private void loadFragment(Fragment fragment) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, fragment);
        transaction.addToBackStack(null);
        transaction.commit();
//        saveKeyToClient(rece_value);
    }

    public void saveKeyToClient(byte[] key) {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (fragment instanceof ClientFragment) {
            ((ClientFragment) fragment).save_Key(key);
        } else {
            Toast.makeText(this, "ClientFragment is not active", Toast.LENGTH_SHORT).show();
        }
    }
    @Override
    public void onMessageReceived(String message) throws JSONException {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, "Receive key：" + message, Toast.LENGTH_SHORT).show();


                MainActivity.this.message = message;
//                writeKeyToClient(message);
            }
        });
    }


    private void startBleAdvertising() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Bluetooth is not available or not enabled", Toast.LENGTH_SHORT).show();
            return;
        }

        if (bleAdvertiseThread == null) {

            bleAdvertiseThread = new BleAdvertiseThread(bluetoothAdapter, MainActivity.this);
            bleAdvertiseThread.start();
            Toast.makeText(this, "Started BLE Advertising", Toast.LENGTH_SHORT).show();
        }
    }


    private void stopBleAdvertising() {
        if (bleAdvertiseThread != null) {
            bleAdvertiseThread.stopAdvertising();
            bleAdvertiseThread = null;
            Toast.makeText(this, "Stopped BLE Advertising", Toast.LENGTH_SHORT).show();
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopBleAdvertising();
//        try {
////            mqtttest.mqttdisconnect();
//        } catch (MqttException e) {
//            throw new RuntimeException(e);
//        }
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {

            hexString.append(String.format("%02X", b));
        }
        return hexString.toString();
    }


}
