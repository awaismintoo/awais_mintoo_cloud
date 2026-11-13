#!/usr/bin/env python3
"""MintooSuite offline places (build 518).
Weekly, from OpenStreetMap (Overpass), for the big cities: every named shop, restaurant, hostel, hotel, office,
school, hospital, mosque, park and so on, plus neighbourhoods/villages (with population when mapped) and road names.
Published on the `media` branch as places/<city>.json, which the app reads from raw.githubusercontent.com and keeps
on the phone, so Places search works on networks that block the map servers and with no internet at all.
Data © OpenStreetMap contributors, ODbL. Usage: fetch_places.py <out_dir>"""
import json, os, sys, time
import requests

UA = {'User-Agent': 'MintooSuitePlaces/1.0 (+https://github.com/awaismintoo/awais_mintoo_cloud)'}
CITIES = {
    'isl': (33.45, 72.80, 33.80, 73.30), 'lhr': (31.35, 74.15, 31.65, 74.50), 'khi': (24.78, 66.92, 25.08, 67.30),
    'pew': (33.93, 71.43, 34.07, 71.62), 'fsd': (31.33, 73.00, 31.50, 73.18), 'mux': (30.13, 71.38, 30.26, 71.56), 'uet': (30.12, 66.92, 30.26, 67.07)}
SERVERS = ['https://overpass-api.de/api/interpreter', 'https://overpass.kumi.systems/api/interpreter', 'https://overpass.private.coffee/api/interpreter']
KEYS = ['amenity', 'shop', 'tourism', 'leisure', 'office', 'healthcare', 'craft', 'railway', 'aeroway']

def q(ql):
    last = None
    for s in SERVERS:
        try:
            r = requests.post(s, data={'data': ql}, headers=UA, timeout=300)
            if r.status_code == 200:
                return r.json()
            last = 'HTTP %d' % r.status_code
        except Exception as e:
            last = e
        time.sleep(5)
    raise RuntimeError(last)

def city(bb):
    b = '(%s,%s,%s,%s)' % bb
    sel = ''.join('node["name"]["%s"]%s;way["name"]["%s"]%s;' % (k, b, k, b) for k in KEYS)
    j = q('[out:json][timeout:280];(' + sel + ');out center tags;')
    P = []
    for el in j.get('elements', []):
        t = el.get('tags', {}); n = t.get('name:en') or t.get('name')
        la = el.get('lat') or (el.get('center') or {}).get('lat'); lo = el.get('lon') or (el.get('center') or {}).get('lon')
        if not n or la is None:
            continue
        ty = ''
        for k in KEYS:
            if k in t:
                ty = k + '=' + t[k]; break
        if t.get('religion') and ty == 'amenity=place_of_worship':
            ty += ';religion=' + t['religion']
        ad = ', '.join(x for x in [t.get('addr:housename'), t.get('addr:street'), t.get('addr:suburb') or t.get('addr:neighbourhood'), t.get('addr:city')] if x)
        P.append([n[:80], round(la, 5), round(lo, 5), ty, ad[:100], (t.get('phone') or t.get('contact:phone') or t.get('contact:mobile') or '')[:40],
                  (t.get('website') or t.get('contact:website') or '')[:120], (t.get('opening_hours') or '')[:80]])
    time.sleep(10)
    j2 = q('[out:json][timeout:200];(node["place"~"^(city|town|suburb|neighbourhood|quarter|village|hamlet|locality)$"]%s;way["highway"]["name"]%s;);out center tags;' % (b, b))
    A, R, seen = [], [], set()
    for el in j2.get('elements', []):
        t = el.get('tags', {}); n = t.get('name:en') or t.get('name')
        la = el.get('lat') or (el.get('center') or {}).get('lat'); lo = el.get('lon') or (el.get('center') or {}).get('lon')
        if not n or la is None:
            continue
        if 'place' in t:
            A.append([n[:60], round(la, 5), round(lo, 5), t['place'], t.get('population', ''), t.get('name:ur', '')])
        elif n.lower() not in seen:
            seen.add(n.lower()); R.append([n[:60], round(la, 5), round(lo, 5)])
    return {'t': int(time.time() * 1000), 'p': P, 'a': A, 'r': R, 'lic': 'Data © OpenStreetMap contributors, ODbL'}

def main():
    out = os.path.join(sys.argv[1], 'places'); os.makedirs(out, exist_ok=True)
    for c, bb in CITIES.items():
        try:
            d = city(bb)
            json.dump(d, open(os.path.join(out, c + '.json'), 'w', encoding='utf-8'), ensure_ascii=False, separators=(',', ':'))
            print(c, len(d['p']), 'places', len(d['a']), 'areas', len(d['r']), 'roads')
        except Exception as e:
            print('failed', c, e)
        time.sleep(20)

if __name__ == '__main__':
    main()
