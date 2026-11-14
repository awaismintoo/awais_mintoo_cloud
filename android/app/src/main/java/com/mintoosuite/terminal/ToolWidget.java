package com.mintoosuite.terminal;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.widget.RemoteViews;

/* build 525: shared drawing for the 🕋 Qibla and 📍 Location & altitude home-screen widgets.
   The app saves three short lines (worked out on the phone, no internet needed); tapping opens the live compass window. */
final class ToolWidget {
    private ToolWidget() { }

    static String pref(String which) { return "place".equals(which) ? "mw_tw_place" : "mw_tw_qibla"; }

    static Class<?> cls(String which) { return "place".equals(which) ? PlaceWidget.class : QiblaWidget.class; }

    static void drawAll(Context c, AppWidgetManager m, int[] ids, String which) {
        for (int i = 0; i < ids.length; i++) { draw(c, m, ids[i], which); }
    }

    static void draw(Context c, AppWidgetManager m, int id, String which) {
        try {
            SharedPreferences sp = c.getSharedPreferences(pref(which), Context.MODE_PRIVATE);
            boolean place = "place".equals(which);
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.mw_tool_widget);
            v.setTextViewText(R.id.mw_t0, place ? "MintooSuite · My location & altitude" : "MintooSuite · Qibla");
            v.setTextViewText(R.id.mw_t1, sp.getString("l1", place ? "📍 Open MintooSuite › Compass & Qibla" : "🕋 Open MintooSuite › Compass & Qibla"));
            v.setTextViewText(R.id.mw_t2, sp.getString("l2", "to set your place"));
            v.setTextViewText(R.id.mw_t3, sp.getString("l3", ""));
            Intent in = new Intent(c, MainActivity.class);
            in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            in.putExtra("mw_t", "__open__compass"); in.putExtra("mw_b", "");
            int fl = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) { fl |= PendingIntent.FLAG_IMMUTABLE; }
            v.setOnClickPendingIntent(R.id.mw_tool_root, PendingIntent.getActivity(c, place ? 3 : 2, in, fl));
            m.updateAppWidget(id, v);
        } catch (Exception e) { }
    }

    static void save(Context c, String which, String l1, String l2, String l3) {
        try {
            c.getSharedPreferences(pref(which), Context.MODE_PRIVATE).edit().putString("l1", cut(l1)).putString("l2", cut(l2)).putString("l3", cut(l3)).apply();
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            int[] ids = m.getAppWidgetIds(new ComponentName(c, cls(which)));
            drawAll(c, m, ids, which);
        } catch (Exception e) { }
    }

    static String pin(Context c, String which) {
        try {
            if (Build.VERSION.SDK_INT < 26) { return "old"; }
            AppWidgetManager m = c.getSystemService(AppWidgetManager.class);
            if (m == null || !m.isRequestPinAppWidgetSupported()) { return "old"; }
            return m.requestPinAppWidget(new ComponentName(c, cls(which)), null, null) ? "ok" : "no";
        } catch (Exception e) { return "no"; }
    }

    private static String cut(String s) { if (s == null) { return ""; } return s.length() > 140 ? s.substring(0, 140) : s; }
}
