package com.example.bkeattacker;

import android.os.Handler;
import android.webkit.WebView;

import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import com.example.bkeattacker.DupClientManager;

/**
 * A global singleton for front-end logging. Any class can use it to push structured logs to the current WebView.
 * Prerequisite: The Activity must call attach(webView, mainHandler) in its onCreate method.
 * The front-end page needs to implement window.appendLogStructured(jsonStr) (built-in in p6/p51).
 *
 * Usage:
 * 1) In the Activity that holds the WebView (e.g., MainActivity):
 *    - After initializing the WebView in onCreate, call:
 *        WebLogManager.get().attach(webView, mainHandler);
 *    - In onDestroy, call:
 *        WebLogManager.get().detach();
 *
 * 2) In any other class (e.g., ClientCallback, ServerCallback):
 *    - Import: import com.example.bkeattacker.WebLogManager;
 *    - To log (unified entry point):
 *        WebLogManager.get().log(null, "in", "Received data len=20", WebLogManager.LOG_RECV);
 *        WebLogManager.get().log(null, "out", "Write to 0xFFE1 successful", WebLogManager.LOG_SEND);
 *        WebLogManager.get().log("14:25:31", "sys", "System initialization complete", WebLogManager.LOG_SYS);
 *      Notes:
 *        - If `time` is null, the front-end will use the current time.
 *        - It's recommended to use "in"/"out" for `direction`, which the front-end displays as "← Recv" and "→ Sent". Custom strings are also allowed.
 *        - Use the constants LOG_SYS/LOG_RECV/LOG_SEND for `type` for better extensibility.
 *
 * 3) If you already have a structured JSON, you can pass it directly:
 *        WebLogManager.get().logJson("{\"time\":\"14:26:10\",\"direction\":\"in\",\"message\":\"Parsing complete\",\"type\":2}");
 *
 * JSON Field Convention:
 * - time: String (e.g., "14:25:31", optional)
 * - direction: String (e.g., "in" / "out" / other labels, optional)
 * - message: String (required)
 * - type: int (See constants below: LOG_SYS=0x01, LOG_RECV=0x02, LOG_SEND=0x03, extensible)
 */
public class WebLogManager {
    // Log type constant: extensible
    public static final int LOG_SYS  = 0x01; // System gray (not related to BLE)
    public static final int LOG_RECV = 0x02; // Receive green (received by BLE)
    public static final int LOG_SEND = 0x03; // Send yellow (sent by BLE)

    private static final WebLogManager INSTANCE = new WebLogManager();

    public static WebLogManager get() { return INSTANCE; }

    private WeakReference<WebView> webViewRef;
    private WeakReference<Handler> handlerRef;

    private WebLogManager() {}

    /**
     * Called in onCreate/onResume of the page that holds the WebView to bind
     */
    public synchronized void attach(WebView webView, Handler mainHandler) {
        this.webViewRef = new WeakReference<>(webView);
        this.handlerRef = new WeakReference<>(mainHandler);
    }

    /** Unbind (e.g., when the page is destroyed) */
    public synchronized void detach() {
        this.webViewRef = null;
        this.handlerRef = null;
    }

    /**
     * Directly pass through the JSON string (structured log constructed as agreed)
     */
    public void logJson(String json) {
        if (json == null || json.isEmpty()) return;
        final WebView wv = webViewRef != null ? webViewRef.get() : null;
        final Handler h = handlerRef != null ? handlerRef.get() : null;
        if (wv == null || h == null) return;
        final String safe = JSONObject.quote(json);
        h.post(() -> {
            try {
                wv.evaluateJavascript("(function(j){if(window.appendLogStructured){window.appendLogStructured(j);} })(" + safe + ");", null);
            } catch (Throwable ignore) {}
        });
    }

    /**
     * Allows any class to trigger the current WebView to execute JS (to facilitate the UI phase).
     */
    public void callJs(String js) {
        final WebView wv = webViewRef != null ? webViewRef.get() : null;
        final Handler h = handlerRef != null ? handlerRef.get() : null;
        if (wv == null || h == null || js == null) return;
        h.post(() -> {
            try { wv.evaluateJavascript(js, null); } catch (Throwable ignore) {}
        });
    }

    /**
     * Unified log entry: The type is passed in by the caller to facilitate subsequent expansion.
     * @param time       Log time (can be null, the front end will use the current time)
     * @param direction  Direction mark (recommended "in" / "out" or other custom)
     * @param message    Log message
     * @param type       Log type (e.g., 0x01 system gray; 0x02 receive green; 0x03 send yellow; extensible)
     */
    /**
     * New unified log entry: The time is obtained internally through DupClientManager.ReadCurrentTimestamp(), no need for external time input.
     */
    public void log(String message, int type) {
        logTyped(message, type);
    }


    private String nowTimeFromNative() {
        try {
            // Note: Go returns int64 nanoseconds, Java must use long to receive it!
            long nanos = DupClientManager.ReadCurrentTimestamp(); // ← Key: The return type must be long

            if (nanos <= 0) {
                // Invalid time, fall back to the current system time (milliseconds)
                return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        .format(new Date());
            }

            long millis = nanos / 1_000_000L; // Nanoseconds → Milliseconds
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(new Date(millis));

        } catch (Throwable e) {
            // Fall back to the current time on exception
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(new Date());
        }
    }

    /** Construct JSON and send (get time internally) */
    public void logTyped(String message, int type) {
        try {
            JSONObject o = new JSONObject();
            o.put("time", nowTimeFromNative());
            // The direction parameter has been removed, the direction is determined by type (LOG_RECV=receive, LOG_SEND=send)
            o.put("message", message != null ? message : "");
            o.put("type", type);
            logJson(o.toString());
        } catch (Throwable ignore) {}
    }
}

