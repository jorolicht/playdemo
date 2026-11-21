import { test, expect, Browser, BrowserContext, Page } from '@playwright/test';
import { execSync } from 'child_process';
import fs from 'fs';
import path from 'path';

/**
 * UseCase 30: Abrechnung Cloud-Turniere (includes/user.php, cpt.php) und Archiv.
 *
 * Ein Testbenutzer mit Rolle tourney_admin (per wp-cli angelegt, ohne Administratorrechte):
 *   - Guthaben in Posten, gekaufte Turniere 2 Jahre gültig
 *   - Reservierung beim Anlegen, Rückgabe beim Löschen ohne Ergebnis
 *   - Vereins-/Verbandslizenz: Anlegen ohne Guthaben, keine Archivierung
 *   - Archiv 1 Jahr nach Turnierende: schreibgeschützt, öffentlich lesbar
 */

const ADMIN = process.env.WP_ADMIN_USER || '';
const ADMIN_PASS = process.env.WP_ADMIN_PASSWORD || '';
const WP = 'docker exec wp-cli-instance wp --path=/var/www/html --allow-root';
const LOGIN = `crtest${Date.now() % 100000}`;
const PASS = `Pw-${Date.now()}-x`;

/** Führt wp-cli aus und liefert die Ausgabe. */
function wp(args: string): string {
  return execSync(`${WP} ${args}`).toString().trim();
}

/** Guthaben-Profil des Testbenutzers. */
function profile(id: number): any {
  return JSON.parse(wp(`eval 'echo wp_json_encode(tourney_get_user_profile(${id}));'`));
}

/** Datum in n Tagen als "JJJJ-MM-TT". */
function day(n: number): string {
  return new Date(Date.now() + n * 86400000).toISOString().slice(0, 10);
}

test.describe.configure({ mode: 'serial' });

