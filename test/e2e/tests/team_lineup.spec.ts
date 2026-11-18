import { test, expect, Page } from '@playwright/test';

/**
 * Mannschaftsaufstellung (DlgLineup), 6er-Paarkreuz: In den Doppeln darf jeder Spieler nur einmal eingesetzt werden.
 */

/** Legt einen 6er-Mannschaftswettbewerb mit drei Mannschaften an, startet eine Spielphase und öffnet die Aufstellung von Mannschaft A. */
async function openLineup6(page: Page) {
  await page.goto('/');
  await page.locator('#MainView_BtnDemo').click();
  await expect(page.locator('td', { hasText: 'S-Klasse Herren Einzel' }).first()).toBeVisible({ timeout: 15000 });
  await page.locator('[id^="TourneyInfo_BtnAddComp"]').click();
  await page.locator('#DlgCompetition_CompNameId').fill('Sechser Test');
  await page.locator('#DlgCompetition_CompTypId').selectOption('TEAM6');
  await page.locator('#DlgCompetition_ApplyId').click();
  await expect(page.locator('#DlgCompetition_ModalId')).not.toBeVisible({ timeout: 5000 });
  await page.evaluate(() => (window as any).appLoadPage('TeamList', ''));
  for (let t = 0; t < 3; t++) {
    await page.locator('button[id^="TeamList_BtnAddTeam-"]').first().click();
    await page.locator('#DlgTeam_InputNameId').fill(`Team ${t + 1}`);
    for (let i = 1; i <= 6; i++) {
      const sel = page.locator(`#DlgTeam_SelPlayerId-${i}`);
      const val = await sel.locator('option:not([disabled])').nth(1).getAttribute('value');
      await sel.selectOption(val!);
    }
    await page.locator('#DlgTeam_ApplyId').click();
    await expect(page.locator('#DlgTeam_ModalId')).not.toBeVisible({ timeout: 5000 });
  }
  await page.evaluate(() => (window as any).appLoadPage('TourneyInfo', ''));
  await page.locator('tr', { hasText: 'Sechser Test' }).locator('.comp-link').click();
  await page.locator('button[id*="BtnStartStage"]').click();
  await page.locator('input[id*="DlgStageStart_InputNameId"]').fill('Runde');
  await page.locator('button[id*="DlgStageStart_ApplyId"]').click();
  await expect(page.locator('#DlgStageStart_ModalId')).not.toBeVisible({ timeout: 5000 });
  await page.locator('#StageAdmin_SelectDrawMode').selectOption('RR');
  await page.locator('#StageAdmin_BtnStartDrawing').click();
  await page.locator('#StageDraw_BtnStartPlaying').click();
  await openDialog(page, 1, 0);
}

/** Öffnet die Aufstellung einer Mannschaft (side 0 = A, 1 = B) der Begegnung gameNo. */
async function openDialog(page: Page, gameNo: number, side: number) {
  await page.locator(`#StageInput_LineupBtn-${gameNo}_${side}`).click();
  await expect(page.locator('#DlgLineup_ModalId')).toBeVisible({ timeout: 5000 });
}

/** Auswahl einer Position im Aufstellungsdialog. */
const sel = (page: Page, pos: string, idx = 1) => page.locator(`#DlgLineup_SelPosId-${pos}-${idx}`);

/** Prüft, ob der Spieler mit der Id in der Auswahl gesperrt ist. */
async function isBlocked(page: Page, pos: string, idx: number, id: string) {
  return sel(page, pos, idx).locator(`option[value="${id}"]`).evaluate(o => (o as HTMLOptionElement).disabled);
}

