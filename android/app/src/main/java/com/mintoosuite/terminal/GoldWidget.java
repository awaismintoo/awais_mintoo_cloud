package com.mintoosuite.terminal;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

/* build 535: Gold & dollar home-screen widget (lines saved by the app; tap opens the window). */
public class GoldWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) { ToolWidget.drawAll(c, m, ids, "gold"); }
}
