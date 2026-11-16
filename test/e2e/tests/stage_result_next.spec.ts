import { test, expect } from '@playwright/test';
import { MainViewPage } from '../pages/MainViewPage';

/**
 * StageResult: button "START nachfolgender Abschnitt".
 * Demo tourney, competition 1 "S-Klasse Herren Einzel": stage 4 Endrunde (knockout, only one match played).
 */
test.describe('UseCase 9: StageResult – START nachfolgender Abschnitt', () => {

  test('TC-SI-10 [👤]: Button ist ohne eingegebene Ergebnisse deaktiviert', async ({ page }) => {
    const mainView = new MainViewPage(page);
    await mainView.goto();
    await mainView.startDemo();

    // new round robin stage with 4 dummies, drawn and started, no results yet
    await page.locator('tr', { hasText: 'B-Klasse Damen Einzel' }).locator('.comp-link').click();
    await page.locator('button[id*="BtnStartStage"]').click();
    await page.locator('input[id*="DlgStageStart_InputNameId"]').fill('Vorrunde');
    await page.locator('button[id*="DlgStageStart_ApplyId"]').click();
    await expect(page.locator('#DlgStageStart_ModalId')).not.toBeVisible({ timeout: 5000 });
    for (let i = 0; i < 4; i++) await page.locator('#StageAdmin_BtnAddDummy').click();
    await page.locator('#StageAdmin_SelectDrawMode').selectOption('RR');
    await page.locator('#StageAdmin_BtnStartDrawing').click();
    await page.locator('button[id*="BtnStartPlaying"]').click();
    await page.evaluate(() => (window as any).appLoadPage('StageResult', ''));

    const nextBtn = page.locator('#StageResult_NextStageBtn');
    await expect(nextBtn).toHaveText(/START nachfolgender Abschnitt/, { timeout: 5000 });
    await expect(nextBtn).toBeDisabled();
  });

  test('TC-SI-11 [👤]: Unvollständige Ergebnisse zeigen nur einen Hinweisdialog', async ({ page }) => {
    const mainView = new MainViewPage(page);
    await mainView.goto();
    await mainView.startDemo();

    await page.locator('tr', { hasText: 'S-Klasse Herren Einzel' }).locator('.comp-link').click();
    await page.locator('.stage-link', { hasText: 'Endrunde' }).click();
    await page.evaluate(() => (window as any).appLoadPage('StageResult', ''));

    const nextBtn = page.locator('#StageResult_NextStageBtn');
    await expect(nextBtn).toBeEnabled({ timeout: 5000 });
    await nextBtn.click();

    const box = page.locator('.modal.show');
    await expect(box).toContainText('noch nicht alle Ergebnisse', { timeout: 5000 });
    await expect(page.locator('#DlgStageStart_ModalId')).not.toBeVisible();
    await box.locator('button').filter({ hasText: /OK/i }).click();
    await expect(box).toHaveCount(0);
    await expect(page.locator('.stage-tabs .nav-link.active[href*="StageResult"]')).toBeVisible();
  });
});
