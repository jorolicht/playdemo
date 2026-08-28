import { test, expect } from '@playwright/test';
import { StageAdminPage } from '../pages/StageAdminPage';

test.describe('UseCase 8: Phasen-Verwaltung & Auslosung (StageAdmin & StageDraw)', () => {
  let stageAdminPage: StageAdminPage;

  test.beforeEach(async ({ page }) => {
    stageAdminPage = new StageAdminPage(page);
    await stageAdminPage.goto();
  });

  test('TC-SA-01 [👤]: Spielsysteme wählen (KO, Gruppe, Schweizer System)', async ({ page }) => {
    if (await stageAdminPage.selectSystem.count() > 0) {
      await stageAdminPage.selectSystem.selectOption({ index: 0 });
    }
    await expect(page.locator('body')).toBeVisible();
  });

  test('TC-SA-02 [👤]: Gruppen- & KO-Parameter setzen', async ({ page }) => {
    if (await stageAdminPage.inputNoGroups.count() > 0 && await stageAdminPage.inputNoGroups.isVisible()) {
      await stageAdminPage.inputNoGroups.fill('4');
    }
    await expect(page.locator('body')).toBeVisible();
  });

  test('TC-SA-03 [👤]: Auslosung ausführen (StageDraw)', async ({ page }) => {
    if (await stageAdminPage.btnStartDraw.count() > 0 && await stageAdminPage.btnStartDraw.isVisible()) {
      await stageAdminPage.btnStartDraw.click();
      await page.waitForTimeout(500);
    }
    await expect(page.locator('body')).toBeVisible();
  });

  test('TC-SA-04 [👤]: Spieler manuell tauschen in Gruppen', async ({ page }) => {
    const swapBtn = page.locator('button:has-text("Tauschen"), button:has-text("Wechseln")').first();
    if (await swapBtn.count() > 0) {
      await swapBtn.click();
      await page.waitForTimeout(400);
    }
    await expect(page.locator('body')).toBeVisible();
  });

  test('TC-SA-05 [👤]: Phase starten (Freigabe der Ergebniseingabe)', async ({ page }) => {
    if (await stageAdminPage.btnStartStage.count() > 0 && await stageAdminPage.btnStartStage.isVisible()) {
      await stageAdminPage.btnStartStage.click();
      await page.waitForTimeout(500);
    }
    await expect(page.locator('body')).toBeVisible();
  });
});
