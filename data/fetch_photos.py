#!/usr/bin/env python3
"""MintooSuite photo mirror (build 518).
Some phone networks cannot reach Wikimedia Commons, so the app's free photos failed there.
This job (GitHub Actions, weekly) looks up each item's free photos on Wikimedia Commons (then Openverse),
saves small copies (320 px) with author and licence, and publishes them on the `media` branch,
which the app reads from raw.githubusercontent.com (reachable on those networks).
Usage: fetch_photos.py <queries.json> <media_dir>
"""
import hashlib, json, os, re, sys, time
import requests

UA = {'User-Agent': 'MintooSuitePhotoMirror/1.0 (+https://github.com/awaismintoo/awais_mintoo_cloud)'}
PER = 2
MAX_NEW = int(os.environ.get('MAX_NEW', '1500'))

def norm(s):
    s = re.sub(r'\(.*?\)', '', str(s or '').lower())
    s = re.sub(r'[^a-z0-9 ]', ' ', s)
    return re.sub(r'\s+', ' ', s).strip()[:60]

STOP = set('and the for with from best new old price prices rate rates shop shops near pakistan pakistani men women kids set pack large small big top good'.split())

def relevant(q, text):
    T = [w for w in norm(q).split(' ') if len(w) >= 3 and w not in STOP]
    if not T:
        return True
    text = ' ' + norm(re.sub(r'[_-]', ' ', text or '')) + ' '
    hit = sum(1 for w in T if w in text or (len(w) > 4 and w[:-1] in text))
    return hit >= min(2, len(T)) or (len(T) >= 3 and hit >= 1 and T[0] in text)

UNSAFE = re.compile(r'\b(nude|naked|nudity|nsfw|erotic|erotica|sexy|sex|lingerie|underwear|undies|bikini|swimsuit|swimwear|topless|bra|bras|panties|thong|boudoir|fetish|porn|adult|playboy|pinup|doll|dolls|bjd|dollfie|figure|figurine|figurines|cosplay|model|models|modeling|glamour|seductive|kiss|kissing|bed|bedroom|bath|bathing|shower|body|bodies|legs|breast|butt|lady|ladies|girl|girls|babe|anime|hentai|manga|cleavage|tattoo|corpse|blood|gore|weapon|gun)\b', re.I)

def safe(text):
    return not UNSAFE.search(re.sub(r'[_-]', ' ', text or ''))

def strip(h):
    return re.sub(r'\s+', ' ', re.sub(r'<[^>]+>', ' ', h or '')).strip()[:80]

def commons(q, kind):
    qq = q + (' logo' if kind == 'share' else '') + ' filetype:bitmap'
    r = requests.get('https://commons.wikimedia.org/w/api.php', params={
        'action': 'query', 'format': 'json', 'generator': 'search', 'gsrnamespace': 6, 'gsrlimit': 6,
        'gsrsearch': qq, 'prop': 'imageinfo', 'iiprop': 'url|extmetadata', 'iiurlwidth': 320}, headers=UA, timeout=30)
    p = (r.json().get('query') or {}).get('pages') or {}
    out = []
    for x in sorted(p.values(), key=lambda a: a.get('index', 0)):
        ii = (x.get('imageinfo') or [{}])[0]; m = ii.get('extmetadata') or {}
        u = ii.get('thumburl') or ii.get('url')
        lic = strip((m.get('LicenseShortName') or {}).get('value'))
        if not u or not lic or not relevant(q, x.get('title', '')) or not safe(x.get('title', '')) or (OLDPIC.search(x.get('title', '')) and not HIST.search(norm(q))):
            continue
        out.append({'u': u, 'page': ii.get('descriptionurl'), 'a': strip((m.get('Artist') or {}).get('value')) or 'Wikimedia Commons', 'l': lic})
    return out

OV = {'off': False}

