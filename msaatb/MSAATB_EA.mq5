//+------------------------------------------------------------------+
//| MSAATB_EA.mq5 - MintooSuite Auto AI Trading Bot for MetaTrader 5   |
//| Works with any MT5 broker, for example Exness (gold XAUUSD, forex, |
//| indices, crypto CFDs). Sizes are in LOTS from your risk %.         |
//|                                                                    |
//| Same rules as the MSAATB in the MintooSuite app:                   |
//|  - Regime (M15): TREND when ADX(14) >= 25 and Bollinger width rises |
//|  - Direction: EMA20 vs EMA50 on M15 and close vs EMA20 on M5        |
//|  - Entry when M1 and M3 turn back with the trend, not stretched     |
//|  - Stop 1.5 x ATR(14) on M5; lots = risk% of equity / stop value    |
//|  - Ladder: +0.5R -> -0.5R, +1R -> break-even, +1.5R -> +0.5R,        |
//|            +2R trail 1R, +3R trail 0.7R; time stop 60 min           |
//|  - Max open risk 3%, daily loss stop 3%, 4 h rest after a loss,     |
//|    24 h after two losses in a day, spread filter                    |
//|  - FullControl: also manages trades you open by hand (same ladder,  |
//|    and closes them if the trend turns against them)                 |
//| No EA can promise profit. Test in the Strategy Tester and on a demo |
//| account for weeks first. Educational software, not advice.          |
//+------------------------------------------------------------------+
#property copyright "MintooSuite"
#property version   "5.36"
#property strict

#include <Trade/Trade.mqh>

input double RiskPercent    = 0.5;    // risk per trade, % of equity (0.25 - 1)
input double MaxOpenRiskPct = 3.0;    // total open risk cap, %
input double DailyLossPct   = 3.0;    // stop new trades after this daily loss, %
input int    MaxTrades      = 3;      // max trades opened by this EA
input double MaxSpreadATR   = 0.15;   // skip if spread > this part of ATR(M5)
input bool   FullControl    = true;   // also manage trades you open by hand
input int    TimeStopMin    = 60;     // close if below +0.5R after this many minutes (never reached +1R)
input long   Magic          = 536536; // EA id

CTrade trade;
int hADX, hBB, hATR5, hE20, hE50, hE5, hE9_1, hE9_3, hRSI1, hATR1;
double dayStartEq = 0; int dayNum = -1;
datetime lossT1 = 0, lossT2 = 0;

struct Track { ulong ticket; double e; double R; double sl; double best; datetime at; };
Track T[64]; int nT = 0;

double Buf(int h, int buf, int shift) { double v[]; if (CopyBuffer(h, buf, shift, 1, v) != 1) return 0; return v[0]; }

int OnInit()
{
   hADX  = iADX(_Symbol, PERIOD_M15, 14);
   hBB   = iBands(_Symbol, PERIOD_M15, 20, 0, 2.0, PRICE_CLOSE);
   hATR5 = iATR(_Symbol, PERIOD_M5, 14);
   hATR1 = iATR(_Symbol, PERIOD_M1, 14);
   hE20  = iMA(_Symbol, PERIOD_M15, 20, 0, MODE_EMA, PRICE_CLOSE);
   hE50  = iMA(_Symbol, PERIOD_M15, 50, 0, MODE_EMA, PRICE_CLOSE);
   hE5   = iMA(_Symbol, PERIOD_M5, 20, 0, MODE_EMA, PRICE_CLOSE);
   hE9_1 = iMA(_Symbol, PERIOD_M1, 9, 0, MODE_EMA, PRICE_CLOSE);
   hE9_3 = iMA(_Symbol, PERIOD_M3, 9, 0, MODE_EMA, PRICE_CLOSE);
   hRSI1 = iRSI(_Symbol, PERIOD_M1, 14, PRICE_CLOSE);
   if (hADX == INVALID_HANDLE || hBB == INVALID_HANDLE || hATR5 == INVALID_HANDLE) return INIT_FAILED;
   trade.SetExpertMagicNumber(Magic);
   EventSetTimer(5);
   return INIT_SUCCEEDED;
}
void OnDeinit(const int r) { EventKillTimer(); }

double BBWidth(int shift)
{
   double up = Buf(hBB, 1, shift), lo = Buf(hBB, 2, shift), mid = Buf(hBB, 0, shift);
   return mid > 0 ? (up - lo) / mid : 0;
}

