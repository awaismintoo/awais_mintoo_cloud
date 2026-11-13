"""MintooSuite data job (runs on GitHub Actions, build 496).
1) Weather for main cities from MET Norway (free, CC BY 4.0, also for commercial use with credit;
   requests are made from this server with an identifying User-Agent and cached, as MET asks).
2) Official weekly average prices of 51 essential items from the Pakistan Bureau of Statistics (SPI PDF).
Writes out/weather.json and out/prices.json. Old files are kept when a source fails."""
import json, math, os, re, sys, time, datetime as dt
import requests

UA = {'User-Agent': 'MintooSuite/10.8 github.com/awaismintoo/awais_mintoo_cloud'}
# PBS's site may refuse unknown clients: use ordinary browser headers there, and log every try to status.json
BUA = {'User-Agent': 'Mozilla/5.0 (Linux; Android 14; SM-G986B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36',
       'Accept': 'text/html,application/xhtml+xml,application/pdf,*/*;q=0.8', 'Accept-Language': 'en-PK,en;q=0.9', 'Referer': 'https://www.pbs.gov.pk/'}
LOG = []
def pbs_get(u, timeout=40):
    try:
        r = requests.get(u, headers=BUA, timeout=timeout)
        LOG.append([u[-90:], r.status_code, len(r.content)])
        return r
    except Exception as e:
        LOG.append([u[-90:], 'ERR', str(e)[:120]]); raise
OUT = sys.argv[1] if len(sys.argv) > 1 else 'out'
OLD = sys.argv[2] if len(sys.argv) > 2 else 'old'
os.makedirs(OUT, exist_ok=True)

CITIES = [
 ('Islamabad','PK',33.68,73.05,5),('Rawalpindi','PK',33.60,73.04,5),('Lahore','PK',31.55,74.34,5),('Karachi','PK',24.86,67.01,5),
 ('Peshawar','PK',34.01,71.58,5),('Quetta','PK',30.18,66.98,5),('Multan','PK',30.20,71.47,5),('Faisalabad','PK',31.42,73.08,5),
 ('Hyderabad','PK',25.40,68.37,5),('Gujranwala','PK',32.16,74.19,5),('Sialkot','PK',32.49,74.53,5),('Sargodha','PK',32.08,72.67,5),
 ('Bahawalpur','PK',29.40,71.68,5),('Sukkur','PK',27.71,68.85,5),('Abbottabad','PK',34.15,73.21,5),('Murree','PK',33.91,73.39,5),
 ('Muzaffarabad','PK',34.37,73.47,5),('Gilgit','PK',35.92,74.31,5),('Hunza (Karimabad)','PK',36.32,74.66,5),('Skardu','PK',35.30,75.63,5),
 ('Swat (Mingora)','PK',34.78,72.36,5),('Gwadar','PK',25.12,62.32,5),('Larkana','PK',27.56,68.21,5),('Mardan','PK',34.20,72.04,5),
 ('Dera Ismail Khan','PK',31.83,70.90,5),('Chitral','PK',35.85,71.79,5),('Sahiwal','PK',30.66,73.11,5),('Jhelum','PK',32.93,73.73,5),
 ('Dubai','AE',25.20,55.27,4),('Abu Dhabi','AE',24.45,54.38,4),('Riyadh','SA',24.71,46.68,3),('Jeddah','SA',21.49,39.19,3),
 ('Makkah','SA',21.39,39.86,3),('Madinah','SA',24.47,39.61,3),('Doha','QA',25.29,51.53,3),('Kuwait City','KW',29.38,47.99,3),
 ('Muscat','OM',23.59,58.41,4),('London','GB',51.51,-0.13,0),('Manchester','GB',53.48,-2.24,0),('New York','US',40.71,-74.01,-5),
 ('Toronto','CA',43.65,-79.38,-5),('Sydney','AU',-33.87,151.21,11),('Kuala Lumpur','MY',3.14,101.69,8),('Istanbul','TR',41.01,28.98,3),
 ('Delhi','IN',28.61,77.21,5.5),('Mumbai','IN',19.08,72.88,5.5),('Kabul','AF',34.53,69.17,4.5),('Tehran','IR',35.69,51.39,3.5),
 ('Beijing','CN',39.90,116.41,8),('Tokyo','JP',35.68,139.69,9),('Paris','FR',48.86,2.35,1),('Berlin','DE',52.52,13.40,1),
]
# MET symbol -> WMO-like code used by the app
def wcode(sym):
    s = (sym or '').split('_')[0]
    if s in ('clearsky',): return 0
    if s in ('fair',): return 1
    if s in ('partlycloudy',): return 2
    if s in ('cloudy',): return 3
    if s in ('fog',): return 45
    if 'thunder' in s: return 95
    if 'sleet' in s or 'snow' in s: return 85 if 'showers' in s else 73
    if 'showers' in s: return 80
    if 'lightrain' in s: return 61
    if 'heavyrain' in s: return 65
    if 'rain' in s: return 63
    return 2
