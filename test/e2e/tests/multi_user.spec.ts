import { test, expect, Browser, BrowserContext, Page } from '@playwright/test';
import fs from 'fs';
import path from 'path';
import { LoginPage } from '../pages/LoginPage';

/**
 * UseCase 26: Multiuser-Betrieb (Variante A, siehe doc/MultiUser.md).
 *
 * Zwei angemeldete Bearbeiter (A, B) öffnen dasselbe Cloud-Turnier (Stage 5 "Vorrunde" der Demo-Daten,
 * D-Klasse Damen Einzel, Gruppen, Spiele 3–5 offen). Änderungen werden über den WebSocket des Play-Servers an den
 * anderen gemeldet; Konflikte führt der Client je Spiel zusammen ("letzte Eingabe gewinnt" mit Hinweis);
 * Ergebnisse werden in WordPress protokolliert (TourneyAdmin, Reiter "Änderungen").
 *
 * Für Konflikte unterdrückt B per routeWebSocket die eingehenden Änderungsmeldungen (B arbeitet auf altem Stand).
 */

const USER = process.env.WP_ADMIN_USER || '';
const PASS = process.env.WP_ADMIN_PASSWORD || '';
const COMP = 'D-Klasse Damen Einzel';

test.describe.configure({ mode: 'serial' });

