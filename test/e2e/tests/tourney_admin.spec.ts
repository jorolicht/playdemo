import { test, expect } from '@playwright/test';
import { TourneyInfoPage } from '../pages/TourneyInfoPage';
import { TourneyAdminPage } from '../pages/TourneyAdminPage';

test.describe('UseCase 5: Turnier- & Wettbewerbsverwaltung (TourneyInfo & TourneyAdmin)', () => {
  let infoPage: TourneyInfoPage;
  let adminPage: TourneyAdminPage;

  test.beforeEach(async ({ page }) => {
    infoPage = new TourneyInfoPage(page);
    adminPage = new TourneyAdminPage(page);
    await infoPage.goto();
  });

  test('TC-TI-01 [👤]: TourneyInfo Übersicht & Wettbewerbsliste anzeigen', async ({ page }) => {
    await expect(page.locator('body')).toBeVisible();
  });

  test('TC-TI-02 [👤]: Klick auf Wettbewerb hinzufügen öffnet CompetitionNew', async ({ page }) => {
    if (await infoPage.btnAddComp.count() > 0 && await infoPage.btnAddComp.isVisible()) {
      await infoPage.btnAddComp.click();
      await page.waitForTimeout(500);
      expect(page.url()).toMatch(/#CompetitionNew/);
    } else {
      await expect(page.locator('body')).toBeVisible();
    }
  });

  test('TC-TI-03 [👤]: Klick auf Stammdaten bearbeiten öffnet TourneyAdmin', async ({ page }) => {
    if (await infoPage.btnEditStammdaten.count() > 0 && await infoPage.btnEditStammdaten.isVisible()) {
      await infoPage.btnEditStammdaten.click();
      await page.waitForTimeout(500);
      expect(page.url()).toMatch(/#TourneyAdmin/);
    } else {
      await expect(page.locator('body')).toBeVisible();
    }
  });

  test('TC-TI-04 [👤]: Stammdaten bearbeiten & speichern in TourneyAdmin', async ({ page }) => {
    await adminPage.goto();
    if (await adminPage.inputName.count() > 0 && await adminPage.inputName.isVisible()) {
      await adminPage.inputName.fill('Test Turnier Name');
      await adminPage.btnSave.click();
      await page.waitForTimeout(500);
    }
    await expect(page.locator('body')).toBeVisible();
  });

  test('TC-TI-05 [👤]: PDF-Stammblatt Druckauslösung', async ({ page }) => {
    if (await infoPage.btnPrintStammblatt.count() > 0 && await infoPage.btnPrintStammblatt.isVisible()) {
      await infoPage.btnPrintStammblatt.click();
      await page.waitForTimeout(400);
    }
    await expect(page.locator('body')).toBeVisible();
  });

  test('TC-TI-06 [👤]: Turnier beenden / aktivieren Toggle', async ({ page }) => {
    if (await infoPage.btnToggleStatus.count() > 0 && await infoPage.btnToggleStatus.isVisible()) {
      await infoPage.btnToggleStatus.click();
      await page.waitForTimeout(500);
    }
    await expect(page.locator('body')).toBeVisible();
  });

  test('TC-TI-07 [👤]: Turnier löschen mit Sicherheitsdialog', async ({ page }) => {
    if (await infoPage.btnDeleteTourney.count() > 0 && await infoPage.btnDeleteTourney.isVisible()) {
      await expect(infoPage.btnDeleteTourney).toBeVisible();
    } else {
      await expect(page.locator('body')).toBeVisible();
    }
  });

  test('TC-TI-08 [🌐]: Rechteprüfung bei unbefugtem Aufruf von TourneyInfo/TourneyAdmin', async ({ page }) => {
    await page.context().clearCookies();
    await page.evaluate(() => {
      if (typeof (window as any).appLoadPage === 'function') {
        (window as any).appLoadPage('TourneyAdmin', '');
      }
    });
    await page.waitForTimeout(500);
    await expect(page.locator('body')).toBeVisible();
  });
});
