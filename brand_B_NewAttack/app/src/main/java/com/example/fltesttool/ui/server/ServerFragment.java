package com.example.fltesttool.ui.server;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import com.example.fltesttool.MqttManager;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.UUID;
import android.widget.TextView;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelUuid;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.example.fltesttool.databinding.FragmentServerBinding;


import com.example.fltesttool.databinding.FragmentServerBinding;

public class ServerFragment extends Fragment implements MqttManager.MqttMessageListener {

    private MqttManager mqttManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothManager mBluetoothManager;
    private BluetoothLeAdvertiser mBluetoothLeAdvertiser;
    private BluetoothGattServer mBluetoothGattServer;
    private BluetoothDevice mBluetoothDevice;

    private TextView log_data;
    private FragmentServerBinding binding;
    private final MyHandler mhandler = new MyHandler(Looper.myLooper());
    private Context context;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("VehicleFragment", "onCreate: Initializing MQTT and BLE components.");
        mqttManager = MqttManager.getInstance(context);
        mqttManager.addMessageListener(this);

        try {
            mqttManager.subscribe("cmd");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onMessageReceived(String topic, String message) {
        Log.d("MQTT", "onMessageReceived: Topic=" + topic + ", Message=" + message);
    }
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        if(Looper.myLooper() == null){
            Looper.prepare();
        }


        binding = FragmentServerBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        log_data   = binding.log;
        return root;
    }
    /**
     * Handler to operate UI in non-main thread
     */
    class MyHandler extends Handler {
        public MyHandler(@NonNull Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            Log.d("Handler", "handleMessage: Received message with what=" + msg.what);
        }
    }
}