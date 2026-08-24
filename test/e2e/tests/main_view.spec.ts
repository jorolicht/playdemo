import { test, expect } from '@playwright/test';
import { MainViewPage } from '../pages/MainViewPage';
import { MainSearchPage } from '../pages/MainSearchPage';

test.describe('MainView - Landing Page & Home View Tests', () => {

  test('soll die Startseite ordnungsgemäß laden und Haupt-Buttons anzeigen', async ({ page }) => {
    const mainView = new MainViewPage(page);
    await mainView.goto();

    // Verifiziere Sichtbarkeit der zentralen Elemente
    await mainView.assertPageLoaded();
    await expect(mainView.btnSearch).toBeVisible();
    await expect(mainView.btnOption1).toBeVisible();
    await expect(mainView.btnOption2).toBeVisible();
  });

  test('soll beim Klick auf den Suchen-Button zur MainSearch navigieren', async ({ page }) => {
    const mainView = new MainViewPage(page);
    await mainView.goto();

    await mainView.clickSearch();

    const searchPage = new MainSearchPage(page);
    await expect(searchPage.inputTitle).toBeVisible();
    await expect(searchPage.resultCount).toBeVisible();
  });

  test('soll Marken-Logo in der Navigationsleiste anzeigen', async ({ page }) => {
    const mainView = new MainViewPage(page);
    await mainView.goto();

    await expect(mainView.brandLogo).toBeVisible();
  });

  test('soll Footer-Bereich mit Links anzeigen', async ({ page }) => {
    const mainView = new MainViewPage(page);
    await mainView.goto();

    await expect(page.locator('footer')).toBeVisible();
  });

});
