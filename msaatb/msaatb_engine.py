#!/usr/bin/env python3
"""
MSAATB engine (MintooSuite Auto AI Trading Bot) - standalone package
=====================================================================
The same rules as the MSAATB inside the MintooSuite app, for your own computer or server.

Exchanges: Binance, OKX, Bitget and Bybit USDT perpetual futures (through the free `ccxt` library).
Modes:     paper (default, no orders are sent) and live (needs your API key, trading permission only,
           NEVER withdrawal permission, plus the --live flag and typing YES once).

Rules (identical to the app):
  * Regime on 15-minute candles: TREND when ADX(14) >= 25 and Bollinger width is rising; otherwise WAIT.
    A shock candle (body > 3x ATR on 1 minute) in the last 5 minutes means WAIT.
  * Direction: EMA20 above EMA50 on 15 m and the 5 m close above its EMA20 -> LONG (mirror for SHORT).
  * Entry only when the 1 m and 3 m candles turn back in the trend direction, and the move is not
    stretched (distance from the 5 m EMA20 < 2.5 ATR, RSI(14) on 1 m not above 75 / below 25).
  * Stop = 1.5 x ATR(14) on 5 m. Size = risk% of equity / stop distance (0.25-1 %, default 0.5 %).
  * Stop ladder: +0.5R -> stop -0.5R, +1R -> break-even + fees, +1.5R -> +0.5R, +2R -> trail 1R,
    +3R -> trail 0.7R. Time stop: after 60 minutes below +0.5R while it never reached +1R.
  * Total open risk never above 3 %, daily loss stop 3 %, max 5 trades, after a loss the coin rests
    4 hours, after two losses within 24 hours it rests 24 hours.
  * FULL CONTROL of trades you open yourself (--manage-manual): the same stop ladder and time stop,
    and the trade is closed at once if the engine's own analysis turns against it.

No bot can promise profit. Run paper mode for weeks first. This file is educational software,
not financial advice.  (c) MintooSuite. Licence: personal use; see README.
"""
import argparse, json, math, os, sys, time

FEE = 0.0005           # taker fee per side (0.05 %)
VERSION = "536.1"


# ----------------------------------------------------------------------------- indicators
def ema(v, n):
    k, out = 2 / (n + 1), []
    for i, x in enumerate(v):
        out.append(x if i == 0 else x * k + out[-1] * (1 - k))
    return out


def atr(k, n=14):
    tr = [max(b["h"] - b["l"], abs(b["h"] - a["c"]), abs(b["l"] - a["c"])) for a, b in zip(k, k[1:])]
    if not tr:
        return 0.0
    tr = tr[-n * 3:]
    a = sum(tr[:n]) / min(n, len(tr))
    for x in tr[n:]:
        a = (a * (n - 1) + x) / n
    return a


def rsi(c, n=14):
    g = l = 0.0
    for a, b in zip(c[-n - 1:], c[-n:]):
        d = b - a
        g += max(d, 0)
        l += max(-d, 0)
    return 100.0 if l == 0 else 100 - 100 / (1 + g / l)


def adx(k, n=14):
    if len(k) < n * 2 + 2:
        return 0.0
    pdm, mdm, tr = [], [], []
    for a, b in zip(k, k[1:]):
        up, dn = b["h"] - a["h"], a["l"] - b["l"]
        pdm.append(up if up > dn and up > 0 else 0)
        mdm.append(dn if dn > up and dn > 0 else 0)
        tr.append(max(b["h"] - b["l"], abs(b["h"] - a["c"]), abs(b["l"] - a["c"])))

    def wild(v):
        s = sum(v[:n])
        out = [s]
        for x in v[n:]:
            s = s - s / n + x
            out.append(s)
        return out
    P, M, T = wild(pdm), wild(mdm), wild(tr)
    dx = []
    for p, m, t in zip(P, M, T):
        if t == 0:
            dx.append(0)
            continue
        pi, mi = 100 * p / t, 100 * m / t
        dx.append(0 if pi + mi == 0 else 100 * abs(pi - mi) / (pi + mi))
    a = sum(dx[:n]) / n
    for x in dx[n:]:
        a = (a * (n - 1) + x) / n
    return a


def bbw(c, n=20):
    def w(s):
        m = sum(s) / len(s)
        sd = math.sqrt(sum((x - m) ** 2 for x in s) / len(s))
        return 4 * sd / m if m else 0
    return w(c[-n:]), w(c[-n - 1:-1])


