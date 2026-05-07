package com.example.bkeattacker;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.util.Log;
import com.example.bkeattacker.utils.Conversion;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import com.example.bkeattacker.ServerStub;

public class ClientCallback extends BluetoothGattCallback {

    private static final String TAG = "ClientCallback";
    private Map<String, BluetoothGattCharacteristic> gattcharacteristicMap = null;

    public void setGattcharacteristicMap(Map<String, BluetoothGattCharacteristic> gattcharacteristicMap) {
        this.gattcharacteristicMap = gattcharacteristicMap;
    }

    // —————— BLE Callbacks ——————

    @SuppressLint("MissingPermission")
    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        log("onConnectionStateChange: status=" + status + ", newState=" + newState);
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            log("onConnectionStateChange: discoverServices");
            gatt.discoverServices();
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            if (gattcharacteristicMap != null) {
                gattcharacteristicMap.clear();
            }
        }
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        byte[] value = characteristic.getValue();
        String serviceUuid = characteristic.getService().getUuid().toString();
        String charUuid = characteristic.getUuid().toString();
        log("onCharacteristicChanged: service=" + serviceUuid +
                ", char=" + charUuid +
                ", data=" + Conversion.Bytes2HexString(value));
        // Forward notification to native ServerStub
        ServerStub.notifyCharacteristicChanged(serviceUuid, charUuid, value);
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        log("onServicesDiscovered: trigger");
        if (status == BluetoothGatt.GATT_SUCCESS) {
            List<BluetoothGattService> services = gatt.getServices();
            if (gattcharacteristicMap != null) {
                gattcharacteristicMap.clear();
            }
            if (services != null) {
                for (BluetoothGattService service : services) {
                    UUID serviceUuid = service.getUuid();
                    for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                        String charUuid = characteristic.getUuid().toString();
                        String key = serviceUuid.toString() + "_" + charUuid;
                        if (gattcharacteristicMap != null) {
                            gattcharacteristicMap.put(key, characteristic);
                        }
                        log("add map: " + key);
                    }
                }
                log("onServicesDiscovered: characteristicMap populated with " +
                        (gattcharacteristicMap != null ? gattcharacteristicMap.size() : 0) + " characteristics.");
            } else {
                log("onServicesDiscovered: No services found.");
            }

            // Enable indication or notification
            if (gattcharacteristicMap != null && !gattcharacteristicMap.isEmpty()) {
                for (Map.Entry<String, BluetoothGattCharacteristic> entry : gattcharacteristicMap.entrySet()) {
                    BluetoothGattCharacteristic characteristic = entry.getValue();
                    int properties = characteristic.getProperties();

                    // Check if notify or indicate is supported
                    boolean supportsNotify = (properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0;
                    boolean supportsIndicate = (properties & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0;

                    if (!supportsNotify && !supportsIndicate) {
                        continue; // Skip characteristics that do not support notify/indicate
                    }

                    // Enable Android local notification reception (mandatory!)
                    gatt.setCharacteristicNotification(characteristic, true);

                    // Find CCCD (0x2902)
                    BluetoothGattDescriptor cccd = characteristic.getDescriptor(
                            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                    );

                    if (cccd != null) {
                        // Decide value to write based on properties
                        if (supportsIndicate) {
                            // Prioritize enabling indication (can be changed to prioritize notify as needed)
                            cccd.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                        } else if (supportsNotify) {
                            cccd.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        }

                        boolean success = gatt.writeDescriptor(cccd);
                        if (!success) {
                            log("Failed to write CCCD for characteristic: " + characteristic.getUuid());
                        }
                        // ⚠️ Important: Since GATT operations are serial, consecutive calls to writeDescriptor might fail
                    } else {
                        log("CCCD not found for characteristic: " + characteristic.getUuid());
                    }
                }
            }

            // Request MTU
            int mtu = ServerStub.getMTU();
            boolean requested = gatt.requestMtu(mtu);
            if (!requested) {
                log("onServicesDiscovered: Failed to request MTU.");
            }
        } else {
            log("onServicesDiscovered: error status=" + status);
        }
    }

    @Override
    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        byte[] value = characteristic.getValue();
        String serviceUuid = characteristic.getService().getUuid().toString();
        String charUuid = characteristic.getUuid().toString();
        log("onCharacteristicRead: service=" + serviceUuid +
                ", char=" + charUuid +
                ", status=" + status +
                ", data=" + Conversion.Bytes2HexString(value));
        // Forward read response to native
        ServerStub.sendReadCharacteristicResponse(status, value);
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        byte[] value = characteristic.getValue();
        String serviceUuid = characteristic.getService().getUuid().toString();
        String charUuid = characteristic.getUuid().toString();
        log("onCharacteristicWrite: service=" + serviceUuid +
                ", char=" + charUuid +
                ", status=" + status +
                ", data=" + Conversion.Bytes2HexString(value));
        ServerStub.sendWriteCharacteristicResponse(status, value);
    }

    @Override
    public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        byte[] value = descriptor.getValue();
        String serviceUuid = descriptor.getCharacteristic().getService().getUuid().toString();
        String charUuid = descriptor.getCharacteristic().getUuid().toString();
        String descUuid = descriptor.getUuid().toString();
        log("onDescriptorRead: service=" + serviceUuid +
                ", char=" + charUuid +
                ", desc=" + descUuid +
                ", status=" + status +
                ", data=" + Conversion.Bytes2HexString(value));
        ServerStub.sendReadDescriptorResponse(status, value);
    }

    @Override
    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        byte[] value = descriptor.getValue();
        String serviceUuid = descriptor.getCharacteristic().getService().getUuid().toString();
        String charUuid = descriptor.getCharacteristic().getUuid().toString();
        String descUuid = descriptor.getUuid().toString();
        log("onDescriptorWrite: service=" + serviceUuid +
                ", char=" + charUuid +
                ", desc=" + descUuid +
                ", status=" + status +
                ", data=" + Conversion.Bytes2HexString(value));
        ServerStub.sendWriteDescriptorResponse(status, value);
    }

    @Override
    public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            log("onMtuChanged: MTU changed to: " + mtu);
        } else {
            log("onMtuChanged: MTU change failed, status: " + status);
        }
    }

    // —————— Logging ——————
    private void log(String message) {
        Log.d(TAG, message);
        WebLogManager.get().log(message, WebLogManager.LOG_RECV);
        // Trigger the frontend to enter the "unlock instruction sending" stage (p51)
        WebLogManager.get().callJs("window.enterUserSendStage && window.enterUserSendStage();");
    }
}
