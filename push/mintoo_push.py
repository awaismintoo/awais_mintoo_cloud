#!/usr/bin/env python3
"""MintooSuite closed-app alerts: runs on GitHub (free) every 15 minutes, looks at the biggest USDT futures coins,
and sends ONE short push to every phone that switched on 'Alerts when the app is closed'.
Rule ("Baba pick"): the UT Bot trailing stop (key 2, ATR 10) flips on the last CLOSED 15m candle AND price is on the
right side of the EMA 50. This is a statistical idea, not a promise of profit. No personal data is used or stored."""
import json, os, sys, time, urllib.request

# GitHub's servers are in the USA and Binance refuses them (HTTP 451), so the market data comes from the first source that answers.
COINS = 25
TOP = ['BTC', 'ETH', 'BNB', 'SOL', 'XRP', 'DOGE', 'ADA', 'TRX', 'AVAX', 'LINK', 'DOT', 'LTC', 'BCH', 'NEAR', 'UNI', 'ATOM', 'APT', 'ARB', 'OP', 'SUI', 'FIL', 'ETC', 'INJ', 'AAVE', 'TON']
SRC = None
MAX_AGE_MIN = 16
STABLE = ('USDC', 'FDUSD', 'TUSD', 'BUSD', 'USDP')


def get(url):
    req = urllib.request.Request(url, headers={'User-Agent': 'mintoosuite-push'})
    with urllib.request.urlopen(req, timeout=20) as r:
        return json.loads(r.read().decode())


def utbot(close, high, low, key=2.0, per=10):
    n = len(close)
    tr = [high[0] - low[0]] + [max(high[i] - low[i], abs(high[i] - close[i - 1]), abs(low[i] - close[i - 1])) for i in range(1, n)]
    atr = [None] * n
    if n <= per:
        return [], []
    a = sum(tr[:per]) / per
    atr[per - 1] = a
    for i in range(per, n):
        a = (a * (per - 1) + tr[i]) / per
        atr[i] = a
    stop = [None] * n
    pos = [0] * n
    st = None
    for i in range(per - 1, n):
        nl = key * atr[i]
        c = close[i]
        if st is None:
            st = c - nl
        else:
            pc = close[i - 1]
            if c > st and pc > st:
                st = max(st, c - nl)
            elif c < st and pc < st:
                st = min(st, c + nl)
            elif c > st:
                st = c - nl
            else:
                st = c + nl
        stop[i] = st
        if i > per - 1:
            pc = close[i - 1]
            ps = stop[i - 1]
            if c > st and pc <= ps:
                pos[i] = 1
            elif c < st and pc >= ps:
                pos[i] = -1
    return stop, pos


def ema(v, p):
    k = 2 / (p + 1)
    e = v[0]
    out = [e]
    for x in v[1:]:
        e = x * k + e * (1 - k)
        out.append(e)
    return out


def check(kl, now_ms):
    """kl = binance klines list; returns 'BUY' / 'SELL' / None for the last closed candle."""
    if len(kl) < 80:
        return None
    last = kl[-2] if now_ms < int(kl[-1][6]) else kl[-1]
    kl = kl[:kl.index(last) + 1]
    close = [float(k[4]) for k in kl]
    high = [float(k[2]) for k in kl]
    low = [float(k[3]) for k in kl]
    close_time = int(last[6])
    if now_ms - close_time > MAX_AGE_MIN * 60000:
        return None
    stop, pos = utbot(close, high, low)
    if not pos or pos[-1] == 0:
        return None
    e50 = ema(close, 50)[-1]
    if pos[-1] == 1 and close[-1] > e50:
        return 'BUY'
    if pos[-1] == -1 and close[-1] < e50:
        return 'SELL'
    return None


def pick_coins():
    return [c + 'USDT' for c in TOP[:COINS]]


def k_binance(sym):
    return get('https://data-api.binance.vision/api/v3/klines?symbol=%s&interval=15m&limit=120' % sym)


def k_okx(sym):
    d = get('https://www.okx.com/api/v5/market/candles?instId=%s-USDT-SWAP&bar=15m&limit=120' % sym[:-4])['data']
    out = []
    for r in reversed(d):
        t = int(r[0])
        out.append([t, r[1], r[2], r[3], r[4], r[5], t + 899999])
    return out


def k_gate(sym):
    d = get('https://api.gateio.ws/api/v4/futures/usdt/candlesticks?contract=%s_USDT&interval=15m&limit=120' % sym[:-4])
    out = []
    for r in d:
        t = int(r['t']) * 1000
        out.append([t, r['o'], r['h'], r['l'], r['c'], r.get('v', 0), t + 899999])
    return out


SOURCES = [('Binance public data', k_binance), ('OKX', k_okx), ('Gate.io', k_gate)]


def klines(sym):
    global SRC
    if SRC is None:
        for name, fn in SOURCES:
            try:
                k = fn('BTCUSDT')
                if len(k) > 80:
                    SRC = (name, fn)
                    print('market data from', name)
                    break
            except Exception as e:
                print('source', name, 'not available:', str(e)[:80])
        if SRC is None:
            raise RuntimeError('no market data source reachable from GitHub')
    return SRC[1](sym)


def send(sa, title, body, dry):
    if dry:
        print('DRY-RUN push:', title, '|', body)
        return True
    from google.oauth2 import service_account
    from google.auth.transport.requests import Request
    cred = service_account.Credentials.from_service_account_info(sa, scopes=['https://www.googleapis.com/auth/firebase.messaging'])
    cred.refresh(Request())
    msg = {'message': {'topic': 'picks', 'notification': {'title': title, 'body': body}, 'android': {'priority': 'HIGH', 'notification': {'channel_id': 'mintoo_push'}}}}
    req = urllib.request.Request('https://fcm.googleapis.com/v1/projects/%s/messages:send' % sa['project_id'], data=json.dumps(msg).encode(), headers={'Authorization': 'Bearer ' + cred.token, 'Content-Type': 'application/json'})
    with urllib.request.urlopen(req, timeout=20) as r:
        print('FCM answer', r.status)
    return True


def main():
    dry = '--dry' in sys.argv
    raw = os.environ.get('FCM_SERVICE_ACCOUNT', '').strip()
    if not raw and not dry:
        print('No FCM_SERVICE_ACCOUNT secret yet: nothing to send. (See the guide: add it in GitHub > Settings > Secrets.)')
        return 0
    sa = json.loads(raw) if raw else {}
    now = int(time.time() * 1000)
    hits = []
    for s in pick_coins():
        try:
            kl = klines(s)
            r = check(kl, now)
            if r:
                hits.append((s, r, float(kl[-2][4])))
        except Exception as e:
            print('skip', s, e)
    print('hits', hits)
    for s, r, p in hits[:3]:
        name = s[:-4]
        send(sa, 'Baba pick: %s %s' % (name, r), '15m signal on %s near %g. A statistical idea, not a promise: keep your stop. Open the app to see the plan.' % (name, p), dry)
    return 0


if __name__ == '__main__':
    sys.exit(main())
