const CACHE='nextmove-dashboard-v2.0.8-test-v10';
const ASSETS=['./','index.html','styles.css?v=10','header-sync.css?v=7','app.js?v=7','dashboard-nav.js?v=1','sync-button.js?v=7','notification-permission.js?v=1','manifest.webmanifest','icons/nextmove-icon.svg','icons/sync-icon.svg'];
self.addEventListener('install',e=>e.waitUntil(caches.open(CACHE).then(c=>c.addAll(ASSETS)).then(()=>self.skipWaiting())));
self.addEventListener('activate',e=>e.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(k=>k!==CACHE).map(k=>caches.delete(k)))).then(()=>self.clients.claim())));
self.addEventListener('fetch',e=>{if(e.request.method!=='GET')return;e.respondWith(fetch(e.request).then(r=>{const copy=r.clone();caches.open(CACHE).then(c=>c.put(e.request,copy));return r;}).catch(()=>caches.match(e.request).then(r=>r||caches.match('./'))));});
