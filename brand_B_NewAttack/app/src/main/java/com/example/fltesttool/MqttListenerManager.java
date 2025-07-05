package com.example.fltesttool;
import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

import java.util.UUID;
public class MqttListenerManager {
    private MqttManager mqttManager;
    private MqttMessageListener messageListener;


    public MqttListenerManager(Context context, MqttMessageListener messageListener) {
        this.messageListener = messageListener;

        mqttManager = MqttManager.getInstance(context);
        mqttManager.addMessageListener(new MqttManager.MqttMessageListener() {
            @Override
            public void onMessageReceived(String topic, String message) throws JSONException {

                if (messageListener != null) {
                    messageListener.onMessageReceived(message);
                }
            }
        });
    }

    public void subscribe(String topic) throws MqttException {
        mqttManager.subscribe(topic);
    }


    public void disconnect() throws MqttException {
        mqttManager.disconnect();
    }


    public interface MqttMessageListener {
        void onMessageReceived(String message) throws JSONException;
    }
}