test.describe('UseCase 24: Mannschaftsaufstellung (DlgLineup)', () => {
  test('TC-TL-01 [👤]: 6er-Mannschaft – jeder Spieler nur in einem Doppel', async ({ page }) => {
    await openLineup6(page);
    const ids = await sel(page, 'D1', 1).locator('option').evaluateAll(os => os.map(o => (o as HTMLOptionElement).value).filter(v => v !== '0'));
    expect(ids.length).toBe(6);
    const [p1, p2] = ids;
    await sel(page, 'D1', 1).selectOption(p1);
    await sel(page, 'D1', 2).selectOption(p2);
    // in den übrigen Doppeln gesperrt, im Einzel weiterhin wählbar
    for (const pos of ['D2', 'D3']) for (const idx of [1, 2]) {
      expect(await isBlocked(page, pos, idx, p1)).toBe(true);
      expect(await isBlocked(page, pos, idx, p2)).toBe(true);
    }
    expect(await isBlocked(page, 'A1', 1, p1)).toBe(false);
    // nach Abwahl wieder freigegeben
    await sel(page, 'D1', 2).selectOption('0');
    expect(await isBlocked(page, 'D2', 1, p2)).toBe(false);

    // Prüfung beim Übernehmen (z. B. bei bestehender, älterer Aufstellung)
    await sel(page, 'D1', 2).selectOption(p2);
    await sel(page, 'D2', 2).selectOption(ids[2]);
    await sel(page, 'D2', 1).evaluate((el, v) => { (el as HTMLSelectElement).value = v; }, p1);
    let msg = '';
    page.once('dialog', d => { msg = d.message(); d.dismiss(); });
    await page.locator('#DlgLineup_ApplyId').click();
    await expect.poll(() => msg).toBe('Ein Spieler kann nur ein Doppel spielen.');
    await expect(page.locator('#DlgLineup_ModalId')).toBeVisible();
  });

  test('TC-TL-02 [👤]: Demo-Ergebnisse stellen fehlende Aufstellungen auf, starten die Begegnungen und tragen die Ergebnisse ein', async ({ page }) => {
    await openLineup6(page);
    // Mannschaft A der Begegnung 1 von Hand: Spieler in umgekehrter Reihenfolge
    const ids = await sel(page, 'A1').locator('option').evaluateAll(os => os.map(o => (o as HTMLOptionElement).value).filter(v => v !== '0'));
    const rev = [...ids].reverse();
    const lastA1 = (await sel(page, 'A1').locator(`option[value="${rev[0]}"]`).textContent())!.split(',')[0].trim();
    for (let i = 0; i < 6; i++) await sel(page, `A${i + 1}`).selectOption(rev[i]);
    for (let d = 0; d < 3; d++) {
      await sel(page, `D${d + 1}`, 1).selectOption(rev[2 * d]);
      await sel(page, `D${d + 1}`, 2).selectOption(rev[2 * d + 1]);
    }
    await page.locator('#DlgLineup_ApplyId').click();
    await expect(page.locator('#DlgLineup_ModalId')).not.toBeVisible({ timeout: 5000 });

    await page.locator('#StageInput_BtnDemoScores').click();
    await expect(page.locator('.badge', { hasText: 'Beendet' }).first()).toBeVisible({ timeout: 10000 });

    // Spiele der Begegnungen: Positionen und Spieler je Seite (Heim-/Gast-Zelle)
    const games = (gameNo: number) => page.locator(`tr.enc-sub-${gameNo}`).evaluateAll(trs => trs.map(tr => {
      const tds = Array.from(tr.querySelectorAll('td')).map(td => td.textContent!.replace(/\s+/g, ' ').trim());
      const k = tds.findIndex(t => t === 'Doppel' || t === 'Einzel');
      return { home: tds[k - 1], kind: tds[k], away: tds[k + 1] };
    }));
    for (const gameNo of [1, 2, 3]) {
      const gs = await games(gameNo);
      expect(gs.length).toBeGreaterThan(0);
      for (const side of ['home', 'away'] as const) {
        const pos = (g: any) => side === 'home' ? g[side].split(' ')[0] : g[side].split(' ').pop();
        const doubles = new Map<string, string>();
        gs.filter(g => g.kind === 'Doppel').forEach(g => doubles.set(pos(g), g[side]));
        expect(doubles.size).toBe(3);
        const names = [...doubles.values()].flatMap(v => v.replace(/^D\d\s*|\s*D\d$/g, '').split('/').map(x => x.trim()));
        expect(new Set(names).size).toBe(6);              // jeder Spieler nur in einem Doppel
      }
    }
    // eigene Aufstellung von Mannschaft A in Begegnung 1 bleibt: A1 = letzter Spieler des Kaders
    const a1 = (await games(1)).find(g => g.kind === 'Einzel' && g.home.startsWith('A1 '))!;
    expect(a1.home).toContain(lastA1);
  });
});
