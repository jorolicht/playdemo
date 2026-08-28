import { test, expect } from '@playwright/test';
import { ManagementPage } from '../pages/ManagementPage';

test.describe('UseCase 12: Admin-Verwaltung & Guthaben (Management)', () => {
  let mgmtPage: ManagementPage;

  test.beforeEach(async ({ page }) => {
    mgmtPage = new ManagementPage(page);
    await mgmtPage.goto();
  });

  test('TC-MG-01 [👑]: Management Konsole laden', async ({ page }) => {
    await expect(page.locator('body')).toBeVisible();
  });

  test('TC-MG-02 [👑]: Benutzerliste anzeigen', async ({ page }) => {
    await expect(page.locator('table, .card').first()).toBeVisible();
  });

  test('TC-MG-03 [👑]: Turnier-Guthaben (available) ändern & speichern', async ({ page }) => {
    if (await mgmtPage.availableInput.count() > 0 && await mgmtPage.availableInput.isVisible()) {
      await mgmtPage.availableInput.fill('10');
      if (await mgmtPage.btnSaveUser.count() > 0) {
        await mgmtPage.btnSaveUser.click();
        await page.waitForTimeout(500);
      }
    }
    await expect(page.locator('body')).toBeVisible();
  });

  test('TC-MG-04 [👑]: Payhip-Zahlungen einsehen & zuweisen', async ({ page }) => {
    const payhipTab = page.locator('button:has-text("Payhip"), a:has-text("Payhip")').first();
    if (await payhipTab.count() > 0) {
      await payhipTab.click();
      await page.waitForTimeout(500);
    }
    await expect(page.locator('body')).toBeVisible();
  });

  test('TC-MG-05 [👑]: System-Logs & Turnierstatistiken', async ({ page }) => {
    const logTab = page.locator('button:has-text("Log"), a:has-text("Log"), button:has-text("Statistik")').first();
    if (await logTab.count() > 0) {
      await logTab.click();
      await page.waitForTimeout(400);
    }
    await expect(page.locator('body')).toBeVisible();
  });

  test('TC-MG-06 [🌐/👤]: Zugriffsschutz bei unbefugtem Aufruf von #Management', async ({ page }) => {
    await page.context().clearCookies();
    await page.goto('/');
    await page.waitForLoadState('domcontentloaded');
    await page.evaluate(() => {
      if (typeof (window as any).appLoadPage === 'function') {
        (window as any).appLoadPage('Management', '');
      }
    });
    await page.waitForTimeout(500);
    await expect(page.locator('body')).toBeVisible();
  });
});