# ----------------------------------------------------------------------------- analysis (same as the app)
def analyse(k15, k5, k3, k1):
    c15, c5, c3, c1 = [x["c"] for x in k15], [x["c"] for x in k5], [x["c"] for x in k3], [x["c"] for x in k1]
    A = adx(k15, 14)
    bnow, bprev = bbw(c15, 20)
    at5 = atr(k5, 14)
    px = c1[-1]
    e20, e50, e5, e9_1, e9_3 = ema(c15, 20), ema(c15, 50), ema(c5, 20), ema(c1, 9), ema(c3, 9)
    a1 = atr(k1, 14)
    shock = any(abs(x["c"] - x["o"]) > 3 * a1 for x in k1[-5:]) if a1 else False
    regime = "WAIT" if shock else ("TREND" if A >= 25 and bnow > bprev else ("RANGE" if A <= 20 and bnow < 0.06 else "WAIT"))
    up15 = e20[-1] > e50[-1]
    side = "LONG" if up15 and c5[-1] > e5[-1] else ("SHORT" if (not up15) and c5[-1] < e5[-1] else "")
    r1 = rsi(c1, 14)
    stretch = abs(px - e5[-1]) / (at5 or 1)
    l = c1[-4:]
    turn_up = l[3] > l[2] >= l[1] and px > e9_1[-1] and c3[-1] > e9_3[-1]
    turn_dn = l[3] < l[2] <= l[1] and px < e9_1[-1] and c3[-1] < e9_3[-1]
    exhausted = stretch > 2.5 or (side == "LONG" and r1 > 75) or (side == "SHORT" and r1 < 25)
    go = regime == "TREND" and bool(side) and not exhausted and ((side == "LONG" and turn_up) or (side == "SHORT" and turn_dn))
    return {"px": px, "adx": A, "regime": regime, "side": side, "go": go, "at5": at5, "rsi1": r1,
            "why": "ADX %.0f, BB width %.2f%%, %s" % (A, bnow * 100, regime)}


def ladder(best_r, side_long, e, R, sl):
    """Return the new stop after the profit-lock ladder (never moves backwards)."""
    fee = e * FEE * 2
    n = None
    if best_r >= 3:
        n = e + (1 if side_long else -1) * (best_r - 0.7) * R
    elif best_r >= 2:
        n = e + (1 if side_long else -1) * (best_r - 1) * R
    elif best_r >= 1.5:
        n = e + (0.5 if side_long else -0.5) * R
    elif best_r >= 1:
        n = e + (fee if side_long else -fee)
    elif best_r >= 0.5:
        n = e - (0.5 if side_long else -0.5) * R
    if n is not None and ((side_long and n > sl) or ((not side_long) and n < sl)):
        return n
    return sl


# ----------------------------------------------------------------------------- exchange layer
class Market:
    def __init__(self, ex, key=None, secret=None, password=None, testnet=False):
        try:
            import ccxt  # noqa
        except ImportError:
            sys.exit("Please install ccxt first:  pip install ccxt")
        import ccxt
        ids = {"binance": "binanceusdm", "okx": "okx", "bitget": "bitget", "bybit": "bybit"}
        if ex not in ids:
            sys.exit("Exchange must be one of: binance, okx, bitget, bybit")
        cfg = {"enableRateLimit": True, "options": {"defaultType": "swap"}}
        if key:
            cfg.update({"apiKey": key, "secret": secret})
        if password:
            cfg["password"] = password
        self.x = getattr(ccxt, ids[ex])(cfg)
        if testnet:
            self.x.set_sandbox_mode(True)
        self.x.load_markets()
        self.ex = ex

    def sym(self, s):
        base = s.upper().replace("USDT", "")
        return "%s/USDT:USDT" % base

    def kl(self, s, tf, n):
        rows = self.x.fetch_ohlcv(self.sym(s), tf, limit=n)
        return [{"t": r[0], "o": r[1], "h": r[2], "l": r[3], "c": r[4], "v": r[5]} for r in rows]

    def price(self, s):
        t = self.x.fetch_ticker(self.sym(s))
        return float(t["last"])

    def top(self, n=24):
        ts = self.x.fetch_tickers()
        rows = [(k, v.get("quoteVolume") or 0) for k, v in ts.items() if k.endswith("/USDT:USDT")]
        rows.sort(key=lambda r: -r[1])
        return [k.split("/")[0] + "USDT" for k, _ in rows[:n]]

    def positions(self):
        out = []
        for p in self.x.fetch_positions():
            amt = float(p.get("contracts") or 0)
            if amt:
                out.append({"sym": p["symbol"].split("/")[0] + "USDT", "side": "LONG" if p.get("side") == "long" else "SHORT",
                            "entry": float(p.get("entryPrice") or 0), "qty": amt})
        return out

    def order(self, s, side_long, qty, reduce=False):
        params = {"reduceOnly": True} if reduce else {}
        q = float(self.x.amount_to_precision(self.sym(s), qty))
        return self.x.create_order(self.sym(s), "market", "buy" if side_long else "sell", q, None, params)

    def isolated(self, s, lev):
        try:
            self.x.set_margin_mode("isolated", self.sym(s))
        except Exception:
            pass
        try:
            self.x.set_leverage(lev, self.sym(s))
        except Exception:
            pass

    def cancel_all(self, s):
        try:
            self.x.cancel_all_orders(self.sym(s))
        except Exception:
            pass


