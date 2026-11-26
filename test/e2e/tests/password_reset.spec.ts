import { test, expect, APIRequestContext } from '@playwright/test';
import { execSync } from 'child_process';
import { MailcatcherHelper } from '../helpers/MailcatcherHelper';

/**
 * UseCase 44: Eigener Passwort-Reset in der App statt der WordPress-Standardseiten
 * (pages.PasswordReset, wp-plugin/includes/api/password.php).
 *
 * Ein Testkonto wird per wp-cli angelegt; die Reset-Mail wird im Mailcatcher abgefangen. Die Zähler gegen Missbrauch
 * (Transients tourney_pw_*) werden vor jedem Test gelöscht, damit wiederholte Läufe nicht an die Grenze stoßen.
 */
const WP = 'docker exec wp-cli-instance wp --path=/var/www/html --allow-root';
const stamp = Date.now();
const LOGIN = `pwtest${stamp}`;
const EMAIL = `pwtest_${stamp}@example.com`;
const OLD_PW = 'AltesPasswort1';
const NEW_PW = 'NeuesPasswort2';
let uid = '';

/** Löscht die Zähler gegen Missbrauch (IP und Konto). */
function clearLimits() {
  execSync(`${WP} db query "DELETE FROM wp_options WHERE option_name LIKE '\\_transient%tourney\\_pw\\_%'"`);
}

/** Anmeldung über die REST-API; liefert den HTTP-Status. */
async function loginStatus(request: APIRequestContext, password: string): Promise<number> {
  const r = await request.post('/wp-json/tourney/v1/auth/login', { data: { email: EMAIL, password } });
  return r.status();
}

