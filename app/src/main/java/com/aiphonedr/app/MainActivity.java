package com.aiphonedr.app;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import java.io.OutputStream;
import android.Manifest;
import android.content.pm.PackageManager;
import android.speech.RecognizerIntent;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.os.Build;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.os.VibratorManager;
import java.io.InputStream;
import java.security.MessageDigest;
import android.os.StatFs;
import android.os.Environment;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.TrafficStats;
import android.os.SystemClock;
import android.net.Uri;
import android.provider.Settings;
import android.provider.OpenableColumns;
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
    private BroadcastReceiver powerReceiver;
    private static final int REQUEST_VOICE_PERMISSION = 7101;
    private static final int REQUEST_VOICE_RECOGNITION = 7102;
    private static final int REQUEST_PICK_DOCUMENT = 7103;
    private static final int REQUEST_SAVE_REPORT_PDF = 7104;
    private static final int REQUEST_BACKUP_SOURCE = 7105;
    private static final int REQUEST_BACKUP_DESTINATION = 7106;
    private static final int REQUEST_APK_INSPECTION = 7107;
    private Uri pendingBackupSource;
    private String pendingPdfText = "";

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
        powerReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                boolean connected = Intent.ACTION_POWER_CONNECTED.equals(intent.getAction());
                try {
                    JSONObject event = new JSONObject();
                    event.put("connected", connected);
                    event.put("event", connected ? "connected" : "disconnected");
                    event.put("battery", new JSONObject(new AndroidBridge().getBatteryInfo()));
                    sendToPage("onChargingChanged", event.toString());
                } catch (Exception ignored) {}
            }
        };
        android.content.IntentFilter powerFilter = new android.content.IntentFilter();
        powerFilter.addAction(Intent.ACTION_POWER_CONNECTED);
        powerFilter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(powerReceiver, powerFilter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(powerReceiver, powerFilter);
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
        @JavascriptInterface public String getDeviceStats() { return deviceStats(); }
        @JavascriptInterface public void exportReport(String reportText) {
            pendingPdfText = reportText == null ? "DR.DEFENDER report is empty." : reportText;
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/pdf");
                intent.putExtra(Intent.EXTRA_TITLE, "DR_DEFENDER_Phone_Management_Report.pdf");
                startActivityForResult(intent, REQUEST_SAVE_REPORT_PDF);
            });
        }
        @JavascriptInterface public void pickDocument() { runOnUiThread(() -> { Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("*/*"); i.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i, REQUEST_PICK_DOCUMENT); }); }
        @JavascriptInterface public void pickBackupSource() { runOnUiThread(() -> { Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("*/*"); i.addCategory(Intent.CATEGORY_OPENABLE); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivityForResult(i, REQUEST_BACKUP_SOURCE); }); }
        @JavascriptInterface public String getAppUsage() { return appUsageStats(); }
        @JavascriptInterface public String getNotificationActivity() { try { JSONObject data = new JSONObject(DefenderNotificationListener.getSummary(MainActivity.this)); String enabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners"); boolean access = enabled != null && enabled.contains(new android.content.ComponentName(MainActivity.this, DefenderNotificationListener.class).flattenToString()); data.put("accessGranted", access); return data.toString(); } catch (Exception e) { return "{}"; } }
        @JavascriptInterface public void inspectApk() { runOnUiThread(() -> { Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("application/vnd.android.package-archive"); i.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i, REQUEST_APK_INSPECTION); }); }
        @JavascriptInterface public void testVibration() { runOnUiThread(() -> { try { if (Build.VERSION.SDK_INT >= 31) { VibratorManager vm = (VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE); vm.getDefaultVibrator().vibrate(VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE)); } else { Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE); if (v != null && v.hasVibrator()) v.vibrate(VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE)); } sendToPage("onNativeTestResult", "Vibration test sent. Did you feel it?"); } catch (Exception e) { sendToPage("onNativeTestResult", "Vibration test unavailable: " + e.getMessage()); } }); }
        @JavascriptInterface public void startVoiceCommand() {
            runOnUiThread(() -> {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_VOICE_PERMISSION);
                } else launchVoiceRecognition();
            });
        }
        @JavascriptInterface public String getBatteryInfo() {
            try {
                Intent battery = registerReceiver(null, new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                if (battery == null) return "{}";
                JSONObject info = new JSONObject();
                info.put("temperature", battery.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, -1));
                int level = battery.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
                int scale = battery.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1);
                info.put("percent", level >= 0 && scale > 0 ? Math.round(level * 100f / scale) : -1);
                info.put("voltageMv", battery.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE, -1));
                info.put("technology", battery.getStringExtra(android.os.BatteryManager.EXTRA_TECHNOLOGY));
                info.put("health", healthName(battery.getIntExtra(android.os.BatteryManager.EXTRA_HEALTH, 0)));
                int plugType = battery.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0);
                info.put("connected", plugType != 0);
                info.put("plugType", plugType == 0 ? "Not connected" : plugType == android.os.BatteryManager.BATTERY_PLUGGED_USB ? "USB" : plugType == android.os.BatteryManager.BATTERY_PLUGGED_AC ? "AC adapter" : plugType == android.os.BatteryManager.BATTERY_PLUGGED_WIRELESS ? "Wireless" : "External power");
                info.put("charging", battery.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) == android.os.BatteryManager.BATTERY_STATUS_CHARGING
                    || battery.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) == android.os.BatteryManager.BATTERY_STATUS_FULL);
                info.put("status", statusName(battery.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)));
                return info.toString();
            } catch (Exception e) { return "{}"; }
        }
        private String healthName(int value) {
            switch (value) {
                case android.os.BatteryManager.BATTERY_HEALTH_GOOD: return "Good";
                case android.os.BatteryManager.BATTERY_HEALTH_OVERHEAT: return "Overheating";
                case android.os.BatteryManager.BATTERY_HEALTH_DEAD: return "Dead";
                case android.os.BatteryManager.BATTERY_HEALTH_COLD: return "Cold";
                case android.os.BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: return "Over voltage";
                default: return "Unknown";
            }
        }
        private String statusName(int value) {
            switch (value) {
                case android.os.BatteryManager.BATTERY_STATUS_CHARGING: return "Charging";
                case android.os.BatteryManager.BATTERY_STATUS_DISCHARGING: return "Discharging";
                case android.os.BatteryManager.BATTERY_STATUS_FULL: return "Full";
                case android.os.BatteryManager.BATTERY_STATUS_NOT_CHARGING: return "Not charging";
                default: return "Unknown";
            }
        }
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
                    case "notification_access": intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS); break;
                    case "network": intent = new Intent(Settings.ACTION_WIFI_SETTINGS); break;
                    case "lost": intent = new Intent(Settings.ACTION_SECURITY_SETTINGS); break;
                    case "gaming": intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS); break;
                    case "performance": intent = new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS); break;
                    case "ram": intent = new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS); break;
                    case "privacy": intent = new Intent("android.settings.PRIVACY_SETTINGS"); break;
                    case "app_usage": intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS); break;
                    case "display": intent = new Intent(Settings.ACTION_DISPLAY_SETTINGS); break;
                    case "sound": intent = new Intent(Settings.ACTION_SOUND_SETTINGS); break;
                    case "location": intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS); break;
                    case "wifi_security": intent = new Intent(Settings.ACTION_WIFI_SETTINGS); break;
                    case "app_details": intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())); break;
                    case "system_update": intent = new Intent("android.settings.SYSTEM_UPDATE_SETTINGS"); break;
                    default: intent = new Intent(Settings.ACTION_SETTINGS); break;
                }
                try { startActivity(intent); }
                catch (Exception e) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            });
        }
    }

    private String deviceStats() {
        try {
            JSONObject out = new JSONObject();
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mem = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mem);
            out.put("ramTotal", mem.totalMem);
            out.put("ramAvailable", mem.availMem);
            StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
            out.put("storageTotal", fs.getTotalBytes());
            out.put("storageAvailable", fs.getAvailableBytes());
            out.put("uptimeMs", SystemClock.elapsedRealtime());
            out.put("lowMemory", mem.lowMemory);
            out.put("networkRxBytes", TrafficStats.getTotalRxBytes());
            out.put("networkTxBytes", TrafficStats.getTotalTxBytes());
            out.put("model", Build.MANUFACTURER + " " + Build.MODEL);
            out.put("android", Build.VERSION.RELEASE);
            out.put("sdk", Build.VERSION.SDK_INT);
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            android.net.Network active = cm == null ? null : cm.getActiveNetwork();
            NetworkCapabilities caps = active == null || cm == null ? null : cm.getNetworkCapabilities(active);
            String network = "Offline";
            if (caps != null) {
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) network = "Wi-Fi";
                else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) network = "Mobile data";
                else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) network = "Ethernet";
                else network = "Connected";
            }
            out.put("network", network);
            return out.toString();
        } catch (Exception e) { return "{}"; }
    }

    private void writeReportPdf(Uri destination) {
        if (destination == null) return;
        PdfDocument document = new PdfDocument();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(10f);
        paint.setColor(android.graphics.Color.BLACK);
        int pageNumber = 1;
        int pageWidth = 595;
        int pageHeight = 842;
        int left = 42;
        int y = 52;
        PdfDocument.Page page = document.startPage(new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create());
        Canvas canvas = page.getCanvas();
        Paint heading = new Paint(Paint.ANTI_ALIAS_FLAG);
        heading.setColor(android.graphics.Color.rgb(30, 65, 105));
        heading.setTextSize(16f);
        heading.setFakeBoldText(true);
        canvas.drawText("DR.DEFENDER — Phone Management Report", left, y, heading);
        y += 30;
        String[] lines = pendingPdfText.split("\\n");
        for (String original : lines) {
            String remaining = original;
            if (remaining.length() == 0) remaining = " ";
            while (remaining.length() > 0) {
                int count = paint.breakText(remaining, true, pageWidth - left - 42, null);
                if (count <= 0) count = Math.min(1, remaining.length());
                String line = remaining.substring(0, count);
                if (y > pageHeight - 48) {
                    document.finishPage(page);
                    pageNumber++;
                    page = document.startPage(new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create());
                    canvas = page.getCanvas();
                    y = 52;
                    canvas.drawText("DR.DEFENDER — Phone Management Report (continued)", left, 30, heading);
                }
                canvas.drawText(line, left, y, paint);
                y += 16;
                remaining = remaining.substring(count);
            }
        }
        document.finishPage(page);
        try (OutputStream output = getContentResolver().openOutputStream(destination)) {
            if (output == null) throw new java.io.IOException("Unable to open selected destination");
            document.writeTo(output);
            sendToPage("onReportPdfSaved", "PDF report saved successfully.");
        } catch (Exception e) {
            sendToPage("onReportPdfError", "Could not save PDF: " + e.getMessage());
        } finally {
            document.close();
            pendingPdfText = "";
        }
    }

    private void inspectApkFile(Uri uri) {
        new Thread(() -> {
            java.io.File temp = null;
            try {
                temp = java.io.File.createTempFile("drdefender-apk-", ".apk", getCacheDir());
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                long bytes = 0;
                try (InputStream in = getContentResolver().openInputStream(uri); java.io.FileOutputStream out = new java.io.FileOutputStream(temp)) {
                    if (in == null) throw new java.io.IOException("Could not read the selected APK");
                    byte[] buffer = new byte[16384]; int count;
                    while ((count = in.read(buffer)) != -1) { out.write(buffer, 0, count); digest.update(buffer, 0, count); bytes += count; }
                }
                android.content.pm.PackageInfo info = getPackageManager().getPackageArchiveInfo(temp.getAbsolutePath(), android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES);
                JSONObject result = new JSONObject();
                result.put("fileBytes", bytes);
                result.put("sha256", toHex(digest.digest()));
                result.put("validPackageArchive", info != null);
                if (info != null) {
                    result.put("packageName", info.packageName);
                    result.put("versionName", info.versionName == null ? "Unknown" : info.versionName);
                    result.put("versionCode", Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode);
                    if (Build.VERSION.SDK_INT >= 28 && info.signingInfo != null) {
                        android.content.pm.Signature[] signatures = info.signingInfo.getApkContentsSigners();
                        JSONArray signers = new JSONArray();
                        if (signatures != null) for (android.content.pm.Signature signature : signatures) {
                            MessageDigest certDigest = MessageDigest.getInstance("SHA-256");
                            signers.put(toHex(certDigest.digest(signature.toByteArray())));
                        }
                        result.put("signerCertificateSha256", signers);
                    } else result.put("signerCertificateSha256", "Not available on this Android version");
                } else result.put("message", "The selected file is not a readable Android APK archive.");
                sendToPage("onApkInspection", result.toString());
            } catch (Exception e) { sendToPage("onApkInspection", "APK inspection failed: " + e.getMessage()); }
            finally { if (temp != null) temp.delete(); }
        }).start();
    }

    private String toHex(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        for (byte b : bytes) out.append(String.format(java.util.Locale.US, "%02x", b & 0xff));
        return out.toString();
    }

    private String queryDisplayName(Uri uri) {
        try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) { int col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (col >= 0) return cursor.getString(col); }
        } catch (Exception ignored) {}
        return null;
    }

    private void copySelectedFile(Uri source, Uri destination) {
        new Thread(() -> {
            byte[] buffer = new byte[8192];
            long copied = 0;
            try (InputStream in = getContentResolver().openInputStream(source); OutputStream out = getContentResolver().openOutputStream(destination, "w")) {
                if (in == null || out == null) throw new java.io.IOException("Could not open the selected file or backup destination");
                int count;
                while ((count = in.read(buffer)) != -1) { out.write(buffer, 0, count); copied += count; }
                out.flush();
                sendToPage("onBackupResult", "Backup complete. Copied " + copied + " bytes to the destination you selected. The original file was not changed.");
            } catch (Exception e) { sendToPage("onBackupResult", "Backup failed: " + e.getMessage()); }
        }).start();
    }

    private String appUsageStats() {
        JSONObject result = new JSONObject();
        JSONArray apps = new JSONArray();
        try {
            AppOpsManager ops = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
            int mode = ops == null ? AppOpsManager.MODE_IGNORED : ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), getPackageName());
            boolean allowed = mode == AppOpsManager.MODE_ALLOWED;
            result.put("accessGranted", allowed);
            if (allowed) {
                long end = System.currentTimeMillis();
                long start = end - 24L * 60L * 60L * 1000L;
                UsageStatsManager manager = (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
                List<UsageStats> stats = manager == null ? java.util.Collections.emptyList() : manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end);
                if (stats != null) {
                    java.util.Collections.sort(stats, (a,b) -> Long.compare(b.getTotalTimeInForeground(), a.getTotalTimeInForeground()));
                    int count = 0;
                    for (UsageStats item : stats) {
                        long ms = item.getTotalTimeInForeground();
                        if (ms <= 0 || count >= 20) continue;
                        JSONObject row = new JSONObject();
                        row.put("package", item.getPackageName());
                        row.put("foregroundMs", ms);
                        try { android.content.pm.ApplicationInfo ai = getPackageManager().getApplicationInfo(item.getPackageName(), 0); row.put("label", getPackageManager().getApplicationLabel(ai).toString()); }
                        catch (Exception ignored) { row.put("label", item.getPackageName()); }
                        apps.put(row); count++;
                    }
                }
            }
            result.put("apps", apps);
            result.put("period", "Last 24 hours");
        } catch (Exception e) { try { result.put("error", e.getMessage()); } catch (Exception ignored) {} }
        return result.toString();
    }

    private void launchVoiceRecognition() {
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a DR.DEFENDER command");
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            startActivityForResult(intent, REQUEST_VOICE_RECOGNITION);
        } catch (Exception e) { sendToPage("onVoiceError", "Voice recognition is unavailable on this device."); }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_VOICE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) launchVoiceRecognition();
            else sendToPage("onVoiceError", "Microphone permission was denied. Enable it in Android app settings.");
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SAVE_REPORT_PDF) {
            if (resultCode == RESULT_OK && data != null) writeReportPdf(data.getData());
            else sendToPage("onReportPdfError", "PDF export was cancelled.");
            return;
        }
        if (requestCode == REQUEST_APK_INSPECTION) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) inspectApkFile(data.getData());
            else sendToPage("onApkInspection", "APK inspection cancelled.");
            return;
        }
        if (requestCode == REQUEST_BACKUP_SOURCE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                pendingBackupSource = data.getData();
                String name = queryDisplayName(pendingBackupSource);
                Intent save = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                save.addCategory(Intent.CATEGORY_OPENABLE);
                save.setType(getContentResolver().getType(pendingBackupSource) == null ? "application/octet-stream" : getContentResolver().getType(pendingBackupSource));
                save.putExtra(Intent.EXTRA_TITLE, name == null ? "DR_DEFENDER_Backup" : "Backup_" + name);
                startActivityForResult(save, REQUEST_BACKUP_DESTINATION);
            } else sendToPage("onBackupResult", "Backup cancelled; no file was copied.");
            return;
        }
        if (requestCode == REQUEST_BACKUP_DESTINATION) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null && pendingBackupSource != null) copySelectedFile(pendingBackupSource, data.getData());
            else sendToPage("onBackupResult", "Backup destination was not selected; no file was copied.");
            pendingBackupSource = null;
            return;
        }
        if (requestCode == REQUEST_PICK_DOCUMENT) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                Uri uri = data.getData();
                JSONObject selected = new JSONObject();
                try {
                    selected.put("uri", uri.toString());
                    selected.put("mime", getContentResolver().getType(uri));
                    try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                        if (cursor != null && cursor.moveToFirst()) {
                            int nameCol = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                            int sizeCol = cursor.getColumnIndex(OpenableColumns.SIZE);
                            if (nameCol >= 0) selected.put("name", cursor.getString(nameCol));
                            if (sizeCol >= 0 && !cursor.isNull(sizeCol)) selected.put("size", cursor.getLong(sizeCol));
                        }
                    }
                } catch (Exception ignored) {}
                sendToPage("onDocumentPicked", selected.toString());
            } else sendToPage("onDocumentPicked", "{}");
            return;
        }
        if (requestCode == REQUEST_VOICE_RECOGNITION) {
            if (resultCode == RESULT_OK && data != null) {
                java.util.ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (matches != null && !matches.isEmpty()) sendToPage("onVoiceResult", matches.get(0));
                else sendToPage("onVoiceError", "I did not catch that. Please try again.");
            } else sendToPage("onVoiceError", "Voice input cancelled or not recognised.");
        }
    }

    @Override protected void onPause() { super.onPause(); stopSensors(); }
    @Override protected void onResume() { super.onResume(); }
    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }
    @Override protected void onDestroy() {
        stopSensors();
        if (powerReceiver != null) { try { unregisterReceiver(powerReceiver); } catch (Exception ignored) {} powerReceiver = null; }
        if (webView != null) { webView.removeJavascriptInterface("AndroidBridge"); webView.destroy(); webView = null; }
        super.onDestroy();
    }
}