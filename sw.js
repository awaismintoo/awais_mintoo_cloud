self.addEventListener('install', (event) => {
    self.skipWaiting();
});

self.addEventListener('activate', (event) => {
    event.waitUntil(clients.claim());
});

self.addEventListener('message', (event) => {
    if (event.data && event.data.type === 'TRIGGER_ALERT') {
        const title = event.data.title || 'MintooSuite Signal Alert';
        const options = {
            body: event.data.body || 'Exness Gold Confluence Confirmed.',
            icon: './icon.png',
            badge: './badge.png',
            tag: 'mintoo-trade-signal',
            renotify: true,
            requireInteraction: true
        };
        self.registration.showNotification(title, options);
    }
});
