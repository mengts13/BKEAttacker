package com.example.fltesttool;

import static com.example.fltesttool.utils.ToastUtils.showToast;

import android.app.Activity;
import android.content.Context;
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
import java.util.Base64;
import java.util.List;

import java.util.UUID;
public class MqttManager {
    private static MqttManager instance;
    private MqttClient mqttClient;
    private String brokerUrl = "tcp://47.96.101.174:55052";
    private String clientId = "AndroidApp-" + UUID.randomUUID().toString();
    private Context context;
    private byte[] value_received;

    private final List<MqttMessageListener> listeners = new ArrayList<>();


    private MqttManager(Context context) {
        try {
            mqttClient = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
            connect();
        } catch (MqttException e) {
            e.printStackTrace();
        }
        this.context = context;
    }


    public static synchronized MqttManager getInstance(Context context) {
        if (instance == null) {
            instance = new MqttManager(context);
        }
        return instance;
    }


    private void connect() throws MqttException {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(20);

        mqttClient.connect(options);
        mqttClient.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                showToast("MQTT connection lost:" + cause.getMessage());
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                String payload = new String(message.getPayload());
                showToast("Message received: Subject = " + topic + ", Message = " + payload);
                byte[] array = Base64.getDecoder().decode((payload));
                if(array.length > 2){
                    value_received = array;
                }

                notifyListeners(topic, payload);
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                showToast("Message sent");
            }
        });
    }

    public void subscribe(String topic) throws MqttException {
        mqttClient.subscribe(topic);
    }

    public void publish(String topic, String payload) throws MqttException {
        MqttMessage message = new MqttMessage(payload.getBytes());
        message.setQos(1);
        mqttClient.publish(topic, message);
    }


    public void disconnect() throws MqttException {
        if (mqttClient != null && mqttClient.isConnected()) {
            mqttClient.disconnect();
        }
    }

    public void addMessageListener(MqttMessageListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    public byte[] getValue_received() {
        return value_received;
    }


    public void removeMessageListener(MqttMessageListener listener) {
        listeners.remove(listener);
    }


    private void notifyListeners(String topic, String message) throws JSONException {
        for (MqttMessageListener listener : listeners) {
            listener.onMessageReceived(topic, message);
        }
    }
    public interface MqttMessageListener {
        void onMessageReceived(String topic, String message) throws JSONException;
    }

    public boolean isconnect() {
        if(mqttClient.isConnected()){
            return true;
        }else{
            return false;
        }
    }


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

