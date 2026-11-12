package com.mintoosuite.terminal;

import android.content.Context;
import android.content.Intent;

import java.io.BufferedReader;
import java.io.StringReader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/* MintooSuiteVPN: a WireGuard tunnel run by the open-source WireGuard library (loaded by reflection, so the app still starts if the library is missing).
   The user imports their own WireGuard config (e.g. ProtonVPN Free). The private key stays in the app's private storage and is never sent anywhere. */
final class VpnHelper {
    static final int REQ = 7731;
    private static Object backend, tunnel;
    private static volatile String state = "off", err = "";

    private VpnHelper() { }

    static boolean available() { try { Class.forName("com.wireguard.android.backend.GoBackend"); return true; } catch (Throwable e) { return false; } }

    private static android.content.SharedPreferences sp(Context c) { return c.getApplicationContext().getSharedPreferences("mw_vpn", Context.MODE_PRIVATE); }

    static boolean has(Context c) { return sp(c).getString("cfg", "").length() > 20; }

    static String state(Context c) {
        return "{\"avail\":" + available() + ",\"has\":" + has(c) + ",\"state\":\"" + state + "\",\"err\":" + org.json.JSONObject.quote(err) + "}";
    }

    static boolean save(Context c, String text) {
        try {
            if (text == null || text.length() > 8000 || !text.contains("[Interface]") || !text.contains("[Peer]")) { err = "That is not a WireGuard config (it needs [Interface] and [Peer])."; return false; }
            parse(text);
            sp(c).edit().putString("cfg", text).apply(); err = ""; return true;
        } catch (Throwable e) { err = msg(e); return false; }
    }

    static void clear(Context c) { down(c); sp(c).edit().remove("cfg").apply(); state = "off"; err = ""; }

    private static String msg(Throwable e) {
        Throwable t = e; while (t.getCause() != null && t != t.getCause()) { t = t.getCause(); }
        String m = t.getMessage(); return (m == null || m.length() == 0) ? t.getClass().getSimpleName() : (m.length() > 160 ? m.substring(0, 160) : m);
    }

    private static Object parse(String text) throws Exception {
        Class<?> cc = Class.forName("com.wireguard.config.Config");
        return cc.getMethod("parse", BufferedReader.class).invoke(null, new BufferedReader(new StringReader(text)));
    }

    /* Android asks the user once: "Allow MintooSuite to set up a VPN connection?" */
    static void up(final Context c) {
        try {
            if (!available()) { err = "The VPN engine is not in this build."; state = "off"; return; }
            if (!has(c)) { err = "Import a WireGuard config first."; return; }
            final Context app = c.getApplicationContext();
            Intent i = android.net.VpnService.prepare(app);
            if (i != null) {
                MainActivity a = MainActivity.cur;
                if (a == null) { err = "Open the app first."; return; }
                state = "asking"; err = "";
                a.startActivityForResult(i, REQ); return;
            }
            connect(app);
        } catch (Throwable e) { err = msg(e); state = "off"; }
    }

    static void afterPrepare(Context c, boolean okay) {
        if (!okay) { state = "off"; err = "You did not allow the VPN. Tap Connect and press OK."; return; }
        connect(c.getApplicationContext());
    }

    private static void connect(final Context app) {
        state = "connecting"; err = "";
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Class<?> tc = Class.forName("com.wireguard.android.backend.Tunnel");
                    Class<?> sc = Class.forName("com.wireguard.android.backend.Tunnel$State");
                    Class<?> bc = Class.forName("com.wireguard.android.backend.GoBackend");
                    Class<?> cc = Class.forName("com.wireguard.config.Config");
                    if (backend == null) { backend = bc.getConstructor(Context.class).newInstance(app); }
                    if (tunnel == null) {
                        tunnel = Proxy.newProxyInstance(tc.getClassLoader(), new Class<?>[]{tc}, new InvocationHandler() {
                            @Override
                            public Object invoke(Object p, Method m, Object[] a) {
                                String n = m.getName();
                                if ("getName".equals(n)) { return "mintoosuite"; }
                                if ("onStateChange".equals(n) && a != null && a.length > 0) { String s = String.valueOf(a[0]).toLowerCase(); state = "up".equals(s) ? "on" : "off"; return null; }
                                if (m.getReturnType() == boolean.class) { return Boolean.FALSE; }
                                return null;
                            }
                        });
                    }
                    Object cfg = parse(sp(app).getString("cfg", ""));
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    Object up = Enum.valueOf((Class) sc, "UP");
                    bc.getMethod("setState", tc, sc, cc).invoke(backend, tunnel, up, cfg);
                    state = "on"; err = "";
                } catch (Throwable e) { err = msg(e); state = "off"; }
            }
        }).start();
    }

    static void down(final Context c) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (backend == null || tunnel == null) { state = "off"; return; }
                    Class<?> tc = Class.forName("com.wireguard.android.backend.Tunnel");
                    Class<?> sc = Class.forName("com.wireguard.android.backend.Tunnel$State");
                    Class<?> bc = Class.forName("com.wireguard.android.backend.GoBackend");
                    Class<?> cc = Class.forName("com.wireguard.config.Config");
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    Object dn = Enum.valueOf((Class) sc, "DOWN");
                    bc.getMethod("setState", tc, sc, cc).invoke(backend, tunnel, dn, null);
                    state = "off"; err = "";
                } catch (Throwable e) { err = msg(e); state = "off"; }
            }
        }).start();
    }
}