test.describe('UseCase 30: Abrechnung und Archiv', () => {
  test.skip(!ADMIN || !ADMIN_PASS, 'WP_ADMIN_USER/WP_ADMIN_PASSWORD nicht gesetzt');
  test.skip(({ browserName }) => browserName !== 'chromium', 'nur Chromium (wp-cli, Cloud-Turniere)');

  let userId = 0;
  let ctx: BrowserContext, admin: BrowserContext;
  let page: Page, adminPage: Page;
  const created: number[] = [];

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

  /** Legt als Testbenutzer ein Cloud-Turnier an. */
  async function create(name: string, endDate = 0): Promise<any> {
    const d = endDate || Number(day(20).replace(/-/g, ''));
    const t = { wpId: 0, name: `${name} ${Date.now()}`, organizer: LOGIN, startDate: d, endDate: d,
                ident: '', category: 'TT', clubs: [], players: [], competitions: [], stages: [], address: null };
    const res = await api(page, 'POST', 'create', t);
    if (res.status === 200) created.push(res.json.pageId);
    return res;
  }

  async function login(c: BrowserContext, p: Page, user: string, pass: string) {
    await p.goto('/login/');
    await p.locator('input[id*="LoginId"]').fill(user);
    await p.locator('input[id*="PasswordId"]').fill(pass);
    await p.locator('button[id*="BtnLogin"]').click();
    await expect.poll(async () => (await c.cookies()).some(x => x.name.startsWith('wordpress_logged_in')), { timeout: 10000 }).toBe(true);
    await p.goto('/');
  }

  test.beforeAll(async ({ browser }: { browser: Browser }) => {
    userId = Number(wp(`user create ${LOGIN} ${LOGIN}@example.com --role=tourney_admin --user_pass='${PASS}' --porcelain`));
    wp(`user meta update ${userId} organizer ${LOGIN}`);
    ctx = await browser.newContext({ ignoreHTTPSErrors: true, locale: 'de-DE' });
    page = await ctx.newPage();
    await login(ctx, page, `${LOGIN}@example.com`, PASS);
    admin = await browser.newContext({ ignoreHTTPSErrors: true, locale: 'de-DE' });
    adminPage = await admin.newPage();
    await login(admin, adminPage, ADMIN, ADMIN_PASS);
  });

  test.afterAll(async () => {
    for (const id of created) await api(adminPage, 'DELETE', `delete?postId=${id}`).catch(() => {});
    if (userId) { try { wp(`user delete ${userId} --yes`); } catch { /* ignore */ } }
    await ctx?.close();
    await admin?.close();
  });

  test('TC-CR-01 [👤]: Kauf = Posten 2 Jahre gültig; Anlegen reserviert, Löschen ohne Ergebnis gibt zurück', async () => {
    expect((await create('Ohne Guthaben')).json.msgCode).toBe('tourney_limit_reached');
    wp(`eval 'tourney_user_profile_add_purchase(${userId}, 1, 9.9);'`);
    let p = profile(userId);
    expect(p.available).toBe(1);
    const expires = p.lots[0].expires;
    expect(expires >= day(729) && expires <= day(731)).toBe(true);

    const res = await create('Reserviert');
    expect(res.status).toBe(200);
    p = profile(userId);
    expect(p.available).toBe(0);
    expect(p.executed).toBe(1);

    expect((await api(page, 'DELETE', `delete?postId=${res.json.pageId}`)).status).toBe(200);
    p = profile(userId);
    expect(p.available).toBe(1);
    expect(p.executed).toBe(0);
    expect(p.lots.map((l: any) => l.expires)).toContain(expires);
  });

  test('TC-CR-02 [👤]: Mit eingetragenem Ergebnis gibt Löschen nichts zurück', async () => {
    const res = await create('Mit Ergebnis');
    expect(res.status).toBe(200);
    expect(profile(userId).available).toBe(0);
    const demo = JSON.parse(fs.readFileSync(path.resolve(__dirname, '../../../wp-plugin/data/demo_tourney.json'), 'utf8'));
    const stage = demo.tourney.tourney.stages.find((s: any) => s && s.id === 5);   // enthält beendete Spiele
    expect((await api(page, 'POST', `stages-sync?postId=${res.json.pageId}`, { events: [stage] })).status).toBe(200);
    expect(wp(`post meta get ${res.json.pageId} _has_results`)).toBe('1');
    expect((await api(page, 'DELETE', `delete?postId=${res.json.pageId}`)).status).toBe(200);
    expect(profile(userId).available).toBe(0);
  });

  test('TC-CR-03 [👤]: Abgelaufenes Guthaben zählt nicht', async () => {
    wp(`user meta update ${userId} user_profile '{"available":2,"executed":0,"history":[],"lots":[{"remaining":2,"expires":"2020-01-01"}]}'`);
    expect(profile(userId).available).toBe(0);
    expect((await create('Abgelaufen')).json.msgCode).toBe('tourney_limit_reached');
  });

  test('TC-CR-04 [👑]: Lizenz in der Management-Konsole: Anlegen ohne Guthaben, kein Verbrauch', async () => {
    const r = await api(adminPage, 'POST', 'admin/update_user_profile', { user_id: userId, license_until: day(30) });
    expect(r.status, JSON.stringify(r.json)).toBe(200);
    expect(r.json.license_until).toBe(day(30));
    const res = await create('Mit Lizenz');
    expect(res.status).toBe(200);
    expect(profile(userId).available).toBe(0);
    expect(profile(userId).executed).toBe(0);
    expect(wp(`eval 'echo get_post_meta(${res.json.pageId}, "_counter_decremented", true);'`)).toBe('');
  });

  test('TC-AR-01 [👤]: Archiv 1 Jahr nach Turnierende: schreibgeschützt für den Turnierleiter, öffentlich lesbar', async () => {
    const res = await create('Archiv', 20230101);
    expect(res.status).toBe(200);
    const id = res.json.pageId;
    wp(`post update ${id} --post_date='2023-01-01 10:00:00'`);
    // mit Lizenz nicht archiviert
    expect((await api(page, 'GET', `read-aci?postId=${id}`)).json.archived).toBe(false);

    await api(adminPage, 'POST', 'admin/update_user_profile', { user_id: userId, license_until: '' });
    await expect.poll(async () => (await api(page, 'GET', `read-aci?postId=${id}`)).json.archived).toBe(true);

    const w = await api(page, 'POST', `players-sync?postId=${id}`, { version: 0, players: [] });
    expect(w.status).toBe(403);
    expect(w.json.code).toBe('tourney_archived');
    expect((await api(adminPage, 'POST', `players-sync?postId=${id}`, { version: 0, players: [] })).status).toBe(200);

    // Turnierleiter sieht die öffentliche Seite mit Archiv-Hinweis
    await page.goto(`/${res.json.slug}/`);
    await expect(page.locator('[data-testid="tourney-archived"]')).toContainText('Archiv', { timeout: 15000 });
    expect(await page.locator('#Main_ParamId').getAttribute('data-canedit')).toBe('0');
  });
});
