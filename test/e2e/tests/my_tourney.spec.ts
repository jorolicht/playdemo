import { test, expect, Browser, BrowserContext, Page } from '@playwright/test';
import fs from 'fs';
import path from 'path';

/**
 * UseCase 28: „Mein Turnier“ – persönliche Ansicht für Spieler (pages.MyTourney).
 *
 * Ein Administrator legt aus den Demo-Daten ein Cloud-Turnier an. Ein anonymer Besucher öffnet über die Turnierseite
 * „Mein Turnier“ und wählt einen Namen. Verwendete Demo-Daten:
 *   D-Klasse Damen Einzel, Vorrunde (Gruppen):
 *     Hose, Lutz       – 3:0 gegen Fähre, Conny; spielbereit gegen Enzien, Ingrid; wartet gegen Ass, Caro (Gruppe A)
 *     Brandenburg, Mark – aufgerufen an Tisch 12 gegen Fehr, Luzie
 *   S-Klasse Herren Einzel, Endrunde (K.-o.):
 *     Ehfer, Maik – Freilos im Viertelfinale, steht im Halbfinale gegen Container, Klaas
 *     Bert, Ali   – im Viertelfinale ausgeschieden
 */

const USER = process.env.WP_ADMIN_USER || '';
const PASS = process.env.WP_ADMIN_PASSWORD || '';

test.describe.configure({ mode: 'serial' });

