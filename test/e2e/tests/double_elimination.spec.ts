import { test, expect, Page } from '@playwright/test';
import { MainViewPage } from '../pages/MainViewPage';

/**
 * Doppeltes K.-o. (Double Elimination) als gekoppelte Stages Gewinnerrunde/Verliererrunde, siehe doc/DoubleModes.md.
 * Demo tourney, competition "B-Klasse Damen Einzel" (no stages): new stage "Cup" with n dummies.
 */

/** Legt "Cup" mit n Dummies als Doppeltes K.-o. an, lost aus und startet die Spielphase (Gewinnerrunde). */
async function startDoubleElimination(page: Page, players: number, reset = false) {
  const mainView = new MainViewPage(page);
  await mainView.goto();
  await mainView.startDemo();
  await page.locator('tr', { hasText: 'B-Klasse Damen Einzel' }).locator('.comp-link').click();
  await page.locator('button[id*="BtnStartStage"]').click();
  await page.locator('input[id*="DlgStageStart_InputNameId"]').fill('Cup');
  await page.locator('button[id*="DlgStageStart_ApplyId"]').click();
  await expect(page.locator('#DlgStageStart_ModalId')).not.toBeVisible({ timeout: 5000 });
  for (let i = 0; i < players; i++) await page.locator('#StageAdmin_BtnAddDummy').click();
  await page.locator('#StageAdmin_SelectDrawMode').selectOption('DE');
  await expect(page.locator('#StageAdmin_BoxFinalReset')).toBeVisible();
  if (reset) await page.locator('#StageAdmin_ChkFinalReset').check();
  await page.locator('#StageAdmin_BtnStartDrawing').click();
  await expect(page.locator('.alert', { hasText: 'Verliererrunde mit Finale angelegt' })).toBeVisible({ timeout: 5000 });
  await page.locator('#StageDraw_BtnStartPlaying').click();
  await expect(page.locator('h2', { hasText: 'Cup – Gewinnerrunde' })).toBeVisible({ timeout: 5000 });
}

type Row = { a: string; b: string; sets: string };

/** Spiele der Ergebniseingabe: Spielnummer → Namen A/B und Satzstand ("3:1"). */
async function rows(page: Page): Promise<Record<number, Row>> {
  return page.evaluate(() => {
    const res: Record<number, { a: string; b: string; sets: string }> = {};
    document.querySelectorAll('tr[id^="row-"]').forEach(tr => {
      const no = Number(tr.id.substring(4));
      const txt = (id: string) => document.getElementById(id)?.textContent?.trim() ?? '';
      res[no] = { a: txt(`nameA-${no}`), b: txt(`nameB-${no}`), sets: txt(`sets_${no}`) };
    });
    return res;
  });
}

const winner = (r: Row) => { const [x, y] = r.sets.split(':').map(Number); return x > y ? r.a : r.b; };
const loser  = (r: Row) => { const [x, y] = r.sets.split(':').map(Number); return x > y ? r.b : r.a; };

/** Wechselt mit dem Umschalter auf die Verliererrunde (gleiche Seite). */
async function toLosers(page: Page) {
  await page.locator('button[id^="CompetitionInfo_BtnStageTab-"]', { hasText: 'Verliererrunde' }).click();
  await expect(page.locator('h2', { hasText: 'Cup – Verliererrunde' })).toBeVisible({ timeout: 5000 });
}

/** Trägt ein Ergebnis ein: aWins = true → A gewinnt 3:0, sonst B. */
async function enter(page: Page, gameNo: number, aWins: boolean) {
  for (let s = 1; s <= 3; s++) await page.locator(`#input_${gameNo}_${s}`).fill(aWins ? '5' : '-5');
  await page.locator(`#StageInput_SaveMatchBtn-${gameNo}`).click();
  await expect(page.locator(`#sets_${gameNo}`)).toHaveText(aWins ? '3:0' : '0:3', { timeout: 5000 });
}

/** Plätze der Platzierungstabelle der Verliererrunde. */
async function placement(page: Page): Promise<{ rank: number; name: string }[]> {
  await page.evaluate(() => (window as any).appLoadPage('StageResult', ''));
  await expect(page.locator('#de-placement')).toBeVisible({ timeout: 5000 });
  return page.locator('#de-placement tbody tr').evaluateAll(trs => trs
    .filter(tr => tr.querySelectorAll('td').length === 2)
    .map(tr => ({ rank: Number(tr.children[0].textContent!.trim()), name: tr.children[1].textContent!.trim() })));
}