def openverse(q):
    if OV['off']:
        return []
    r = requests.get('https://api.openverse.org/v1/images/', params={'q': q, 'page_size': 6, 'mature': 'false'}, headers=UA, timeout=30)
    if r.status_code == 429:
        OV['off'] = True; print('Openverse daily limit reached; Commons only for the rest of this run'); return []
    return [{'u': x.get('thumbnail') or x.get('url'), 'page': x.get('foreign_landing_url'), 'a': (x.get('creator') or 'Openverse')[:80],
             'l': ((x.get('license') or '') + ' ' + (x.get('license_version') or '')).upper().strip()} for x in r.json().get('results', []) if (x.get('thumbnail') or x.get('url')) and not x.get('mature') and safe((x.get('title') or '') + ' ' + ' '.join((t or {}).get('name', '') for t in (x.get('tags') or []))) and relevant(q, (x.get('title') or '') + ' ' + ' '.join((t or {}).get('name', '') for t in (x.get('tags') or [])))]

STOPV = STOP | set('chunky bold fine layered light heavy piece pieces style styles trend trends type types design designs modern classic simple fancy latest cheap premium local branded ready wear unstitched stitched daily formal casual one two three online offline guide tips how what which'.split())
SKIPQ = re.compile(r'^(live events|this week|today|tomorrow|(january|february|march|april|may|june|july|august|september|october|november|december) \d{4}|(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b)', re.I)

CONCEPT = [(r'\b(phone|phones|mobile|smartphone)\b', 'smartphone'), (r'\b(used car|car check|cars|car|bikes|motorcycle)\b', 'car showroom'), (r'\b(property|plot|plots|real estate|house|houses|flat|flats)\b', 'residential house'), (r'\b(local market|bazaar|market|offers|deals|sale)\b', 'bazaar market stalls'), (r'\b(hostel|hostels|guest house|room|rooms)\b', 'hostel room'), (r'\b(food|restaurant|dish|eat)\b', 'pakistani food'), (r'\b(business|software|shop|store)\b', 'small shop counter'), (r'\b(student|students|study|project|school|college)\b', 'students studying'), (r'\b(shopping|clothes|clothing)\b', 'clothing store'), (r'\b(grocery|vegetable|vegetables|fruit|fruits)\b', 'vegetable market'), (r'\b(gold|jewellery|jewelry|necklace|earrings|bangles)\b', 'gold jewellery'), (r'\b(laptop|computer|pc|tech)\b', 'laptop computer')]
OLDPIC = re.compile(r'(?<![0-9])(1[5-8][0-9][0-9]|19[0-8][0-9])(?![0-9])')
HIST = re.compile(r'\b(history|historic|heritage|old|tomb|fort|mosque|museum|monument|ancient|classic)\b')

def variants(q):
    """build 521: better searches for long names ("Chunky gold hoops and bold chains" -> "gold hoops chains", "gold hoops", "hoops chains"; "Abaya and shawl" -> "abaya", "shawl")"""
    def clean(t):
        return [w for w in norm(t).split(' ') if len(w) >= 3 and not w.isdigit() and w not in STOPV]
    T = clean(q); V = [q]
    if T:
        V.append(' '.join(T))
    for part in re.split(r'\band\b|&|,|/|\bor\b|\bwith\b|\bvs\b', norm(q)):
        P = clean(part)
        if len(P) >= 2 or (len(P) == 1 and len(T) <= 2 and len(P[0]) > 3):
            V.append(' '.join(P))
    if len(T) >= 3:
        V += [' '.join(T[:2]), ' '.join(T[-2:])]
    nq = norm(q)
    for rx, cq in CONCEPT:
        if re.search(rx, nq):
            V.append(cq)
    out = []
    for v in V:
        v = v.strip()
        if v and v.lower() not in [o.lower() for o in out]:
            out.append(v)
    return out[:8]