test.describe('UseCase 26: Multiuser (Benachrichtigung, Zusammenführen, Protokoll)', () => {
  test.skip(!USER || !PASS, 'WP_ADMIN_USER/WP_ADMIN_PASSWORD nicht gesetzt');
  test.skip(({ browserName }) => browserName !== 'chromium', 'nur Chromium (gemeinsames Cloud-Turnier)');

  let ctxA: BrowserContext, ctxB: BrowserContext;
  let pageA: Page, pageB: Page;
  let slug = '';
  let postId = 0;
  let blockSyncB = false;

  /** Meldet einen Browser-Kontext an und liefert eine Seite. */
  async function loggedIn(browser: Browser): Promise<[BrowserContext, Page]> {
    const ctx = await browser.newContext({ ignoreHTTPSErrors: true, locale: 'de-DE' });
    const page = await ctx.newPage();
    const login = new LoginPage(page);
    await login.goto();
    if (await login.loginBtn.isVisible()) await login.login(USER, PASS);
    await expect.poll(async () => (await ctx.cookies()).some(c => c.name.startsWith('wordpress_logged_in')),
                      { timeout: 10000 }).toBe(true);
    return [ctx, page];
  }

  /** Legt aus den Demo-Daten ein Cloud-Turnier an (wie der Import in TourneyAdmin) und liefert den Slug. */
  async function createCloudTourney(page: Page): Promise<[string, number]> {
    const demo = JSON.parse(fs.readFileSync(path.resolve(__dirname, '../../../wp-plugin/data/demo_tourney.json'), 'utf8'));
    const t = demo.tourney.tourney;
    t.wpId = 0;
    t.name = `MU-Test ${Date.now()}`;
    return await page.evaluate(async (t: any) => {
      const p = document.getElementById('Main_ParamId')!;
      const home = (p.dataset.homeurl || '').replace(/\/$/, '');
      const hdrs = { 'Content-Type': 'application/json', 'X-WP-Nonce': p.dataset.nonce || '' };
      const post = async (route: string, body: any, postId = 0) => {
        const r = await fetch(`${home}/wp-json/tourney/v1/${route}${postId ? `?postId=${postId}` : ''}`,
          { method: 'POST', headers: hdrs, body: JSON.stringify(body), credentials: 'include' });
        if (!r.ok) throw new Error(`${route}: ${r.status} ${await r.text()}`);
        return r.json();
      };
      const comps = t.competitions.filter((c: any) => c);
      const stages = t.stages.filter((s: any) => s);
      const res = await post('create', t);
      const id = res.pageId;
      await post('clubs-sync', { version: 0, clubs: t.clubs }, id);
      await post('players-sync', { version: 0, players: t.players }, id);
      await post('competitions-sync', { events: comps }, id);
      await post('stages-sync', { events: stages }, id);
      return [res.slug as string, id as number] as [string, number];
    }, t);
  }

  /** Öffnet die Ergebniseingabe der Stage 5 im Turnier. */
  async function openStageInput(page: Page) {
    await page.goto(`/${slug.replace(/^tourney\//, 'tourney/')}/`);
    await page.waitForFunction(() => typeof (window as any).appLoadPage === 'function');
    await page.evaluate(() => (window as any).appLoadPage('TourneyInfo', ''));
    const compCell = page.locator('tr', { hasText: COMP }).locator('.comp-link');
    await expect(compCell).toBeVisible({ timeout: 10000 });
    await compCell.click();
    await page.locator('.stage-link', { hasText: 'Vorrunde' }).click();
    await expect(page.locator('#input_3_1')).toBeVisible({ timeout: 10000 });
  }

  /** Trägt ein Ergebnis (Kurzschreibweise je Satz, z. B. ["5","5","5"] = 3:0) ein und speichert. */
  async function enterResult(page: Page, game: number, sets: string[]) {
    for (let i = 0; i < sets.length; i++) {
      await page.locator(`#input_${game}_${i + 1}`).fill(sets[i]);
    }
    const save = page.locator('tr', { has: page.locator(`#input_${game}_1`) }).locator('button.btn-success');
    await expect(save).toBeEnabled({ timeout: 5000 });
    await save.click();
    await expect(page.locator(`#sets_${game}`)).not.toHaveText('', { timeout: 5000 });
  }

  /** Liefert ein Promise, das bei der nächsten Antwort von stages-sync mit dem Status erfüllt wird. */
  function syncResponse(page: Page, status = 200) {
    return page.waitForResponse(r => r.url().includes('/stages-sync') && r.status() === status, { timeout: 15000 });
  }

  /** Satzstand eines Spiels in einer frisch geöffneten Seite (Stand in WordPress). */
  async function storedSets(game: number): Promise<string> {
    const page = await ctxA.newPage();
    await openStageInput(page);
    const txt = (await page.locator(`#sets_${game}`).textContent() || '').trim();
    await page.close();
    return txt;
  }

  test.beforeAll(async ({ browser }) => {
    [ctxA, pageA] = await loggedIn(browser);
    [ctxB, pageB] = await loggedIn(browser);
    // B: eingehende Änderungsmeldungen bei Bedarf unterdrücken (Konflikt-Szenario)
    await pageB.routeWebSocket(/\/ws\//, ws => {
      const server = ws.connectToServer();
      server.onMessage(m => { if (!(blockSyncB && String(m).startsWith('SYNC:'))) ws.send(m); });
      ws.onMessage(m => server.send(m));
    });
    [slug, postId] = await createCloudTourney(pageA);
    await openStageInput(pageA);
    await openStageInput(pageB);
    // WebSocket-Verbindungen aufbauen lassen
    await pageA.waitForTimeout(1500);
  });

  test.afterAll(async () => {
    // Test-Turnier löschen (das Änderungsprotokoll wird dabei mit gelöscht)
    if (postId && pageA) {
      await pageA.evaluate(async (id: number) => {
        const p = document.getElementById('Main_ParamId')!;
        const home = (p.dataset.homeurl || '').replace(/\/$/, '');
        await fetch(`${home}/wp-json/tourney/v1/delete?postId=${id}`,
          { method: 'DELETE', headers: { 'X-WP-Nonce': p.dataset.nonce || '' }, credentials: 'include' });
      }, postId).catch(() => {});
    }
    await ctxA?.close();
    await ctxB?.close();
  });

  test('TC-MU-01 [👤]: Ergebnis von A erscheint bei B ohne Neuladen, mit Hinweis', async () => {
    const saved = syncResponse(pageA);
    await enterResult(pageA, 3, ['5', '5', '5']);
    await saved;
    await expect(pageB.locator('#sets_3')).toHaveText('3:0', { timeout: 10000 });
    await expect(pageB.locator('.mu-toast', { hasText: 'Spiel 3' })).toBeVisible({ timeout: 5000 });
  });

  test('TC-MU-02 [👤]: Noch nicht gespeicherte Eingaben von B bleiben bei der Aktualisierung erhalten', async () => {
    await pageB.locator('#input_4_1').fill('7');
    const saved = syncResponse(pageA);
    await enterResult(pageA, 5, ['-4', '-4', '-4']);
    await saved;
    await expect(pageB.locator('#sets_5')).toHaveText('0:3', { timeout: 10000 });
    await expect(pageB.locator('#input_4_1')).toHaveValue('7');
    await pageB.locator('#input_4_1').fill('');
  });

  test('TC-MU-03 [👤]: Konflikt in verschiedenen Spielen derselben Stage wird zusammengeführt', async () => {
    blockSyncB = true;
    const savedA = syncResponse(pageA);
    await enterResult(pageA, 6, ['6', '6', '6']);
    await savedA;
    // B kennt Spiel 6 nicht (alter Stand) und speichert Spiel 4 → 409, Zusammenführen, erneut speichern
    const conflictB = syncResponse(pageB, 409);
    const savedB = syncResponse(pageB);
    await enterResult(pageB, 4, ['8', '8', '8']);
    await conflictB;
    await savedB;
    blockSyncB = false;
    await expect(pageB.locator('#sets_6')).toHaveText('3:0', { timeout: 10000 });
    expect(await storedSets(6)).toBe('3:0');
    expect(await storedSets(4)).toBe('3:0');
  });

  test('TC-MU-04 [👤]: Gleiches Spiel von A und B: letzte Eingabe gewinnt, B erhält Hinweis', async () => {
    blockSyncB = true;
    const savedA = syncResponse(pageA);
    await enterResult(pageA, 7, ['5', '5', '5']);
    await savedA;
    const conflictB = syncResponse(pageB, 409);
    const savedB = syncResponse(pageB);
    await enterResult(pageB, 7, ['-9', '-9', '-9']);
    await conflictB;
    await savedB;
    blockSyncB = false;
    await expect(pageB.locator('.mu-toast', { hasText: 'Ihre Eingabe gilt' })).toContainText('Spiel 7', { timeout: 5000 });
    expect(await storedSets(7)).toBe('0:3');
    // A erhält die Meldung von B und zeigt das überschriebene Ergebnis
    await expect(pageA.locator('#sets_7')).toHaveText('0:3', { timeout: 10000 });
  });

  test('TC-MU-05 [👤]: Änderungsprotokoll in TourneyAdmin zeigt Ergebnisse mit Bearbeiter', async () => {
    await pageA.evaluate(() => (window as any).appLoadPage('TourneyAdmin', 'CHANGELOG'));
    const rows = pageA.locator('#changelog-table tbody tr');
    await expect(rows.first()).toBeVisible({ timeout: 10000 });
    for (const g of [3, 4, 5, 6, 7]) {
      await expect(rows.filter({ hasText: `Spiel ${g} ` }).first()).toBeVisible();
    }
    // Spiel 7: zuerst A (3:0), danach B (0:3)
    await expect(rows.filter({ hasText: 'Spiel 7 ' })).toHaveCount(2);
    await expect(rows.filter({ hasText: 'Spiel 7 ' }).first()).toContainText('0:3');
  });
});
