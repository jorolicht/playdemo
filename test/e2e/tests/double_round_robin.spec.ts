import { test, expect, Page } from '@playwright/test';
import { MainViewPage } from '../pages/MainViewPage';

/**
 * Doppelte Runde (Double Round Robin) als gekoppelte Stages Hinrunde/Rückrunde, siehe doc/DoubleModes.md.
 * Demo tourney, competition "B-Klasse Damen Einzel" (no stages): new stage "Liga" with 4 dummies.
 */

/** Legt die Stage "Liga" mit 4 Dummies als Doppelte Runde an, lost aus und startet die Spielphase (Hinrunde). */
async function startDoubleRoundRobin(page: Page) {
  const mainView = new MainViewPage(page);
  await mainView.goto();
  await mainView.startDemo();
  await page.locator('tr', { hasText: 'B-Klasse Damen Einzel' }).locator('.comp-link').click();
  await page.locator('button[id*="BtnStartStage"]').click();
  await page.locator('input[id*="DlgStageStart_InputNameId"]').fill('Liga');
  await page.locator('button[id*="DlgStageStart_ApplyId"]').click();
  await expect(page.locator('#DlgStageStart_ModalId')).not.toBeVisible({ timeout: 5000 });
  for (let i = 0; i < 4; i++) await page.locator('#StageAdmin_BtnAddDummy').click();
  await page.locator('#StageAdmin_SelectDrawMode').selectOption('DRR');
  await page.locator('#StageAdmin_BtnStartDrawing').click();
  await expect(page.locator('.alert', { hasText: 'Rückrunde mit denselben Paarungen' })).toBeVisible({ timeout: 5000 });
  await page.locator('#StageDraw_BtnStartPlaying').click();
  await expect(page.locator('h2', { hasText: 'Liga – Hinrunde' })).toBeVisible({ timeout: 5000 });
}

/** Paarungen der Ergebniseingabe: Spielnummer → [Name A, Name B]. */
async function pairings(page: Page): Promise<Record<string, [string, string]>> {
  return page.evaluate(() => {
    const res: Record<string, [string, string]> = {};
    document.querySelectorAll('tr[id^="row-"]').forEach(tr => {
      const no = tr.id.substring(4);
      const a = document.getElementById(`nameA-${no}`)?.textContent?.trim() ?? '';
      const b = document.getElementById(`nameB-${no}`)?.textContent?.trim() ?? '';
      res[no] = [a, b];
    });
    return res;
  });
}

/** Summe gewonnener + verlorener Spiele je Zeile der Ergebnistabelle (Spalte 3, Format "g:v"). */
async function matchesPerRow(page: Page): Promise<number[]> {
  const cells = await page.locator('.stage-content table tbody tr td:nth-child(3)').allTextContents();
  return cells.map(c => c.trim().split(':').map(Number).reduce((a, b) => a + b, 0));
}

