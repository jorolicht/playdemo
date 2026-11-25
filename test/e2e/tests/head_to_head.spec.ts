import { test, expect, Page } from '@playwright/test';
import { MainViewPage } from '../pages/MainViewPage';

/**
 * UseCase 36: Direkter Vergleich bei Punktgleichheit (Wertungssystem tt_003, Kriterium headToHead).
 * Neue Wettbewerbe erhalten die neueste Version (tt_003), bestehende behalten ihre (Demo: ohne Eintrag = tt_001).
 * Die Tabellenberechnung selbst prüfen die Unit-Tests (shared/src/test/scala/shared/format/HeadToHeadSuite.scala).
 */
test.describe('UseCase 36: Direkter Vergleich – Version des Wertungssystems', () => {

  test.beforeEach(async ({ page }) => {
    const mainView = new MainViewPage(page);
    await mainView.goto();
    await mainView.startDemo();
  });

  /** Startet im geöffneten Wettbewerb einen Abschnitt und liefert die Auswahl des Wertungssystems. */
  async function startStage(page: Page) {
    await page.locator('button[id*="BtnStartStage"]').click();
    await page.locator('input[id*="DlgStageStart_InputNameId"]').fill('Vorrunde');
    await page.locator('button[id*="DlgStageStart_ApplyId"]').click();
    await expect(page.locator('#DlgStageStart_ModalId')).not.toBeVisible({ timeout: 5000 });
    await page.evaluate(() => (window as any).appLoadPage('StageAdmin', ''));
    const sel = page.locator('select[id$="SelScoring"]');
    await expect(sel).toBeVisible({ timeout: 10000 });
    return sel;
  }

  test('TC-H2H-01 [👤]: Neuer Wettbewerb erhält Version 3 (mit direktem Vergleich)', async ({ page }) => {
    await page.locator('[id^="TourneyInfo_BtnAddComp"]').click();
    await page.locator('#DlgCompetition_CompNameId').fill('Direkter Vergleich Test');
    await page.locator('#DlgCompetition_CompTypId').selectOption('SINGLE');
    await page.locator('#DlgCompetition_ApplyId').click();
    await expect(page.locator('#DlgCompetition_ModalId')).not.toBeVisible({ timeout: 5000 });
    await page.locator('tr', { hasText: 'Direkter Vergleich Test' }).locator('.comp-link').click();
    const sel = await startStage(page);
    await expect(sel).toHaveValue('tt_003');
    await expect(sel.locator('option:checked')).toContainText('Version 3');
  });

  test('TC-H2H-02 [👤]: Bestehender Wettbewerb behält seine Version (Demo: tt_001)', async ({ page }) => {
    await page.locator('tr', { hasText: 'B-Klasse Damen Einzel' }).locator('.comp-link').click();
    const sel = await startStage(page);
    await expect(sel).toHaveValue('tt_001');
  });
});
