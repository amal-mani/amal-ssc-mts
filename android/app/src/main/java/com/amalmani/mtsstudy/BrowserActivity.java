package com.amalmani.mtsstudy;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** In-app browser for Falcon (Classplus) videos and Testbook tests. Log in once; the login is kept. */
public class BrowserActivity extends Activity {
    private WebView web;
    private FrameLayout root;
    private View fullView;
    private WebChromeClient.CustomViewCallback fullCb;
    private LinearLayout bar;
    private TextView title;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        float dp = getResources().getDisplayMetrics().density;
        root = new FrameLayout(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(0xFF0E1014);
        bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xFF181B21);
        int pad = (int) (6 * dp);
        bar.setPadding(pad, pad, pad, pad);
        Button close = button("✕ Back to study");
        close.setOnClickListener(v -> finish());
        title = new TextView(this);
        title.setTextColor(0xFF9AA3B2);
        title.setSingleLine(true);
        title.setPadding(pad * 2, 0, pad, 0);
        Button ext = button("Open in browser");
        ext.setOnClickListener(v -> { try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(web.getUrl()))); } catch (Exception e) { } });
        bar.addView(close);
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(ext);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String sc = r.getUrl().getScheme();
                if ("http".equals(sc) || "https".equals(sc)) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, r.getUrl())); } catch (Exception e) { }
                return true;
            }
            @Override public void onPageFinished(WebView v, String url) { title.setText(v.getTitle()); CookieManager.getInstance().flush(); }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest req) {
                // protected (DRM) course videos need this to play
                for (String r : req.getResources()) {
                    if (PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID.equals(r)) { req.grant(new String[]{r}); return; }
                }
                req.deny();
            }
            @Override
            public void onShowCustomView(View view, CustomViewCallback cb) {
                if (fullView != null) { cb.onCustomViewHidden(); return; }
                fullView = view; fullCb = cb;
                root.addView(view, new FrameLayout.LayoutParams(-1, -1));
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            }
            @Override
            public void onHideCustomView() {
                if (fullView == null) return;
                root.removeView(fullView); fullView = null;
                if (fullCb != null) fullCb.onCustomViewHidden();
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            }
        });
        col.addView(bar, new LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(web, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        if (saved != null) web.restoreState(saved);
        else if (getIntent().getData() != null) web.loadUrl(getIntent().getData().toString());
    }

    private Button button(String t) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackgroundColor(0xFF2C313B);
        return b;
    }

    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); web.saveState(out); }
    @Override protected void onPause() { super.onPause(); web.onPause(); CookieManager.getInstance().flush(); }
    @Override protected void onResume() { super.onResume(); web.onResume(); }
    @Override
    public void onBackPressed() {
        if (fullView != null) { onHideFull(); return; }
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
    private void onHideFull() {
        root.removeView(fullView); fullView = null;
        if (fullCb != null) fullCb.onCustomViewHidden();
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
    }
    @Override protected void onDestroy() { if (web != null) web.destroy(); super.onDestroy(); }
}
