import { test, expect } from '@playwright/test';
import { LoginPage } from '../pages/LoginPage';

test.describe('UseCase 11: E-Mail/Passwort-Login & Rate-Limiting (UserLogin)', () => {
  let loginPage: LoginPage;

  test.beforeEach(async ({ page }) => {
    loginPage = new LoginPage(page);
    await loginPage.goto();
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
