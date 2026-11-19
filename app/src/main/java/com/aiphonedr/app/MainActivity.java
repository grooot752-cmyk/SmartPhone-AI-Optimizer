package com.aiphonedr.app;

import android.app.Activity;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;

public class MainActivity extends Activity implements SensorEventListener {
    private WebView webView;
    private SensorManager sensorManager;
    private boolean sensorsActive = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("file:///android_asset/index.html");
        setContentView(webView);
    }

    private String sensorInventory() {
        JSONArray out = new JSONArray();
        List<Sensor> sensors = sensorManager.getSensorList(Sensor.TYPE_ALL);
        try {
            for (Sensor s : sensors) {
                JSONObject item = new JSONObject();
                item.put("name", s.getName());
                item.put("vendor", s.getVendor());
                item.put("type", s.getStringType());
                item.put("version", s.getVersion());
                item.put("power", s.getPower());
                out.put(item);
            }
        } catch (Exception ignored) {}
        return out.toString();
    }

    private void sendToPage(String function, String payload) {
        if (webView == null) return;
        String safe = JSONObject.quote(payload);
        runOnUiThread(() -> webView.evaluateJavascript(function + "(" + safe + ")", null));
    }

    @Override public void onSensorChanged(SensorEvent event) {
        try {
            JSONObject data = new JSONObject();
            data.put("type", event.sensor.getStringType());
            data.put("name", event.sensor.getName());
            JSONArray values = new JSONArray();
            for (float v : event.values) values.put((double) v);
            data.put("values", values);
            data.put("accuracy", event.accuracy);
            sendToPage("onNativeSensor", data.toString());
        } catch (Exception ignored) {}
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void startSensors() {
        if (sensorsActive) return;
        sensorsActive = true;
        for (Sensor s : sensorManager.getSensorList(Sensor.TYPE_ALL)) {
            try { sensorManager.registerListener(this, s, SensorManager.SENSOR_DELAY_NORMAL); }
            catch (Exception ignored) {}
        }
    }

    private void stopSensors() {
        if (sensorManager != null) sensorManager.unregisterListener(this);
        sensorsActive = false;
    }

    public class AndroidBridge {
        @JavascriptInterface public String getSensors() { return sensorInventory(); }
        @JavascriptInterface public void startSensors() { runOnUiThread(() -> MainActivity.this.startSensors()); }
        @JavascriptInterface public void stopSensors() { runOnUiThread(() -> MainActivity.this.stopSensors()); }

        @JavascriptInterface public void openSettings(String section) {
            final String key = section == null ? "" : section;
            runOnUiThread(() -> {
                Intent intent;
                switch (key) {
                    case "storage": intent = new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS); break;
                    case "battery": intent = new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS); break;
                    case "apps": intent = new Intent(Settings.ACTION_APPLICATION_SETTINGS); break;
                    case "notifications": intent = new Intent("android.settings.APP_NOTIFICATION_SETTINGS").putExtra("android.provider.extra.APP_PACKAGE", getPackageName()); break;
                    case "network": intent = new Intent(Settings.ACTION_WIFI_SETTINGS); break;
                    case "lost": intent = new Intent(Settings.ACTION_SECURITY_SETTINGS); break;
                    case "gaming": intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS); break;
                    case "performance":
                    case "ram": intent = new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS); break;
                    default: intent = new Intent(Settings.ACTION_SETTINGS); break;
                }
                try { startActivity(intent); }
                catch (Exception e) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            });
        }
    }

    @Override protected void onPause() { super.onPause(); stopSensors(); }
    @Override protected void onResume() { super.onResume(); }
    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }
    @Override protected void onDestroy() {
        stopSensors();
        if (webView != null) { webView.removeJavascriptInterface("AndroidBridge"); webView.destroy(); webView = null; }
        super.onDestroy();
    }
}