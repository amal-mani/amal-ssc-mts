package com.amalmani.mtsstudy;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.webkit.WebViewAssetLoader;
import java.util.Locale;

/** MTS Study: the study app runs from files inside the APK, so it works offline. */
public class MainActivity extends Activity {
    static final String HOME = "https://appassets.androidplatform.net/assets/www/index.html";
    private WebView web;
    private TextToSpeech tts;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this)).build();
        web = new WebView(this);
        web.setBackgroundColor(0xFF0E1014);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setSupportMultipleWindows(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) { tts.setLanguage(new Locale("en", "IN")); tts.setSpeechRate(0.9f); }
        });
        web.addJavascriptInterface(new Speaker(), "AndroidTTS");
        web.addJavascriptInterface(new AppBridge(), "AndroidApp");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return loader.shouldInterceptRequest(r.getUrl());
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if ("appassets.androidplatform.net".equals(u.getHost())) return false;
                openLink(u);
                return true;
            }
        });
        setContentView(web);
        if (saved != null) web.restoreState(saved); else web.loadUrl(HOME + hashFrom(getIntent()));
        Alarms.scheduleSaved(this);
    }

    /** Falcon (Classplus) and Testbook open inside the app; other links open in the phone's browser. */
    void openLink(Uri u) {
        String h = u.getHost() == null ? "" : u.getHost();
        boolean inside = h.endsWith("classplusapp.com") || h.endsWith("testbook.com");
        try {
            if (inside) startActivity(new Intent(this, BrowserActivity.class).setData(u));
            else startActivity(new Intent(Intent.ACTION_VIEW, u));
        } catch (Exception e) { }
    }

    static String hashFrom(Intent i) {
        String h = i == null ? null : i.getStringExtra("hash");
        return h == null || h.isEmpty() ? "" : "#" + h;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String h = hashFrom(intent);
        if (!h.isEmpty() && web != null) web.evaluateJavascript("location.hash='" + h.substring(1) + "'", null);
    }

    class Speaker {
        @JavascriptInterface
        public void speak(String text) {
            if (tts != null) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mts");
        }
    }

    class AppBridge {
        @JavascriptInterface
        public void setAlarms(String json) {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);
                Alarms.save(MainActivity.this, json);
                Alarms.scheduleSaved(MainActivity.this);
            });
        }
        @JavascriptInterface
        public void keepOn(boolean on) {
            runOnUiThread(() -> {
                if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        }
        @JavascriptInterface
        public void immersive(boolean on) {
            runOnUiThread(() -> {
                View d = getWindow().getDecorView();
                d.setSystemUiVisibility(on ? (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE) : View.SYSTEM_UI_FLAG_VISIBLE);
            });
        }
    }

    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); web.saveState(out); }
    @Override public void onBackPressed() { if (web.canGoBack()) web.goBack(); else super.onBackPressed(); }
    @Override protected void onDestroy() { if (tts != null) tts.shutdown(); super.onDestroy(); }
}
