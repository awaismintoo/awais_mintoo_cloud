package com.mintoosuite.terminal;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

/* build 538: calendar events and reminders ring at the exact minute even when MintooSuite is closed.
   The app sends the next ring times (list of {id, at, t, b, tone}); this class keeps one exact alarm for the earliest,
   shows the notice with the chosen tone (each tone is its own Android channel, so the user can also change it in phone settings),
   offers "Snooze 10 min" and "Done", and sets itself again after a phone restart (BootReceiver). */
public class RemAlarm extends BroadcastReceiver {
    static final String P = "mw_alarm538", FIRE = "com.mintoosuite.terminal.REM_FIRE", SNOOZE = "com.mintoosuite.terminal.REM_SNOOZE", DONE = "com.mintoosuite.terminal.REM_DONE";

    static SharedPreferences sp(Context c) { return c.getSharedPreferences(P, Context.MODE_PRIVATE); }

    static void save(Context c, String json) {
        try { new JSONArray(json == null ? "[]" : json); } catch (Exception e) { return; }
        sp(c).edit().putString("list", json).apply();
        schedule(c);
    }

    static JSONArray list(Context c) { try { return new JSONArray(sp(c).getString("list", "[]")); } catch (Exception e) { return new JSONArray(); } }

    static int fl() { int f = PendingIntent.FLAG_UPDATE_CURRENT; if (Build.VERSION.SDK_INT >= 23) { f |= PendingIntent.FLAG_IMMUTABLE; } return f; }

