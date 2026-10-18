import { test, expect } from '@playwright/test';
import { UserRegistrationPage } from '../pages/UserRegistrationPage';
import { LoginPage } from '../pages/LoginPage';
import { MailcatcherHelper } from '../helpers/MailcatcherHelper';
import { execSync } from 'child_process';

test.describe('UseCase 11: Automatische Benutzer-Registrierung via Mailcatcher (UserRegistration & VerifyAccount)', () => {
  test.describe.configure({ mode: 'serial' });

  let regPage: UserRegistrationPage;
  let loginPage: LoginPage;
  const createdUserIds: string[] = [];

  test.beforeEach(async ({ page }) => {
    regPage = new UserRegistrationPage(page);
    loginPage = new LoginPage(page);
    page.on('dialog', async dialog => {
      await dialog.accept();
    });
    await page.context().clearCookies();
    await MailcatcherHelper.clearMessages();
  });

  test.afterAll(async () => {
    // Cleanup created test users from WordPress DB
    for (const uid of createdUserIds) {
      try {
        execSync(`docker exec wp-cli-instance wp user delete ${uid} --yes --path=/var/www/html --allow-root 2>/dev/null`);
      } catch (e) {
        // Ignore cleanup error
      }
    }
  });

  test('TC-REG-01 [🌐]: Mailcatcher REST-API ist erreichbar und einsatzbereit', async () => {
    const msgs = await MailcatcherHelper.getMessages();
    expect(Array.isArray(msgs)).toBe(true);
  });

  test('TC-REG-02 [🌐]: Passwortbasierte Registrierung, Empfang der Aktivierungsmail in Mailcatcher, Verifizierung & Login', async ({ page }) => {
    const timestamp = Date.now();
    const testEmail = `testuser_${timestamp}@example.com`;
    const testPassword = 'Password1234!';

    // 1. Registrierungsseite aufrufen
    await regPage.goto();

    // 2. Registrierungsformular ausfüllen (mit Passwort, normaler Benutzer)
    await regPage.register({
      email: testEmail,
      password: testPassword,
      isAdmin: false
    });

    // 3. Bestätigungsansicht nach Registrierung prüfen
    await expect(regPage.successView).toBeVisible({ timeout: 10000 });
    await expect(page.locator('text=Registrierung fast abgeschlossen')).toBeVisible();

    // 4. Aktivierungs-E-Mail aus Mailcatcher abfangen
    const mail = await MailcatcherHelper.waitForMessage(testEmail, 15000);
    expect(mail.subject).toContain('Willkommen bei Tourney - Bitte E-Mail bestätigen');
    expect(mail.plainBody.toLowerCase()).toContain(testEmail.split('@')[0].toLowerCase());

    // 5. Verifizierungs-URL aus der Mail extrahieren
    const verifyUrl = MailcatcherHelper.extractVerificationUrl(mail.plainBody);
    expect(verifyUrl).not.toBeNull();

    // User-ID für späteren Cleanup merken
    const uidMatch = verifyUrl!.match(/uid=(\d+)/);
    if (uidMatch) {
      createdUserIds.push(uidMatch[1]);
    }

    // 6. Verifizierungslink im Browser aufrufen
    await page.goto(verifyUrl!);
    await page.waitForLoadState('domcontentloaded');

    // 7. Prüfen, dass Verifizierung erfolgreich war
    const verifyView = page.locator('.verify-account-view');
    await expect(verifyView).toBeVisible({ timeout: 10000 });
    await expect(page.locator('h2:has-text("Erfolgreich!")')).toBeVisible();
    await expect(page.locator('text=E-Mail erfolgreich bestätigt')).toBeVisible();

    // 8. Zur Login-Seite navigieren: User ist nach erfolgreicher Verifizierung bereits eingeloggt
    await loginPage.goto();
    await expect(loginPage.logoutBtn).toBeVisible({ timeout: 8000 });

    // Zur vollständigen Prüfung der Anmeldedaten: Logout & regulärer Login mit Passwort
    await loginPage.logout();
    await loginPage.goto();
    await loginPage.login(testEmail, testPassword);
    await expect(loginPage.successAlert).toBeVisible({ timeout: 10000 });
    await page.waitForURL('**/', { timeout: 10000 });
    await expect(page.locator('#Main_NavbarId')).toContainText(testEmail.split('@')[0], { ignoreCase: true, timeout: 10000 });
  });

  test('TC-REG-03 [🌐]: Passwortlose Registrierung, E-Mail-Empfang und Aktivierung via Verifizierungslink', async ({ page }) => {
    const timestamp = Date.now();
    const testEmail = `testpassless_${timestamp}@example.com`;

    // 1. Registrierungsseite aufrufen
    await regPage.goto();

    // 2. Passwortlos registrieren (Checkbox bleibt aktiv)
    await regPage.register({
      email: testEmail,
      isAdmin: false
    });

    // 3. Bestätigungsansicht
    await expect(regPage.successView).toBeVisible({ timeout: 10000 });

    // 4. E-Mail in Mailcatcher abfangen
    const mail = await MailcatcherHelper.waitForMessage(testEmail, 15000);
    expect(mail.subject).toContain('Willkommen bei Tourney - Bitte E-Mail bestätigen');

    const verifyUrl = MailcatcherHelper.extractVerificationUrl(mail.plainBody);
    expect(verifyUrl).not.toBeNull();

    const uidMatch = verifyUrl!.match(/uid=(\d+)/);
    if (uidMatch) {
      createdUserIds.push(uidMatch[1]);
    }

    // 5. Verifizierungslink aufrufen
    await page.goto(verifyUrl!);
    await page.waitForLoadState('domcontentloaded');

    // 6. Bestätigungsmeldung verifizieren
    await expect(page.locator('.verify-account-view')).toBeVisible({ timeout: 10000 });
    await expect(page.locator('h2:has-text("Erfolgreich!")')).toBeVisible();
  });

  test('TC-REG-04 [🌐]: Registrierung als Turnierverwalter (turnier_admin) mit Veranstalter & Admin-Prüfung', async ({ page }) => {
    const timestamp = Date.now();
    const testEmail = `testadmin_${timestamp}@example.com`;
    const testPassword = 'Password1234!';
    const testOrganizer = 'TTC Mailcatcher Testclub';

    // 1. Registrierungsseite aufrufen
    await regPage.goto();

    // 2. Als Admin registrieren
    await regPage.register({
      email: testEmail,
      password: testPassword,
      isAdmin: true,
      organizer: testOrganizer
    });

    // 3. Bestätigungsansicht
    await expect(regPage.successView).toBeVisible({ timeout: 10000 });

    // 4. Mail abfangen
    const mail = await MailcatcherHelper.waitForMessage(testEmail, 15000);
    const verifyUrl = MailcatcherHelper.extractVerificationUrl(mail.plainBody);
    expect(verifyUrl).not.toBeNull();

    const uidMatch = verifyUrl!.match(/uid=(\d+)/);
    if (uidMatch) {
      createdUserIds.push(uidMatch[1]);
    }

    // 5. Verifizierungslink aufrufen
    await page.goto(verifyUrl!);
    await page.waitForLoadState('domcontentloaded');

    // 6. Für turnier_admin lautet die Meldung: "Dein Account wird nun vom Administrator geprüft."
    await expect(page.locator('.verify-account-view')).toBeVisible({ timeout: 10000 });
    await expect(page.locator('text=vom Administrator geprüft')).toBeVisible();
  });
});
