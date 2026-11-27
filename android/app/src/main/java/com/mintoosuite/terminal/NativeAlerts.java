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
                    if (true) { tick(c); sp(c).edit().remove("err").apply(); }
                } catch (Throwable e) { String m = String.valueOf(e); if (!m.equals(sp(c).getString("err", ""))) { sp(c).edit().putString("err", m).apply(); } }
                try { if (running && hh == h) { hh.postDelayed(this, 10000L); } } catch (Throwable ignored) { }
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
        if (first == null || r.peekFirst() == null) { return Double.NaN; }
        if (now - r.peekFirst()[0] < win * 0.6) { return Double.NaN; } /* not enough history yet */
        ref = first[1]; if (!(ref > 0)) { return Double.NaN; }
        return (p / ref - 1) * 100;
    }

    /* build 545: every exchange, pumps AND dumps, 1 / 3 / 5-minute moves (much earlier than the old 5 / 15-minute check).
       Settings from the Alert centre (PumpAlert prefs). Volume filter keeps out thin coins. */
    private static final HashMap<String, Long> FIRED = new HashMap<>();
    private static final HashMap<String, Integer> FTIER = new HashMap<>();
    private static final HashMap<String, Double> FPX = new HashMap<>();
    private static void feed(Context c, String ex, String sym, double p, double vol, long now, java.util.ArrayList<String[]> out) {
        if (!(p > 0)) { return; }
        String k = ex + ":" + sym; ArrayDeque<double[]> r = RING.get(k); if (r == null) { r = new ArrayDeque<>(); RING.put(k, r); }
        double[] last = r.peekLast(); if (last != null && (p / last[1] > 6 || p / last[1] < 1.0 / 6)) { r.clear(); }
        r.addLast(new double[]{now, p}); while (!r.isEmpty() && now - r.peekFirst()[0] > 6 * 60000L) { r.pollFirst(); }
        SharedPreferences ps = PumpAlert.sp(c);
        if (vol < ps.getFloat("minVol", 3e6f)) { return; }
        double d1 = rise(r, 60000L, now, p), d3 = rise(r, 180000L, now, p), d5 = rise(r, 300000L, now, p);
        double t1 = ps.getFloat("p1", 2.5f), t3 = ps.getFloat("p3", 4f), t5 = ps.getFloat("p5", 6f);
        int tier = 0; String why = ""; boolean up = true; double mv = 0;
        double[][] W = {{d1, t1, 1}, {d3, t3, 3}, {d5, t5, 5}};
        for (double[] w : W) {
            if (Double.isNaN(w[0])) { continue; }
            if (w[0] >= w[1]) { int tt = w[0] >= w[1] * 2 ? 2 : 1; if (tt > tier) { tier = tt; up = true; mv = Math.abs(w[0]); why = "+" + String.format(java.util.Locale.US, "%.1f", w[0]) + "% in " + (int) w[2] + " min"; } }
            else if (w[0] <= -w[1]) { int tt = w[0] <= -w[1] * 2 ? 2 : 1; if (tt > tier) { tier = tt; up = false; mv = Math.abs(w[0]); why = String.format(java.util.Locale.US, "%.1f", w[0]) + "% in " + (int) w[2] + " min"; } }
        }
        /* 550 owner: alerts came late / against the move. Keep only fresh moves: the 3-minute move must agree with the
           direction, the last minute must still be going that way, and a move already far stretched (5-min move over
           3x its trigger) is called exhausted and skipped. */
        if (tier > 0) {
            double sg = up ? 1 : -1;
            if (!Double.isNaN(d3) && d3 * sg < 0) { tier = 0; }
            if (!Double.isNaN(d1) && d1 * sg < t1 * 0.25) { tier = 0; }
            if (!Double.isNaN(d5) && Math.abs(d5) > t5 * 3) { tier = 0; }
            /* 556 owner (INTER came at the top): skip if the coin has already given back over 40% of its 3-minute move */
            double mx = 0, mn = Double.MAX_VALUE; for (double[] q : r) { if (now - q[0] <= 180000L) { mx = Math.max(mx, q[1]); mn = Math.min(mn, q[1]); } }
            if (mx > mn && mn > 0) { double back = up ? (mx - p) / (mx - mn) : (p - mn) / (mx - mn); if (back > 0.4) { tier = 0; } }
        }
        if (tier == 0) { if (!Double.isNaN(d1) && Math.abs(d1) < t1 / 3) { PumpAlert.moveOver(k); } return; }
        if (up && !ps.getBoolean("pump", true)) { return; } if (!up && !ps.getBoolean("dump", true)) { return; }
        String fk = k + (up ? "+" : "-"); Long ft = FIRED.get(fk); Integer fti = FTIER.get(fk);
        /* 554 owner: a coin that keeps pumping / dumping alerts again once it has moved one more trigger step beyond the last alert (at most every 2 min) */
        Double lp = FPX.get(fk); boolean further = lp != null && lp > 0 && (up ? (p / lp - 1) * 100 >= t1 : (1 - p / lp) * 100 >= t1) && ft != null && now - ft >= 2 * 60000L;
        if (ft != null && now - ft < 15 * 60000L && (fti == null || tier <= fti) && !further) { return; }
        FIRED.put(fk, now); FTIER.put(fk, tier); FPX.put(fk, p);
        out.add(new String[]{up ? "1" : "0", ex, sym, why, trim(p), String.valueOf(Math.round(vol)), String.valueOf(mv)});
    }
    private static boolean quiet(Context c) {
        SharedPreferences ps = PumpAlert.sp(c); int a = ps.getInt("qs", -1), b = ps.getInt("qe", -1); if (a < 0 || b < 0 || a == b) { return false; }
        java.util.Calendar k = java.util.Calendar.getInstance(); int m = k.get(java.util.Calendar.HOUR_OF_DAY) * 60 + k.get(java.util.Calendar.MINUTE);
        return a < b ? (m >= a && m < b) : (m >= a || m < b);
    }
    private static void tick(Context c) throws Exception {
        SharedPreferences ps = PumpAlert.sp(c); if (!ps.getBoolean("on", true)) { return; }
        String ex = ps.getString("ex", "bmygo"); long now = System.currentTimeMillis(); java.util.ArrayList<String[]> out = new java.util.ArrayList<>();
        if (ex.indexOf('b') >= 0) { try { JSONArray a = new JSONArray(get("https://fapi.binance.com/fapi/v1/ticker/24hr")); for (int i = 0; i < a.length(); i++) { JSONObject o = a.getJSONObject(i); String s = o.optString("symbol"); if (s.endsWith("USDT") && !s.contains("_")) { feed(c, "Binance", s.substring(0, s.length() - 4), o.optDouble("lastPrice", 0), o.optDouble("quoteVolume", 0), now, out); } } } catch (Exception ignored) { } }
        if (ex.indexOf('m') >= 0) { try { JSONArray a = new JSONObject(get("https://contract.mexc.com/api/v1/contract/ticker")).optJSONArray("data"); for (int i = 0; a != null && i < a.length(); i++) { JSONObject o = a.getJSONObject(i); String s = o.optString("symbol"); if (s.endsWith("_USDT")) { feed(c, "MEXC", s.substring(0, s.length() - 5), o.optDouble("lastPrice", 0), o.optDouble("amount24", 0), now, out); } } } catch (Exception ignored) { } }
        if (ex.indexOf('y') >= 0) { try { JSONArray a = new JSONObject(get("https://api.bybit.com/v5/market/tickers?category=linear")).getJSONObject("result").optJSONArray("list"); for (int i = 0; a != null && i < a.length(); i++) { JSONObject o = a.getJSONObject(i); String s = o.optString("symbol"); if (s.endsWith("USDT")) { feed(c, "Bybit", s.substring(0, s.length() - 4), o.optDouble("lastPrice", 0), o.optDouble("turnover24h", 0), now, out); } } } catch (Exception ignored) { } }
        if (ex.indexOf('g') >= 0) { try { JSONArray a = new JSONObject(get("https://api.bitget.com/api/v2/mix/market/tickers?productType=USDT-FUTURES")).optJSONArray("data"); for (int i = 0; a != null && i < a.length(); i++) { JSONObject o = a.getJSONObject(i); String s = o.optString("symbol"); if (s.endsWith("USDT")) { feed(c, "Bitget", s.substring(0, s.length() - 4), o.optDouble("lastPr", 0), o.optDouble("usdtVolume", o.optDouble("quoteVolume", 0)), now, out); } } } catch (Exception ignored) { } }
        if (ex.indexOf('o') >= 0) { try { JSONArray a = new JSONObject(get("https://www.okx.com/api/v5/market/tickers?instType=SWAP")).optJSONArray("data"); for (int i = 0; a != null && i < a.length(); i++) { JSONObject o = a.getJSONObject(i); String s = o.optString("instId"); if (s.endsWith("-USDT-SWAP")) { double l = o.optDouble("last", 0); feed(c, "OKX", s.substring(0, s.length() - 10), l, o.optDouble("volCcy24h", 0) * l, now, out); } } } catch (Exception ignored) { } }
        if (out.isEmpty() || quiet(c)) { return; }
        if (c.getSharedPreferences("mw_mode538", Context.MODE_PRIVATE).getBoolean("tradeOff", false)) { return; }
        java.util.Collections.sort(out, (x, y) -> Double.compare(Double.parseDouble(y[6]), Double.parseDouble(x[6])));
        int n = 0;
        for (String[] a : out) {
            if (n++ >= 3) { break; }
            boolean up = "1".equals(a[0]); String title = (up ? "🟢 " : "🔴 ") + a[2] + (up ? " pumping" : " dumping") + " · " + a[1];
            String body = a[3] + " · now " + a[4] + " · 24h volume $" + String.format(java.util.Locale.US, "%,d", Long.parseLong(a[5])) + ". Fast moves reverse fast: use a stop.";
            if (WebHolder.isFg()) { WebHolder.js("window.__pump545&&window.__pump545(" + new JSONArray(java.util.Arrays.asList(a)).toString() + ");"); }
            else { PumpAlert.fire(c, up, a[1] + ":" + a[2], title, body); }
        }
    }

    static String trim(double p) { return p >= 1 ? String.format(java.util.Locale.US, "%.4f", p) : String.format(java.util.Locale.US, "%.6f", p); }

    private static boolean post(Context c, String base, String title, String body) {
        if (c.getSharedPreferences("mw_mode538", Context.MODE_PRIVATE).getBoolean("tradeOff", false)) { return false; } /* build 538: mode without trading */
        if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) { return false; }
        MainActivity.alertChannel(c);
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        open.putExtra("mw_t", title == null ? "" : title).putExtra("mw_b", body == null ? "" : body);
        PendingIntent pi = PendingIntent.getActivity(c, 3000 + (base.hashCode() & 0x3ff), open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, MainActivity.CHANNEL).setSmallIcon(R.drawable.ic_stat).setContentTitle(title).setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body)).setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).setGroup("mw_alerts").setShowWhen(true).setWhen(System.currentTimeMillis()).setTimeoutAfter(60L * 60L * 1000L).setContentIntent(pi);
        ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).notify("nal_" + base, 500, b.build());
        try { Overlay.show(c, title, body); } catch (Exception ignored) { }
        return true;
    }
}
