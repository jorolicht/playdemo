import { test, expect, Browser, BrowserContext, Page } from '@playwright/test';
import fs from 'fs';
import path from 'path';

/**
 * UseCase 38: Matchboard – Namen der Spieler wie in der Ergebniseingabe: Einzel „Nachname, Vorname“, länger als
 * 20 Zeichen mit gekürztem Vornamen (pages.Stage.StageInput.formatSnoName, shared.utils.PlayerName).
 *
 * Cloud-Turnier aus den Demo-Daten; Spielerin 59 (Fehr, Luzie) heißt hier „Müller-Lüdenscheidt“. D-Klasse Damen Einzel,
 * Vorrunde, Spiel 3: Brandenburg, Mark – Müller-Lüdenscheidt, Luzie. Die Turnierleitung weist Tisch 9 zu; die Meldung
 * an den Play-Server (/matchboard/set) und die Anzeigetafel enthalten die Namen im neuen Format.
 */

const USER = process.env.WP_ADMIN_USER || '';
const PASS = process.env.WP_ADMIN_PASSWORD || '';

test.describe.configure({ mode: 'serial' });

test.describe('UseCase 38: Matchboard – Spielernamen', () => {
  test.skip(!USER || !PASS, 'WP_ADMIN_USER/WP_ADMIN_PASSWORD nicht gesetzt');
  test.skip(({ browserName }) => browserName !== 'chromium', 'nur Chromium (Cloud-Turnier)');

  let admin: BrowserContext;
  let page: Page;
  let slug = '';
  let postId = 0;

  /** REST-Aufruf im Seitenkontext (mit Nonce und Cookies). */
  async function api(method: string, route: string, body?: any): Promise<any> {
    return await page.evaluate(async ([method, route, body]: any) => {
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

  test.beforeAll(async ({ browser }: { browser: Browser }) => {
    admin = await browser.newContext({ ignoreHTTPSErrors: true, locale: 'de-DE' });
    page = await admin.newPage();
    await page.goto('/login/');
    await page.locator('input[id*="LoginId"]').fill(USER);
    await page.locator('input[id*="PasswordId"]').fill(PASS);
    await page.locator('button[id*="BtnLogin"]').click();
    await expect.poll(async () => (await admin.cookies()).some(c => c.name.startsWith('wordpress_logged_in')),
                      { timeout: 10000 }).toBe(true);
    await page.goto('/');

    const demo = JSON.parse(fs.readFileSync(path.resolve(__dirname, '../../../wp-plugin/data/demo_tourney.json'), 'utf8'));
    const t = demo.tourney.tourney;
    t.wpId = 0;
    t.name = `Matchboard-Namen ${Date.now() % 100000}`;
    t.players.find((p: any) => p && p.id === 59).lastName = 'Müller-Lüdenscheidt';
    const res = await api('POST', 'create', t);
    expect(res.status, JSON.stringify(res.json)).toBe(200);
    slug = res.json.slug;
    postId = res.json.pageId;
    expect((await api('POST', `clubs-sync?postId=${postId}`, { version: 0, clubs: t.clubs })).status).toBe(200);
    expect((await api('POST', `players-sync?postId=${postId}`, { version: 0, players: t.players })).status).toBe(200);
    expect((await api('POST', `competitions-sync?postId=${postId}`, { events: t.competitions.filter((c: any) => c) })).status).toBe(200);
    expect((await api('POST', `stages-sync?postId=${postId}`, { events: t.stages.filter((s: any) => s) })).status).toBe(200);
  });

  test.afterAll(async () => {
    if (postId && page) await api('DELETE', `delete?postId=${postId}`).catch(() => {});
    await admin?.close();
  });

  test('TC-MB-02 [👤]: Tischzuweisung meldet „Nachname, Vorname“ (über 20 Zeichen gekürzt) ans Matchboard', async () => {
    await page.goto(`/${slug}/`);
    await page.waitForFunction(() => typeof (window as any).appLoadPage === 'function');
    await expect(async () => {
      await page.evaluate(() => (window as any).appLoadPage('TourneyInfo', ''));
      await page.locator('tr', { hasText: 'D-Klasse Damen Einzel' }).locator('.comp-link').click({ timeout: 2000 });
    }).toPass({ timeout: 15000 });
    await page.locator('.stage-link', { hasText: 'Vorrunde' }).click();
    await page.evaluate(() => (window as any).appLoadPage('StageInput', ''));
    await expect(page.locator('#nameB-3')).toHaveText('Müller-Lüdenscheidt, L.', { timeout: 10000 });

    const sent = page.waitForRequest(r => r.url().includes('/matchboard/set') && (r.postData() || '').includes('"start"'));
    await page.locator('#table_3').fill('9');
    await page.locator('#table_3').press('Tab');
    const body = (await sent).postDataJSON();
    expect(body.entry.nameA).toBe('Brandenburg, Mark');
    expect(body.entry.nameB).toBe('Müller-Lüdenscheidt, L.');
    expect(body.entry.court).toBe('9');

    // Anzeigetafel zeigt das Spiel mit den Namen
    await page.evaluate(() => (window as any).appLoadPage('TourneyInfo', ''));
    const href = await page.locator('a[href*="/matchboard/view/"]').first().getAttribute('href', { timeout: 15000 });
    const board = await (await page.context().browser()!.newContext({ ignoreHTTPSErrors: true })).newPage();
    await board.goto(new URL(href!, page.url()).toString());
    await expect(board.locator('body')).toContainText('Müller-Lüdenscheidt, L.', { timeout: 15000 });
    await expect(board.locator('body')).toContainText('Brandenburg, Mark');
    await board.context().close();
  });
});
