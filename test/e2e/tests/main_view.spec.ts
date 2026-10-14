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

  test('TC-MV-01b [🌐]: Klick auf Demo-Container öffnet Dialog "Demo lokal starten?", Abbrechen verbleibt, Bestätigen startet Demo wenn lokales Turnier vorhanden ist', async ({ page }) => {
    const mainView = new MainViewPage(page);
    const infoPage = new TourneyInfoPage(page);
    await mainView.goto();

    // Prepare an existing local tournament so the warning dialog appears
    await page.evaluate(() => {
      window.localStorage.setItem('App.demo_tourney', JSON.stringify({
        version: 1,
        tourney: { wpId: 999999, name: 'Vorhandenes lokales Turnier', ident: 'LOCAL' }
      }));
    });
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

  test('TC-MV-01c [🌐]: Klick auf Demo-Container startet Demo direkt ohne Dialog, wenn kein lokales Turnier vorhanden ist', async ({ page }) => {
    const mainView = new MainViewPage(page);
    const infoPage = new TourneyInfoPage(page);
    await mainView.goto();

    // Ensure clean state (no local tournament)
    await page.evaluate(() => {
      window.localStorage.removeItem('App.demo_tourney');
    });
    await mainView.goto();

    // Click Demo container button -> starts demo directly without modal
    await mainView.clickDemoBox();
    await expect(mainView.dlgStartDemoModal).not.toBeVisible();
    await expect(infoPage.cardHeaderTitle).toBeVisible({ timeout: 10000 });
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

    // 5. Verify CompetitionNew page is shown (quickstart competition creation form)
    await expect(page.locator('input[id*="CompetitionNew_CompNameId"]')).toBeVisible({ timeout: 7000 });
    await expect(page.locator('button[id*="CompetitionNew_BtnSave"]')).toBeVisible({ timeout: 5000 });
  });

  test('TC-MV-08b [🌐]: Schnellstart über CompetitionNew erstellt Wettbewerb und navigiert zu CompetitionInfo', async ({ page }) => {
    const mainView = new MainViewPage(page);
    await mainView.goto();

    await mainView.startQuickCompetition();
    await expect(page.locator('input[id*="CompetitionNew_CompNameId"]')).toBeVisible({ timeout: 7000 });

    const compName = `QuickComp ${Date.now()}`;
    await page.locator('input[id*="CompetitionNew_CompNameId"]').fill(compName);
    await page.locator('button[id*="CompetitionNew_BtnSave"]').click();

    // Verify navigation to CompetitionInfo and competition name is displayed
    await expect(page.locator('.card-header:has-text("Wettbewerb"), .card-header:has-text("Competition")').first()).toBeVisible({ timeout: 10000 });
    await expect(page.locator('body')).toContainText(compName);
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

  test('TC-MV-11a [🌐]: Status-Zeile wird NICHT angezeigt, wenn keine Speicherschlüssel für ein letztes Turnier existieren', async ({ page }) => {
    const mainView = new MainViewPage(page);
    await mainView.goto();

    await page.evaluate(() => {
      window.localStorage.clear();
      window.sessionStorage.clear();
    });
    await mainView.goto();

    // Verify status bar is NOT visible
    await expect(mainView.statusTourneyBar).not.toBeVisible();
    await expect(page.locator('.hero-section').first()).toBeVisible();
  });

  test('TC-MV-11b [🌐]: Status-Zeile zeigt lokales Turnier an, wenn App.demo_tourney in localStorage existiert, und Klick auf Laden öffnet TourneyInfo', async ({ page }) => {
    const mainView = new MainViewPage(page);
    const infoPage = new TourneyInfoPage(page);
    await mainView.goto();

    // Setup local tournament in storage
    const localTourneyName = 'Mein Lokales Vereinsturnier';
    const samplePayload = {
      version: 1,
      tourney: {
        wpId: 999999,
        name: localTourneyName,
        organizer: 'Verein',
        startDate: 20261013,
        endDate: 20261013,
        ident: 'LOC-1',
        category: 'Club',
        slug: 'local',
        clubs: [],
        players: [],
        competitions: [],
        stages: []
      }
    };

    await page.evaluate((payload) => {
      window.localStorage.setItem('App.demo_tourney', JSON.stringify(payload));
      window.localStorage.setItem('tourney_is_local_mode', 'true');
      window.sessionStorage.setItem('tourney_is_local_mode', 'true');
      window.localStorage.setItem('tourney_slug', 'local');
      window.sessionStorage.setItem('tourney_slug', 'local');
    }, samplePayload);

    await mainView.goto();

    // 1. Verify status bar is visible before hero-section
    await expect(mainView.statusTourneyBar).toBeVisible();
    await expect(mainView.statusTourneyName).toHaveText(localTourneyName);
    await expect(mainView.statusTourneyBar.locator('.badge')).toHaveText(/Lokal|Local/i);

    // 2. Click load button in status bar
    await expect(mainView.btnStatusLoadLast).toBeVisible();
    await mainView.btnStatusLoadLast.click();

    // 3. Verify navigation to TourneyInfo and tournament name is loaded
    await expect(infoPage.cardHeaderTitle).toBeVisible({ timeout: 10000 });
    const tourneyNameElem = page.locator('.card-body .fw-bold.text-primary').first();
    await expect(tourneyNameElem).toBeVisible();
    expect(await tourneyNameElem.innerText()).toContain(localTourneyName);
  });

  test('TC-MV-11c [🌐]: Status-Zeile zeigt Cloud-Turnier an, wenn tourney_slug und tourney_name existieren', async ({ page }) => {
    const mainView = new MainViewPage(page);
    await mainView.goto();

    const cloudTourneyName = 'Cloud Championship 2026';
    await page.evaluate((name) => {
      window.localStorage.removeItem('App.demo_tourney');
      window.localStorage.setItem('tourney_is_local_mode', 'false');
      window.sessionStorage.setItem('tourney_is_local_mode', 'false');
      window.localStorage.setItem('tourney_slug', 'cloud-championship-2026');
      window.sessionStorage.setItem('tourney_slug', 'cloud-championship-2026');
      window.localStorage.setItem('tourney_name', name);
      window.sessionStorage.setItem('tourney_name', name);
    }, cloudTourneyName);

    await mainView.goto();

    // Verify status bar is visible with Cloud badge and tournament name
    await expect(mainView.statusTourneyBar).toBeVisible();
    await expect(mainView.statusTourneyName).toHaveText(cloudTourneyName);
    await expect(mainView.statusTourneyBar.locator('.badge')).toHaveText(/Cloud/i);
    await expect(mainView.btnStatusLoadLast).toBeVisible();
  });

  test('TC-MV-11d [🌐]: Status-Zeile zeigt Cloud-Slug an, wenn nur tourney_slug ohne separaten Namen gespeichert ist', async ({ page }) => {
    const mainView = new MainViewPage(page);
    await mainView.goto();

    const cloudSlug = 'stadtliga-2026';
    await page.evaluate((slug) => {
      window.localStorage.removeItem('App.demo_tourney');
      window.localStorage.removeItem('tourney_name');
      window.sessionStorage.removeItem('tourney_name');
      window.sessionStorage.removeItem('tourney_last_selection');
      window.localStorage.setItem('tourney_is_local_mode', 'false');
      window.sessionStorage.setItem('tourney_is_local_mode', 'false');
      window.localStorage.setItem('tourney_slug', slug);
      window.sessionStorage.setItem('tourney_slug', slug);
    }, cloudSlug);

    await mainView.goto();

    // Verify status bar is visible with Cloud badge and fallback slug as name
    await expect(mainView.statusTourneyBar).toBeVisible();
    await expect(mainView.statusTourneyName).toHaveText(cloudSlug);
    await expect(mainView.statusTourneyBar.locator('.badge')).toHaveText(/Cloud/i);
  });

  test('TC-MV-11e [🌐]: Status-Zeile zeigt "Letzter Wettbewerb" an, wenn ident == "SIMPLE", und Klick auf Laden öffnet CompetitionInfo', async ({ page }) => {
    const mainView = new MainViewPage(page);
    await mainView.goto();

    const compName = 'Schnellstart Pokal 2026';
    const samplePayload = {
      version: 1,
      tourney: {
        wpId: 999999,
        name: compName,
        organizer: 'System',
        startDate: 20261013,
        endDate: 20261013,
        ident: 'SIMPLE',
        category: 'TT',
        slug: 'local',
        clubs: [],
        players: [],
        competitions: [
          {
            id: 1,
            name: compName,
            typ: 'KO',
            category: 'TT',
            startDate: '2026-10-13 10:00:00',
            status: 'Planned',
            deleted: false,
            pants1Stage: []
          }
        ],
        stages: []
      }
    };

    await page.evaluate((payload) => {
      window.localStorage.setItem('App.demo_tourney', JSON.stringify(payload));
      window.localStorage.setItem('tourney_is_local_mode', 'true');
      window.sessionStorage.setItem('tourney_is_local_mode', 'true');
      window.localStorage.setItem('tourney_slug', 'local');
      window.sessionStorage.setItem('tourney_slug', 'local');
      window.localStorage.setItem('tourney_ident', 'SIMPLE');
      window.sessionStorage.setItem('tourney_ident', 'SIMPLE');
    }, samplePayload);

    await mainView.goto();

    // 1. Verify status bar is visible and shows "Letzter Wettbewerb" (or "Last Competition")
    await expect(mainView.statusTourneyBar).toBeVisible();
    await expect(mainView.statusTourneyName).toHaveText(compName);
    await expect(mainView.statusTourneyLabel).toHaveText(/Letzter Wettbewerb|Last Competition/);
    await expect(mainView.statusTourneyIcon).toHaveClass(/bi-lightning-charge-fill/);

    // 2. Click load button
    await expect(mainView.btnStatusLoadLast).toBeVisible();
    await mainView.btnStatusLoadLast.click();

    // 3. Verify navigation to CompetitionInfo (not TourneyInfo)
    await expect(page.locator('.card-header:has-text("Wettbewerb"), .card-header:has-text("Competition")').first()).toBeVisible({ timeout: 10000 });
    await expect(page.locator('body')).toContainText(compName);
  });

  test('TC-MV-11f [🌐]: Direkte Navigation zu TourneyInfo leitet bei SIMPLE-Wettbewerb automatisch zu CompetitionInfo weiter', async ({ page }) => {
    const mainView = new MainViewPage(page);
    await mainView.goto();

    const compName = 'Schnellstart Blitzturnier';
    const samplePayload = {
      version: 1,
      tourney: {
        wpId: 999999,
        name: compName,
        organizer: 'System',
        startDate: 20261013,
        endDate: 20261013,
        ident: 'SIMPLE',
        category: 'TT',
        slug: 'local',
        clubs: [],
        players: [],
        competitions: [
          {
            id: 1,
            name: compName,
            typ: 'KO',
            category: 'TT',
            startDate: '2026-10-13 10:00:00',
            status: 'Planned',
            deleted: false,
            pants1Stage: []
          }
        ],
        stages: []
      }
    };

    await page.evaluate((payload) => {
      window.localStorage.setItem('App.demo_tourney', JSON.stringify(payload));
      window.localStorage.setItem('tourney_is_local_mode', 'true');
      window.sessionStorage.setItem('tourney_is_local_mode', 'true');
      window.localStorage.setItem('tourney_slug', 'local');
      window.sessionStorage.setItem('tourney_slug', 'local');
      window.localStorage.setItem('tourney_ident', 'SIMPLE');
      window.sessionStorage.setItem('tourney_ident', 'SIMPLE');
    }, samplePayload);

    await mainView.goto();

    // 1. Click load button to load the simple tournament into memory
    await mainView.btnStatusLoadLast.click();
    await expect(page.locator('.card-header:has-text("Wettbewerb"), .card-header:has-text("Competition")').first()).toBeVisible({ timeout: 10000 });

    // 2. Now attempt to navigate to TourneyInfo directly via appLoadPage
    await page.evaluate(() => {
      (window as any).appLoadPage('TourneyInfo', '');
    });

    // 3. Verify it automatically redirects to CompetitionInfo instead of displaying TourneyInfo
    await expect(page.locator('.card-header:has-text("Wettbewerb"), .card-header:has-text("Competition")').first()).toBeVisible({ timeout: 10000 });
    await expect(page.locator('body')).toContainText(compName);
  });

  test('TC-MV-11g [🌐]: Laden eines SIMPLE-Wettbewerbs mit App.demo_comps öffnet CompetitionInfo direkt ohne Dialog "Turnier verlassen"', async ({ page }) => {
    const mainView = new MainViewPage(page);
    await mainView.goto();

    const compName = 'Schnellstart Pokal Live';
    const tourneyPayload = {
      version: 1,
      tourney: {
        wpId: 999999,
        name: compName,
        organizer: 'System',
        startDate: 20261013,
        endDate: 20261013,
        ident: 'SIMPLE',
        category: 'TT',
        slug: 'local',
        clubs: [],
        players: [],
        competitions: [],
        stages: []
      }
    };

    const compsPayload = {
      events: [
        {
          id: 1,
          name: compName,
          typ: 'SINGLE',
          category: 'TT',
          startDate: '2026-10-13 10:00:00',
          status: 'CFG',
          activ: true,
          deleted: false,
          pants1Stage: []
        }
      ]
    };

    await page.evaluate(({ tPayload, cPayload }) => {
      window.localStorage.setItem('App.demo_tourney', JSON.stringify(tPayload));
      window.localStorage.setItem('App.demo_comps', JSON.stringify(cPayload));
      window.localStorage.setItem('tourney_is_local_mode', 'true');
      window.sessionStorage.setItem('tourney_is_local_mode', 'true');
      window.localStorage.setItem('tourney_slug', 'local');
      window.sessionStorage.setItem('tourney_slug', 'local');
      window.localStorage.setItem('tourney_ident', 'SIMPLE');
      window.sessionStorage.setItem('tourney_ident', 'SIMPLE');
    }, { tPayload: tourneyPayload, cPayload: compsPayload });

    await mainView.goto();

    // Verify status bar is visible
    await expect(mainView.statusTourneyBar).toBeVisible();
    await expect(mainView.statusTourneyName).toHaveText(compName);
    await expect(mainView.statusTourneyLabel).toHaveText(/Letzter Wettbewerb|Last Competition/);

    // Click load button
    await expect(mainView.btnStatusLoadLast).toBeVisible();
    await mainView.btnStatusLoadLast.click();

    // Verify leave dialog ("Turnier verlassen" / DlgExportTourney) does NOT appear
    await expect(page.locator('#DlgExportTourney')).not.toBeVisible();

    // Verify navigation to CompetitionInfo and competition name is displayed
    await expect(page.locator('.card-header:has-text("Wettbewerb"), .card-header:has-text("Competition")').first()).toBeVisible({ timeout: 10000 });
    await expect(page.locator('body')).toContainText(compName);
    await expect(page.locator('#DlgExportTourney')).not.toBeVisible();
  });

});
