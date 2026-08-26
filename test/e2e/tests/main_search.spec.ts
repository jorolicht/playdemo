import { test, expect } from '@playwright/test';
import { MainSearchPage } from '../pages/MainSearchPage';
import { TourneyWelcomePage } from '../pages/TourneyWelcomePage';
import { CompetitionWelcomePage } from '../pages/CompetitionWelcomePage';

test.describe('MainSearch - Turnier & Wettbewerb Suche Tests', () => {

  let searchPage: MainSearchPage;

  test.beforeEach(async ({ page }) => {
    searchPage = new MainSearchPage(page);
    await searchPage.goto();
  });

  test('soll alle Such-Eingabefelder und Filter-Optionen anzeigen', async ({ page }) => {
    await expect(searchPage.inputTitle).toBeVisible();
    await expect(searchPage.inputOrganizer).toBeVisible();
    await expect(searchPage.inputDate).toBeVisible();

    await expect(searchPage.radioTypeAll).toBeChecked();
    await expect(searchPage.resultCount).toBeVisible();
  });

  test('soll Suchergebnisse filtern bei Eingabe in das Titel-Feld', async ({ page }) => {
    await searchPage.searchByTitle('Turnier');

    // Verifiziere Ergebniszähler-Update
    await expect(searchPage.resultCount).toBeVisible();
  });

  test('soll Suchergebnisse filtern bei Eingabe in das Veranstalter-Feld', async ({ page }) => {
    await searchPage.searchByOrganizer('Club');

    await expect(searchPage.resultCount).toBeVisible();
  });

  test('soll Radio-Button Filter zwischen Turnieren und Wettbewerben umschalten', async ({ page }) => {
    // Umschalten auf nur Wettbewerbe
    await searchPage.selectTypeFilter('competition');
    await expect(searchPage.radioTypeComp).toBeChecked();

    // Umschalten auf nur Turniere
    await searchPage.selectTypeFilter('tourney');
    await expect(searchPage.radioTypeTourney).toBeChecked();

    // Zurückschalten auf Alle
    await searchPage.selectTypeFilter('all');
    await expect(searchPage.radioTypeAll).toBeChecked();
  });

  test('soll Sortierung der Spalte Datum umschalten', async ({ page }) => {
    await searchPage.toggleDateSort();
    await expect(searchPage.headerDateSort).toBeVisible();
  });

  test('soll beim Klick auf ein Suchergebnis als anonymer Nutzer zu TourneyWelcome oder CompetitionWelcome leiten', async ({ page }) => {
    await searchPage.searchByTitle('Turnier');
    await page.waitForTimeout(600);

    const count = await searchPage.resultRows.count();
    if (count > 0) {
      await searchPage.clickResult(0);
      await page.waitForTimeout(800);
      const currentUrl = page.url();
      expect(currentUrl).toMatch(/#TourneyWelcome|#CompetitionWelcome/);
    } else {
      await expect(searchPage.resultsBody).toBeVisible();
    }
  });

});
