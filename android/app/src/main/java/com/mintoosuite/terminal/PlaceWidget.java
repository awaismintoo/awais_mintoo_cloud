package com.mintoosuite.terminal;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

/* build 525: 📍 Location & altitude home-screen widget (last GPS fix the app saved). */
public class PlaceWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) { ToolWidget.drawAll(c, m, ids, "place"); }
}