    @android.annotation.SuppressLint({"ScheduleExactAlarm", "MissingPermission"})
    static void schedule(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE); if (am == null) { return; }
            Intent in = new Intent(c, RemAlarm.class).setAction(FIRE);
            PendingIntent pi = PendingIntent.getBroadcast(c, 538, in, fl());
            JSONArray a = list(c); long now = System.currentTimeMillis(), next = 0; String fired = sp(c).getString("fired", "");
            for (int i = 0; i < a.length(); i++) { JSONObject o = a.optJSONObject(i); if (o == null) { continue; } long at = o.optLong("at", 0); if (at > now - 60000 && !fired.contains("|" + o.optString("id") + "|") && (next == 0 || at < next)) { next = at; } }
            long sz = sp(c).getLong("snz_at", 0); if (sz > now && (next == 0 || sz < next)) { next = sz; }
            if (next == 0) { am.cancel(pi); return; }
            if (next < now + 1000) { next = now + 1000; }
            boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
            if (exact && Build.VERSION.SDK_INT >= 23) { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi); }
            else if (Build.VERSION.SDK_INT >= 23) { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi); }
            else { am.set(AlarmManager.RTC_WAKEUP, next, pi); }
        } catch (Exception e) { }
    }

    @Override
    public void onReceive(Context c, Intent in) {
        try {
            String act = in.getAction() == null ? "" : in.getAction();
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (SNOOZE.equals(act)) {
                int nid = in.getIntExtra("nid", 0); if (nm != null) { nm.cancel(nid); }
                sp(c).edit().putLong("snz_at", System.currentTimeMillis() + 10L * 60000L).putString("snz_t", in.getStringExtra("t")).putString("snz_b", in.getStringExtra("b")).putString("snz_tone", in.getStringExtra("tone")).apply();
                schedule(c); return;
            }
            if (DONE.equals(act)) { int nid = in.getIntExtra("nid", 0); if (nm != null) { nm.cancel(nid); } return; }
            long now = System.currentTimeMillis();
            SharedPreferences s = sp(c); String fired = s.getString("fired", "");
            long sz = s.getLong("snz_at", 0);
            if (sz > 0 && sz <= now + 30000) { show(c, "snz" + sz, "⏰ " + s.getString("snz_t", "Reminder"), "Snoozed · " + s.getString("snz_b", ""), s.getString("snz_tone", "alarm")); s.edit().remove("snz_at").apply(); }
            JSONArray a = list(c); StringBuilder nf = new StringBuilder(fired.length() > 6000 ? fired.substring(fired.length() - 4000) : fired);
            if (nf.length() == 0) { nf.append("|"); }
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i); if (o == null) { continue; }
                long at = o.optLong("at", 0); String id = o.optString("id");
                if (at <= now + 30000 && at > now - 15L * 60000L && !fired.contains("|" + id + "|")) { show(c, id, o.optString("t", "Reminder"), o.optString("b", ""), o.optString("tone", "notif")); nf.append(id).append("|"); }
            }
            s.edit().putString("fired", nf.toString()).apply();
            schedule(c);
        } catch (Exception e) { }
    }

    static String channel(Context c, String tone) {
        String ch = "mw_rem_" + tone + "_v1";
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(ch) == null) {
                String name = "alarm".equals(tone) ? "Calendar reminders · alarm tone" : "ring".equals(tone) ? "Calendar reminders · ringtone" : "azan".equals(tone) ? "Calendar reminders · Azan" : "vib".equals(tone) ? "Calendar reminders · vibrate only" : "silent".equals(tone) ? "Calendar reminders · silent" : "Calendar reminders · notification sound";
                NotificationChannel nc = new NotificationChannel(ch, name, "silent".equals(tone) ? NotificationManager.IMPORTANCE_DEFAULT : NotificationManager.IMPORTANCE_HIGH);
                Uri u = null; int usage = AudioAttributes.USAGE_NOTIFICATION_EVENT;
                if ("alarm".equals(tone)) { u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM); usage = AudioAttributes.USAGE_ALARM; }
                else if ("ring".equals(tone)) { u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE); usage = AudioAttributes.USAGE_NOTIFICATION_RINGTONE; }
                else if ("azan".equals(tone)) { int raw = c.getResources().getIdentifier("mw_azan", "raw", c.getPackageName()); if (raw != 0) { u = Uri.parse("android.resource://" + c.getPackageName() + "/" + raw); usage = AudioAttributes.USAGE_ALARM; } }
                else if ("notif".equals(tone)) { u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION); }
                if (u != null) { nc.setSound(u, new AudioAttributes.Builder().setUsage(usage).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()); } else { nc.setSound(null, null); }
                nc.enableVibration(!"silent".equals(tone)); if (!"silent".equals(tone)) { nc.setVibrationPattern(new long[]{0, 500, 250, 500, 250, 800}); }
                nm.createNotificationChannel(nc);
            }
        }
        return ch;
    }

    @android.annotation.SuppressLint({"MissingPermission", "NotificationPermission"})
    static void show(Context c, String id, String title, String body, String tone) {
        try {
            if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) { return; }
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE); if (nm == null) { return; }
            String ch = channel(c, tone == null ? "notif" : tone); int nid = 5380 + Math.abs(id.hashCode() % 500);
            Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("mw_t", "📅 Calendar").putExtra("mw_b", "");
            PendingIntent pi = PendingIntent.getActivity(c, nid, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Intent sn = new Intent(c, RemAlarm.class).setAction(SNOOZE).putExtra("nid", nid).putExtra("t", title).putExtra("b", body).putExtra("tone", tone);
            Intent dn = new Intent(c, RemAlarm.class).setAction(DONE).putExtra("nid", nid);
            android.app.Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new android.app.Notification.Builder(c, ch) : new android.app.Notification.Builder(c);
            b.setSmallIcon(R.drawable.ic_stat).setContentTitle(title).setContentText(body).setAutoCancel(true).setContentIntent(pi).setWhen(System.currentTimeMillis()).setShowWhen(true)
                    .setCategory(android.app.Notification.CATEGORY_REMINDER)
                    .addAction(0, "Snooze 10 min", PendingIntent.getBroadcast(c, nid + 1, sn, fl()))
                    .addAction(0, "Done", PendingIntent.getBroadcast(c, nid + 2, dn, fl()));
            if (Build.VERSION.SDK_INT < 26 && !"silent".equals(tone) && !"vib".equals(tone)) { b.setSound(RingtoneManager.getDefaultUri("alarm".equals(tone) ? RingtoneManager.TYPE_ALARM : RingtoneManager.TYPE_NOTIFICATION)); }
            android.app.Notification n = b.build();
            if ("alarm".equals(tone) || "azan".equals(tone)) { n.flags |= android.app.Notification.FLAG_INSISTENT; }
            nm.notify(nid, n);
        } catch (Exception e) { }
    }
}
