package com.mintoosuite.terminal;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;

/* mw-v108 experimental: a coin price list drawn by Android (smooth, light on memory). The web page only supplies rows and a screen position. */
final class NativeList extends FrameLayout {
    private static NativeList inst;
    private static int lx = -1, ly = -1, lw = -1, lh = -1;
    private final List<String[]> rows = new ArrayList<>();
    private final ListView lv;
    private final BaseAdapter ad;
    private final float d;

    NativeList(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        setBackgroundColor(0xFF101218);
        lv = new ListView(c);
        lv.setDivider(null); lv.setVerticalScrollBarEnabled(false); lv.setBackgroundColor(0xFF101218);
        ad = new BaseAdapter() {
            public int getCount() { return rows.size(); }
            public Object getItem(int i) { return rows.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View v, ViewGroup p) {
                LinearLayout row; TextView a, b, cc;
                if (v == null) {
                    Context cx = p.getContext(); row = new LinearLayout(cx); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding((int) (12 * d), (int) (11 * d), (int) (12 * d), (int) (11 * d));
                    a = new TextView(cx); a.setTextColor(0xFFFFFFFF); a.setTextSize(15f); a.setTypeface(Typeface.DEFAULT_BOLD); a.setSingleLine(true);
                    b = new TextView(cx); b.setTextColor(0xFFE5E5EA); b.setTextSize(15f); b.setGravity(Gravity.END); b.setSingleLine(true);
                    cc = new TextView(cx); cc.setTextSize(14f); cc.setTypeface(Typeface.DEFAULT_BOLD); cc.setGravity(Gravity.END); cc.setSingleLine(true);
                    row.addView(a, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.1f));
                    row.addView(b, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f));
                    row.addView(cc, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.9f));
                    row.setTag(new TextView[]{a, b, cc});
                } else { row = (LinearLayout) v; }
                TextView[] t = (TextView[]) row.getTag(); String[] r = rows.get(i);
                t[0].setText(r[0]); t[1].setText(r[1]); t[2].setText(r[2]); t[2].setTextColor(r[2].startsWith("-") ? 0xFFFF453A : 0xFF30D158);
                row.setBackgroundColor(i % 2 == 0 ? 0xFF101218 : 0xFF14171F);
                return row;
            }
        };
        lv.setAdapter(ad);
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                try { String s = rows.get(pos)[3]; WebHolder.js("window.__nlTap&&window.__nlTap('" + s.replaceAll("[^A-Z0-9]", "") + "');"); } catch (Throwable ignored) { }
            }
        });
        addView(lv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private static void ensure(MainActivity a) { if (inst == null) { inst = new NativeList(a); } }

    static void place(final int x, final int y, final int w, final int h) {
        final MainActivity a = MainActivity.cur;
        if (a == null || w < 20 || h < 20) { return; }
        a.runOnUiThread(new Runnable() {
            public void run() {
                try {
                    ensure(a);
                    FrameLayout r = a.rootView();
                    if (r == null) { return; }
                    if (inst.getParent() == null || x != lx || y != ly || w != lw || h != lh) {
                        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
                        lp.leftMargin = x; lp.topMargin = y;
                        if (inst.getParent() == null) { r.addView(inst, lp); } else { inst.setLayoutParams(lp); }
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
        a.runOnUiThread(new Runnable() { public void run() { try { if (inst != null) { inst.setVisibility(View.GONE); } } catch (Throwable ignored) { } } });
    }

    /* json: [["BTC","77,933.8","+1.2%","BTCUSDT"], ...] */
    static void data(String json) {
        final MainActivity a = MainActivity.cur;
        if (a == null || json == null) { return; }
        try {
            JSONArray arr = new JSONArray(json);
            final List<String[]> nr = new ArrayList<>();
            for (int i = 0; i < arr.length() && i < 300; i++) { JSONArray r = arr.getJSONArray(i); nr.add(new String[]{r.getString(0), r.getString(1), r.getString(2), r.getString(3)}); }
            a.runOnUiThread(new Runnable() {
                public void run() {
                    try { ensure(a); inst.rows.clear(); inst.rows.addAll(nr); inst.ad.notifyDataSetChanged(); } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable ignored) { }
    }
}
