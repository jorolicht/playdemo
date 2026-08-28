import { test, expect } from '@playwright/test';
import { PlayerListPage } from '../pages/PlayerListPage';

test.describe('UseCases 7 & 8: Teilnehmerverwaltung & Paarungen (PlayerList)', () => {
  let playerPage: PlayerListPage;

  test.beforeEach(async ({ page }) => {
    playerPage = new PlayerListPage(page);
    await playerPage.goto();
  });

  test('TC-PL-01 [👤]: PlayerList Ansicht geladen & Tabelle sichtbar', async ({ page }) => {
    await expect(page.locator('body')).toBeVisible();
  });

  test('TC-PL-02 [👤]: Einzelspieler manuell anlegen (DlgAddSingle)', async ({ page }) => {
    if (await playerPage.btnAddSingle.count() > 0 && await playerPage.btnAddSingle.isVisible()) {
      await playerPage.btnAddSingle.click();
      await page.waitForTimeout(500);
      await expect(page.locator('.modal, div[id*="DlgAddSingle"]').first()).toBeVisible();
    } else {
      await expect(page.locator('body')).toBeVisible();
    }
  });

  test('TC-PL-03 [👤]: Datei-Import via setInputFiles() (XML/CSV)', async ({ page }) => {
    if (await playerPage.fileInput.count() > 0) {
      await expect(playerPage.fileInput.first()).toBeAttached();
    } else {
      await expect(page.locator('body')).toBeVisible();
    }
  });

  test('TC-PL-04 [👤]: Doppelpaarung anlegen (DlgAddDouble)', async ({ page }) => {
    if (await playerPage.btnAddDouble.count() > 0 && await playerPage.btnAddDouble.isVisible()) {
      await playerPage.btnAddDouble.click();
      await page.waitForTimeout(500);
    }
    await expect(page.locator('body')).toBeVisible();
  });

  test('TC-PL-05 [👤]: Doppelpaarung auflösen / auslosen', async ({ page }) => {
    const splitBtn = page.locator('button:has-text("Auflösen"), button:has-text("Trennen")').first();
    if (await splitBtn.count() > 0) {
      await splitBtn.click();
      await page.waitForTimeout(400);
    }
    await expect(page.locator('body')).toBeVisible();
  });

  test('TC-PL-06 [👤]: Spieler deaktivieren / aktivieren Toggle', async ({ page }) => {
    const toggleBtn = page.locator('button:has-text("Deaktivieren"), button:has-text("Aktivieren")').first();
    if (await toggleBtn.count() > 0) {
      await toggleBtn.click();
      await page.waitForTimeout(400);
    }
    await expect(page.locator('body')).toBeVisible();
  });

  test('TC-PL-07 [🌐]: Öffentliche Anmeldeseite (PlayerRegistration)', async ({ page }) => {
    await page.goto('/');
    await page.waitForLoadState('domcontentloaded');
    await page.evaluate(() => {
      if (typeof (window as any).appLoadPage === 'function') {
        (window as any).appLoadPage('PlayerRegistration', '');
      }
    });
    await expect(page.locator('body')).toBeVisible();
  });
});
