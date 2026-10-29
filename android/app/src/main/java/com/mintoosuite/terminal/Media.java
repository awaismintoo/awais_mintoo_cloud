package com.mintoosuite.terminal;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;

import java.util.ArrayList;

/* File picker for the web page (pictures, videos, audio, any file) and speech-to-text for the Professor microphone. */
final class Media {
    static final int REQ_FILE = 7711, REQ_MIC = 7712;
    private static ValueCallback<Uri[]> fileCb;
    private static SpeechRecognizer sr;
    private static String lang = "en-US";

    private Media() { }

    static boolean chooser(ValueCallback<Uri[]> cb, WebChromeClient.FileChooserParams p) {
        Activity a = MainActivity.cur;
        if (a == null) { cb.onReceiveValue(null); return true; }
        if (fileCb != null) { fileCb.onReceiveValue(null); fileCb = null; }
        fileCb = cb;
        try {
            a.startActivityForResult(p.createIntent(), REQ_FILE);
        } catch (Exception e) {
            fileCb = null;
            cb.onReceiveValue(null);
        }
        return true;
    }

    static void onResult(int req, int res, Intent data) {
        if (req != REQ_FILE || fileCb == null) { return; }
        Uri[] r = null;
        try { r = WebChromeClient.FileChooserParams.parseResult(res, data); } catch (Exception ignored) { }
        fileCb.onReceiveValue(r);
        fileCb = null;
    }

    private static void say(String kind, String text) {
        WebHolder.js("window.__mwSp&&window.__mwSp(" + org.json.JSONObject.quote(kind) + "," + org.json.JSONObject.quote(text == null ? "" : text) + ");");
    }

    static void listen(String l) {
        final Activity a = MainActivity.cur;
        if (a == null) { return; }
        lang = (l == null || l.length() < 2) ? "en-US" : l;
        a.runOnUiThread(new Runnable() { @Override public void run() { begin(a); } });
    }

    static void onPerm(int req, int[] grants) {
        if (req != REQ_MIC) { return; }
        boolean ok = grants != null && grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED;
        if (ok && MainActivity.cur != null) { begin(MainActivity.cur); } else { say("err", "Microphone permission is off. Turn it on in Android Settings > Apps > MintooSuite > Permissions."); }
    }

    private static void begin(Activity a) {
        try {
            if (a.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                a.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
                return;
            }
            if (!SpeechRecognizer.isRecognitionAvailable(a)) { say("err", "Speech recognition is not available on this phone. Please type your question."); return; }
            stop();
            sr = SpeechRecognizer.createSpeechRecognizer(a);
            sr.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) { say("ready", ""); }
                @Override public void onBeginningOfSpeech() { say("begin", ""); }
                @Override public void onRmsChanged(float rmsdB) { }
                @Override public void onBufferReceived(byte[] buffer) { }
                @Override public void onEndOfSpeech() { say("end", ""); }
                @Override public void onError(int error) {
                    String m;
                    if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) { m = "I did not hear anything. Tap the microphone and try again."; }
                    else if (error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT) { m = "Speech needs the internet. Check your connection."; }
                    else if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) { m = "Microphone permission is off."; }
                    else if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) { m = "The microphone is busy. Try again in a moment."; }
                    else { m = "Could not understand. Please try again or type."; }
                    say("err", m);
                }
                @Override public void onResults(Bundle results) {
                    ArrayList<String> l = results == null ? null : results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    say("final", (l != null && !l.isEmpty()) ? l.get(0) : "");
                }
                @Override public void onPartialResults(Bundle partial) {
                    ArrayList<String> l = partial == null ? null : partial.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (l != null && !l.isEmpty()) { say("partial", l.get(0)); }
                }
                @Override public void onEvent(int eventType, Bundle params) { }
            });
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            sr.startListening(i);
        } catch (Throwable t) {
            say("err", "Could not start the microphone. Please type your question.");
        }
    }

    static void stop() {
        try { if (sr != null) { sr.stopListening(); sr.destroy(); } } catch (Throwable ignored) { }
        sr = null;
    }
}
