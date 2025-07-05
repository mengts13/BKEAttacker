package com.example.fltesttool.ui.client;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import com.example.fltesttool.MqttManager;
import com.example.fltesttool.databinding.FragmentClientBinding;
import com.example.fltesttool.databinding.FragmentServerBinding;

import org.json.JSONException;

import java.util.UUID;
import com.example.fltesttool.databinding.FragmentServerBinding;
public class ClientFragment extends Fragment implements MqttManager.MqttMessageListener {

    private MqttManager mqttManager;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice mDevice;
    private String mac;
    private boolean isServiceConnected;
    private Context context;

    private TextView log_data;
    private FragmentClientBinding binding;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        mqttManager = MqttManager.getInstance(context);
        mqttManager.addMessageListener(this);
    }

    @Override
    public void onMessageReceived(String topic, String message) throws JSONException {
        Log.d("MQTT", "onMessageReceived: Topic=" + topic + ", Message=" + message);
    }

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        if(Looper.myLooper() == null){
            Looper.prepare();
        }


        binding = com.example.fltesttool.databinding.FragmentClientBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        log_data   = binding.log;
        return root;
    }
}