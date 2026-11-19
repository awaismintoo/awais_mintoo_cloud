package com.mintoosuite.terminal;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import androidx.core.app.NotificationCompat;

/* Freeze guard (build 356). The page runs on the Android main thread, so a watchdog that also runs there cannot help when the page is stuck.
   This guard runs on its own thread: if the main thread does not answer for 8 s (was 20 s before build 426) while the app is on screen, it posts a notification,
   schedules a re-launch and ends the process (the foreground bot service restarts by itself). At most 2 restarts in 10 minutes. */
final class Guard {
    private static volatile long beat = SystemClock.uptimeMillis(); /* build 533: uptime does not count deep sleep, so a locked phone never looks frozen */
    private static volatile boolean pending = false;
    private static boolean on = false;
    private static boolean wasFg = false;
    private static long grace = 0;
    private static int misses = 0;

    static synchronized void start(Context c) {
        if (on) { return; }
        on = true;
        final Context app = c.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        final Runnable ping = new Runnable() { @Override public void run() { beat = SystemClock.uptimeMillis(); pending = false; } };
        final PowerManager pm = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try { Thread.sleep(3000); } catch (Exception e) { return; }
                    try {
                        long now = SystemClock.uptimeMillis();
                        boolean awake = pm == null || pm.isInteractive();
                        if (!WebHolder.isFg() || !awake) { beat = now; wasFg = false; continue; }
                        /* build 533: after unlocking or coming back, the page needs a moment to catch up; that is not a freeze */
                        if (!wasFg) { wasFg = true; grace = now + 20000; beat = now; }
                        if (now < grace) { beat = Math.max(beat, now - 3000); }
                        else if (now - beat > 15000) { misses++; beat = now; if (misses >= 2) { misses = 0; recover(app); } }
                        else if (now - beat < 6000) { misses = 0; }
                        if (!pending) { pending = true; main.post(ping); }
                    } catch (Throwable ignored) { }
                }
            }
        }, "mw-guard");
        t.setDaemon(true);
        t.start();
    }

    static void recover(Context app) { recover(app, false); }

    static void recover(Context app, boolean force) {
        try {
            SharedPreferences sp = app.getSharedPreferences("mw_guard", Context.MODE_PRIVATE);
            long now = System.currentTimeMillis();
            long t1 = sp.getLong("t1", 0), t2 = sp.getLong("t2", 0);
            if (!force && now - t2 < 600000 && now - t1 < 600000 && t1 > 0 && t2 > 0) { return; }
            sp.edit().putLong("t1", t2).putLong("t2", now).putInt("n", sp.getInt("n", 0) + 1).commit();
            try {
                if (Build.VERSION.SDK_INT < 33 || app.checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED) {
                    MainActivity.alertChannel(app);
                    Intent open = new Intent(app, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    PendingIntent pi = PendingIntent.getActivity(app, 77, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                    NotificationCompat.Builder b = new NotificationCompat.Builder(app, MainActivity.CHANNEL)
                            .setSmallIcon(R.drawable.ic_stat)
                            .setContentTitle("MintooSuite restarted itself")
                            .setContentText("The app froze, so it was restarted. Tap to open it in safe mode.")
                            .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).setGroup("mw_alerts").setContentIntent(pi);
                    ((NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE)).notify(777, b.build());
                }
            } catch (Throwable ignored) { }
            try {
                Intent open2 = new Intent(app, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                PendingIntent pi2 = PendingIntent.getActivity(app, 78, open2, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_CANCEL_CURRENT);
                ((AlarmManager) app.getSystemService(Context.ALARM_SERVICE)).set(AlarmManager.RTC, now + 1500, pi2);
            } catch (Throwable ignored) { }
            Process.killProcess(Process.myPid());
        } catch (Throwable ignored) { }
    }

    /* how many guard restarts in the last 15 minutes (read by the page to start in safe mode) */
    static int recent(Context c) {
        try {
            SharedPreferences sp = c.getSharedPreferences("mw_guard", Context.MODE_PRIVATE);
            long now = System.currentTimeMillis();
            int n = 0;
            if (now - sp.getLong("t1", 0) < 900000 && sp.getLong("t1", 0) > 0) { n++; }
            if (now - sp.getLong("t2", 0) < 900000 && sp.getLong("t2", 0) > 0) { n++; }
            return n;
        } catch (Throwable e) { return 0; }
    }
}
