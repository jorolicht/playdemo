/**
 * Service Worker für Push-Benachrichtigungen (Durchsagen der Turnierleitung).
 *
 * Wird von includes/api/push.php unter <home>/?tourney_push=sw ausgeliefert, Scope <home>/tourney-push/ (steuert keine
 * Seiten, nur Push). Inhalt einer Nachricht: {title, body, url, tag, postId, icon}.
 * - push:              Benachrichtigung anzeigen und offenen Seiten melden ("tourney-announcement"), damit „Mein Turnier“
 *                      die Durchsagen sofort neu lädt
 * - notificationclick: offenes Fenster des Turniers nach vorn holen bzw. „Mein Turnier“ öffnen (url mit #mein-turnier)
 */

self.addEventListener("install", () => self.skipWaiting());
self.addEventListener("activate", (event) => event.waitUntil(self.clients.claim()));

self.addEventListener("push", (event) => {
  let data = {};
  try {
    data = event.data ? event.data.json() : {};
  } catch (e) {
    data = { body: event.data ? event.data.text() : "" };
  }
  event.waitUntil((async () => {
    await self.registration.showNotification(data.title || "Turnier", {
      body: data.body || "",
      tag: data.tag || "tourney",
      renotify: true,
      icon: data.icon,
      data: { url: data.url, postId: data.postId },
    });
    const wins = await self.clients.matchAll({ type: "window", includeUncontrolled: true });
    wins.forEach((c) => c.postMessage({ type: "tourney-announcement", postId: data.postId }));
  })());
});

self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const url = event.notification.data && event.notification.data.url;
  event.waitUntil((async () => {
    const wins = await self.clients.matchAll({ type: "window", includeUncontrolled: true });
    const base = url ? url.split("#")[0] : "";
    for (const c of wins) {
      if (base && c.url.split("#")[0] === base && "focus" in c) {
        c.postMessage({ type: "tourney-open-mytourney" });
        return c.focus();
      }
    }
    if (url) return self.clients.openWindow(url);
  })());
});