test.describe('UseCase 28: Mein Turnier', () => {
  test.skip(!USER || !PASS, 'WP_ADMIN_USER/WP_ADMIN_PASSWORD nicht gesetzt');
  test.skip(({ browserName }) => browserName !== 'chromium', 'nur Chromium (legt ein Cloud-Turnier an)');

  let admin: BrowserContext, anon: BrowserContext;
  let adminPage: Page, page: Page;
  let slug = '';
  let postId = 0;

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

    const demo = JSON.parse(fs.readFileSync(path.resolve(__dirname, '../../../wp-plugin/data/demo_tourney.json'), 'utf8'));
    const t = demo.tourney.tourney;
    t.wpId = 0;
    t.name = `Mein-Turnier-Test ${Date.now()}`;
    const res = await api(adminPage, 'POST', 'create', t);
    expect(res.status, JSON.stringify(res.json)).toBe(200);
    slug = res.json.slug;
    postId = res.json.pageId;
    expect((await api(adminPage, 'POST', `clubs-sync?postId=${postId}`, { version: 0, clubs: t.clubs })).status).toBe(200);
    expect((await api(adminPage, 'POST', `players-sync?postId=${postId}`, { version: 0, players: t.players })).status).toBe(200);
    expect((await api(adminPage, 'POST', `competitions-sync?postId=${postId}`, { events: t.competitions.filter((c: any) => c) })).status).toBe(200);
    expect((await api(adminPage, 'POST', `stages-sync?postId=${postId}`, { events: t.stages.filter((s: any) => s) })).status).toBe(200);

    anon = await browser.newContext({ ignoreHTTPSErrors: true, locale: 'de-DE' });
    page = await anon.newPage();
  });

  test.afterAll(async () => {
    if (postId && adminPage) await api(adminPage, 'DELETE', `delete?postId=${postId}`).catch(() => {});
    await anon?.close();
    await admin?.close();
  });

  /** Öffnet die Turnierseite und von dort „Mein Turnier“. */
  async function openMyTourney() {
    await page.goto(`/${slug}/`);
    await page.locator('[data-testid="my-tourney-btn"]').click();
    await expect(page.locator('[data-testid="my-tourney"]')).toBeVisible({ timeout: 15000 });
  }

  /** Wählt einen Namen aus. */
  async function choose(name: string) {
    await page.locator('select[id$="SelectId"]').selectOption({ label: name });
    await page.locator('button[id$="BtnSelectId"]').click();
    await expect(page.locator('[data-testid="my-player"]')).toHaveText(name);
  }

  test('TC-MT-01 [🌐]: Name wählen: nächste Spiele (spielbereit/wartet), Ergebnis und Tabellenplatz', async () => {
    await openMyTourney();
    await expect(page.locator('[data-testid="my-tourney"]')).toContainText('Wählen Sie Ihren Namen');
    await choose('Hose, Lutz');

    const next = page.locator('[data-testid="my-next"] li');
    await expect(next).toHaveCount(2);
    await expect(next.nth(0)).toContainText('gegen Enzien, Ingrid');
    await expect(next.nth(0)).toContainText('Spielbereit');
    await expect(next.nth(0)).toContainText('D-Klasse Damen Einzel · Vorrunde · Gruppe A');
    await expect(next.nth(1)).toContainText('gegen Ass, Caro');
    await expect(next.nth(1)).toContainText('Wartet');

    const result = page.locator('[data-testid="my-results"] li', { hasText: 'Fähre, Conny' });
    await expect(result.locator('.badge')).toHaveText('3:0');
    await expect(result.locator('.badge')).toHaveClass(/bg-success/);

    const comp = page.locator('[data-testid="my-comps"] li', { hasText: 'D-Klasse Damen Einzel' });
    await expect(comp).toContainText(/Vorrunde:\s*Gruppe A: Platz \d+ \(Punkte \d+:\d+\)/);
  });

  test('TC-MT-02 [🌐]: Aufgerufenes Spiel steht oben mit Tisch', async () => {
    await page.locator('button[id$="BtnChangeId"]').click();
    await choose('Brandenburg, Mark');
    const first = page.locator('[data-testid="my-next"] li').first();
    await expect(first).toContainText('gegen Fehr, Luzie');
    await expect(first.locator('[data-testid="my-called"]')).toHaveText('Aufgerufen – Tisch 12');
  });

  test('TC-MT-03 [🌐]: Stand im K.-o.-Baum: nächste Runde bzw. ausgeschieden; Freilos', async () => {
    await page.locator('button[id$="BtnChangeId"]').click();
    await choose('Ehfer, Maik');
    const comp = page.locator('[data-testid="my-comps"] li', { hasText: 'S-Klasse Herren Einzel' });
    await expect(comp).toContainText('Endrunde: Nächste Runde: Halbfinale');
    await expect(page.locator('[data-testid="my-next"] li', { hasText: 'Container, Klaas' })).toContainText('Halbfinale');
    const bye = page.locator('[data-testid="my-results"] li', { hasText: 'Viertelfinale' });
    await expect(bye.locator('.fw-bold')).toHaveText('Freilos');
    await expect(bye.locator('.badge')).toHaveCount(0);

    await page.locator('button[id$="BtnChangeId"]').click();
    await choose('Bert, Ali');
    await expect(page.locator('[data-testid="my-comps"] li', { hasText: 'S-Klasse Herren Einzel' }))
      .toContainText('Endrunde: Ausgeschieden: Viertelfinale');
  });

  test('TC-MT-04 [🌐]: Auswahl bleibt im Browser gespeichert; "Anderer Name" setzt sie zurück', async () => {
    await page.locator('button[id$="BtnChangeId"]').click();
    await choose('Hose, Lutz');
    await openMyTourney();
    await expect(page.locator('[data-testid="my-player"]')).toHaveText('Hose, Lutz');
    await page.locator('button[id$="BtnChangeId"]').click();
    await expect(page.locator('select[id$="SelectId"]')).toBeVisible();
    await openMyTourney();
    await expect(page.locator('select[id$="SelectId"]')).toBeVisible();
  });

  test('TC-MT-05 [🌐]: Aufruf durch die Turnierleitung erscheint nach spätestens 30 s ohne Neuladen', async () => {
    test.setTimeout(90000);
    await choose('Hose, Lutz');
    await expect(page.locator('[data-testid="my-called"]')).toHaveCount(0);

    // Turnierleitung ruft Spiel 7 (Enzien – Hose) an Tisch 5 auf
    const stages = (await api(adminPage, 'GET', `stages?postId=${postId}`)).json.stages;
    const st = stages.find((s: any) => s.id === 5);
    const idx = st.matches.findIndex((m: any) => m.content.startsWith('7^'));
    const f = st.matches[idx].content.split('^');
    f[9] = '5';   // playfield
    f[13] = '1';  // status RUN
    st.matches[idx].content = f.join('^');
    st.version = (st.version || 0) + 1;
    expect((await api(adminPage, 'POST', `stages-sync?postId=${postId}`, { events: [st] })).status).toBe(200);

    await expect(page.locator('[data-testid="my-called"]')).toHaveText('Aufgerufen – Tisch 5', { timeout: 40000 });
    await expect(page.locator('[data-testid="my-next"] li').first()).toContainText('gegen Enzien, Ingrid');
  });

  test('TC-MT-06 [🌐]: Eigene Statistik: Spiele, Siege:Niederlagen, Sätze, TTR ±, längstes Spiel', async () => {
    // Werte wie im Reiter „Spielerstatistik“ (TC-STAT-01 rechnet sie unabhängig aus den Demo-Daten nach)
    await page.locator('button[id$="BtnChangeId"]').click();
    await choose('Elb-Rösel, Sam');
    const stats = page.locator('[data-testid="my-stats"]');
    await expect(stats).toContainText('Meine Statistik');
    await expect(stats.locator('[data-testid="my-stats-games"]')).toHaveText('9');
    await expect(stats.locator('[data-testid="my-stats-winloss"]')).toHaveText('7:2');
    await expect(stats.locator('[data-testid="my-stats-sets"]')).toHaveText('22:14');
    await expect(stats.locator('[data-testid="my-stats-ttr"]')).toHaveText('+30');
    await expect(stats.locator('[data-testid="my-stats-ttr"]')).toHaveClass(/text-success/);
    await expect(stats.locator('[data-testid="my-stats-longest"]'))
      .toHaveText('Längstes Spiel: 3:2 gegen Container, Klaas, 96 Bälle (S-Klasse Herren Einzel)');

    // Noch kein beendetes Spiel: keine Statistik
    await page.locator('button[id$="BtnChangeId"]').click();
    await choose('Fehr, Luzie');
    await expect(page.locator('[data-testid="my-results"]')).toBeVisible();
    await expect(page.locator('[data-testid="my-stats"]')).toHaveCount(0);
  });
});
