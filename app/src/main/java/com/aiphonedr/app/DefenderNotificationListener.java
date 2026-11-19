package com.aiphonedr.app;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Map;

public class DefenderNotificationListener extends NotificationListenerService {
    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || getPackageName().equals(sbn.getPackageName())) return;
        SharedPreferences prefs = getSharedPreferences("notification_activity", MODE_PRIVATE);
        String key = "app_" + sbn.getPackageName();
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).putLong("last_event", System.currentTimeMillis()).apply();
    }

    public static String getSummary(android.content.Context context) {
        JSONObject result = new JSONObject();
        JSONArray apps = new JSONArray();
        try {
            SharedPreferences prefs = context.getSharedPreferences("notification_activity", MODE_PRIVATE);
            Map<String, ?> all = prefs.getAll();
            int total = 0;
            for (Map.Entry<String, ?> entry : all.entrySet()) {
                if (!entry.getKey().startsWith("app_") || !(entry.getValue() instanceof Integer)) continue;
                int count = (Integer) entry.getValue();
                total += count;
                JSONObject row = new JSONObject();
                String packageName = entry.getKey().substring(4);
                row.put("package", packageName);
                row.put("count", count);
                try {
                    android.content.pm.ApplicationInfo ai = context.getPackageManager().getApplicationInfo(packageName, 0);
                    row.put("label", context.getPackageManager().getApplicationLabel(ai).toString());
                } catch (Exception ignored) { row.put("label", packageName); }
                apps.put(row);
            }
            result.put("totalSinceEnable", total);
            result.put("apps", apps);
            result.put("storesContent", false);
        } catch (Exception e) {
            try { result.put("error", e.getMessage()); } catch (Exception ignored) {}
        }
        return result.toString();
    }
}