// Client.java

package com.example.bkeattacker;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;

public class Client {

    private static final String TAG = "Client";
    private final Context context;
    private final ClientCallback gattCallback;
    private final ClientImpl clientImpl;

    public Client(@NonNull Context context) {
        this.context = context.getApplicationContext(); // Avoid memory leaks
        this.gattCallback = new ClientCallback();
        this.clientImpl = new ClientImpl(this.context, gattCallback);
    }

    /**
     * Starts the RPC consumer thread (must be called after Bluetooth permissions are granted and BLE is available)
     */
    public void start() {
        Log.d(TAG, "Starting BLE client...");
        clientImpl.startRpcConsumer();
    }

    /**
     * Stops the RPC consumer thread and disconnects all connections
     */
    public void stop() {
        Log.d(TAG, "Stopping BLE client...");
        clientImpl.stopRpcConsumer();
    }

    /**
     * Optional: Set a fixed target device address (if not specified by the Go layer)
     */
    public void setTargetDeviceAddress(String address) {
        clientImpl.setTargetDeviceAddress(address);
    }
}
