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

    /* ----- ads: the page tells us whether a banner may be shown on the current screen ----- */
    @JavascriptInterface
    public void adsShow(String tok, boolean show) { if (ok(tok)) { AdsHelper.show(show); } }

    /* ----- optional usage statistics (off until the user agrees) ----- */
    @JavascriptInterface
    public void anaEnable(String tok, boolean on) { if (ok(tok)) { AnalyticsHelper.enable(ctx, on); } }

    @JavascriptInterface
    public void anaEvent(String tok, String name, String item) { if (ok(tok)) { AnalyticsHelper.event(ctx, name, item); } }

    /* ----- Professor microphone (speech to text) ----- */
    @JavascriptInterface
    public void speechStart(String tok, String lang) { if (ok(tok)) { Media.listen(lang); } }

    @JavascriptInterface
    public void speechStop(String tok) { if (ok(tok)) { Media.stop(); } }

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
    public void lockAuth(String tok, String cb) {
        if (!ok(tok)) { return; }
        MainActivity a = MainActivity.cur;
        if (a == null) { MainActivity.lockCb(cb, false, "Open the app first."); return; }
        a.lockAuth(cb);
    }

    @JavascriptInterface
    public void lockSet(String tok, boolean on, String cb) {
        if (!ok(tok)) { return; }
        MainActivity a = MainActivity.cur;
        if (a == null) { MainActivity.lockCb(cb, false, "Open the app first."); return; }
        a.lockChange(on, cb);
    }

    /* ----- permissions popup ----- */
    private boolean has(String p) {
        try { return androidx.core.content.ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED; } catch (Exception e) { return false; }
    }

    private boolean overlayOk() {
        try { if (Build.VERSION.SDK_INT >= 23) { return android.provider.Settings.canDrawOverlays(ctx); } return true; } catch (Exception e) { return false; }
    }

    private boolean storeOk() {
        if (Build.VERSION.SDK_INT >= 33) { return has("android.permission.READ_MEDIA_IMAGES"); }
        return has(Manifest.permission.READ_EXTERNAL_STORAGE);
    }


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
        return "{\"sdk\":" + Build.VERSION.SDK_INT + ",\"notif\":" + notif + ",\"batt\":" + batt + ",\"lock\":" + lock + ",\"svc\":" + BotService.isOn(ctx) + ",\"overlay\":" + overlayOk() + ",\"loc\":" + has(Manifest.permission.ACCESS_FINE_LOCATION) + ",\"cam\":" + has(Manifest.permission.CAMERA) + ",\"mic\":" + has(Manifest.permission.RECORD_AUDIO) + ",\"store\":" + storeOk() + "}";
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
                    else if ("overlay".equals(which)) { a.openOverlaySettings(); }
                    else if ("loc".equals(which)) { a.askRuntime(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}); }
                    else if ("cam".equals(which)) { a.askRuntime(new String[]{Manifest.permission.CAMERA}); }
                    else if ("mic".equals(which)) { a.askRuntime(new String[]{Manifest.permission.RECORD_AUDIO}); }
                    else if ("store".equals(which)) { a.askRuntime(Build.VERSION.SDK_INT >= 33 ? new String[]{"android.permission.READ_MEDIA_IMAGES", "android.permission.READ_MEDIA_VIDEO"} : new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}); }
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

    /* ----- mw-v109: API keys encrypted with a hardware-backed Android Keystore key (AES-256-GCM) ----- */
    private javax.crypto.SecretKey ksKey() throws Exception {
        java.security.KeyStore ks = java.security.KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (!ks.containsAlias("mw_secret_key")) {
            javax.crypto.KeyGenerator g = javax.crypto.KeyGenerator.getInstance(android.security.keystore.KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            g.init(new android.security.keystore.KeyGenParameterSpec.Builder("mw_secret_key", android.security.keystore.KeyProperties.PURPOSE_ENCRYPT | android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256).build());
            g.generateKey();
        }
        return (javax.crypto.SecretKey) ks.getKey("mw_secret_key", null);
    }

    @JavascriptInterface
    public boolean secSet(String tok, String name, String val) {
        if (!ok(tok) || name == null || val == null) { return false; }
        try {
            javax.crypto.Cipher c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
            c.init(javax.crypto.Cipher.ENCRYPT_MODE, ksKey());
            byte[] ct = c.doFinal(val.getBytes("UTF-8"));
            String out = android.util.Base64.encodeToString(c.getIV(), android.util.Base64.NO_WRAP) + ":" + android.util.Base64.encodeToString(ct, android.util.Base64.NO_WRAP);
            return ctx.getSharedPreferences("mw_sec", Context.MODE_PRIVATE).edit().putString(name, out).commit();
        } catch (Throwable e) { return false; }
    }

    @JavascriptInterface
    public String secGet(String tok, String name) {
        if (!ok(tok) || name == null) { return ""; }
        try {
            String v = ctx.getSharedPreferences("mw_sec", Context.MODE_PRIVATE).getString(name, null);
            if (v == null) { return ""; }
            int i = v.indexOf(':');
            byte[] iv = android.util.Base64.decode(v.substring(0, i), android.util.Base64.NO_WRAP);
            byte[] ct = android.util.Base64.decode(v.substring(i + 1), android.util.Base64.NO_WRAP);
            javax.crypto.Cipher c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
            c.init(javax.crypto.Cipher.DECRYPT_MODE, ksKey(), new javax.crypto.spec.GCMParameterSpec(128, iv));
            return new String(c.doFinal(ct), "UTF-8");
        } catch (Throwable e) { return ""; }
    }

    @JavascriptInterface
    public void secDel(String tok, String name) {
        if (!ok(tok) || name == null) { return; }
        try { ctx.getSharedPreferences("mw_sec", Context.MODE_PRIVATE).edit().remove(name).commit(); } catch (Throwable e) { }
    }


    @JavascriptInterface
    public void exitApp(String tok) {
        if (!ok(tok)) { return; }
        final MainActivity a = MainActivity.cur;
        if (a != null) { a.runOnUiThread(new Runnable() { @Override public void run() { a.finishAffinity(); } }); }
    }

    /* signed exchange requests (GET/POST/DELETE) - only to the exchanges the app supports; returns status + body to the page */
    @JavascriptInterface
    public void httpReq(String tok, final String method, final String url, final String headersJson, final String body, final String cb) {
        if (!ok(tok)) { return; }
        new Thread(new Runnable() {
            @Override
            public void run() {
                int code = 0;
                String out;
                try {
                    java.net.URL u = new java.net.URL(url);
                    String h = u.getHost();
                    boolean allowed = "https".equals(u.getProtocol()) && ("api.bitget.com".equals(h) || "api.mexc.com".equals(h) || "api.bybit.com".equals(h) || "www.okx.com".equals(h) || "www.binance.com".equals(h) || "fapi.binance.com".equals(h) || "api.binance.com".equals(h) || "testnet.binancefuture.com".equals(h));
                    if (!allowed) { throw new Exception("host not allowed"); }
                    String m = "POST".equals(method) ? "POST" : ("DELETE".equals(method) ? "DELETE" : "GET");
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection) u.openConnection();
                    c.setRequestMethod(m);
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(12000);
                    c.setRequestProperty("User-Agent", "MintooSuite/10.8");
                    org.json.JSONObject hj = new org.json.JSONObject(headersJson == null || headersJson.length() == 0 ? "{}" : headersJson);
                    java.util.Iterator<String> it = hj.keys();
                    while (it.hasNext()) { String k = it.next(); c.setRequestProperty(k, hj.getString(k)); }
                    if ("POST".equals(m) && body != null) {
                        c.setDoOutput(true);
                        byte[] bb = body.getBytes("UTF-8");
                        c.getOutputStream().write(bb);
                        c.getOutputStream().flush();
                    }
                    code = c.getResponseCode();
                    java.io.InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                    java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n, total = 0;
                    while (in != null && (n = in.read(buf)) > 0 && total < 1000000) { bo.write(buf, 0, n); total += n; }
                    out = bo.toString("UTF-8");
                } catch (Exception e) { out = String.valueOf(e.getMessage()); }
                WebHolder.js("window.__mwHttp2&&window.__mwHttp2(" + org.json.JSONObject.quote(cb) + "," + code + "," + org.json.JSONObject.quote(out) + ");");
            }
        }).start();
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
                    while (in != null && (n = in.read(buf)) > 0 && total < 9000000) { bo.write(buf, 0, n); total += n; }
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
        try { Overlay.show(ctx, title, body); } catch (Exception ignored) { }
    }
}
