import { test, expect, Browser, BrowserContext, Page } from '@playwright/test';
import fs from 'fs';
import path from 'path';

/**
 * UseCase 29: Eigenes Logo eines Turniers (TourneyAdmin, Reiter „Homepage“; includes/api/logo.php).
 *
 * Ein Administrator legt ein Cloud-Turnier an und lädt im Reiter „Homepage“ ein Logo hoch (og-image.jpg, 1200×630).
 * Das Logo wird auf höchstens 400 px verkleinert, erscheint auf der Turnierseite und auf der Standardurkunde.
 */

const USER = process.env.WP_ADMIN_USER || '';
const PASS = process.env.WP_ADMIN_PASSWORD || '';
const IMG = path.resolve(__dirname, '../../../wp-plugin/img/og-image.jpg');
const IMG2 = path.resolve(__dirname, '../../../wp-plugin/img/pwa-512.png');

test.describe.configure({ mode: 'serial' });

test.describe('UseCase 29: Eigenes Logo', () => {
  test.skip(!USER || !PASS, 'WP_ADMIN_USER/WP_ADMIN_PASSWORD nicht gesetzt');
  test.skip(({ browserName }) => browserName !== 'chromium', 'nur Chromium (legt ein Cloud-Turnier an)');

  let admin: BrowserContext, anon: BrowserContext;
  let adminPage: Page, page: Page;
  let slug = '';
  let postId = 0;
  let firstUrl = '';

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

  /** HTTP-Status einer Adresse. */
  async function statusOf(url: string): Promise<number> {
    return (await adminPage.request.get(url, { failOnStatusCode: false })).status();
  }

  /** Öffnet die Turnier-Administration, Reiter „Homepage“. */
  async function openHomepageTab() {
    await adminPage.goto(`/${slug}/`);
    await adminPage.waitForFunction(() => typeof (window as any).appLoadPage === 'function');
    // erst die Turnierübersicht abwarten, sonst überschreibt sie die Administration
    await expect(adminPage.getByText('Turnier-Informationen')).toBeVisible({ timeout: 15000 });
    await adminPage.evaluate(() => (window as any).appLoadPage('TourneyAdmin', 'HOMEPAGE'));
    await expect(adminPage.locator('[data-testid="logo-section"]')).toBeVisible({ timeout: 10000 });
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
    const d = new Date(Date.now() + 20 * 86400000);
    const day = d.getFullYear() * 10000 + (d.getMonth() + 1) * 100 + d.getDate();
    const t = { wpId: 0, name: `Logo-Test ${Date.now()}`, organizer: 'TTC Test', startDate: day, endDate: day,
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

  test('TC-LOGO-01 [👤]: Logo hochladen: Vorschau, Verkleinerung auf 400 px, Adresse über read-aci', async () => {
    await openHomepageTab();
    await expect(adminPage.locator('#tourney-logo-none')).toBeVisible();
    const up = adminPage.waitForResponse(r => r.url().includes('/tourney/v1/logo') && r.request().method() === 'POST');
    await adminPage.locator('#tourney-logo-file').setInputFiles(IMG);
    expect((await up).status()).toBe(200);
    const img = adminPage.locator('#tourney-logo-preview');
    await expect(img).toBeVisible();
    await expect.poll(() => img.evaluate((el: HTMLImageElement) => el.complete ? el.naturalWidth : 0)).toBe(400);
    firstUrl = (await api(adminPage, 'GET', `read-aci?postId=${postId}`)).json.logoUrl;
    expect(firstUrl).toMatch(/\/wp-content\/uploads\/.+\.jpg$/);
    expect(await img.getAttribute('src')).toBe(firstUrl);
  });

  test('TC-LOGO-02 [🌐]: Logo auf der Turnierseite', async () => {
    await page.goto(`/${slug}/`);
    const logo = page.locator('[data-testid="tourney-logo"]');
    await expect(logo).toBeVisible({ timeout: 15000 });
    await expect(logo).toHaveAttribute('src', firstUrl);
  });

  test('TC-LOGO-03 [👤]: Logo auf der Standardurkunde statt des Pokal-Symbols', async () => {
    await adminPage.evaluate(() => (window as any).appLoadPage('Certificate', 'Muster, Max|Herren A|1'));
    await expect(adminPage.locator('[data-testid="cert-logo"]')).toHaveAttribute('src', firstUrl, { timeout: 10000 });
    await expect(adminPage.locator('.certificate-container .bi-award-fill')).toHaveCount(0);
  });

  test('TC-LOGO-04 [👤]: Falsches Format wird abgelehnt (Client und Server)', async () => {
    await openHomepageTab();
    await adminPage.locator('#tourney-logo-file').setInputFiles({ name: 'logo.txt', mimeType: 'text/plain', buffer: Buffer.from('kein Bild') });
    await expect(adminPage.locator('#tourney-logo-error')).toContainText('PNG-, JPG- oder WebP');
    // Server: Inhalt kein Bild, auch wenn Name/Typ ein Bild vorgeben
    const res = await adminPage.evaluate(async (postId: number) => {
      const el = document.getElementById('Main_ParamId')!;
      const home = (el.dataset.homeurl || '').replace(/\/$/, '');
      const fd = new FormData();
      fd.append('file', new Blob(['<svg onload="alert(1)"></svg>'], { type: 'image/png' }), 'evil.png');
      const r = await fetch(`${home}/wp-json/tourney/v1/logo?postId=${postId}`,
        { method: 'POST', body: fd, credentials: 'include', headers: { 'X-WP-Nonce': el.dataset.nonce || '' } });
      return { status: r.status, json: await r.json() };
    }, postId);
    expect(res.status).toBe(400);
    expect(res.json.msgCode).toBe('logo_type');
  });

  test('TC-LOGO-05 [🌐]: Hochladen ohne Schreibrecht wird abgelehnt', async () => {
    await page.goto(`/${slug}/`);
    const status = await page.evaluate(async (postId: number) => {
      const el = document.getElementById('Main_ParamId')!;
      const home = (el.dataset.homeurl || '').replace(/\/$/, '');
      const fd = new FormData();
      fd.append('file', new Blob([new Uint8Array([137, 80, 78, 71])], { type: 'image/png' }), 'x.png');
      const r = await fetch(`${home}/wp-json/tourney/v1/logo?postId=${postId}`, { method: 'POST', body: fd });
      return r.status;
    }, postId);
    expect([401, 403]).toContain(status);
  });

  test('TC-LOGO-06 [👤]: Neues Logo ersetzt das alte (alte Datei gelöscht); Entfernen löscht das Logo', async () => {
    await openHomepageTab();
    const up = adminPage.waitForResponse(r => r.url().includes('/tourney/v1/logo') && r.request().method() === 'POST');
    await adminPage.locator('#tourney-logo-file').setInputFiles(IMG2);
    expect((await up).status()).toBe(200);
    await expect(adminPage.locator('#tourney-logo-preview')).toHaveAttribute('src', /\.png$/);
    const secondUrl = (await api(adminPage, 'GET', `read-aci?postId=${postId}`)).json.logoUrl;
    expect(secondUrl).not.toBe(firstUrl);
    expect(await statusOf(firstUrl)).toBe(404);

    // Homepage-Einstellungen speichern überschreibt das Logo nicht
    adminPage.once('dialog', d => d.accept());
    await adminPage.locator('#homepage-save-btn').click();
    await expect.poll(async () => (await api(adminPage, 'GET', `read-aci?postId=${postId}`)).json.logoUrl).toBe(secondUrl);

    const del = adminPage.waitForResponse(r => r.url().includes('/tourney/v1/logo') && r.request().method() === 'DELETE');
    await adminPage.locator('#tourney-logo-delete-btn').click();
    expect((await del).status()).toBe(200);
    await expect(adminPage.locator('#tourney-logo-none')).toBeVisible();
    expect((await api(adminPage, 'GET', `read-aci?postId=${postId}`)).json.logoUrl).toBe('');
    expect(await statusOf(secondUrl)).toBe(404);

    await page.goto(`/${slug}/`);
    await expect(page.getByText('Herzlich Willkommen')).toBeVisible({ timeout: 15000 });
    await expect(page.locator('[data-testid="tourney-logo"]')).toHaveCount(0);
  });

  test('TC-LOGO-07 [👤]: Beim Löschen des Turniers wird das Logo mit gelöscht', async () => {
    await openHomepageTab();
    const up = adminPage.waitForResponse(r => r.url().includes('/tourney/v1/logo') && r.request().method() === 'POST');
    await adminPage.locator('#tourney-logo-file').setInputFiles(IMG2);
    expect((await up).status()).toBe(200);
    const url = (await api(adminPage, 'GET', `read-aci?postId=${postId}`)).json.logoUrl;
    expect(await statusOf(url)).toBe(200);
    expect((await api(adminPage, 'DELETE', `delete?postId=${postId}`)).status).toBe(200);
    postId = 0;
    expect(await statusOf(url)).toBe(404);
  });
});
