self.addEventListener('install', (event) => {
    self.skipWaiting();
});

self.addEventListener('activate', (event) => {
    event.waitUntil(clients.claim());
});

self.addEventListener('message', (event) => {
    if (event.data && event.data.type === 'TRIGGER_PUSH') {
        const uniqueTag = 'mintoo-secure-' + Date.now() + '-' + Math.floor(Math.random() * 1000);
        self.registration.showNotification(event.data.title, {
            body: event.data.body,
            icon: 'icon-192.png',
            badge: 'icon-192.png',
            vibrate: [300, 100, 300, 100, 300],
            tag: uniqueTag,
            renotify: true,
            requireInteraction: true
        });
    }
});

/* build 495: offline start. Same-site pages and icons are fetched fresh when online and the last copy is used offline. Market data and other websites are never cached here. */
const MW_CACHE = 'mintoosuite-shell-v495';
self.addEventListener('fetch', (event) => {
    const req = event.request;
    if (req.method !== 'GET') { return; }
    let url; try { url = new URL(req.url); } catch (e) { return; }
    if (url.origin !== self.location.origin) { return; }
    event.respondWith(fetch(req).then((res) => {
        if (res && res.ok) { const copy = res.clone(); caches.open(MW_CACHE).then((c) => c.put(req, copy)).catch(() => {}); }
        return res;
    }).catch(() => caches.match(req, { ignoreSearch: true }).then((r) => r || caches.match('./index.html'))));
});