def feels(t, rh, ws):
    if t is None: return None
    if t >= 27 and rh is not None:
        f = t * 9 / 5 + 32
        hi = (-42.379 + 2.04901523*f + 10.14333127*rh - .22475541*f*rh - .00683783*f*f - .05481717*rh*rh + .00122874*f*f*rh + .00085282*f*rh*rh - .00000199*f*f*rh*rh)
        return round((hi - 32) * 5 / 9, 1)
    if t <= 10 and ws is not None and ws * 3.6 > 4.8:
        v = (ws * 3.6) ** 0.16
        return round(13.12 + 0.6215*t - 11.37*v + 0.3965*t*v, 1)
    return t
def sun(lat, lon, day, tz):
    # NOAA sunrise equation (approximate, +-2 min)
    n = day.timetuple().tm_yday
    g = 2*math.pi/365*(n-1)
    eq = 229.18*(0.000075+0.001868*math.cos(g)-0.032077*math.sin(g)-0.014615*math.cos(2*g)-0.040849*math.sin(2*g))
    de = 0.006918-0.399912*math.cos(g)+0.070257*math.sin(g)-0.006758*math.cos(2*g)+0.000907*math.sin(2*g)-0.002697*math.cos(3*g)+0.00148*math.sin(3*g)
    la = math.radians(lat)
    c = (math.cos(math.radians(90.833))/(math.cos(la)*math.cos(de)) - math.tan(la)*math.tan(de))
    if c > 1 or c < -1: return None, None
    ha = math.degrees(math.acos(c))
    def hm(m):
        m = (m + tz*60) % 1440
        return '%02d:%02d' % (int(m//60), int(m % 60))
    return hm(720-4*(lon+ha)-eq), hm(720-4*(lon-ha)-eq)
def city_wx(name, cc, lat, lon, tz):
    r = requests.get('https://api.met.no/weatherapi/locationforecast/2.0/complete', params={'lat': lat, 'lon': lon}, headers=UA, timeout=30)
    r.raise_for_status()
    ts = r.json()['properties']['timeseries']
    hours = []
    for x in ts:
        t = dt.datetime.strptime(x['time'], '%Y-%m-%dT%H:%M:%SZ') + dt.timedelta(hours=tz)
        d = x['data']['instant']['details']
        n1 = x['data'].get('next_1_hours') or x['data'].get('next_6_hours') or {}
        sym = (n1.get('summary') or {}).get('symbol_code', '')
        pp = (n1.get('details') or {}).get('probability_of_precipitation')
        hours.append((t, d.get('air_temperature'), d.get('relative_humidity'), d.get('wind_speed'), d.get('ultraviolet_index_clear_sky'), sym, pp))
    if not hours: raise RuntimeError('empty')
    h0 = hours[0]
    cur = {'temperature_2m': h0[1], 'apparent_temperature': feels(h0[1], h0[2], h0[3]), 'relative_humidity_2m': h0[2],
           'weather_code': wcode(h0[5]), 'wind_speed_10m': round((h0[3] or 0)*3.6, 1), 'is_day': 0 if 'night' in (h0[5] or '') else 1}
    hh = [h for h in hours if h[0] - h0[0] <= dt.timedelta(hours=48)]
    hourly = {'time': [h[0].strftime('%Y-%m-%dT%H:00') for h in hh], 'temperature_2m': [h[1] for h in hh],
              'weather_code': [wcode(h[5]) for h in hh], 'precipitation_probability': [h[6] if h[6] is not None else None for h in hh]}
    days = {}
    for h in hours:
        k = h[0].strftime('%Y-%m-%d'); days.setdefault(k, []).append(h)
    daily = {'time': [], 'weather_code': [], 'temperature_2m_max': [], 'temperature_2m_min': [], 'sunrise': [], 'sunset': [], 'precipitation_probability_max': [], 'uv_index_max': []}
    for k in sorted(days):
        a = days[k]; temps = [x[1] for x in a if x[1] is not None]
        if not temps: continue
        mid = min(a, key=lambda x: abs(x[0].hour - 13))
        sr, ss = sun(lat, lon, dt.date.fromisoformat(k), tz)
        pps = [x[6] for x in a if x[6] is not None]; uvs = [x[4] for x in a if x[4] is not None]
        daily['time'].append(k); daily['weather_code'].append(wcode(mid[5])); daily['temperature_2m_max'].append(max(temps)); daily['temperature_2m_min'].append(min(temps))
        daily['sunrise'].append(k + 'T' + sr if sr else ''); daily['sunset'].append(k + 'T' + ss if ss else '')
        daily['precipitation_probability_max'].append(max(pps) if pps else None); daily['uv_index_max'].append(max(uvs) if uvs else None)
    return {'n': name, 'cc': cc, 'la': lat, 'lo': lon, 'wx': {'current': cur, 'hourly': hourly, 'daily': daily}}

def load_old(fn):
    try:
        return json.load(open(os.path.join(OLD, fn)))
    except Exception:
        return None

def weather():
    old = load_old('weather.json') or {}
    oldc = {c['n']: c for c in old.get('cities', [])}
    out = []
    for c in CITIES:
        try:
            out.append(city_wx(*c)); time.sleep(0.3)
        except Exception as e:
            print('weather fail', c[0], e)
            if c[0] in oldc: out.append(oldc[c[0]])
    json.dump({'t': int(time.time()*1000), 'src': 'MET Norway (CC BY 4.0)', 'cities': out}, open(os.path.join(OUT, 'weather.json'), 'w'), separators=(',', ':'))
    print('weather ok', len(out))

ROW = re.compile(r'^\s*\d{1,2}\s+(.+?)\s+(\d+(?:\.\d+)?)\s+(\d+(?:\.\d+)?)\s+(\d+(?:\.\d+)?)\s+(-?\d+(?:\.\d+)?)\s+(-?\d+(?:\.\d+)?)\s+-?\d+(?:\.\d+)?\s+-?\d+(?:\.\d+)?\s+-?\d+(?:\.\d+)?\s+-?\d+(?:\.\d+)?\s*$')
NUMC = re.compile(r'^-?\(?[\d,]+(?:\.\d+)?\)?%?$')
def numv(x):
    x = str(x).strip().replace(',', '').replace('%', '')
    neg = x.startswith('(') and x.endswith(')')
    x = x.strip('()')
    v = float(x)
    return -v if neg else v
def rows_parse(rows):
    """Generic: each row = list of cells; the item name is the text cells, the first 5 numbers are
    price now, last week, a year ago, % change on the week, % change on the year."""
    items = {}
    for row in rows:
        cells = [str(c).strip() for c in row if c is not None and str(c).strip() != '']
        if len(cells) < 6: continue
        txt, nums = [], []
        for i, c in enumerate(cells):
            if NUMC.match(c) and not (i == 0 and re.match(r'^\d{1,2}$', c)):
                try: nums.append(numv(c)); continue
                except Exception: pass
            if not nums: txt.append(c)
        if len(nums) < 5 or not txt: continue
        name = re.sub(r'^\d{1,2}\s+', '', ' '.join(txt)).strip()
        if len(name) < 3 or re.search(r'items|units|prices in|weight|impact|%change', name, re.I): continue
        p, prev, yago, wow, yoy = nums[:5]
        if p <= 0 or abs(wow) > 300: continue
        if name not in items: items[name] = {'n': name, 'p': p, 'prev': prev, 'yago': yago, 'wow': wow, 'yoy': yoy}
    return list(items.values())
def spi_parse(text):
    rows = []
    for line in text.splitlines():
        tk = line.split()
        if not tk: continue
        # keep numbers with commas together; the name is everything before the trailing run of numbers
        i = len(tk)
        while i > 0 and NUMC.match(tk[i-1]): i -= 1
        if len(tk) - i < 5: continue
        rows.append([' '.join(tk[:i])] + tk[i:])
    return rows_parse(rows)
SAMPLE = []
def pdf_items(u):
    import pdfplumber, io
    r = pbs_get(u, 40)
    if r.status_code != 200 or b'%PDF' not in r.content[:1024]: return None, ''
    with pdfplumber.open(io.BytesIO(r.content)) as pdf:
        text = '\n'.join((p.extract_text() or '') for p in pdf.pages)
        items = spi_parse(text)
        if len(items) < 30:
            rows = []
            for pg in pdf.pages:
                for t in (pg.extract_tables() or []): rows.extend(t)
            it2 = rows_parse(rows)
            if len(it2) > len(items): items = it2
    if len(items) < 30 and len(SAMPLE) < 2: SAMPLE.append(text[:2500])
    m = re.search(r'week ended on (\d{2}[-.]\d{2}[-.]\d{4})', text, re.I)
    return items, (m.group(1) if m else '')
def xlsx_items(u):
    import openpyxl, io
    r = pbs_get(u, 40)
    if r.status_code != 200 or r.content[:2] != b'PK': return None
    wb = openpyxl.load_workbook(io.BytesIO(r.content), data_only=True, read_only=True)
    rows = []
    for ws in wb.worksheets:
        for row in ws.iter_rows(values_only=True): rows.append(list(row))
    return rows_parse(rows)
def spi_weeks():
    """Week-ended dates (newest first). PBS publishes the weekly SPI on Friday for the week ended Thursday."""
    today = dt.date.today()
    out = []
    for back in range(0, 50):
        d = today - dt.timedelta(days=back)
        if d.weekday() == 3: out.append(d)
        if len(out) >= 6: break
    return out
def spi_docs():
    """(week, report_pdf_urls, annex_pdf_urls) newest first. Reads each week's PBS post for the real links,
    then falls back to the known file-name patterns (PBS changed them in 2025-26)."""
    res = []
    for d in spi_weeks():
        dm, dmy, dmy2 = d.strftime('%d.%m'), d.strftime('%d.%m.%Y'), d.strftime('%d-%m-%Y')
        rep, ann = [], []
        try:
            h = pbs_get('https://www.pbs.gov.pk/weekly-sensitive-price-indicator-spi-for-the-week-ended-on-%s/' % dmy2, 30).text
            for u in re.findall(r'href="([^"]+\.(?:pdf|xlsx))"', h):
                u = requests.compat.urljoin('https://www.pbs.gov.pk/', u)
                (ann if 'ANNEX' in u.upper() else rep).append(u)
        except Exception as e:
            print('post fail', dmy2, e)
        base = 'https://www.pbs.gov.pk/wp-content/uploads/2020/07/'
        rep.append(base + 'SPI-Report-%s.xlsx' % dmy)
        for pat in ('SPI-Executive-Summary-Report-%s.pdf', 'Executive-Summary-SPI-Report_%s.pdf', 'SPI-Executive-Summary-SPI-Report_%s.pdf', 'SPI-Executive-Summary-SPI-Report_-%s.pdf'):
            rep.append(base + pat % dmy)
        for x in (dm, dmy): ann.append(base + 'Annex_%s.pdf' % x)
        res.append((dmy2, list(dict.fromkeys(rep)), list(dict.fromkeys(ann))))
    return res
CITY17 = ['Islamabad', 'Rawalpindi', 'Gujranwala', 'Sialkot', 'Lahore', 'Faisalabad', 'Sargodha', 'Multan', 'Bahawalpur', 'Karachi', 'Hyderabad', 'Sukkur', 'Larkana', 'Peshawar', 'Bannu', 'Quetta', 'Khuzdar']
NUM = re.compile(r'^-?\d+(?:\.\d+)?$')
def annex_parse(pages):
    """City-wise prices. Each page has a header 'Islamabad (01) Rawalpindi (02) ...' and rows
    'Sr Item Unit  min avg max  min avg max ...' (3 numbers per city; 1 number per city is also accepted)."""
    out = {}
    for t in pages:
        cs = []
        for m in re.finditer(r'([A-Z][A-Za-z\. ]{2,20}?)\s*\((\d{2})\)', t):
            n = m.group(1).strip().split(' ')[-1]
            for c in CITY17:
                if c.lower() == n.lower() and c not in cs: cs.append(c)
        if not cs: continue
        nc = len(cs)
        for line in t.splitlines():
            m = re.match(r'^\s*\d{1,2}\s+(.+)$', line)
            if not m: continue
            tk = m.group(1).split(); i = len(tk)
            while i > 0 and NUM.match(tk[i-1]): i -= 1
            name, nums = ' '.join(tk[:i]).strip(), [float(x) for x in tk[i:]]
            if len(name) < 3: continue
            if len(nums) >= 3*nc: k, vals = 3, nums[-3*nc:]
            elif len(nums) >= nc: k, vals = 1, nums[-nc:]
            else: continue
            row = out.setdefault(name, {})
            for j, c in enumerate(cs):
                v = vals[j*k:(j+1)*k]
                avg = v[1] if k == 3 else v[0]
                if avg > 0: row[c] = [round(v[0], 2), round(avg, 2), round(v[-1], 2)] if k == 3 else [round(avg, 2)]*3
    return out
def nkey(s):
    return re.sub(r'[^a-z]', '', s.lower())
def pdf_pages(u):
    import pdfplumber, io
    r = pbs_get(u, 40)
    if r.status_code != 200 or b'%PDF' not in r.content[:1024]: return None
    with pdfplumber.open(io.BytesIO(r.content)) as pdf:
        return [(p.extract_text() or '') for p in pdf.pages]
def prices():
    old = load_old('prices.json')
    if old and old.get('items') and time.time()*1000 - old.get('t', 0) < 12*3600*1000:
        json.dump(old, open(os.path.join(OUT, 'prices.json'), 'w'), separators=(',', ':')); print('prices kept (fresh)'); return
    for week, reps, anns in spi_docs():
        items, src = None, ''
        wk = ''
        for u in sorted(reps, key=lambda x: 0 if x.endswith('.xlsx') else 1):
            try:
                if u.endswith('.xlsx'): it = xlsx_items(u)
                else: it, wk = pdf_items(u)
                if it and len(it) >= 30: items, src = it, u; break
                LOG.append(['parsed ' + u[-40:], 'items', len(it or [])])
            except Exception as e:
                print('doc fail', u, e); LOG.append(['parse fail ' + u[-40:], 'ERR', str(e)[:150]])
        if not items: continue
        city = {}
        for u in anns:
            try:
                pg = pdf_pages(u)
                if pg:
                    city = annex_parse(pg)
                    if len(city) >= 20: break
            except Exception as e:
                print('annex fail', u, e)
        ck = {nkey(k): v for k, v in city.items()}
        hits = 0
        for x in items:
            k = nkey(x['n']); c = ck.get(k)
            if c is None:
                for kk, vv in ck.items():
                    if kk[:14] == k[:14]: c = vv; break
            if c: x['c'] = c; hits += 1
        json.dump({'t': int(time.time()*1000), 'week': week, 'src': src, 'cities': CITY17, 'items': items}, open(os.path.join(OUT, 'prices.json'), 'w'), separators=(',', ':'))
        print('prices ok', len(items), 'with city prices', hits, src); return
    if old:
        json.dump(old, open(os.path.join(OUT, 'prices.json'), 'w'), separators=(',', ':')); print('prices kept (old)')

def oil():
    """WTI and Brent daily spot prices from the U.S. Energy Information Administration (public domain),
    via the datahub.io copy of the EIA series (no key). Keeps the old file when the source fails."""
    old = load_old('oil.json')
    out = {}
    for k, u in (('wti', 'https://datahub.io/core/oil-prices/r/wti-daily.csv'), ('brent', 'https://datahub.io/core/oil-prices/r/brent-daily.csv')):
        try:
            r = requests.get(u, timeout=40, headers={'User-Agent': 'MintooSuite data job'})
            rows = [l.split(',') for l in r.text.strip().splitlines()[1:] if ',' in l]
            pts = [[x[0], float(x[1])] for x in rows[-400:] if x[1] not in ('', 'null')]
            if len(pts) > 20: out[k] = pts
        except Exception as e:
            print('oil fail', k, e)
    if len(out) == 2:
        json.dump({'t': int(time.time()*1000), 'src': 'U.S. Energy Information Administration (public domain), daily spot prices', 'wti': out['wti'], 'brent': out['brent']}, open(os.path.join(OUT, 'oil.json'), 'w'), separators=(',', ':')); print('oil ok', out['wti'][-1])
    elif old:
        json.dump(old, open(os.path.join(OUT, 'oil.json'), 'w'), separators=(',', ':')); print('oil kept (old)')

EV_CITIES = ['Islamabad', 'Rawalpindi', 'Lahore', 'Karachi', 'Peshawar', 'Quetta', 'Multan', 'Faisalabad', 'Hyderabad', 'Sialkot', 'Gilgit', 'Murree']
EV_Q = {
    'events': '{c} (expo OR exhibition OR festival OR mela OR "car show" OR "auto show" OR concert OR "book fair" OR "trade show" OR "food festival" OR gala OR seminar OR conference)',
    'sports': '{c} (match OR tournament OR PSL OR cricket OR hockey OR football OR marathon OR "sports gala" OR championship)',
    'tourism': '{c} (tourism OR tourists OR "tourist spot" OR park OR trek OR "hill station" OR heritage)',
}
def rss_items(q):
    import xml.etree.ElementTree as ET, email.utils as eu
    u = 'https://news.google.com/rss/search?hl=en-PK&gl=PK&ceid=PK:en&q=' + requests.utils.quote(q + ' when:30d')
    r = requests.get(u, timeout=30, headers={'User-Agent': 'MintooSuite data job'})
    root = ET.fromstring(r.content); out = []
    for it in root.iter('item'):
        t = (it.findtext('title') or '').strip(); l = (it.findtext('link') or '').strip(); d = it.findtext('pubDate') or ''; src = it.find('source')
        try: ts = int(eu.parsedate_to_datetime(d).timestamp() * 1000)
        except Exception: ts = 0
        if t and l: out.append({'t': t[:160], 'u': l, 'd': ts, 's': (src.text if src is not None else '')[:40]})
    return out
def events():
    """Headlines (title, link, date, publisher) about events, sports and tourism per city, from Google News RSS.
    Only the headline and link are kept; the full story opens on the publisher's site."""
    old = load_old('events.json') or {}
    if old and time.time()*1000 - old.get('t', 0) < 3*3600*1000:
        json.dump(old, open(os.path.join(OUT, 'events.json'), 'w'), separators=(',', ':')); print('events kept (fresh)'); return
    out = {}
    for c in EV_CITIES:
        for k, q in EV_Q.items():
            try:
                L = rss_items(q.format(c=c))
                seen = set(); L2 = []
                for x in sorted(L, key=lambda x: -x['d']):
                    key = x['t'].lower()[:60]
                    if key in seen: continue
                    seen.add(key); L2.append(x)
                out.setdefault(c, {})[k] = L2[:25]
            except Exception as e:
                print('events fail', c, k, e)
                if old.get('c', {}).get(c, {}).get(k): out.setdefault(c, {})[k] = old['c'][c][k]
            time.sleep(1)
    if out:
        json.dump({'t': int(time.time()*1000), 'src': 'Headlines via Google News; stories on the publishers\' sites', 'c': out}, open(os.path.join(OUT, 'events.json'), 'w'), separators=(',', ':')); print('events ok', sum(len(v) for c in out.values() for v in c.values()))
    elif old:
        json.dump(old, open(os.path.join(OUT, 'events.json'), 'w'), separators=(',', ':'))

if __name__ == '__main__':
    for f in (weather, prices, oil, events):
        try: f()
        except Exception as e: print('job fail', f.__name__, e); LOG.append(['job fail ' + f.__name__, 'ERR', str(e)[:200]])
    try: json.dump({'t': int(time.time()*1000), 'pbs': LOG[-80:], 'sample': SAMPLE, 'files': sorted(os.listdir(OUT))}, open(os.path.join(OUT, 'status.json'), 'w'))
    except Exception as e: print('status fail', e)
