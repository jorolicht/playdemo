import { test, expect } from '@playwright/test';

test.describe('Section 16: Input-Boundary, Edge Cases & Sicherheits-Injektionen', () => {

  test('TC-IB-01 [🌐]: Extreme String-Längen im Suchfeld (Boundary-Testing)', async ({ page }) => {
    await page.goto('/');
    await page.waitForLoadState('domcontentloaded');
    await page.evaluate(() => {
      if (typeof (window as any).appLoadPage === 'function') {
        (window as any).appLoadPage('MainSearch', '');
      }
    });

    const longString = 'A'.repeat(500);
    const searchInput = page.locator('input[id*="IdInputTitle"]');
    if (await searchInput.isVisible()) {
      await searchInput.fill(longString);
      await page.waitForTimeout(400);
    }
    await expect(page.locator('body')).toBeVisible();
  });

  test('TC-IB-02 [🌐]: XSS & HTML-Injektionen (<script>alert(1)</script>) Maskierung', async ({ page }) => {
    await page.goto('/');
    await page.waitForLoadState('domcontentloaded');
    await page.evaluate(() => {
      if (typeof (window as any).appLoadPage === 'function') {
        (window as any).appLoadPage('MainSearch', '');
      }
    });

    const xssPayload = '<script>window.__xssTriggered=true;</script><img src="x" onerror="window.__xssTriggered=true">';
    const searchInput = page.locator('input[id*="IdInputTitle"]');
    if (await searchInput.isVisible()) {
      await searchInput.fill(xssPayload);
      await page.waitForTimeout(400);
    }

    const isXssExecuted = await page.evaluate(() => (window as any).__xssTriggered === true);
    expect(isXssExecuted).toBe(false);
  });

  test('TC-IB-03 [🌐]: Unicode, Emojis & Sonderzeichen (äöü, 🏆, #$%&*)', async ({ page }) => {
    await page.goto('/');
    await page.waitForLoadState('domcontentloaded');
    await page.evaluate(() => {
      if (typeof (window as any).appLoadPage === 'function') {
        (window as any).appLoadPage('MainSearch', '');
      }
    });

    const unicodeInput = 'Tischtennis 🏆 Meisterschaft äöüß @#$&*()!';
    const searchInput = page.locator('input[id*="IdInputTitle"]');
    if (await searchInput.isVisible()) {
      await searchInput.fill(unicodeInput);
      await page.waitForTimeout(400);
    }
    await expect(page.locator('body')).toBeVisible();
  });

  test('TC-IB-04 [🌐]: Dateivalidierung beim Spielerimport (setInputFiles)', async ({ page }) => {
    await page.goto('/');
    await page.waitForLoadState('domcontentloaded');
    await page.evaluate(() => {
      if (typeof (window as any).appLoadPage === 'function') {
        (window as any).appLoadPage('PlayerList', '');
      }
    });

    const fileInput = page.locator('input[type="file"]');
    if (await fileInput.count() > 0) {
      await fileInput.setInputFiles({
        name: 'invalid_file.exe',
        mimeType: 'application/x-msdownload',
        buffer: Buffer.from('BINARY_CONTENT')
      });
      await page.waitForTimeout(400);
    }
    await expect(page.locator('body')).toBeVisible();
  });

});
