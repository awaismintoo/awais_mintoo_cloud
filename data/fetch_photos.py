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
        if not u or not lic or not relevant(q, x.get('title', '')) or not safe(x.get('title', '')):
            continue
        out.append({'u': u, 'page': ii.get('descriptionurl'), 'a': strip((m.get('Artist') or {}).get('value')) or 'Wikimedia Commons', 'l': lic})
    return out

def openverse(q):
    r = requests.get('https://api.openverse.org/v1/images/', params={'q': q, 'page_size': 6, 'mature': 'false'}, headers=UA, timeout=30)
    return [{'u': x.get('thumbnail') or x.get('url'), 'page': x.get('foreign_landing_url'), 'a': (x.get('creator') or 'Openverse')[:80],
             'l': ((x.get('license') or '') + ' ' + (x.get('license_version') or '')).upper().strip()} for x in r.json().get('results', []) if (x.get('thumbnail') or x.get('url')) and not x.get('mature') and safe((x.get('title') or '') + ' ' + ' '.join((t or {}).get('name', '') for t in (x.get('tags') or []))) and relevant(q, (x.get('title') or '') + ' ' + ' '.join((t or {}).get('name', '') for t in (x.get('tags') or [])))]

def main():
    qs = json.load(open(sys.argv[1], encoding='utf-8'))
    out = sys.argv[2]; os.makedirs(os.path.join(out, 'ph'), exist_ok=True)
    ip = os.path.join(out, 'ph', 'index.json')
    idx = json.load(open(ip, encoding='utf-8')) if os.path.exists(ip) else {}
    if idx.get('_v') != 3:  # build 519: re-check every photo with the family-safe rules
        for f in os.listdir(os.path.join(out, 'ph')):
            if f != 'index.json':
                os.remove(os.path.join(out, 'ph', f))
        idx = {}
    idx['_v'] = 3
    new = 0
    for item in qs:
        q, kind = (item if isinstance(item, list) else [item, 'pk'])[:2]
        k = norm(q)
        if not k or k in idx:
            continue
        if new >= MAX_NEW:
            break
        new += 1
        L = []
        T = [w for w in norm(q).split(' ') if len(w) >= 3 and w not in STOP]
        variants = [q]
        if len(T) >= 3:
            variants.append(' '.join(T[-2:]))
        if len(T) >= 2:
            variants.append(T[-1] if len(T[-1]) > 4 else ' '.join(T[-2:]))
        for vq in variants:
            try:
                L = commons(vq, kind) or openverse(vq)
            except Exception as e:
                print('search failed', vq, e); L = []
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
        idx[k] = saved
        time.sleep(0.3)
    json.dump(idx, open(ip, 'w', encoding='utf-8'), ensure_ascii=False, separators=(',', ':'))
    print('items', len(idx), 'new', new, 'with photos', sum(1 for v in idx.values() if v))

if __name__ == '__main__':
    main()
