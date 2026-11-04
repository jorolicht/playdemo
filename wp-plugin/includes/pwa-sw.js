/**
 * Service Worker der Offline-App /app/ (Website-Variante, TOURNEY_FWS = true).
 *
 * Wird von includes/pwa.php unter <home>/?tourney_pwa=sw ausgeliefert; davor steht
 * `self.TOURNEY_PWA = { version: "<TOURNEY_VERSION>", precache: [...] }`. Scope ist /app/.
 *
 * Strategien (nur GET):
 * - Seitenaufrufe:              Network-first, offline die zuletzt geladene App-Seite
 * - Plugin-Daten (data/*.json): Network-first, Cache-Schlüssel ohne Query (Client hängt ?t=<zeit> an)
 * - Statische Dateien:          mit Query (?ver=, ?v=) Cache-first, sonst Stale-while-revalidate
 * REST-Aufrufe, Admin/Login und schreibende Requests laufen unverändert ins Netz.
 */

const CONFIG      = self.TOURNEY_PWA || {};
const VERSION     = CONFIG.version || "0";
const PRECACHE    = Array.isArray(CONFIG.precache) ? CONFIG.precache : [];
const CACHE_PAGES = "tourney-pages-" + VERSION;
const CACHE_DATA  = "tourney-data-" + VERSION;
const CACHE_ASSET = "tourney-assets-" + VERSION;
const CACHES      = [CACHE_PAGES, CACHE_DATA, CACHE_ASSET];

/** Fremde Hosts, deren Styles/Skripte/Schriften gecacht werden (Bootstrap, Bootstrap-Icons, QR-Code). */
const CDN_HOSTS = ["cdn.jsdelivr.net", "cdnjs.cloudflare.com"];

/** Vorab-Cache laden (Fehler einzelner URLs brechen die Installation nicht ab), neue Version sofort aktivieren. */
self.addEventListener("install", (event) => {
  self.skipWaiting();
  event.waitUntil(Promise.all(PRECACHE.map(precache)));
});

/** Caches älterer Versionen löschen und offene Seiten sofort übernehmen. */
self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys
        .filter((k) => k.startsWith("tourney-") && !CACHES.includes(k))
        .map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

/**
 * Nachricht der Seite {type: "tourney-cache", urls: [...]}: Seite und alle beim ersten Laden
 * (noch ohne Service Worker) geholten Dateien nachträglich in den Cache legen.
 */
self.addEventListener("message", (event) => {
  const data = event.data || {};
  if (data.type !== "tourney-cache" || !Array.isArray(data.urls)) return;
  event.waitUntil(Promise.all(data.urls.map(precache)));
});

/**
 * Legt eine URL im passenden Cache ab, falls noch nicht vorhanden; Fehler werden ignoriert.
 *
 * @param {string} href absolute URL
 * @returns {Promise<void>}
 */
function precache(href) {
  let url;
  try { url = new URL(href, self.location.href); } catch (e) { return Promise.resolve(); }
  const sameOrigin = url.origin === self.location.origin;
  if (sameOrigin && (isRest(url) || isExcluded(url))) return Promise.resolve();
  if (!sameOrigin && !CDN_HOSTS.includes(url.hostname)) return Promise.resolve();

  const cacheName = !sameOrigin ? CACHE_ASSET : isData(url) ? CACHE_DATA : inScope(url) ? CACHE_PAGES : CACHE_ASSET;
  const key = isData(url) && sameOrigin ? dataKey(url) : url.href;
  // Fremde Dateien mit CORS laden (Schriften werden immer mit CORS angefordert und
  // dürfen keine opake Antwort erhalten), nur notfalls opak (no-cors)
  const load = () => sameOrigin
    ? fetch(url.href, { credentials: "same-origin" })
    : fetch(url.href, { mode: "cors" }).catch(() => fetch(url.href, { mode: "no-cors" }));
  return caches.open(cacheName).then((cache) =>
    cache.match(key).then((hit) => hit || load().then((res) => {
      if ((res.ok || res.type === "opaque") && !res.redirected) return cache.put(key, res);
    }))
  ).catch(() => {});
}

self.addEventListener("fetch", (event) => {
  const req = event.request;
  if (req.method !== "GET") return;
  const url = new URL(req.url);

  if (req.mode === "navigate") {
    event.respondWith(networkFirst(req, req, CACHE_PAGES, true));
  } else if (url.origin === self.location.origin) {
    if (isRest(url) || isExcluded(url)) return;
    if (isData(url)) event.respondWith(networkFirst(req, dataKey(url), CACHE_DATA, false));
    else event.respondWith(staticAsset(req, url));
  } else if (CDN_HOSTS.includes(url.hostname) && ["style", "script", "font"].includes(req.destination)) {
    event.respondWith(staticAsset(req, url));
  }
});

/**
 * @param {URL} url
 * @returns {boolean} true für WordPress-REST-Aufrufe
 */
function isRest(url) {
  return url.pathname.includes("/wp-json/") || url.searchParams.has("rest_route");
}

/**
 * @param {URL} url
 * @returns {boolean} true für Admin, Login und die PWA-Endpunkte selbst (nie cachen)
 */
function isExcluded(url) {
  return url.pathname.includes("/wp-admin/") || url.pathname.endsWith("wp-login.php") ||
         url.searchParams.has("tourney_pwa");
}

/**
 * @param {URL} url
 * @returns {boolean} true für Plugin-Daten (Nachrichten, Demo-Turnier)
 */
function isData(url) {
  return url.pathname.includes("/plugins/tourney/data/") && url.pathname.endsWith(".json");
}

/**
 * @param {URL} url
 * @returns {string} Cache-Schlüssel für Plugin-Daten: URL ohne Query
 */
function dataKey(url) {
  return url.origin + url.pathname;
}

/**
 * @param {URL} url
 * @returns {boolean} true, wenn die URL im Scope dieses Service Workers liegt
 */
function inScope(url) {
  return url.href.startsWith(self.registration.scope);
}

/**
 * Network-first: Antwort aus dem Netz (und in den Cache), offline aus dem Cache.
 *
 * @param {Request} req Anfrage ans Netz
 * @param {Request|string} key Cache-Schlüssel
 * @param {string} cacheName
 * @param {boolean} ignoreSearchFallback offline notfalls dieselbe Seite mit anderer Query liefern
 * @returns {Promise<Response>}
 */
function networkFirst(req, key, cacheName, ignoreSearchFallback) {
  return fetch(req).then((res) => {
    if (res.ok && res.type === "basic" && !res.redirected) {
      const copy = res.clone();
      caches.open(cacheName).then((cache) => cache.put(key, copy));
    }
    return res;
  }).catch(() => caches.open(cacheName).then((cache) =>
    cache.match(key).then((hit) =>
      hit || (ignoreSearchFallback ? cache.match(key, { ignoreSearch: true }) : undefined)
    ).then((hit) => hit || Response.error())
  ));
}

/**
 * Statische Datei: versionierte URLs (mit Query) Cache-first, sonst Stale-while-revalidate.
 *
 * @param {Request} req
 * @param {URL} url
 * @returns {Promise<Response>}
 */
function staticAsset(req, url) {
  return caches.open(CACHE_ASSET).then((cache) => cache.match(req).then((hit) => {
    if (hit && url.search) return hit;
    const update = fetch(req).then((res) => {
      if (res.ok || res.type === "opaque") cache.put(req, res.clone());
      return res;
    });
    if (hit) {
      update.catch(() => {});
      return hit;
    }
    return update.catch(() => Response.error());
  }));
}
