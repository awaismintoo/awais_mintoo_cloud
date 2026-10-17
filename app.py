import urllib.request
import json
import time
import threading
import subprocess
from flask import Flask, render_template_string, jsonify, request

app = Flask(__name__)

bot_status = "STOPPED"
bot_thread = None
scan_results = []
lock = threading.Lock()
alert_history = {}

def push_android_notification(title, content):
    try:
        subprocess.run([
            'termux-notification', 
            '--title', title, 
            '--content', content, 
            '--icon', 'robot',
            '--priority', 'high'
        ], check=False)
    except Exception as e:
        print(f"Notification dispatch error: {e}")

def fetch_binance_futures_tickers():
    try:
        url = "https://fapi.binance.com/fapi/v1/ticker/24hr"
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req, timeout=6) as response:
            data = json.loads(response.read().decode())
            # Target USDT perpetual contracts excluding leveraged tokens
            usdt_pairs = [item for item in data if item['symbol'].endswith('USDT') and 'UP' not in item['symbol'] and 'DOWN' not in item['symbol']]
            usdt_pairs.sort(key=lambda x: float(x.get('quoteVolume', 0)), reverse=True)
            return usdt_pairs[:25]
    except Exception as e:
        print(f"Ticker error: {e}")
        return []

def fetch_klines(symbol, interval, limit=10):
    try:
        url = f"https://fapi.binance.com/fapi/v1/klines?symbol={symbol}&interval={interval}&limit={limit}"
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req, timeout=5) as response:
            return json.loads(response.read().decode())
    except:
        return []

def sovereign_scanner_loop():
    global bot_status, scan_results, alert_history
    while bot_status == "RUNNING":
        try:
            tickers = fetch_binance_futures_tickers()
            temp_results = []
            
            for item in tickers:
                if bot_status != "RUNNING": 
                    break
                
                symbol = item['symbol']
                price = float(item.get('lastPrice', 0))
                if price == 0:
                    continue

                # 1. Check 3m Candle Momentum (Strict Filtering)
                k3m = fetch_klines(symbol, '3m', limit=5)
                change_3m = 0.0
                m_status = "NEUTRAL"
                
                if len(k3m) >= 2:
                    prev_c = float(k3m[-2][4])
                    curr_c = float(k3m[-1][4])
                    if prev_c > 0:
                        change_3m = round(((curr_c - prev_c) / prev_c) * 100, 2)
                    
                    if change_3m >= 0.4:
                        m_status = "BULLISH 🟢"
                    elif change_3m <= -0.4:
                        m_status = "BEARISH 🔴"

                # Filter out neutral coins: process only Bullish/Bearish 3m candidates
                if m_status == "NEUTRAL":
                    time.sleep(0.1)
                    continue

                # 2. Check 20m Candle with Continuous Movement Over Last 10 Minutes
                k20m = fetch_klines(symbol, '20m', limit=5)
                k10m = fetch_klines(symbol, '10m', limit=5)
                recommendation = "NEUTRAL 🟡"
                
                if len(k20m) >= 2 and len(k10m) >= 2:
                    open_20m = float(k20m[-1][1])
                    close_20m = float(k20m[-1][4])
                    close_10m_prev = float(k10m[-2][4])
                    close_10m_curr = float(k10m[-1][4])
                    
                    # Continuous upward trend in last 10 mins on a 20m candle
                    if close_20m > open_20m and close_10m_curr > close_10m_prev:
                        recommendation = "STRONG LONG 🚀"
                    # Continuous downward trend in last 10 mins on a 20m candle
                    elif close_20m < open_20m and close_10m_curr < close_10m_prev:
                        recommendation = "STRONG SHORT 📉"

                # Construct record
                record = {
                    "symbol": symbol,
                    "price": price,
                    "change_3m": change_3m,
                    "momentum": m_status,
                    "recommendation": recommendation
                }
                temp_results.append(record)

                # Send Android Push Notification on qualified signal
                history_key = f"{symbol}_{m_status}_{recommendation}"
                if alert_history.get(symbol) != history_key:
                    alert_history[symbol] = history_key
                    msg = f"Price: ${price} | 3m Shift: {change_3m}% | 20m Rec: {recommendation}"
                    push_android_notification(f"⚡ Sovereign Alert: {symbol}", msg)

                time.sleep(0.15)
                
            with lock:
                if bot_status == "RUNNING":
                    scan_results = temp_results
                    
        except Exception as e:
            print(f"Scanner exception: {e}")
        
        time.sleep(30)

@app.route('/')
def index():
    return render_template_string(HTML_PAGE)

@app.route('/api/status', methods=['GET'])
def get_status():
    global bot_status, scan_results
    with lock:
        return jsonify({"status": bot_status, "data": scan_results})

@app.route('/api/control', methods=['POST'])
def control_bot():
    global bot_status, bot_thread, scan_results
    action = request.json.get('action')
    if action == 'start' and bot_status != "RUNNING":
        bot_status = "RUNNING"
        bot_thread = threading.Thread(target=sovereign_scanner_loop, daemon=True)
        bot_thread.start()
        push_android_notification("Sovereign Bot", "Engine STARTED. Scanning Binance Futures...")
    elif action == 'stop':
        bot_status = "STOPPED"
        with lock:
            scan_results = []
        push_android_notification("Sovereign Bot", "Engine STOPPED. All alerts halted.")
    return jsonify({"status": bot_status})

