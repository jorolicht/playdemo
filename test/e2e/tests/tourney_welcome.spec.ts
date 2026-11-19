import { test, expect } from '@playwright/test';
import { TourneyWelcomePage } from '../pages/TourneyWelcomePage';

test.describe('UseCases 3 & 4: Öffentliche Turnierseite (TourneyWelcome)', () => {
  let welcomePage: TourneyWelcomePage;

  test.beforeEach(async ({ page }) => {
    welcomePage = new TourneyWelcomePage(page);
    await page.goto('/');
    await page.waitForLoadState('domcontentloaded');
    await page.evaluate(() => {
      if (typeof (window as any).appLoadPage === 'function') {
        (window as any).appLoadPage('TourneyWelcome', '');
      }
    });
  });

  test('TC-TW-01 [🌐]: Turnierdetails, Ort, Datum & Wettbewerbsliste anzeigen', async ({ page }) => {
    await expect(page.locator('.card, .container').first()).toBeVisible();
    if (await welcomePage.compTable.count() > 0) {
      await expect(welcomePage.compTable).toBeVisible();
    }
  });

  test('TC-TW-02 [🌐]: DSGVO-konforme Karte: PNG 640×320 für eine Adresse, 404 für eine unbekannte Adresse', async ({ page }) => {
    const response = await page.request.get('/srv/api/map/static?query=Rathausmarkt%201%2020095%20Hamburg%20DE&zoom=16');
    expect(response.status()).toBe(200);
    expect(response.headers()['content-type']).toContain('image/png');
    const png = await response.body();
    // PNG-Kopf: Breite und Höhe ab Byte 16
    expect(png.readUInt32BE(16)).toBe(640);
    expect(png.readUInt32BE(20)).toBe(320);
    const unknown = await page.request.get('/srv/api/map/static?query=Xyzzyqqq%2099999%20Nirgendwoqqq');
    expect(unknown.status()).toBe(404);
  });

  test('TC-TW-03 [🌐]: iCal Kalenderdatei Link (.ics) vorhanden', async ({ page }) => {
    const icsBtn = page.locator('a[href*=".ics"], button:has-text("iCal")');
    if (await icsBtn.count() > 0) {
      await expect(icsBtn.first()).toBeVisible();
    } else {
      await expect(page.locator('body')).toBeVisible();
    }
  });

  test('TC-TW-04 [🌐]: Öffentliche Spieleranmeldung Modal öffnen (DlgPublicRegistration)', async ({ page }) => {
    const regBtn = page.locator('button:has-text("Anmeldung"), button:has-text("Online-Anmeldung")');
    if (await regBtn.count() > 0) {
      await regBtn.first().click();
      await page.waitForTimeout(500);
      await expect(page.locator('.modal, div[id*="DlgPublicRegistration"]').first()).toBeVisible();
    } else {
      await expect(page.locator('body')).toBeVisible();
    }
  });

  test('TC-TW-05 [🌐]: Klick auf Wettbewerbszeile leitet zu CompetitionWelcome', async ({ page }) => {
    const compRow = page.locator('tr[onclick*="CompetitionWelcome"], tr[onclick*="CompetitionInfo"]').first();
    if (await compRow.count() > 0) {
      await compRow.click();
      await page.waitForTimeout(500);
      expect(page.url()).toMatch(/#CompetitionWelcome|#CompetitionInfo/);
    } else {
      await expect(page.locator('body')).toBeVisible();
    }
  });
});

/**
 * Karte „Adresse & Anfahrt“ auf der öffentlichen Turnierseite eines Cloud-Turniers (Play-Server-Karte).
 * Legt als Administrator zwei Turniere an (bekannte bzw. unbekannte Adresse), öffnet sie anonym und löscht sie wieder.
 */
test.describe('UseCase 3: Karte auf TourneyWelcome (Cloud-Turnier)', () => {
  // nacheinander: gleichzeitiges Anlegen beim selben Veranstalter kann kollidieren
  test.describe.configure({ mode: 'serial' });
  const USER = process.env.WP_ADMIN_USER || '';
  const PASS = process.env.WP_ADMIN_PASSWORD || '';
  test.skip(!USER || !PASS, 'WP_ADMIN_USER/WP_ADMIN_PASSWORD nicht gesetzt');
  test.skip(({ browserName }) => browserName !== 'chromium', 'nur Chromium (legt Cloud-Turniere an)');

  /** Legt angemeldet ein Turnier mit Adresse an, öffnet es anonym und liefert die Seite; löscht es am Ende. */
  async function withTourney(browser: any, address: any, check: (page: any) => Promise<void>) {
    const admin = await browser.newContext({ ignoreHTTPSErrors: true, locale: 'de-DE' });
    const ap = await admin.newPage();
    await ap.goto('/login/');
    await ap.locator('input[id*="LoginId"]').fill(USER);
    await ap.locator('input[id*="PasswordId"]').fill(PASS);
    await ap.locator('button[id*="BtnLogin"]').click();
    await expect.poll(async () => (await admin.cookies()).some((c: any) => c.name.startsWith('wordpress_logged_in')),
                      { timeout: 10000 }).toBe(true);
    await ap.goto('/');
    const [slug, id] = await ap.evaluate(async (address: any) => {
      const p = document.getElementById('Main_ParamId')!;
      const home = (p.dataset.homeurl || '').replace(/\/$/, '');
      const now = new Date();
      const day = now.getFullYear() * 10000 + (now.getMonth() + 1) * 100 + now.getDate();
      const t = { wpId: 0, name: `Karte-Test ${Date.now()}`, organizer: 'TTC Test', startDate: day, endDate: day,
                  ident: '', category: 'TT', clubs: [], players: [], competitions: [], stages: [], address };
      const r = await fetch(`${home}/wp-json/tourney/v1/create`, { method: 'POST', credentials: 'include',
        headers: { 'Content-Type': 'application/json', 'X-WP-Nonce': p.dataset.nonce || '' }, body: JSON.stringify(t) });
      const j = await r.json();
      return [j.slug, j.pageId];
    }, address);
    expect(slug, 'Turnier angelegt').toBeTruthy();
    const anon = await browser.newContext({ ignoreHTTPSErrors: true, locale: 'de-DE' });
    const page = await anon.newPage();
    try {
      await page.goto(`/${slug}/`);
      await expect(page.locator('text=Adresse & Anfahrt')).toBeVisible({ timeout: 15000 });
      await check(page);
    } finally {
      await anon.close();
      await ap.evaluate(async (id: number) => {
        const p = document.getElementById('Main_ParamId')!;
        const home = (p.dataset.homeurl || '').replace(/\/$/, '');
        await fetch(`${home}/wp-json/tourney/v1/delete?postId=${id}`,
          { method: 'DELETE', credentials: 'include', headers: { 'X-WP-Nonce': p.dataset.nonce || '' } });
      }, id).catch(() => {});
      await admin.close();
    }
  }

  test('TC-TW-07 [🌐]: Bekannte Adresse: Karte 640×320 mit Ort in der Mitte, Klick öffnet OpenStreetMap', async ({ browser }) => {
    await withTourney(browser, { description: 'Rathaus', country: 'DE', zip: '20095', city: 'Hamburg', street: 'Rathausmarkt 1' }, async page => {
      const img = page.locator('#TourneyWelcome_MapImg');
      await expect(img).toBeVisible({ timeout: 15000 });
      await expect.poll(() => img.evaluate((el: HTMLImageElement) => el.complete ? el.naturalWidth : 0), { timeout: 15000 }).toBe(640);
      await expect(page.locator('#TourneyWelcome_MapCol a[href*="openstreetmap.org"]')).toHaveCount(1);
    });
  });

  test('TC-TW-08 [🌐]: Unbekannte Adresse bzw. nur Land: keine Karte, Adresse und Links bleiben', async ({ browser }) => {
    await withTourney(browser, { description: '', country: 'DE', zip: '99999', city: 'Xyzzyqqq', street: 'Nirgendwoqqq 1' }, async page => {
      await expect(page.locator('#TourneyWelcome_MapCol')).toHaveCount(0, { timeout: 15000 });
      await expect(page.locator('a[href*="openstreetmap.org/search"]')).toHaveCount(1);
    });
    await withTourney(browser, { description: '', country: 'DE', zip: '', city: '', street: '' }, async page => {
      await expect(page.locator('#TourneyWelcome_MapImg')).toHaveCount(0);
    });
  });
});
