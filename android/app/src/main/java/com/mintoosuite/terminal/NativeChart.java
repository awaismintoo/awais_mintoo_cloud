package com.mintoosuite.terminal;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.DashPathEffect;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.FrameLayout;

import org.json.JSONArray;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/* mw-v108 experimental: a chart drawn natively (candles, drag, pinch zoom, long-press crosshair). The web page only gives it data and a screen position. */
final class NativeChart extends View {
    private static NativeChart inst;
    private static int lx = -1, ly = -1, lw = -1, lh = -1;

    private double[][] k = new double[0][];
    private String label = "";
    private final float d;
    private float cw;
    private float pan = 0f;
    private boolean cross = false;
    private float cx = 0f, cy = 0f;
    private boolean scaling = false;
    private final Paint pUp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pDn = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pGrid = new Paint();
    private final Paint pTxt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBox = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBoxTxt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBg = new Paint();
    private final ScaleGestureDetector sgd;
    private final GestureDetector gd;
    private final SimpleDateFormat tf = new SimpleDateFormat("MM-dd HH:mm", Locale.US);

    NativeChart(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        cw = 7f * d;
        pUp.setColor(0xFF30D158);
        pDn.setColor(0xFFFF453A);
        pGrid.setColor(0x33888888);
        pGrid.setStrokeWidth(1f);
        pTxt.setColor(0xFF8E8E93);
        pTxt.setTextSize(11f * d);
        pLine.setStrokeWidth(1f * d);
        pBox.setColor(0xEE1C1C22);
        pBoxTxt.setColor(0xFFFFFFFF);
        pBoxTxt.setTextSize(12f * d);
        pBg.setColor(0xFF101218);
        sgd = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(ScaleGestureDetector s) { scaling = true; return true; }

            @Override
            public boolean onScale(ScaleGestureDetector s) {
                zoomBy(s.getScaleFactor(), s.getFocusX());
                return true;
            }

            @Override
            public void onScaleEnd(ScaleGestureDetector s) { scaling = false; }
        });
        gd = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) { return true; }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                if (scaling) { return true; }
                if (cross) { cx = e2.getX(); cy = e2.getY(); invalidate(); return true; }
                pan = clampPan(pan - dx);
                invalidate();
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if (cross) { cross = false; invalidate(); }
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                cross = true; cx = e.getX(); cy = e.getY(); invalidate();
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                cross = false; cw = 7f * d; pan = 0f; invalidate();
                return true;
            }
        });
    }

    /* ---------- called from the bridge ---------- */
    private static void ensure(MainActivity a) {
        if (inst == null) { inst = new NativeChart(a); }
    }

    static void place(final int x, final int y, final int w, final int h) {
        final MainActivity a = MainActivity.cur;
        if (a == null || w < 20 || h < 20) { return; }
        a.runOnUiThread(new Runnable() {
            public void run() {
                try {
                    if (inst != null && (inst.getContext() != a || (inst.getParent() != null && inst.getParent() != a.rootView()))) { try { if (inst.getParent() != null) { ((android.view.ViewGroup) inst.getParent()).removeView(inst); } } catch (Throwable ignored) { } inst = null; lx = ly = lw = lh = -1; }
                    ensure(a);
                    FrameLayout r = a.rootView();
                    if (r == null) { return; }
                    if (inst.getParent() == null) {
                        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
                        lp.leftMargin = x; lp.topMargin = y;
                        r.addView(inst, lp);
                        lx = x; ly = y; lw = w; lh = h;
                    } else if (x != lx || y != ly || w != lw || h != lh) {
                        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
                        lp.leftMargin = x; lp.topMargin = y;
                        inst.setLayoutParams(lp);
                        lx = x; ly = y; lw = w; lh = h;
                    }
                    inst.setVisibility(View.VISIBLE);
                } catch (Throwable ignored) { }
            }
        });
    }

    static void hide() {
        final MainActivity a = MainActivity.cur;
        if (a == null) { return; }
        a.runOnUiThread(new Runnable() {
            public void run() {
                try { if (inst != null) { inst.setVisibility(View.GONE); } } catch (Throwable ignored) { }
            }
        });
    }

    static void data(String json, final String lab) {
        final MainActivity a = MainActivity.cur;
        if (a == null || json == null) { return; }
        try {
            JSONArray arr = new JSONArray(json);
            final double[][] nk = new double[arr.length()][];
            for (int i = 0; i < nk.length; i++) {
                JSONArray r = arr.getJSONArray(i);
                nk[i] = new double[] { r.getDouble(0), r.getDouble(1), r.getDouble(2), r.getDouble(3), r.getDouble(4), r.optDouble(5, 0) };
            }
            a.runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        ensure(a);
                        boolean fresh = inst.k.length == 0 || !lab.equals(inst.label);
                        inst.k = nk;
                        inst.label = lab;
                        if (fresh) { inst.pan = 0f; inst.cross = false; }
                        inst.invalidate();
                    } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable ignored) { }
    }

    /* ---------- maths ---------- */
    private float plotW() { return getWidth() - 58f * d; }

    private float clampPan(float p) {
        float pw = plotW();
        float max = Math.max(0f, k.length * cw - pw + 6f * d + cw);
        float min = -0.35f * pw;
        if (p > max) { p = max; }
        if (p < min) { p = min; }
        return p;
    }

    private void zoomBy(float f, float fx) {
        if (k.length == 0) { return; }
        float pw = plotW();
        int n = k.length;
        float idx = (n - 1) - (pw - 6f * d + pan - cw / 2f - fx) / cw;
        float ncw = cw * f;
        if (ncw < 2f * d) { ncw = 2f * d; }
        if (ncw > 40f * d) { ncw = 40f * d; }
        cw = ncw;
        pan = clampPan(fx - pw + 6f * d + (n - 1 - idx) * cw + cw / 2f);
        invalidate();
    }

    private float xOf(int i) {
        return plotW() - 6f * d - (k.length - 1 - i) * cw + pan - cw / 2f;
    }

    private static String fmt(double p) {
        if (p >= 1000) { return String.format(Locale.US, "%.2f", p); }
        if (p >= 1) { return String.format(Locale.US, "%.4f", p); }
        return String.format(Locale.US, "%.6f", p);
    }

    /* ---------- drawing ---------- */
    @Override
    protected void onDraw(Canvas c) {
        int W = getWidth(), H = getHeight();
        c.drawRect(0f, 0f, W, H, pBg);
        int n = k.length;
        if (n == 0) {
            c.drawText("Loading chart...", 16f * d, 30f * d, pTxt);
            return;
        }
        float pw = plotW();
        float top = 24f * d, bot = 22f * d;
        float ph = H - top - bot;
        double hi = -1e300, lo = 1e300;
        int first = n, last = -1;
        for (int i = 0; i < n; i++) {
            float x = xOf(i);
            if (x < -cw || x > pw + cw) { continue; }
            if (i < first) { first = i; }
            last = i;
            if (k[i][2] > hi) { hi = k[i][2]; }
            if (k[i][3] < lo) { lo = k[i][3]; }
        }
        if (last < 0) { first = Math.max(0, n - 1); last = n - 1; hi = k[last][2]; lo = k[last][3]; }
        double span = hi - lo;
        if (span <= 0) { span = Math.abs(hi) * 0.001 + 1e-9; }
        hi += span * 0.06; lo -= span * 0.06; span = hi - lo;
        // grid and price labels
        for (int g = 0; g <= 4; g++) {
            float y = top + ph * g / 4f;
            c.drawLine(0f, y, pw, y, pGrid);
            double pv = hi - span * g / 4.0;
            c.drawText(fmt(pv), pw + 4f * d, y + 4f * d, pTxt);
        }
        // time labels
        int step = Math.max(1, (int) (80f * d / cw));
        for (int i = last; i >= first; i -= step) {
            float x = xOf(i);
            if (x < 24f * d || x > pw - 24f * d) { continue; }
            c.drawText(tf.format(new Date((long) k[i][0])), x - 28f * d, H - 6f * d, pTxt);
        }
        // candles
        float bw = Math.max(1f, cw * 0.7f);
        for (int i = first; i <= last; i++) {
            float x = xOf(i);
            double[] r = k[i];
            Paint p = r[4] >= r[1] ? pUp : pDn;
            float yh = (float) (top + (hi - r[2]) / span * ph);
            float yl = (float) (top + (hi - r[3]) / span * ph);
            float yo = (float) (top + (hi - r[1]) / span * ph);
            float yc = (float) (top + (hi - r[4]) / span * ph);
            p.setStrokeWidth(Math.max(1f, d * 0.8f));
            c.drawLine(x, yh, x, yl, p);
            float t = Math.min(yo, yc), b = Math.max(yo, yc);
            if (b - t < 1f) { b = t + 1f; }
            c.drawRect(x - bw / 2f, t, x + bw / 2f, b, p);
        }
        // last price line
        double lp = k[n - 1][4];
        float yl2 = (float) (top + (hi - lp) / span * ph);
        if (yl2 >= top && yl2 <= top + ph) {
            pLine.setColor(k[n - 1][4] >= k[n - 1][1] ? 0xFF30D158 : 0xFFFF453A);
            pLine.setPathEffect(new DashPathEffect(new float[] { 6f * d, 4f * d }, 0f));
            c.drawLine(0f, yl2, pw, yl2, pLine);
            pLine.setPathEffect(null);
            c.drawRect(pw, yl2 - 9f * d, W, yl2 + 9f * d, k[n - 1][4] >= k[n - 1][1] ? pUp : pDn);
            pBoxTxt.setTextSize(11f * d);
            c.drawText(fmt(lp), pw + 3f * d, yl2 + 4f * d, pBoxTxt);
            pBoxTxt.setTextSize(12f * d);
        }
        c.drawText(label, 8f * d, 16f * d, pTxt);
        // crosshair
        if (cross) {
            int ci = Math.round((n - 1) - (pw - 6f * d + pan - cw / 2f - cx) / cw);
            if (ci < 0) { ci = 0; }
            if (ci > n - 1) { ci = n - 1; }
            float x = xOf(ci);
            pLine.setColor(0xCCFFFFFF);
            c.drawLine(x, top, x, top + ph, pLine);
            float cyy = Math.max(top, Math.min(top + ph, cy));
            c.drawLine(0f, cyy, pw, cyy, pLine);
            double pa = hi - (cyy - top) / ph * span;
            c.drawRect(pw, cyy - 9f * d, W, cyy + 9f * d, pBox);
            pBoxTxt.setTextSize(11f * d);
            c.drawText(fmt(pa), pw + 3f * d, cyy + 4f * d, pBoxTxt);
            pBoxTxt.setTextSize(12f * d);
            double[] r = k[ci];
            double chg = r[1] != 0 ? (r[4] - r[1]) / r[1] * 100.0 : 0;
            String[] lines = new String[] {
                tf.format(new Date((long) r[0])),
                "Open  " + fmt(r[1]),
                "High  " + fmt(r[2]),
                "Low   " + fmt(r[3]),
                "Close " + fmt(r[4]),
                String.format(Locale.US, "Change %+.2f%%", chg)
            };
            float bx = x < pw / 2f ? pw - 150f * d : 8f * d;
            float by = top + 4f * d;
            c.drawRect(bx, by, bx + 142f * d, by + 6f * 17f * d + 8f * d, pBox);
            for (int i = 0; i < lines.length; i++) {
                c.drawText(lines[i], bx + 8f * d, by + (i + 1) * 17f * d, pBoxTxt);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN && getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        sgd.onTouchEvent(e);
        if (!sgd.isInProgress()) { gd.onTouchEvent(e); }
        return true;
    }
}
