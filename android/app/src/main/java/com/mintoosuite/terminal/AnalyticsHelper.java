package com.mintoosuite.terminal;

import android.content.Context;
import android.os.Bundle;

/* Optional usage statistics (Firebase Analytics), written with reflection so the app always compiles and
   does nothing if the library or google-services.json is missing. Off until the user switches it on. */
final class AnalyticsHelper {
    private AnalyticsHelper() { }

    private static Object inst(Context c) throws Exception {
        Class<?> k = Class.forName("com.google.firebase.analytics.FirebaseAnalytics", true, AnalyticsHelper.class.getClassLoader());
        return k.getMethod("getInstance", Context.class).invoke(null, c.getApplicationContext());
    }

    static void enable(Context c, boolean on) {
        try { Object a = inst(c); a.getClass().getMethod("setAnalyticsCollectionEnabled", boolean.class).invoke(a, on); } catch (Throwable ignored) { }
    }

    static void event(Context c, String name, String param) {
        try {
            if (name == null || !name.matches("[a-z][a-z0-9_]{0,39}")) { return; }
            Bundle b = new Bundle();
            if (param != null && param.length() > 0) { b.putString("item", param.length() > 90 ? param.substring(0, 90) : param); }
            Object a = inst(c);
            a.getClass().getMethod("logEvent", String.class, Bundle.class).invoke(a, name, b);
        } catch (Throwable ignored) { }
    }
}
