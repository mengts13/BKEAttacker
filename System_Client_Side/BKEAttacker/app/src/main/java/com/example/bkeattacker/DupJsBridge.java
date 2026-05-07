package com.example.bkeattacker;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;
import android.webkit.JavascriptInterface;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * WebView JS Bridge: Frontend H5 calls -> This class -> DupClientManager (JNI native)
 * Note: long-token automatic reconnection is handled in MainActivity.onCreate.
 */
public class DupJsBridge {

    private static final String TAG = "DupJsBridge";
    private final Context appCtx;
    private final String tokenDir;

    /**
     * @param context Context
     * @param tokenDir Directory for token files (will be set in MainActivity via DupClientManager.setTokenFileDir)
     */
    public DupJsBridge(Context context, String tokenDir) {
        this.appCtx = context.getApplicationContext();
        this.tokenDir = tokenDir;
    }

    // ============ Configuration/Utilities ============

    /** Reads the text from assets/config.yaml */
    @JavascriptInterface
    public String readConfigYaml() {
        try {
            AssetManager am = appCtx.getAssets();
            InputStream is = am.open("config.yaml");
            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
            br.close();
            is.close();
            return sb.toString();
        } catch (Throwable t) {
            Log.e(TAG, "readConfigYaml error", t);
            return "";
        }
    }

    /**
     * Checks if the auth token after login is persistently ready.
     * Compatible with the two filenames currently written by the backend: long-token-message / long-token-stream.
     */
    @JavascriptInterface
    public boolean hasAuthToken() {
        try {
            File f1 = new File(tokenDir, "long-token-message");
            File f2 = new File(tokenDir, "long-token-stream");
            boolean ok1 = f1.exists() && f1.isFile() && f1.length() > 0;
            boolean ok2 = f2.exists() && f2.isFile() && f2.length() > 0;
            boolean ok = ok1 || ok2;
            Log.d(TAG, "hasAuthToken=" + ok +
                    ", msgPath=" + f1.getAbsolutePath() + "(" + f1.length() + ")" +
                    ", streamPath=" + f2.getAbsolutePath() + "(" + f2.length() + ")");
            return ok;
        } catch (Throwable t) {
            Log.e(TAG, "hasAuthToken error", t);
            return false;
        }
    }

    /**
     * Deprecated: Old single address setting.
     * Now changed to set message and stream servers separately, please use setServerAddrs(ip, messagePort, streamPort)
     */
    @JavascriptInterface
    public void setServerAddr(String addr) {
        Log.w(TAG, "setServerAddr is deprecated. Please use setServerAddrs(ip, messagePort, streamPort).");
    }

    /** Sets message and stream server addresses (same IP, different ports) */
    @JavascriptInterface
    public void setServerAddrs(String ip, int messagePort, int streamPort) {
        if (ip == null) return;
        try {
            String msgAddr = ip.trim() + ":" + messagePort;
            String streamAddr = ip.trim() + ":" + streamPort;
            Log.d(TAG, "SetMessageServerAddr=" + msgAddr + ", SetStreamServerAddr=" + streamAddr);
            DupClientManager.SetMessageServerAddr(msgAddr);
            DupClientManager.SetStreamServerAddr(streamAddr);
        } catch (Throwable t) {
            Log.e(TAG, "setServerAddrs error", t);
        }
    }

    // ============ Session/Login ============

    /** Gets the current timestamp (nanoseconds, from native) */
    @JavascriptInterface
    public long readCurrentTimestampNs() {
        try {
            return DupClientManager.ReadCurrentTimestamp();
        } catch (Throwable t) {
            Log.e(TAG, "readCurrentTimestampNs error", t);
            return 0;
        }
    }

    /** Starts the client (some devices/implementations require StartClient before Login) */
    @JavascriptInterface
    public int startClient() {
        try {
            int rc = DupClientManager.StartClient();
            Log.d(TAG, "StartClient returned rc=" + rc);
            return rc;
        } catch (Throwable t) {
            Log.e(TAG, "StartClient error", t);
            return -1;
        }
    }

    /** Login: Proactively call StartClient once before login to avoid "uninitialized" state (idempotent) */
    @JavascriptInterface
    public int login(String account, String password) {
        try {
            // Ensure client is started first (idempotent call)
            int rcStart = -999;
            try {
                rcStart = DupClientManager.StartClient();
                Log.d(TAG, "StartClient returned rc=" + rcStart);
            } catch (Throwable te) {
                Log.w(TAG, "StartClient call failed (ignored if already started)", te);
            }

            if (account == null) account = "";
            if (password == null) password = "";

            String acc = account.trim();
            Log.d(TAG, "Login called. account=" + acc + ", passwordLen=" + password.length());
            int rc = DupClientManager.Login(acc, password);
            Log.d(TAG, "Login returned rc=" + rc);
            return rc;
        } catch (Throwable t) {
            Log.e(TAG, "Login error", t);
            return -1;
        }
    }

