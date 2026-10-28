package com.mintoosuite.terminal;

import android.Manifest;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.PowerManager;
import android.webkit.JavascriptInterface;

import androidx.biometric.BiometricManager;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/* mw-v108: everything the web page may ask the phone to do. Every call needs the secret token that only this page receives. */
final class Bridge {
    private final Context ctx;
    private int nid = 1;

    Bridge(Context c) { ctx = c.getApplicationContext(); }

    private boolean ok(String tok) { return tok != null && tok.equals(WebHolder.TOKEN); }

    /* ----- wallet fingerprint ----- */
    @JavascriptInterface
    public boolean bioAvailable(String tok) {
        try { return ok(tok) && BiometricManager.from(ctx).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS; } catch (Exception e) { return false; }
    }

    @JavascriptInterface
    public boolean bioHas(String tok) { return ok(tok) && ctx.getSharedPreferences("mw_bio", Context.MODE_PRIVATE).contains("ct"); }

    @JavascriptInterface
    public void bioSave(String tok, String secret, String cb) {
        if (!ok(tok)) { return; }
        MainActivity a = MainActivity.cur;
        if (a == null) { MainActivity.jsCb(cb, false, "Open the app first."); return; }
        a.bioSave(secret, cb);
    }

    @JavascriptInterface
    public void bioUnlock(String tok, String cb) {
        if (!ok(tok)) { return; }
        MainActivity a = MainActivity.cur;
        if (a == null) { MainActivity.jsCb(cb, false, "Open the app first."); return; }
        a.bioUnlock(cb);
    }

    @JavascriptInterface
    public void bioClear(String tok) {
        if (!ok(tok)) { return; }
        MainActivity a = MainActivity.cur;
        if (a != null) { a.bioClear(); } else { ctx.getSharedPreferences("mw_bio", Context.MODE_PRIVATE).edit().clear().apply(); }
    }

    /* ----- whole-app lock ----- */
    @JavascriptInterface
    public String lockState(String tok) {
        if (!ok(tok)) { return "{}"; }
        boolean can = false;
        try { can = BiometricManager.from(ctx).canAuthenticate(MainActivity.LOCK_AUTH) == BiometricManager.BIOMETRIC_SUCCESS; } catch (Exception ignored) { }
        boolean on = ctx.getSharedPreferences("mw_lock", Context.MODE_PRIVATE).getBoolean("on", false);
        return "{\"on\":" + on + ",\"can\":" + can + "}";
    }

    @JavascriptInterface
    public void lockSet(String tok, boolean on, String cb) {
        if (!ok(tok)) { return; }
        MainActivity a = MainActivity.cur;
        if (a == null) { MainActivity.lockCb(cb, false, "Open the app first."); return; }
        a.lockChange(on, cb);
    }

