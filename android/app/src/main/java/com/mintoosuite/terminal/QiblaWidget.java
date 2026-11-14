package com.mintoosuite.terminal;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

/* build 525: 🕋 Qibla home-screen widget (bearing from North for the saved place). */
public class QiblaWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) { ToolWidget.drawAll(c, m, ids, "qibla"); }
}
