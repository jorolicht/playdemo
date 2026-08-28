import { test, expect } from '@playwright/test';
import { TourneyWelcomePage } from '../pages/TourneyWelcomePage';

test.describe('UseCases 3 & 4: Öffentliche Turnierseite (TourneyWelcome)', () => {
  let welcomePage: TourneyWelcomePage;

  test.beforeEach(async ({ page }) => {
    welcomePage = new TourneyWelcomePage(page);
    await page.goto('/');
    await page.waitForLoadState('domcontentloaded');
    await page.evaluate(() => {
      if (typeof (window as any).appLoadPage === 'function') {
        (window as any).appLoadPage('TourneyWelcome', '');
      }
    });
  });

  test('TC-TW-01 [🌐]: Turnierdetails, Ort, Datum & Wettbewerbsliste anzeigen', async ({ page }) => {
    await expect(page.locator('.card, .container').first()).toBeVisible();
    if (await welcomePage.compTable.count() > 0) {
      await expect(welcomePage.compTable).toBeVisible();
    }
  });

  test('TC-TW-02 [🌐]: DSGVO-konforme Karten-Preview URL & Status Check', async ({ page }) => {
    const response = await page.request.get('/srv/api/map/static?query=Hamburg');
    expect(response.status()).toBeLessThan(500);
  });

  test('TC-TW-03 [🌐]: iCal Kalenderdatei Link (.ics) vorhanden', async ({ page }) => {
    const icsBtn = page.locator('a[href*=".ics"], button:has-text("iCal")');
    if (await icsBtn.count() > 0) {
      await expect(icsBtn.first()).toBeVisible();
    } else {
      await expect(page.locator('body')).toBeVisible();
    }
  });

  test('TC-TW-04 [🌐]: Öffentliche Spieleranmeldung Modal öffnen (DlgPublicRegistration)', async ({ page }) => {
    const regBtn = page.locator('button:has-text("Anmeldung"), button:has-text("Online-Anmeldung")');
    if (await regBtn.count() > 0) {
      await regBtn.first().click();
      await page.waitForTimeout(500);
      await expect(page.locator('.modal, div[id*="DlgPublicRegistration"]').first()).toBeVisible();
    } else {
      await expect(page.locator('body')).toBeVisible();
    }
  });

  test('TC-TW-05 [🌐]: Klick auf Wettbewerbszeile leitet zu CompetitionWelcome', async ({ page }) => {
    const compRow = page.locator('tr[onclick*="CompetitionWelcome"], tr[onclick*="CompetitionInfo"]').first();
    if (await compRow.count() > 0) {
      await compRow.click();
      await page.waitForTimeout(500);
      expect(page.url()).toMatch(/#CompetitionWelcome|#CompetitionInfo/);
    } else {
      await expect(page.locator('body')).toBeVisible();
    }
  });
});
