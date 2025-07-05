package com.example.fltesttool;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.BluetoothLeScanner;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
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

public class DeviceAdapter extends ArrayAdapter<BluetoothDevice> {
    private final Context context;
    private final List<BluetoothDevice> devices;

    public DeviceAdapter(Context context, List<BluetoothDevice> devices) {
        super(context, 0, devices);
        this.context = context;
        this.devices = devices;
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {

        BluetoothDevice device = devices.get(position);


        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_2, parent, false);
        }


        TextView text1 = convertView.findViewById(android.R.id.text1);
        TextView text2 = convertView.findViewById(android.R.id.text2);


        String deviceName = device.getName();
        if (deviceName == null) {
            deviceName = "Unknown Device";
        }
        text1.setText(deviceName);
        text2.setText(device.getAddress());

        return convertView;
    }
}
