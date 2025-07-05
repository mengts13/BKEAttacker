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

public class MqttTest {
    public MqttManager instance;
    public String message;
    boolean need_pub = false;


    public MqttTest(Context context, String message, boolean need_pub) throws MqttException {
        this.need_pub = need_pub;
        instance = MqttManager.getInstance(context);
        this.message = message;
        if(instance.isconnect()) {
            Log.e("mqttTest","success connect");
        }else {
            Log.e("mqttTest","failed connect");
        }
        send_message();
    }

    private void send_message() throws MqttException {
        instance.subscribe("test/android");
        if(need_pub){
            instance.publish("test/android", message);
        }

    }

    public void mqttdisconnect() throws MqttException {
        instance.disconnect();
    }

}
