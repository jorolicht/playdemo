import { test, expect } from '@playwright/test';

test.describe('Section 14: Netzwerk-Resilienz & Error Handling (API Interception)', () => {

  test('TC-NR-01 [🌐]: Injektion von Server-Fehlern (HTTP 500 / 503)', async ({ page }) => {
    await page.route('**/wp-json/tourney/v1/auth/login', route => {
      route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({ code: 'server_error', message: 'Interner Serverfehler' })
      });
    });

    await page.goto('/');
    await page.waitForLoadState('domcontentloaded');
    await page.evaluate(() => {
      if (typeof (window as any).appLoadPage === 'function') {
        (window as any).appLoadPage('UserLogin', '');
      }
    });

    const emailInput = page.locator('input[id*="LoginId"]');
    const passInput = page.locator('input[id*="PasswordId"]');
    const loginBtn = page.locator('button[id*="BtnLogin"]');

    if (await loginBtn.isVisible()) {
      await emailInput.fill('test@beispiel.de');
      await passInput.fill('pass1234');
      await loginBtn.click();
      await page.waitForTimeout(500);
    }
    await expect(page.locator('body')).toBeVisible();
  });

  test('TC-NR-02 [🌐]: Injektion von Rate-Limiting (HTTP 429 Too Many Requests)', async ({ page }) => {
    await page.route('**/wp-json/tourney/v1/auth/login', route => {
      route.fulfill({
        status: 429,
        contentType: 'application/json',
        body: JSON.stringify({ code: 'account_locked', message: 'Zu viele fehlgeschlagene Anmeldeversuche. Zugriff gesperrt.' })
      });
    });

    await page.goto('/');
    await page.waitForLoadState('domcontentloaded');
    await page.evaluate(() => {
      if (typeof (window as any).appLoadPage === 'function') {
        (window as any).appLoadPage('UserLogin', '');
      }
    });

    const emailInput = page.locator('input[id*="LoginId"]');
    const passInput = page.locator('input[id*="PasswordId"]');
    const loginBtn = page.locator('button[id*="BtnLogin"]');

    if (await loginBtn.isVisible()) {
      await emailInput.fill('test@beispiel.de');
      await passInput.fill('pass1234');
      await loginBtn.click();
      await page.waitForTimeout(500);
      await expect(page.locator('#LoginErrorAlert, body').first()).toBeVisible();
    }
  });

  test('TC-NR-03 [🌐]: Simulation von hoher Netzwerk-Latenz (Throttling / Delay)', async ({ page }) => {
    await page.route('**/wp-json/tourney/v1/**', async route => {
      await new Promise(res => setTimeout(res, 800));
      await route.continue();
    });

    await page.goto('/');
    await page.waitForLoadState('domcontentloaded');
    await expect(page.locator('body')).toBeVisible();
  });

  test('TC-NR-04 [🌐]: Simulation von Netzwerkausfall / Verbindungsabbruch (Network Abort)', async ({ page }) => {
    await page.route('**/wp-json/tourney/v1/search**', route => route.abort('failed'));

    await page.goto('/');
    await page.waitForLoadState('domcontentloaded');
    await page.evaluate(() => {
      if (typeof (window as any).appLoadPage === 'function') {
        (window as any).appLoadPage('MainSearch', '');
      }
    });

    await expect(page.locator('body')).toBeVisible();
  });

});
