import { test, expect, Browser, BrowserContext, Page } from '@playwright/test';
import fs from 'fs';
import path from 'path';
import { MailcatcherHelper } from '../helpers/MailcatcherHelper';

/**
 * UseCase 33: Online-Anmeldung mit Warteliste (Competition.maxPants, public-register, waitlist-notify).
 *
 * Ein Administrator legt ein Cloud-Turnier an mit
 *   1 "Herren Einzel" – Höchstzahl 1
 *   2 "Damen Einzel"  – ohne Höchstzahl
 * Spieler A belegt den einzigen Platz, Spielerin B kommt auf die Warteliste und rückt nach, wenn der Turnierleiter A
 * entfernt. Beide geben eine E-Mail-Adresse an und erhalten Mails (Dev: Mailcatcher, http://localhost:1080).
 */

const USER = process.env.WP_ADMIN_USER || '';
const PASS = process.env.WP_ADMIN_PASSWORD || '';

test.describe.configure({ mode: 'serial' });

test.describe('UseCase 33: Online-Anmeldung mit Warteliste', () => {
  test.skip(!USER || !PASS, 'WP_ADMIN_USER/WP_ADMIN_PASSWORD nicht gesetzt');
  test.skip(({ browserName }) => browserName !== 'chromium', 'nur Chromium (Cloud-Turnier, Mailcatcher)');

  let admin: BrowserContext, anon: BrowserContext;
  let adminPage: Page, page: Page;
  let slug = '';
  let postId = 0;
  const stamp = Date.now() % 100000;
  const mailA = `wl-a-${stamp}@example.com`;
  const mailB = `wl-b-${stamp}@example.com`;

  /** Datum in n Tagen als "JJJJ-MM-TT". */
  function day(n: number): string {
    const d = new Date(Date.now() + n * 86400000);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
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

  /** Wartet auf eine Mail an `to`, deren Betreff `subject` enthält. */
  async function expectMail(to: string, subject: string) {
    await expect.poll(async () => (await MailcatcherHelper.getMessages())
      .filter(m => m.recipients.some(r => r.includes(to))).map(m => m.subject).join(' | '),
      { timeout: 15000 }).toContain(subject);
  }

  /** Meldet über die öffentliche Turnierseite an (Turnstile-Testschlüssel: Captcha besteht automatisch). */
  async function registerViaDialog(comp: string, button: string, last: string, first: string, email: string) {
    await page.goto(`/${slug}/`);
    await page.locator('tr', { hasText: comp }).getByRole('link', { name: button }).click({ timeout: 15000 });
    await page.locator('#reg-lastname').fill(last);
    await page.locator('#reg-firstname').fill(first);
    await page.locator('#reg-club').fill('TTC Warteliste');
    await page.locator('#reg-ttr').fill('1500');
    await page.locator('#reg-email').fill(email);
    await expect.poll(() => page.locator('[name="cf-turnstile-response"]').inputValue().catch(() => ''), { timeout: 20000 }).not.toBe('');
    const saved = page.waitForResponse(r => r.url().includes('/public-register'));
    await page.locator('.modal.show').getByRole('button', { name: 'Anmelden' }).click();
    expect((await saved).status()).toBe(200);
  }

  /** Wettbewerb 1 aus Sicht des Administrators. */
  async function comp1(): Promise<any> {
    return (await api(adminPage, 'GET', `competitions?postId=${postId}`)).json.competitions.find((c: any) => c && c.id === 1);
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
      ...base, id, name, status: 'CFG', startStage: null, pants1Stage: [], version: 0, stagePlan: [],
      startDate: `${date} 10:00:00`, regFrom: '', regTo: '', regClosed: false, maxPants: 0, ...extra,
    });
    const dateInt = Number(date.replace(/-/g, ''));
    const t = { wpId: 0, name: `Warteliste-Test ${stamp}`, organizer: 'TTC Test', startDate: dateInt, endDate: dateInt,
                ident: '', category: 'TT', clubs: [], players: [], competitions: [], stages: [], address: null };
    const res = await api(adminPage, 'POST', 'create', t);
    expect(res.status, JSON.stringify(res.json)).toBe(200);
    slug = res.json.slug;
    postId = res.json.pageId;
    const comps = [comp(1, 'Herren Einzel', { maxPants: 1 }), comp(2, 'Damen Einzel', {})];
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

  test('TC-WL-01 [🌐]: Freier Platz: Anmeldung mit fester Teilnahme und Bestätigungsmail', async () => {
    await page.goto(`/${slug}/`);
    await expect(page.locator('[data-testid="places-1"]')).toHaveText('1 von 1 Plätzen frei', { timeout: 15000 });
    await expect(page.locator('[data-testid="places-2"]')).toHaveCount(0);
    await registerViaDialog('Herren Einzel', 'Jetzt anmelden', `Anton${stamp}`, 'Anton', mailA);
    await expect(page.locator('#reg-success-title')).toHaveText('Anmeldung erfolgreich!');
    await expectMail(mailA, 'Anmeldung bestätigt: Herren Einzel');
    const p = (await comp1()).pants1Stage;
    expect(p).toHaveLength(1);
    expect(p[0].status).toBe('REGI');
  });

  test('TC-WL-02 [🌐]: Ausgebucht: Anmeldung auf die Warteliste mit Platz und Mail', async () => {
    await page.goto(`/${slug}/`);
    const row = page.locator('tr', { hasText: 'Herren Einzel' });
    await expect(row.locator('[data-testid="places-1"]')).toHaveText('Ausgebucht – 0 auf der Warteliste', { timeout: 15000 });
    await row.getByRole('link', { name: 'Auf die Warteliste' }).click();
    await expect(page.locator('[data-testid="reg-waitlist-info"]')).toContainText('ausgebucht (1 Plätze)');
    await page.locator('.modal.show button[id$="CancelId"]').click();

    await registerViaDialog('Herren Einzel', 'Auf die Warteliste', `Berta${stamp}`, 'Berta', mailB);
    await expect(page.locator('#reg-success-title')).toHaveText('Auf der Warteliste');
    await expect(page.locator('#reg-success-msg')).toContainText('Platz 1 der Warteliste');
    await expect(page.locator('#reg-success-hint')).toContainText('erhalten eine E-Mail');
    await expectMail(mailB, 'Warteliste: Herren Einzel');

    const b = (await comp1()).pants1Stage.find((x: any) => x.name.startsWith(`Berta${stamp}`));
    expect(b.status).toBe('WAIT');
    expect(b.active).toBe(false);
  });

  test('TC-WL-03 [👤]: Turnierleiter entfernt einen Teilnehmer: Warteliste rückt nach, Mail an Nachrücker', async () => {
    await adminPage.goto(`/${slug}/`);
    await adminPage.waitForFunction(() => typeof (window as any).appLoadPage === 'function');
    await adminPage.evaluate(() => (window as any).appLoadPage('TourneyInfo', ''));
    await adminPage.locator('tr', { hasText: 'Herren Einzel' }).locator('.comp-link').click({ timeout: 15000 });
    await adminPage.evaluate(() => (window as any).appLoadPage('PlayerRegistration', ''));
    const rowB = adminPage.locator('tr', { hasText: `Berta${stamp}` });
    await expect(rowB.locator('[data-testid="waitlist-badge"]')).toHaveText('Warteliste 1', { timeout: 10000 });

    const notified = adminPage.waitForResponse(r => r.url().includes('/waitlist-notify'));
    await adminPage.locator('tr', { hasText: `Anton${stamp}` }).locator('.delete-participant-btn').click();
    const res = await notified;
    expect(res.status()).toBe(200);
    expect((await res.json()).sent).toBe(1);
    await expect(rowB.locator('[data-testid="waitlist-badge"]')).toHaveCount(0);
    await expectMail(mailB, 'Nachgerückt: Herren Einzel');

    const p = (await comp1()).pants1Stage;
    expect(p).toHaveLength(1);
    expect(p[0].name).toContain(`Berta${stamp}`);
    expect(p[0].status).toBe('REGI');
    expect(p[0].active).toBe(true);
  });

  test('TC-WL-04 [👤]: Höchstzahl im Dialog "Wettbewerb bearbeiten" festlegen', async () => {
    await adminPage.evaluate(() => (window as any).appLoadPage('TourneyInfo', ''));
    await adminPage.locator('tr', { hasText: 'Damen Einzel' }).locator('.comp-link').click({ timeout: 15000 });
    await adminPage.locator('[id$="BtnEditComp"]').click();
    const dlg = adminPage.locator('.modal.show');
    await expect(adminPage.locator('input[id$="MaxPantsId"]')).toHaveValue('');
    await adminPage.locator('input[id$="MaxPantsId"]').fill('16');
    const saved = adminPage.waitForResponse(r => r.url().includes('/competitions-sync') && r.status() === 200);
    await dlg.locator('button[id$="ApplyId"]').click();
    await saved;
    const c2 = (await api(adminPage, 'GET', `competitions?postId=${postId}`)).json.competitions.find((c: any) => c && c.id === 2);
    expect(c2.maxPants).toBe(16);

    await page.goto(`/${slug}/`);
    await expect(page.locator('[data-testid="places-2"]')).toHaveText('16 von 16 Plätzen frei', { timeout: 15000 });
  });
});
