package com.example.fltesttool.utils;

import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.util.Log;

import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.List;
import java.util.Map;

public class ConfigManager {
    private static final String TAG = "ConfigManager";
    private static final String CONFIG_DIR = "configs/";
    private static final String CONFIG_DIR_NAME = "configs";
    private static final String DEFAULT_CONFIG_NAME = "device.yml";
    private static final String ASSETS_CONFIG_PATH = "configs/target.yml";

    private Map<String, Object> configMap;
    private File configDir;
    private File configFile;

    private ConfigManager() {}


    private static class Holder {
        private static final ConfigManager INSTANCE = new ConfigManager();
    }

    public static ConfigManager getInstance() {
        return Holder.INSTANCE;
    }


    public void init(Context context) {
        configDir = new File(context.getFilesDir(), CONFIG_DIR_NAME);
        configFile = new File(configDir, DEFAULT_CONFIG_NAME);


        if (!configFile.exists()) {
            boolean success = copyDefaultConfigFromAssets(context);
            if (!success) {
                Log.e(TAG, "Failed to copy config file from assets.");
                return;
            }
        }
        loadConfigFromFile();
    }


    private boolean copyDefaultConfigFromAssets(Context context) {
        try (InputStream is = context.getAssets().open(ASSETS_CONFIG_PATH);
             FileOutputStream fos = new FileOutputStream(configFile);
             Writer writer = new OutputStreamWriter(fos)) {

            Yaml yaml = new Yaml();
            Map<String, Object> defaultConfig = yaml.load(is);
            yaml.dump(defaultConfig, writer);

            Log.d(TAG, "Default config copied from assets.");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error copying config file", e);
            return false;
        }
    }


    private void loadConfigFromFile() {
        try (FileInputStream fis = new FileInputStream(configFile)) {
            Yaml yaml = new Yaml();
            configMap = yaml.load(fis);
            Log.d(TAG, "Config loaded successfully.");
        } catch (IOException e) {
            Log.e(TAG, "Failed to load config file", e);
        }
    }





    public Object get(String key) {
        String[] keys = key.split("\\.");
        Map<String, Object> currentMap = configMap;
        for (int i = 0; i < keys.length - 1; i++) {
            currentMap = (Map<String, Object>) currentMap.get(keys[i]);
            if (currentMap == null) return null;
        }
        return currentMap.get(keys[keys.length - 1]);
    }



    public static int parseProperties(List<String> props) {
        int prop = 0;
        for (String p : props) {
            switch (p.toLowerCase()) {
                case "broadcast":
                    prop |= BluetoothGattCharacteristic.PROPERTY_BROADCAST; // 0x01
                    break;
                case "read":
                    prop |= BluetoothGattCharacteristic.PROPERTY_READ; // 0x02
                    break;
                case "write_no_response":
                    prop |= BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE; // 0x04
                    break;
                case "write":
                    prop |= BluetoothGattCharacteristic.PROPERTY_WRITE; // 0x08
                    break;
                case "notify":
                    prop |= BluetoothGattCharacteristic.PROPERTY_NOTIFY; // 0x10
                    break;
                case "indicate":
                    prop |= BluetoothGattCharacteristic.PROPERTY_INDICATE; // 0x20
                    break;
                case "signed_write":
                    prop |= BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE; // 0x40
                    break;
                case "extended_props":
                    prop |= BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS; // 0x80
                    break;
                case "authenticated_signed_writes":
                    prop |= 0x100; // PROPERTY_AUTHENTICATED_SIGNED_WRITES
                    break;
                case "reliable_write":
                    prop |= 0x200; // PROPERTY_RELIABLE_WRITE
                    break;
                case "writable_auxiliaries":
                    prop |= 0x400; // PROPERTY_WRITABLE_AUXILIARIES
                    break;
                default:
                    Log.w("BleConfig", "Unknown property: " + p);
                    break;
            }
        }
        return prop;
    }
    public static int parsePermissions(List<String> perms) {
        int permission = 0;
        for (String p : perms) {
            switch (p.toLowerCase()) {
                case "read":
                    permission |= BluetoothGattCharacteristic.PERMISSION_READ; // 0x01
                    break;
                case "read_encrypted":
                    permission |= BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED; // 0x02
                    break;
                case "read_encrypted_mitm":
                    permission |= BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED_MITM; // 0x04
                    break;
                case "write":
                    permission |= BluetoothGattCharacteristic.PERMISSION_WRITE; // 0x10
                    break;
                case "write_encrypted":
                    permission |= BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED; // 0x20
                    break;
                case "write_encrypted_mitm":
                    permission |= BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED_MITM; // 0x40
                    break;
                case "write_signed":
                    permission |= BluetoothGattCharacteristic.PERMISSION_WRITE_SIGNED; // 0x80
                    break;
                case "write_signed_mitm":
                    permission |= BluetoothGattCharacteristic.PERMISSION_WRITE_SIGNED_MITM; // 0x100
                    break;
                default:
                    Log.w("BleConfig", "Unknown permission: " + p);
                    break;
            }
        }
        return permission;
    }

}
