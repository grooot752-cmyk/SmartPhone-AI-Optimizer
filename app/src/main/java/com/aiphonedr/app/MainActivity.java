package com.aiphonedr.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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

    public class AndroidBridge {
        @JavascriptInterface
        public void openSettings(String section) {
            final String safeSection = section == null ? "" : section;
            runOnUiThread(() -> {
                Intent intent;
                switch (safeSection) {
                    case "storage":
                        intent = new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS);
                        break;
                    case "battery":
                        intent = new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS);
                        break;
                    case "apps":
                        intent = new Intent(Settings.ACTION_APPLICATION_SETTINGS);
                        break;
                    case "performance":
                        intent = new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS);
                        break;
                    default:
                        intent = new Intent(Settings.ACTION_SETTINGS);
                        break;
                }
                try {
                    startActivity(intent);
                } catch (Exception e) {
                    startActivity(new Intent(Settings.ACTION_SETTINGS));
                }
            });
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}