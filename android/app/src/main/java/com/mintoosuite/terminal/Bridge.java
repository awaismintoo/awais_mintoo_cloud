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

    /* ----- why the page was last restarted (read once, then cleared) ----- */
    @JavascriptInterface
    public String lastRestart(String tok) {
        if (!ok(tok)) { return ""; }
        try {
            android.content.SharedPreferences sp = ctx.getSharedPreferences("mw_why", 0);
            String v = sp.getString("w", "");
            if (v != null && v.length() > 0) { sp.edit().remove("w").apply(); }
            if ((v == null || v.length() == 0) && android.os.Build.VERSION.SDK_INT >= 30) {
                /* build 412: ask Android itself why the app process last ended (low memory, native crash, killed...) */
                try {
                    android.app.ActivityManager am = (android.app.ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
                    java.util.List<android.app.ApplicationExitInfo> xl = am.getHistoricalProcessExitReasons(ctx.getPackageName(), 0, 3);
                    if (xl != null && !xl.isEmpty()) {
                        android.app.ApplicationExitInfo xi = xl.get(0);
                        if (xi.getTimestamp() > sp.getLong("xt", 0L)) {
                            sp.edit().putLong("xt", xi.getTimestamp()).apply();
                            String[] nm = {"unknown", "app exited itself", "killed by a signal", "LOW MEMORY (Android closed it)", "app crashed", "NATIVE CRASH", "app not responding", "start-up failed", "permission change", "too many resources", "user closed it", "user stopped it", "dependency died", "other", "frozen by Android", "package state change", "package updated"};
                            int rs = xi.getReason();
                            v = "x|" + (rs >= 0 && rs < nm.length ? nm[rs] : "code " + rs) + " - " + xi.getDescription() + " (memory use about " + (xi.getPss() / 1024) + " MB)";
                        }
                    }
                } catch (Throwable e2) { }
            }
            return v == null ? "" : v;
        } catch (Throwable e) { return ""; }
    }

    /* ----- experimental native chart: the page gives it data and a place on the screen ----- */
    @JavascriptInterface
    public void nchartPlace(String tok, int x, int y, int w, int h) { if (ok(tok)) { NativeChart.place(x, y, w, h); } }

    @JavascriptInterface
    public void nchartData(String tok, String json, String label) { if (ok(tok)) { NativeChart.data(json, label == null ? "" : label); } }

    @JavascriptInterface
    public void nchartHide(String tok) { if (ok(tok)) { NativeChart.hide(); } }

    /* ----- home-screen widget text: the page saves one short line; the widget shows it ----- */
    @JavascriptInterface
    public void widgetSet(String tok, String text) {
        if (!ok(tok) || text == null) { return; }
        try {
            String t = text.length() > 160 ? text.substring(0, 160) : text;
            ctx.getSharedPreferences("mw_widget", Context.MODE_PRIVATE).edit().putString("text", t).apply();
            MwWidget.refresh(ctx);
        } catch (Throwable e) { }
    }

    /* ----- build 494: "My city" widget (weather, date with Hijri, next event) ----- */
    @JavascriptInterface
    public void lifeSet(String tok, String l1, String l2, String l3) { if (ok(tok)) { LifeWidget.save(ctx, l1, l2, l3); } }

    @JavascriptInterface
    public String lifePin(String tok) { return ok(tok) ? LifeWidget.pin(ctx) : "no"; }

    /* ----- build 518: 🕌 Namaz widget + Azan alarm (times worked out on the phone from the saved place and settings) ----- */
    @JavascriptInterface
    public void prayerSet(String tok, String json) { if (ok(tok)) { PrayerWidget.save(ctx, json); } }

    @JavascriptInterface
    public String prayerPin(String tok) { return ok(tok) ? PrayerWidget.pin(ctx) : "no"; }

    @JavascriptInterface
    public String prayerExact(String tok) { return ok(tok) ? PrayerWidget.exactState(ctx) : "no"; }

    @JavascriptInterface
    public void prayerExactAsk(String tok) { if (ok(tok)) { PrayerWidget.exactAsk(ctx); } }

    /* ----- screenshot and screen-recording block: the page turns it on for wallet and other money screens ----- */
    @JavascriptInterface
    public void setSecure(String tok, boolean on) {
        if (!ok(tok)) { return; }
        final MainActivity a = MainActivity.cur; if (a == null) { return; }
        final boolean s = on;
        a.runOnUiThread(new Runnable() { public void run() { try { if (s) { a.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE); } else { a.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE); } } catch (Exception e) { } } });
    }

    private static boolean okName(String n) { return n != null && n.matches("mintoosuite_[A-Za-z0-9_.\\-]{1,80}"); }

    /* ----- live navigation: keep the screen on while the full-screen map is open (build 520) ----- */
    @JavascriptInterface
    public void keepOn(String tok, boolean on) {
        if (!ok(tok)) { return; }
        final MainActivity a = MainActivity.cur; if (a == null) { return; }
        final boolean s = on;
        a.runOnUiThread(new Runnable() { public void run() { try { if (s) { a.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); } else { a.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); } } catch (Exception e) { } } });
    }

    /* ----- chart rotation: the page asks to lock landscape / portrait, or go back to automatic ----- */
    @JavascriptInterface
    public void orient(String tok, String mode) {
        if (!ok(tok)) { return; }
        final MainActivity a = MainActivity.cur; if (a == null) { return; }
        final int o = "land".equals(mode) ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE : ("port".equals(mode) ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        a.runOnUiThread(new Runnable() { public void run() { try { a.setRequestedOrientation(o); } catch (Exception e) { } } });
    }

    /* ----- ads: the page tells us whether a banner may be shown on the current screen ----- */
    @JavascriptInterface
    public void adsShow(String tok, boolean show) { if (ok(tok)) { AdsHelper.show(show); } }
    @JavascriptInterface
    public void setMinor(String tok, boolean m) { if (ok(tok)) { AdsHelper.setMinor(m); } }

    /* ----- optional usage statistics (off until the user agrees) ----- */
    @JavascriptInterface
    public void anaEnable(String tok, boolean on) { if (ok(tok)) { AnalyticsHelper.enable(ctx, on); } }

    @JavascriptInterface
    public void anaEvent(String tok, String name, String item) { if (ok(tok)) { AnalyticsHelper.event(ctx, name, item); } }

    /* ----- crash notes (kept on the phone; sent to the developer only if the user agrees) ----- */
    @JavascriptInterface
    public String crashLast(String tok) { return ok(tok) ? CrashHelper.last(ctx) : ""; }

    @JavascriptInterface
    public void crashClear(String tok) { if (ok(tok)) { CrashHelper.clear(ctx); } }

    @JavascriptInterface
    public void crashSend(String tok, boolean yes) { if (ok(tok)) { CrashHelper.collect(yes); } }

    @JavascriptInterface
    public boolean crashCloud(String tok) { return ok(tok) && CrashHelper.available(); }

    /* ----- when this app was first installed on this phone (a trial cannot be restarted by clearing app data) ----- */
    @JavascriptInterface
    public long installAt(String tok) {
        if (!ok(tok)) { return 0L; }
        try { return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).firstInstallTime; } catch (Throwable e) { return 0L; }
    }

    /* ----- Google Play Billing ----- */
    @JavascriptInterface
    public String billState(String tok) { if (!ok(tok)) { return "{}"; } BillingHelper.start(ctx); return BillingHelper.state(ctx); }

    @JavascriptInterface
    public void billRefresh(String tok) { if (ok(tok)) { BillingHelper.start(ctx); BillingHelper.refresh(ctx); } }

    @JavascriptInterface
    public void billBuy(String tok, final int idx) {
        if (!ok(tok)) { return; }
        final MainActivity a = MainActivity.cur;
        if (a == null) { return; }
        a.runOnUiThread(new Runnable() { @Override public void run() { BillingHelper.buy(a, idx); } });
    }

    /* ----- Professor microphone (speech to text) ----- */
    @JavascriptInterface
    public void speechStart(String tok, String lang) { if (ok(tok)) { Media.listen(lang); } }

    @JavascriptInterface
    public void speechStop(String tok) { if (ok(tok)) { Media.stop(); } }

    /* ----- language voices: open Android's own "install voice data" screen ----- */
    @JavascriptInterface
    public void ttsInstall(String tok) {
        if (!ok(tok)) { return; }
        try {
            Intent i = new Intent("android.speech.tts.engine.INSTALL_TTS_DATA");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Exception e) {
            try {
                Intent j = new Intent("com.android.settings.TTS_SETTINGS");
                j.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(j);
            } catch (Exception ignored) { }
        }
    }

    /* ----- alerts when the app is closed (needs google-services.json at build time) ----- */
    @JavascriptInterface
    public boolean pushAvailable(String tok) { return ok(tok) && PushHelper.available(); }

    @JavascriptInterface
    public void pushSub(String tok, boolean on) { if (ok(tok)) { PushHelper.subscribe(ctx, on); } }

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

    /* ----- fingerprint log-in (its own slot, separate from the wallet) ----- */
    @JavascriptInterface
    public boolean bioHasS(String tok, String slot) { return ok(tok) && ctx.getSharedPreferences("mw_bio" + safeSlot(slot), Context.MODE_PRIVATE).contains("ct"); }

    @JavascriptInterface
    public void bioSaveS(String tok, String slot, String secret, String cb) {
        if (!ok(tok)) { return; }
        MainActivity a = MainActivity.cur;
        if (a == null) { MainActivity.jsCb(cb, false, "Open the app first."); return; }
        a.bioSave(safeSlot(slot), secret, cb);
    }

    @JavascriptInterface
    public void bioUnlockS(String tok, String slot, String cb) {
        if (!ok(tok)) { return; }
        MainActivity a = MainActivity.cur;
        if (a == null) { MainActivity.jsCb(cb, false, "Open the app first."); return; }
        a.bioUnlock(safeSlot(slot), cb);
    }

    @JavascriptInterface
    public void bioClearS(String tok, String slot) {
        if (!ok(tok)) { return; }
        MainActivity a = MainActivity.cur;
        if (a != null) { a.bioClear(safeSlot(slot)); } else { ctx.getSharedPreferences("mw_bio" + safeSlot(slot), Context.MODE_PRIVATE).edit().clear().apply(); }
    }

    private static String safeSlot(String s) { return s != null && s.matches("[a-z]{1,8}") ? "_" + s : "_x"; }

    /* ----- offline language packs (ML Kit, by reflection) ----- */
    @JavascriptInterface
    public boolean trAvailable(String tok) { return ok(tok) && TranslateHelper.available(); }

    @JavascriptInterface
    public void trHave(String tok, final String cb) {
        if (!ok(tok)) { return; }
        TranslateHelper.have((good, data) -> trCb(cb, good, data));
    }

    @JavascriptInterface
    public void trDownload(String tok, String lang, final String cb) {
        if (!ok(tok) || lang == null || !lang.matches("[a-z]{2,3}")) { return; }
        TranslateHelper.download(lang, (good, data) -> trCb(cb, good, data));
    }

    @JavascriptInterface
    public void trTranslate(String tok, String from, String to, String text, final String cb) {
        if (!ok(tok) || from == null || to == null || !from.matches("[a-z]{2,3}") || !to.matches("[a-z]{2,3}")) { return; }
        TranslateHelper.translate(from, to, text == null ? "" : text, (good, data) -> trCb(cb, good, data));
    }

    private static void trCb(String cb, boolean good, String data) {
        WebHolder.js("window.__mwTr&&window.__mwTr(" + org.json.JSONObject.quote(cb) + "," + good + "," + org.json.JSONObject.quote(data == null ? "" : data) + ");");
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
        if (!ok(tok) || !okName(name) || val == null) { return false; }
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
        if (!ok(tok) || !okName(name)) { return ""; }
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
        if (!ok(tok) || !okName(name)) { return; }
        try { ctx.getSharedPreferences("mw_sec", Context.MODE_PRIVATE).edit().remove(name).commit(); } catch (Throwable e) { }
    }


    @JavascriptInterface
    public void exitApp(String tok) {
        if (!ok(tok)) { return; }
        final MainActivity a = MainActivity.cur;
        if (a != null) { a.runOnUiThread(new Runnable() { @Override public void run() { a.finishAffinity(); } }); }
    }

    /* freeze guard: number of automatic restarts in the last 15 minutes (the page then starts in safe mode) */
    @JavascriptInterface
    public int guardRecent(String tok) { return ok(tok) ? Guard.recent(ctx) : 0; }

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
                    if (java.util.regex.Pattern.compile("withdraw|/transfer|sub-?account/(universal|transfer)|capital/deposit/subAddress|asset/(transfer|withdraw)|wallet/withdraw|sapi/v1/(capital/withdraw|asset/transfer|sub-account)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(u.getPath() == null ? "" : u.getPath()).find()) { throw new Exception("withdrawals and transfers are blocked by MintooSuite"); }
                    String m = "POST".equals(method) ? "POST" : ("DELETE".equals(method) ? "DELETE" : "GET");
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection) u.openConnection();
                    c.setInstanceFollowRedirects(false);
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

    /* build 515: ask Android to place the prices widget on the home screen */
    @JavascriptInterface
    public String pricePin(String tok) {
        if (!ok(tok)) { return "no"; }
        try {
            if (Build.VERSION.SDK_INT < 26) { return "old"; }
            android.appwidget.AppWidgetManager m = ctx.getSystemService(android.appwidget.AppWidgetManager.class);
            if (m == null || !m.isRequestPinAppWidgetSupported()) { return "old"; }
            return m.requestPinAppWidget(new android.content.ComponentName(ctx, MwWidget.class), null, null) ? "ok" : "no";
        } catch (Exception e) { return "no"; }
    }

    /* build 514: open a web link in the phone's browser or app (YouTube, Maps, shops) */
    @JavascriptInterface
    public boolean openUrl(String tok, String url) {
        if (!ok(tok) || url == null) { return false; }
        try {
            android.net.Uri u = android.net.Uri.parse(url);
            String sch = u.getScheme();
            if (!"https".equals(sch) && !"http".equals(sch) && !"geo".equals(sch)) { return false; }
            Intent i = new Intent(Intent.ACTION_VIEW, u);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
            return true;
        } catch (Exception e) { return false; }
    }

    /* build 514: fetch any public https resource (text or picture) through Android itself when the page's own fetch fails;
       follows up to 4 https redirects, max 8 MB, returns the bytes as base64 and the content type */
    @JavascriptInterface
    public void httpB64(String tok, final String url, final String cb) {
        if (!ok(tok)) { return; }
        new Thread(new Runnable() {
            @Override
            public void run() {
                int code = 0; String out = ""; String type = "";
                try {
                    String cur = url;
                    for (int hop = 0; hop < 5; hop++) {
                        java.net.URL u = new java.net.URL(cur);
                        if (!"https".equals(u.getProtocol())) { throw new Exception("https only"); }
                        for (java.net.InetAddress ia : java.net.InetAddress.getAllByName(u.getHost())) {
                            if (ia.isLoopbackAddress() || ia.isSiteLocalAddress() || ia.isLinkLocalAddress() || ia.isAnyLocalAddress() || ia.isMulticastAddress()) { throw new Exception("blocked address"); }
                        }
                        java.net.HttpURLConnection c = (java.net.HttpURLConnection) u.openConnection();
                        c.setInstanceFollowRedirects(false);
                        c.setConnectTimeout(10000);
                        c.setReadTimeout(20000);
                        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) MintooSuite/10.8 (github.com/awaismintoo/awais_mintoo_cloud)");
                        code = c.getResponseCode();
                        if (code >= 300 && code < 400 && c.getHeaderField("Location") != null) { cur = new java.net.URL(u, c.getHeaderField("Location")).toString(); continue; }
                        type = c.getContentType() == null ? "" : c.getContentType();
                        java.io.InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                        byte[] buf = new byte[16384];
                        int n, total = 0;
                        while (in != null && (n = in.read(buf)) > 0 && total < 8000000) { bo.write(buf, 0, n); total += n; }
                        out = android.util.Base64.encodeToString(bo.toByteArray(), android.util.Base64.NO_WRAP);
                        break;
                    }
                } catch (Exception e) { code = 0; out = String.valueOf(e.getMessage()); }
                WebHolder.js("window.__mwHttpB&&window.__mwHttpB(" + org.json.JSONObject.quote(cb) + "," + code + "," + org.json.JSONObject.quote(type) + "," + org.json.JSONObject.quote(out) + ");");
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
                    for (java.net.InetAddress ia : java.net.InetAddress.getAllByName(u.getHost())) {
                        if (ia.isLoopbackAddress() || ia.isSiteLocalAddress() || ia.isLinkLocalAddress() || ia.isAnyLocalAddress() || ia.isMulticastAddress()) { throw new Exception("blocked address"); }
                    }
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection) u.openConnection();
                    c.setInstanceFollowRedirects(false);
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(10000);
                    c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) MintooSuite");
                    int code = c.getResponseCode();
                    java.io.InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                    java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n, total = 0;
                    while (in != null && (n = in.read(buf)) > 0 && total < 6000000) { bo.write(buf, 0, n); total += n; }
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
    public String notifOpen(String tok) {
        if (!ok(tok)) { return ""; }
        MainActivity a = MainActivity.cur;
        return a == null ? "" : a.takeNotif();
    }

    @JavascriptInterface
    public void notify(String tok, String title, String body) {
        if (!ok(tok)) { return; }
        if (title != null && title.length() > 80) { title = title.substring(0, 80); }
        if (body != null && body.length() > 300) { body = body.substring(0, 300); }
        if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) { return; }
        try { NativeAlerts.jsNotified(ctx, title); } catch (Exception ignored) { }
        MainActivity.alertChannel(ctx);
        Intent open = new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        final int slot = nid++ % 50;
        open.putExtra("mw_t", title == null ? "" : title).putExtra("mw_b", body == null ? "" : body);
        PendingIntent pi = PendingIntent.getActivity(ctx, 100 + slot, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, MainActivity.CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title == null ? "MintooSuite" : title)
                .setContentText(body == null ? "" : body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body == null ? "" : body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setShowWhen(true).setWhen(System.currentTimeMillis()).setTimeoutAfter(60L * 60L * 1000L)
                .setContentIntent(pi);
        ((NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE)).notify(100 + slot, b.build());
        try { Overlay.show(ctx, title, body); } catch (Exception ignored) { }
    }

    /* BJ2: save a document or picture into Downloads/MintooSuite and share text (no storage permission needed on Android 10+) */
    @JavascriptInterface
    public boolean saveDoc(String tok, String name, String mime, String content) {
        if (!ok(tok)) { return false; }
        try { return writeDownload(name, mime, content.getBytes("UTF-8")); } catch (Exception e) { return false; }
    }

    @JavascriptInterface
    public boolean saveBin(String tok, String name, String mime, String b64) {
        if (!ok(tok)) { return false; }
        try { return writeDownload(name, mime, android.util.Base64.decode(b64, android.util.Base64.DEFAULT)); } catch (Exception e) { return false; }
    }

    private boolean writeDownload(String name, String mime, byte[] data) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                android.content.ContentValues v = new android.content.ContentValues();
                v.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name);
                v.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime);
                v.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/MintooSuite");
                android.net.Uri u = ctx.getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (u == null) { return false; }
                java.io.OutputStream o = ctx.getContentResolver().openOutputStream(u);
                if (o == null) { return false; }
                o.write(data);
                o.close();
                return true;
            }
        } catch (Exception ignored) { }
        return false;
    }

    @JavascriptInterface
    public void shareText(String tok, String title, String text) {
        if (!ok(tok)) { return; }
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_SUBJECT, title);
            i.putExtra(Intent.EXTRA_TEXT, text);
            Intent c = Intent.createChooser(i, "Share");
            c.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(c);
        } catch (Exception ignored) { }
    }

    /* native background alerts: settings from the web part, and "did the native part already alert this coin?" */
    @JavascriptInterface
    public void nalCfg(String tok, boolean on, boolean hi) { if (ok(tok)) { NativeAlerts.setCfg(ctx, on, hi); } }

    @JavascriptInterface
    public boolean nalSeen(String tok, String title) { return ok(tok) && NativeAlerts.seenRecently(ctx, title, "nat"); }

    @JavascriptInterface
    public String nalInfo(String tok) { if (!ok(tok)) { return ""; } android.content.SharedPreferences s = NativeAlerts.sp(ctx); return "on=" + s.getBoolean("on", true) + " err=" + s.getString("err", ""); }

    /* native coin list (experimental) */
    @JavascriptInterface
    public void nlistPlace(String tok, int x, int y, int w, int h) { if (ok(tok)) { NativeList.place(x, y, w, h); } }

    @JavascriptInterface
    public void nlistData(String tok, String json) { if (ok(tok)) { NativeList.data(json); } }

    @JavascriptInterface
    public void nlistHide(String tok) { if (ok(tok)) { NativeList.hide(); } }

    /* ----- MintooSuiteVPN (WireGuard) ----- */
    @JavascriptInterface
    public String vpnState(String tok) { return ok(tok) ? VpnHelper.state(ctx) : "{}"; }

    @JavascriptInterface
    public boolean vpnSave(String tok, String text) { return ok(tok) && VpnHelper.save(ctx, text); }

    @JavascriptInterface
    public void vpnClear(String tok) { if (ok(tok)) { VpnHelper.clear(ctx); } }

    @JavascriptInterface
    public void vpnUp(String tok) {
        if (!ok(tok)) { return; }
        final MainActivity a = MainActivity.cur;
        if (a == null) { return; }
        a.runOnUiThread(new Runnable() { @Override public void run() { VpnHelper.up(a); } });
    }

    @JavascriptInterface
    public void vpnDown(String tok) { if (ok(tok)) { VpnHelper.down(ctx); } }

    /* ----- Baba AI reads aloud with the phone's own voice ----- */
    @JavascriptInterface
    public String ttsSpeak(String tok, String text, String lang) { return ok(tok) ? TtsHelper.speak(ctx, text, lang) : "no"; }

    @JavascriptInterface
    public void ttsStop(String tok) { if (ok(tok)) { TtsHelper.stop(); } }
}
