package com.paydaybudget.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.webkit.JavascriptInterface;
import org.json.JSONObject;
import java.util.ArrayList;
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
    private SpeechRecognizer recognizer;
    private static final int REQ_MIC = 42;

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
        webView.addJavascriptInterface(new VoiceBridge(), "AndroidVoice");

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
        if (recognizer != null) recognizer.destroy();
        if (webView != null) webView.destroy();
        super.onDestroy();
    }

    // ---------- 語音記帳：網頁長按＋ → start()，放開 → stop() ----------

    /** 把辨識狀態傳回網頁：window.onVoiceEvent(type, text) */
    private void sendToPage(String type, String text) {
        final String js = "window.onVoiceEvent && window.onVoiceEvent("
                + JSONObject.quote(type) + "," + JSONObject.quote(text == null ? "" : text) + ")";
        runOnUiThread(() -> webView.evaluateJavascript(js, null));
    }

    private class VoiceBridge {
        @JavascriptInterface
        public void start() { runOnUiThread(MainActivity.this::startListening); }

        @JavascriptInterface
        public void stop() { runOnUiThread(() -> { if (recognizer != null) recognizer.stopListening(); }); }
    }

    private void startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            sendToPage("error", "請允許使用麥克風，然後再長按＋一次");
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            sendToPage("error", "這支手機沒有可用的語音辨識服務");
            return;
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) { }
                @Override public void onBeginningOfSpeech() { }
                @Override public void onRmsChanged(float rmsdB) { }
                @Override public void onBufferReceived(byte[] buffer) { }
                @Override public void onEndOfSpeech() { }
                @Override public void onEvent(int eventType, Bundle params) { }

                @Override public void onPartialResults(Bundle partial) {
                    String t = first(partial);
                    if (t != null && !t.isEmpty()) sendToPage("partial", t);
                }

                @Override public void onResults(Bundle results) {
                    sendToPage("result", first(results));
                }

                @Override public void onError(int error) {
                    String msg;
                    switch (error) {
                        case SpeechRecognizer.ERROR_NO_MATCH:
                        case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                            msg = "沒有聽清楚，請按住＋再說一次"; break;
                        case SpeechRecognizer.ERROR_NETWORK:
                        case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                            msg = "語音辨識需要網路，請確認連線"; break;
                        case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                            msg = "請允許使用麥克風"; break;
                        case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                            msg = "語音辨識忙碌中，請稍後再試"; break;
                        default:
                            msg = "語音辨識失敗（代碼 " + error + "）";
                    }
                    sendToPage("error", msg);
                }
            });
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-TW");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        // 按住期間不要太快自動結束
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500);
        recognizer.startListening(intent);
    }

    private static String first(Bundle b) {
        if (b == null) return null;
        ArrayList<String> list = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return (list == null || list.isEmpty()) ? null : list.get(0);
    }
}
