import { test, expect } from '@playwright/test';

/**
 * StageScoreSheet: "Runde drucken (4 pro Seite)" muss alle Schiri-Zettel drucken, nicht nur die erste Seite.
 * Das Plugin-Template setzt html/body auf height:100% + overflow-x:hidden; im Druck wird das aufgehoben.
 */
test.describe('StageScoreSheet: Druck aller Zettel', () => {
  test('TC-PR-06 [👤]: Druck enthält alle Zettel (4 pro Seite), keine leere Seite am Ende', async ({ page }) => {
    await page.goto('/');
    await page.locator('#MainView_BtnDemo').click();
    const compRow = page.locator('tr', { hasText: 'S-Klasse Herren Einzel' }).first();
    await expect(compRow).toBeVisible({ timeout: 15000 });
    await compRow.locator('[id^="TourneyInfo_"][role="button"]').click();
    await page.locator('tr.table-light [role="button"]').first().click();
    await page.locator('[id$="StageScoreSheet"]').first().click();

    const cards = page.locator('#scoresheet-print-area .refereeGrid');
    await expect(cards.first()).toBeAttached({ timeout: 10000 });
    const n = await cards.count();
    expect(n).toBeGreaterThan(4);

    // Button in Hero-Blau
    await expect(page.locator('button.btn-hero-primary', { hasText: /drucken|Print/i }).first()).toBeVisible();

    await page.emulateMedia({ media: 'print' });
    // html/body dürfen im Druck nicht auf die Fensterhöhe begrenzt sein
    const clip = await page.evaluate(() => [document.documentElement, document.body].map(e => getComputedStyle(e).overflowY));
    expect(clip).toEqual(['visible', 'visible']);

    const pdf = await page.pdf({ format: 'A4' });
    const pages = (pdf.toString('latin1').match(/\/Type\s*\/Page[^s]/g) || []).length;
    expect(pages).toBe(Math.ceil(n / 4));
  });
});
