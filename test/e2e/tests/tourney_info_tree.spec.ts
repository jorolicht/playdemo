import { test, expect } from '@playwright/test';

/**
 * TourneyInfo: Wettbewerbsliste als Baum (Tree Node Glyphs).
 * Wettbewerb mit Stages aufklappen -> Stages, Stage aufklappen -> Stage-Reiter, Reiter öffnet die Seite.
 */
test.describe('TourneyInfo: Baum Wettbewerb > Stage > Reiter', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    const btnDemo = page.locator('#MainView_BtnDemo');
    await expect(btnDemo).toBeVisible({ timeout: 15000 });
    await btnDemo.click();
    await expect(page.locator('td', { hasText: 'S-Klasse Herren Einzel' }).first()).toBeVisible({ timeout: 15000 });
  });

  test('TC-TI-TREE-01 [👤]: Stages und Reiter auf- und zuklappen, Reiter öffnet Stage-Seite', async ({ page }) => {
    const compRow = page.locator('tr', { hasText: 'S-Klasse Herren Einzel' }).first();
    const compTng = compRow.locator('[id^="TourneyInfo_"][role="button"]');
    await expect(compTng.locator('i')).toHaveClass(/bi-caret-right-fill/);

    // Wettbewerb aufklappen: Stage-Zeilen mit eigenem TNG erscheinen
    await compTng.click();
    const stageTngs = page.locator('tr.table-light [role="button"]');
    await expect(stageTngs.first()).toBeVisible();
    await expect(page.locator('tr', { hasText: 'S-Klasse Herren Einzel' }).first().locator('[role="button"] i')).toHaveClass(/bi-caret-down-fill/);

    // Stage aufklappen: Reiter erscheinen
    await stageTngs.first().click();
    // Reiter-IDs enden auf den Seitennamen (sprachunabhängig)
    for (const pg of ['StageAdmin', 'StageDraw', 'StageInput', 'StageResult', 'StageScoreSheet']) {
      await expect(page.locator(`tr.table-light button[id$="-${pg}"]`).first()).toBeVisible();
    }

    // Reiter "Ergebnisse" öffnet die Stage-Seite mit aktivem Reiter
    await page.locator('tr.table-light button[id$="-StageResult"]').first().click();
    await expect(page.locator('.nav-tabs .nav-link.active[href*="StageResult"]')).toBeVisible({ timeout: 10000 });
  });

  test('TC-TI-TREE-02 [👤]: Wettbewerb wieder zuklappen blendet Stages aus', async ({ page }) => {
    const compTng = page.locator('tr', { hasText: 'S-Klasse Herren Einzel' }).first().locator('[role="button"]');
    await compTng.click();
    await expect(page.locator('tr.table-light').first()).toBeVisible();
    await page.locator('tr', { hasText: 'S-Klasse Herren Einzel' }).first().locator('[role="button"]').click();
    await expect(page.locator('tr.table-light')).toHaveCount(0);
  });
});
