package com.mintoosuite.terminal;

import android.content.Context;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Offline translation through ML Kit, used by reflection so the app still builds without it. */
final class TranslateHelper {
    private static final Map<String, Object> CACHE = new HashMap<>();

    interface Done { void run(boolean ok, String data); }

    static boolean available() {
        try { Class.forName("com.google.mlkit.nl.translate.Translation"); return true; } catch (Throwable t) { return false; }
    }

    private static Object listen(Object task, final Done d, final boolean wantValue) throws Exception {
        Class<?> ok = Class.forName("com.google.android.gms.tasks.OnSuccessListener");
        Class<?> bad = Class.forName("com.google.android.gms.tasks.OnFailureListener");
        Object okL = Proxy.newProxyInstance(ok.getClassLoader(), new Class<?>[]{ok}, (p, m, a) -> {
            if (m.getName().equals("onSuccess")) { d.run(true, a != null && a[0] != null && wantValue ? String.valueOf(a[0]) : ""); }
            return null;
        });
        Object badL = Proxy.newProxyInstance(bad.getClassLoader(), new Class<?>[]{bad}, (p, m, a) -> {
            if (m.getName().equals("onFailure")) { d.run(false, a != null && a[0] != null ? String.valueOf(((Throwable) a[0]).getMessage()) : "failed"); }
            return null;
        });
        Method s = task.getClass().getMethod("addOnSuccessListener", ok);
        Object t2 = s.invoke(task, okL);
        Method f = t2.getClass().getMethod("addOnFailureListener", bad);
        return f.invoke(t2, badL);
    }

    private static synchronized Object translator(String from, String to) throws Exception {
        String k = from + ">" + to;
        Object t = CACHE.get(k);
        if (t != null) { return t; }
        Class<?> ob = Class.forName("com.google.mlkit.nl.translate.TranslatorOptions$Builder");
        Object b = ob.getConstructor().newInstance();
        ob.getMethod("setSourceLanguage", String.class).invoke(b, from);
        ob.getMethod("setTargetLanguage", String.class).invoke(b, to);
        Object opts = ob.getMethod("build").invoke(b);
        Class<?> tr = Class.forName("com.google.mlkit.nl.translate.Translation");
        Class<?> oc = Class.forName("com.google.mlkit.nl.translate.TranslatorOptions");
        t = tr.getMethod("getClient", oc).invoke(null, opts);
        CACHE.put(k, t);
        return t;
    }

    private static Object remoteModel(String lang) throws Exception {
        Class<?> mb = Class.forName("com.google.mlkit.nl.translate.TranslateRemoteModel$Builder");
        Object b = mb.getConstructor(String.class).newInstance(lang);
        return mb.getMethod("build").invoke(b);
    }

    /** Download one language pack (the pack is paired with English, which every ML Kit pair uses). */
    static void download(String lang, Done d) {
        try {
            String from = lang.equals("en") ? "es" : lang;
            String to = "en";
            Object t = translator(from, to);
            Class<?> cb = Class.forName("com.google.mlkit.common.model.DownloadConditions$Builder");
            Object conds = cb.getMethod("build").invoke(cb.getConstructor().newInstance());
            Class<?> cc = Class.forName("com.google.mlkit.common.model.DownloadConditions");
            Object task = t.getClass().getMethod("downloadModelIfNeeded", cc).invoke(t, conds);
            listen(task, d, false);
        } catch (Throwable e) { d.run(false, "Language packs are not available in this build."); }
    }

    /** Comma separated list of downloaded language codes. */
    static void have(final Done d) {
        try {
            Class<?> rm = Class.forName("com.google.mlkit.common.model.RemoteModelManager");
            Object mgr = rm.getMethod("getInstance").invoke(null);
            Class<?> tm = Class.forName("com.google.mlkit.nl.translate.TranslateRemoteModel");
            Object task = rm.getMethod("getDownloadedModels", Class.class).invoke(mgr, tm);
            Class<?> ok = Class.forName("com.google.android.gms.tasks.OnSuccessListener");
            Class<?> bad = Class.forName("com.google.android.gms.tasks.OnFailureListener");
            Object okL = Proxy.newProxyInstance(ok.getClassLoader(), new Class<?>[]{ok}, (p, m, a) -> {
                if (m.getName().equals("onSuccess")) {
                    StringBuilder sb = new StringBuilder();
                    try {
                        for (Object o : (Set<?>) a[0]) {
                            if (sb.length() > 0) { sb.append(','); }
                            sb.append(String.valueOf(o.getClass().getMethod("getLanguage").invoke(o)));
                        }
                    } catch (Throwable ignored) { }
                    d.run(true, sb.toString());
                }
                return null;
            });
            Object badL = Proxy.newProxyInstance(bad.getClassLoader(), new Class<?>[]{bad}, (p, m, a) -> {
                if (m.getName().equals("onFailure")) { d.run(false, "failed"); }
                return null;
            });
            Object t2 = task.getClass().getMethod("addOnSuccessListener", ok).invoke(task, okL);
            t2.getClass().getMethod("addOnFailureListener", bad).invoke(t2, badL);
        } catch (Throwable e) { d.run(false, "none"); }
    }

    static void translate(String from, String to, String text, Done d) {
        try {
            Object t = translator(from, to);
            Object task = t.getClass().getMethod("translate", String.class).invoke(t, text);
            listen(task, d, true);
        } catch (Throwable e) { d.run(false, "Translation is not available."); }
    }
}
