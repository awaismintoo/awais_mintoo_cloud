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
                Media.geo(origin, cb);
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb, FileChooserParams params) {
                return Media.chooser(cb, params);
            }
        });
        web = w;
        w.loadUrl("https://" + HOST + "/assets/index.html");
        return w;
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
