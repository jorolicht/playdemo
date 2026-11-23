import { test, expect, Page, BrowserContext } from '@playwright/test';
import { execSync } from 'child_process';
import fs from 'fs';
import path from 'path';
import { MainViewPage } from '../pages/MainViewPage';

/**
 * UseCase 32: Turnier kopieren – Turniervorlage aus dem Vorjahr (TourneyInfo „Kopieren“, services.TourneyCopy,
 * shared TourneyTemplate). Lokal: Demo-Turnier → neues lokales Turnier. Cloud: Cloud-Turnier → neues Cloud-Turnier
 * mit Homepage-Texten und Logo.
 */

const USER = process.env.WP_ADMIN_USER || '';
const PASS = process.env.WP_ADMIN_PASSWORD || '';
const WP = 'docker exec wp-cli-instance wp --path=/var/www/html --allow-root';
const LOGO = path.resolve(__dirname, '../../../wp-plugin/img/pwa-512.png');

/** Öffnet den Kopier-Dialog in der Turnierübersicht und bestätigt mit Name und Datum. */
async function copyTourney(page: Page, name: string, date: string) {
  await page.locator('#TourneyInfo_BtnCopyTourney').click();
  const dlg = page.locator('.modal.show', { has: page.locator('[id$="DlgCopyTourney_InputNameId"]') });
  await expect(dlg).toBeVisible();
  await dlg.locator('[id$="DlgCopyTourney_InputNameId"]').fill(name);
  await dlg.locator('[id$="DlgCopyTourney_InputDateId"]').fill(date);
  await dlg.locator('[id$="DlgCopyTourney_ApplyId"]').click();
}

