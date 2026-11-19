package com.mintoosuite.terminal;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/* Google Play Billing (build 365), subscription "mintoo_pro" (base plans such as monthly / yearly are made in Play Console).
   Written with reflection so the app always compiles; if the billing library or Play is not there, everything just says "not available". */
final class BillingHelper {
    private BillingHelper() { }
    static final String PRODUCT = "mintoo_pro";
    private static final String PK = "com.android.billingclient.api.";
    private static Object client;
    private static boolean ready = false, connecting = false;
    private static final List<Object> details = new ArrayList<Object>();
    private static final List<JSONObject> offers = new ArrayList<JSONObject>();
    private static final List<Object> offerObj = new ArrayList<Object>();   // [ProductDetails, offerToken] pairs flattened
    private static String msg = "";

    private static Class<?> k(String n) throws Exception { return Class.forName(PK + n, true, BillingHelper.class.getClassLoader()); }
    private static SharedPreferences sp(Context c) { return c.getSharedPreferences("mw_bill", Context.MODE_PRIVATE); }

    private static Object proxy(String iface, final InvocationHandler h) throws Exception {
        return Proxy.newProxyInstance(BillingHelper.class.getClassLoader(), new Class<?>[]{ k(iface) }, h);
    }

    private static int code(Object billingResult) {
        try { return ((Integer) billingResult.getClass().getMethod("getResponseCode").invoke(billingResult)).intValue(); } catch (Throwable e) { return -1; }
    }

    static boolean available() {
        try { k("BillingClient"); return true; } catch (Throwable e) { return false; }
    }

    static synchronized void start(final Context c) {
        if (ready || connecting) { return; }
        try {
            final Context app = c.getApplicationContext();
            Object pl = proxy("PurchasesUpdatedListener", new InvocationHandler() {
                @Override public Object invoke(Object p, Method m, Object[] a) {
                    try { if ("onPurchasesUpdated".equals(m.getName())) { handle(app, code(a[0]), (List<?>) a[1]); } } catch (Throwable ignored) { }
                    return null;
                }
            });
            Object b = k("BillingClient").getMethod("newBuilder", Context.class).invoke(null, app);
            b = b.getClass().getMethod("setListener", k("PurchasesUpdatedListener")).invoke(b, pl);
            try {
                Object pp = k("PendingPurchasesParams").getMethod("newBuilder").invoke(null);
                pp = pp.getClass().getMethod("enableOneTimeProducts").invoke(pp);
                pp = pp.getClass().getMethod("build").invoke(pp);
                b = b.getClass().getMethod("enablePendingPurchases", k("PendingPurchasesParams")).invoke(b, pp);
            } catch (Throwable t1) {
                try { b = b.getClass().getMethod("enablePendingPurchases").invoke(b); } catch (Throwable ignored) { }
            }
            client = b.getClass().getMethod("build").invoke(b);
            connecting = true;
            Object sl = proxy("BillingClientStateListener", new InvocationHandler() {
                @Override public Object invoke(Object p, Method m, Object[] a) {
                    try {
                        if ("onBillingSetupFinished".equals(m.getName())) {
                            connecting = false;
                            if (code(a[0]) == 0) { ready = true; msg = ""; refresh(app); loadOffers(); } else { ready = false; msg = "Google Play billing is not available on this phone (code " + code(a[0]) + ")."; }
                        } else if ("onBillingServiceDisconnected".equals(m.getName())) { ready = false; connecting = false; }
                    } catch (Throwable ignored) { }
                    return null;
                }
            });
            client.getClass().getMethod("startConnection", k("BillingClientStateListener")).invoke(client, sl);
        } catch (Throwable e) { connecting = false; ready = false; msg = "Google Play billing is not part of this app build."; }
    }

    /* ask Play which subscriptions are active right now; this is what makes a cancelled or refunded plan stop */
    static void refresh(final Context app) {
        try {
            if (!ready || client == null) { return; }
            Object q = k("QueryPurchasesParams").getMethod("newBuilder").invoke(null);
            q = q.getClass().getMethod("setProductType", String.class).invoke(q, "subs");
            q = q.getClass().getMethod("build").invoke(q);
            Object l = proxy("PurchasesResponseListener", new InvocationHandler() {
                @Override public Object invoke(Object p, Method m, Object[] a) {
                    try { if ("onQueryPurchasesResponse".equals(m.getName()) && code(a[0]) == 0) { handle(app, 0, (List<?>) a[1]); } } catch (Throwable ignored) { }
                    return null;
                }
            });
            client.getClass().getMethod("queryPurchasesAsync", k("QueryPurchasesParams"), k("PurchasesResponseListener")).invoke(client, q, l);
        } catch (Throwable ignored) { }
    }

    private static void handle(final Context app, int rc, List<?> purchases) throws Exception {
        if (rc == 1) { msg = "Payment cancelled."; return; }
        if (rc != 0) { msg = "Payment did not complete (code " + rc + ")."; return; }
        boolean active = false; String token = "", product = ""; long when = 0; boolean auto = false;
        if (purchases != null) {
            for (Object p : purchases) {
                @SuppressWarnings("unchecked") List<String> ids = (List<String>) p.getClass().getMethod("getProducts").invoke(p);
                int st = ((Integer) p.getClass().getMethod("getPurchaseState").invoke(p)).intValue();
                if (ids == null || !ids.contains(PRODUCT) || st != 1) { continue; }
                active = true;
                token = (String) p.getClass().getMethod("getPurchaseToken").invoke(p);
                when = ((Long) p.getClass().getMethod("getPurchaseTime").invoke(p)).longValue();
                auto = ((Boolean) p.getClass().getMethod("isAutoRenewing").invoke(p)).booleanValue();
                product = PRODUCT;
                boolean acked = ((Boolean) p.getClass().getMethod("isAcknowledged").invoke(p)).booleanValue();
                if (!acked) { ack(token); }
            }
        }
        sp(app).edit().putBoolean("active", active).putString("token", token).putString("product", product).putLong("buyAt", when).putBoolean("auto", auto).putLong("checkedAt", System.currentTimeMillis()).apply();
        if (active) { msg = "Pro is active through Google Play."; }
    }

