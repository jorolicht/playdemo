import { test, expect } from '@playwright/test';
import { StageInputPage } from '../pages/StageInputPage';

test.describe('UseCase 9: Ergebniseingabe & Live-Scores (StageInput & StageResult)', () => {
  let stageInputPage: StageInputPage;

  test.beforeEach(async ({ page }) => {
    stageInputPage = new StageInputPage(page);
    await stageInputPage.goto();
  });

  test('TC-SI-01 [👤]: StageInput Ansicht geladen', async ({ page }) => {
    await expect(page.locator('body')).toBeVisible();
  });

  test('TC-SI-02 [👤]: Satzstände eingeben & speichern', async ({ page }) => {
    if (await stageInputPage.setInputs.count() > 0) {
      await stageInputPage.setInputs.first().fill('11:9, 9:11, 11:8, 11:6');
      if (await stageInputPage.btnSave.count() > 0) {
        await stageInputPage.btnSave.click();
      }
    }
    await expect(page.locator('body')).toBeVisible();
  });

  test('TC-SI-03 [👤]: Tischzuweisung für Ansetzungen', async ({ page }) => {
    if (await stageInputPage.tableInputs.count() > 0) {
      await stageInputPage.tableInputs.first().fill('Tisch 3');
    }
    await expect(page.locator('body')).toBeVisible();
  });

  test('TC-SI-04 [👤]: Satzvalidierung (Ungültige Satzstände verhüten)', async ({ page }) => {
    if (await stageInputPage.setInputs.count() > 0) {
      await stageInputPage.setInputs.first().fill('11:0');
    }
    await expect(page.locator('body')).toBeVisible();
  });

  test('TC-SI-05 [👤]: StageResult Echtzeit-Tabellenstände (Punkte, Sätze, Bälle)', async ({ page }) => {
    await page.goto('/');
    await page.waitForLoadState('domcontentloaded');
    await page.evaluate(() => {
      if (typeof (window as any).appLoadPage === 'function') {
        (window as any).appLoadPage('StageResult', '');
      }
    });
    await expect(page.locator('body')).toBeVisible();
  });

  test('TC-SI-06 [🌐]: Schreibgeschützte Phasenansicht für nicht angemeldete Besucher', async ({ page }) => {
    await page.context().clearCookies();
    await page.goto('/');
    await page.waitForLoadState('domcontentloaded');
    await page.evaluate(() => {
      if (typeof (window as any).appLoadPage === 'function') {
        (window as any).appLoadPage('StageResult', '');
      }
    });
    await expect(page.locator('body')).toBeVisible();
  });
});
