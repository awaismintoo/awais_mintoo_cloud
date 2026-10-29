package com.mintoosuite.terminal;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

/* mw-v108: the screen. The WebView itself lives in WebHolder so the background service can keep it running. */
public class MainActivity extends AppCompatActivity {
    static MainActivity cur;
    static final String CHANNEL = "mintoo_alerts";
    private static final String KEY_ALIAS = "mw_bio_key";
    private static final long RELOCK_MS = 30000L;
    private FrameLayout root;
    private View lockView;
    private long leftAt = 0L;
    private boolean night;

    interface BioDo { String run(javax.crypto.Cipher c) throws Exception; }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        cur = this;
        night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(!night);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightNavigationBars(!night);

        root = new FrameLayout(this);
        root.setBackgroundColor(night ? 0xFF000000 : 0xFFF2F2F7);
        setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            return WindowInsetsCompat.CONSUMED;
        });
        createChannel();
        WebHolder.attach(this, root);
        try { AdsHelper.start(this, root); } catch (Throwable ignored) { }
        if (lockOn()) { showLock(); }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                WebView w = WebHolder.alive() ? WebHolder.get(MainActivity.this) : null;
                if (w != null && w.canGoBack()) { w.goBack(); } else { finish(); }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Media.onResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Media.onPerm(requestCode, grantResults);
    }

    @Override
    protected void onPause() {
        AdsHelper.pause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        AdsHelper.resume();
    }

    @Override
    protected void onStart() {
        super.onStart();
        cur = this;
        if (leftAt > 0 && lockOn() && lockView == null && SystemClock.elapsedRealtime() - leftAt > RELOCK_MS) { showLock(); }
    }

    @Override
    protected void onStop() {
        leftAt = SystemClock.elapsedRealtime();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (cur == this) { cur = null; }
        AdsHelper.destroy();
        WebHolder.detach();
        if (!BotService.isOn(this)) { WebHolder.destroy(); }
        super.onDestroy();
    }

    private void createChannel() { alertChannel(this); BotService.ensureChannel(this); }

    static void alertChannel(android.content.Context c) {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            android.app.NotificationChannel ch = new android.app.NotificationChannel(CHANNEL, "Trading alerts", android.app.NotificationManager.IMPORTANCE_HIGH);
            ((android.app.NotificationManager) c.getSystemService(android.content.Context.NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
    }

    /* ---------- whole-app lock (fingerprint, face or the phone's PIN / pattern) ---------- */
    static final int LOCK_AUTH = BiometricManager.Authenticators.BIOMETRIC_WEAK | BiometricManager.Authenticators.DEVICE_CREDENTIAL;

    SharedPreferences lockPrefs() { return getSharedPreferences("mw_lock", MODE_PRIVATE); }

    boolean lockOn() { return lockPrefs().getBoolean("on", false); }

    boolean lockCanAuth() {
        try { return BiometricManager.from(this).canAuthenticate(LOCK_AUTH) == BiometricManager.BIOMETRIC_SUCCESS; } catch (Exception e) { return false; }
    }

    private void showLock() {
        if (lockView != null) { return; }
        if (!lockCanAuth()) { lockPrefs().edit().putBoolean("on", false).apply(); return; }
        FrameLayout v = new FrameLayout(this);
        v.setBackgroundColor(night ? 0xFF000000 : 0xFFF2F2F7);
        v.setClickable(true);
        v.setFocusable(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        TextView t = new TextView(this);
        t.setText("MintooSuite is locked");
        t.setTextSize(22f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(night ? Color.WHITE : Color.BLACK);
        t.setGravity(Gravity.CENTER);
        TextView t2 = new TextView(this);
        t2.setText("Use your fingerprint, face or phone PIN to open it.");
        t2.setTextSize(15f);
        t2.setTextColor(night ? 0xFFAAAAAA : 0xFF555555);
        t2.setGravity(Gravity.CENTER);
        t2.setPadding(48, 16, 48, 32);
        Button b = new Button(this);
        b.setText("Unlock");
        b.setOnClickListener(x -> lockPrompt());
        col.addView(t);
        col.addView(t2);
        col.addView(b);
        v.addView(col, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        root.addView(v, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        lockView = v;
        v.post(this::lockPrompt);
    }

    private void hideLock() {
        if (lockView != null) { root.removeView(lockView); lockView = null; }
    }

    private void lockPrompt() {
        try {
            BiometricPrompt bp = new BiometricPrompt(this, ContextCompat.getMainExecutor(this), new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) { hideLock(); }

                @Override
                public void onAuthenticationError(int code, CharSequence msg) { }
            });
            bp.authenticate(new BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Unlock MintooSuite")
                    .setAllowedAuthenticators(LOCK_AUTH)
                    .build());
        } catch (Exception e) {
            lockPrefs().edit().putBoolean("on", false).apply();
            hideLock();
        }
    }

    /** Turn the lock on or off. The phone asks for the fingerprint or PIN first, so nobody else can change it. */
    void lockChange(final boolean on, final String cb) {
        runOnUiThread(() -> {
            if (!lockCanAuth()) { lockCb(cb, false, "Set a fingerprint, face or screen PIN in the phone settings first."); return; }
            try {
                BiometricPrompt bp = new BiometricPrompt(this, ContextCompat.getMainExecutor(this), new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) {
                        lockPrefs().edit().putBoolean("on", on).apply();
                        lockCb(cb, true, on ? "on" : "off");
                    }

                    @Override
                    public void onAuthenticationError(int code, CharSequence msg) { lockCb(cb, false, String.valueOf(msg)); }
                });
                bp.authenticate(new BiometricPrompt.PromptInfo.Builder()
                        .setTitle(on ? "Turn on app lock" : "Turn off app lock")
                        .setAllowedAuthenticators(LOCK_AUTH)
                        .build());
            } catch (Exception e) { lockCb(cb, false, "App lock is not available on this phone."); }
        });
    }

    static void lockCb(String cb, boolean ok, String data) {
        WebHolder.js("window.__mwLock&&window.__mwLock(" + org.json.JSONObject.quote(cb) + "," + ok + "," + org.json.JSONObject.quote(data == null ? "" : data) + ");");
    }

    /* ---------- wallet fingerprint (unchanged from v10.4) ---------- */
    static void jsCb(String cb, boolean ok, String data) {
        WebHolder.js("window.__mwBio&&window.__mwBio(" + org.json.JSONObject.quote(cb) + "," + ok + "," + org.json.JSONObject.quote(data == null ? "" : data) + ");");
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
                SharedPreferences sp = getSharedPreferences("mw_bio", MODE_PRIVATE);
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

    /* ---------- permission helpers used by the first-start popup ---------- */
    void askNotifications() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1);
        } else {
            openNotificationSettings();
        }
    }

    void openNotificationSettings() {
        Intent i;
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            i = new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName());
        } else {
            i = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + getPackageName()));
        }
        try { startActivity(i); } catch (Exception ignored) { }
    }

    void askBattery() {
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, android.net.Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            try { startActivity(new Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); } catch (Exception ignored) { openAppSettings(); }
        }
    }

    void openAppSettings() {
        try { startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + getPackageName()))); } catch (Exception ignored) { }
    }
}
