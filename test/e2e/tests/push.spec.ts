import { test, expect, Browser, BrowserContext, Page } from '@playwright/test';

/**
 * UseCase 40: Push-Benachrichtigungen für Durchsagen (includes/api/push.php, includes/push-sw.js, services.Push).
 *
 * Die echte Zustellung über einen Push-Dienst (Google, Apple, Mozilla) ist im Test-Browser nicht möglich (die Anmeldung
 * beim Push-Dienst hängt in Playwright-Chromium). Geprüft wird alles bis zur Übergabe: Schlüssel, Service Worker,
 * An-/Abmeldung, Versand beim Senden einer Durchsage (ohne Abbruch bei Fehlern), Knopf und Direktlink in „Mein Turnier“.
 */

const USER = process.env.WP_ADMIN_USER || '';
const PASS = process.env.WP_ADMIN_PASSWORD || '';

/**
 * Formal gültige Anmeldung mit einer Push-Adresse ohne Push-Dienst (example.com antwortet mit 405): Die Zustellung
 * scheitert, gilt aber nicht als abgelaufen (das melden Push-Dienste mit 404/410 – im Test nicht nachstellbar).
 * Adressen, die sich nicht auflösen lassen oder intern sind, lehnt WordPress schon bei der Anmeldung ab
 * (wp_http_validate_url).
 */
const fakeSub = (n: number) => ({
  endpoint: `https://example.com/push-test/${n}-${Date.now()}`,
  keys: { p256dh: 'B' + 'A'.repeat(86), auth: 'a'.repeat(22) },
});

test.describe.configure({ mode: 'serial' });

