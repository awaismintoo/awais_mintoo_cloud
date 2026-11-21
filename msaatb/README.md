# MSAATB package (MintooSuite Auto AI Trading Bot) – version 536.1

The same trading rules as the MSAATB inside the MintooSuite app, as three parts you can use outside the app:

| File | Use it for | Markets |
|---|---|---|
| `MSAATB_EA.mq5` | MetaTrader 5 Expert Advisor; sizes in **lots** | Exness and any MT5 broker: gold (XAUUSD), forex, indices, crypto CFDs |
| `msaatb_engine.py` | Runs on a PC, server or Termux; paper by default | Binance, OKX, Bitget, Bybit USDT futures |
| `msaatb_core.js` | Library: put MSAATB's decisions into **any** trading app (Node.js or browser) | Any market where you have candles |

## The rules (same everywhere)
- Trades only when the 15-minute trend is strong (ADX ≥ 25 and Bollinger width rising); otherwise **WAIT**.
- Direction from EMA20/EMA50 (15 m) and the 5 m close; enters on a 1 m + 3 m turn back with the trend, never when stretched.
- Stop 1.5 × ATR(5 m). Size = risk % ÷ stop (0.25–1 %, default 0.5 %).
- Profit lock: +0.5R → stop −0.5R, +1R → break-even, +1.5R → +0.5R, +2R trail 1R, +3R trail 0.7R. Time stop after 60 min below +0.5R.
- Total open risk ≤ 3 %, daily loss stop 3 %, max 5 trades, 4 h rest after a loss, 24 h after two losses in a day.
- **Full control** of trades you open by hand (EA: `FullControl=true`; Python: `--manage-manual`): MSAATB replaces your own stop/target, moves the stop with the same ladder, uses the time stop, and closes at once if its own analysis turns against the trade. You can always close any trade yourself.

## 1) Exness / MetaTrader 5 (lots)
1. In MT5: File › Open Data Folder › `MQL5/Experts`, copy `MSAATB_EA.mq5` there.
2. Open MetaEditor, open the file, press **Compile**.
3. Drag **MSAATB_EA** onto a chart (for example XAUUSD, M1–M15 any), tick *Allow Algo Trading*.
4. Test first: Strategy Tester (Every tick based on real ticks) and a **demo** account for several weeks.
MT5 must stay running (PC or a VPS). The Exness mobile app cannot run Expert Advisors.

## 2) Binance / OKX / Bitget / Bybit (Python)
```
pip install ccxt
python msaatb_engine.py --exchange binance            # paper money, no orders
python msaatb_engine.py --exchange bybit --testnet --live
export MSAATB_KEY=... MSAATB_SECRET=... [MSAATB_PASS=... for OKX/Bitget]
python msaatb_engine.py --exchange okx --live --manage-manual
```
API keys: **trading permission only, never withdrawals**, IP-restricted if the exchange allows it. Live mode asks you to type YES.

## 3) Other trading apps (library)
`msaatb_core.js` gives `analyse()`, `ladder()`, `size()`, `timeStop()` and `cooldown()`. Your app supplies candles and sends orders with its own broker connection.

## What it does NOT do (honest limits)
- **Quotex and other binary-option platforms are not supported.** They have no official trading API, and binary options pay a fixed amount whatever the move, so a stop-loss/profit-lock bot cannot work there; many regulators also warn against them.
- No bot can promise to beat Pionex, 3Commas or any other bot, or to make a profit. MSAATB's rules are strict and tested, but only your own paper/demo record shows how it does in today's market. Compare it fairly: same coins, same period, same risk, after fees.
- This is software, not a licensed adviser or a registered financial product. Use small amounts you can afford to lose.

© MintooSuite. Personal use. Not financial advice.
