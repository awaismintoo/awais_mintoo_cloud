package com.mintoosuite.terminal;

import android.content.Context;
import android.speech.tts.TextToSpeech;

import java.util.Locale;

/* Baba AI reads answers aloud with the phone's own text-to-speech engine (more reliable than the web page's speech). */
final class TtsHelper {
    private static TextToSpeech tts;
    private static boolean ready = false;
    private static String pText, pLang;

    private TtsHelper() { }

    static synchronized String speak(Context c, String text, String lang) {
        if (text == null || text.length() == 0) { return "empty"; }
        String t = text.length() > 3500 ? text.substring(0, 3500) : text;
        if (tts == null) {
            pText = t; pLang = lang;
            try {
                tts = new TextToSpeech(c.getApplicationContext(), new TextToSpeech.OnInitListener() {
                    @Override
                    public void onInit(int status) {
                        synchronized (TtsHelper.class) {
                            ready = status == TextToSpeech.SUCCESS;
                            if (ready && pText != null) { doSpeak(pText, pLang); }
                            pText = null;
                        }
                    }
                });
            } catch (Throwable e) { tts = null; return "fail"; }
            return "wait";
        }
        if (!ready) { pText = t; pLang = lang; return "wait"; }
        return doSpeak(t, lang);
    }

    private static String doSpeak(String t, String l) {
        try {
            int r = tts.setLanguage(Locale.forLanguageTag(l == null || l.length() == 0 ? "en-US" : l));
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) { return "nolang"; }
            tts.speak(t, TextToSpeech.QUEUE_FLUSH, null, "mwbaba");
            return "ok";
        } catch (Throwable e) { return "fail"; }
    }

    static synchronized void stop() { try { if (tts != null) { tts.stop(); } } catch (Throwable ignored) { } }
}
