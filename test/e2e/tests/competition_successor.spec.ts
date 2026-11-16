import { test, expect } from '@playwright/test';
import { MainViewPage } from '../pages/MainViewPage';

/**
 * CompetitionInfo: button "Nachfolger" in the stage list.
 * Demo tourney, competition 1 "S-Klasse Herren Einzel": stage 3 Vorrunde (finished), stage 4 Endrunde (one match played).
 */
test.describe('UseCase 6: CompetitionInfo – Nachfolger eines Abschnitts', () => {

  test.beforeEach(async ({ page }) => {
    const mainView = new MainViewPage(page);
    await mainView.goto();
    await mainView.startDemo();
  });

  test('TC-CI-10 [👤]: Nachfolger ist ohne Ergebnisse deaktiviert', async ({ page }) => {
    await page.locator('tr', { hasText: 'B-Klasse Damen Einzel' }).locator('.comp-link').click();
    await page.locator('button[id*="BtnStartStage"]').click();
    await page.locator('input[id*="DlgStageStart_InputNameId"]').fill('Vorrunde');
    await page.locator('button[id*="DlgStageStart_ApplyId"]').click();
    await expect(page.locator('#DlgStageStart_ModalId')).not.toBeVisible({ timeout: 5000 });
    await page.evaluate(() => (window as any).appLoadPage('CompetitionInfo', ''));
    const btn = page.locator('button[id^="CompetitionInfo_BtnSuccessor-"]');
    await expect(btn).toHaveCount(1);
    await expect(btn).toContainText('Nachfolger');
    await expect(btn).toBeDisabled();
  });

  test('TC-CI-11 [👤]: Unvollständige Ergebnisse – Rückfrage ABBRUCH/WEITER', async ({ page }) => {
    await page.locator('tr', { hasText: 'S-Klasse Herren Einzel' }).locator('.comp-link').click();
    const btn = page.locator('#CompetitionInfo_BtnSuccessor-4');
    await expect(btn).toBeEnabled({ timeout: 5000 });

    const box = page.locator('.modal.show');
    await btn.click();
    await expect(box).toContainText('noch nicht alle Ergebnisse', { timeout: 5000 });
    await box.locator('button', { hasText: 'ABBRUCH' }).click();
    await expect(page.locator('.modal.show')).toHaveCount(0);
    await expect(page.locator('#DlgStageStart_ModalId')).not.toBeVisible();

    await btn.click();
    await page.locator('.modal.show button', { hasText: 'WEITER' }).click();
    await expect(page.locator('#DlgStageStart_ModalId')).toBeVisible({ timeout: 5000 });
    await expect(page.locator('select[id*="DlgStageStart_InputPrefId"]')).toHaveValue('4');
  });

  test('TC-CI-12 [👤]: Vollständige Ergebnisse – direkt zum Dialog', async ({ page }) => {
    await page.locator('tr', { hasText: 'S-Klasse Herren Einzel' }).locator('.comp-link').click();
    await page.locator('#CompetitionInfo_BtnSuccessor-3').click();
    await expect(page.locator('#DlgStageStart_ModalId')).toBeVisible({ timeout: 5000 });
    await expect(page.locator('select[id*="DlgStageStart_InputPrefId"]')).toHaveValue('3');
    await page.locator('input[id*="DlgStageStart_InputNameId"]').fill('Platzierungsrunde');
    await page.locator('button[id*="DlgStageStart_ApplyId"]').click();
    await expect(page.locator('h2', { hasText: 'Platzierungsrunde' })).toBeVisible({ timeout: 5000 });
  });
});
