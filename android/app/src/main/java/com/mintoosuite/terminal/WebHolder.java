package com.mintoosuite.terminal;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.MutableContextWrapper;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

/* mw-v108: one WebView for the whole process. The screen (MainActivity) only shows it; the background service keeps it alive. */
final class WebHolder {
    static final String HOST = "appassets.androidplatform.net";
    static final String TOKEN = java.util.UUID.randomUUID().toString();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static WebView web;
    private static MutableContextWrapper wrap;
    private static Context app;

    private WebHolder() { }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    static synchronized WebView get(Context any) {
        if (web != null) { return web; }
        app = any.getApplicationContext();
        wrap = new MutableContextWrapper(app);
        final WebView w = new WebView(wrap);
        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(app))
                .build();
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setSupportZoom(true); s.setBuiltInZoomControls(true); s.setDisplayZoomControls(false); s.setUseWideViewPort(true);
        s.setDomStorageEnabled(true);
        try { if (android.os.Build.VERSION.SDK_INT >= 26) { w.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false); } } catch (Exception ignored) { }
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        w.addJavascriptInterface(new Bridge(app), "MintooNative");
        w.setWebViewClient(new WebViewClient() {
            @SuppressLint("NewApi")
            @Override
            public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
                try { if (view.getParent() instanceof ViewGroup) { ((ViewGroup) view.getParent()).removeView(view); } view.destroy(); } catch (Exception ignored) { }
                web = null;
                return true;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (HOST.equals(u.getHost())) { return false; }
                if (!request.isForMainFrame() && !request.hasGesture()) { return true; }
                String sch = u.getScheme();
                if ("mailto".equals(sch) || "tel".equals(sch) || "sms".equals(sch) || "smsto".equals(sch) || "whatsapp".equals(sch)) {
                    try {
                        Intent i = new Intent("mailto".equals(sch) ? Intent.ACTION_SENDTO : Intent.ACTION_VIEW, u);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        app.startActivity(i);
                    } catch (Exception e) {
                        view.evaluateJavascript("window.__mwMailFail&&window.__mwMailFail();", null);
                    }
                    return true;
                }
                if ("http".equals(sch) || "https".equals(sch)) {
                    try {
                        Intent i = new Intent(Intent.ACTION_VIEW, u);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        app.startActivity(i);
                    } catch (Exception ignored) { }
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                view.evaluateJavascript("window.__mwTok='" + TOKEN + "';", null);
            }
        });
        w.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, android.webkit.GeolocationPermissions.Callback cb) {
                if (origin == null || !(origin.equals("https://" + HOST) || origin.equals("https://" + HOST + "/"))) { cb.invoke(origin, false, false); return; }
                Media.geo(origin, cb);
            }

            @Override
            public void onPermissionRequest(final android.webkit.PermissionRequest req) {
                try {
                    if (req.getOrigin() == null || !HOST.equals(req.getOrigin().getHost())) { req.deny(); return; }
                    final java.util.ArrayList<String> ok = new java.util.ArrayList<String>();
                    String[] rs = req.getResources();
                    for (int i = 0; i < rs.length; i++) {
                        if (android.webkit.PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(rs[i])) { ok.add(rs[i]); }
                    }
                    if (ok.isEmpty()) { req.deny(); return; }
                    MAIN.post(new Runnable() {
                        @Override
                        public void run() {
                            try { req.grant(ok.toArray(new String[0])); } catch (Exception ignored) { }
                        }
                    });
                } catch (Exception e) {
                    try { req.deny(); } catch (Exception ignored) { }
                }
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb, FileChooserParams params) {
                return Media.chooser(cb, params);
            }
        });
        web = w;
        startWatchdog();
        w.loadUrl("https://" + HOST + "/assets/index.html");
        return w;
    }


    /* native freeze watchdog: asks the page a trivial question every 6 s; if the page has not answered for 25 s while it is on screen, reload it (at most 3 times in 10 minutes) */
    private static boolean wdOn = false;
    private static long wdLastOk = 0, wdWinStart = 0;
    private static int wdReloads = 0;

    static void startWatchdog() {
        if (wdOn) { return; }
        wdOn = true;
        wdLastOk = System.currentTimeMillis();
        MAIN.postDelayed(new Runnable() {
            @Override
            public void run() {
                try { wdTick(); } catch (Exception ignored) { }
                MAIN.postDelayed(this, 6000);
            }
        }, 40000);
    }

    private static void wdTick() {
        final WebView w = web;
        if (w == null) { return; }
        long now = System.currentTimeMillis();
        if (!w.isShown()) { wdLastOk = now; return; }
        if (now - wdLastOk > 25000) {
            if (now - wdWinStart > 600000) { wdWinStart = now; wdReloads = 0; }
            wdLastOk = now;
            if (wdReloads < 3) {
                wdReloads++;
                try { w.reload(); } catch (Exception ignored) { }
            }
            return;
        }
        try {
            w.evaluateJavascript("(function(){return 1;})()", new ValueCallback<String>() {
                @Override
                public void onReceiveValue(String v) { wdLastOk = System.currentTimeMillis(); }
            });
        } catch (Exception ignored) { }
    }

    static boolean alive() { return web != null; }

    static void attach(android.app.Activity a, ViewGroup root) {
        WebView w = get(a);
        wrap.setBaseContext(a);
        if (w.getParent() instanceof ViewGroup) { ((ViewGroup) w.getParent()).removeView(w); }
        root.addView(w, 0, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    static void detach() {
        if (web == null) { return; }
        if (web.getParent() instanceof ViewGroup) { ((ViewGroup) web.getParent()).removeView(web); }
        if (wrap != null && app != null) { wrap.setBaseContext(app); }
        keepAlive();
    }

    /* keep page timers running while the app is in the background (alerts, prices, bot) */
    static void keepAlive() {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                try { if (web != null) { web.onResume(); web.resumeTimers(); } } catch (Exception ignored) { }
            }
        });
    }

    static void destroy() {
        if (web == null) { return; }
        detach();
        try { web.destroy(); } catch (Exception ignored) { }
        web = null;
    }

    static void js(final String code) {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                if (web != null) { web.evaluateJavascript(code, null); }
            }
        });
    }
}
