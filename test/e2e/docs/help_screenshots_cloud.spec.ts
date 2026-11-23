import { test, expect, Browser, BrowserContext, Page } from '@playwright/test';
import fs from 'fs';
import path from 'path';

/**
 * Screenshots für das Benutzerhandbuch (site/content/hilfe.html), Teil 2: Cloud-Turnier aus den Demo-Daten.
 * Ein Administrator legt das Turnier an (Anmeldung freigeschaltet, Logo); Besucher sehen Turnierseite,
 * Online-Anmeldung und „Mein Turnier“. Aufnahmen als Administrator zeigen nur den Seiteninhalt (kein Benutzername).
 * Das Turnier wird danach wieder gelöscht.
 */

const OUT = path.resolve(__dirname, '../../../site/content/hilfe');
const USER = process.env.WP_ADMIN_USER || '';
const PASS = process.env.WP_ADMIN_PASSWORD || '';
const LOGO = path.resolve(__dirname, '../../../wp-plugin/img/pwa-512.png');

async function clean(page: Page) {
  await page.addStyleTag({ content: '#wpadminbar{display:none!important} html{margin-top:0!important}' });
}

async function shot(page: Page, name: string, target?: string) {
  await page.waitForTimeout(800);
  if (target) await page.locator(target).first().screenshot({ path: `${OUT}/${name}.png` });
  else await page.screenshot({ path: `${OUT}/${name}.png` });
}

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

test('Handbuch: Cloud-Turnier, Turnierseite und Spieleransicht', async ({ browser }: { browser: Browser }) => {
  test.skip(!USER || !PASS, 'WP_ADMIN_USER/WP_ADMIN_PASSWORD nicht gesetzt');
  const admin: BrowserContext = await browser.newContext({ ignoreHTTPSErrors: true, locale: 'de-DE', viewport: { width: 1280, height: 1300 } });
  const ap = await admin.newPage();
  await ap.goto('/login/');
  await ap.locator('input[id*="LoginId"]').fill(USER);
  await ap.locator('input[id*="PasswordId"]').fill(PASS);
  await ap.locator('button[id*="BtnLogin"]').click();
  await expect.poll(async () => (await admin.cookies()).some(c => c.name.startsWith('wordpress_logged_in')), { timeout: 10000 }).toBe(true);
  await ap.goto('/');

  const demo = JSON.parse(fs.readFileSync(path.resolve(__dirname, '../../../wp-plugin/data/demo_tourney.json'), 'utf8'));
  const t = demo.tourney.tourney;
  t.wpId = 0;
  t.name = 'Vereinsmeisterschaft 2028';
  t.address = { description: 'Sporthalle', country: 'DE', zip: '20095', city: 'Hamburg', street: 'Rathausmarkt 1' };
  const res = await api(ap, 'POST', 'create?overwrite=1', t);   // Rest eines abgebrochenen Laufs überschreiben
  expect(res.status, JSON.stringify(res.json)).toBe(200);
  const slug = res.json.slug; const postId = res.json.pageId;
  try {
    await api(ap, 'POST', `clubs-sync?postId=${postId}`, { version: 0, clubs: t.clubs });
    await api(ap, 'POST', `players-sync?postId=${postId}`, { version: 0, players: t.players });
    await api(ap, 'POST', `competitions-sync?postId=${postId}`, { events: t.competitions.filter((c: any) => c) });
    await api(ap, 'POST', `stages-sync?postId=${postId}`, { events: t.stages.filter((s: any) => s) });
    await api(ap, 'POST', `save-aci?postId=${postId}`, { allowRegistration: true,
      homepageInfo: '**Startgeld:** 8 €, Jugend 5 €. Bitte eigene Bälle mitbringen.' });

    // Administration, Reiter Homepage, mit Logo
    await ap.goto(`/${slug}/`);
    await clean(ap);
    await expect(ap.getByText('Turnier-Informationen')).toBeVisible({ timeout: 15000 });
    await ap.evaluate(() => (window as any).appLoadPage('TourneyAdmin', 'HOMEPAGE'));
    await expect(ap.locator('[data-testid="logo-section"]')).toBeVisible({ timeout: 10000 });
    await ap.locator('#tourney-logo-file').setInputFiles(LOGO);
    await expect(ap.locator('#tourney-logo-preview')).toBeVisible({ timeout: 10000 });
    // Navigationsleiste (mit Benutzername) ausblenden, nur den Inhalt fotografieren
    await ap.addStyleTag({ content: 'nav.navbar, #navbarMain, .navbar-custom{display:none!important}' });
    await ap.evaluate(() => window.scrollTo(0, 0));
    await shot(ap, '17-administration-homepage', '#Main_ContentId');

    // Besucher: Turnierseite, Online-Anmeldung
    const anon = await browser.newContext({ ignoreHTTPSErrors: true, locale: 'de-DE', viewport: { width: 1280, height: 900 } });
    const p = await anon.newPage();
    await p.goto(`/${slug}/`);
    await clean(p);
    await expect(p.locator('[data-testid="my-tourney-btn"]')).toBeVisible({ timeout: 15000 });
    await shot(p, '18-turnierseite');
    const reg = p.getByRole('link', { name: 'Jetzt anmelden' }).first();
    await reg.click();
    await expect(p.locator('.modal.show')).toBeVisible();
    await p.locator('#reg-lastname').fill('Muster');
    await p.locator('#reg-firstname').fill('Max');
    await p.locator('#reg-club').fill('TTC Musterstadt');
    await p.locator('#reg-ttr').fill('1520');
    await shot(p, '19-online-anmeldung', '.modal.show .modal-content');
    await anon.close();

    // Spieler am Handy: Mein Turnier
    const mobile = await browser.newContext({ ignoreHTTPSErrors: true, locale: 'de-DE', viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true });
    const m = await mobile.newPage();
    await m.goto(`/${slug}/`);
    await clean(m);
    await m.locator('[data-testid="my-tourney-btn"]').click();
    await m.locator('select[id$="SelectId"]').selectOption({ label: 'Hose, Lutz' });
    await m.locator('button[id$="BtnSelectId"]').click();
    await expect(m.locator('[data-testid="my-player"]')).toBeVisible();
    await shot(m, '20-mein-turnier');
    await mobile.close();
  } finally {
    await api(ap, 'DELETE', `delete?postId=${postId}`).catch(() => {});
    await admin.close();
  }
});
