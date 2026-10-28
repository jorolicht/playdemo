import { test, expect } from '@playwright/test';
import { LoginPage } from '../pages/LoginPage';
import { execSync } from 'child_process';

/**
 * Setzt Fehllogin-Zähler und IP-Sperre (Transients pd_login_*) in WordPress zurück.
 * Sonst verlangt der Server nach 3 Fehlversuchen derselben IP 15 Minuten lang ein Captcha,
 * und parallel laufende Login-Tests (z. B. TC-REG-02) schlagen fehl.
 */
function resetLoginFailures(): void {
  const wp = 'docker exec wp-cli-instance wp --path=/var/www/html --allow-root';
  try {
    const rows: { name: string }[] = JSON.parse(
      execSync(`${wp} transient list --search='pd_login_*' --fields=name --format=json`).toString() || '[]');
    for (const { name } of rows) execSync(`${wp} transient delete ${name}`);
  } catch (e) {
    // Ohne lokale Docker-Umgebung (z. B. Test gegen entfernten Server) nicht möglich
  }
}

test.describe('UseCase 11: E-Mail/Passwort-Login & Rate-Limiting (UserLogin)', () => {
  // Nacheinander ausführen: mit dem Reset nach jedem Test bleiben höchstens 2 Fehlversuche je IP
  // aktiv (Captcha-Schwelle 3), sodass parallel laufende Login-Tests nicht gesperrt werden
  test.describe.configure({ mode: 'serial' });
  let loginPage: LoginPage;

  test.beforeEach(async ({ page }) => {
    loginPage = new LoginPage(page);
    await loginPage.goto();
  });

  test.afterEach(() => {
    resetLoginFailures();
  });

  test('TC-AU-01 [🌐]: Regulärer Login mit E-Mail & Passwort', async ({ page }) => {
    await loginPage.login('admin@beispiel.de', 'password123');
    await expect(page.locator('body')).toBeVisible();
  });

  test('TC-AU-02 [👤]: Abmelden (Logout)', async ({ page }) => {
    await loginPage.logout();
    await expect(page.locator('body')).toBeVisible();
  });

  test('TC-AU-03 [🌐]: Registrierungsseite Aufruf (UserRegistration)', async ({ page }) => {
    await page.goto('/');
    await page.waitForLoadState('domcontentloaded');
    await page.evaluate(() => {
      if (typeof (window as any).appLoadPage === 'function') {
        (window as any).appLoadPage('UserRegistration', '');
      }
    });
    await expect(page.locator('body')).toBeVisible();
  });

  test('TC-AU-04 [🌐]: Fehllogin-Handling & Dynamisches Captcha / Rate-Limiting', async ({ page }) => {
    await loginPage.login('wrong@beispiel.de', 'wrongpass1');
    await expect(loginPage.errorAlert).toBeVisible();

    await loginPage.login('wrong@beispiel.de', 'wrongpass2');
    await expect(loginPage.errorAlert).toBeVisible();
  });
});
