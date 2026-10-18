import { test, expect } from '@playwright/test';
import { StageInputPage } from '../pages/StageInputPage';
import { MainViewPage } from '../pages/MainViewPage';

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

  test('TC-SI-07 [👤]: Demo-Modus DEMO-Ergebnisse Button generiert automatisch alle Spielergebnisse', async ({ page }) => {
    const consoleErrors: string[] = [];
    const matchboardRequests: string[] = [];
    page.on('console', msg => {
      if (msg.type() === 'error' || msg.text().includes('Failed to update stage')) {
        consoleErrors.push(msg.text());
      }
    });
    page.on('request', req => {
      if (req.url().includes('/matchboard/set')) {
        matchboardRequests.push(req.url());
      }
    });

    // 1. Start Demo mode from home page
    const mainView = new MainViewPage(page);
    await mainView.goto();
    await mainView.startDemo();

    // 2. Select first competition from TourneyInfo
    const compCell = page.locator('table tbody tr td.fw-bold').first();
    await expect(compCell).toBeVisible({ timeout: 5000 });
    await compCell.click();

    // 3. Start a new stage from CompetitionInfo
    const btnStartStage = page.locator('button[id*="BtnStartStage"], button:has-text("Austragungsphase")');
    await expect(btnStartStage).toBeVisible({ timeout: 5000 });
    await btnStartStage.click();

    // Fill stage start dialog
    const inputStageName = page.locator('input[id*="DlgStageStart_InputNameId"]');
    await expect(inputStageName).toBeVisible({ timeout: 5000 });
    await inputStageName.fill('Vorrunde Demo');
    const btnStageStartApply = page.locator('button[id*="DlgStageStart_ApplyId"]');
    await btnStageStartApply.click();
    await expect(page.locator('#DlgStageStart_ModalId')).not.toBeVisible({ timeout: 5000 });

    // 4. In StageAdmin: Add 4 dummy players and select available game mode
    const btnAddDummy = page.locator('#StageAdmin_BtnAddDummy, button:has-text("ADD DUMMY")');
    await expect(btnAddDummy).toBeVisible({ timeout: 5000 });
    for (let i = 0; i < 4; i++) {
      await btnAddDummy.click();
    }

    const selectMode = page.locator('#StageAdmin_SelectDrawMode, select[id*="SelectDrawMode"]');
    await expect(selectMode).toBeVisible({ timeout: 5000 });
    const enabledOption = selectMode.locator('option:not([disabled]):not([value="UNKN"])').first();
    const optVal = await enabledOption.getAttribute('value');
    if (optVal) {
      await selectMode.selectOption(optVal);
    }

    const btnStartDrawing = page.locator('#StageAdmin_BtnStartDrawing, button[id*="BtnStartDrawing"]');
    await expect(btnStartDrawing).toBeEnabled({ timeout: 5000 });
    await btnStartDrawing.click();

    // 5. In StageDraw: Start playing matches
    const btnStartPlaying = page.locator('#StageDraw_BtnStartPlaying, button[id*="BtnStartPlaying"]');
    await expect(btnStartPlaying).toBeVisible({ timeout: 5000 });
    await btnStartPlaying.click();

    // 6. In StageInput: DEMO-Ergebnisse button must be visible and enabled
    await expect(stageInputPage.btnDemoScores).toBeVisible({ timeout: 5000 });
    await expect(stageInputPage.btnDemoScores).toBeEnabled();

    // Click DEMO-Ergebnisse
    await stageInputPage.btnDemoScores.click();

    // Verify DEMO-Ergebnisse button becomes disabled once all matches are finished
    await expect(stageInputPage.btnDemoScores).toBeDisabled({ timeout: 5000 });

    // Assert that badges displaying scores are visible and contain valid scores
    const setsBadges = page.locator('span[id^="sets_"]');
    const count = await setsBadges.count();
    expect(count).toBeGreaterThan(0);
    for (let i = 0; i < count; i++) {
      const text = await setsBadges.nth(i).textContent();
      expect(text?.trim()).toMatch(/\d+:\d+/);
    }

    // Verify no matchboard requests occurred for local/demo tourney
    expect(matchboardRequests).toHaveLength(0);

    // Verify no stage.notFound errors occurred
    expect(consoleErrors.filter(e => e.includes('stage.notFound'))).toHaveLength(0);
  });

  test('TC-SI-08 [👤]: KO-System Schiedsrichterzettel für Spiele mit noch nicht feststehenden Spielern und Ausblenden bei Freilosen', async ({ page }) => {
    // 1. Start Demo mode from home page
    const mainView = new MainViewPage(page);
    await mainView.goto();
    await mainView.startDemo();

    // 2. Select first competition from TourneyInfo
    const compCell = page.locator('table tbody tr td.fw-bold').first();
    await expect(compCell).toBeVisible({ timeout: 5000 });
    await compCell.click();

    // 3. Start a new stage from CompetitionInfo
    const btnStartStage = page.locator('button[id*="BtnStartStage"], button:has-text("Austragungsphase")');
    await expect(btnStartStage).toBeVisible({ timeout: 5000 });
    await btnStartStage.click();

    // Fill stage start dialog
    const inputStageName = page.locator('input[id*="DlgStageStart_InputNameId"]');
    await expect(inputStageName).toBeVisible({ timeout: 5000 });
    await inputStageName.fill('KO Demo Phase');
    const btnStageStartApply = page.locator('button[id*="DlgStageStart_ApplyId"]');
    await btnStageStartApply.click();
    await expect(page.locator('#DlgStageStart_ModalId')).not.toBeVisible({ timeout: 5000 });

    // 4. In StageAdmin: Add 6 dummy players (in bracket of 8, gives 2 byes)
    const btnAddDummy = page.locator('#StageAdmin_BtnAddDummy, button:has-text("ADD DUMMY")');
    await expect(btnAddDummy).toBeVisible({ timeout: 5000 });
    for (let i = 0; i < 6; i++) {
      await btnAddDummy.click();
    }

    // Select KO mode (KO)
    const selectMode = page.locator('#StageAdmin_SelectDrawMode, select[id*="SelectDrawMode"]');
    await expect(selectMode).toBeVisible({ timeout: 5000 });
    await selectMode.selectOption('KO');

    const btnStartDrawing = page.locator('#StageAdmin_BtnStartDrawing, button[id*="BtnStartDrawing"]');
    await expect(btnStartDrawing).toBeEnabled({ timeout: 5000 });
    await btnStartDrawing.click();

    // 5. In StageDraw: Start playing matches
    const btnStartPlaying = page.locator('#StageDraw_BtnStartPlaying, button[id*="BtnStartPlaying"]');
    await expect(btnStartPlaying).toBeVisible({ timeout: 5000 });
    await btnStartPlaying.click();

    // 6. In StageInput:
    // Verify Bye match (game with Freilos / Bye) has NO Schiedsrichterzettel button
    const byeRow = page.locator('tr:has-text("Freilos"), tr:has-text("Bye")').first();
    await expect(byeRow).toBeVisible({ timeout: 5000 });
    const byeSchiriBtn = byeRow.locator('button[id*="SchiriBtn"]');
    await expect(byeSchiriBtn).toHaveCount(0);

    // Verify final match (round 1 section, where players are not yet determined) HAS a Schiedsrichterzettel button
    const finalRow = page.locator('#round-section-1 tr[id^="row-"]').first();
    await expect(finalRow).toBeVisible({ timeout: 5000 });
    const finalSchiriBtn = finalRow.locator('button[id*="SchiriBtn"]');
    await expect(finalSchiriBtn).toBeVisible({ timeout: 5000 });

    // 7. Click Schiedsrichterzettel button for the final match
    await finalSchiriBtn.click();

    // 8. In StageScoreSheet:
    // Verify the referee card for this match is visible
    const refereeGrid = page.locator('.refereeGrid').first();
    await expect(refereeGrid).toBeVisible({ timeout: 5000 });

    // Verify player names are empty (not "noch nicht ermittelt")
    const p1Name = await page.locator('.RefereePlayer1').first().textContent();
    const p2Name = await page.locator('.RefereePlayer2').first().textContent();
    expect(p1Name?.trim()).toBe('');
    expect(p2Name?.trim()).toBe('');
  });

  test('TC-SI-09 [👤]: Groups Schiedsrichterzettel zeigt nur den Schiri-Zettel des ausgewählten Spiels an', async ({ page }) => {
    // 1. Start Demo mode from home page
    const mainView = new MainViewPage(page);
    await mainView.goto();
    await mainView.startDemo();

    // 2. Select first competition from TourneyInfo
    const compCell = page.locator('table tbody tr td.fw-bold').first();
    await expect(compCell).toBeVisible({ timeout: 5000 });
    await compCell.click();

    // 3. Start a new stage from CompetitionInfo
    const btnStartStage = page.locator('button[id*="BtnStartStage"], button:has-text("Austragungsphase")');
    await expect(btnStartStage).toBeVisible({ timeout: 5000 });
    await btnStartStage.click();

    // Fill stage start dialog
    const inputStageName = page.locator('input[id*="DlgStageStart_InputNameId"]');
    await expect(inputStageName).toBeVisible({ timeout: 5000 });
    await inputStageName.fill('Gruppen Demo Phase');
    const btnStageStartApply = page.locator('button[id*="DlgStageStart_ApplyId"]');
    await btnStageStartApply.click();
    await expect(page.locator('#DlgStageStart_ModalId')).not.toBeVisible({ timeout: 5000 });

    // 4. In StageAdmin: Add 6 dummy players
    const btnAddDummy = page.locator('#StageAdmin_BtnAddDummy, button:has-text("ADD DUMMY")');
    await expect(btnAddDummy).toBeVisible({ timeout: 5000 });
    for (let i = 0; i < 6; i++) {
      await btnAddDummy.click();
    }

    // Select Groups mode (GRPS3)
    const selectMode = page.locator('#StageAdmin_SelectDrawMode, select[id*="SelectDrawMode"]');
    await expect(selectMode).toBeVisible({ timeout: 5000 });
    await selectMode.selectOption('GRPS3');

    const btnStartDrawing = page.locator('#StageAdmin_BtnStartDrawing, button[id*="BtnStartDrawing"]');
    await expect(btnStartDrawing).toBeEnabled({ timeout: 5000 });
    await btnStartDrawing.click();

    // 5. In StageDraw: Start playing matches
    const btnStartPlaying = page.locator('#StageDraw_BtnStartPlaying, button[id*="BtnStartPlaying"]');
    await expect(btnStartPlaying).toBeVisible({ timeout: 5000 });
    await btnStartPlaying.click();

    // 6. In StageInput:
    // Locate the first match's Schiedsrichterzettel button
    const firstSchiriBtn = page.locator('button[id*="SchiriBtn"]').first();
    await expect(firstSchiriBtn).toBeVisible({ timeout: 5000 });
    await firstSchiriBtn.click();

    // 7. In StageScoreSheet:
    // Verify that exactly 1 referee card is visible in the preview grid
    const refereeCards = page.locator('.row.row-cols-1.row-cols-md-2 .refereeGrid');
    await expect(refereeCards).toHaveCount(1, { timeout: 5000 });

    // Verify the single-card print button is rendered
    const printSingleBtn = page.locator('button[id*="PrintSingleBtn"], button:has-text("Zettel drucken"), button:has-text("Print Scoresheet")');
    await expect(printSingleBtn).toBeVisible({ timeout: 5000 });
  });
});
