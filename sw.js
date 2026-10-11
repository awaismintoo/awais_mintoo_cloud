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
