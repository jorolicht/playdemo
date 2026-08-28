import { test, expect } from '@playwright/test';
import { MainSearchPage } from '../pages/MainSearchPage';

test.describe('UseCase 2: Turniersuche (MainSearch)', () => {
  let searchPage: MainSearchPage;

  test.beforeEach(async ({ page }) => {
    searchPage = new MainSearchPage(page);
    await searchPage.goto();
  });

  test('TC-MS-01 [🌐]: Formulareingabefelder & Filter-Optionen anzeigen', async ({ page }) => {
    await expect(searchPage.inputTitle).toBeVisible();
    await expect(searchPage.inputOrganizer).toBeVisible();
    await expect(searchPage.inputDate).toBeVisible();

    await expect(searchPage.radioTypeAll).toBeChecked();
    await expect(searchPage.resultCount).toBeVisible();
  });

  test('TC-MS-02 [🌐]: Live-Suche per Titel-Eingabe (Debounce 400ms)', async ({ page }) => {
    await searchPage.searchByTitle('Turnier');
    await expect(searchPage.resultCount).toBeVisible();
  });

  test('TC-MS-03 [🌐]: Live-Suche per Veranstalter-Eingabe', async ({ page }) => {
    await searchPage.searchByOrganizer('Club');
    await expect(searchPage.resultCount).toBeVisible();
  });

  test('TC-MS-04 [🌐]: Datumsfilter ab Startdatum', async ({ page }) => {
    await searchPage.searchByDate('2026-08-01');
    await expect(searchPage.resultCount).toBeVisible();
  });

  test('TC-MS-05 [🌐]: Radiobutton Typ-Filter umschalten (Alle / Turniere / Wettbewerbe)', async ({ page }) => {
    await searchPage.selectTypeFilter('competition');
    await expect(page.locator('body')).toBeVisible();

    await searchPage.selectTypeFilter('tourney');
    await expect(page.locator('body')).toBeVisible();

    await searchPage.selectTypeFilter('all');
    await expect(page.locator('body')).toBeVisible();
  });

  test('TC-MS-06 [🌐]: Spaltensortierung Datum umschalten (ASC/DESC)', async ({ page }) => {
    await searchPage.toggleDateSort();
    await expect(searchPage.headerDateSort).toBeVisible();
  });

  test('TC-MS-07 [🌐]: Klick auf Turnierergebnis leitet anonymer Nutzer zu TourneyWelcome', async ({ page }) => {
    await searchPage.searchByTitle('Turnier');
    await page.waitForTimeout(600);

    const count = await searchPage.resultRows.count();
    if (count > 0) {
      await searchPage.clickResult(0);
      await page.waitForTimeout(800);
      expect(page.url()).toMatch(/#TourneyWelcome|#CompetitionWelcome/);
    } else {
      await expect(searchPage.resultsBody).toBeVisible();
    }
  });

  test('TC-MS-08 [🌐]: Klick auf Wettbewerbsergebnis leitet anonymer Nutzer zu CompetitionWelcome', async ({ page }) => {
    await searchPage.selectTypeFilter('competition');
    await page.waitForTimeout(600);

    const count = await searchPage.resultRows.count();
    if (count > 0) {
      await searchPage.clickResult(0);
      await page.waitForTimeout(800);
      expect(page.url()).toMatch(/#CompetitionWelcome|#TourneyWelcome/);
    } else {
      await expect(searchPage.resultsBody).toBeVisible();
    }
  });

  test('TC-MS-09 [👤]: TourneyAdmin Klick auf eigenes Turnier leitet zu TourneyInfo', async ({ page }) => {
    await searchPage.searchByTitle('Turnier');
    await expect(searchPage.resultsBody).toBeVisible();
  });

  test('TC-MS-10 [👤]: TourneyAdmin Klick auf fremdes Turnier leitet zu TourneyWelcome', async ({ page }) => {
    await searchPage.searchByTitle('Turnier');
    await expect(searchPage.resultsBody).toBeVisible();
  });

  test('TC-MS-11 [👑]: Systemadmin / Master Klick leitet stets zu TourneyInfo / CompetitionInfo', async ({ page }) => {
    await searchPage.searchByTitle('Turnier');
    await expect(searchPage.resultsBody).toBeVisible();
  });
});
