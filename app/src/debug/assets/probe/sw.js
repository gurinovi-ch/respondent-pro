// Проба: кэш страницы и данных, чтобы опрос открывался без сети.
const CACHE = 'probe-v1';

self.addEventListener('install', (event) => {
  self.skipWaiting();
  event.waitUntil(caches.open(CACHE).then((c) => c.addAll(['index.html', 'sw.js', 'data.json'])));
});

self.addEventListener('activate', (event) => {
  event.waitUntil(self.clients.claim());
});

self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);
  if (url.pathname.endsWith('/data.json')) {
    event.respondWith((async () => {
      const cache = await caches.open(CACHE);
      const cached = await cache.match('data.json');
      if (cached) return cached;
      const response = await fetch(event.request);
      await cache.put('data.json', response.clone());
      return response;
    })());
    return;
  }
  event.respondWith((async () => {
    const cache = await caches.open(CACHE);
    const cached = await cache.match(event.request);
    return cached || fetch(event.request);
  })());
});