    private static void ack(String token) {
        try {
            Object a = k("AcknowledgePurchaseParams").getMethod("newBuilder").invoke(null);
            a = a.getClass().getMethod("setPurchaseToken", String.class).invoke(a, token);
            a = a.getClass().getMethod("build").invoke(a);
            Object l = proxy("AcknowledgePurchaseResponseListener", new InvocationHandler() { @Override public Object invoke(Object p, Method m, Object[] x) { return null; } });
            client.getClass().getMethod("acknowledgePurchase", k("AcknowledgePurchaseParams"), k("AcknowledgePurchaseResponseListener")).invoke(client, a, l);
        } catch (Throwable ignored) { }
    }

    private static void loadOffers() {
        try {
            Object pr = k("QueryProductDetailsParams$Product").getMethod("newBuilder").invoke(null);
            pr = pr.getClass().getMethod("setProductId", String.class).invoke(pr, PRODUCT);
            pr = pr.getClass().getMethod("setProductType", String.class).invoke(pr, "subs");
            pr = pr.getClass().getMethod("build").invoke(pr);
            List<Object> list = new ArrayList<Object>(); list.add(pr);
            Object q = k("QueryProductDetailsParams").getMethod("newBuilder").invoke(null);
            q = q.getClass().getMethod("setProductList", List.class).invoke(q, list);
            q = q.getClass().getMethod("build").invoke(q);
            Object l = proxy("ProductDetailsResponseListener", new InvocationHandler() {
                @Override public Object invoke(Object p, Method m, Object[] a) {
                    try { if ("onProductDetailsResponse".equals(m.getName()) && code(a[0]) == 0) { readOffers((List<?>) a[1]); } } catch (Throwable ignored) { }
                    return null;
                }
            });
            client.getClass().getMethod("queryProductDetailsAsync", k("QueryProductDetailsParams"), k("ProductDetailsResponseListener")).invoke(client, q, l);
        } catch (Throwable ignored) { }
    }

    private static synchronized void readOffers(List<?> pds) throws Exception {
        offers.clear(); offerObj.clear(); details.clear();
        if (pds == null) { return; }
        for (Object pd : pds) {
            List<?> os = (List<?>) pd.getClass().getMethod("getSubscriptionOfferDetails").invoke(pd);
            if (os == null) { continue; }
            for (Object o : os) {
                String tok = (String) o.getClass().getMethod("getOfferToken").invoke(o);
                String base = (String) o.getClass().getMethod("getBasePlanId").invoke(o);
                Object phases = o.getClass().getMethod("getPricingPhases").invoke(o);
                List<?> pl = (List<?>) phases.getClass().getMethod("getPricingPhaseList").invoke(phases);
                Object ph = pl.get(pl.size() - 1);
                JSONObject j = new JSONObject();
                j.put("i", offers.size()); j.put("base", base);
                j.put("price", ph.getClass().getMethod("getFormattedPrice").invoke(ph));
                j.put("period", ph.getClass().getMethod("getBillingPeriod").invoke(ph));
                offers.add(j); offerObj.add(pd); offerObj.add(tok);
            }
        }
    }

    static void buy(final Activity a, final int idx) {
        try {
            if (!ready || client == null || idx < 0 || idx >= offers.size()) { msg = "Pick a plan again in a moment."; return; }
            Object pd = offerObj.get(idx * 2); String tok = (String) offerObj.get(idx * 2 + 1);
            Object pp = k("BillingFlowParams$ProductDetailsParams").getMethod("newBuilder").invoke(null);
            pp = pp.getClass().getMethod("setProductDetails", k("ProductDetails")).invoke(pp, pd);
            pp = pp.getClass().getMethod("setOfferToken", String.class).invoke(pp, tok);
            pp = pp.getClass().getMethod("build").invoke(pp);
            List<Object> list = new ArrayList<Object>(); list.add(pp);
            Object f = k("BillingFlowParams").getMethod("newBuilder").invoke(null);
            f = f.getClass().getMethod("setProductDetailsParamsList", List.class).invoke(f, list);
            f = f.getClass().getMethod("build").invoke(f);
            client.getClass().getMethod("launchBillingFlow", Activity.class, k("BillingFlowParams")).invoke(client, a, f);
        } catch (Throwable e) { msg = "Could not open Google Play payment."; }
    }

    static String state(Context c) {
        try {
            SharedPreferences s = sp(c);
            JSONObject j = new JSONObject();
            j.put("lib", available()); j.put("ready", ready); j.put("msg", msg);
            j.put("active", s.getBoolean("active", false)); j.put("checkedAt", s.getLong("checkedAt", 0));
            j.put("buyAt", s.getLong("buyAt", 0)); j.put("auto", s.getBoolean("auto", false)); j.put("token", s.getString("token", ""));
            JSONArray arr = new JSONArray(); synchronized (BillingHelper.class) { for (JSONObject o : offers) { arr.put(o); } }
            j.put("offers", arr);
            return j.toString();
        } catch (Throwable e) { return "{\"lib\":false}"; }
    }
}
