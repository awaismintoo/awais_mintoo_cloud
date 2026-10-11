self.addEventListener('install', (event) => {
    self.skipWaiting();
});

self.addEventListener('activate', (event) => {
    event.waitUntil(clients.claim());
});

self.addEventListener('message', (event) => {
    if (event.data && event.data.type === 'TRIGGER_PUSH') {
        self.registration.showNotification(event.data.title, {
            body: event.data.body,
            icon: 'icon-192.png',
            badge: 'icon-192.png',
            vibrate: [200, 100, 200],
            tag: 'mintoosuite-trade-alert',
            renotify: true
        });
    }
});