# ----------------------------------------------------------------------------- the bot
class MSAATB:
    def __init__(self, m, a):
        self.m, self.a = m, a
        self.state_file = a.state
        self.D = {"cash": a.start, "start": a.start, "pos": [], "hist": [], "day": "", "day_start": a.start, "g": {}}
        if os.path.exists(self.state_file):
            self.D.update(json.load(open(self.state_file)))

    def save(self):
        json.dump(self.D, open(self.state_file, "w"), indent=1)

    def log(self, t):
        print(time.strftime("%H:%M:%S"), t, flush=True)

    def equity(self):
        u = 0.0
        for p in self.D["pos"]:
            try:
                px = self.m.price(p["sym"])
                u += (px - p["entry"] if p["side"] == "LONG" else p["entry"] - px) * p["qty"]
            except Exception:
                pass
        return self.D["cash"] + sum(p["margin"] for p in self.D["pos"]) + u

    def cooled(self, sym):
        now = time.time()
        L = [h["end"] for h in self.D["hist"] if h["sym"] == sym and h["pl"] < 0 and now - h["end"] < 48 * 3600]
        L.sort(reverse=True)
        if L and now - L[0] < 4 * 3600:
            return True
        for a, b in zip(L, L[1:]):
            if a - b < 24 * 3600 and now - a < 24 * 3600:
                return True
        return False

    def open(self, sym, an):
        eq = self.equity()
        if (len(self.D["pos"]) + 1) * self.a.risk > 3:
            return
        risk = eq * self.a.risk / 100
        stop = an["at5"] * 1.5
        if stop <= 0 or stop / an["px"] < 0.003:
            return
        qty = risk / stop
        L = an["side"] == "LONG"
        px = an["px"]
        notional = qty * px
        margin = notional / self.a.lev
        if margin > self.D["cash"] * 0.9:
            return
        if self.a.live:
            self.m.isolated(sym, self.a.lev)
            self.m.order(sym, L, qty)
        self.D["cash"] -= margin + notional * FEE
        p = {"id": "ab%d" % int(time.time() * 1000), "sym": sym, "side": an["side"], "entry": px, "qty": qty, "margin": margin,
             "R": stop, "sl": px - stop if L else px + stop, "best": 0, "at": time.time(), "live": bool(self.a.live)}
        self.D["pos"].append(p)
        self.log("OPEN %s %s @ %.6g stop %.6g (%s)" % (an["side"], sym, px, p["sl"], an["why"]))

    def close(self, p, why):
        if p not in self.D["pos"]:
            return
        px = self.m.price(p["sym"])
        if p.get("live") and self.a.live:
            self.m.order(p["sym"], p["side"] != "LONG", p["qty"], reduce=True)
        g = (px - p["entry"] if p["side"] == "LONG" else p["entry"] - px) * p["qty"] - p["qty"] * px * FEE
        self.D["cash"] += p["margin"] + g
        self.D["pos"].remove(p)
        self.D["hist"].insert(0, {"sym": p["sym"], "side": p["side"], "pl": g, "r": g / (p["R"] * p["qty"]), "end": time.time(), "why": why})
        self.log("CLOSE %s %s %+.2f USDT (%s)" % (p["side"], p["sym"], g, why))

    def manage(self):
        for p in list(self.D["pos"]):
            try:
                px = self.m.price(p["sym"])
            except Exception:
                continue
            L = p["side"] == "LONG"
            r = (px - p["entry"] if L else p["entry"] - px) / p["R"]
            p["best"] = max(p["best"], r)
            p["sl"] = ladder(p["best"], L, p["entry"], p["R"], p["sl"])
            if (L and px <= p["sl"]) or ((not L) and px >= p["sl"]):
                self.close(p, "profit lock" if p["best"] >= 1 else "stop-loss")
            elif time.time() - p["at"] > 3600 and r < 0.5 and p["best"] < 1:
                self.close(p, "time stop")

    def manage_manual(self):
        """FULL CONTROL of positions you opened yourself on the exchange."""
        if not (self.a.manage_manual and self.a.live):
            return
        mine = {p["sym"] for p in self.D["pos"]}
        for q in self.m.positions():
            if q["sym"] in mine:
                continue
            k = q["sym"] + ":" + q["side"]
            g = self.D["g"].get(k)
            px = self.m.price(q["sym"])
            L = q["side"] == "LONG"
            if not g:
                self.m.cancel_all(q["sym"])            # your own stop/target orders are replaced (full control)
                R = q["entry"] * 0.02
                sl = q["entry"] - R if L else q["entry"] + R
                if (L and px <= sl) or ((not L) and px >= sl):
                    sl = px - R if L else px + R
                g = self.D["g"][k] = {"e": q["entry"], "R": R, "sl": sl, "best": 0, "at": time.time()}
                self.log("TAKING CHARGE of your %s %s" % (q["side"], q["sym"]))
            r = (px - g["e"] if L else g["e"] - px) / g["R"]
            g["best"] = max(g["best"], r)
            g["sl"] = ladder(g["best"], L, g["e"], g["R"], g["sl"])
            why = ""
            if (L and px <= g["sl"]) or ((not L) and px >= g["sl"]):
                why = "stop / profit lock"
            elif time.time() - g["at"] > 3 * 3600 and g["best"] < 0.5:
                why = "time stop"
            else:
                try:
                    an = analyse(self.m.kl(q["sym"], "15m", 120), self.m.kl(q["sym"], "5m", 120), self.m.kl(q["sym"], "3m", 60), self.m.kl(q["sym"], "1m", 60))
                    if an["side"] and an["regime"] != "WAIT" and an["side"] != q["side"]:
                        why = "trend turned against the trade"
                    elif an["regime"] == "WAIT" and r < -0.3:
                        why = "no clear trend while losing"
                except Exception:
                    pass
            if why:
                self.m.order(q["sym"], not L, q["qty"], reduce=True)
                self.log("CLOSED your %s %s: %s" % (q["side"], q["sym"], why))
                self.D["g"].pop(k, None)

    def day_guard(self):
        d = time.strftime("%Y-%m-%d", time.gmtime())
        eq = self.equity()
        if self.D["day"] != d:
            self.D["day"], self.D["day_start"] = d, eq
        return (self.D["day_start"] - eq) / self.D["day_start"] * 100 < 3

    def scan(self):
        if not self.day_guard():
            self.log("Daily loss limit (3%) reached: no new trades today")
            return
        held = {p["sym"] for p in self.D["pos"]}
        for sym in (self.a.coins.split(",") if self.a.coins else self.m.top(24)):
            if len(self.D["pos"]) >= 5:
                break
            if sym in held or self.cooled(sym):
                continue
            try:
                an = analyse(self.m.kl(sym, "15m", 120), self.m.kl(sym, "5m", 120), self.m.kl(sym, "3m", 60), self.m.kl(sym, "1m", 60))
            except Exception as e:
                self.log("skip %s: %s" % (sym, e))
                continue
            if an["go"]:
                self.open(sym, an)
            time.sleep(0.25)

    def run(self):
        self.log("MSAATB %s on %s · %s · risk %.2f%% · leverage %dx" % (VERSION, self.m.ex, "LIVE" if self.a.live else "PAPER", self.a.risk, self.a.lev))
        last = 0
        while True:
            try:
                self.manage()
                self.manage_manual()
                if time.time() - last > 60:
                    last = time.time()
                    self.scan()
                self.save()
            except KeyboardInterrupt:
                self.save()
                raise
            except Exception as e:
                self.log("error: %s" % e)
            time.sleep(5)