test.describe('UseCase 12: Doppeltes K.-o. (Gewinner- und Verliererrunde)', () => {

  test('TC-DM-03 [👤]: 8 Spieler – Verlierer landen an den richtigen Plätzen der Verliererrunde, Platzierung 1–8', async ({ page }) => {
    await startDoubleElimination(page, 8);
    const wbInit = await rows(page);
    expect(Object.keys(wbInit).length).toBe(7);           // kein Spiel um Platz 3
    await page.locator('#StageInput_BtnDemoScores').click();
    await expect(page.locator('#sets_7')).not.toHaveText('', { timeout: 5000 });
    const wb = await rows(page);

    await toLosers(page);
    await expect(page.locator('.round-section h6').first()).toHaveText('Verliererrunde 1');
    const lb = await rows(page);
    expect(Object.keys(lb).length).toBe(7);               // 6 LB-Spiele + Finale
    // LB-Runde 1: Verlierer der WB-Spiele 1/2 und 3/4
    expect([lb[1].a, lb[1].b]).toEqual([loser(wb[1]), loser(wb[2])]);
    expect([lb[2].a, lb[2].b]).toEqual([loser(wb[3]), loser(wb[4])]);
    // LB-Runde 2 (Einsteiger, umgekehrt): Verlierer WB-Halbfinale 1 → Spiel 4, Halbfinale 2 → Spiel 3
    expect(lb[4].b).toBe(loser(wb[5]));
    expect(lb[3].b).toBe(loser(wb[6]));
    // LB-Runde 4: Verlierer WB-Finale; Finale: WB-Sieger auf Position A
    expect(lb[6].b).toBe(loser(wb[7]));
    expect(lb[7].a).toBe(winner(wb[7]));

    await page.locator('#StageInput_BtnDemoScores').click();
    await expect(page.locator('#sets_7')).not.toHaveText('', { timeout: 5000 });
    const ranks = (await placement(page)).map(p => p.rank);
    expect(ranks).toEqual([1, 2, 3, 4, 5, 5, 7, 7]);
  });

  test('TC-DM-04 [👤]: 6 Spieler – Freilose werden in der Verliererrunde durchgereicht', async ({ page }) => {
    await startDoubleElimination(page, 6);
    await page.locator('#StageInput_BtnDemoScores').click();
    await expect(page.locator('#sets_7')).not.toHaveText('', { timeout: 5000 });
    await toLosers(page);
    await page.locator('#StageInput_BtnDemoScores').click();
    await expect(page.locator('#sets_7')).not.toHaveText('', { timeout: 5000 });
    await expect(page.locator('.badge', { hasText: 'Beendet' }).first()).toBeVisible();
    const places = await placement(page);
    expect(places.length).toBe(6);
    expect(places.slice(0, 4).map(p => p.rank)).toEqual([1, 2, 3, 4]);
    expect(places.every(p => p.name.startsWith('Dummy'))).toBe(true);
    expect(new Set(places.map(p => p.name)).size).toBe(6);
  });

  test('TC-DM-05 [👤]: Finale mit Rückspiel – nur nötig, wenn der Sieger der Verliererrunde gewinnt', async ({ page }) => {
    await startDoubleElimination(page, 4, true);
    await page.locator('#StageInput_BtnDemoScores').click();
    await expect(page.locator('#sets_3')).not.toHaveText('', { timeout: 5000 });
    await toLosers(page);
    await enter(page, 1, true);
    await enter(page, 2, true);
    const before = await rows(page);
    const wbChamp = before[3].a, lbChamp = before[3].b;
    // LB-Sieger gewinnt das erste Finale → Rückspiel mit denselben Spielern
    await enter(page, 3, false);
    await expect(page.locator('#nameA-4')).toHaveText(wbChamp);
    await expect(page.locator('#nameB-4')).toHaveText(lbChamp);
    await enter(page, 4, true);
    const places = await placement(page);
    expect(places.map(p => p.rank)).toEqual([1, 2, 3, 4]);
    expect(places[0].name).toBe(wbChamp);
    expect(places[1].name).toBe(lbChamp);

    // Korrektur: WB-Sieger gewinnt das erste Finale → Rückspiel nicht erforderlich
    await page.evaluate(() => (window as any).appLoadPage('StageInput', ''));
    await page.locator('#StageInput_DeleteMatchBtn-4').click();
    await page.locator('.modal.show .box-btn.btn-hero-primary').click();
    await page.locator('#StageInput_DeleteMatchBtn-3').click();
    await page.locator('.modal.show .box-btn.btn-hero-primary').click();
    await expect(page.locator('#sets_3')).toHaveText('', { timeout: 5000 });
    await enter(page, 3, true);
    await page.evaluate(() => (window as any).appLoadPage('StageResult', ''));
    await expect(page.locator('.de-match[data-game="4"]')).toContainText('nicht erforderlich', { timeout: 5000 });
    const places2 = await placement(page);
    expect(places2[0].name).toBe(wbChamp);
    expect(places2[1].name).toBe(lbChamp);
  });

  test('TC-DM-08 [👤]: Korrektur in der Gewinnerrunde setzt abhängige Spiele der Verliererrunde zurück', async ({ page }) => {
    await startDoubleElimination(page, 4);
    await page.locator('#StageInput_BtnDemoScores').click();
    await expect(page.locator('#sets_3')).not.toHaveText('', { timeout: 5000 });
    await toLosers(page);
    await enter(page, 1, true);
    await expect(page.locator('#sets_1')).toHaveText('3:0');
    // zurück zur Gewinnerrunde, Ergebnis von Spiel 1 löschen (Rückfrage mit Hinweis)
    await page.locator('button[id^="CompetitionInfo_BtnStageTab-"]', { hasText: 'Gewinnerrunde' }).click();
    await page.locator('#StageInput_DeleteMatchBtn-1').click();
    const box = page.locator('.modal.show');
    await expect(box).toContainText('Verliererrunde werden ebenfalls zurückgesetzt', { timeout: 5000 });
    await box.locator('.box-btn.btn-hero-primary').click();
    await toLosers(page);
    await expect(page.locator('#sets_1')).toHaveText('');
    await expect(page.locator('#nameA-1')).not.toHaveText(/Dummy/);
  });
});