def main():
    t0 = time.time(); budget = float(os.environ.get('TIME_BUDGET', '6000'))  # stop in time and publish what is done (job limit 150 min)
    qs = json.load(open(sys.argv[1], encoding='utf-8'))
    out = sys.argv[2]; os.makedirs(os.path.join(out, 'ph'), exist_ok=True)
    ip = os.path.join(out, 'ph', 'index.json')
    idx = json.load(open(ip, encoding='utf-8')) if os.path.exists(ip) else {}
    tried = idx.get('_t') if isinstance(idx.get('_t'), dict) else {}
    if idx.get('_v') in (3, 4):  # build 522: drop very old photos for modern things (a 1920s photo for "hostel"), then retry every empty item with the better searches
        for k in list(idx.keys()):
            if k.startswith('_') or not isinstance(idx[k], list):
                continue
            if not HIST.search(k):
                keep = []
                for x in idx[k]:
                    if OLDPIC.search(x.get('page') or ''):
                        try: os.remove(os.path.join(out, x['f']))
                        except Exception: pass
                    else:
                        keep.append(x)
                idx[k] = keep
            if not idx[k]:
                del idx[k]
    if idx.get('_v') not in (3, 4, 5):  # build 521: keep good photos, drop only those that fail the family-safe check
        drop = 0
        for k in list(idx.keys()):
            if k.startswith('_') or not isinstance(idx[k], list):
                continue
            keep = []
            for x in idx[k]:
                if safe((x.get('page') or '').split('/')[-1]) and relevant(k, (x.get('page') or '').split('/')[-1]):
                    keep.append(x)
                else:
                    drop += 1
                    try: os.remove(os.path.join(out, x['f']))
                    except Exception: pass
            idx[k] = keep
        print('re-checked old photos, removed', drop)
    idx['_v'] = 5
    now = time.time(); new = 0
    todo = []
    for item in qs:
        q, kind = (item if isinstance(item, list) else [item, 'pk'])[:2]
        k = norm(q)
        if not k or SKIPQ.search(k):
            continue
        if idx.get(k):
            continue
        if k in idx and now - tried.get(k, 0) < 86400:
            continue
        todo.append((0 if k not in idx else 1, q, kind, k))
    todo.sort(key=lambda x: x[0])  # never-tried first, then empty ones again with the better searches
    print('to do', len(todo))
    for _, q, kind, k in todo:
        if new >= MAX_NEW or time.time() - t0 > budget:
            print('time or count limit reached; the next run continues'); break
        new += 1
        L = []; failed = False
        for vq in variants(q):
            try:
                L = commons(vq, kind)
            except Exception as e:
                print('commons failed', vq, e); L = []; failed = True
            if not L:
                try:
                    L = openverse(vq)
                except Exception as e:
                    print('openverse failed', vq, e); L = []; failed = True
            if L:
                break
            time.sleep(0.3)
        saved = []
        h = hashlib.md5(k.encode()).hexdigest()[:16]
        for x in L:
            if len(saved) >= PER:
                break
            try:
                r = requests.get(x['u'], headers=UA, timeout=40)
                if r.status_code != 200 or len(r.content) < 2000 or len(r.content) > 400000:
                    continue
                ext = '.png' if 'png' in r.headers.get('content-type', '') else '.jpg'
                f = 'ph/%s_%d%s' % (h, len(saved), ext)
                open(os.path.join(out, f), 'wb').write(r.content)
                saved.append({'f': f, 'a': x['a'], 'l': x['l'], 'page': x.get('page')})
            except Exception as e:
                print('download failed', q, e)
            time.sleep(0.3)
        if not saved and failed:
            continue  # a network error is not a real 'no photos': try again next run
        idx[k] = saved; tried[k] = now
        if new % 50 == 0:
            idx['_t'] = tried; json.dump(idx, open(ip, 'w', encoding='utf-8'), ensure_ascii=False, separators=(',', ':'))
        time.sleep(0.3)
    idx['_t'] = tried
    json.dump(idx, open(ip, 'w', encoding='utf-8'), ensure_ascii=False, separators=(',', ':'))
    print('items', sum(1 for k in idx if not k.startswith('_')), 'searched now', new, 'with photos', sum(1 for k, v in idx.items() if not k.startswith('_') and v))

if __name__ == '__main__':
    main()
