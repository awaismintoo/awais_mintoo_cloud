package com.mintoosuite.terminal;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.RemoteViews;

/* Home-screen widget: shows the last Bitcoin / Ethereum line the app saved. Tapping opens the app. It never trades and never reads keys. */
public class MwWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int i = 0; i < ids.length; i++) { draw(c, m, ids[i]); }
    }

    static void draw(Context c, AppWidgetManager m, int id) {
        try {
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.mw_widget);
            String t = c.getSharedPreferences("mw_widget", Context.MODE_PRIVATE).getString("text", "Open MintooSuite to load prices");
            v.setTextViewText(R.id.mw_w_text, t);
            Intent in = new Intent(c, MainActivity.class);
            in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            int fl = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) { fl |= PendingIntent.FLAG_IMMUTABLE; }
            v.setOnClickPendingIntent(R.id.mw_w_root, PendingIntent.getActivity(c, 0, in, fl));
            m.updateAppWidget(id, v);
        } catch (Exception e) { }
    }

    static void refresh(Context c) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            int[] ids = m.getAppWidgetIds(new ComponentName(c, MwWidget.class));
            for (int i = 0; i < ids.length; i++) { draw(c, m, ids[i]); }
        } catch (Exception e) { }
    }
}
