import urllib.request
import json
import time
import subprocess

def send_android_notification(title, content):
    try:
        subprocess.run([
            'termux-notification', 
            '--title', title, 
            '--content', content, 
            '--icon', 'robot',
            '--priority', 'high',
            '--sound'
        ], check=False)
    except Exception as e:
        print(f"Notification error: {e}")

def fetch_binance_futures():
    url = "https://fapi.binance.com/fapi/v1/ticker/24hr"
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req, timeout=5) as resp:
            data = json.loads(resp.read().decode())
            return [item['symbol'] for item in data if item['symbol'].endswith('USDT') and 'UP' not in item['symbol'] and 'DOWN' not in item['symbol']]
    except:
        return []

def get_klines(symbol, interval, limit=5):
    url = f"https://fapi.binance.com/fapi/v1/klines?symbol={symbol}&interval={interval}&limit={limit}"
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req, timeout=5) as resp:
            return json.loads(resp.read().decode())
    except:
        return []

def monitor_market():
    print("Local Android Notification Monitor Active...")
    alerted_cache = {}
    
    while True:
        symbols = fetch_binance_futures()
        for symbol in symbols[:30]:
            try:
                # 1. Check 3m Candle Momentum
                k3m = get_klines(symbol, '3m', limit=3)
                if len(k3m) >= 2:
                    prev_close = float(k3m[-2][4])
                    curr_close = float(k3m[-1][4])
                    pct_change = ((curr_close - prev_close) / prev_close) * 100
                    
                    if pct_change >= 0.6 and alerted_cache.get(symbol) != '3M_BULL':
                        alerted_cache[symbol] = '3M_BULL'
                        send_android_notification(f"🚀 3M BULLISH: {symbol}", f"Price: ${curr_close} | Spike: +{pct_change:.2f}%")
                    elif pct_change <= -0.6 and alerted_cache.get(symbol) != '3M_BEAR':
                        alerted_cache[symbol] = '3M_BEAR'
                        send_android_notification(f"📉 3M BEARISH: {symbol}", f"Price: ${curr_close} | Drop: {pct_change:.2f}%")

                # 2. Check Continuous Trend on 10m Candles
                k10m = get_klines(symbol, '10m', limit=3)
                if len(k10m) >= 3:
                    c1 = float(k10m[-3][4])
                    c2 = float(k10m[-2][4])
                    c3 = float(k10m[-1][4])
                    
                    if c3 > c2 > c1 and alerted_cache.get(f"{symbol}_10m") != 'UP':
                        alerted_cache[f"{symbol}_10m"] = 'UP'
                        send_android_notification(f"📈 10M UPTREND: {symbol}", f"Continuous upward candles. Price: ${c3}")
                    elif c3 < c2 < c1 and alerted_cache.get(f"{symbol}_10m") != 'DOWN':
                        alerted_cache[f"{symbol}_10m"] = 'DOWN'
                        send_android_notification(f"📉 10M DOWNTREND: {symbol}", f"Continuous downward candles. Price: ${c3}")
                
                time.sleep(0.2)
            except Exception:
                continue
        time.sleep(60)

if __name__ == '__main__':
    monitor_market()
