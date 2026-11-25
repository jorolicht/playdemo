import { test, expect, Browser, BrowserContext, Page } from '@playwright/test';

/**
 * UseCase 37: Durchsagen der Turnierleitung (includes/api/announcement.php, services.Announcements).
 *
 * Der Administrator legt ein Cloud-Turnier an und gibt in der Turnierübersicht Durchsagen ein. Sie werden in
 * WordPress gespeichert, erscheinen als Info-Eintrag auf dem Matchboard (Play-Server) und in „Mein Turnier“.
 */

const USER = process.env.WP_ADMIN_USER || '';
const PASS = process.env.WP_ADMIN_PASSWORD || '';

test.describe.configure({ mode: 'serial' });

test.describe('UseCase 37: Durchsagen der Turnierleitung', () => {
  test.skip(!USER || !PASS, 'WP_ADMIN_USER/WP_ADMIN_PASSWORD nicht gesetzt');
  test.skip(({ browserName }) => browserName !== 'chromium', 'nur Chromium (Cloud-Turnier)');

  let admin: BrowserContext, anon: BrowserContext;
  let adminPage: Page, page: Page;
  let slug = '';
  let postId = 0;
  const stamp = Date.now() % 100000;
  const TEXT = `Siegerehrung Herren A um 16 Uhr (${stamp})`;

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
      return { status: r.status, json: await r.json().catch(() => null) };
    }, [method, route, body]);
  }

  /** Info-Texte des Matchboards (Play-Server) für das Turnier. */
  async function boardInfos(p: Page): Promise<string[]> {
    return await p.evaluate(async ([slug, postId]: any) => {
      const el = document.getElementById('Main_ParamId')!;
      // wie ComWrapper.genPath: ohne playurl auf localhost über /srv
      const play = (el.dataset.playurl || (location.hostname === 'localhost' ? '/srv' : '')).replace(/\/$/, '');
      const key = `${String(slug).split('/').pop()}-${postId}`;
      const r = await fetch(`${play}/matchboard/get?slug=${encodeURIComponent(key)}`, {
        method: 'POST', headers: { 'Content-Type': 'application/json', 'Csrf-Token': 'nocheck' }, body: '""' });
      const mb = await r.json();
      return mb.entries.filter((e: any) => e.entryType === 'info').map((e: any) => e.text);
    }, [slug, postId]);
  }

  /** Öffnet „Mein Turnier“ als Besucher, nachdem die Turnierseite geladen ist (sonst überschreibt der Start). */
  async function openMyTourney() {
    await page.goto(`/${slug}/`);
    await expect(page.getByRole('heading', { name: /Durchsagen-Test/ })).toBeVisible({ timeout: 15000 });
    await page.evaluate(() => (window as any).appLoadPage('MyTourney', ''));
    await expect(page.locator('[data-testid="my-tourney"]')).toBeVisible({ timeout: 15000 });
  }

  /** Öffnet die Turnierübersicht des Testturniers als Administrator. */
  async function openTourneyInfo() {
    await adminPage.goto(`/${slug}/`);
    await adminPage.waitForFunction(() => typeof (window as any).appLoadPage === 'function');
    await adminPage.evaluate(() => (window as any).appLoadPage('TourneyInfo', ''));
    await expect(adminPage.locator('[data-testid="announce-card"]')).toBeVisible({ timeout: 15000 });
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
    const t = { wpId: 0, name: `Durchsagen-Test ${stamp}`, organizer: 'TTC Test', startDate: dateInt, endDate: dateInt,
                ident: '', category: 'TT', clubs: [], players: [], competitions: [], stages: [], address: null };
    const res = await api(adminPage, 'POST', 'create', t);
    expect(res.status, JSON.stringify(res.json)).toBe(200);
    slug = res.json.slug;
    postId = res.json.pageId;

    anon = await browser.newContext({ ignoreHTTPSErrors: true, locale: 'de-DE' });
    page = await anon.newPage();
  });

  test.afterAll(async () => {
    if (postId && adminPage) await api(adminPage, 'DELETE', `delete?postId=${postId}`).catch(() => {});
    await anon?.close();
    await admin?.close();
  });

  test('TC-ANN-01 [👤]: Durchsage in der Turnierübersicht senden – gespeichert und auf dem Matchboard', async () => {
    await openTourneyInfo();
    await expect(adminPage.locator('[data-testid="announce-empty"]')).toBeVisible();
    const input = adminPage.locator('input[id$="InputAnnounce"]');
    await expect(input).toHaveAttribute('maxlength', '200');
    await input.fill(TEXT);
    const saved = adminPage.waitForResponse(r => r.url().includes('/announcements') && r.request().method() === 'POST');
    await adminPage.locator('button[id$="BtnAnnounce"]').click();
    expect((await saved).status()).toBe(200);
    await expect(adminPage.locator('[data-testid="announce-text"]')).toHaveText([TEXT]);
    await expect(input).toHaveValue('');

    const stored = await api(adminPage, 'GET', `announcements?postId=${postId}`);
    expect(stored.json.announcements.map((a: any) => a.text)).toEqual([TEXT]);
    await expect.poll(() => boardInfos(adminPage), { timeout: 10000 }).toContain(TEXT);
  });

  test('TC-ANN-02 [🌐]: „Mein Turnier“ zeigt die Durchsage ohne Anmeldung', async () => {
    await openMyTourney();
    const box = page.locator('[data-testid="my-announcements"]');
    await expect(box).toBeVisible({ timeout: 15000 });
    await expect(box.locator('[data-testid="announce-text"]')).toHaveText([TEXT]);
    await expect(box.locator('button')).toHaveCount(0);   // kein Löschen für Spieler
  });

  test('TC-ANN-03 [🌐]: Ohne Schreibrecht kein Senden und Löschen; zu lange Texte werden abgelehnt', async () => {
    const add = await api(page, 'POST', `announcements?postId=${postId}`, { text: 'Fremde Durchsage' });
    expect([401, 403]).toContain(add.status);
    const del = await api(page, 'DELETE', `announcements?postId=${postId}&id=x`);
    expect([401, 403]).toContain(del.status);
    const long = await api(adminPage, 'POST', `announcements?postId=${postId}`, { text: 'x'.repeat(201) });
    expect(long.status).toBe(400);
    expect((await api(page, 'GET', `announcements?postId=${postId}`)).json.announcements).toHaveLength(1);
  });

  test('TC-ANN-04 [👤]: Durchsage löschen – auch auf dem Matchboard und in „Mein Turnier“', async () => {
    await openTourneyInfo();
    await expect(adminPage.locator('[data-testid="announce-text"]')).toHaveText([TEXT], { timeout: 10000 });
    await adminPage.locator('[data-testid="announce-item"]').locator('button[id*="BtnAnnounceDelete-"]').click();
    await expect(adminPage.locator('[data-testid="announce-empty"]')).toBeVisible({ timeout: 10000 });
    expect((await api(adminPage, 'GET', `announcements?postId=${postId}`)).json.announcements).toHaveLength(0);
    await expect.poll(() => boardInfos(adminPage), { timeout: 10000 }).not.toContain(TEXT);

    await openMyTourney();
    await expect(page.locator('[data-testid="my-tourney"]')).toBeVisible({ timeout: 15000 });
    await expect(page.locator('[data-testid="my-announcements"]')).toHaveCount(0);
  });
});
