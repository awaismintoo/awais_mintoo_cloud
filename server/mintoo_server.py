#!/usr/bin/env python3
# Copyright (c) 2026 Muhammad Awais. All rights reserved. MintooSuite and Baba Signal are original works of Muhammad Awais.
"""MintooSuite always-on alert server.
Runs all day on a small server. At every 15-minute candle close (+10 s) it checks the biggest USDT futures coins with the
SAME rule functions as mintoo_push.py (UT Bot flip + Baba early breakout), sends ONE push per signal, and remembers what it
already sent (sent.json) so nothing is ever sent twice, even after a restart. It does not need the phone, and it uses no personal data.
This is a statistical idea, not a promise of profit.   Run:  python3 mintoo_server.py [--dry] [--once]"""
import json, os, sys, time, threading, traceback
from concurrent.futures import ThreadPoolExecutor
import mintoo_push as P

HERE = os.path.dirname(os.path.abspath(__file__))
STATE = os.path.join(HERE, 'sent.json')
LOGF = os.path.join(HERE, 'signals.jsonl')
HEALTH = os.path.join(HERE, 'health.json')
COINS = int(os.environ.get('MW_COINS', '60'))
MAX_PUSH = int(os.environ.get('MW_MAX_PUSH', '3'))          # per bot, per candle
SA_FILE = os.environ.get('FCM_SERVICE_ACCOUNT_FILE', os.path.join(HERE, 'fcm_service_account.json'))
DRY = '--dry' in sys.argv
LOCK = threading.Lock()


def load_state():
    try:
        with open(STATE) as f:
            return json.load(f)
    except Exception:
        return {}


def save_state(st):
    now = time.time()
    st = {k: v for k, v in st.items() if now - v < 3 * 86400}
    tmp = STATE + '.tmp'
    with open(tmp, 'w') as f:
        json.dump(st, f)
    os.replace(tmp, STATE)
    return st


def universe():
    """Top USDT perpetual coins by 24h turnover (OKX public list); falls back to the fixed list."""
    try:
        d = P.get('https://www.okx.com/api/v5/market/tickers?instType=SWAP')['data']
        rows = []
        for r in d:
            iid = r.get('instId', '')
            if iid.endswith('-USDT-SWAP'):
                try:
                    rows.append((float(r['volCcy24h']) * float(r['last']), iid.split('-')[0]))
                except Exception:
                    pass
        rows.sort(reverse=True)
        names = [n + 'USDT' for _, n in rows if n not in ('USDC', 'FDUSD', 'TUSD', 'BUSD', 'USDP')]
        if len(names) >= 20:
            return names[:COINS]
    except Exception as e:
        print('universe fallback:', str(e)[:80])
    return P.pick_coins()


def one(sym, now_ms):
    for attempt in (0, 1):
        try:
            kl = P.klines(sym)
            out = []
            r = P.check(kl, now_ms)
            last = kl[-2] if now_ms < int(kl[-1][6]) else kl[-1]
            if r:
                out.append(('ut', sym, r, float(last[4]), int(last[6])))
            b = P.baba_signal(kl, now_ms)
            if b:
                out.append(('baba', sym, b, float(last[4]), int(last[6])))
            return out, True
        except Exception as e:
            if attempt:
                return [], False
            time.sleep(1.5)
    return [], False


def cycle(sa):
    now = int(time.time() * 1000)
    syms = universe()
    ok = fail = 0
    hits = []
    with ThreadPoolExecutor(max_workers=6) as ex:
        for out, good in ex.map(lambda s: one(s, now), syms):
            if good:
                ok += 1
            else:
                fail += 1
            hits += out
    st = load_state()
    sent = {'ut': 0, 'baba': 0}
    for bot, sym, side, price, close in sorted(hits, key=lambda h: h[0] != 'baba'):
        key = '%s|%s|%s|%d' % (bot, sym, side, close)
        if key in st:
            continue
        st[key] = time.time()
        with open(LOGF, 'a') as f:
            f.write(json.dumps({'t': close, 'bot': bot, 'sym': sym, 'side': side, 'price': price}) + '\n')
        if sent[bot] >= MAX_PUSH:
            continue
        name = sym[:-4]
        word = 'LONG' if side == 'BUY' else 'SHORT'
        try:
            if bot == 'baba':
                P.send(sa, 'Baba Signal: %s %s' % (name, word), 'Early breakout on %s near %g with strong volume. A statistical idea, not a promise: use a stop. Open the app to check it before acting.' % (name, price), DRY)
            else:
                P.send(sa, 'UT Bot: %s %s' % (name, word), '15m UT Bot flip on %s near %g. A statistical idea, not a promise: keep your stop. Open the app to see the plan.' % (name, price), DRY)
            sent[bot] += 1
        except Exception as e:
            print('push failed:', str(e)[:120])
            st.pop(key, None)
    save_state(st)
    with open(HEALTH, 'w') as f:
        json.dump({'at': int(time.time()), 'coins_ok': ok, 'coins_failed': fail, 'signals': len(hits), 'pushed': sent, 'source': (P.SRC[0] if P.SRC else None)}, f)
    print(time.strftime('%H:%M:%S'), 'checked', ok, 'failed', fail, 'signals', len(hits), 'pushed', sent, flush=True)
    return ok


def main():
    sa = {}
    if not DRY:
        try:
            with open(SA_FILE) as f:
                sa = json.load(f)
        except Exception:
            print('No service-account file at', SA_FILE, '- see README_SERVER.md. Use --dry to test without sending.')
            return 1
    if '--once' in sys.argv:
        return 0 if cycle(sa) else 2
    while True:
        try:
            nxt = (int(time.time()) // 900 + 1) * 900 + 10
            time.sleep(max(1, nxt - time.time()))
            if cycle(sa) < 5:
                P.SRC = None          # data source trouble: re-pick it and try once more in a minute
                time.sleep(60)
                cycle(sa)
        except KeyboardInterrupt:
            return 0
        except Exception:
            traceback.print_exc()
            time.sleep(30)


if __name__ == '__main__':
    sys.exit(main())
