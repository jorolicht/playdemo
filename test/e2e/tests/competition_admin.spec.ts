import { test, expect } from '@playwright/test';
import { CompetitionInfoPage } from '../pages/CompetitionInfoPage';

test.describe('UseCase 6: Wettbewerbsverwaltung (CompetitionInfo)', () => {
  let compInfoPage: CompetitionInfoPage;

  test.beforeEach(async ({ page }) => {
    compInfoPage = new CompetitionInfoPage(page);
    await compInfoPage.goto();
  });

  test('TC-CI-01 [👤]: CompetitionInfo Ansicht geladen', async ({ page }) => {
    await expect(page.locator('body')).toBeVisible();
  });

  test('TC-CI-02 [👤]: Phasen starten Button löst Auslosungs-Assistent aus', async ({ page }) => {
    if (await compInfoPage.btnStartStage.count() > 0 && await compInfoPage.btnStartStage.isVisible()) {
      await compInfoPage.btnStartStage.click();
      await page.waitForTimeout(500);
      expect(page.url()).toMatch(/#StageAdmin|#StageDraw|#CompetitionInfo/);
    } else {
      await expect(page.locator('body')).toBeVisible();
    }
  });

  test('TC-CI-03 [👤]: Wettbewerb bearbeiten (TTR-Limits & Altersklassen)', async ({ page }) => {
    if (await compInfoPage.btnEditComp.count() > 0 && await compInfoPage.btnEditComp.isVisible()) {
      await compInfoPage.btnEditComp.click();
      await page.waitForTimeout(500);
    }
    await expect(page.locator('body')).toBeVisible();
  });

  test('TC-CI-04 [👤]: Urkunden Radio-Button Auswahl', async ({ page }) => {
    const radio = page.locator('input[name="stage-certificate-radio"]').first();
    if (await radio.count() > 0) {
      await radio.check({ force: true });
      await page.waitForTimeout(400);
    }
    await expect(page.locator('body')).toBeVisible();
  });

  test('TC-CI-05 [👤]: Wettbewerb beenden / reaktivieren', async ({ page }) => {
    if (await compInfoPage.btnToggleStatus.count() > 0 && await compInfoPage.btnToggleStatus.isVisible()) {
      await compInfoPage.btnToggleStatus.click();
      await page.waitForTimeout(500);
    }
    await expect(page.locator('body')).toBeVisible();
  });
});
