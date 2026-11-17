import { test, expect, Page } from '@playwright/test';

/**
 * StageDraw: Änderungen an der Auslosung (Spieler tauschen, Setzen, Start) nur im Status Auslosung.
 * Demo-Turnier, "S-Klasse Herren Einzel": Vorrunde (3, beendet, Jeder-gegen-Jeden), Endrunde (4, Spiele laufen, K.-o.).
 */
async function openDraw(page: Page, stageIndex: number) {
  await page.goto('/');
  await page.locator('#MainView_BtnDemo').click();
  const compRow = page.locator('tr', { hasText: 'S-Klasse Herren Einzel' }).first();
  await expect(compRow).toBeVisible({ timeout: 15000 });
  await compRow.locator('[role="button"]').click();
  await page.locator('tr.table-light [role="button"]').nth(stageIndex).click();
  await page.locator('tr.table-light button[id$="-StageDraw"]').click();
}

test.describe('StageDraw: Sperre nach der Auslosung', () => {
  test('TC-SD-10 [👤]: Spiele laufen (K.-o.) – Hinweis, kein Setzen, Klick tauscht nicht', async ({ page }) => {
    await openDraw(page, 1);
    await expect(page.locator('.stage-content .alert-info', { hasText: 'Auslosung ist abgeschlossen' })).toBeVisible({ timeout: 10000 });
    await expect(page.locator('[id^="StageDraw_BtnSetzen"]')).toHaveCount(0);
    await expect(page.locator('.draw-pos-input')).toHaveCount(0);
    await expect(page.locator('#StageDraw_BtnStartPlaying')).toBeDisabled();
    const cells = page.locator('td[id^="StageDraw_PlayerItem"]');
    await cells.nth(0).click();
    await cells.nth(1).click();
    await expect(page.locator('td[id^="StageDraw_PlayerItem"].bg-warning')).toHaveCount(0);
    await expect(page.locator('.modal.show')).toHaveCount(0);
  });

  test('TC-SD-11 [👤]: Abschnitt beendet (Jeder-gegen-Jeden) – Klick auf Spieler tauscht nicht', async ({ page }) => {
    await openDraw(page, 0);
    await expect(page.locator('.stage-content .alert-info', { hasText: 'Auslosung ist abgeschlossen' })).toBeVisible({ timeout: 10000 });
    const items = page.locator('li[id^="StageDraw_PlayerItem"]');
    await items.nth(0).click();
    await items.nth(1).click();
    await expect(page.locator('li[id^="StageDraw_PlayerItem"].bg-warning')).toHaveCount(0);
    await expect(page.locator('.modal.show')).toHaveCount(0);
  });

  test('TC-SD-12 [👤]: Status Auslosung – Tauschen weiterhin möglich, kein Sperrhinweis', async ({ page }) => {
    await page.goto('/');
    await page.locator('#MainView_BtnDemo').click();
    await page.locator('tr', { hasText: 'B-Klasse Damen Einzel' }).locator('.comp-link').click();
    await page.locator('button[id*="BtnStartStage"]').click();
    await page.locator('input[id*="DlgStageStart_InputNameId"]').fill('Vorrunde');
    await page.locator('button[id*="DlgStageStart_ApplyId"]').click();
    await expect(page.locator('#DlgStageStart_ModalId')).not.toBeVisible({ timeout: 5000 });
    const btnAddDummy = page.locator('#StageAdmin_BtnAddDummy');
    for (let i = 0; i < 4; i++) await btnAddDummy.click();
    await page.locator('#StageAdmin_SelectDrawMode').selectOption('RR');
    await page.locator('#StageAdmin_BtnStartDrawing').click();
    await expect(page.locator('#StageDraw_BtnStartPlaying')).toBeEnabled({ timeout: 5000 });
    // TC-SD-13: Button "Start <Stagename>" mit Pfeil ⤳ in btn-mv-tourney
    await expect(page.locator('#StageDraw_BtnStartPlaying')).toHaveText(/⤳\s*Start Vorrunde/);
    await expect(page.locator('#StageDraw_BtnStartPlaying')).toHaveClass(/btn-mv-tourney/);
    await expect(page.locator('.stage-content .alert-info', { hasText: 'Auslosung ist abgeschlossen' })).toHaveCount(0);
    const items = page.locator('li[id^="StageDraw_PlayerItem"]');
    await items.nth(0).click();
    await items.nth(1).click();
    await expect(page.locator('.modal.show')).toContainText('vertauschen', { timeout: 5000 });
  });
});
