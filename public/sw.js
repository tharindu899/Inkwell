/* ══════════════════════════════════════════
   Inkwell — public/sw.js
   Network-Only Service Worker
   ─────────────────────────────────────────
   No caching. Every request always goes
   straight to the network. The app works
   fully online only — no offline fallback.
   ══════════════════════════════════════════ */

const SW_VERSION = '1.0.6';

/* ── Install: skip waiting so the new SW takes over immediately ── */
self.addEventListener('install', (event) => {
  console.log(`[Inkwell SW v${SW_VERSION}] Installing...`);
  self.skipWaiting();
});

/* ── Activate: claim all open clients immediately + wipe any old caches ── */
self.addEventListener('activate', (event) => {
  console.log(`[Inkwell SW v${SW_VERSION}] Activating...`);
  event.waitUntil(
    Promise.all([
      /* Remove ALL cache storage left by any previous version */
      caches.keys().then((keys) =>
        Promise.all(keys.map((key) => {
          console.log(`[Inkwell SW] Deleting old cache: ${key}`);
          return caches.delete(key);
        }))
      ),
      /* Take control of every open tab immediately */
      self.clients.claim(),
    ])
  );
});

/* ── Fetch: pure network-only — no cache reads, no cache writes ── */
self.addEventListener('fetch', (event) => {
  /* Only intercept GET requests; let everything else pass through */
  if (event.request.method !== 'GET') return;

  event.respondWith(
    fetch(event.request).catch((err) => {
      /* Network failed — let the browser show its own offline error.
         We intentionally do NOT serve a cached fallback. */
      console.warn('[Inkwell SW] Network request failed:', event.request.url, err);
      return Response.error();
    })
  );
});
