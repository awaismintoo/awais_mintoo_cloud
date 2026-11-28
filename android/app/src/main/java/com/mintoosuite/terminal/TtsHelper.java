package com.mintoosuite.terminal;

import android.content.Context;
import android.speech.tts.TextToSpeech;

import java.util.Locale;

/* Baba AI reads answers aloud with the phone's own text-to-speech engine (more reliable than the web page's speech). */
final class TtsHelper {
    private static TextToSpeech tts;
    private static boolean ready = false;
    private static String pText, pLang;
    private static Context ctx;
    private static boolean triedOther = false;

    private TtsHelper() { }

    static synchronized String speak(Context c, String text, String lang) {
        if (text == null || text.length() == 0) { return "empty"; }
        String t = text.length() > 3500 ? text.substring(0, 3500) : text;
        ctx = c.getApplicationContext();
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
            String lg = l == null || l.length() == 0 ? "en-US" : l;
            int r = tts.setLanguage(Locale.forLanguageTag(lg));
            if ((r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) && lg.startsWith("ur")) {
                /* build 625 owner: Urdu voice installed but silent: try ur-PK, ur-IN and plain ur on the current engine, then switch to another installed engine that has Urdu */
                String[] alt = { "ur-PK", "ur-IN", "ur" };
                for (String a : alt) { r = tts.setLanguage(Locale.forLanguageTag(a)); if (r >= TextToSpeech.LANG_AVAILABLE) { break; } }
                if (r < TextToSpeech.LANG_AVAILABLE && !triedOther && ctx != null) {
                    triedOther = true;
                    try {
                        String cur = tts.getDefaultEngine();
                        for (TextToSpeech.EngineInfo e : tts.getEngines()) {
                            if (e.name.equals(cur)) { continue; }
                            final String txt = t; final String eng = e.name;
                            try { tts.shutdown(); } catch (Throwable ignored) { }
                            ready = false; pText = txt; pLang = lg;
                            tts = new TextToSpeech(ctx, new TextToSpeech.OnInitListener() { @Override public void onInit(int st) { synchronized (TtsHelper.class) { ready = st == TextToSpeech.SUCCESS; if (ready && pText != null) { doSpeak(pText, pLang); } pText = null; } } }, eng);
                            return "wait";
                        }
                    } catch (Throwable ignored) { }
                }
            }
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) { return "nolang"; }
            tts.speak(t, TextToSpeech.QUEUE_FLUSH, null, "mwbaba");
            return "ok";
        } catch (Throwable e) { return "fail"; }
    }

    /* build 543: the Listen button shows Stop while reading */
    static boolean busy() { try { return tts != null && tts.isSpeaking(); } catch (Throwable t) { return false; } }
    static synchronized void stop() { try { if (tts != null) { tts.stop(); } } catch (Throwable ignored) { } }
}
