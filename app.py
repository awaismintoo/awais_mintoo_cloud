from flask import Flask, render_template_string, jsonify
import random

app = Flask(__name__)

CLOUD_UI = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="google-site-verification" content="_xIb98t7daOeEIngaxR0jEUcGENU2HS8WIjrsiq4-ic" />
    <title>Awais Mintoo & Co. | Quantum Sovereign Suite</title>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <style>
        :root {
            --bg: #07090e;
            --card: rgba(17, 24, 39, 0.95);
            --blue: #3b82f6;
            --green: #10b981;
            --gold: #f59e0b;
            --red: #ef4444;
            --text: #ffffff;
            --sub: #9ca3af;
        }
        * { box-sizing: border-box; }
        body { background: var(--bg); color: var(--text); font-family: sans-serif; margin: 0; padding: 14px; }
        .header { background: linear-gradient(135deg, #1e293b, #0f172a); border: 1px solid rgba(59,130,246,0.3); border-radius: 16px; padding: 16px; text-align: center; margin-bottom: 14px; box-shadow: 0 4px 20px rgba(0,0,0,0.5); }
        .header h1 { color: #60a5fa; font-size: 18px; margin: 0 0 4px 0; text-transform: uppercase; letter-spacing: 1px; }
        .header p { color: var(--gold); font-size: 9px; margin: 0; text-transform: uppercase; letter-spacing: 2px; }
        
        .stream-box { background: #0d1117; border: 1px solid rgba(59,130,246,0.3); border-radius: 12px; padding: 12px; margin-bottom: 14px; }
        .chart { display: flex; align-items: flex-end; gap: 4px; height: 55px; padding-top: 5px; border-bottom: 1px solid rgba(255,255,255,0.1); }
        .candle { flex: 1; background: var(--green); border-radius: 2px; height: 50%; transition: height 0.3s ease; }
        .candle.red { background: var(--red); }

        .card { background: var(--card); border: 1px solid rgba(255,255,255,0.08); border-radius: 16px; padding: 16px; margin-bottom: 14px; position: relative; overflow: hidden; box-shadow: 0 8px 25px rgba(0,0,0,0.4); }
        .card::before { content: ''; position: absolute; top:0; left:0; width:5px; height:100%; background: var(--blue); }
        .card.gold::before { background: var(--gold); }

        .row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
        .badge { font-size: 9px; padding: 3px 10px; border-radius: 20px; background: rgba(156,163,175,0.15); color: var(--sub); text-transform: uppercase; font-weight: bold; }
        .badge.active { background: rgba(16,185,129,0.2); color: var(--green); border: 1px solid rgba(16,185,129,0.4); }

        .pnl-box { background: rgba(0,0,0,0.5); border-radius: 10px; padding: 10px 14px; display: flex; justify-content: space-between; align-items: center; margin: 12px 0; font-family: monospace; }
        
        .btn { width: 100%; padding: 14px; border-radius: 10px; font-weight: 800; font-size: 13px; border: none; cursor: pointer; text-transform: uppercase; letter-spacing: 1px; margin-top: 8px; color: #fff; }
        .btn-start { background: linear-gradient(135deg, #10b981, #059669); }
        .btn-stop { background: linear-gradient(135deg, #ef4444, #dc2626); }
    </style>
</head>
<body>
    <div class="header">
        <h1>Awais Mintoo & Co.</h1>
        <p>Quantum Sovereign Cloud Suite</p>
    </div>

    <div class="stream-box">
        <div style="display:flex; justify-content:space-between; font-size:11px; margin-bottom:8px;">
            <span style="color:#60a5fa; font-weight:bold;">CLOUD FEED: SECURE WS</span>
            <span style="color:var(--green); font-weight:bold; font-size:9px;">● CONNECTED</span>
        </div>
        <div class="chart" id="candlesticks">
            <div class="candle" style="height: 60%;"></div>
            <div class="candle red" style="height: 40%;"></div>
            <div class="candle" style="height: 75%;"></div>
            <div class="candle red" style="height: 45%;"></div>
            <div class="candle" style="height: 85%;"></div>
            <div class="candle" style="height: 65%;"></div>
        </div>
        <div style="display:flex; justify-content:space-between; font-size:10px; color:var(--sub); margin-top:6px; font-family:monospace;">
            <span>BTC/USDT: <strong id="b-val" style="color:var(--green);">$68,420</strong></span>
            <span>XAU/USD: <strong id="g-val" style="color:var(--gold);">$2,654.50</strong></span>
        </div>
    </div>

    <div class="card">
        <div class="row">
            <span style="font-size:15px; font-weight:800;">Binance Institutional Engine</span>
            <span class="badge" id="b-stat">Standby</span>
        </div>
        <div style="font-size:12px; color:var(--sub);">Protocol: <span style="color:#fff; font-family:monospace;">Neural Arbitrage v2.4</span></div>
        <div class="pnl-box">
            <span style="font-size:10px; color:var(--sub);">Live PnL Yield</span>
            <span id="b-pnl" style="font-size:14px; font-weight:bold; color:var(--green);">$0.00</span>
        </div>
        <button class="btn btn-start" onclick="toggleTrade('b', 'start')">Start Automated Trading</button>
        <button class="btn btn-stop" onclick="toggleTrade('b', 'stop')">Stop & Secure Capital</button>
    </div>

    <div class="card gold">
        <div class="row">
            <span style="font-size:15px; font-weight:800; color:var(--gold);">Exness Sovereign Gold</span>
            <span class="badge" id="e-stat">Standby</span>
        </div>
        <div style="font-size:12px; color:var(--sub);">Protocol: <span style="color:#fff; font-family:monospace;">XAU/USD Margin Grid</span></div>
        <div class="pnl-box">
            <span style="font-size:10px; color:var(--sub);">Live PnL Yield</span>
            <span id="e-pnl" style="font-size:14px; font-weight:bold; color:var(--gold);">$0.00</span>
        </div>
        <button class="btn btn-start" onclick="toggleTrade('e', 'start')">Start Automated Trading</button>
        <button class="btn btn-stop" onclick="toggleTrade('e', 'stop')">Stop & Secure Capital</button>
    </div>

    <script>
        let running = false;
        function toggleTrade(engine, action) {
            const statusBadge = document.getElementById(engine + '-stat');
            if(action === 'start') {
                running = true;
                statusBadge.innerText = 'Active';
                statusBadge.classList.add('active');
                alert('Awais Mintoo & Co.: Automated Cloud Protocol Activated.');
            } else {
                running = false;
                statusBadge.innerText = 'Standby';
                statusBadge.classList.remove('active');
                document.getElementById(engine + '-pnl').innerText = '$0.00 (Secured)';
                alert('Awais Mintoo & Co.: Protocol Halted & Capital Secured.');
            }
        }

        setInterval(async () => {
            try {
                let res = await fetch('/api/telemetry');
                let data = await res.json();
                document.getElementById('b-val').innerText = '$' + data.btc.toLocaleString();
                document.getElementById('g-val').innerText = '$' + data.gold.toFixed(2);
                
                if(running) {
                    document.getElementById('b-pnl').innerText = '+$' + (150 + Math.random()*35).toFixed(2);
                    document.getElementById('e-pnl').innerText = '+$' + (420 + Math.random()*50).toFixed(2);
                }
            } catch(e) { console.log('Telemetry sync error'); }
        }, 1200);
    </script>
</body>
</html>
"""

@app.route('/')
def index():
    return render_template_string(CLOUD_UI)

@app.route('/api/telemetry')
def telemetry():
    return jsonify({
        "btc": 68400 + random.randint(1, 80),
        "gold": 2650.0 + random.random() * 8.0
    })

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000)
