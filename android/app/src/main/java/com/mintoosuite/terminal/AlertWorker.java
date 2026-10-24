package com.mintoosuite.terminal;

import android.Manifest;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;

/** Checks the saved price alerts about every 15 minutes, even when the app is closed. */
public class AlertWorker extends Worker {
    public AlertWorker(@NonNull Context context, @NonNull WorkerParameters params) { super(context, params); }

    @NonNull
    @Override
    public Result doWork() {
        try {
            Context ctx = getApplicationContext();
            SharedPreferences sp = ctx.getSharedPreferences("mw_bg", Context.MODE_PRIVATE);
            String rules = sp.getString("rules", "");
            if (rules.isEmpty()) { return Result.success(); }
            JSONObject o = new JSONObject(rules);
            JSONArray pal = o.optJSONArray("pal");
            if (pal == null || pal.length() == 0) { return Result.success(); }
            JSONArray prices = new JSONArray(get("https://fapi.binance.com/fapi/v1/ticker/price"));
            HashMap<String, Double> px = new HashMap<>();
            for (int i = 0; i < prices.length(); i++) {
                JSONObject r = prices.getJSONObject(i);
                px.put(r.getString("symbol"), Double.parseDouble(r.getString("price")));
            }
            JSONArray fired = new JSONArray(sp.getString("fired", "[]"));
            boolean changed = false;
            for (int i = 0; i < pal.length(); i++) {
                JSONObject a = pal.getJSONObject(i);
                if (a.optBoolean("done")) { continue; }
                String sym = a.getString("sym");
                Double p = px.get(sym);
                if (p == null) { continue; }
                double target = a.getDouble("price");
                boolean above = "above".equals(a.optString("dir"));
                if (above ? p >= target : p <= target) {
                    String name = sym.endsWith("USDT") ? sym.substring(0, sym.length() - 4) : sym;
                    notifyUser(ctx, "Price alert: " + name, name + " is " + (above ? "above " : "below ") + target + " (now " + p + ")", i);
                    a.put("done", true);
                    fired.put(a.getLong("id"));
                    changed = true;
                }
            }
            if (changed) {
                o.put("pal", pal);
                sp.edit().putString("rules", o.toString()).putString("fired", fired.toString()).apply();
            }
            return Result.success();
        } catch (Exception e) {
            return Result.success();
        }
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(10000);
        InputStream in = c.getInputStream();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) { bo.write(buf, 0, n); }
        return bo.toString("UTF-8");
    }

    private static void notifyUser(Context ctx, String title, String body, int n) {
        if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) { return; }
        Intent open = new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, "mintoo_alerts")
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi);
        ctx.getSystemService(NotificationManager.class).notify(100 + (int) (System.currentTimeMillis() % 800) + n, b.build());
    }
}
