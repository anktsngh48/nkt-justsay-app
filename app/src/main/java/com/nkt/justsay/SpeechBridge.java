package com.nkt.justsay;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONObject;

import java.util.ArrayList;

/**
 * Android's WebView has no built-in speech recognition, so the page's mic button would not work.
 * speech-polyfill.js gives the page a standard webkitSpeechRecognition object that calls this class,
 * which uses Android's own speech recognizer and sends the results back to the page.
 */
class SpeechBridge {

    private final Activity act;
    private final WebView web;
    private SpeechRecognizer recognizer;

    private String pendingLang = "en-US";
    private boolean pendingInterim = true;
    private boolean waitingForPermission = false;
    private boolean active = false;

    SpeechBridge(Activity act, WebView web) {
        this.act = act;
        this.web = web;
    }

    // ── Called from the page ─────────────────────────────────────────────────

    @JavascriptInterface
    public void start(String lang, boolean interim) {
        act.runOnUiThread(() -> begin(lang, interim));
    }

    @JavascriptInterface
    public void stop() {
        act.runOnUiThread(() -> { if (recognizer != null && active) recognizer.stopListening(); });
    }

    @JavascriptInterface
    public void abort() {
        act.runOnUiThread(this::cancel);
    }

    // ── Native side ──────────────────────────────────────────────────────────

    private void begin(String lang, boolean interim) {
        pendingLang = (lang == null || lang.isEmpty()) ? "en-US" : lang;
        pendingInterim = interim;

        if (act.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            waitingForPermission = true;
            act.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MainActivity.micRequestCode());
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(act)) {
            fail("service-not-allowed");
            return;
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(act);
            recognizer.setRecognitionListener(listener);
        }
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, pendingLang);
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, pendingInterim);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, act.getPackageName());
        active = true;
        try {
            recognizer.startListening(i);
        } catch (Exception e) {
            fail("audio-capture");
        }
    }

    void onPermissionResult(boolean granted) {
        if (!waitingForPermission) return;
        waitingForPermission = false;
        if (granted) begin(pendingLang, pendingInterim);
        else fail("not-allowed");
    }

    void cancel() {
        if (recognizer != null && active) {
            recognizer.cancel();
            active = false;
            emit("end", "{}");
        }
    }

    void destroy() {
        if (recognizer != null) {
            recognizer.destroy();
            recognizer = null;
        }
    }

    private void fail(String code) {
        active = false;
        emit("error", "{\"error\":" + JSONObject.quote(code) + "}");
        emit("end", "{}");
    }

    private final RecognitionListener listener = new RecognitionListener() {
        @Override public void onReadyForSpeech(Bundle params) { emit("start", "{}"); }
        @Override public void onBeginningOfSpeech() { }
        @Override public void onRmsChanged(float rmsdB) { }
        @Override public void onBufferReceived(byte[] buffer) { }
        @Override public void onEndOfSpeech() { }
        @Override public void onEvent(int eventType, Bundle params) { }

        @Override
        public void onPartialResults(Bundle b) {
            String t = first(b);
            if (t != null && !t.isEmpty()) emit("result", "{\"text\":" + JSONObject.quote(t) + ",\"final\":false}");
        }

        @Override
        public void onResults(Bundle b) {
            active = false;
            String t = first(b);
            if (t != null && !t.isEmpty()) emit("result", "{\"text\":" + JSONObject.quote(t) + ",\"final\":true}");
            else emit("error", "{\"error\":\"no-speech\"}");
            emit("end", "{}");
        }

        @Override
        public void onError(int code) {
            if (!active) return;
            String e;
            switch (code) {
                case SpeechRecognizer.ERROR_NETWORK:
                case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                case SpeechRecognizer.ERROR_SERVER:
                    e = "network"; break;
                case SpeechRecognizer.ERROR_NO_MATCH:
                case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                    e = "no-speech"; break;
                case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                    e = "not-allowed"; break;
                case SpeechRecognizer.ERROR_AUDIO:
                    e = "audio-capture"; break;
                default:
                    e = "aborted";
            }
            fail(e);
        }
    };

    private static String first(Bundle b) {
        if (b == null) return null;
        ArrayList<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return (r == null || r.isEmpty()) ? null : r.get(0);
    }

    private void emit(String type, String json) {
        final String js = "window.__nktSpeechEvent&&window.__nktSpeechEvent(" + JSONObject.quote(type) + "," + json + ")";
        act.runOnUiThread(() -> web.evaluateJavascript(js, null));
    }
}
