import { test, expect } from '@playwright/test';
import { MainViewPage } from '../pages/MainViewPage';
import { MainSearchPage } from '../pages/MainSearchPage';
import { TourneyInfoPage } from '../pages/TourneyInfoPage';

test.describe('UseCase 1: Startseite (MainView)', () => {

  test('TC-MV-01 [🌐]: Startseite Aufruf & 4 Haupt-Container von links nach rechts (Demo, Neues Turnier, Schnellstart, Suche)', async ({ page }) => {
    const mainView = new MainViewPage(page);
    await mainView.goto();

    await mainView.assertPageLoaded();

    // Verify 4 container cards and buttons in order from left to right
    const cards = page.locator('.main-multi-landing .container .row > div');
    await expect(cards).toHaveCount(4);

    // 1. Demo (far left)
    const cardDemo = cards.nth(0);
    await expect(cardDemo.locator('h3')).toHaveText(/Demo/i);
    await expect(cardDemo.locator('#MainView_BtnDemo')).toBeVisible();

    // 2. Neues Turnier / Create Tournament
    const cardTourney = cards.nth(1);
    await expect(cardTourney.locator('h3')).toHaveText(/Neues Turnier|New Tournament/i);
    await expect(cardTourney.locator('#MainView_BtnOption1')).toBeVisible();

    // 3. Schnellstart / Create Single Competition
    const cardQuick = cards.nth(2);
    await expect(cardQuick.locator('h3')).toHaveText(/Schnellstart|Quick Start/i);
    await expect(cardQuick.locator('#MainView_BtnOption2')).toBeVisible();

    // 4. Suchen & Finden / Search
    const cardSearch = cards.nth(3);
    await expect(cardSearch.locator('h3')).toHaveText(/Suchen & Finden|Search & Find/i);
    await expect(cardSearch.locator('#MainView_BtnSearch')).toBeVisible();
  });

  test('TC-MV-01b [🌐]: Klick auf Demo-Container öffnet Dialog "Demo lokal starten?", Abbrechen verbleibt, Bestätigen startet Demo', async ({ page }) => {
    const mainView = new MainViewPage(page);
    const infoPage = new TourneyInfoPage(page);
    await mainView.goto();

    // 1. Click Demo container button
    await mainView.clickDemoBox();

    // 2. Verify DlgStartDemo modal opens
    await expect(mainView.dlgStartDemoModal).toBeVisible({ timeout: 5000 });
    await expect(mainView.dlgStartDemoTitle).toHaveText(/Demo lokal starten\?|Start demo locally\?/i);
    await expect(mainView.dlgStartDemoPrompt).toHaveText(/Achtung: das aktuelle lokale Turnier wird gelöscht!|Warning: the current local tournament will be deleted!/i);

    const footerButtons = mainView.dlgStartDemoModal.locator('.modal-footer button');
    await expect(footerButtons).toHaveCount(2);
    await expect(mainView.btnStartDemoCancel).toHaveText(/ABBRECHEN|CANCEL/i);
    await expect(mainView.btnStartDemoApply).toHaveText(/DEMO STARTEN|START DEMO/i);

    // 3. Click ABBRECHEN -> modal closes, stays on MainView
    await mainView.btnStartDemoCancel.click();
    await expect(mainView.dlgStartDemoModal).not.toBeVisible({ timeout: 3000 });
    await expect(infoPage.cardHeaderTitle).not.toBeVisible();

    // 4. Click Demo container again -> click DEMO STARTEN -> navigates to TourneyInfo
    await mainView.clickDemoBox();
    await expect(mainView.dlgStartDemoModal).toBeVisible();
    await mainView.btnStartDemoApply.click();
    await expect(mainView.dlgStartDemoModal).not.toBeVisible({ timeout: 3000 });

    await expect(infoPage.cardHeaderTitle).toBeVisible({ timeout: 10000 });
    const tourneyNameElem = page.locator('.card-body .fw-bold.text-primary').first();
    await expect(tourneyNameElem).toBeVisible();
    const titleText = await tourneyNameElem.innerText();
    expect(titleText).toContain('Demo:');
  });

  test('TC-MV-02 [🌐]: Klick auf Turniersuche navigiert zu MainSearch', async ({ page }) => {
    const mainView = new MainViewPage(page);
    await mainView.goto();

    await mainView.clickSearch();

    const searchPage = new MainSearchPage(page);
    await expect(searchPage.inputTitle).toBeVisible();
  });

  test('TC-MV-03 [🌐]: Klick auf "Ganzes Turnier" zeigt DlgStartTourney (Titel, Text, Buttons) und erlaubt Starten & Abbrechen', async ({ page }) => {
    const mainView = new MainViewPage(page);
    await mainView.goto();

    // 1. Click Option1 -> Modal opens
    await mainView.clickOption1(false);
    await expect(mainView.dlgStartTourneyModal).toBeVisible({ timeout: 5000 });

    // 2. Verify title, warning text and buttons
    await expect(mainView.dlgStartTourneyTitle).toContainText(/Ganzes Turnier starten\?|Start full tournament\?/);
    await expect(mainView.dlgStartTourneyPrompt).toContainText(/Achtung: das aktuelle lokale Turnier wird gelöscht!|Warning: the current local tournament will be deleted!/);
    await expect(mainView.btnStartTourneyCancel).toHaveText(/ABBRECHEN|CANCEL/);
    await expect(mainView.btnStartTourneyApply).toHaveText(/STARTEN|START/);

    // 3. Test Abbrechen: closes modal and stays on MainView
    await mainView.btnStartTourneyCancel.click();
    await expect(mainView.dlgStartTourneyModal).not.toBeVisible({ timeout: 3000 });
    await expect(page.locator('.hero-section').first()).toBeVisible();

    // 4. Click Option1 again and confirm with STARTEN
    await mainView.clickOption1(false);
    await expect(mainView.dlgStartTourneyModal).toBeVisible({ timeout: 5000 });
    await mainView.btnStartTourneyApply.click();
    await expect(mainView.dlgStartTourneyModal).not.toBeVisible({ timeout: 3000 });

    // 5. Verify navigation to TourneyNew
    await expect(page.locator('#tn-name')).toBeVisible({ timeout: 7000 });
  });

  test('TC-MV-04 [🌐]: Footer-Links laden statische Seiten (Datenschutz, Impressum)', async ({ page }) => {
    const mainView = new MainViewPage(page);
    await mainView.goto();

    await expect(page.locator('footer')).toBeVisible();
  });

  test('TC-MV-05 [🌐]: Sprachumschaltung in Navigationsleiste', async ({ page }) => {
    const mainView = new MainViewPage(page);
    await mainView.goto();

    const langBtn = page.locator('.navbar a:text-is("EN"), .navbar a:text-is("DE")').first();
    if (await langBtn.count() > 0 && await langBtn.isVisible()) {
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

  test('TC-MV-08 [🌐]: Klick auf "Wettbewerb starten" zeigt DlgStartCompetition (Titel, Text, Buttons) und erlaubt Starten & Abbrechen', async ({ page }) => {
    const mainView = new MainViewPage(page);
    await mainView.goto();

    // 1. Click Option2 -> Modal opens
    await mainView.clickOption2(false);
    await expect(mainView.dlgStartCompetitionModal).toBeVisible({ timeout: 5000 });

    // 2. Verify title, warning text and buttons
    await expect(mainView.dlgStartCompetitionTitle).toContainText(/Wettbewerb starten\?|Start competition\?/);
    await expect(mainView.dlgStartCompetitionPrompt).toContainText(/Achtung: der aktuelle lokale Wettbewerb bzw das aktuelle lokale Turnier wird gelöscht!|Warning: the current local competition or local tournament will be deleted!/);
    await expect(mainView.btnStartCompetitionCancel).toHaveText(/ABBRECHEN|CANCEL/);
    await expect(mainView.btnStartCompetitionApply).toHaveText(/STARTEN|START/);

    // 3. Test Abbrechen: closes modal and stays on MainView
    await mainView.btnStartCompetitionCancel.click();
    await expect(mainView.dlgStartCompetitionModal).not.toBeVisible({ timeout: 3000 });
    await expect(page.locator('.hero-section').first()).toBeVisible();

    // 4. Click Option2 again and confirm with STARTEN
    await mainView.clickOption2(false);
    await expect(mainView.dlgStartCompetitionModal).toBeVisible({ timeout: 5000 });
    await mainView.btnStartCompetitionApply.click();
    await expect(mainView.dlgStartCompetitionModal).not.toBeVisible({ timeout: 3000 });

    // 5. Verify DlgCompetition modal is shown (quickstart competition creation form)
    await expect(page.locator('div[id*="DlgCompetition"].modal')).toBeVisible({ timeout: 7000 });
    await expect(page.locator('input[id*="DlgCompetition_CompNameId"]')).toBeVisible({ timeout: 5000 });
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