test.describe('UseCase 40: Push-Benachrichtigungen für Durchsagen', () => {
  test.skip(!USER || !PASS, 'WP_ADMIN_USER/WP_ADMIN_PASSWORD nicht gesetzt');
  test.skip(({ browserName }) => browserName !== 'chromium', 'nur Chromium (Cloud-Turnier)');

  let admin: BrowserContext, anon: BrowserContext;
  let adminPage: Page, page: Page;
  let slug = '';
  let postId = 0;
  const stamp = Date.now() % 100000;

  /** REST-Aufruf im Seitenkontext (mit Nonce und Cookies). */
  async function api(p: Page, method: string, route: string, body?: any): Promise<any> {
    return await p.evaluate(async ([method, route, body]: any) => {
      const el = document.getElementById('Main_ParamId')!;
      const home = (el.dataset.homeurl || '').replace(/\/$/, '');
      const r = await fetch(`${home}/wp-json/tourney/v1/${route}`, {
        method, credentials: 'include',
        headers: { 'Content-Type': 'application/json', 'X-WP-Nonce': el.dataset.nonce || '' },
        body: body === undefined ? undefined : JSON.stringify(body),
      });
      return { status: r.status, json: await r.json().catch(() => null), headers: Object.fromEntries(r.headers.entries()) };
    }, [method, route, body]);
  }

  test.beforeAll(async ({ browser }: { browser: Browser }) => {
    admin = await browser.newContext({ ignoreHTTPSErrors: true, locale: 'de-DE' });
    adminPage = await admin.newPage();
    await adminPage.goto('/login/');
    await adminPage.locator('input[id*="LoginId"]').fill(USER);
    await adminPage.locator('input[id*="PasswordId"]').fill(PASS);
    await adminPage.locator('button[id*="BtnLogin"]').click();
    await expect.poll(async () => (await admin.cookies()).some(c => c.name.startsWith('wordpress_logged_in')),
                      { timeout: 10000 }).toBe(true);
    await adminPage.goto('/');
    const d = new Date(Date.now() + 30 * 86400000);
    const dateInt = Number(`${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, '0')}${String(d.getDate()).padStart(2, '0')}`);
    const res = await api(adminPage, 'POST', 'create', { wpId: 0, name: `Push-Test ${stamp}`, organizer: 'TTC Test',
      startDate: dateInt, endDate: dateInt, ident: '', category: 'TT', clubs: [], players: [], competitions: [], stages: [], address: null });
    expect(res.status, JSON.stringify(res.json)).toBe(200);
    slug = res.json.slug;
    postId = res.json.pageId;

    anon = await browser.newContext({ ignoreHTTPSErrors: true, locale: 'de-DE' });
    page = await anon.newPage();
    await page.goto('/');
  });

  test.afterAll(async () => {
    if (postId && adminPage) await api(adminPage, 'DELETE', `delete?postId=${postId}`).catch(() => {});
    await anon?.close();
    await admin?.close();
  });

  test('TC-PUSH-01 [🌐]: Öffentlicher Schlüssel und Service Worker werden ausgeliefert', async ({ request }) => {
    const key = await api(page, 'GET', 'push-key');
    expect(key.status).toBe(200);
    expect(key.json.publicKey).toMatch(/^[A-Za-z0-9_-]{80,90}$/);
    expect((await api(page, 'GET', 'push-key')).json.publicKey).toBe(key.json.publicKey);   // bleibt gleich
    const sw = await request.get('/?tourney_push=sw');
    expect(sw.status()).toBe(200);
    expect(sw.headers()['content-type']).toContain('javascript');
    const js = await sw.text();
    expect(js).toContain('showNotification');
    expect(js).toContain('tourney-announcement');
  });

  test('TC-PUSH-02 [🌐]: Anmelden und Abmelden; ungültige Anmeldungen werden abgelehnt', async () => {
    expect((await api(page, 'POST', `push-subscribe?postId=${postId}`, { subscription: { endpoint: 'http://unsicher.example/x' } })).status).toBe(400);
    expect((await api(page, 'POST', `push-subscribe?postId=${postId}`, { subscription: { ...fakeSub(9), endpoint: 'https://127.0.0.1/intern' } })).status).toBe(400);
    expect((await api(page, 'POST', `push-subscribe?postId=999999999`, { subscription: fakeSub(0) })).status).toBe(404);
    const sub = fakeSub(1);
    const ok = await api(page, 'POST', `push-subscribe?postId=${postId}`, { subscription: sub, lang: 'de' });
    expect(ok.status).toBe(200);
    expect(ok.json.subscribed).toBe(true);
    // erneute Anmeldung desselben Geräts ist erlaubt (wird aktualisiert)
    expect((await api(page, 'POST', `push-subscribe?postId=${postId}`, { subscription: sub, lang: 'en' })).status).toBe(200);
    const off = await api(page, 'POST', `push-unsubscribe?postId=${postId}`, { endpoint: sub.endpoint });
    expect(off.json.subscribed).toBe(false);
  });

  test('TC-PUSH-03 [👤]: Durchsage wird gespeichert und versandt; Zustellfehler brechen nicht ab', async () => {
    const sub = fakeSub(2);
    expect((await api(page, 'POST', `push-subscribe?postId=${postId}`, { subscription: sub })).status).toBe(200);
    const res = await api(adminPage, 'POST', `announcements?postId=${postId}`, { text: `Push-Durchsage ${stamp}` });
    expect(res.status).toBe(200);
    expect(res.json.announcements[0].text).toBe(`Push-Durchsage ${stamp}`);
    // Versand wurde versucht und ist gescheitert (405), die Durchsage ist trotzdem gespeichert, die Anmeldung bleibt
    expect(res.json.push).toEqual({ sent: 0, removed: 0 });
    expect((await api(page, 'POST', `push-unsubscribe?postId=${postId}`, { endpoint: sub.endpoint })).json.subscribed).toBe(false);
  });

  test('TC-PUSH-04 [🌐]: „Mein Turnier“ per Direktlink, mit Knopf bzw. Hinweis für Benachrichtigungen', async ({ browser }) => {
    // Headless-Chromium meldet Benachrichtigungen immer als blockiert → Hinweis statt Knopf
    await page.goto(`/${slug}/#mein-turnier`);
    await expect(page.locator('[data-testid="my-tourney"]')).toBeVisible({ timeout: 15000 });
    await expect(page.locator('[data-testid="my-announcements"] [data-testid="announce-text"]')).toHaveText([`Push-Durchsage ${stamp}`]);
    await expect(page.locator('[data-testid="push-denied"]')).toContainText('blockiert', { timeout: 10000 });
    await expect(page.locator('[data-testid="push-btn"]')).toHaveCount(0);

    // Browser, der noch nicht gefragt hat: Knopf zum Einschalten
    const ctx = await browser.newContext({ ignoreHTTPSErrors: true, locale: 'de-DE' });
    await ctx.addInitScript(() => Object.defineProperty(Notification, 'permission', { get: () => 'default' }));
    const p2 = await ctx.newPage();
    await p2.goto(`/${slug}/#mein-turnier`);
    const btn = p2.locator('[data-testid="push-btn"]');
    await expect(btn).toBeVisible({ timeout: 15000 });
    await expect(btn).toHaveAttribute('data-state', 'off');
    await expect(btn).toContainText('Durchsagen als Benachrichtigung erhalten');
    await ctx.close();
  });
});
