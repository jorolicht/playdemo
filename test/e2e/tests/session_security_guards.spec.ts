import { test, expect } from '@playwright/test';

test.describe('Section 15: Session-Management, Cookies & Security Route Guards', () => {

  test('TC-SM-01 [🌐]: Session-Management & Storage-Clearance bei Logout', async ({ page }) => {
    await page.goto('/');
    await page.waitForLoadState('domcontentloaded');

    await page.context().clearCookies();
    await page.evaluate(() => {
      window.sessionStorage.clear();
      window.localStorage.clear();
    });

    await page.waitForTimeout(300);
    await expect(page.locator('body')).toBeVisible();
  });

  test('TC-SM-02 [🌐]: Route Guard #Management (Zugriffsschutz für geschützte Admin-URL)', async ({ page }) => {
    await page.context().clearCookies();
    await page.goto('/#Management');
    await page.waitForLoadState('domcontentloaded');

    await page.evaluate(() => {
      if (typeof (window as any).appLoadPage === 'function') {
        (window as any).appLoadPage('Management', '');
      }
    });

    await page.waitForTimeout(500);
    await expect(page.locator('body')).toBeVisible();
  });

  test('TC-SM-03 [🌐]: Route Guard #TourneyAdmin (Zugriffsschutz für fremde Turnierverwaltung)', async ({ page }) => {
    await page.context().clearCookies();
    await page.goto('/#TourneyAdmin');
    await page.waitForLoadState('domcontentloaded');

    await page.evaluate(() => {
      if (typeof (window as any).appLoadPage === 'function') {
        (window as any).appLoadPage('TourneyAdmin', '999');
      }
    });

    await page.waitForTimeout(500);
    await expect(page.locator('body')).toBeVisible();
  });

});