    // ============ Role/Pairing/PIN Flow ============

    /** Registers self role (e.g., "U"/"V" etc.) */
    @JavascriptInterface
    public int registerSelf(String role) {
        try {
            if (role == null) role = "";
            int rc = DupClientManager.RegisterSelf(role);
            Log.d(TAG, "RegisterSelf(" + role + ") rc=" + rc);
            return rc;
        } catch (Throwable t) {
            Log.e(TAG, "RegisterSelf error", t);
            return -1;
        }
    }

    /** Registers pairing (e.g., "U"/"V" etc.) */
    @JavascriptInterface
    public int registerPair(String role) {
        try {
            if (role == null) role = "";
            int rc = DupClientManager.RegisterPair(role);
            Log.d(TAG, "RegisterPair(" + role + ") rc=" + rc);
            return rc;
        } catch (Throwable t) {
            Log.e(TAG, "RegisterPair error", t);
            return -1;
        }
    }

    /** Sends PIN */
    @JavascriptInterface
    public int sendPin(String pin) {
        try {
            if (pin == null) pin = "";
            int rc = DupClientManager.sendPin(pin);
            Log.d(TAG, "sendPin rc=" + rc + ", pinLen=" + pin.length());
            return rc;
        } catch (Throwable t) {
            Log.e(TAG, "sendPin error", t);
            return -1;
        }
    }

    /** Fetches opponent's temp token via PIN */
    @JavascriptInterface
    public int fetchPinToken(String pin) {
        try {
            if (pin == null) pin = "";
            int rc = DupClientManager.fetchPinToken(pin);
            Log.d(TAG, "fetchPinToken rc=" + rc + ", pinLen=" + pin.length());
            return rc;
        } catch (Throwable t) {
            Log.e(TAG, "fetchPinToken error", t);
            return -1;
        }
    }

    /** Reads locally saved PIN */
    @JavascriptInterface
    public String readPin() {
        try {
            String pin = DupClientManager.readPin();
            Log.d(TAG, "readPin -> " + pin);
            return pin;
        } catch (Throwable t) {
            Log.e(TAG, "readPin error", t);
            return "";
        }
    }

    /** Reads the temporary token obtained via PIN */
    @JavascriptInterface
    public String readPinTempToken() {
        try {
            String t = DupClientManager.readPinTempToken();
            Log.d(TAG, "readPinTempToken -> " + (t != null ? ("len=" + t.length()) : "null"));
            return t;
        } catch (Throwable t) {
            Log.e(TAG, "readPinTempToken error", t);
            return "";
        }
    }

    /** Sets the global pair token */
    @JavascriptInterface
    public void setGlobalPairToken(String token) {
        try {
            if (token == null) token = "";
            DupClientManager.SetGlobalPairToken(token);
            Log.d(TAG, "SetGlobalPairToken len=" + token.length());
        } catch (Throwable t) {
            Log.e(TAG, "SetGlobalPairToken error", t);
        }
    }

    @JavascriptInterface
    public String refreshQRCode(){
        String PinToken=DupClientManager.readPin();
        Log.d(TAG,"QRToken: "+ PinToken);
        return  QRCodeUtils.generateQRCodeBase64(PinToken,100);
    }

    @JavascriptInterface
    public void StartScanQRCode(){
    }

    @JavascriptInterface
    public int setGlobalUUIDMap(String b64Str) {
        try {
            if (b64Str == null) {
                b64Str = "";
            }
            Log.d(TAG, "setGlobalUUIDMap called. b64Str length=" + b64Str.length());
            int rc = DupClientManager.SetGlobalUUIDMap(b64Str);
            Log.d(TAG, "SetGlobalUUIDMap returned rc=" + rc);
            return rc;
        } catch (Throwable t) {
            Log.e(TAG, "setGlobalUUIDMap error", t);
            return -1; // Return -1 for error
        }
    }

    @JavascriptInterface
    public int sendMessageData(String advData) {
        try {
            if (advData == null) {
                advData = "";
            }
            Log.d(TAG, "sendMessageData called. advData length=" + advData.length());
            int rc = DupClientManager.SendMessageData(advData);
            Log.d(TAG, "SendMessageData returned rc=" + rc);
            return rc;
        } catch (Throwable t) {
            Log.e(TAG, "sendMessageData error", t);
            return -13; // Return -1 for error
        }
    }

    @JavascriptInterface
    public String readMessageData() {
        try {
            Log.d(TAG, "readMessageData called.");
            String data = DupClientManager.ReadMessageData();
            Log.d(TAG, "ReadMessageData returned data length=" + (data != null ? data.length() : 0));
            return data;
        } catch (Throwable t) {
            Log.e(TAG, "readMessageData error", t);
            return null; // Return null for error
        }
    }
}
