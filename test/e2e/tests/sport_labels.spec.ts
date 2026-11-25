import { test, expect, Page } from '@playwright/test';

/**
 * UseCase 35: Bezeichnungen aus dem Wertungssystem (shared/sports/tt_00n.json, base.SportText) statt aus den
 * Message-Dateien: Format des Wettbewerbs, Stufen in Tabellenköpfen und Gewinnsätze, deutsch und englisch.
 * Demo-Turnier: Wettbewerb 1 „S-Klasse Herren Einzel“ mit abgeschlossener Vorrunde (Jeder gegen jeden).
 */
test.describe('UseCase 35: Bezeichnungen aus dem Wertungssystem', () => {

  /** Öffnet das Demo-Turnier und einen Reiter der ersten Stage von „S-Klasse Herren Einzel“. */
  async function openStageTab(page: Page, home: string, tab: string) {
    await page.goto(home);
    await page.locator('#MainView_BtnDemo').click();
    const comp = page.locator('tr', { hasText: 'S-Klasse Herren Einzel' }).first();
    await expect(comp).toBeVisible({ timeout: 15000 });
    await comp.locator('[id^="TourneyInfo_"][role="button"]').click();
    await page.locator('tr.table-light [role="button"]').first().click();
    await page.locator(`tr.table-light button[id$="-${tab}"]`).first().click();
    await expect(page.locator(`.nav-tabs .nav-link.active[href*="${tab}"]`)).toBeVisible({ timeout: 10000 });
  }

  for (const c of [
    { lang: 'de', home: '/',    single: 'Einzel',  double: 'Doppel',  heads: ['Spiele', 'Sätze', 'Bälle'],
      wins: ['2 Gewinnsätze', '3 Gewinnsätze', '4 Gewinnsätze'] },
    { lang: 'en', home: '/en/', single: 'Singles', double: 'Doubles', heads: ['Matches', 'Games', 'Points'],
      wins: ['Best of 3', 'Best of 5', 'Best of 7'] },
  ]) {
    test(`TC-SPN-01 [👤] (${c.lang}): Format der Wettbewerbe in der Turnierübersicht`, async ({ page }) => {
      await page.goto(c.home);
      await page.locator('#MainView_BtnDemo').click();
      await expect(page.locator('tr', { hasText: 'S-Klasse Herren Einzel' }).first().locator('.badge').first())
        .toHaveText(c.single, { timeout: 15000 });
      await expect(page.locator('tr', { hasText: 'S-Klasse Herren Doppel' }).first().locator('.badge').first()).toHaveText(c.double);
    });

    test(`TC-SPN-02 [👤] (${c.lang}): Tabellenkopf nennt die Stufen Spiel, Satz und Ball`, async ({ page }) => {
      await openStageTab(page, c.home, 'StageResult');
      const head = page.locator('table thead').first();
      for (const h of c.heads) await expect(head).toContainText(h);
    });

    test(`TC-SPN-03 [👤] (${c.lang}): Auswahl der Gewinnsätze aus den Varianten`, async ({ page }) => {
      await openStageTab(page, c.home, 'StageAdmin');
      const sel = page.locator('#rnd-sets');
      await expect(sel.locator('option')).toHaveText(c.wins);
      expect(await sel.locator('option').evaluateAll(os => os.map(o => (o as HTMLOptionElement).value))).toEqual(['2', '3', '4']);
      await expect(sel).toHaveValue('3');
    });
  }
});
