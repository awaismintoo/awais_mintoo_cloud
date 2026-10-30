package com.mintoosuite.terminal;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

/* mw-v108: foreground service. Keeps the app process (and its live prices, alerts and bot) alive with a permanent notification.
   Swiping the notification away re-posts it. The only ways to stop it are the Stop button, the switch inside the app, or Force stop in Android settings. */
public class BotService extends Service {
    static final String CH = "mintoo_bg";
    static final int NID = 7001;
    static final String A_REPOST = "com.mintoosuite.terminal.REPOST";
    static final String A_PAUSE = "com.mintoosuite.terminal.PAUSE";
    static final String A_STOP = "com.mintoosuite.terminal.STOP";
    private PowerManager.WakeLock wl;
    private final android.os.Handler beat = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable beatRun = new Runnable() {
        @Override
        public void run() {
            try { WebHolder.keepAlive(); } catch (Exception ignored) { }
            try { if (wl != null && wl.isHeld()) { wl.acquire(10L * 60L * 60L * 1000L); } } catch (Exception ignored) { }
            beat.postDelayed(this, 45000L);
        }
    };

    static SharedPreferences sp(Context c) { return c.getApplicationContext().getSharedPreferences("mw_svc", Context.MODE_PRIVATE); }

    static boolean isOn(Context c) { return sp(c).getBoolean("on", false); }

    static void start(Context c) {
        sp(c).edit().putBoolean("on", true).apply();
        Intent i = new Intent(c, BotService.class);
        if (Build.VERSION.SDK_INT >= 26) { c.startForegroundService(i); } else { c.startService(i); }
    }

    static void stop(Context c) {
        sp(c).edit().putBoolean("on", false).apply();
        c.stopService(new Intent(c, BotService.class));
    }

    static void update(Context c, String title, String text, boolean paused, boolean wake) {
        sp(c).edit().putString("title", title).putString("text", text).putBoolean("paused", paused).putBoolean("wake", wake).apply();
        if (isOn(c)) {
            try {
                NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
                nm.notify(NID, build(c));
            } catch (Exception ignored) { }
        }
    }

    static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel ch = new NotificationChannel(CH, "Background run (always on)", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Shows that MintooSuite is running in the background: live prices, alerts and the bot.");
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
    }

    static Notification build(Context c) {
        SharedPreferences p = sp(c);
        String title = p.getString("title", "MintooSuite is running");
        String text = p.getString("text", "Live prices and alerts are active");
        boolean paused = p.getBoolean("paused", false);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CH) : new Notification.Builder(c);
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPi = PendingIntent.getActivity(c, 10, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent repostPi = PendingIntent.getService(c, 11, new Intent(c, BotService.class).setAction(A_REPOST), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent pausePi = PendingIntent.getService(c, 12, new Intent(c, BotService.class).setAction(A_PAUSE), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stopPi = PendingIntent.getService(c, 13, new Intent(c, BotService.class).setAction(A_STOP), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        b.setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setContentIntent(openPi)
                .setDeleteIntent(repostPi)
                .addAction(R.drawable.ic_stat, paused ? "Resume bot" : "Pause bot", pausePi)
                .addAction(R.drawable.ic_stat, "Stop bots", stopPi);
        if (Build.VERSION.SDK_INT < 26) { b.setPriority(Notification.PRIORITY_LOW); }
        return b.build();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ensureChannel(this);
        MainActivity.alertChannel(this);
        try { WebHolder.get(this); } catch (Exception ignored) { }
    }

    private void show() {
        Notification n = build(this);
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NID, n);
        }
    }

    private void wake(boolean on) {
        try {
            if (on) {
                if (wl == null) {
                    PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                    wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MintooSuite:bot");
                    wl.setReferenceCounted(false);
                }
                wl.acquire(10L * 60L * 60L * 1000L);
            } else if (wl != null && wl.isHeld()) {
                wl.release();
            }
        } catch (Exception ignored) { }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String a = intent == null ? null : intent.getAction();
        if (A_STOP.equals(a)) {
            sp(this).edit().putBoolean("on", false).apply();
            WebHolder.js("window.__mwSvcCmd&&window.__mwSvcCmd('svcoff');");
            wake(false);
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (A_PAUSE.equals(a)) {
            boolean now = !sp(this).getBoolean("paused", false);
            sp(this).edit().putBoolean("paused", now).apply();
            WebHolder.js("window.__mwSvcCmd&&window.__mwSvcCmd('" + (now ? "pause" : "resume") + "');");
        }
        show();
        beat.removeCallbacks(beatRun); beat.post(beatRun);
        wake(sp(this).getBoolean("wake", false));
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        try { show(); } catch (Exception ignored) { }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        beat.removeCallbacks(beatRun);
        wake(false);
        if (!isOn(this)) {
            WebHolder.js("window.__mwSvcCmd&&window.__mwSvcCmd('svcoff');");
            if (MainActivity.cur == null) { WebHolder.destroy(); }
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