// -1 short, +1 long, 0 wait; also returns regime trend flag
int Analyse(bool &trend, bool &go)
{
   double A = Buf(hADX, 0, 1);
   double w0 = BBWidth(1), w1 = BBWidth(2);
   double a1 = Buf(hATR1, 0, 1);
   bool shock = false;
   for (int i = 1; i <= 5; i++) { double o = iOpen(_Symbol, PERIOD_M1, i), c = iClose(_Symbol, PERIOD_M1, i); if (a1 > 0 && MathAbs(c - o) > 3 * a1) shock = true; }
   trend = !shock && A >= 25 && w0 > w1;
   bool up15 = Buf(hE20, 0, 1) > Buf(hE50, 0, 1);
   double c5 = iClose(_Symbol, PERIOD_M5, 1), e5 = Buf(hE5, 0, 1);
   int side = (up15 && c5 > e5) ? 1 : ((!up15 && c5 < e5) ? -1 : 0);
   double px = SymbolInfoDouble(_Symbol, SYMBOL_BID), at5 = Buf(hATR5, 0, 1), r1 = Buf(hRSI1, 0, 1);
   double stretch = at5 > 0 ? MathAbs(px - e5) / at5 : 0;
   double c1a = iClose(_Symbol, PERIOD_M1, 3), c1b = iClose(_Symbol, PERIOD_M1, 2), c1c = iClose(_Symbol, PERIOD_M1, 1);
   double c3 = iClose(_Symbol, PERIOD_M3, 1);
   bool turnUp = c1c > c1b && c1b >= c1a && px > Buf(hE9_1, 0, 1) && c3 > Buf(hE9_3, 0, 1);
   bool turnDn = c1c < c1b && c1b <= c1a && px < Buf(hE9_1, 0, 1) && c3 < Buf(hE9_3, 0, 1);
   bool exhausted = stretch > 2.5 || (side == 1 && r1 > 75) || (side == -1 && r1 < 25);
   go = trend && side != 0 && !exhausted && ((side == 1 && turnUp) || (side == -1 && turnDn));
   return side;
}

double LotsFor(double stopDist)
{
   double eq = AccountInfoDouble(ACCOUNT_EQUITY), riskMoney = eq * MathMax(0.25, MathMin(1.0, RiskPercent)) / 100.0;
   double tv = SymbolInfoDouble(_Symbol, SYMBOL_TRADE_TICK_VALUE), ts = SymbolInfoDouble(_Symbol, SYMBOL_TRADE_TICK_SIZE);
   if (tv <= 0 || ts <= 0 || stopDist <= 0) return 0;
   double perLot = stopDist / ts * tv, lots = riskMoney / perLot;
   double step = SymbolInfoDouble(_Symbol, SYMBOL_VOLUME_STEP), mn = SymbolInfoDouble(_Symbol, SYMBOL_VOLUME_MIN), mx = SymbolInfoDouble(_Symbol, SYMBOL_VOLUME_MAX);
   lots = MathFloor(lots / step) * step;
   if (lots < mn) return 0;   // the account is too small for this stop: skip instead of over-risking
   return MathMin(lots, mx);
}

int Find(ulong tk) { for (int i = 0; i < nT; i++) if (T[i].ticket == tk) return i; return -1; }

bool Cooling()
{
   datetime now = TimeCurrent();
   if (lossT1 > 0 && now - lossT1 < 4 * 3600) return true;
   if (lossT1 > 0 && lossT2 > 0 && lossT1 - lossT2 < 24 * 3600 && now - lossT1 < 24 * 3600) return true;
   return false;
}

void OnTradeTransaction(const MqlTradeTransaction &t, const MqlTradeRequest &rq, const MqlTradeResult &rs)
{
   if (t.type != TRADE_TRANSACTION_DEAL_ADD) return;
   if (!HistoryDealSelect(t.deal)) return;
   if (HistoryDealGetString(t.deal, DEAL_SYMBOL) != _Symbol) return;
   if (HistoryDealGetInteger(t.deal, DEAL_ENTRY) != DEAL_ENTRY_OUT) return;
   double pl = HistoryDealGetDouble(t.deal, DEAL_PROFIT) + HistoryDealGetDouble(t.deal, DEAL_COMMISSION) + HistoryDealGetDouble(t.deal, DEAL_SWAP);
   if (pl < 0) { lossT2 = lossT1; lossT1 = TimeCurrent(); }
}

