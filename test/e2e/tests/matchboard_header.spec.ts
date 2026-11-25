import { test, expect, Browser, BrowserContext, Page } from '@playwright/test';

/**
 * UseCase 38: Kopfleiste des Matchboards (InfoAnzeige, Play-Server server/app/views/MatchboardView.scala.html).
 *
 * Wie das Hauptmenü der App (#336699): links das Logo (Vereinslogo des Turniers, sonst Logo des Turnierprogramms),
 * mittig der Turniername (aus WordPress, Post-ID am Ende des Slugs), rechts eine Uhr. Kein Platzhalter „Turnier“.
 */

const USER = process.env.WP_ADMIN_USER || '';
const PASS = process.env.WP_ADMIN_PASSWORD || '';

test.describe.configure({ mode: 'serial' });

test.describe('UseCase 38: Matchboard – Kopfleiste', () => {
  test.skip(!USER || !PASS, 'WP_ADMIN_USER/WP_ADMIN_PASSWORD nicht gesetzt');
  test.skip(({ browserName }) => browserName !== 'chromium', 'nur Chromium (Cloud-Turnier)');

  let admin: BrowserContext;
  let adminPage: Page;
  let postId = 0;
  let slug = '';
  const name = `Matchboard-Kopf ${Date.now() % 100000}`;

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
    const res = await adminPage.evaluate(async (t: any) => {
      const el = document.getElementById('Main_ParamId')!;
      const home = (el.dataset.homeurl || '').replace(/\/$/, '');
      const r = await fetch(`${home}/wp-json/tourney/v1/create`, { method: 'POST', credentials: 'include',
        headers: { 'Content-Type': 'application/json', 'X-WP-Nonce': el.dataset.nonce || '' }, body: JSON.stringify(t) });
      return await r.json();
    }, { wpId: 0, name, organizer: 'TTC Test', startDate: dateInt, endDate: dateInt, ident: '', category: 'TT',
         clubs: [], players: [], competitions: [], stages: [], address: null });
    postId = res.pageId;
    slug = res.slug;
    expect(postId).toBeGreaterThan(0);
  });

  test.afterAll(async () => {
    if (postId) await adminPage.evaluate(async (id: number) => {
      const el = document.getElementById('Main_ParamId')!;
      const home = (el.dataset.homeurl || '').replace(/\/$/, '');
      await fetch(`${home}/wp-json/tourney/v1/delete?postId=${id}`, { method: 'DELETE', credentials: 'include',
        headers: { 'X-WP-Nonce': el.dataset.nonce || '' } });
    }, postId).catch(() => {});
    await admin?.close();
  });

  test('TC-MB-01 [🌐]: Logo links, Turniername mittig, Uhr rechts – in der Farbe des Hauptmenüs', async () => {
    // Link aus der Turnierübersicht (mit ?home=)
    await adminPage.goto(`/${slug}/`);
    await adminPage.waitForFunction(() => typeof (window as any).appLoadPage === 'function');
    await adminPage.evaluate(() => (window as any).appLoadPage('TourneyInfo', ''));
    const link = adminPage.locator('a[href*="/matchboard/view/"]').first();
    await expect(link).toHaveAttribute('href', new RegExp(`-${postId}\\?home=`), { timeout: 15000 });
    const href = await link.getAttribute('href');

    // Matchboard ohne Anmeldung öffnen
    const board = await (await adminPage.context().browser()!.newContext({ ignoreHTTPSErrors: true })).newPage();
    await board.goto(new URL(href!, adminPage.url()).toString());
    const bar = board.locator('header.topbar');
    await expect(bar).toHaveCSS('background-color', 'rgb(51, 102, 153)');
    await expect(board.locator('#tourney-name')).toHaveText(name, { timeout: 10000 });
    await expect(board).toHaveTitle(`Anzeigetafel - ${name}`);
    await expect(board.locator('#mb-logo')).toHaveAttribute('src', /\/wp-content\/plugins\/tourney\/img\/logo\.png$/);
    await expect.poll(() => board.locator('#mb-logo').evaluate((i: HTMLImageElement) => i.complete ? i.naturalWidth : 0)).toBeGreaterThan(0);
    await expect(board.locator('#mb-clock')).toHaveText(/^\d{2}:\d{2}$/);
    await expect(board.locator('body')).not.toContainText(/^Turnier$/m);

    // Reihenfolge: Logo links, Name mittig, Uhr rechts
    const lx = (await board.locator('#mb-logo').boundingBox())!.x;
    const nb = (await board.locator('#tourney-name').boundingBox())!;
    const cx = (await board.locator('#mb-clock').boundingBox())!.x;
    const vw = board.viewportSize()!.width;
    expect(lx).toBeLessThan(nb.x);
    expect(nb.x + nb.width).toBeLessThan(cx);
    expect(Math.abs(nb.x + nb.width / 2 - vw / 2)).toBeLessThan(40);
    await board.locator('header.topbar').screenshot({ path: 'test-results/matchboard-topbar.png' });
    await board.context().close();
  });
});