    /* ----- permissions popup ----- */
    @JavascriptInterface
    public String permState(String tok) {
        if (!ok(tok)) { return "{}"; }
        boolean notif = true, batt = true, lock = false;
        try { notif = NotificationManagerCompat.from(ctx).areNotificationsEnabled(); } catch (Exception ignored) { }
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
                batt = pm.isIgnoringBatteryOptimizations(ctx.getPackageName());
            }
        } catch (Exception ignored) { }
        try { lock = BiometricManager.from(ctx).canAuthenticate(MainActivity.LOCK_AUTH) == BiometricManager.BIOMETRIC_SUCCESS; } catch (Exception ignored) { }
        return "{\"sdk\":" + Build.VERSION.SDK_INT + ",\"notif\":" + notif + ",\"batt\":" + batt + ",\"lock\":" + lock + ",\"svc\":" + BotService.isOn(ctx) + "}";
    }

    @JavascriptInterface
    public void permAsk(String tok, final String which) {
        if (!ok(tok)) { return; }
        final MainActivity a = MainActivity.cur;
        if (a == null) { return; }
        a.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    if ("notif".equals(which)) { a.askNotifications(); }
                    else if ("notifset".equals(which)) { a.openNotificationSettings(); }
                    else if ("batt".equals(which)) { a.askBattery(); }
                    else if ("appset".equals(which)) { a.openAppSettings(); }
                } catch (Exception ignored) { }
            }
        });
    }

    /* ----- background service ----- */
    @JavascriptInterface
    public String svcState(String tok) {
        if (!ok(tok)) { return "{}"; }
        return "{\"on\":" + BotService.isOn(ctx) + ",\"paused\":" + BotService.sp(ctx).getBoolean("paused", false) + "}";
    }

    @JavascriptInterface
    public boolean svcStart(String tok) {
        if (!ok(tok)) { return false; }
        try { BotService.start(ctx); return true; } catch (Exception e) { return false; }
    }

    @JavascriptInterface
    public void svcStop(String tok) {
        if (!ok(tok)) { return; }
        BotService.stop(ctx);
    }

    @JavascriptInterface
    public void svcUpdate(String tok, String title, String text, boolean paused, boolean wake) {
        if (!ok(tok)) { return; }
        BotService.update(ctx, title == null ? "MintooSuite" : title, text == null ? "" : text, paused, wake);
    }

    /* ----- network, alerts, notifications (unchanged from v10.4) ----- */
    @JavascriptInterface
    public void httpGet(String tok, final String url, final String cb) {
        if (!ok(tok)) { return; }
        new Thread(new Runnable() {
            @Override
            public void run() {
                String out;
                boolean good = false;
                try {
                    java.net.URL u = new java.net.URL(url);
                    if (!"https".equals(u.getProtocol())) { throw new Exception("https only"); }
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection) u.openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(10000);
                    c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) MintooSuite");
                    int code = c.getResponseCode();
                    java.io.InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                    java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n, total = 0;
                    while (in != null && (n = in.read(buf)) > 0 && total < 2000000) { bo.write(buf, 0, n); total += n; }
                    out = bo.toString("UTF-8");
                    good = code >= 200 && code < 300;
                } catch (Exception e) { out = String.valueOf(e.getMessage()); }
                WebHolder.js("window.__mwHttp&&window.__mwHttp(" + org.json.JSONObject.quote(cb) + "," + good + "," + org.json.JSONObject.quote(out) + ");");
            }
        }).start();
    }

    @JavascriptInterface
    public void bgSet(String tok, String json) {
        if (!ok(tok)) { return; }
        ctx.getSharedPreferences("mw_bg", Context.MODE_PRIVATE).edit().putString("rules", json).apply();
        boolean any = false;
        try {
            org.json.JSONArray a = new org.json.JSONObject(json).optJSONArray("pal");
            any = a != null && a.length() > 0;
        } catch (Exception ignored) { }
        WorkManager wm = WorkManager.getInstance(ctx);
        if (any) {
            wm.enqueueUniquePeriodicWork("mw_alerts", ExistingPeriodicWorkPolicy.UPDATE,
                    new PeriodicWorkRequest.Builder(AlertWorker.class, 15, TimeUnit.MINUTES)
                            .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                            .build());
        } else {
            wm.cancelUniqueWork("mw_alerts");
        }
    }

    @JavascriptInterface
    public String bgFired(String tok) {
        if (!ok(tok)) { return "[]"; }
        android.content.SharedPreferences sp = ctx.getSharedPreferences("mw_bg", Context.MODE_PRIVATE);
        String f = sp.getString("fired", "[]");
        sp.edit().remove("fired").apply();
        return f;
    }

    @JavascriptInterface
    public void notify(String title, String body) {
        if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) { return; }
        MainActivity.alertChannel(ctx);
        Intent open = new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, MainActivity.CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title == null ? "MintooSuite" : title)
                .setContentText(body == null ? "" : body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body == null ? "" : body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi);
        ((NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE)).notify(100 + (nid++ % 50), b.build());
    }
}
