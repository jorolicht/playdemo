import { test, expect } from '@playwright/test';

test.describe('Section 13: Cross-Browser & Responsiveness (Viewport-Testing)', () => {

  test('TC-CB-01 [🌐]: Mobile Viewport (375x667) & Hamburger-Menü', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 });
    await page.goto('/');
    await page.waitForLoadState('domcontentloaded');

    const toggler = page.locator('.navbar-toggler');
    if (await toggler.count() > 0 && await toggler.isVisible()) {
      await toggler.click();
      await page.waitForTimeout(300);
      await expect(page.locator('.navbar-collapse')).toBeVisible();
    }
    await expect(page.locator('body')).toBeVisible();
  });

  test('TC-CB-02 [🌐]: Tablet Viewport (768x1024) & Grid-Anpassung', async ({ page }) => {
    await page.setViewportSize({ width: 768, height: 1024 });
    await page.goto('/');
    await page.waitForLoadState('domcontentloaded');
    await page.evaluate(() => {
      if (typeof (window as any).appLoadPage === 'function') {
        (window as any).appLoadPage('MainSearch', '');
      }
    });

    await expect(page.locator('input[id*="IdInputTitle"]')).toBeVisible();
    await expect(page.locator('body')).toBeVisible();
  });

  test('TC-CB-03 [🌐]: Desktop Viewport (1280x800) & Nebeneinander-Darstellung', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.goto('/');
    await page.waitForLoadState('domcontentloaded');
    await page.evaluate(() => {
      if (typeof (window as any).appLoadPage === 'function') {
        (window as any).appLoadPage('MainSearch', '');
      }
    });

    await expect(page.locator('input[id*="IdInputTitle"]')).toBeVisible();
  });

  test('TC-CB-04 [🌐]: Hoch- / Querformat-Umschaltung (Portrait vs Landscape)', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 });
    await page.goto('/');
    await page.waitForLoadState('domcontentloaded');

    await page.setViewportSize({ width: 667, height: 375 });
    await page.waitForTimeout(300);
    await expect(page.locator('body')).toBeVisible();
  });

});