HTML_PAGE = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Sovereign Futures Terminal</title>
    <link rel="manifest" href="/manifest.json">
    <link rel="apple-touch-icon" href="https://img.icons8.com/color/192/000000/bot.png">
    <style>
        body { background: #0b0e14; color: #e6edf3; font-family: -apple-system, sans-serif; margin: 0; padding: 15px; text-align: center; }
        .header { display: flex; align-items: center; justify-content: center; gap: 10px; margin-bottom: 15px; }
        .robot-icon { width: 48px; height: 48px; filter: drop-shadow(0 0 8px #00f2fe); }
        .controls { display: flex; gap: 10px; max-width: 400px; margin: 0 auto 15px auto; }
        button { flex: 1; padding: 12px; font-weight: bold; border: none; border-radius: 8px; cursor: pointer; font-size: 1rem; }
        .btn-start { background: #238636; color: #fff; }
        .btn-stop { background: #da3633; color: #fff; }
        .status-badge { font-weight: bold; padding: 6px 14px; border-radius: 20px; background: #21262d; display: inline-block; margin-bottom: 15px; font-size: 0.9rem; }
        .table-container { max-width: 600px; margin: 0 auto; overflow-x: auto; background: #161b22; border: 1px solid #30363d; border-radius: 12px; box-shadow: 0 8px 24px rgba(0,0,0,0.5); }
        table { width: 100%; border-collapse: collapse; text-align: left; font-size: 0.85rem; }
        th, td { padding: 10px 12px; border-bottom: 1px solid #21262d; }
        th { color: #8b949e; background: #0d1117; }
        .green { color: #3fb950; font-weight: bold; }
        .red { color: #f85149; font-weight: bold; }
    </style>
</head>
<body>
    <div class="header">
        <img src="https://img.icons8.com/color/192/000000/bot.png" class="robot-icon">
        <h2>Sovereign Futures Terminal</h2>
    </div>

    <div>
        <div id="statusBadge" class="status-badge" style="color: #f85149;">STATUS: STOPPED 🔴</div>
    </div>

    <div class="controls">
        <button class="btn-start" onclick="toggleBot('start')">START ENGINE</button>
        <button class="btn-stop" onclick="toggleBot('stop')">STOP ENGINE</button>
    </div>

    <div class="table-container">
        <table>
            <thead>
                <tr>
                    <th>Symbol</th>
                    <th>Price</th>
                    <th>3m State</th>
                    <th>20m Trend Setup</th>
                </tr>
            </thead>
            <tbody id="dataTable">
                <tr><td colspan="4" style="text-align:center; color:#8b949e;">Engine is stopped. Tap START ENGINE to scan active setup coins.</td></tr>
            </tbody>
        </table>
    </div>

    <script>
        async function toggleBot(action) {
            let res = await fetch('/api/control', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({action: action})
            });
            let data = await res.json();
            updateUIState(data.status);
            if (action === 'stop') {
                document.getElementById('dataTable').innerHTML = '<tr><td colspan="4" style="text-align:center; color:#8b949e;">Engine is stopped. All monitoring halted.</td></tr>';
            }
        }

        async function pollStatus() {
            let res = await fetch('/api/status');
            let json = await res.json();
            updateUIState(json.status);
            if(json.status === 'RUNNING') {
                if (json.data.length === 0) {
                    document.getElementById('dataTable').innerHTML = '<tr><td colspan="4" style="text-align:center; color:#58a6ff;">Scanning market for 3m Bullish/Bearish movers...</td></tr>';
                } else {
                    let html = '';
                    json.data.forEach(row => {
                        let mColor = row.change_3m >= 0 ? 'green' : 'red';
                        let rColor = row.recommendation.includes('LONG') ? 'green' : (row.recommendation.includes('SHORT') ? 'red' : '');
                        html += `<tr>
                            <td><b>${row.symbol}</b></td>
                            <td>$${row.price}</td>
                            <td class="${mColor}">${row.momentum} (${row.change_3m > 0 ? '+' : ''}${row.change_3m}%)</td>
                            <td class="${rColor}">${row.recommendation}</td>
                        </tr>`;
                    });
                    document.getElementById('dataTable').innerHTML = html;
                }
            }
        }

        function updateUIState(status) {
            let badge = document.getElementById('statusBadge');
            if(status === 'RUNNING') {
                badge.innerText = "STATUS: RUNNING 🟢";
                badge.style.color = "#3fb950";
            } else {
                badge.innerText = "STATUS: STOPPED 🔴";
                badge.style.color = "#f85149";
            }
        }

        setInterval(pollStatus, 3000);
        pollStatus();
    </script>
</body>
</html>
"""

MANIFEST = """{
  "short_name": "ProTrader",
  "name": "Sovereign Futures Terminal",
  "icons": [{ "src": "https://img.icons8.com/color/192/000000/bot.png", "type": "image/png", "sizes": "192x192" }],
  "start_url": "/",
  "background_color": "#0b0e14",
  "theme_color": "#0b0e14",
  "display": "standalone"
}"""

@app.route('/manifest.json')
def manifest():
    return app.response_class(MANIFEST, mimetype='application/json')

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000)
