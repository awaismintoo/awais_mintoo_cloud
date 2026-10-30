package com.mintoosuite.terminal;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

/* BJ: small floating alert shown over other apps, only when the user allowed "Display over other apps" and the app is not on screen. */
public class Overlay {
    private static View cur;
    private static final Handler H = new Handler(Looper.getMainLooper());

    static void show(final Context c, final String title, final String body) {
        try {
            if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(c)) { return; }
            if (MainActivity.cur != null) { return; }
            H.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        final WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
                        if (cur != null) { try { wm.removeView(cur); } catch (Exception ignored) { } cur = null; }
                        final TextView t = new TextView(c);
                        t.setText((title == null ? "" : title) + "\n" + (body == null ? "" : body));
                        t.setTextColor(Color.WHITE);
                        t.setTextSize(14);
                        t.setPadding(36, 24, 36, 24);
                        GradientDrawable g = new GradientDrawable();
                        g.setColor(0xEE1C1C24);
                        g.setCornerRadius(36f);
                        t.setBackground(g);
                        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
                        WindowManager.LayoutParams p = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, type,
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT);
                        p.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                        p.y = 90;
                        t.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                try {
                                    Intent i = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                                    c.startActivity(i);
                                    wm.removeView(t);
                                    cur = null;
                                } catch (Exception ignored) { }
                            }
                        });
                        wm.addView(t, p);
                        cur = t;
                        H.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                try { if (cur == t) { wm.removeView(t); cur = null; } } catch (Exception ignored) { }
                            }
                        }, 9000L);
                    } catch (Exception ignored) { }
                }
            });
        } catch (Exception ignored) { }
    }
}
