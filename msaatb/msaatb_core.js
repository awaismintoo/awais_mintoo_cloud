/*! MSAATB core (MintooSuite Auto AI Trading Bot) – the decision rules as a small library you can put in any trading app.
 *  No orders, no keys: give it candles, it tells you WAIT / LONG / SHORT, the stop, the size and how to move the stop.
 *  Works in Node.js and browsers.  usage:
 *    const M = require('./msaatb_core.js');
 *    const a = M.analyse(k15, k5, k3, k1);           // candles: [{o,h,l,c}], oldest first
 *    if (a.go) { const stop = a.at5 * 1.5; const qty = M.size(equity, 0.5, stop); }
 *    newStop = M.ladder(bestR, isLong, entry, R, currentStop);
 *  (c) MintooSuite · educational software, not financial advice; no bot can promise profit. */
(function (root, f) { if (typeof module === 'object' && module.exports) { module.exports = f(); } else { root.MSAATB = f(); } })(this, function () {
    var FEE = 0.0005;
    function ema(v, n) { var k = 2 / (n + 1), o = []; v.forEach(function (x, i) { o.push(i ? x * k + o[i - 1] * (1 - k) : x); }); return o; }
    function atr(k, n) { n = n || 14; var tr = []; for (var i = 1; i < k.length; i++) { var a = k[i - 1], b = k[i]; tr.push(Math.max(b.h - b.l, Math.abs(b.h - a.c), Math.abs(b.l - a.c))); } if (!tr.length) { return 0; } tr = tr.slice(-n * 3); var s = tr.slice(0, n), a2 = s.reduce(function (x, y) { return x + y; }, 0) / s.length; tr.slice(n).forEach(function (x) { a2 = (a2 * (n - 1) + x) / n; }); return a2; }
    function rsi(c, n) { n = n || 14; var g = 0, l = 0, s = c.slice(-n - 1); for (var i = 1; i < s.length; i++) { var d = s[i] - s[i - 1]; if (d > 0) { g += d; } else { l -= d; } } return l === 0 ? 100 : 100 - 100 / (1 + g / l); }
    function adx(k, n) { n = n || 14; if (k.length < n * 2 + 2) { return 0; } var P = [], M = [], T = []; for (var i = 1; i < k.length; i++) { var a = k[i - 1], b = k[i], up = b.h - a.h, dn = a.l - b.l; P.push(up > dn && up > 0 ? up : 0); M.push(dn > up && dn > 0 ? dn : 0); T.push(Math.max(b.h - b.l, Math.abs(b.h - a.c), Math.abs(b.l - a.c))); }
        function w(v) { var s = v.slice(0, n).reduce(function (x, y) { return x + y; }, 0), o = [s]; v.slice(n).forEach(function (x) { s = s - s / n + x; o.push(s); }); return o; }
        var p = w(P), m = w(M), t = w(T), dx = p.map(function (pp, i) { if (!t[i]) { return 0; } var pi = 100 * pp / t[i], mi = 100 * m[i] / t[i]; return pi + mi ? 100 * Math.abs(pi - mi) / (pi + mi) : 0; }); var a2 = dx.slice(0, n).reduce(function (x, y) { return x + y; }, 0) / n; dx.slice(n).forEach(function (x) { a2 = (a2 * (n - 1) + x) / n; }); return a2; }
    function bbw(c, n) { n = n || 20; function w(s) { var m = s.reduce(function (x, y) { return x + y; }, 0) / s.length, sd = Math.sqrt(s.reduce(function (x, y) { return x + (y - m) * (y - m); }, 0) / s.length); return m ? 4 * sd / m : 0; } return { now: w(c.slice(-n)), prev: w(c.slice(-n - 1, -1)) }; }
    function analyse(k15, k5, k3, k1) {
        var c = function (k) { return k.map(function (x) { return x.c; }); }, c15 = c(k15), c5 = c(k5), c3 = c(k3), c1 = c(k1);
        var A = adx(k15, 14), B = bbw(c15, 20), at5 = atr(k5, 14), a1 = atr(k1, 14), px = c1[c1.length - 1];
        var e20 = ema(c15, 20), e50 = ema(c15, 50), e5 = ema(c5, 20), e91 = ema(c1, 9), e93 = ema(c3, 9), L = function (a) { return a[a.length - 1]; };
        var shock = a1 > 0 && k1.slice(-5).some(function (x) { return Math.abs(x.c - x.o) > 3 * a1; });
        var regime = shock ? 'WAIT' : (A >= 25 && B.now > B.prev) ? 'TREND' : (A <= 20 && B.now < 0.06) ? 'RANGE' : 'WAIT';
        var up15 = L(e20) > L(e50), side = up15 && L(c5) > L(e5) ? 'LONG' : (!up15 && L(c5) < L(e5) ? 'SHORT' : '');
        var r1 = rsi(c1, 14), stretch = Math.abs(px - L(e5)) / (at5 || 1), l = c1.slice(-4);
        var tu = l[3] > l[2] && l[2] >= l[1] && px > L(e91) && L(c3) > L(e93), td = l[3] < l[2] && l[2] <= l[1] && px < L(e91) && L(c3) < L(e93);
        var ex = stretch > 2.5 || (side === 'LONG' && r1 > 75) || (side === 'SHORT' && r1 < 25);
        return { px: px, adx: A, regime: regime, side: side, go: regime === 'TREND' && !!side && !ex && ((side === 'LONG' && tu) || (side === 'SHORT' && td)), at5: at5, rsi1: r1 };
    }
    function ladder(best, isLong, e, R, sl) { var f = e * FEE * 2, n = null; if (best >= 3) { n = e + (isLong ? 1 : -1) * (best - 0.7) * R; } else if (best >= 2) { n = e + (isLong ? 1 : -1) * (best - 1) * R; } else if (best >= 1.5) { n = e + (isLong ? 0.5 : -0.5) * R; } else if (best >= 1) { n = e + (isLong ? f : -f); } else if (best >= 0.5) { n = e - (isLong ? 0.5 : -0.5) * R; } return n !== null && (isLong ? n > sl : n < sl) ? n : sl; }
    function size(equity, riskPct, stopDist) { riskPct = Math.max(0.25, Math.min(1, riskPct)); return stopDist > 0 ? equity * riskPct / 100 / stopDist : 0; }
    function timeStop(minutesOpen, r, best) { return minutesOpen > 60 && r < 0.5 && best < 1; }
    function cooldown(lossEndTimes, now) { var L = lossEndTimes.slice().sort(function (a, b) { return b - a; }); if (L.length && now - L[0] < 4 * 36e5) { return true; } for (var i = 0; i + 1 < L.length; i++) { if (L[i] - L[i + 1] < 24 * 36e5 && now - L[i] < 24 * 36e5) { return true; } } return false; }
    return { version: '536.1', analyse: analyse, ladder: ladder, size: size, timeStop: timeStop, cooldown: cooldown, indicators: { ema: ema, atr: atr, rsi: rsi, adx: adx, bbw: bbw }, rules: { maxOpenRiskPct: 3, dailyLossPct: 3, maxTrades: 5, stopATR: 1.5 } };
});
