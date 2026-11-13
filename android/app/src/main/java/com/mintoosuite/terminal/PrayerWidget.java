package com.mintoosuite.terminal;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.util.Calendar;
import java.util.Locale;

/* build 518: 🕌 Namaz home-screen widget and Azan alarm.
   The app saves the place and the calculation settings; this class works out the prayer times on the phone (same maths as the app,
   no internet), shows today's times with the next prayer, wakes up at each prayer time to refresh itself and, when the user switched
   Azan on in the app, posts the Azan notification with the bundled public-domain (CC0) Azan sound. It never trades and never reads keys. */
public class PrayerWidget extends AppWidgetProvider {
    static final String TICK = "com.mintoosuite.terminal.PRAYER_TICK";
    static final String CH = "mw_azan_v1";
    static final String CH_SILENT = "mw_namaz_v1";
    static final String[] N = {"Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"};

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int i = 0; i < ids.length; i++) { draw(c, m, ids[i]); }
        schedule(c);
    }

    @Override
    public void onReceive(Context c, Intent in) {
        super.onReceive(c, in);
        try {
            String a = in == null ? null : in.getAction();
            if (TICK.equals(a)) {
                int which = in.getIntExtra("p", -1);
                long due = in.getLongExtra("at", 0L);
                if (which >= 0 && which != 1 && Math.abs(System.currentTimeMillis() - due) < 20L * 60L * 1000L) { azan(c, which); }
                refresh(c);
            } else if (Intent.ACTION_BOOT_COMPLETED.equals(a) || "android.intent.action.MY_PACKAGE_REPLACED".equals(a)
                    || Intent.ACTION_TIME_CHANGED.equals(a) || Intent.ACTION_TIMEZONE_CHANGED.equals(a) || Intent.ACTION_DATE_CHANGED.equals(a)) {
                refresh(c);
            }
        } catch (Exception e) { }
    }

    /* ---------- settings saved by the app ---------- */
    static JSONObject cfg(Context c) {
        try { return new JSONObject(c.getSharedPreferences("mw_prayer", Context.MODE_PRIVATE).getString("cfg", "{}")); } catch (Exception e) { return new JSONObject(); }
    }

    static void save(Context c, String json) {
        try {
            new JSONObject(json);
            c.getSharedPreferences("mw_prayer", Context.MODE_PRIVATE).edit().putString("cfg", json).apply();
            refresh(c);
        } catch (Exception e) { }
    }

    static void refresh(Context c) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            int[] ids = m.getAppWidgetIds(new ComponentName(c, PrayerWidget.class));
            for (int i = 0; i < ids.length; i++) { draw(c, m, ids[i]); }
        } catch (Exception e) { }
        schedule(c);
    }

    static String pin(Context c) {
        try {
            if (Build.VERSION.SDK_INT < 26) { return "old"; }
            AppWidgetManager m = c.getSystemService(AppWidgetManager.class);
            if (m == null || !m.isRequestPinAppWidgetSupported()) { return "old"; }
            return m.requestPinAppWidget(new ComponentName(c, PrayerWidget.class), null, null) ? "ok" : "no";
        } catch (Exception e) { return "no"; }
    }

    /* exact alarms are a special permission on Android 12+: tell the app whether we have it, and open the switch for the user */
    static String exactState(Context c) {
        try {
            if (Build.VERSION.SDK_INT < 31) { return "ok"; }
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            return am != null && am.canScheduleExactAlarms() ? "ok" : "no";
        } catch (Exception e) { return "no"; }
    }

    static void exactAsk(Context c) {
        try {
            if (Build.VERSION.SDK_INT < 31) { return; }
            Intent i = new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + c.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Exception e) { }
    }

    /* ---------- prayer time maths (hours, local clock) ---------- */
    static double dsin(double d) { return Math.sin(Math.toRadians(d)); }
    static double dcos(double d) { return Math.cos(Math.toRadians(d)); }
    static double dtan(double d) { return Math.tan(Math.toRadians(d)); }
    static double fix(double a, double b) { a = a - b * Math.floor(a / b); return a < 0 ? a + b : a; }

    static double julian(int y, int m, int d) {
        if (m <= 2) { y -= 1; m += 12; }
        double A = Math.floor(y / 100.0), B = 2 - A + Math.floor(A / 4.0);
        return Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1)) + d + B - 1524.5;
    }

    /* returns {decl, eqt} */
    static double[] sun(double jd) {
        double D = jd - 2451545.0, g = fix(357.529 + 0.98560028 * D, 360), q = fix(280.459 + 0.98564736 * D, 360);
        double L = fix(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g), 360), e = 23.439 - 0.00000036 * D;
        double RA = Math.toDegrees(Math.atan2(dcos(e) * dsin(L), dcos(L))) / 15.0;
        return new double[]{Math.toDegrees(Math.asin(dsin(e) * dsin(L))), q / 15.0 - fix(RA, 24)};
    }

    static double[] times(Calendar day, JSONObject o) {
        double la = o.optDouble("la", 33.6844), lo = o.optDouble("lo", 73.0479), tz = o.optDouble("tz", 5);
        double fa = o.optDouble("fa", 18), ia = o.optDouble("ia", 18), im = o.optDouble("im", 0), af = o.optDouble("af", 2);
        double jd = julian(day.get(Calendar.YEAR), day.get(Calendar.MONTH) + 1, day.get(Calendar.DAY_OF_MONTH)) - lo / (15.0 * 24.0);
        double fajr = ang(jd, la, fa, 5 / 24.0, true), rise = ang(jd, la, 0.833, 6 / 24.0, true), dh = mid(jd, 12 / 24.0);
        double[] s = sun(jd + 13 / 24.0);
        double asrA = -Math.toDegrees(Math.atan(1.0 / (af + dtan(Math.abs(la - s[0])))));
        double asr = ang(jd, la, asrA, 13 / 24.0, false), set = ang(jd, la, 0.833, 18 / 24.0, false);
        double isha = im > 0 ? set + im / 60.0 : ang(jd, la, ia, 18 / 24.0, false);
        double night = fix(rise - set, 24); if (night == 0) { night = 12; }
        if (Double.isNaN(fajr) || fix(rise - fajr, 24) > night / 2) { fajr = rise - night / 2; }
        if (Double.isNaN(isha) || fix(isha - set, 24) > night / 2) { isha = set + night / 2; }
        double off = tz - lo / 15.0;
        double[] t = {fajr, rise, dh, asr, set, isha};
        JSONObject adj = o.optJSONObject("adj");
        for (int i = 0; i < 6; i++) { t[i] = t[i] + off + ((adj == null ? 0 : adj.optDouble(N[i], 0)) + (i == 2 ? 1 : 0)) / 60.0; }
        return t;
    }

    static double mid(double jd, double t) { return fix(12 - sun(jd + t)[1], 24); }

    static double ang(double jd, double la, double a, double t, boolean ccw) {
        double D = sun(jd + t)[0], N0 = mid(jd, t), v = (-dsin(a) - dsin(D) * dsin(la)) / (dcos(D) * dcos(la));
        if (v > 1 || v < -1) { return Double.NaN; }
        double T = Math.toDegrees(Math.acos(v)) / 15.0;
        return N0 + (ccw ? -T : T);
    }

    static String hm(double h, boolean h24) {
        if (Double.isNaN(h)) { return "--:--"; }
        h = fix(h + 0.5 / 60.0, 24); int H = (int) Math.floor(h), M = (int) Math.floor((h - H) * 60);
        if (h24) { return String.format(Locale.US, "%02d:%02d", H, M); }
        return String.format(Locale.US, "%d:%02d %s", (H % 12) == 0 ? 12 : H % 12, M, H < 12 ? "am" : "pm");
    }

    /* the phone clock may be in a different zone than the chosen place: convert "place hours" to a real moment */
    static long at(Calendar day, double h, double tz) {
        Calendar u = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        u.clear(); u.set(day.get(Calendar.YEAR), day.get(Calendar.MONTH), day.get(Calendar.DAY_OF_MONTH), 0, 0, 0);
        return u.getTimeInMillis() + (long) ((h - tz) * 3600000.0);
    }

    static Calendar placeDay(long now, double tz) {
        Calendar u = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        u.setTimeInMillis(now + (long) (tz * 3600000.0));
        Calendar d = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        d.clear(); d.set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH), 12, 0, 0);
        return d;
    }

    /* ---------- drawing ---------- */
    static void draw(Context c, AppWidgetManager m, int id) {
        try {
            JSONObject o = cfg(c);
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.mw_prayer_widget);
            if (!o.has("la")) {
                v.setTextViewText(R.id.mw_p_title, "🕌 Namaz");
                v.setTextViewText(R.id.mw_p_next, "Open MintooSuite › 🕌 Namaz timings");
                v.setTextViewText(R.id.mw_p_row1, "and choose your place once.");
                v.setTextViewText(R.id.mw_p_row2, "");
            } else {
                double tz = o.optDouble("tz", 5); boolean h24 = o.optInt("h24", 0) == 1;
                long now = System.currentTimeMillis(); Calendar d = placeDay(now, tz); double[] t = times(d, o);
                int nx = -1; long nxAt = 0;
                for (int i = 0; i < 6; i++) { if (i == 1) { continue; } long a = at(d, t[i], tz); if (a > now) { nx = i; nxAt = a; break; } }
                double nxH;
                if (nx < 0) { Calendar d2 = (Calendar) d.clone(); d2.add(Calendar.DAY_OF_MONTH, 1); double[] t2 = times(d2, o); nx = 0; nxH = t2[0]; nxAt = at(d2, t2[0], tz); } else { nxH = t[nx]; }
                long mins = Math.max(0, (nxAt - now) / 60000L);
                String left = mins >= 60 ? (mins / 60) + " h " + (mins % 60) + " min" : mins + " min";
                v.setTextViewText(R.id.mw_p_title, "🕌 Namaz · " + o.optString("n", "My place"));
                v.setTextViewText(R.id.mw_p_next, "Next: " + N[nx] + " " + hm(nxH, h24) + "  (in " + left + ")");
                v.setTextViewText(R.id.mw_p_row1, "Fajr " + hm(t[0], h24) + " · Sunrise " + hm(t[1], h24) + " · Dhuhr " + hm(t[2], h24));
                v.setTextViewText(R.id.mw_p_row2, "Asr " + hm(t[3], h24) + " · Maghrib " + hm(t[4], h24) + " · Isha " + hm(t[5], h24));
            }
            Intent in = new Intent(c, MainActivity.class);
            in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            in.putExtra("mw_t", "🕌 Namaz timings").putExtra("mw_b", "");
            int fl = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) { fl |= PendingIntent.FLAG_IMMUTABLE; }
            v.setOnClickPendingIntent(R.id.mw_prayer_root, PendingIntent.getActivity(c, 7, in, fl));
            m.updateAppWidget(id, v);
        } catch (Exception e) { }
    }

    /* ---------- next wake-up: at the next prayer (Azan) and at least every 30 minutes while a widget exists ---------- */
    @android.annotation.SuppressLint({"ScheduleExactAlarm", "MissingPermission"})
    static void schedule(Context c) {
        try {
            JSONObject o = cfg(c);
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) { return; }
            Intent in = new Intent(c, PrayerWidget.class).setAction(TICK);
            int fl = PendingIntent.FLAG_UPDATE_CURRENT; if (Build.VERSION.SDK_INT >= 23) { fl |= PendingIntent.FLAG_IMMUTABLE; }
            boolean widgets = AppWidgetManager.getInstance(c).getAppWidgetIds(new ComponentName(c, PrayerWidget.class)).length > 0;
            boolean az = o.optInt("az", 0) == 1;
            if (!o.has("la") || (!widgets && !az)) { am.cancel(PendingIntent.getBroadcast(c, 71, in, fl)); return; }
            double tz = o.optDouble("tz", 5); long now = System.currentTimeMillis(); Calendar d = placeDay(now, tz); double[] t = times(d, o);
            int nx = -1; long nxAt = 0;
            for (int i = 0; i < 6; i++) { if (i == 1) { continue; } long a = at(d, t[i], tz); if (a > now + 5000) { nx = i; nxAt = a; break; } }
            if (nx < 0) { Calendar d2 = (Calendar) d.clone(); d2.add(Calendar.DAY_OF_MONTH, 1); nx = 0; nxAt = at(d2, times(d2, o)[0], tz); }
            long when = nxAt; int which = nx;
            if (widgets && nxAt - now > 30L * 60000L) { when = now + 30L * 60000L; which = -1; }
            in.putExtra("p", which).putExtra("at", when);
            PendingIntent pi = PendingIntent.getBroadcast(c, 71, in, fl);
            boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
            if (which >= 0 && exact && Build.VERSION.SDK_INT >= 23) { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi); }
            else if (Build.VERSION.SDK_INT >= 23) { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi); }
            else { am.set(AlarmManager.RTC_WAKEUP, when, pi); }
        } catch (Exception e) { }
    }

    /* ---------- Azan notification ---------- */
    @android.annotation.SuppressLint({"MissingPermission", "NotificationPermission"})
    static void azan(Context c, int which) {
        try {
            JSONObject o = cfg(c);
            if (o.optInt("az", 0) != 1) { return; }
            if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) { return; }
            boolean sound = o.optInt("snd", 1) == 1;
            int raw = c.getResources().getIdentifier("mw_azan", "raw", c.getPackageName());
            String ch = sound && raw != 0 ? CH : CH_SILENT;
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26) {
                if (ch.equals(CH)) {
                    NotificationChannel nc = new NotificationChannel(CH, "Azan (prayer time)", NotificationManager.IMPORTANCE_HIGH);
                    nc.setSound(Uri.parse("android.resource://" + c.getPackageName() + "/" + raw), new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
                    nm.createNotificationChannel(nc);
                } else {
                    nm.createNotificationChannel(new NotificationChannel(CH_SILENT, "Namaz reminder", NotificationManager.IMPORTANCE_HIGH));
                }
            }
            Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            open.putExtra("mw_t", "🕌 Namaz timings").putExtra("mw_b", "");
            PendingIntent pi = PendingIntent.getActivity(c, 72, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            String title = "🕌 " + N[which] + " · Azan";
            String body = "It is time for " + N[which] + " in " + o.optString("n", "your place") + ".";
            android.app.Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new android.app.Notification.Builder(c, ch) : new android.app.Notification.Builder(c);
            b.setSmallIcon(R.drawable.ic_stat).setContentTitle(title).setContentText(body).setAutoCancel(true).setContentIntent(pi).setWhen(System.currentTimeMillis()).setShowWhen(true);
            if (Build.VERSION.SDK_INT < 26 && sound && raw != 0) { b.setSound(Uri.parse("android.resource://" + c.getPackageName() + "/" + raw)); }
            nm.notify(900 + which, b.build());
        } catch (Exception e) { }
    }
}
