import { test, expect, Browser, BrowserContext, Page } from '@playwright/test';
import fs from 'fs';
import path from 'path';
import { MainViewPage } from '../pages/MainViewPage';

/**
 * Spielphase, Reiter „Eingabe“: Bemerkung zu einem Spiel im Spielinfo-Dialog; Spiele mit Bemerkung sind mit „!“
 * zwischen den Spielern gekennzeichnet (pages.Stage.StageInput, MEntry.remark, dialogs.DlgMsgbox).
 *
 * D-Klasse Damen Einzel, Vorrunde, Spiel 4: Kaemmerling, T. – Kern jun., Martin.
 */

/** Öffnet den Spielinfo-Dialog eines Spiels und liefert den Dialog. */
async function openInfo(page: Page, gameNo: number) {
  await page.locator(`button[id$="InfoMatchBtn-${gameNo}"]`).click();
  const dlg = page.locator('.modal.show');
  await expect(dlg).toBeVisible();
  return dlg;
}

/** Trägt im geöffneten Dialog eine Bemerkung ein und speichert. */
async function saveRemark(page: Page, gameNo: number, text: string) {
  const dlg = await openInfo(page, gameNo);
  await dlg.locator('[data-testid="msgbox-input"]').fill(text);
  await dlg.getByRole('button', { name: 'Speichern' }).click();
  await expect(dlg).toBeHidden();
}

test('TC-SI-14 [👤]: Bemerkung im Spielinfo-Dialog eingeben, mit „!“ gekennzeichnet, wieder entfernen', async ({ page }) => {
  const main = new MainViewPage(page);
  await main.goto();
  await main.startDemo();
  await page.locator('tr', { hasText: 'D-Klasse Damen Einzel' }).locator('.comp-link').click();
  await page.locator('.stage-link', { hasText: 'Vorrunde' }).click();
  await page.evaluate(() => (window as any).appLoadPage('StageInput', ''));
  await expect(page.locator('#nameA-4')).toBeVisible({ timeout: 10000 });
  await expect(page.locator('[data-testid="remark-flag"]')).toHaveCount(0);

  // Dialog: Textfeld leer, Abbrechen ändert nichts
  let dlg = await openInfo(page, 4);
  await expect(dlg.locator('[data-testid="msgbox-input"]')).toHaveValue('');
  await dlg.locator('[data-testid="msgbox-input"]').fill('wird verworfen');
  await dlg.getByRole('button', { name: /abbruch/i }).click();
  await expect(page.locator('[data-testid="remark-flag"]')).toHaveCount(0);

  // Speichern: „!“ zwischen den Spielern, Bemerkung als Tooltip
  await saveRemark(page, 4, 'Spielerin B verletzt, Spiel unterbrochen');
  const flag = page.locator('#remark-4');
  await expect(flag).toHaveText('!');
  await expect(flag).toHaveAttribute('title', 'Spielerin B verletzt, Spiel unterbrochen');
  await expect(page.locator('#row-4 td').filter({ has: flag })).toContainText('Kaemmerling, T.');
  await expect(page.locator('[data-testid="remark-flag"]')).toHaveCount(1);

  // erneut öffnen: Bemerkung vorbelegt
  dlg = await openInfo(page, 4);
  await expect(dlg.locator('[data-testid="msgbox-input"]')).toHaveValue('Spielerin B verletzt, Spiel unterbrochen');
  await dlg.getByRole('button', { name: /abbruch/i }).click();
  await expect(dlg).toBeHidden();

  // leeren und speichern: Kennzeichnung verschwindet
  await saveRemark(page, 4, '');
  await expect(page.locator('[data-testid="remark-flag"]')).toHaveCount(0);
});

const USER = process.env.WP_ADMIN_USER || '';
const PASS = process.env.WP_ADMIN_PASSWORD || '';

test.describe('Bemerkung im Cloud-Turnier', () => {
  test.skip(!USER || !PASS, 'WP_ADMIN_USER/WP_ADMIN_PASSWORD nicht gesetzt');
  test.skip(({ browserName }) => browserName !== 'chromium', 'nur Chromium (Cloud-Turnier)');

  let admin: BrowserContext;
  let page: Page;
  let postId = 0;
  let slug = '';

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

  /** Öffnet die Eingabe der Vorrunde der D-Klasse Damen Einzel. */
  async function openInput() {
    await page.goto(`/${slug}/`);
    await page.waitForFunction(() => typeof (window as any).appLoadPage === 'function');
    await expect(async () => {
      await page.evaluate(() => (window as any).appLoadPage('TourneyInfo', ''));
      await page.locator('tr', { hasText: 'D-Klasse Damen Einzel' }).locator('.comp-link').click({ timeout: 2000 });
    }).toPass({ timeout: 15000 });
    await page.locator('.stage-link', { hasText: 'Vorrunde' }).click();
    await page.evaluate(() => (window as any).appLoadPage('StageInput', ''));
    await expect(page.locator('#nameA-4')).toBeVisible({ timeout: 10000 });
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
    t.name = `Bemerkung-Test ${Date.now() % 100000}`;
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

  test('TC-SI-15 [👤]: Bemerkung wird gespeichert und ist nach dem Neuladen noch da', async () => {
    await openInput();
    await saveRemark(page, 4, 'Protest eingelegt; Ball^gewechselt');
    await expect(page.locator('#remark-4')).toHaveAttribute('title', 'Protest eingelegt; Ball gewechselt');   // "^" bereinigt

    // gespeichert in WordPress: nach dem Abschluss "_" angehängt, "^" ersetzt
    await expect.poll(async () => {
      const stages = (await api('GET', `stages?postId=${postId}`)).json.stages;
      const m = stages.find((s: any) => s.id === 5).matches.find((x: any) => x.content.startsWith('4^'));
      return m.content.split('^_^')[1] || '';
    }, { timeout: 15000 }).toBe('Protest eingelegt; Ball gewechselt');

    await openInput();
    await expect(page.locator('#remark-4')).toHaveAttribute('title', 'Protest eingelegt; Ball gewechselt');
    const dlg = await openInfo(page, 4);
    await expect(dlg.locator('[data-testid="msgbox-input"]')).toHaveValue('Protest eingelegt; Ball gewechselt');
  });
});