void Manage()
{
   bool trend, go; int view = Analyse(trend, go);
   double openRisk = 0;
   for (int i = PositionsTotal() - 1; i >= 0; i--)
   {
      ulong tk = PositionGetTicket(i); if (!PositionSelectByTicket(tk)) continue;
      if (PositionGetString(POSITION_SYMBOL) != _Symbol) continue;
      bool mine = PositionGetInteger(POSITION_MAGIC) == Magic;
      if (!mine && !FullControl) continue;
      bool L = PositionGetInteger(POSITION_TYPE) == POSITION_TYPE_BUY;
      double e = PositionGetDouble(POSITION_PRICE_OPEN), px = L ? SymbolInfoDouble(_Symbol, SYMBOL_BID) : SymbolInfoDouble(_Symbol, SYMBOL_ASK);
      int k = Find(tk);
      if (k < 0)
      {
         if (nT >= 64) continue;
         double sl0 = PositionGetDouble(POSITION_SL), R = sl0 > 0 ? MathAbs(e - sl0) : Buf(hATR5, 0, 1) * 1.5;
         if (R <= 0) R = e * 0.01;
         double sl = L ? e - R : e + R;
         if ((L && px <= sl) || (!L && px >= sl)) sl = L ? px - R : px + R;   // taking charge never closes on first sight
         T[nT].ticket = tk; T[nT].e = e; T[nT].R = R; T[nT].sl = sl; T[nT].best = 0; T[nT].at = TimeCurrent(); k = nT; nT++;
         if (!mine) Print("MSAATB is now in FULL CONTROL of your trade #", tk);
      }
      double r = (L ? px - e : e - px) / T[k].R;
      if (r > T[k].best) T[k].best = r;
      double b = T[k].best, R2 = T[k].R, n = 0; bool has = true;
      if (b >= 3) n = e + (L ? 1 : -1) * (b - 0.7) * R2;
      else if (b >= 2) n = e + (L ? 1 : -1) * (b - 1) * R2;
      else if (b >= 1.5) n = e + (L ? 0.5 : -0.5) * R2;
      else if (b >= 1) n = e + (L ? 1 : -1) * e * 0.0002;
      else if (b >= 0.5) n = e - (L ? 0.5 : -0.5) * R2;
      else has = false;
      if (has && ((L && n > T[k].sl) || (!L && n < T[k].sl))) T[k].sl = n;
      double dg = (double)SymbolInfoInteger(_Symbol, SYMBOL_DIGITS), slN = NormalizeDouble(T[k].sl, (int)dg);
      double cur = PositionGetDouble(POSITION_SL);
      if (MathAbs(cur - slN) > SymbolInfoDouble(_Symbol, SYMBOL_POINT)) trade.PositionModify(tk, slN, mine ? PositionGetDouble(POSITION_TP) : 0.0);
      string why = "";
      if ((L && px <= T[k].sl) || (!L && px >= T[k].sl)) why = b >= 1 ? "profit lock" : "stop-loss";
      else if (TimeCurrent() - T[k].at > TimeStopMin * 60 && r < 0.5 && b < 1) why = "time stop";
      else if (!mine && trend && view != 0 && ((L && view == -1) || (!L && view == 1))) why = "trend turned against your trade";
      if (why != "") { trade.PositionClose(tk); Print("MSAATB closed #", tk, ": ", why); continue; }
      openRisk += MathAbs(px - T[k].sl) / px;
   }
}

void OnTimer()
{
   MqlDateTime d; TimeToStruct(TimeCurrent(), d);
   if (d.day_of_year != dayNum) { dayNum = d.day_of_year; dayStartEq = AccountInfoDouble(ACCOUNT_EQUITY); }
   Manage();
   double eq = AccountInfoDouble(ACCOUNT_EQUITY);
   if (dayStartEq > 0 && (dayStartEq - eq) / dayStartEq * 100 >= DailyLossPct) return;
   int mineN = 0; for (int i = 0; i < PositionsTotal(); i++) { ulong tk = PositionGetTicket(i); if (PositionSelectByTicket(tk) && PositionGetInteger(POSITION_MAGIC) == Magic) mineN++; }
   if (mineN >= MaxTrades || (mineN + 1) * RiskPercent > MaxOpenRiskPct) return;
   if (Cooling()) return;
   bool trend, go; int side = Analyse(trend, go); if (!go) return;
   double at5 = Buf(hATR5, 0, 1), stop = at5 * 1.5;
   double spread = SymbolInfoDouble(_Symbol, SYMBOL_ASK) - SymbolInfoDouble(_Symbol, SYMBOL_BID);
   if (at5 <= 0 || spread > at5 * MaxSpreadATR) return;
   double lots = LotsFor(stop); if (lots <= 0) return;
   int dg = (int)SymbolInfoInteger(_Symbol, SYMBOL_DIGITS);
   if (side == 1) { double a = SymbolInfoDouble(_Symbol, SYMBOL_ASK); trade.Buy(lots, _Symbol, 0, NormalizeDouble(a - stop, dg), 0, "MSAATB"); }
   else { double bd = SymbolInfoDouble(_Symbol, SYMBOL_BID); trade.Sell(lots, _Symbol, 0, NormalizeDouble(bd + stop, dg), 0, "MSAATB"); }
}
void OnTick() { }