test.describe('UseCase 32: Turnier kopieren', () => {

  test('TC-COPY-01 [🌐]: Lokales Turnier kopieren: Wettbewerbe ohne Spieler, Termine verschoben, Phasen aus dem Plan', async ({ page }) => {
    const main = new MainViewPage(page);
    await main.goto();
    await main.startDemo();
    await expect(page.locator('.comp-link').first()).toBeVisible({ timeout: 15000 });

    // Vorschlag: gleicher Wochentag ein Jahr später (2030-04-01 + 364 Tage)
    await page.locator('#TourneyInfo_BtnCopyTourney').click();
    await expect(page.locator('[id$="DlgCopyTourney_InputDateId"]')).toHaveValue('2031-03-31');
    await page.locator('.modal.show [id$="DlgCopyTourney_CancelId"]').click();
    await expect(page.locator('.modal.show')).toHaveCount(0);

    await copyTourney(page, 'Vorlage-Test 2031', '2031-03-31');
    // lokales Turnier wird ersetzt: Rückfrage „vorher speichern?“
    await page.locator('.modal.show button', { hasText: /ÜBERSCHREIBEN/i }).click();
    await expect(page.locator('.card', { hasText: 'Turnier-Informationen' })).toContainText('Vorlage-Test 2031', { timeout: 15000 });

    const rows = page.locator('tr', { has: page.locator('.comp-link') });
    await expect(rows).toHaveCount(4);
    await expect(rows.filter({ hasText: 'Aktiv' })).toHaveCount(0);

    // Wettbewerb ohne Teilnehmer, Startzeit verschoben, Phasenplan beim Start
    await page.locator('tr', { hasText: 'S-Klasse Herren Einzel' }).locator('.comp-link').click();
    await expect(page.locator('body')).toContainText('31.03.2031 12:00');
    await page.locator('button[id*="BtnStartStage"]').click();
    await expect(page.locator('input[id*="DlgStageStart_InputNameId"]')).toHaveValue('Vorrunde');
    await page.locator('button[id*="DlgStageStart_ApplyId"]').click();
    await expect(page.locator('#StageAdmin_SelectDrawMode')).toHaveValue('RR', { timeout: 10000 });

    // zweite Phase: Name aus dem Plan, Spielsystem K.-o.
    await page.evaluate(() => (window as any).appLoadPage('CompetitionInfo', ''));
    await page.locator('button[id*="BtnStartStage"]').click();
    await expect(page.locator('input[id*="DlgStageStart_InputNameId"]')).toHaveValue('Endrunde');
    await page.locator('select[id*="DlgStageStart_InputPrefId"]').selectOption({ index: 1 });
    await page.locator('button[id*="DlgStageStart_ApplyId"]').click();
    await expect(page.locator('#StageAdmin_SelectDrawMode')).toHaveValue('KO', { timeout: 10000 });
  });

  test.describe('Cloud', () => {
    test.skip(!USER || !PASS, 'WP_ADMIN_USER/WP_ADMIN_PASSWORD nicht gesetzt');
    test.skip(({ browserName }) => browserName !== 'chromium', 'nur Chromium (Cloud-Turniere, wp-cli)');

    let ctx: BrowserContext;
    let page: Page;
    const ids: number[] = [];
    const stamp = Date.now() % 100000;
    const srcName = `Kopierquelle ${stamp}`;
    const dstName = `Kopierziel ${stamp}`;

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

    test.afterAll(async () => {
      for (const id of ids) await api('DELETE', `delete?postId=${id}`).catch(() => {});
      await ctx?.close();
    });

    test('TC-COPY-02 [👤]: Cloud-Turnier kopieren: neues Cloud-Turnier mit Wettbewerben, Homepage-Text und Logo', async ({ browser }) => {
      ctx = await browser.newContext({ ignoreHTTPSErrors: true, locale: 'de-DE' });
      page = await ctx.newPage();
      await page.goto('/login/');
      await page.locator('input[id*="LoginId"]').fill(USER);
      await page.locator('input[id*="PasswordId"]').fill(PASS);
      await page.locator('button[id*="BtnLogin"]').click();
      await expect.poll(async () => (await ctx.cookies()).some(c => c.name.startsWith('wordpress_logged_in')), { timeout: 10000 }).toBe(true);
      await page.goto('/');

      const demo = JSON.parse(fs.readFileSync(path.resolve(__dirname, '../../../wp-plugin/data/demo_tourney.json'), 'utf8'));
      const t = demo.tourney.tourney;
      t.wpId = 0; t.name = srcName;
      const res = await api('POST', 'create', t);
      expect(res.status, JSON.stringify(res.json)).toBe(200);
      const srcId = res.json.pageId; ids.push(srcId);
      await api('POST', `clubs-sync?postId=${srcId}`, { version: 0, clubs: t.clubs });
      await api('POST', `players-sync?postId=${srcId}`, { version: 0, players: t.players });
      await api('POST', `competitions-sync?postId=${srcId}`, { events: t.competitions.filter((c: any) => c) });
      await api('POST', `stages-sync?postId=${srcId}`, { events: t.stages.filter((s: any) => s) });
      await api('POST', `save-aci?postId=${srcId}`, { allowRegistration: true, homepageInfo: 'Startgeld 8 €' });

      // Logo hochladen (Administration, Reiter Homepage)
      await page.goto(`/${res.json.slug}/`);
      await expect(page.locator('.comp-link').first()).toBeVisible({ timeout: 15000 });
      await page.evaluate(() => (window as any).appLoadPage('TourneyAdmin', 'HOMEPAGE'));
      await page.locator('#tourney-logo-file').setInputFiles(LOGO);
      await expect(page.locator('#tourney-logo-preview')).toBeVisible({ timeout: 10000 });
      const srcLogo = (await api('GET', `read-aci?postId=${srcId}`)).json.logoUrl;

      await page.evaluate(() => (window as any).appLoadPage('TourneyInfo', ''));
      await copyTourney(page, dstName, '2031-03-31');
      await expect(page.locator('.card', { hasText: 'Turnier-Informationen' })).toContainText(dstName, { timeout: 20000 });

      const dstId = Number(execSync(`${WP} post list --post_type=tourney --title="${dstName}" --field=ID`).toString().trim());
      expect(dstId).toBeGreaterThan(0);
      ids.push(dstId);
      expect(dstId).not.toBe(srcId);

      expect((await api('GET', `players?postId=${dstId}`)).json.players).toHaveLength(0);
      const comps = (await api('GET', `competitions?postId=${dstId}`)).json.competitions.filter((c: any) => c);
      expect(comps).toHaveLength(4);
      expect(comps.every((c: any) => c.status === 'CFG' && (c.pants1Stage || []).length === 0)).toBe(true);
      const herren = comps.find((c: any) => c.name === 'S-Klasse Herren Einzel');
      expect(herren.startDate).toBe('2031-03-31 12:00');
      expect(herren.stagePlan.map((p: any) => p.name)).toEqual(['Vorrunde', 'Endrunde']);
      expect((await api('GET', `stages?postId=${dstId}`)).json.stages).toHaveLength(0);

      const aci = (await api('GET', `read-aci?postId=${dstId}`)).json;
      expect(aci.homepageInfo).toBe('Startgeld 8 €');
      expect(aci.allowRegistration).toBe(true);
      expect(aci.logoUrl).toMatch(/\.png$/);
      expect(aci.logoUrl).not.toBe(srcLogo);
      // Quelle unverändert
      expect((await api('GET', `read-aci?postId=${srcId}`)).json.logoUrl).toBe(srcLogo);
      expect((await api('GET', `players?postId=${srcId}`)).json.players.length).toBeGreaterThan(0);
    });
  });
});
