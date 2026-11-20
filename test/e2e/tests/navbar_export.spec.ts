import { test, expect, Browser, BrowserContext, Page, Download } from '@playwright/test';
import fs from 'fs';
import path from 'path';

/**
 * Main Navbar, "Meine Turniere → Turnier in Datei exportieren" (Navbar.doLocalExport / exportCandidates).
 *
 * Exportiert werden kann das lokale Turnier (im Browser, App.demo_tourney) und das geöffnete Cloud-Turnier mit
 * Schreibrecht. Gibt es beide, fragt ein Dialog, welches gespeichert werden soll. Der Reiter "Import / Export" in der
 * Turnier-Administration entfällt.
 */

const USER = process.env.WP_ADMIN_USER || '';
const PASS = process.env.WP_ADMIN_PASSWORD || '';

test.describe.configure({ mode: 'serial' });

test.describe('Main Navbar: Turnier in Datei exportieren', () => {
  test.skip(!USER || !PASS, 'WP_ADMIN_USER/WP_ADMIN_PASSWORD nicht gesetzt');
  test.skip(({ browserName }) => browserName !== 'chromium', 'nur Chromium (legt ein Cloud-Turnier an)');

  let ctx: BrowserContext;
  let page: Page;
  let slug = '';
  let postId = 0;
  const stamp = Date.now();
  const cloudName = `Export-Test Cloud ${stamp}`;
  const localName = `Export-Test Lokal ${stamp}`;
  const demo = JSON.parse(fs.readFileSync(path.resolve(__dirname, '../../../wp-plugin/data/demo_tourney.json'), 'utf8'));

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

  /** Legt das lokale Turnier im Browser ab (wie nach "Öffnen/Archiv"). */
  async function storeLocalTourney() {
    const req = { ...demo.tourney, tourney: { ...demo.tourney.tourney, name: localName, wpId: 999999 } };
    await page.evaluate((v: string) => window.localStorage.setItem('App.demo_tourney', v), JSON.stringify(req));
  }

  /** Öffnet das Cloud-Turnier als Turnierleiter (Turnierübersicht). */
  async function openCloudTourney() {
    await page.goto(`/${slug}/`);
    await expect(page.locator('#myTourneysDropdown')).toBeVisible({ timeout: 15000 });
    await expect(page.locator('main, #Main_ContentId, body').getByText(cloudName, { exact: true }).locator('visible=true').first()).toBeVisible({ timeout: 15000 });
  }

  /** Klickt "Meine Turniere → Turnier in Datei exportieren". */
  async function clickExport() {
    await page.locator('#myTourneysDropdown').click();
    const item = page.locator('a[id*="ExportLocalId"]');
    await expect(item).toHaveText(/Turnier in Datei exportieren/);
    await expect(item).not.toHaveClass(/disabled/);
    await item.click();
  }

  /** Bestätigt den Dateinamen-Dialog und liefert den Inhalt der heruntergeladenen Datei. */
  async function confirmDownload(): Promise<any> {
    await expect(page.locator('div[id*="DlgExportTourney_ModalId"].modal')).toBeVisible({ timeout: 5000 });
    // eindeutiger Dateiname, sonst fragt der Dialog nach dem Überschreiben einer früheren Exportdatei
    await page.locator('input[id*="DlgExportTourney_InputFilenameId"]').fill(`export_test_${Date.now()}.json`);
    const [download]: [Download, void] = await Promise.all([
      page.waitForEvent('download', { timeout: 7000 }),
      page.locator('button[id*="DlgExportTourney_ApplyId"]').click(),
    ]);
    return JSON.parse(fs.readFileSync(await download.path(), 'utf8'));
  }

  test.beforeAll(async ({ browser }: { browser: Browser }) => {
    ctx = await browser.newContext({ ignoreHTTPSErrors: true, locale: 'de-DE', acceptDownloads: true });
    page = await ctx.newPage();
    await page.goto('/login/');
    await page.locator('input[id*="LoginId"]').fill(USER);
    await page.locator('input[id*="PasswordId"]').fill(PASS);
    await page.locator('button[id*="BtnLogin"]').click();
    await expect.poll(async () => (await ctx.cookies()).some(c => c.name.startsWith('wordpress_logged_in')),
                      { timeout: 10000 }).toBe(true);
    await page.goto('/');
    const d = new Date(Date.now() + 20 * 86400000);
    const day = d.getFullYear() * 10000 + (d.getMonth() + 1) * 100 + d.getDate();
    const t = { wpId: 0, name: cloudName, organizer: 'TTC Test', startDate: day, endDate: day,
                ident: '', category: 'TT', clubs: [], players: [], competitions: [], stages: [], address: null };
    const res = await api('POST', 'create', t);
    expect(res.status, JSON.stringify(res.json)).toBe(200);
    slug = res.json.slug;
    postId = res.json.pageId;
  });

  test.afterAll(async () => {
    if (postId && page) await api('DELETE', `delete?postId=${postId}`).catch(() => {});
    await ctx?.close();
  });

  test('TC-NB-13 [👤]: Lokales und Cloud-Turnier vorhanden: Auswahldialog, Export des gewählten Turniers', async () => {
    await page.goto('/');
    await storeLocalTourney();
    await openCloudTourney();

    // Cloud-Turnier wählen
    await clickExport();
    const box = page.locator('.modal.show', { hasText: 'Welches Turnier' });
    await expect(box).toBeVisible();
    await expect(box.getByRole('button', { name: `Lokales Turnier „${localName}“` })).toBeVisible();
    await box.getByRole('button', { name: `Cloud-Turnier „${cloudName}“` }).click();
    const cloudFile = await confirmDownload();
    expect(cloudFile.tourney.tourney.name).toBe(cloudName);
    expect(cloudFile.tourney.tourney.wpId).toBe(postId);

    // Lokales Turnier wählen: Export aus dem Browserspeicher, das Cloud-Turnier bleibt geöffnet
    await clickExport();
    await page.locator('.modal.show', { hasText: 'Welches Turnier' }).getByRole('button', { name: `Lokales Turnier „${localName}“` }).click();
    const localFile = await confirmDownload();
    expect(localFile.tourney.tourney.name).toBe(localName);
    expect(localFile.tourney.version).toBe(demo.tourney.version);
    await expect(page.locator('main, #Main_ContentId, body').getByText(cloudName, { exact: true }).locator('visible=true').first()).toBeVisible();
  });

  test('TC-NB-14 [👤]: Nur Cloud-Turnier: Export ohne Auswahldialog', async () => {
    await page.evaluate(() => window.localStorage.removeItem('App.demo_tourney'));
    await openCloudTourney();
    await clickExport();
    await expect(page.locator('.modal.show', { hasText: 'Welches Turnier' })).toHaveCount(0);
    const file = await confirmDownload();
    expect(file.tourney.tourney.name).toBe(cloudName);
  });

  test('TC-TA-IE-01 [👤]: Turnier-Administration ohne Reiter "Import / Export"', async () => {
    await openCloudTourney();
    await page.evaluate(() => (window as any).appLoadPage('TourneyAdmin', 'IMPEXP'));
    const tabs = page.locator('ul.nav-tabs');
    await expect(tabs).toBeVisible({ timeout: 10000 });
    await expect(tabs).not.toContainText('Import / Export');
    await expect(tabs.locator('a.nav-link.active')).toContainText('Urkunden');
  });
});
