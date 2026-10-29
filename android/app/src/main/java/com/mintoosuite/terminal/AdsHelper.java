package com.mintoosuite.terminal;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/* Banner ads (Google AdMob), written with reflection so the app always compiles and simply shows no ads if the library is missing.
   Shown only where the web page allows it (never on trading, wallet or chart screens). EEA/UK consent uses Google's UMP form.
   Ad ids come from android/ads.properties. */
final class AdsHelper {
    private static View banner;
    private static boolean started = false, want = false, ready = false;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ClassLoader LD = AdsHelper.class.getClassLoader();

    private AdsHelper() { }

    private static Class<?> cls(String n) throws Exception { return Class.forName(n, true, LD); }

    private static Object proxy(Class<?> iface, final Runnable r) {
        return Proxy.newProxyInstance(LD, new Class<?>[]{iface}, new InvocationHandler() {
            @Override
            public Object invoke(Object p, Method m, Object[] args) {
                String n = m.getName();
                if (n.equals("hashCode")) { return 0; }
                if (n.equals("equals")) { return false; }
                if (n.equals("toString")) { return "adsproxy"; }
                try { r.run(); } catch (Throwable ignored) { }
                return null;
            }
        });
    }

    static void start(final Activity a, final FrameLayout root) {
        if (started) { return; }
        started = true;
        try {
            final Class<?> upm = cls("com.google.android.ump.UserMessagingPlatform");
            final Object ci = upm.getMethod("getConsentInformation", Context.class).invoke(null, a);
            final Class<?> ciC = cls("com.google.android.ump.ConsentInformation");
            Class<?> prmB = cls("com.google.android.ump.ConsentRequestParameters$Builder");
            Class<?> prmC = cls("com.google.android.ump.ConsentRequestParameters");
            Object prm = prmB.getMethod("build").invoke(prmB.getConstructor().newInstance());
            Class<?> okL = cls("com.google.android.ump.ConsentInformation$OnConsentInfoUpdateSuccessListener");
            Class<?> failL = cls("com.google.android.ump.ConsentInformation$OnConsentInfoUpdateFailureListener");
            final Class<?> dismL = cls("com.google.android.ump.ConsentForm$OnConsentFormDismissedListener");
            final Runnable after = new Runnable() { @Override public void run() { maybeInit(a, root, ci, ciC); } };
            Object ok = proxy(okL, new Runnable() {
                @Override
                public void run() {
                    try { upm.getMethod("loadAndShowConsentFormIfRequired", Activity.class, dismL).invoke(null, a, proxy(dismL, after)); } catch (Throwable t) { after.run(); }
                }
            });
            Object fail = proxy(failL, after);
            ciC.getMethod("requestConsentInfoUpdate", Activity.class, prmC, okL, failL).invoke(ci, a, prm, ok, fail);
            after.run();
        } catch (Throwable t) { started = false; }
    }

    private static void maybeInit(Activity a, FrameLayout root, Object ci, Class<?> ciC) {
        try { if (Boolean.TRUE.equals(ciC.getMethod("canRequestAds").invoke(ci))) { init(a, root); } } catch (Throwable ignored) { }
    }

    private static void init(final Activity a, final FrameLayout root) {
        a.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (banner != null) { return; }
                    Class<?> mobile = cls("com.google.android.gms.ads.MobileAds");
                    mobile.getMethod("initialize", Context.class).invoke(null, a);
                    Class<?> avC = cls("com.google.android.gms.ads.AdView");
                    Class<?> sizeC = cls("com.google.android.gms.ads.AdSize");
                    View v = (View) avC.getConstructor(Context.class).newInstance(a);
                    int rid = a.getResources().getIdentifier("admob_banner_id", "string", a.getPackageName());
                    String id = rid != 0 ? a.getString(rid) : "ca-app-pub-3940256099547665/6300978111";
                    avC.getMethod("setAdUnitId", String.class).invoke(v, id);
                    avC.getMethod("setAdSize", sizeC).invoke(v, sizeC.getField("BANNER").get(null));
                    v.setVisibility(View.GONE);
                    root.addView(v, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
                    banner = v;
                    load();
                    MAIN.postDelayed(poll, 2000);
                } catch (Throwable t) { banner = null; }
            }
        });
    }

    private static void load() {
        try {
            Class<?> avC = cls("com.google.android.gms.ads.AdView");
            Class<?> reqB = cls("com.google.android.gms.ads.AdRequest$Builder");
            Class<?> reqC = cls("com.google.android.gms.ads.AdRequest");
            Object req = reqB.getMethod("build").invoke(reqB.getConstructor().newInstance());
            avC.getMethod("loadAd", reqC).invoke(banner, req);
        } catch (Throwable ignored) { }
    }

    private static int tick = 0;
    private static final Runnable poll = new Runnable() {
        @Override
        public void run() {
            if (banner == null) { return; }
            try {
                Object info = cls("com.google.android.gms.ads.AdView").getMethod("getResponseInfo").invoke(banner);
                boolean r = info != null;
                if (r != ready) { ready = r; apply(); }
                tick++;
                if (!ready && tick % 30 == 0) { load(); }
            } catch (Throwable ignored) { }
            MAIN.postDelayed(this, 2000);
        }
    };

    static void show(boolean w) {
        want = w;
        final Activity a = MainActivity.cur;
        if (a == null) { return; }
        a.runOnUiThread(new Runnable() { @Override public void run() { apply(); } });
    }

    private static void apply() {
        try {
            if (banner == null) { return; }
            boolean vis = want && ready;
            banner.setVisibility(vis ? View.VISIBLE : View.GONE);
            WebHolder.js("window.__mwAdH&&window.__mwAdH(" + (vis ? 50 : 0) + ");");
        } catch (Throwable ignored) { }
    }

    private static void call(String m) {
        try { if (banner != null) { cls("com.google.android.gms.ads.AdView").getMethod(m).invoke(banner); } } catch (Throwable ignored) { }
    }

    static void pause() { call("pause"); }

    static void resume() { call("resume"); }

    static void destroy() {
        MAIN.removeCallbacks(poll);
        call("destroy");
        banner = null; ready = false; started = false;
    }
}
