package com.mintoosuite.terminal;

import android.Manifest;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import androidx.core.app.NotificationCompat;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/* Native background alerts. Runs inside BotService on its own thread, so alerts keep working even if the web page is asleep.
   One light request every 30 s (all Binance futures prices, weight 2). It measures the rise over 5 and 15 minutes itself.
   It never alerts for a coin the web part already alerted in the last 10 minutes, and the web part skips coins this part alerted. */
final class NativeAlerts {
    private static HandlerThread th;
    private static volatile Handler h;
    private static volatile boolean running;
    private static long pauseUntil;
    private static final HashMap<String, ArrayDeque<double[]>> RING = new HashMap<>();
    private static final HashMap<String, Long> LASTV = new HashMap<>();
    private static final String SP = "mw_nal";

    static SharedPreferences sp(Context c) { return c.getApplicationContext().getSharedPreferences(SP, Context.MODE_PRIVATE); }

    static void setCfg(Context c, boolean on, boolean hi) { sp(c).edit().putBoolean("on", on).putBoolean("hi", hi).apply(); }

    /* base coin name taken from an alert title such as "🚀 ESPORTS is pumping on Binance" */
    static String key(String title) {
        if (title == null) { return ""; }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("([A-Z0-9]{2,15})(?:USDT)?\\s+(?:is|:)|COIN on [A-Za-z ]+:\\s*([A-Z0-9]{2,15})").matcher(title);
        if (m.find()) { String a = m.group(1) != null ? m.group(1) : m.group(2); return a == null ? "" : a; }
        return "";
    }

    static void jsNotified(Context c, String title) {
        String k = key(title); if (k.isEmpty()) { return; }
        sp(c).edit().putLong("js_" + k, System.currentTimeMillis()).apply();
    }

    static boolean seenRecently(Context c, String title, String who) {
        String k = key(title); if (k.isEmpty()) { return false; }
        return System.currentTimeMillis() - sp(c).getLong(who + "_" + k, 0) < 60 * 60000L;
    }

    static synchronized void start(final Context c0) {
        if (running) { return; }
        running = true;
        final Context c = c0.getApplicationContext();
        th = new HandlerThread("mw-nal"); th.start(); final Handler hh = new Handler(th.getLooper()); h = hh;
        hh.postDelayed(new Runnable() {
            @Override public void run() {
                try {
                    if (sp(c).getBoolean("on", true) && System.currentTimeMillis() >= pauseUntil && !WebHolder.isFg()) { tick(c); sp(c).edit().remove("err").apply(); }
                } catch (Throwable e) { String m = String.valueOf(e); if (!m.equals(sp(c).getString("err", ""))) { sp(c).edit().putString("err", m).apply(); } }
                try { if (running && hh == h) { hh.postDelayed(this, 30000L); } } catch (Throwable ignored) { }
            }
        }, 8000L);
    }

    static synchronized void stop() {
        running = false;
        try { if (h != null) { h.removeCallbacksAndMessages(null); } if (th != null) { th.quitSafely(); } } catch (Exception ignored) { }
        h = null; th = null;
    }

    private static String get(String u) throws Exception {
        HttpURLConnection cn = (HttpURLConnection) new URL(u).openConnection();
        cn.setConnectTimeout(8000); cn.setReadTimeout(10000); cn.setRequestProperty("User-Agent", "MintooSuite");
        int code = cn.getResponseCode();
        if (code == 418 || code == 429 || code == 403) { long ra = 300000L; try { ra = Math.max(ra, 1000L * Long.parseLong(cn.getHeaderField("Retry-After"))); } catch (Exception ignored) { } pauseUntil = System.currentTimeMillis() + ra; throw new Exception("HTTP " + code); }
        if (code == 451) { pauseUntil = System.currentTimeMillis() + 30 * 60000L; throw new Exception("HTTP 451"); }
        if (code != 200) { throw new Exception("HTTP " + code); }
        BufferedReader r = new BufferedReader(new InputStreamReader(cn.getInputStream())); StringBuilder sb = new StringBuilder(); String ln;
        while ((ln = r.readLine()) != null) { sb.append(ln); }
        r.close(); return sb.toString();
    }

