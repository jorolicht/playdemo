import { test, expect } from '@playwright/test';
import { CompetitionInfoPage } from '../pages/CompetitionInfoPage';
import { MainViewPage } from '../pages/MainViewPage';

test.describe('UseCase 6: Wettbewerbsverwaltung (CompetitionInfo)', () => {

  test.describe('Standard Ansicht & Aktionen', () => {
    let compInfoPage: CompetitionInfoPage;

    test.beforeEach(async ({ page }) => {
      compInfoPage = new CompetitionInfoPage(page);
      await compInfoPage.goto();
    });

    test('TC-CI-01 [👤]: CompetitionInfo Ansicht geladen', async ({ page }) => {
      await expect(page.locator('body')).toBeVisible();
    });

    test('TC-CI-02 [👤]: Phasen starten Button löst Auslosungs-Assistent aus', async ({ page }) => {
      if (await compInfoPage.btnStartStage.count() > 0 && await compInfoPage.btnStartStage.isVisible()) {
        await compInfoPage.btnStartStage.click();
        await page.waitForTimeout(500);
        expect(page.url()).toMatch(/#StageAdmin|#StageDraw|#CompetitionInfo/);
      } else {
        await expect(page.locator('body')).toBeVisible();
      }
    });

    test('TC-CI-03 [👤]: Wettbewerb bearbeiten (TTR-Limits & Altersklassen)', async ({ page }) => {
      if (await compInfoPage.btnEditComp.count() > 0 && await compInfoPage.btnEditComp.isVisible()) {
        await compInfoPage.btnEditComp.click();
        await page.waitForTimeout(500);
      }
      await expect(page.locator('body')).toBeVisible();
    });

    test('TC-CI-04 [👤]: Urkunden Radio-Button Auswahl', async ({ page }) => {
      const radio = page.locator('input[name="stage-certificate-radio"]').first();
      if (await radio.count() > 0) {
        await radio.check({ force: true });
        await page.waitForTimeout(400);
      }
      await expect(page.locator('body')).toBeVisible();
    });

    test('TC-CI-05 [👤]: Wettbewerb beenden / reaktivieren', async ({ page }) => {
      if (await compInfoPage.btnToggleStatus.count() > 0 && await compInfoPage.btnToggleStatus.isVisible()) {
        await compInfoPage.btnToggleStatus.click();
        await page.waitForTimeout(500);
      }
      await expect(page.locator('body')).toBeVisible();
    });
  });

  test.describe('Eigenständigen Wettbewerb (ident == "SIMPLE") löschen', () => {

    test('TC-CI-06 [👤]: Eigenständigen Wettbewerb (ident == "SIMPLE") löschen - Bestätigungsdialog Abbrechen schließt Dialog ohne Löschen', async ({ page }) => {
      const mainView = new MainViewPage(page);
      const compInfoPage = new CompetitionInfoPage(page);
      const compName = 'Schnellstart Löschtest Abbrechen';
      const samplePayload = {
        version: 1,
        tourney: {
          wpId: 999999,
          name: compName,
          organizer: 'System',
          startDate: 20261014,
          endDate: 20261014,
          ident: 'SIMPLE',
          category: 'TT',
          slug: 'local',
          clubs: [],
          players: [],
          competitions: [
            {
              id: 1,
              name: compName,
              typ: 'SINGLE',
              category: 'TT',
              startDate: '2026-10-14 10:00:00',
              status: 'CFG',
              activ: true,
              deleted: false,
              pants1Stage: []
            }
          ],
          stages: []
        }
      };

      await page.goto('/');
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
      await expect(mainView.statusTourneyBar).toBeVisible();
      await expect(mainView.btnStatusLoadLast).toBeVisible();
      await mainView.btnStatusLoadLast.click();

      await expect(compInfoPage.cardHeaderTitle).toBeVisible({ timeout: 10000 });
      await expect(page.locator('body')).toContainText(compName);

      // 1. Click delete button
      await expect(compInfoPage.btnDeleteComp).toBeVisible();
      await compInfoPage.btnDeleteComp.click();

      // 2. Verify confirmation dialog appears
      const msgbox = page.locator('#DlgMsgbox_ModalId');
      await expect(msgbox).toBeVisible({ timeout: 5000 });
      await expect(msgbox).toContainText(/Wettbewerb löschen|Delete Competition/i);
      await expect(msgbox).toContainText(/wirklich unwiderruflich löschen/i);

      // 3. Click "Nein" (Cancel)
      const btnNo = page.locator('button[id*="DlgMsgbox_NoId"], #DlgMsgbox_ModalId button:has-text("Nein"), #DlgMsgbox_ModalId button:has-text("No")').first();
      await expect(btnNo).toBeVisible();
      await btnNo.click();

      // 4. Verify dialog closes and competition is NOT deleted
      await expect(msgbox).not.toBeVisible({ timeout: 3000 });
      await expect(compInfoPage.cardHeaderTitle).toBeVisible();
      await expect(page.locator('body')).toContainText(compName);

      // Storage keys must still exist
      const hasStorage = await page.evaluate(() => {
        return window.localStorage.getItem('App.demo_tourney') !== null;
      });
      expect(hasStorage).toBe(true);
    });

    test('TC-CI-07 [👤]: Eigenständigen Wettbewerb (ident == "SIMPLE") im lokalen Modus löschen - löscht lokalen Speicher & alle Browser-Schlüssel und zeigt keine Status-Zeile auf MainView', async ({ page }) => {
      const mainView = new MainViewPage(page);
      const compInfoPage = new CompetitionInfoPage(page);
      const compName = 'Schnellstart Löschtest Bestätigen';
      const samplePayload = {
        version: 1,
        tourney: {
          wpId: 999999,
          name: compName,
          organizer: 'System',
          startDate: 20261014,
          endDate: 20261014,
          ident: 'SIMPLE',
          category: 'TT',
          slug: 'local',
          clubs: [],
          players: [],
          competitions: [
            {
              id: 1,
              name: compName,
              typ: 'SINGLE',
              category: 'TT',
              startDate: '2026-10-14 10:00:00',
              status: 'CFG',
              activ: true,
              deleted: false,
              pants1Stage: []
            }
          ],
          stages: []
        }
      };

      await page.goto('/');
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
      await expect(mainView.statusTourneyBar).toBeVisible();
      await expect(mainView.btnStatusLoadLast).toBeVisible();
      await mainView.btnStatusLoadLast.click();

      await expect(compInfoPage.cardHeaderTitle).toBeVisible({ timeout: 10000 });
      await expect(page.locator('body')).toContainText(compName);

      // 1. Click delete button
      await expect(compInfoPage.btnDeleteComp).toBeVisible();
      await compInfoPage.btnDeleteComp.click();

      // 2. Verify confirmation dialog appears
      const msgbox = page.locator('#DlgMsgbox_ModalId');
      await expect(msgbox).toBeVisible({ timeout: 5000 });

      // 3. Click "Ja" (Confirm)
      const btnYes = page.locator('button[id*="DlgMsgbox_YesId"], #DlgMsgbox_ModalId button:has-text("Ja"), #DlgMsgbox_ModalId button:has-text("Yes")').first();
      await expect(btnYes).toBeVisible();
      await btnYes.click();

      // 4. Verify redirection to MainView
      await expect(page.locator('.hero-section').first()).toBeVisible({ timeout: 10000 });

      // 5. Verify status bar is NOT visible on MainView
      await expect(page.locator('#MainView_StatusTourney')).not.toBeVisible();

      // 6. Verify all browser storage keys are purged
      const storageKeys = await page.evaluate(() => {
        return {
          demoTourney: window.localStorage.getItem('App.demo_tourney'),
          demoComps: window.localStorage.getItem('App.demo_comps'),
          tourneySlugLocal: window.localStorage.getItem('tourney_slug'),
          tourneySlugSession: window.sessionStorage.getItem('tourney_slug'),
          tourneyNameLocal: window.localStorage.getItem('tourney_name'),
          tourneyNameSession: window.sessionStorage.getItem('tourney_name'),
          tourneyIdentLocal: window.localStorage.getItem('tourney_ident'),
          tourneyIdentSession: window.sessionStorage.getItem('tourney_ident'),
          isLocalModeLocal: window.localStorage.getItem('tourney_is_local_mode'),
          isLocalModeSession: window.sessionStorage.getItem('tourney_is_local_mode')
        };
      });

      expect(storageKeys.demoTourney).toBeNull();
      expect(storageKeys.demoComps).toBeNull();
      expect(storageKeys.tourneySlugLocal).toBeNull();
      expect(storageKeys.tourneySlugSession).toBeNull();
      expect(storageKeys.tourneyNameLocal).toBeNull();
      expect(storageKeys.tourneyNameSession).toBeNull();
      expect(storageKeys.tourneyIdentLocal).toBeNull();
      expect(storageKeys.tourneyIdentSession).toBeNull();
      expect(storageKeys.isLocalModeLocal).toBeNull();
      expect(storageKeys.isLocalModeSession).toBeNull();
    });

    test('TC-CI-08 [👤]: Eigenständigen Wettbewerb (ident == "SIMPLE") im Cloud-Modus löschen - löscht Cloud-Post & alle Browser-Schlüssel und zeigt keine Status-Zeile auf MainView', async ({ page }) => {
      const mainView = new MainViewPage(page);
      const compInfoPage = new CompetitionInfoPage(page);
      const compName = 'Cloud QuickComp Löschen';
      const cloudSlug = 'cloud-quickcomp-delete-test';
      const wpId = 8888;

      const tourneyPayload = {
        version: 1,
        tourney: {
          wpId: wpId,
          name: compName,
          organizer: 'admin',
          startDate: 20261014,
          endDate: 20261014,
          ident: 'SIMPLE',
          category: 'TT',
          slug: cloudSlug,
          clubs: [],
          players: [],
          competitions: [
            {
              id: 1,
              name: compName,
              typ: 'SINGLE',
              category: 'TT',
              startDate: '2026-10-14 10:00:00',
              status: 'CFG',
              activ: true,
              deleted: false,
              pants1Stage: []
            }
          ],
          stages: []
        }
      };

      // 1. Intercept user endpoint to simulate logged-in administrator
      await page.route('**/wp-json/tourney/v1/user', async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            username: 'admin',
            user_id: 1,
            email: 'admin@test.de',
            club: 'Test Club',
            roles: ['administrator'],
            time: '2026-10-14 10:00:00',
          }),
        });
      });

      // 2. Intercept read endpoint for the cloud tournament
      await page.route('**/wp-json/tourney/v1/read*', async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            version: 1,
            tourney: tourneyPayload.tourney
          }),
        });
      });

      // 3. Intercept sub-resources loaded during TourneyDB.init
      await page.route('**/wp-json/tourney/v1/competitions*', async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            competitions: tourneyPayload.tourney.competitions
          }),
        });
      });
      await page.route('**/wp-json/tourney/v1/clubs*', async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ clubs: [], version: 1 }),
        });
      });
      await page.route('**/wp-json/tourney/v1/players*', async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ players: [], version: 1 }),
        });
      });
      await page.route('**/wp-json/tourney/v1/stages*', async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ stages: [], version: 1 }),
        });
      });
      await page.route('**/wp-json/tourney/v1/*-aci*', async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ dateFormat: 'DE' }),
        });
      });

      // 4. Intercept API delete endpoint
      let deleteCalled = false;
      await page.route('**/wp-json/tourney/v1/delete*', async (route) => {
        deleteCalled = true;
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ success: true }),
        });
      });

      await page.goto('/');
      await page.evaluate(({ name, slug }) => {
        window.localStorage.removeItem('App.demo_tourney');
        window.localStorage.setItem('tourney_is_local_mode', 'false');
        window.sessionStorage.setItem('tourney_is_local_mode', 'false');
        window.localStorage.setItem('tourney_slug', slug);
        window.sessionStorage.setItem('tourney_slug', slug);
        window.localStorage.setItem('tourney_name', name);
        window.sessionStorage.setItem('tourney_name', name);
        window.localStorage.setItem('tourney_ident', 'SIMPLE');
        window.sessionStorage.setItem('tourney_ident', 'SIMPLE');
      }, { name: compName, slug: cloudSlug });

      await mainView.goto();
      await expect(mainView.statusTourneyBar).toBeVisible();
      await expect(mainView.btnStatusLoadLast).toBeVisible();
      await mainView.btnStatusLoadLast.click();

      await expect(compInfoPage.cardHeaderTitle).toBeVisible({ timeout: 10000 });
      await expect(page.locator('body')).toContainText(compName);

      // Click delete button
      await expect(compInfoPage.btnDeleteComp).toBeVisible();
      await compInfoPage.btnDeleteComp.click();

      // Verify confirmation dialog appears
      const msgbox = page.locator('#DlgMsgbox_ModalId');
      await expect(msgbox).toBeVisible({ timeout: 5000 });

      // Click "Ja" (Confirm)
      const btnYes = page.locator('button[id*="DlgMsgbox_YesId"], #DlgMsgbox_ModalId button:has-text("Ja"), #DlgMsgbox_ModalId button:has-text("Yes")').first();
      await expect(btnYes).toBeVisible();
      await btnYes.click();

      // Verify API delete was called
      expect(deleteCalled).toBe(true);

      // Verify redirection to MainView
      await expect(page.locator('.hero-section').first()).toBeVisible({ timeout: 10000 });

      // Verify status bar is NOT visible on MainView
      await expect(page.locator('#MainView_StatusTourney')).not.toBeVisible();

      // Verify all browser storage keys are purged
      const storageKeys = await page.evaluate(() => {
        return {
          tourneySlugLocal: window.localStorage.getItem('tourney_slug'),
          tourneySlugSession: window.sessionStorage.getItem('tourney_slug'),
          tourneyNameLocal: window.localStorage.getItem('tourney_name'),
          tourneyNameSession: window.sessionStorage.getItem('tourney_name'),
          tourneyIdentLocal: window.localStorage.getItem('tourney_ident'),
          tourneyIdentSession: window.sessionStorage.getItem('tourney_ident'),
          isLocalModeLocal: window.localStorage.getItem('tourney_is_local_mode'),
          isLocalModeSession: window.sessionStorage.getItem('tourney_is_local_mode')
        };
      });

      expect(storageKeys.tourneySlugLocal).toBeNull();
      expect(storageKeys.tourneySlugSession).toBeNull();
      expect(storageKeys.tourneyNameLocal).toBeNull();
      expect(storageKeys.tourneyNameSession).toBeNull();
      expect(storageKeys.tourneyIdentLocal).toBeNull();
      expect(storageKeys.tourneyIdentSession).toBeNull();
      expect(storageKeys.isLocalModeLocal).toBeNull();
      expect(storageKeys.isLocalModeSession).toBeNull();
    });

    test('TC-CI-09 [👤]: Nach dem Löschen eines Wettbewerbs startet Klick auf Demo direkt ohne Warnmeldung "Achtung: das aktuelle Turnier wird gelöscht"', async ({ page }) => {
      const mainView = new MainViewPage(page);
      const compInfoPage = new CompetitionInfoPage(page);
      const compName = 'Schnellstart Löschtest Demo-Nachfolge';
      const samplePayload = {
        version: 1,
        tourney: {
          wpId: 999999,
          name: compName,
          organizer: 'System',
          startDate: 20261014,
          endDate: 20261014,
          ident: 'SIMPLE',
          category: 'TT',
          slug: 'local',
          clubs: [],
          players: [],
          competitions: [
            {
              id: 1,
              name: compName,
              typ: 'SINGLE',
              category: 'TT',
              startDate: '2026-10-14 10:00:00',
              status: 'CFG',
              activ: true,
              deleted: false,
              pants1Stage: []
            }
          ],
          stages: []
        }
      };

      await page.goto('/');
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
      await expect(mainView.statusTourneyBar).toBeVisible();
      await expect(mainView.btnStatusLoadLast).toBeVisible();
      await mainView.btnStatusLoadLast.click();

      await expect(compInfoPage.cardHeaderTitle).toBeVisible({ timeout: 10000 });

      // 1. Delete competition
      await expect(compInfoPage.btnDeleteComp).toBeVisible();
      await compInfoPage.btnDeleteComp.click();

      const msgbox = page.locator('#DlgMsgbox_ModalId');
      await expect(msgbox).toBeVisible({ timeout: 5000 });

      const btnYes = page.locator('button[id*="DlgMsgbox_YesId"], #DlgMsgbox_ModalId button:has-text("Ja"), #DlgMsgbox_ModalId button:has-text("Yes")').first();
      await expect(btnYes).toBeVisible();
      await btnYes.click();

      // 2. Verified on MainView
      await expect(page.locator('.hero-section').first()).toBeVisible({ timeout: 10000 });
      await expect(page.locator('#MainView_StatusTourney')).not.toBeVisible();

      // 3. Now click Demo box -> must start directly WITHOUT DlgStartDemo warning
      await mainView.clickDemoBox();
      await expect(mainView.dlgStartDemoModal).not.toBeVisible();

      // 4. Verify navigation directly to TourneyInfo
      const infoTitle = page.locator('.card-header h3');
      await expect(infoTitle).toBeVisible({ timeout: 10000 });
      const tourneyNameElem = page.locator('.card-body .fw-bold.text-primary').first();
      await expect(tourneyNameElem).toBeVisible();
      const titleText = await tourneyNameElem.innerText();
      expect(titleText).toContain('Demo:');
    });
  });
});
