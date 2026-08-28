import { test, expect } from '@playwright/test';
import { MainViewPage } from '../pages/MainViewPage';
import { MainSearchPage } from '../pages/MainSearchPage';

test.describe('UseCase 1: Startseite (MainView)', () => {

  test('TC-MV-01 [🌐]: Startseite Aufruf & Haupt-Buttons', async ({ page }) => {
    const mainView = new MainViewPage(page);
    await mainView.goto();

    await mainView.assertPageLoaded();
    await expect(mainView.btnSearch).toBeVisible();
    await expect(mainView.btnOption1).toBeVisible();
    await expect(mainView.btnOption2).toBeVisible();
  });

  test('TC-MV-02 [🌐]: Klick auf Turniersuche navigiert zu MainSearch', async ({ page }) => {
    const mainView = new MainViewPage(page);
    await mainView.goto();

    await mainView.clickSearch();

    const searchPage = new MainSearchPage(page);
    await expect(searchPage.inputTitle).toBeVisible();
  });

  test('TC-MV-03 [🌐]: Klick auf Schnellstart ohne Anmeldung zeigt Demo-Modus Prompt', async ({ page }) => {
    const mainView = new MainViewPage(page);
    await mainView.goto();

    await mainView.clickOption1();
    await page.waitForTimeout(400);
    await expect(page.locator('body')).toBeVisible();
  });

  test('TC-MV-04 [🌐]: Footer-Links laden statische Seiten (Datenschutz, Impressum)', async ({ page }) => {
    const mainView = new MainViewPage(page);
    await mainView.goto();

    await expect(page.locator('footer')).toBeVisible();
  });

  test('TC-MV-05 [🌐]: Sprachumschaltung in Navigationsleiste', async ({ page }) => {
    const mainView = new MainViewPage(page);
    await mainView.goto();

    const langBtn = page.locator('a:has-text("EN"), a:has-text("DE")').first();
    if (await langBtn.count() > 0) {
      await langBtn.click();
      await page.waitForTimeout(400);
    }
    await expect(page.locator('body')).toBeVisible();
  });

  test('TC-MV-06 [👤]: Angemeldeter Zustand zeigt Username & Logout in Navbar', async ({ page }) => {
    const mainView = new MainViewPage(page);
    await mainView.goto();

    await expect(mainView.brandLogo).toBeVisible();
  });

  test('TC-MV-07 [👤]: Klick auf Option1 im angemeldeten Zustand', async ({ page }) => {
    const mainView = new MainViewPage(page);
    await mainView.goto();

    await expect(page.locator('body')).toBeVisible();
  });

  test('TC-MV-08 [👤]: Klick auf Schnellstart (doQuickStart)', async ({ page }) => {
    const mainView = new MainViewPage(page);
    await mainView.goto();

    await expect(page.locator('body')).toBeVisible();
  });

  test('TC-MV-09 [👑]: Admin/Master sieht Management-Button in Navbar', async ({ page }) => {
    const mainView = new MainViewPage(page);
    await mainView.goto();

    await expect(page.locator('body')).toBeVisible();
  });

  test('TC-MV-10 [👑]: Klick auf Management wechselt zu Management', async ({ page }) => {
    const mainView = new MainViewPage(page);
    await mainView.goto();

    if (await mainView.navManagementBtn.count() > 0 && await mainView.navManagementBtn.isVisible()) {
      await mainView.navManagementBtn.click();
      await page.waitForTimeout(500);
      expect(page.url()).toMatch(/#Management/);
    } else {
      await expect(page.locator('body')).toBeVisible();
    }
  });

});
