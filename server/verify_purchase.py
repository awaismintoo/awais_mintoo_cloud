#!/usr/bin/env python3
# Copyright (c) 2026 Muhammad Awais. All rights reserved.
"""Optional Google Play purchase checker. The app sends the purchase token here; this asks Google directly (Play Developer API)
whether the subscription is really active, so a faked or refunded purchase cannot unlock Pro.
Needs: a Google Cloud service account added in Play Console > Users and permissions (view financial data + manage orders),
its JSON file at /opt/mintoo-alerts/play_service_account.json, and:  python3 verify_purchase.py   (port 8099, put it behind https)
In the app (Menu > Legal is not needed): store the URL once in the browser storage key mintoosuite_verifyurl (https://your-server/verify)."""
import json, os, sys, urllib.request
from http.server import BaseHTTPRequestHandler, HTTPServer
HERE = os.path.dirname(os.path.abspath(__file__))
SA = os.environ.get('PLAY_SERVICE_ACCOUNT_FILE', os.path.join(HERE, 'play_service_account.json'))
PKG = 'com.mintoosuite.terminal'


def token():
    from google.oauth2 import service_account
    from google.auth.transport.requests import Request
    c = service_account.Credentials.from_service_account_file(SA, scopes=['https://www.googleapis.com/auth/androidpublisher'])
    c.refresh(Request())
    return c.token


def active(ptoken):
    url = 'https://androidpublisher.googleapis.com/androidpublisher/v3/applications/%s/purchases/subscriptionsv2/tokens/%s' % (PKG, ptoken)
    req = urllib.request.Request(url, headers={'Authorization': 'Bearer ' + token()})
    with urllib.request.urlopen(req, timeout=20) as r:
        j = json.loads(r.read().decode())
    return j.get('subscriptionState') in ('SUBSCRIPTION_STATE_ACTIVE', 'SUBSCRIPTION_STATE_IN_GRACE_PERIOD')


class H(BaseHTTPRequestHandler):
    def _send(self, code, obj):
        b = json.dumps(obj).encode()
        self.send_response(code); self.send_header('content-type', 'application/json'); self.send_header('access-control-allow-origin', '*')
        self.send_header('access-control-allow-headers', 'content-type'); self.end_headers(); self.wfile.write(b)

    def do_OPTIONS(self):
        self._send(204, {})

    def do_POST(self):
        try:
            n = int(self.headers.get('content-length', '0'))
            j = json.loads(self.rfile.read(min(n, 4096)).decode())
            t = str(j.get('token', ''))
            if not t or len(t) > 1000 or not all(ch.isalnum() or ch in '-_.' for ch in t):
                return self._send(400, {'active': False})
            self._send(200, {'active': bool(active(t))})
        except Exception as e:
            self._send(200, {'active': False, 'error': str(e)[:80]})

    def log_message(self, *a):
        pass


if __name__ == '__main__':
    HTTPServer(('127.0.0.1', int(os.environ.get('PORT', '8099'))), H).serve_forever()
