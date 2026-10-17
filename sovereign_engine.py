import urllib.request
import json
import time
import subprocess
from concurrent.futures import ThreadPoolExecutor

MAX_WORKERS = 25
REQ_DELAY = 0.008
alert_cache = {}

def trigger_alert(title, message):
    # Print high-visibility alert directly to the active terminal window
    print(f"\n\033[1;31m>>> {title} <<<\033[0m")
    print(f"\033[1;33m{message}\033[0m\n")
    try:
        # Audible terminal bell / vibration fallback
        subprocess.run(['termux-vibrate', '-d', '250'], check=False)
    except Exception:
        pass

def api_fetch(url):
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req, timeout=3) as response:
            return json.loads(response.read().decode())
    except Exception:
        return None

def fetch_all_futures_symbols():
    url = "https://fapi.binance.com/fapi/v1/ticker/24hr"
    data = api_fetch(url)
    if not data:
        return []
    
    return [
        item['symbol'] for item in data 
        if item['symbol'].endswith('USDT') 
        and 'UP' not in item['symbol'] 
        and 'DOWN' not in item['symbol']
    ]

def analyze_coin(symbol):
    try:
        time.sleep(REQ_DELAY)
        
        # 1. Check 3m Candle Shift (>= +/- 0.5%)
        url_3m = f"https://fapi.binance.com/fapi/v1/klines?symbol={symbol}&interval=3m&limit=3"
        k3m = api_fetch(url_3m)
        if k3m and len(k3m) >= 2:
            prev_close = float(k3m[-2][4])
            curr_close = float(k3m[-1][4])
            if prev_close > 0:
                pct_change = ((curr_close - prev_close) / prev_close) * 100
                
                if pct_change >= 0.5 and alert_cache.get(f"{symbol}_3M") != 'BULL':
                    alert_cache[f"{symbol}_3M"] = 'BULL'
                    trigger_alert(f"🚀 3M BULLISH: {symbol}", f"Price: ${curr_close} | Spike: +{pct_change:.2f}%")
                elif pct_change <= -0.5 and alert_cache.get(f"{symbol}_3M") != 'BEAR':
                    alert_cache[f"{symbol}_3M"] = 'BEAR'
                    trigger_alert(f"📉 3M BEARISH: {symbol}", f"Price: ${curr_close} | Drop: {pct_change:.2f}%")

        # 2. Check 10m Continuous Trend
        url_10m = f"https://fapi.binance.com/fapi/v1/klines?symbol={symbol}&interval=10m&limit=3"
        k10m = api_fetch(url_10m)
        if k10m and len(k10m) >= 3:
            c1 = float(k10m[-3][4])
            c2 = float(k10m[-2][4])
            c3 = float(k10m[-1][4])
            
            if c3 > c2 > c1 and alert_cache.get(f"{symbol}_10M") != 'UP':
                alert_cache[f"{symbol}_10M"] = 'UP'
                trigger_alert(f"📈 10M UPTREND: {symbol}", f"Continuous growth. Price: ${c3}")
            elif c3 < c2 < c1 and alert_cache.get(f"{symbol}_10M") != 'DOWN':
                alert_cache[f"{symbol}_10M"] = 'DOWN'
                trigger_alert(f"📉 10M DOWNTREND: {symbol}", f"Continuous decline. Price: ${c3}")

    except Exception:
        pass

def run_high_speed_scan():
    symbols = fetch_all_futures_symbols()
    if not symbols:
        return
        
    with ThreadPoolExecutor(max_workers=MAX_WORKERS) as executor:
        executor.map(analyze_coin, symbols)

if __name__ == '__main__':
    print("[+] Sovereign Terminal Direct-Alert Engine Active (25 Threads)...")
    
    while True:
        start_t = time.time()
        run_high_speed_scan()
        elapsed = time.time() - start_t
        print(f"[✓] Sweep completed in {elapsed:.2f}s. Restarting instantly...")
        time.sleep(2)
