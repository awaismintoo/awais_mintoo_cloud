package com.mintoosuite.terminal;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.PrintWriter;
import java.io.StringWriter;

/* Crash reports (build 365). 1) Always: if the app stops unexpectedly, a short technical note (no keys, balances or personal data) is kept ON THE PHONE
   so the user can look at it or send it. 2) Only if the user agrees AND the Firebase Crashlytics part is in the build: the same note is sent to the developer.
   Crashlytics is reached with reflection, so the app compiles and runs without it. */
final class CrashHelper {
    private CrashHelper() { }
    private static boolean on = false;

    static synchronized void install(final Context c) {
        if (on) { return; }
        on = true;
        final Context app = c.getApplicationContext();
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                try {
                    StringWriter sw = new StringWriter();
                    e.printStackTrace(new PrintWriter(sw));
                    String s = sw.toString();
                    if (s.length() > 5000) { s = s.substring(0, 5000); }
                    String note = "Time: " + new java.util.Date() + "\nThread: " + t.getName() + "\nAndroid: " + android.os.Build.VERSION.SDK_INT + "  Phone: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL + "\n\n" + s;
                    app.getSharedPreferences("mw_crash", Context.MODE_PRIVATE).edit().putString("last", note).putLong("at", System.currentTimeMillis()).commit();
                } catch (Throwable ignored) { }
                try { record(e); } catch (Throwable ignored) { }
                if (prev != null) { prev.uncaughtException(t, e); }
            }
        });
    }

    private static Object cl() throws Exception {
        Class<?> k = Class.forName("com.google.firebase.crashlytics.FirebaseCrashlytics", true, CrashHelper.class.getClassLoader());
        return k.getMethod("getInstance").invoke(null);
    }

    static void record(Throwable e) {
        try { Object c = cl(); c.getClass().getMethod("recordException", Throwable.class).invoke(c, e); } catch (Throwable ignored) { }
    }

    /* the user's choice: send crash notes to the developer (off until agreed) */
    static void collect(boolean yes) {
        try { Object c = cl(); c.getClass().getMethod("setCrashlyticsCollectionEnabled", boolean.class).invoke(c, yes); } catch (Throwable ignored) { }
    }

    static boolean available() {
        try { cl(); return true; } catch (Throwable e) { return false; }
    }

    static String last(Context c) {
        try { SharedPreferences sp = c.getSharedPreferences("mw_crash", Context.MODE_PRIVATE); return sp.getString("last", ""); } catch (Throwable e) { return ""; }
    }

    static void clear(Context c) {
        try { c.getSharedPreferences("mw_crash", Context.MODE_PRIVATE).edit().clear().apply(); } catch (Throwable ignored) { }
    }
}