test.describe('UseCase 44: Eigener Passwort-Reset', () => {
  test.describe.configure({ mode: 'serial' });
  test.use({ locale: 'de-DE' });
  test.skip(({ browserName }) => browserName !== 'chromium', 'nur Chromium (Mailcatcher, Testkonto)');

  test.beforeAll(() => {
    uid = execSync(`${WP} user create ${LOGIN} ${EMAIL} --user_pass=${OLD_PW} --role=subscriber --porcelain`).toString().trim();
    execSync(`${WP} user meta update ${uid} pd_status active`);
  });

  test.afterAll(() => {
    if (uid) execSync(`${WP} user delete ${uid} --yes`);
    clearLimits();
  });

  test.beforeEach(async () => {
    clearLimits();
    await MailcatcherHelper.clearMessages();
  });

  test('TC-PW-01 [🌐]: „Passwort vergessen?“ öffnet die App-Seite; unbekannte Adresse erhält dieselbe Antwort, aber keine Mail', async ({ page }) => {
    await page.goto('/login/');
    await page.locator('a[id$="BtnForgot"]').click();
    await expect(page.locator('[data-testid="pwreset"]')).toHaveAttribute('data-mode', 'Forgot');
    expect(page.url()).not.toContain('wp-login.php');

    // ungültige Eingabe
    await page.locator('input[id$="EmailId"]').fill('keine-adresse');
    await page.locator('button[id$="BtnSend"]').click();
    await expect(page.locator('[data-testid="pwreset-msg"]')).toContainText('gültige E-Mail-Adresse');

    const unknown = `unbekannt_${stamp}@example.com`;
    await page.locator('input[id$="EmailId"]').fill(unknown);
    await page.locator('button[id$="BtnSend"]').click();
    await expect(page.locator('[data-testid="pwreset-sent"]')).toContainText('Falls zu dieser Adresse ein Konto existiert');
    await page.waitForTimeout(1500);
    expect((await MailcatcherHelper.getMessages()).filter(m => m.recipients.some(r => r.includes(unknown)))).toHaveLength(0);
  });

  test('TC-PW-02 [🌐]: Mail mit App-Link, neues Passwort setzen, Link nur einmal gültig, Anmeldung mit neuem Passwort', async ({ page, request }) => {
    await page.goto('/login/');
    await page.locator('a[id$="BtnForgot"]').click();
    await page.locator('input[id$="EmailId"]').fill(EMAIL);
    await page.locator('button[id$="BtnSend"]').click();
    await expect(page.locator('[data-testid="pwreset-sent"]')).toBeVisible();

    const mail = await MailcatcherHelper.waitForMessage(EMAIL, 15000);
    expect(mail.subject).toContain('Passwort zurücksetzen');
    expect(mail.plainBody).toContain('24 Stunden');
    const link = mail.plainBody.match(/https?:\/\/\S+tourney_rp=\S+/)?.[0] || '';
    expect(link).toContain(`tourney_rp=${LOGIN.toLowerCase()}`);
    expect(link).toContain('/login/');          // zurück auf die Seite, von der der Antrag kam
    expect(link).not.toContain('wp-login.php');

    await page.goto(link);
    await expect(page.locator('[data-testid="pwreset"]')).toHaveAttribute('data-mode', 'Set', { timeout: 15000 });

    // Prüfungen im Formular
    await page.locator('input[id$="Pw1Id"]').fill('kurz');
    await page.locator('input[id$="Pw2Id"]').fill('kurz');
    await page.locator('button[id$="BtnSave"]').click();
    await expect(page.locator('[data-testid="pwreset-msg"]')).toContainText('mindestens 8 Zeichen');
    await page.locator('input[id$="Pw1Id"]').fill(NEW_PW);
    await page.locator('input[id$="Pw2Id"]').fill(NEW_PW + 'x');
    await page.locator('button[id$="BtnSave"]').click();
    await expect(page.locator('[data-testid="pwreset-msg"]')).toContainText('stimmen nicht überein');

    await page.locator('input[id$="Pw2Id"]').fill(NEW_PW);
    await page.locator('button[id$="BtnSave"]').click();
    await expect(page.locator('[data-testid="pwreset-done"]')).toContainText('Ihr Passwort wurde geändert');
    expect(page.url()).not.toContain('tourney_rp');   // Link aus der Adresszeile entfernt

    // Link ein zweites Mal: ungültig
    await page.goto(link);
    await expect(page.locator('[data-testid="pwreset-invalid"]')).toBeVisible({ timeout: 15000 });

    expect(await loginStatus(request, NEW_PW)).toBe(200);
    expect(await loginStatus(request, OLD_PW)).toBe(403);
  });

  test('TC-PW-03 [🌐]: Server prüft Schlüssel und Passwortlänge selbst', async ({ request }) => {
    const bad = await request.post('/wp-json/tourney/v1/auth/reset', { data: { login: LOGIN, key: 'falsch', password: 'Irgendwas123' } });
    expect(bad.status()).toBe(403);
    expect((await request.post('/wp-json/tourney/v1/auth/check-reset', { data: { login: LOGIN, key: 'falsch' } })).status()).toBe(403);

    // gültiger Schlüssel, aber zu kurzes Passwort
    expect((await request.post('/wp-json/tourney/v1/auth/forgot', { data: { email: EMAIL, returnUrl: '/login/' } })).status()).toBe(200);   // relativ: Link auf die Startseite
    const mail = await MailcatcherHelper.waitForMessage(EMAIL, 15000);
    const url = new URL(mail.plainBody.match(/https?:\/\/\S+tourney_rp=\S+/)![0]);
    const key = url.searchParams.get('key')!;
    const short = await request.post('/wp-json/tourney/v1/auth/reset', { data: { login: LOGIN, key, password: 'kurz' } });
    expect(short.status()).toBe(400);
    expect((await request.post('/wp-json/tourney/v1/auth/check-reset', { data: { login: LOGIN, key } })).status()).toBe(200);
  });

  test('TC-PW-04 [🌐]: Fremde Rücksprung-Adresse wird nicht übernommen; Grenze je Konto und IP', async ({ request }) => {
    expect((await request.post('/wp-json/tourney/v1/auth/forgot', { data: { email: EMAIL, returnUrl: 'https://boese.example/phish' } })).status()).toBe(200);
    const mail = await MailcatcherHelper.waitForMessage(EMAIL, 15000);
    const link = mail.plainBody.match(/https?:\/\/\S+tourney_rp=\S+/)![0];
    expect(link).not.toContain('boese.example');

    // zweiter Antrag innerhalb von 2 Minuten: gleiche Antwort, aber keine weitere Mail
    await MailcatcherHelper.clearMessages();
    expect((await request.post('/wp-json/tourney/v1/auth/forgot', { data: { email: EMAIL } })).status()).toBe(200);
    await new Promise(r => setTimeout(r, 1500));
    expect(await MailcatcherHelper.getMessages()).toHaveLength(0);

    // je IP höchstens 5 Anträge in 15 Minuten
    for (let i = 0; i < 3; i++) await request.post('/wp-json/tourney/v1/auth/forgot', { data: { email: `x${i}@example.com` } });
    expect((await request.post('/wp-json/tourney/v1/auth/forgot', { data: { email: 'y@example.com' } })).status()).toBe(429);
  });

  test('TC-PW-05 [🌐]: WordPress-Seiten „Passwort vergessen“, „E-Mail gesendet“ und „Zurücksetzen“ leiten in die App um; WordPress-Mail mit App-Link', async ({ page }) => {
    await page.goto('/wp-login.php?action=lostpassword');
    await expect(page).toHaveURL(/tourney_pw=forgot/);
    await expect(page.locator('[data-testid="pwreset"]')).toHaveAttribute('data-mode', 'Forgot', { timeout: 15000 });

    // Formular von WordPress direkt abgeschickt: eigene Mail, Bestätigung in der App
    await MailcatcherHelper.clearMessages();
    const res = await page.request.post('/wp-login.php?action=lostpassword', { form: { user_login: LOGIN, 'wp-submit': 'x' } });
    expect(res.url()).toContain('tourney_pw=sent');
    const mail = await MailcatcherHelper.waitForMessage(EMAIL, 15000);
    expect(mail.subject).toContain('Passwort zurücksetzen');
    expect(mail.plainBody).toMatch(/\?tourney_rp=\S+&key=/);
    await page.goto(res.url());
    await expect(page.locator('[data-testid="pwreset-sent"]')).toBeVisible({ timeout: 15000 });

    await page.goto(`/wp-login.php?action=rp&key=falsch&login=${LOGIN}`);
    await expect(page).toHaveURL(/tourney_rp=/);
    await expect(page.locator('[data-testid="pwreset-invalid"]')).toBeVisible({ timeout: 15000 });
  });
});
