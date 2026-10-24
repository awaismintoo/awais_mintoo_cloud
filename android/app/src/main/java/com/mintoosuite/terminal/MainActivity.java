package com.mintoosuite.terminal;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.webkit.WebViewAssetLoader;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final String CHANNEL = "mintoo_alerts";
    private WebView web;
    final String token = java.util.UUID.randomUUID().toString();
    private static final String KEY_ALIAS = "mw_bio_key";

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(!night);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightNavigationBars(!night);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(night ? 0xFF000000 : 0xFFF2F2F7);
        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            return WindowInsetsCompat.CONSUMED;
        });

        createChannel();
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.addJavascriptInterface(new Bridge(this), "MintooNative");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (HOST.equals(u.getHost())) { return false; }
                String sch = u.getScheme();
                if ("http".equals(sch) || "https".equals(sch)) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                view.evaluateJavascript("window.__mwTok='" + token + "';", null);
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (web.canGoBack()) { web.goBack(); } else { finish(); }
            }
        });

        if (state != null) { web.restoreState(state); } else { web.loadUrl("https://" + HOST + "/assets/index.html"); }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (web != null) { web.saveState(out); }
    }

    @Override
    protected void onDestroy() {
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Trading alerts", NotificationManager.IMPORTANCE_HIGH);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    interface BioDo { String run(javax.crypto.Cipher c) throws Exception; }

    boolean ok(String tok) { return tok != null && tok.equals(token); }

    void js(final String code) {
        runOnUiThread(() -> { if (web != null) { web.evaluateJavascript(code, null); } });
    }

    void jsCb(String cb, boolean ok, String data) {
        js("window.__mwBio&&window.__mwBio(" + org.json.JSONObject.quote(cb) + "," + ok + "," + org.json.JSONObject.quote(data == null ? "" : data) + ");");
    }

    boolean bioAvailable() {
        return BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS;
    }

    boolean bioHas() {
        return getSharedPreferences("mw_bio", MODE_PRIVATE).contains("ct");
    }

    void bioClear() {
        getSharedPreferences("mw_bio", MODE_PRIVATE).edit().clear().apply();
        try {
            java.security.KeyStore ks = java.security.KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            if (ks.containsAlias(KEY_ALIAS)) { ks.deleteEntry(KEY_ALIAS); }
        } catch (Exception ignored) { }
    }

    private javax.crypto.SecretKey bioKey(boolean create) throws Exception {
        java.security.KeyStore ks = java.security.KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(KEY_ALIAS)) { return (javax.crypto.SecretKey) ks.getKey(KEY_ALIAS, null); }
        if (!create) { return null; }
        javax.crypto.KeyGenerator kg = javax.crypto.KeyGenerator.getInstance(android.security.keystore.KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        kg.init(new android.security.keystore.KeyGenParameterSpec.Builder(KEY_ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT | android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
                .build());
        return kg.generateKey();
    }

    private void bioPrompt(javax.crypto.Cipher cipher, String title, final String cb, final BioDo action) {
        BiometricPrompt bp = new BiometricPrompt(this, ContextCompat.getMainExecutor(this), new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) {
                try { jsCb(cb, true, action.run(r.getCryptoObject().getCipher())); } catch (Exception e) { jsCb(cb, false, "Fingerprint failed."); }
            }

            @Override
            public void onAuthenticationError(int code, CharSequence msg) { jsCb(cb, false, String.valueOf(msg)); }
        });
        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle("MintooSuite wallet")
                .setNegativeButtonText("Use password")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build();
        bp.authenticate(info, new BiometricPrompt.CryptoObject(cipher));
    }

    void bioSave(final String secret, final String cb) {
        runOnUiThread(() -> {
            try {
                javax.crypto.Cipher c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
                c.init(javax.crypto.Cipher.ENCRYPT_MODE, bioKey(true));
                bioPrompt(c, "Enable fingerprint", cb, cipher -> {
                    byte[] ct = cipher.doFinal(secret.getBytes("UTF-8"));
                    getSharedPreferences("mw_bio", MODE_PRIVATE).edit()
                            .putString("iv", android.util.Base64.encodeToString(cipher.getIV(), android.util.Base64.NO_WRAP))
                            .putString("ct", android.util.Base64.encodeToString(ct, android.util.Base64.NO_WRAP)).apply();
                    return "ok";
                });
            } catch (Exception e) { jsCb(cb, false, "Fingerprint is not available."); }
        });
    }

    void bioUnlock(final String cb) {
        runOnUiThread(() -> {
            try {
                android.content.SharedPreferences sp = getSharedPreferences("mw_bio", MODE_PRIVATE);
                String ivs = sp.getString("iv", ""), cts = sp.getString("ct", "");
                javax.crypto.SecretKey key = ivs.isEmpty() || cts.isEmpty() ? null : bioKey(false);
                if (key == null) { jsCb(cb, false, "Fingerprint is not set up."); return; }
                final byte[] ct = android.util.Base64.decode(cts, android.util.Base64.NO_WRAP);
                javax.crypto.Cipher c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
                c.init(javax.crypto.Cipher.DECRYPT_MODE, key, new javax.crypto.spec.GCMParameterSpec(128, android.util.Base64.decode(ivs, android.util.Base64.NO_WRAP)));
                bioPrompt(c, "Unlock with fingerprint", cb, cipher -> new String(cipher.doFinal(ct), "UTF-8"));
            } catch (android.security.keystore.KeyPermanentlyInvalidatedException e) {
                bioClear();
                jsCb(cb, false, "Your fingerprints changed. Turn fingerprint on again with your password.");
            } catch (Exception e) { jsCb(cb, false, "Fingerprint is not available."); }
        });
    }

    void httpGet(final String url, final String cb) {
        new Thread(() -> {
            String out;
            boolean good = false;
            try {
                java.net.URL u = new java.net.URL(url);
                if (!"https".equals(u.getProtocol())) { throw new Exception("https only"); }
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) u.openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(10000);
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) MintooSuite");
                int code = c.getResponseCode();
                java.io.InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n, total = 0;
                while (in != null && (n = in.read(buf)) > 0 && total < 2000000) { bo.write(buf, 0, n); total += n; }
                out = bo.toString("UTF-8");
                good = code >= 200 && code < 300;
            } catch (Exception e) { out = String.valueOf(e.getMessage()); }
            js("window.__mwHttp&&window.__mwHttp(" + org.json.JSONObject.quote(cb) + "," + good + "," + org.json.JSONObject.quote(out) + ");");
        }).start();
    }

    void bgSet(String json) {
        getSharedPreferences("mw_bg", MODE_PRIVATE).edit().putString("rules", json).apply();
        boolean any = false;
        try {
            org.json.JSONArray a = new org.json.JSONObject(json).optJSONArray("pal");
            any = a != null && a.length() > 0;
        } catch (Exception ignored) { }
        WorkManager wm = WorkManager.getInstance(getApplicationContext());
        if (any) {
            wm.enqueueUniquePeriodicWork("mw_alerts", ExistingPeriodicWorkPolicy.UPDATE,
                    new PeriodicWorkRequest.Builder(AlertWorker.class, 15, TimeUnit.MINUTES)
                            .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                            .build());
        } else {
            wm.cancelUniqueWork("mw_alerts");
        }
    }

    String bgFired() {
        android.content.SharedPreferences sp = getSharedPreferences("mw_bg", MODE_PRIVATE);
        String f = sp.getString("fired", "[]");
        sp.edit().remove("fired").apply();
        return f;
    }

    static class Bridge {
        private final Context ctx;
        private final MainActivity act;
        private int id = 1;

        Bridge(MainActivity a) { act = a; ctx = a.getApplicationContext(); }

        @JavascriptInterface
        public boolean bioAvailable(String tok) { return act.ok(tok) && act.bioAvailable(); }

        @JavascriptInterface
        public boolean bioHas(String tok) { return act.ok(tok) && act.bioHas(); }

        @JavascriptInterface
        public void bioSave(String tok, String secret, String cb) { if (act.ok(tok)) { act.bioSave(secret, cb); } }

        @JavascriptInterface
        public void bioUnlock(String tok, String cb) { if (act.ok(tok)) { act.bioUnlock(cb); } }

        @JavascriptInterface
        public void bioClear(String tok) { if (act.ok(tok)) { act.bioClear(); } }

        @JavascriptInterface
        public void httpGet(String tok, String url, String cb) { if (act.ok(tok)) { act.httpGet(url, cb); } }

        @JavascriptInterface
        public void bgSet(String tok, String json) { if (act.ok(tok)) { act.bgSet(json); } }

        @JavascriptInterface
        public String bgFired(String tok) { return act.ok(tok) ? act.bgFired() : "[]"; }

        @JavascriptInterface
        public void notify(String title, String body) {
            if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) { return; }
            Intent open = new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(ctx, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL)
                    .setSmallIcon(R.drawable.ic_stat)
                    .setContentTitle(title == null ? "MintooSuite" : title)
                    .setContentText(body == null ? "" : body)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(body == null ? "" : body))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(pi);
            ctx.getSystemService(NotificationManager.class).notify(id++ % 50, b.build());
        }
    }
}
