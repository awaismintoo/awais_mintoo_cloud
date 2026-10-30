package com.mintoosuite.terminal;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

/* Alerts that arrive when the app is closed (Firebase Cloud Messaging), written with reflection so the app always
   compiles and simply does nothing if the library or google-services.json is missing. The phone joins one public
   topic ("picks"); no personal data is sent. */
final class PushHelper {
    private PushHelper() { }

    static final String CH = "mintoo_push";
    static final String TOPIC = "picks";

    static void channel(Context c) {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
                nm.createNotificationChannel(new NotificationChannel(CH, "Baba picks (app closed)", NotificationManager.IMPORTANCE_DEFAULT));
            }
        } catch (Throwable ignored) { }
    }

    static boolean available() {
        try { Class.forName("com.google.firebase.messaging.FirebaseMessaging", false, PushHelper.class.getClassLoader()); return true; } catch (Throwable t) { return false; }
    }

    static void subscribe(Context c, boolean on) {
        try {
            channel(c);
            Class<?> k = Class.forName("com.google.firebase.messaging.FirebaseMessaging", true, PushHelper.class.getClassLoader());
            Object m = k.getMethod("getInstance").invoke(null);
            m.getClass().getMethod(on ? "subscribeToTopic" : "unsubscribeFromTopic", String.class).invoke(m, TOPIC);
        } catch (Throwable ignored) { }
    }
}
