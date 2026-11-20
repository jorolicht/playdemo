import { test, expect, Browser, BrowserContext, Page } from '@playwright/test';
import fs from 'fs';
import path from 'path';

/**
 * UseCase 27: Öffentliche Anmeldung zu Wettbewerben (TourneyWelcome, DlgPublicRegistration, DlgCompetition).
 *
 * Ein Administrator legt ein Cloud-Turnier in der Zukunft an (Anmeldung turnierweit erlaubt) mit
 *   1 "Herren Einzel"  – offen, Anmeldung bis in 10 Tagen
 *   2 "Damen Einzel"   – Anmeldung beendet (Checkbox)
 *   3 "Jugend Einzel"  – Anmeldung ab in 5 Tagen
 *   4 "Herren Doppel"  – Doppel, keine Online-Anmeldung
 * Ein anonymer Besucher meldet sich zu 1 an (Pflicht- und freiwillige Angaben, Captcha).
 * In der Dev-Umgebung sind die Cloudflare-Testschlüssel konfiguriert (Captcha besteht automatisch).
 */

const USER = process.env.WP_ADMIN_USER || '';
const PASS = process.env.WP_ADMIN_PASSWORD || '';

test.describe.configure({ mode: 'serial' });

test.describe('UseCase 27: Öffentliche Anmeldung zu Wettbewerben', () => {
  test.skip(!USER || !PASS, 'WP_ADMIN_USER/WP_ADMIN_PASSWORD nicht gesetzt');
  test.skip(({ browserName }) => browserName !== 'chromium', 'nur Chromium (legt ein Cloud-Turnier an)');

  let admin: BrowserContext, anon: BrowserContext;
  let adminPage: Page, page: Page;
  let slug = '';
  let postId = 0;
  const lastName = `Testanmeldung${Date.now() % 100000}`;

  /** Datum in n Tagen als "JJJJ-MM-TT". */
  function day(n: number): string {
    const d = new Date(Date.now() + n * 86400000);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }
  /** "JJJJ-MM-TT" → "TT.MM.JJJJ". */
  const display = (iso: string) => iso.split('-').reverse().join('.');

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
    const base = demo.tourney.tourney.competitions.find((c: any) => c && c.typ === 'SINGLE');
    const date = day(30);
    const comp = (id: number, name: string, extra: any) => ({
      ...base, id, name, status: 'CFG', startStage: null, pants1Stage: [], version: 0,
      startDate: `${date} 10:00:00`, regFrom: '', regTo: '', regClosed: false, ...extra,
    });
    const comps = [
      comp(1, 'Herren Einzel', { regTo: day(10) }),
      comp(2, 'Damen Einzel', { regClosed: true }),
      comp(3, 'Jugend Einzel', { regFrom: day(5) }),
      comp(4, 'Herren Doppel', { typ: 'DOUBLE' }),
    ];
    const dateInt = Number(date.replace(/-/g, ''));
    const t = { wpId: 0, name: `Anmelde-Test ${Date.now()}`, organizer: 'TTC Test', startDate: dateInt, endDate: dateInt,
                ident: '', category: 'TT', clubs: [], players: [], competitions: [], stages: [], address: null };
    const res = await api(adminPage, 'POST', 'create', t);
    expect(res.status, JSON.stringify(res.json)).toBe(200);
    slug = res.json.slug;
    postId = res.json.pageId;
    expect((await api(adminPage, 'POST', `competitions-sync?postId=${postId}`, { events: comps })).status).toBe(200);
    expect((await api(adminPage, 'POST', `save-aci?postId=${postId}`, { allowRegistration: true })).status).toBe(200);

    anon = await browser.newContext({ ignoreHTTPSErrors: true, locale: 'de-DE' });
    page = await anon.newPage();
  });

  test.afterAll(async () => {
    if (postId && adminPage) await api(adminPage, 'DELETE', `delete?postId=${postId}`).catch(() => {});
    await anon?.close();
    await admin?.close();
  });

  /** Öffnet die öffentliche Turnierseite als anonymer Besucher. */
  async function openWelcome() {
    await page.goto(`/${slug}/`);
    await expect(page.locator('tr', { hasText: 'Herren Einzel' })).toBeVisible({ timeout: 15000 });
  }

  test('TC-PR-01 [🌐]: Anmeldestatus je Wettbewerb: offen (mit Enddatum), beendet, ab Datum, Doppel ohne Anmeldung', async () => {
    await openWelcome();
    const row = (name: string) => page.locator('tr', { hasText: name });
    await expect(row('Herren Einzel').getByRole('link', { name: 'Jetzt anmelden' })).toBeVisible();
    await expect(row('Herren Einzel')).toContainText(`bis ${display(day(10))}`);
    await expect(row('Damen Einzel')).toContainText('Anmeldeschluss');
    await expect(row('Damen Einzel').getByRole('link', { name: 'Jetzt anmelden' })).toHaveCount(0);
    await expect(row('Jugend Einzel')).toContainText(`Anmeldung ab ${display(day(5))}`);
    await expect(row('Herren Doppel').getByRole('link', { name: 'Jetzt anmelden' })).toHaveCount(0);
  });

  test('TC-PR-02 [🌐]: Pflichtfelder und Formatprüfung im Anmeldedialog', async () => {
    await page.locator('tr', { hasText: 'Herren Einzel' }).getByRole('link', { name: 'Jetzt anmelden' }).click();
    const dlg = page.locator('.modal.show');
    await expect(dlg).toBeVisible();
    await expect(dlg).toContainText('Herren Einzel');
    await dlg.getByRole('button', { name: 'Anmelden' }).click();
    await expect(page.locator('#reg-error-alert')).toContainText('Pflichtfelder');
    await page.locator('#reg-lastname').fill(lastName);
    await page.locator('#reg-firstname').fill('Erika');
    await page.locator('#reg-club').fill('TTC Testverein');
    await page.locator('#reg-ttr').fill('1450');
    await page.locator('#reg-email').fill('keine-mail');
    await dlg.getByRole('button', { name: 'Anmelden' }).click();
    await expect(page.locator('#reg-error-alert')).toContainText('E-Mail');
    await page.locator('#reg-email').fill('');
    await page.locator('#reg-whatsapp').fill('abc');
    await dlg.getByRole('button', { name: 'Anmelden' }).click();
    await expect(page.locator('#reg-error-alert')).toContainText('Telefonnummer');
    await dlg.locator('[id$="CancelId"]').click();
  });

  test('TC-PR-03 [🌐]: Anmeldung mit freiwilligen Angaben (Jahrgang, E-Mail, WhatsApp) nach Captcha', async () => {
    await openWelcome();
    await page.locator('tr', { hasText: 'Herren Einzel' }).getByRole('link', { name: 'Jetzt anmelden' }).click();
    const dlg = page.locator('.modal.show');
    await page.locator('#reg-lastname').fill(lastName);
    await page.locator('#reg-firstname').fill('Erika');
    await page.locator('#reg-club').fill('TTC Testverein');
    await page.locator('#reg-ttr').fill('1450');
    await page.locator('#reg-birthyear').fill('1985');
    await page.locator('#reg-email').fill('erika@example.com');
    await page.locator('#reg-whatsapp').fill('+49 170 1234567');
    // Turnstile (Testschlüssel) liefert das Token automatisch
    await expect.poll(() => page.locator('[name="cf-turnstile-response"]').inputValue().catch(() => ''), { timeout: 20000 }).not.toBe('');
    const saved = page.waitForResponse(r => r.url().includes('/public-register'));
    await dlg.getByRole('button', { name: 'Anmelden' }).click();
    expect((await saved).status()).toBe(200);
    await expect(page.locator('#reg-step-3')).toContainText('Anmeldung erfolgreich');

    // Teilnehmer und freiwillige Angaben gespeichert (Sicht des Administrators)
    const players = (await api(adminPage, 'GET', `players?postId=${postId}`)).json.players;
    const p = players.find((x: any) => x.lastName === lastName);
    expect(p).toBeTruthy();
    expect(p.birthYear).toBe(1985);
    expect(p.email).toBe('erika@example.com');
    expect(p.whatsApp).toBe('+49 170 1234567');
    const comps = (await api(adminPage, 'GET', `competitions?postId=${postId}`)).json.competitions;
    const c1 = comps.find((c: any) => c && c.id === 1);
    expect(c1.pants1Stage.some((x: any) => x.id === `[${p.id}]` && x.rating === 1450)).toBe(true);
  });

  test('TC-PR-04 [🌐]: Kontaktdaten sind für Besucher nicht abrufbar', async () => {
    const players = (await api(page, 'GET', `players?postId=${postId}`)).json.players;
    const p = players.find((x: any) => x.lastName === lastName);
    expect(p).toBeTruthy();
    expect(p.email).toBeUndefined();
    expect(p.whatsApp).toBeUndefined();
  });

  test('TC-PR-05 [🌐]: Server lehnt Anmeldung zu beendetem, noch nicht geöffnetem und Doppel-Wettbewerb ab', async () => {
    for (const compId of [2, 3, 4]) {
      const res = await api(page, 'POST', `public-register?postId=${postId}`, {
        compId, firstName: 'Max', lastName: 'Umgehung', clubName: 'TTC X', clubId: 0, normalizedName: 'ttc x',
        ttr: 1000, email: '', turnstileToken: 'XXXX.DUMMY.TOKEN.XXXX', birthYear: 0, whatsApp: '',
      });
      expect(res.status, `Wettbewerb ${compId}`).toBe(403);
      expect(res.json.msgCode).toBe('registration_closed');
    }
  });

  test('TC-PR-06 [👤]: Turnierleiter beendet die Anmeldung im Dialog "Wettbewerb bearbeiten" per Checkbox', async () => {
    await adminPage.goto(`/${slug}/`);
    await adminPage.waitForFunction(() => typeof (window as any).appLoadPage === 'function');
    await adminPage.evaluate(() => (window as any).appLoadPage('TourneyInfo', ''));
    await adminPage.locator('tr', { hasText: 'Herren Einzel' }).locator('.comp-link').click();
    await adminPage.locator('[id$="BtnEditComp"]').click();
    const dlg = adminPage.locator('.modal.show');
    await expect(dlg).toContainText('Online-Anmeldung');
    await expect(adminPage.locator('input[id$="RegToId"]')).toHaveValue(day(10));
    await adminPage.locator('input[id$="RegClosedId"]').check();
    const saved = adminPage.waitForResponse(r => r.url().includes('/competitions-sync') && r.status() === 200);
    await dlg.locator('button[id$="ApplyId"]').click();
    await saved;

    await openWelcome();
    await expect(page.locator('tr', { hasText: 'Herren Einzel' })).toContainText('Anmeldeschluss');
    await expect(page.locator('tr', { hasText: 'Herren Einzel' }).getByRole('link', { name: 'Jetzt anmelden' })).toHaveCount(0);
  });
});
