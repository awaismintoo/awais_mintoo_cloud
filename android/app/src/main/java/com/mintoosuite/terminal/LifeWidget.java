package com.mintoosuite.terminal;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.widget.RemoteViews;

/* build 494: "My city" home-screen widget. Shows three lines the app saved: area weather, today's date with Hijri and holiday, and the next event. Tapping opens the app. It never trades and never reads keys. */
public class LifeWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int i = 0; i < ids.length; i++) { draw(c, m, ids[i]); }
    }

    static void draw(Context c, AppWidgetManager m, int id) {
        try {
            SharedPreferences sp = c.getSharedPreferences("mw_life", Context.MODE_PRIVATE);
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.mw_life_widget);
            v.setTextViewText(R.id.mw_l1, sp.getString("l1", "Open MintooSuite > City life to pick your area"));
            v.setTextViewText(R.id.mw_l2, sp.getString("l2", ""));
            v.setTextViewText(R.id.mw_l3, sp.getString("l3", ""));
            Intent in = new Intent(c, MainActivity.class);
            in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            int fl = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) { fl |= PendingIntent.FLAG_IMMUTABLE; }
            v.setOnClickPendingIntent(R.id.mw_life_root, PendingIntent.getActivity(c, 1, in, fl));
            m.updateAppWidget(id, v);
        } catch (Exception e) { }
    }

    static void save(Context c, String l1, String l2, String l3) {
        try {
            c.getSharedPreferences("mw_life", Context.MODE_PRIVATE).edit().putString("l1", cut(l1)).putString("l2", cut(l2)).putString("l3", cut(l3)).apply();
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            int[] ids = m.getAppWidgetIds(new ComponentName(c, LifeWidget.class));
            for (int i = 0; i < ids.length; i++) { draw(c, m, ids[i]); }
        } catch (Exception e) { }
    }

    static String pin(Context c) {
        try {
            if (Build.VERSION.SDK_INT < 26) { return "old"; }
            AppWidgetManager m = c.getSystemService(AppWidgetManager.class);
            if (m == null || !m.isRequestPinAppWidgetSupported()) { return "old"; }
            return m.requestPinAppWidget(new ComponentName(c, LifeWidget.class), null, null) ? "ok" : "no";
        } catch (Exception e) { return "no"; }
    }

    private static String cut(String s) { if (s == null) { return ""; } return s.length() > 140 ? s.substring(0, 140) : s; }
}