def main():
    ap = argparse.ArgumentParser(description="MSAATB standalone engine (paper by default)")
    ap.add_argument("--exchange", default="binance", help="binance | okx | bitget | bybit")
    ap.add_argument("--risk", type=float, default=0.5, help="risk per trade in %% (0.25-1)")
    ap.add_argument("--lev", type=int, default=3, help="leverage (1-5)")
    ap.add_argument("--start", type=float, default=1000.0, help="paper starting balance in USDT")
    ap.add_argument("--coins", default="", help="comma list, e.g. BTCUSDT,ETHUSDT (default: top 24 by volume)")
    ap.add_argument("--state", default="msaatb_state.json")
    ap.add_argument("--live", action="store_true", help="send real orders (needs API keys in env MSAATB_KEY / MSAATB_SECRET / MSAATB_PASS)")
    ap.add_argument("--testnet", action="store_true")
    ap.add_argument("--manage-manual", action="store_true", help="FULL CONTROL of positions you open yourself (live only)")
    a = ap.parse_args()
    a.risk = max(0.25, min(1.0, a.risk))
    a.lev = max(1, min(5, a.lev))
    key, sec, pw = os.environ.get("MSAATB_KEY"), os.environ.get("MSAATB_SECRET"), os.environ.get("MSAATB_PASS")
    if a.live:
        if not (key and sec):
            sys.exit("Live mode needs MSAATB_KEY and MSAATB_SECRET in the environment (trading permission only, never withdrawals).")
        if input("Type YES to trade REAL money on %s: " % a.exchange).strip() != "YES":
            sys.exit("Cancelled.")
    MSAATB(Market(a.exchange, key, sec, pw, a.testnet), a).run()


if __name__ == "__main__":
    main()
