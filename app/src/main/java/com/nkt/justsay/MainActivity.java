package com.nkt.justsay;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.WebBackForwardList;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class MainActivity extends Activity {

    // ▼▼▼ PUT YOUR PAGE ADDRESS HERE (must start with https://). It is never shown to the user. ▼▼▼
    private static final String START_URL = "https://REPLACE-WITH-YOUR-PAGE-ADDRESS/";
    // ▲▲▲

    private static final long LOAD_TIMEOUT_MS = 25000;   // give up and show "Can't connect" after this
    private static final int REQ_MIC = 7;

    private WebView web;
    private View loading, error, loadingLogo;
    private FrameLayout fullscreen;
    private ObjectAnimator pulse;

    private boolean failed = false;      // the current load has failed
    private boolean shown = false;       // the page has loaded at least once in this load
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable timeout = () -> { if (!shown) showError(); };

    private SpeechBridge speech;
    private String polyfill = "";

    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;
    private ConnectivityManager.NetworkCallback netCallback;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        goFullScreen();

        web = findViewById(R.id.web);
        loading = findViewById(R.id.loading);
        loadingLogo = findViewById(R.id.loading_logo);
        error = findViewById(R.id.error);
        fullscreen = findViewById(R.id.fullscreen);

        // Fonts from the page (Orbitron)
        try {
            Typeface bold = Typeface.createFromAsset(getAssets(), "fonts/orbitron_700.ttf");
            ((TextView) findViewById(R.id.error_text)).setTypeface(bold);
            ((Button) findViewById(R.id.retry)).setTypeface(bold);
        } catch (Exception ignored) { }

        findViewById(R.id.retry).setOnClickListener(v -> retry());
        error.setOnClickListener(v -> retry());

        pulse = ObjectAnimator.ofFloat(loadingLogo, View.ALPHA, 1f, 0.45f);
        pulse.setDuration(1100);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.setRepeatCount(ValueAnimator.INFINITE);

        polyfill = readAsset("speech-polyfill.js");

        WebView.setWebContentsDebuggingEnabled(false);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);                    // remembers Mini / Video choice
        s.setMediaPlaybackRequiresUserGesture(false);    // songs start by themselves
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setSupportMultipleWindows(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        web.setBackgroundColor(0xFF05080F);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);

        speech = new SpeechBridge(this, web);
        web.addJavascriptInterface(speech, "NKTSpeech");
        web.setWebViewClient(new Client());
        web.setWebChromeClient(new Chrome());

        watchNetwork();
        load();
    }

    // ── Loading / error states ───────────────────────────────────────────────

    private void load() {
        failed = false;
        shown = false;
        error.setVisibility(View.GONE);
        loading.setVisibility(View.VISIBLE);
        pulse.start();
        ui.removeCallbacks(timeout);
        ui.postDelayed(timeout, LOAD_TIMEOUT_MS);
        web.loadUrl(START_URL);
    }

    private void retry() { load(); }

    private void showPage() {
        shown = true;
        ui.removeCallbacks(timeout);
        pulse.cancel();
        loading.setVisibility(View.GONE);
        error.setVisibility(View.GONE);
        web.setVisibility(View.VISIBLE);
    }

    /** Every failure ends here. Only "Can't connect" is ever shown — never an address or error code. */
    private void showError() {
        if (failed) return;
        failed = true;
        ui.removeCallbacks(timeout);
        pulse.cancel();
        web.setVisibility(View.INVISIBLE);   // hide the browser's own error page
        web.stopLoading();
        web.loadUrl("about:blank");
        loading.setVisibility(View.GONE);
        error.setVisibility(View.VISIBLE);
    }

    private boolean isOurs(Uri u) {
        Uri home = Uri.parse(START_URL);
        return u != null && home.getHost() != null && home.getHost().equalsIgnoreCase(u.getHost());
    }

    private class Client extends WebViewClient {
        @Override
        public void onPageFinished(WebView view, String url) {
            if (failed || url == null || url.startsWith("about:")) return;
            if (!polyfill.isEmpty()) view.evaluateJavascript(polyfill, null);
            showPage();
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
            if (req.isForMainFrame()) showError();
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest req, WebResourceResponse res) {
            if (req.isForMainFrame() && res.getStatusCode() >= 400) showError();
        }

        @SuppressLint("WebViewClientOnReceivedSslError")
        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError e) {
            handler.cancel();
            showError();
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
            if (!req.isForMainFrame()) return false;
            // Only the page's own site may open. Every other link — tapping the YouTube logo,
            // the video title, "Watch on YouTube", app links — is ignored: nothing opens.
            return !isOurs(req.getUrl());
        }

        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            recreate();       // start fresh; the user just sees the loading logo again
            return true;
        }
    }

    // ── JS dialogs (no page address in the title), fullscreen video ──────────

    private class Chrome extends WebChromeClient {
        @Override
        public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
            new AlertDialog.Builder(MainActivity.this, R.style.Theme_NKT_Dialog)
                    .setMessage(message)
                    .setPositiveButton(R.string.ok, (d, w) -> result.confirm())
                    .setOnCancelListener(d -> result.cancel())
                    .show();
            return true;
        }

        @Override
        public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
            new AlertDialog.Builder(MainActivity.this, R.style.Theme_NKT_Dialog)
                    .setMessage(message)
                    .setPositiveButton(R.string.ok, (d, w) -> result.confirm())
                    .setNegativeButton(R.string.cancel, (d, w) -> result.cancel())
                    .setOnCancelListener(d -> result.cancel())
                    .show();
            return true;
        }

        @Override
        public boolean onJsPrompt(WebView view, String url, String message, String def, JsPromptResult result) {
            final EditText input = new EditText(MainActivity.this);
            input.setText(def);
            new AlertDialog.Builder(MainActivity.this, R.style.Theme_NKT_Dialog)
                    .setMessage(message)
                    .setView(input)
                    .setPositiveButton(R.string.ok, (d, w) -> result.confirm(input.getText().toString()))
                    .setNegativeButton(R.string.cancel, (d, w) -> result.cancel())
                    .setOnCancelListener(d -> result.cancel())
                    .show();
            return true;
        }

        @Override
        public void onPermissionRequest(PermissionRequest request) {
            request.deny();   // the page never needs camera/mic directly; voice goes through NKTSpeech
        }

        @Override
        public Bitmap getDefaultVideoPoster() {
            // no grey "play" placeholder before a video starts
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (customView != null) { callback.onCustomViewHidden(); return; }
            customView = view;
            customCallback = callback;
            fullscreen.addView(view, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            fullscreen.setVisibility(View.VISIBLE);
            web.setVisibility(View.INVISIBLE);
            goFullScreen();
        }

        @Override
        public void onHideCustomView() { exitVideoFullscreen(); }
    }

    private void exitVideoFullscreen() {
        if (customView == null) return;
        fullscreen.removeView(customView);
        fullscreen.setVisibility(View.GONE);
        customView = null;
        if (customCallback != null) customCallback.onCustomViewHidden();
        customCallback = null;
        if (!failed) web.setVisibility(View.VISIBLE);
        goFullScreen();
    }

    // ── Full screen ──────────────────────────────────────────────────────────

    @SuppressWarnings("deprecation")
    private void goFullScreen() {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) goFullScreen();
    }

    // ── Back button, lifecycle ───────────────────────────────────────────────

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (customView != null) { exitVideoFullscreen(); return; }
        if (error.getVisibility() == View.VISIBLE) { finish(); return; }
        WebBackForwardList h = web.copyBackForwardList();
        int i = h.getCurrentIndex();
        if (i > 0) {
            String prev = h.getItemAtIndex(i - 1).getUrl();
            if (prev != null && !prev.startsWith("about:")) { web.goBack(); return; }
        }
        super.onBackPressed();
    }

    /** When the connection comes back while "Can't connect" is showing, try again by itself. */
    private void watchNetwork() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            netCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    ui.postDelayed(() -> { if (error.getVisibility() == View.VISIBLE) retry(); }, 800);
                }
            };
            cm.registerDefaultNetworkCallback(netCallback);
        } catch (Exception ignored) { }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_MIC) {
            boolean granted = grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;
            speech.onPermissionResult(granted);
        }
    }

    static int micRequestCode() { return REQ_MIC; }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        goFullScreen();
    }

    @Override
    protected void onPause() {
        speech.cancel();
        web.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        try {
            if (netCallback != null)
                ((ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE)).unregisterNetworkCallback(netCallback);
        } catch (Exception ignored) { }
        speech.destroy();
        web.destroy();
        super.onDestroy();
    }

    private String readAsset(String name) {
        try (InputStream in = getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } catch (Exception e) {
            return "";
        }
    }
}
