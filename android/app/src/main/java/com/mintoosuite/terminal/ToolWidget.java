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

    static String pref(String which) { return "place".equals(which) ? "mw_tw_place" : "qibla".equals(which) ? "mw_tw_qibla" : "mw_tw_" + which; }

    static Class<?> cls(String which) {
        if ("place".equals(which)) { return PlaceWidget.class; }
        if ("gold".equals(which)) { return GoldWidget.class; }
        if ("bot".equals(which)) { return BotWidget.class; }
        if ("remind".equals(which)) { return RemindWidget.class; }
        return QiblaWidget.class;
    }
    /* build 535: title, first line before the app has saved anything, and the window a tap opens */
    static String title(String w) { return "place".equals(w) ? "📍 My location & altitude" : "gold".equals(w) ? "🥇 Gold, dollar & Bitcoin" : "bot".equals(w) ? "🤖 MSAATB · Command centre" : "remind".equals(w) ? "⏰ Bills & reminders" : "🕋 Qibla"; }
    /* build 544: colour card per widget and three action buttons (label, window to open) */
    static int bg(String w) { return "bot".equals(w) ? R.drawable.mw_wbg_bot : "gold".equals(w) ? R.drawable.mw_wbg_gold : "remind".equals(w) ? R.drawable.mw_wbg_remind : "place".equals(w) ? R.drawable.mw_wbg_place : R.drawable.mw_wbg_qibla; }
    static String[][] acts(String w) {
        if ("bot".equals(w)) { return new String[][] { { "📊 Centre", "__open__autobot" }, { "📈 Trades", "__open__abpos" }, { "⛔ Kill switch", "__open__abkill" } }; }
        if ("gold".equals(w)) { return new String[][] { { "🥇 Gold", "__open__gold" }, { "📊 Compare", "__open__compare" }, { "💡 Ideas", "__open__ideas" } }; }
        if ("remind".equals(w)) { return new String[][] { { "🧾 Bills", "__open__remind" }, { "📅 Calendar", "__open__hcal" }, { "🕌 Prayer", "__open__prayer" } }; }
        if ("place".equals(w)) { return new String[][] { { "🧭 Compass", "__open__compass" }, { "🗺️ Map", "__open__traffic" }, { "⛅ Weather", "__open__weather" } }; }
        return new String[][] { { "🧭 Compass", "__open__compass" }, { "🕌 Prayer", "__open__prayer" }, { "📅 Calendar", "__open__hcal" } };
    }
    static String first(String w) { return "place".equals(w) || "qibla".equals(w) ? ("place".equals(w) ? "📍 Open MintooSuite › Compass & Qibla" : "🕋 Open MintooSuite › Compass & Qibla") : "Open MintooSuite once to fill this widget"; }
    static String target(String w) { return "gold".equals(w) ? "__open__gold" : "bot".equals(w) ? "__open__autobot" : "remind".equals(w) ? "__open__remind" : "__open__compass"; }
    static int code(String w) { return "place".equals(w) ? 3 : "gold".equals(w) ? 4 : "bot".equals(w) ? 5 : "remind".equals(w) ? 6 : 2; }

    static void drawAll(Context c, AppWidgetManager m, int[] ids, String which) {
        for (int i = 0; i < ids.length; i++) { draw(c, m, ids[i], which); }
    }

    static void draw(Context c, AppWidgetManager m, int id, String which) {
        try {
            SharedPreferences sp = c.getSharedPreferences(pref(which), Context.MODE_PRIVATE);
            boolean place = "place".equals(which);
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.mw_tool_widget);
            v.setTextViewText(R.id.mw_t0, title(which));
            v.setTextViewText(R.id.mw_t1, sp.getString("l1", first(which)));
            v.setTextViewText(R.id.mw_t2, sp.getString("l2", place || "qibla".equals(which) ? "to set your place" : ""));
            v.setTextViewText(R.id.mw_t3, sp.getString("l3", ""));
            v.setInt(R.id.mw_tool_root, "setBackgroundResource", bg(which));
            long at = sp.getLong("at", 0);
            v.setTextViewText(R.id.mw_tt, at > 0 ? new java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(new java.util.Date(at)) : "");
            String[][] A = acts(which);
            int[] ids = { R.id.mw_a1, R.id.mw_a2, R.id.mw_a3 };
            for (int k = 0; k < 3; k++) {
                v.setTextViewText(ids[k], A[k][0]);
                Intent ai = new Intent(c, MainActivity.class);
                ai.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                ai.setAction("mw_w_" + which + "_" + k);
                ai.putExtra("mw_t", A[k][1]); ai.putExtra("mw_b", "");
                int fl2 = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= 23) { fl2 |= PendingIntent.FLAG_IMMUTABLE; }
                v.setOnClickPendingIntent(ids[k], PendingIntent.getActivity(c, 100 + code(which) * 10 + k, ai, fl2));
            }
            Intent in = new Intent(c, MainActivity.class);
            in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            in.putExtra("mw_t", target(which)); in.putExtra("mw_b", "");
            int fl = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) { fl |= PendingIntent.FLAG_IMMUTABLE; }
            v.setOnClickPendingIntent(R.id.mw_tool_root, PendingIntent.getActivity(c, code(which), in, fl));
            m.updateAppWidget(id, v);
        } catch (Exception e) { }
    }

    static void save(Context c, String which, String l1, String l2, String l3) {
        try {
            c.getSharedPreferences(pref(which), Context.MODE_PRIVATE).edit().putString("l1", cut(l1)).putString("l2", cut(l2)).putString("l3", cut(l3)).putLong("at", System.currentTimeMillis()).apply();
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