    private static double rise(ArrayDeque<double[]> r, long win, long now, double p) {
        double ref = -1; double[] first = null;
        for (double[] x : r) { if (now - x[0] <= win) { first = x; break; } }
        if (first == null || r.peekFirst() == null) { return -1; }
        if (now - r.peekFirst()[0] < win * 0.6) { return -1; } /* not enough history yet */
        ref = first[1]; if (!(ref > 0)) { return -1; }
        return (p / ref - 1) * 100;
    }

    private static void tick(Context c) throws Exception {
        JSONArray a = new JSONArray(get("https://fapi.binance.com/fapi/v1/ticker/price"));
        long now = System.currentTimeMillis(); boolean hi = sp(c).getBoolean("hi", false);
        double t5 = hi ? 6 : 10, t15 = hi ? 12 : 20; int cand = 0;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i); String s = o.optString("symbol"); if (!s.endsWith("USDT") || s.contains("_")) { continue; }
            double p = o.optDouble("price", 0); if (!(p > 0)) { continue; }
            ArrayDeque<double[]> r = RING.get(s); if (r == null) { r = new ArrayDeque<>(); RING.put(s, r); }
            double[] last = r.peekLast();
            if (last != null && (p / last[1] > 6 || p / last[1] < 1.0 / 6)) { r.clear(); }
            r.addLast(new double[]{now, p});
            while (!r.isEmpty() && now - r.peekFirst()[0] > 16 * 60000L) { r.pollFirst(); }
            double d5 = rise(r, 5 * 60000L, now, p), d15 = rise(r, 15 * 60000L, now, p);
            int tier = 0; String why = "";
            if (d5 >= t5 * 2) { tier = 2; why = "+" + Math.round(d5) + "% in 5 minutes"; } else if (d5 >= t5) { tier = 1; why = "+" + Math.round(d5) + "% in 5 minutes"; }
            else if (d15 >= t15 * 2) { tier = 2; why = "+" + Math.round(d15) + "% in 15 minutes"; } else if (d15 >= t15) { tier = 1; why = "+" + Math.round(d15) + "% in 15 minutes"; }
            if (tier == 0 || cand >= 3 || System.currentTimeMillis() < pauseUntil) { continue; }
            Long lv = LASTV.get(s); if (lv != null && now - lv < 10 * 60000L) { continue; }
            String base = s.substring(0, s.length() - 4);
            if (System.currentTimeMillis() - sp(c).getLong("js_" + base, 0) < 60 * 60000L) { continue; }
            int fired = sp(c).getInt("t_" + base, 0); long ft = sp(c).getLong("f_" + base, 0);
            if (tier <= fired && now - ft < 60 * 60000L) { continue; }
            /* volume check only for the few candidates (1 light request each) */
            double vol = 0; try { vol = new JSONObject(get("https://fapi.binance.com/fapi/v1/ticker/24hr?symbol=" + s)).optDouble("quoteVolume", 0); } catch (Exception e) { continue; }
            cand++;
            if (vol < (hi ? 15000 : 40000)) { LASTV.put(s, now); continue; }
            if (System.currentTimeMillis() - sp(c).getLong("js_" + base, 0) < 60 * 60000L) { continue; }
            if (!post(c, base, "🚀 " + base + " is pumping on Binance", why + " (measured by the app, now " + trim(p) + "). Very risky: do not chase the jump. Use a stop-loss and only money you can lose.")) { continue; }
            sp(c).edit().putInt("t_" + base, tier).putLong("f_" + base, now).putLong("nat_" + base, now).apply();
        }
    }

    static String trim(double p) { return p >= 1 ? String.format(java.util.Locale.US, "%.4f", p) : String.format(java.util.Locale.US, "%.6f", p); }

    private static boolean post(Context c, String base, String title, String body) {
        if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) { return false; }
        MainActivity.alertChannel(c);
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(c, 3, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, MainActivity.CHANNEL).setSmallIcon(R.drawable.ic_stat).setContentTitle(title).setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body)).setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).setShowWhen(true).setWhen(System.currentTimeMillis()).setTimeoutAfter(60L * 60L * 1000L).setContentIntent(pi);
        ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).notify("nal_" + base, 500, b.build());
        try { Overlay.show(c, title, body); } catch (Exception ignored) { }
        return true;
    }
}
