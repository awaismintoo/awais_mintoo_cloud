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
MAX_NEW = int(os.environ.get('MAX_NEW', '900'))

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
        if not u or not lic or not relevant(q, x.get('title', '')):
            continue
        out.append({'u': u, 'page': ii.get('descriptionurl'), 'a': strip((m.get('Artist') or {}).get('value')) or 'Wikimedia Commons', 'l': lic})
    return out

def openverse(q):
    r = requests.get('https://api.openverse.org/v1/images/', params={'q': q, 'page_size': 6, 'mature': 'false'}, headers=UA, timeout=30)
    return [{'u': x.get('thumbnail') or x.get('url'), 'page': x.get('foreign_landing_url'), 'a': (x.get('creator') or 'Openverse')[:80],
             'l': ((x.get('license') or '') + ' ' + (x.get('license_version') or '')).upper().strip()} for x in r.json().get('results', []) if (x.get('thumbnail') or x.get('url')) and relevant(q, (x.get('title') or '') + ' ' + ' '.join((t or {}).get('name', '') for t in (x.get('tags') or [])))]

def main():
    qs = json.load(open(sys.argv[1], encoding='utf-8'))
    out = sys.argv[2]; os.makedirs(os.path.join(out, 'ph'), exist_ok=True)
    ip = os.path.join(out, 'ph', 'index.json')
    idx = json.load(open(ip, encoding='utf-8')) if os.path.exists(ip) else {}
    new = 0
    for item in qs:
        q, kind = (item if isinstance(item, list) else [item, 'pk'])[:2]
        k = norm(q)
        if not k or k in idx:
            continue
        if new >= MAX_NEW:
            break
        new += 1
        try:
            L = commons(q, kind)
            if not L:
                L = openverse(q)
        except Exception as e:
            print('search failed', q, e); L = []
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
