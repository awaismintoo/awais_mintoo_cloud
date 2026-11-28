package com.mintoosuite.terminal;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;

/* build 545: pump / dump alert that reaches the owner with the app closed and the phone locked.
   - A full-screen card (green = pumping, red = dumping) over the lock screen when Android allows it (full-screen intent),
     otherwise a high-priority notification that pops on top.
   - Optional loud alarm-stream beep that repeats until Dismiss is tapped, or until the move is over, or 10 minutes pass.
   - Settings come from the app's Alert centre (SharedPreferences "mw_pump545"). */
public class PumpAlert extends BroadcastReceiver {
    static final String P = "mw_pump545", CH = "mw_pump545", STOP = "com.mintoosuite.terminal.PUMP_STOP";
    private static MediaPlayer mp;
    private static android.media.ToneGenerator tg;
    private static Runnable beepTask;
    private static String ringKey = "";
    private static final Handler H = new Handler(Looper.getMainLooper());

    static SharedPreferences sp(Context c) { return c.getApplicationContext().getSharedPreferences(P, Context.MODE_PRIVATE); }

    static void channel(Context c) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CH, "Pump & dump alerts", NotificationManager.IMPORTANCE_HIGH);
            ch.setSound(null, null); ch.enableVibration(true); ch.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
            ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
    }

    static int fl() { int f = PendingIntent.FLAG_UPDATE_CURRENT; if (Build.VERSION.SDK_INT >= 23) { f |= PendingIntent.FLAG_IMMUTABLE; } return f; }

    /* up = pump, key = "MEXC:ROG" */
    static void fire(Context c0, boolean up, String key, String title, String body) {
        Context c = c0.getApplicationContext();
        try {
            channel(c);
            int id = 7000 + (key.hashCode() & 0x3ff);
            Intent card = new Intent(c, PumpActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                    .putExtra("up", up).putExtra("k", key).putExtra("t", title).putExtra("b", body);
            PendingIntent full = PendingIntent.getActivity(c, id, card, fl());
            Intent stop = new Intent(c, PumpAlert.class).setAction(STOP).putExtra("k", key);
            PendingIntent ps = PendingIntent.getBroadcast(c, id + 1, stop, fl());
            Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra("mw_t", title).putExtra("mw_b", body).putExtra("mw_k", key);
            PendingIntent po = PendingIntent.getActivity(c, id + 2, open, fl());
            NotificationCompat.Builder b = new NotificationCompat.Builder(c, CH).setSmallIcon(R.drawable.ic_stat).setContentTitle(title).setContentText(body)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(body)).setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setColor(up ? 0xFF16A34A : 0xFFDC2626).setColorized(true).setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setAutoCancel(true)
                    .setContentIntent(po).addAction(0, "Dismiss", ps).addAction(0, "Open chart", po).setDeleteIntent(ps).setTimeoutAfter(30L * 60000L);
            if (sp(c).getBoolean("full", true)) { b.setFullScreenIntent(full, true); }
            ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).notify("pump545", id, b.build());
            if (sp(c).getBoolean("beep", true)) { ring(c, key); }
        } catch (Throwable ignored) { }
    }

    static synchronized void ring(final Context c, String key) {
        try {
            ringKey = key;
            if (mp != null || tg != null) { return; }
            String tone = sp(c).getString("tone", "beep");
            if ("beep".equals(tone) || "siren".equals(tone)) {
                /* build 546: a real loud beep (or siren) on the alarm volume, repeating, instead of a ringtone */
                final boolean siren = "siren".equals(tone);
                tg = new android.media.ToneGenerator(sp(c).getBoolean("loud", true) ? android.media.AudioManager.STREAM_ALARM : android.media.AudioManager.STREAM_NOTIFICATION, 100);
                final boolean loop = sp(c).getBoolean("loop", true);
                beepTask = new Runnable() { int n = 0; @Override public void run() { try { if (tg == null) { return; } tg.startTone(siren ? android.media.ToneGenerator.TONE_CDMA_HIGH_L : android.media.ToneGenerator.TONE_PROP_BEEP2, siren ? 900 : 400); n++; if (loop || n < 3) { H.postDelayed(this, siren ? 1100 : 900); } else { stopRing(); } } catch (Throwable e) { } } };
                H.post(beepTask);
                H.postDelayed(new Runnable() { @Override public void run() { stopRing(); } }, 10L * 60000L);
                return;
            }
            Uri u = RingtoneManager.getDefaultUri("notif".equals(sp(c).getString("tone", "beep")) ? RingtoneManager.TYPE_NOTIFICATION : RingtoneManager.TYPE_ALARM);
            if (u == null) { u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION); }
            mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(sp(c).getBoolean("loud", true) ? AudioAttributes.USAGE_ALARM : AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            mp.setDataSource(c, u);
            mp.setLooping(sp(c).getBoolean("loop", true));
            mp.prepare(); mp.start();
            H.postDelayed(new Runnable() { @Override public void run() { stopRing(); } }, 10L * 60000L);
        } catch (Throwable e) { stopRing(); }
    }

    static synchronized void stopRing() {
        try { if (mp != null) { mp.stop(); mp.release(); } } catch (Throwable ignored) { }
        try { if (beepTask != null) { H.removeCallbacks(beepTask); } if (tg != null) { tg.stopTone(); tg.release(); } } catch (Throwable ignored) { }
        mp = null; tg = null; beepTask = null; ringKey = "";
    }

    /* the scanner calls this when a coin's move has faded, so the beep stops by itself */
    static void moveOver(String key) { if (key != null && key.equals(ringKey)) { stopRing(); } }

    @Override
    public void onReceive(Context c, Intent in) {
        if (in != null && STOP.equals(in.getAction())) {
            stopRing();
            try { String k = in.getStringExtra("k"); ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).cancel("pump545", 7000 + ((k == null ? "" : k).hashCode() & 0x3ff)); } catch (Throwable ignored) { }
        }
    }

    /* full-screen card over the lock screen */
    public static class PumpActivity extends Activity {
        @Override
        protected void onCreate(Bundle s) {
            super.onCreate(s);
            if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true); }
            else { getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON); }
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            final Intent in = getIntent(); final boolean up = in.getBooleanExtra("up", true); final String k = in.getStringExtra("k");
            String t = in.getStringExtra("t"), b = in.getStringExtra("b");
            float d = getResources().getDisplayMetrics().density;
            LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setGravity(Gravity.CENTER); root.setBackgroundColor(0xF0101418);
            int pad = (int) (22 * d); root.setPadding(pad, pad, pad, pad);
            LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(pad, pad, pad, pad);
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, up ? new int[]{0xFF14532D, 0xFF16A34A} : new int[]{0xFF7F1D1D, 0xFFDC2626});
            g.setCornerRadius(28 * d); g.setStroke((int) (2 * d), up ? 0xFF86EFAC : 0xFFFCA5A5); box.setBackground(g);
            TextView tag = tv(up ? "▲ PUMPING" : "▼ DUMPING", 15, true, up ? 0xFFBBF7D0 : 0xFFFECACA);
            TextView tt = tv(t == null ? "" : t, 26, true, Color.WHITE);
            TextView bb = tv(b == null ? "" : b, 16, false, 0xF2FFFFFF);
            box.addView(tag); box.addView(tt); box.addView(bb);
            LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setPadding(0, (int) (18 * d), 0, 0);
            TextView dis = btn("Dismiss", 0xFFFFFFFF, up ? 0xFF14532D : 0xFF7F1D1D, d), op = btn("Open chart", 0x33FFFFFF, Color.WHITE, d);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, (int) (54 * d), 1f); lp.setMarginEnd((int) (10 * d));
            row.addView(dis, lp); row.addView(op, new LinearLayout.LayoutParams(0, (int) (54 * d), 1f)); box.addView(row);
            root.addView(box, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            setContentView(root);
            dis.setOnClickListener(v -> { stopRing(); cancel(k); finish(); });
            op.setOnClickListener(v -> { stopRing(); cancel(k); Intent o = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("mw_t", in.getStringExtra("t")).putExtra("mw_b", in.getStringExtra("b")).putExtra("mw_k", in.getStringExtra("k")); startActivity(o); finish(); });
        }
        void cancel(String k) { try { ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).cancel("pump545", 7000 + ((k == null ? "" : k).hashCode() & 0x3ff)); } catch (Throwable ignored) { } }
        TextView tv(String s, int sz, boolean bold, int col) { TextView v = new TextView(this); v.setText(s); v.setTextSize(sz); v.setTextColor(col); if (bold) { v.setTypeface(Typeface.DEFAULT_BOLD); } v.setPadding(0, 6, 0, 6); return v; }
        TextView btn(String s, int bg, int fg, float d) { TextView v = tv(s, 17, true, fg); v.setGravity(Gravity.CENTER); GradientDrawable g = new GradientDrawable(); g.setColor(bg); g.setCornerRadius(18 * d); v.setBackground(g); return v; }
    }
}
