self.addEventListener('install', (e) => {
  e.skipWaiting();
});
self.addEventListener('activate', (e) => {
  e.clients.claim();
});
self.addEventListener('fetch', (e) => {
  e.respondWith(fetch(e.request).catch(() => caches.match(e.request)));
});
