package com.mintoosuite.terminal;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

/* build 535: Auto Bot home-screen widget (lines saved by the app; tap opens the window). */
public class BotWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) { ToolWidget.drawAll(c, m, ids, "bot"); }
}
