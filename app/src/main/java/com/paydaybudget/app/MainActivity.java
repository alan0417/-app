package com.paydaybudget.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.webkit.WebViewAssetLoader;

/**
 * 整個 App 就是一個全螢幕的網頁畫面，載入 assets/index.html。
 * 用 WebViewAssetLoader 讓網頁有固定的網址，資料（localStorage）會穩定存在手機裡。
 */
public class MainActivity extends Activity {
    private static final String START_URL = "https://appassets.androidplatform.net/assets/index.html";
    private WebView webView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView = new WebView(this);
        webView.setBackgroundColor(Color.parseColor("#F1F2F4"));
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);      // 讓 localStorage 可以存資料
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setTextZoom(100);                 // 不跟著系統字體大小放大，避免版面跑掉

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // 只允許 App 自己的頁面
                return !"appassets.androidplatform.net".equals(request.getUrl().getHost());
            }
        });

        // Android 15 起畫面會延伸到狀態列底下：把狀態列、導覽列、鍵盤的高度留白
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#F1F2F4"));
        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            // Android 11 以上：鍵盤高度也自己留白；更舊的版本由系統自動縮小視窗（adjustResize）
            int types = WindowInsetsCompat.Type.systemBars();
            if (Build.VERSION.SDK_INT >= 30) types |= WindowInsetsCompat.Type.ime();
            Insets bars = insets.getInsets(types);
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        setContentView(root);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowCompat.getInsetsController(getWindow(), root).setAppearanceLightStatusBars(true);
        WindowCompat.getInsetsController(getWindow(), root).setAppearanceLightNavigationBars(true);

        if (savedInstanceState != null) webView.restoreState(savedInstanceState);
        else webView.loadUrl(START_URL);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
