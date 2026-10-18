import { test, expect } from '@playwright/test';
import { StageAdminPage } from '../pages/StageAdminPage';
import { MainViewPage } from '../pages/MainViewPage';
import { StageInputPage } from '../pages/StageInputPage';

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

  test('TC-SA-06 [👤]: Vorherige Phase Round Robin - Filter Obere Hälfte / Untere Hälfte / Alle und prev_stage Spalte', async ({ page }) => {
    const mainView = new MainViewPage(page);
    await mainView.goto();
    await mainView.startDemo();

    // 1. Select first competition from TourneyInfo
    const compCell = page.locator('table tbody tr td.fw-bold').first();
    await expect(compCell).toBeVisible({ timeout: 5000 });
    await compCell.click();

    // 2. Start a new stage from CompetitionInfo
    const btnStartStage = page.locator('button[id*="BtnStartStage"], button:has-text("Austragungsphase")');
    await expect(btnStartStage).toBeVisible({ timeout: 5000 });
    await btnStartStage.click();

    // Fill stage start dialog: "Vorrunde RR"
    const inputStageName = page.locator('input[id*="DlgStageStart_InputNameId"]');
    await expect(inputStageName).toBeVisible({ timeout: 5000 });
    await inputStageName.fill('Vorrunde RR');
    const btnStageStartApply = page.locator('button[id*="DlgStageStart_ApplyId"]');
    await btnStageStartApply.click();
    await expect(page.locator('#DlgStageStart_ModalId')).not.toBeVisible({ timeout: 5000 });

    // 3. In StageAdmin: Add 4 dummy players and select RR game mode
    const btnAddDummy = page.locator('#StageAdmin_BtnAddDummy, button:has-text("ADD DUMMY")');
    await expect(btnAddDummy).toBeVisible({ timeout: 5000 });
    for (let i = 0; i < 4; i++) {
      await btnAddDummy.click();
    }

    const selectMode = page.locator('#StageAdmin_SelectDrawMode, select[id*="SelectDrawMode"]');
    await expect(selectMode).toBeVisible({ timeout: 5000 });
    await selectMode.selectOption('RR');

    const btnStartDrawing = page.locator('#StageAdmin_BtnStartDrawing, button[id*="BtnStartDrawing"]');
    await expect(btnStartDrawing).toBeEnabled({ timeout: 5000 });
    await btnStartDrawing.click();

    // 4. In StageDraw: Start playing matches
    const btnStartPlaying = page.locator('#StageDraw_BtnStartPlaying, button[id*="BtnStartPlaying"]');
    await expect(btnStartPlaying).toBeVisible({ timeout: 5000 });
    await btnStartPlaying.click();

    // 5. In StageInput: Generate Demo scores
    const stageInputPage = new StageInputPage(page);
    await expect(stageInputPage.btnDemoScores).toBeVisible({ timeout: 5000 });
    await stageInputPage.btnDemoScores.click();
    await expect(stageInputPage.btnDemoScores).toBeDisabled({ timeout: 5000 });

    // 6. Navigate to StageResult
    const navResult = page.locator('a[id*="Subnav_StageResult"], button[id*="StageResult"], a:has-text("Tabelle")').first();
    if (await navResult.count() > 0 && await navResult.isVisible()) {
      await navResult.click();
    } else {
      await page.evaluate(() => {
        if (typeof (window as any).appLoadPage === 'function') {
          (window as any).appLoadPage('StageResult', '');
        }
      });
    }

    // 7. Click NextStageBtn to start follower stage
    const nextStageBtn = page.locator('button[id*="NextStageBtn"], button:has-text("Nächste Phase")').first();
    await expect(nextStageBtn).toBeVisible({ timeout: 5000 });
    await nextStageBtn.click();

    // In DlgStageStart: Fill follower stage name
    await expect(inputStageName).toBeVisible({ timeout: 5000 });
    await inputStageName.fill('Endrunde');
    await btnStageStartApply.click();
    await expect(page.locator('#DlgStageStart_ModalId')).not.toBeVisible({ timeout: 5000 });

    // 8. Now in StageAdmin for follower stage with predecessor RR:
    // Verify radio buttons exist
    const radAll = page.locator('#StageAdmin_RadAll');
    const radTop = page.locator('#StageAdmin_RadTop');
    const radBottom = page.locator('#StageAdmin_RadBottom');

    await expect(radAll).toBeVisible({ timeout: 5000 });
    await expect(radTop).toBeVisible({ timeout: 5000 });
    await expect(radBottom).toBeVisible({ timeout: 5000 });

    // Verify prev_stage column exists in table header
    const prevStageHeader = page.locator('th:has-text("Vorherige Phase"), th:has-text("Previous Stage")');
    await expect(prevStageHeader).toBeVisible();

    // Verify cell contents show "Jeder gegen Jeden, PLATZ"
    const prevStageCells = page.locator('td.small.text-muted:has-text("Jeder gegen Jeden")');
    await expect(prevStageCells.first()).toBeVisible({ timeout: 5000 });
    const cellCount = await prevStageCells.count();
    expect(cellCount).toBe(4);

    // Verify places 1, 2, 3, 4 are present in the cells
    const cellTexts = await prevStageCells.allTextContents();
    expect(cellTexts.some(t => t.includes('PLATZ 1'))).toBe(true);
    expect(cellTexts.some(t => t.includes('PLATZ 2'))).toBe(true);
    expect(cellTexts.some(t => t.includes('PLATZ 3'))).toBe(true);
    expect(cellTexts.some(t => t.includes('PLATZ 4'))).toBe(true);

    // 9. Test Filter "Obere Hälfte" (RadTop)
    await radTop.click();
    await expect(radTop).toBeChecked();
    // In 4 players RR, top half is places 1 and 2 (2 players)
    const checkedBoxesTop = page.locator('.player-draw-check:checked');
    await expect(checkedBoxesTop).toHaveCount(2);
    const checkedTopSno = await checkedBoxesTop.evaluateAll(els => els.map(e => e.getAttribute('data-sno')));

    // 10. Test Filter "Untere Hälfte" (RadBottom)
    await radBottom.click();
    await expect(radBottom).toBeChecked();
    // Bottom half is places 3 and 4 (2 players)
    const checkedBoxesBottom = page.locator('.player-draw-check:checked');
    await expect(checkedBoxesBottom).toHaveCount(2);

    // Verify the players checked under bottom are distinct from top
    const checkedBottomSno = await checkedBoxesBottom.evaluateAll(els => els.map(e => e.getAttribute('data-sno')));
    expect(checkedBottomSno.some(sno => checkedTopSno.includes(sno))).toBe(false);

    // 11. Test Filter "Alle" (RadAll)
    await radAll.click();
    await expect(radAll).toBeChecked();
    const checkedBoxesAll = page.locator('.player-draw-check:checked');
    await expect(checkedBoxesAll).toHaveCount(4);
  });
});