test.describe('UseCase 12: Doppelte Runde (Hin- und Rückrunde)', () => {

  test('TC-DM-01 [👤]: Start legt die Rückrunde mit gespiegelten Paarungen an', async ({ page }) => {
    await startDoubleRoundRobin(page);
    const first = await pairings(page);
    expect(Object.keys(first).length).toBe(6);

    // Umschalter Hinrunde | Rückrunde, Wechsel auf dieselbe Seite der Rückrunde
    await page.locator('button[id^="CompetitionInfo_BtnStageTab-"]', { hasText: 'Rückrunde' }).click();
    await expect(page.locator('h2', { hasText: 'Liga – Rückrunde' })).toBeVisible({ timeout: 5000 });
    await expect(page.locator('.stage-tabs .nav-link.active[href*="StageInput"]')).toBeVisible();
    const second = await pairings(page);
    expect(Object.keys(second).length).toBe(6);
    for (const [no, [a, b]] of Object.entries(first)) expect(second[no]).toEqual([b, a]);

    // Rückrunde wartet im Status Auslosung, ihre Auslosung ist nicht änderbar
    await expect(page.locator('.badge', { hasText: 'Auslosung' }).first()).toBeVisible();
    await page.evaluate(() => (window as any).appLoadPage('StageDraw', ''));
    await expect(page.locator('.alert', { hasText: 'übernimmt die Auslosung der Hinrunde' })).toBeVisible({ timeout: 5000 });
    await expect(page.locator('#StageDraw_BtnStartPlaying')).toHaveCount(0);

    // Administration der Rückrunde: Hinweis auf die Kopplung, Modus angezeigt, Auslosung/Konfiguration nicht löschbar
    await page.evaluate(() => (window as any).appLoadPage('StageAdmin', ''));
    await expect(page.locator('.alert', { hasText: 'Gekoppelt mit "Liga – Hinrunde"' })).toBeVisible({ timeout: 5000 });
    await expect(page.locator('#StageAdmin_SelectDrawMode')).toHaveValue('DRR2');
    await expect(page.locator('#StageAdmin_InfoSelectMode')).toBeHidden();
    await expect(page.locator('#StageAdmin_BtnResetDrw')).toBeDisabled();
    await expect(page.locator('#StageAdmin_BtnResetCfg')).toBeDisabled();

    // CompetitionInfo: Rückrunde direkt unter der Hinrunde, "Nachfolger starten" nur an der Rückrunde
    await page.evaluate(() => (window as any).appLoadPage('CompetitionInfo', ''));
    const names = (await page.locator('.stage-link').allTextContents()).map(s => s.trim());
    expect(names).toEqual(['Liga – Hinrunde', 'Liga – Rückrunde']);
    const rows = page.locator('tr', { has: page.locator('.stage-link') });
    await expect(rows.nth(1).locator('.bi-link-45deg')).toBeVisible();
    await expect(rows.nth(0).locator('button[id^="CompetitionInfo_BtnSuccessor-"]')).toHaveCount(0);
    await expect(rows.nth(1).locator('button[id^="CompetitionInfo_BtnSuccessor-"]')).toHaveCount(1);
  });

  test('TC-DM-02 [👤]: Rückrunde startet nach der Hinrunde, Gesamttabelle aus beiden Runden', async ({ page }) => {
    await startDoubleRoundRobin(page);
    await page.locator('#StageInput_BtnDemoScores').click();
    await expect(page.locator('.badge', { hasText: 'Beendet' }).first()).toBeVisible({ timeout: 5000 });

    // Ergebnisse der Hinrunde: Hinweis statt Nachfolger/Finalisieren
    await page.evaluate(() => (window as any).appLoadPage('StageResult', ''));
    await expect(page.locator('.stage-content', { hasText: 'in der Rückrunde' })).toBeVisible({ timeout: 5000 });
    await expect(page.locator('#StageResult_NextStageBtn')).toHaveCount(0);

    // Rückrunde ist jetzt spielbar
    await page.locator('button[id^="CompetitionInfo_BtnStageTab-"]', { hasText: 'Rückrunde' }).click();
    await expect(page.locator('h2', { hasText: 'Liga – Rückrunde' })).toBeVisible({ timeout: 5000 });
    await page.evaluate(() => (window as any).appLoadPage('StageInput', ''));
    await expect(page.locator('#StageInput_BtnDemoScores')).toBeEnabled({ timeout: 5000 });
    await page.locator('#StageInput_BtnDemoScores').click();
    await expect(page.locator('.badge', { hasText: 'Beendet' }).first()).toBeVisible({ timeout: 5000 });

    // Gesamttabelle (Standard): je Spielerin 6 Spiele, Ansicht Rückrunde: je 3 Spiele
    await page.evaluate(() => (window as any).appLoadPage('StageResult', ''));
    await expect(page.locator('#StageResult_BtnDrrTotal')).toHaveClass(/btn-hero-primary/, { timeout: 5000 });
    expect(await matchesPerRow(page)).toEqual([6, 6, 6, 6]);
    await page.locator('#StageResult_BtnDrrSecond').click();
    await expect(page.locator('#StageResult_BtnDrrSecond')).toHaveClass(/btn-hero-primary/);
    expect(await matchesPerRow(page)).toEqual([3, 3, 3, 3]);
    await expect(page.locator('#StageResult_NextStageBtn')).toBeEnabled();

    // Nachfolger der Rückrunde: Filter "Obere Hälfte" nach der Gesamttabelle
    await page.locator('#StageResult_BtnDrrTotal').click();
    // obere Hälfte = Platz 1–2 laut Gesamttabelle (bei Gleichstand ggf. mehr)
    const ranks = await page.locator('.stage-content table tbody tr td:nth-child(1)').allTextContents();
    const pnames = await page.locator('.stage-content table tbody tr td:nth-child(2)').allTextContents();
    const top2 = pnames.filter((_, i) => Number(ranks[i].trim()) <= 2).map(s => s.trim());
    await page.locator('#StageResult_NextStageBtn').click();
    await expect(page.locator('select[id*="DlgStageStart_InputPrefId"]')).toHaveValue(/\d+/, { timeout: 5000 });
    await page.locator('input[id*="DlgStageStart_InputNameId"]').fill('Endrunde');
    await page.locator('button[id*="DlgStageStart_ApplyId"]').click();
    await expect(page.locator('h2', { hasText: 'Endrunde' })).toBeVisible({ timeout: 5000 });
    await page.locator('#StageAdmin_RadTop').click();
    const checked = await page.locator('tr', { has: page.locator('.player-draw-check:checked') }).locator('td:nth-child(2)').allTextContents();
    expect(checked.map(s => s.trim()).sort()).toEqual([...top2].sort());
  });

  test('TC-DM-07 [👤]: Auslosung der Hinrunde zurücksetzen löst die Kopplung', async ({ page }) => {
    await startDoubleRoundRobin(page);
    await page.evaluate(() => (window as any).appLoadPage('StageAdmin', ''));
    await page.locator('#StageAdmin_BtnResetDrw').click();
    await page.locator('.modal.show .box-btn.btn-hero-primary').click();
    await expect(page.locator('h2', { hasText: /^\s*Liga\s*$/ })).toBeVisible({ timeout: 5000 });
    await expect(page.locator('button[id^="CompetitionInfo_BtnStageTab-"]', { hasText: 'Rückrunde' })).toHaveCount(0);
    await page.evaluate(() => (window as any).appLoadPage('CompetitionInfo', ''));
    const names = (await page.locator('.stage-link').allTextContents()).map(s => s.trim());
    expect(names).toEqual(['Liga']);
  });

  test('TC-DM-06 [👤]: Löschen der Hinrunde löscht auch die Rückrunde', async ({ page }) => {
    await startDoubleRoundRobin(page);
    await page.evaluate(() => (window as any).appLoadPage('CompetitionInfo', ''));
    await expect(page.locator('.stage-link')).toHaveCount(2, { timeout: 5000 });
    await page.locator('tr', { has: page.locator('.stage-link', { hasText: 'Liga – Hinrunde' }) })
      .locator('button[id^="CompetitionInfo_BtnDeleteStage-"]').click();
    const box = page.locator('.modal.show');
    await expect(box).toContainText('gemeinsam gelöscht', { timeout: 5000 });
    await box.locator('.box-btn.btn-hero-primary').click();
    await expect(page.locator('.stage-link')).toHaveCount(0, { timeout: 5000 });
  });
});
