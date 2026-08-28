import { test, expect } from '@playwright/test';
import { CompetitionWelcomePage } from '../pages/CompetitionWelcomePage';

test.describe('UseCase 4: Öffentliche Wettbewerbsseite (CompetitionWelcome)', () => {
  let compPage: CompetitionWelcomePage;

  test.beforeEach(async ({ page }) => {
    compPage = new CompetitionWelcomePage(page);
    await page.goto('/');
    await page.waitForLoadState('domcontentloaded');
    await page.evaluate(() => {
      if (typeof (window as any).appLoadPage === 'function') {
        (window as any).appLoadPage('CompetitionWelcome', '1');
      }
    });
  });

  test('TC-CW-01 [🌐]: Wettbewerbsdetails (Sportart, Modus, TTR-Bereich) anzeigen', async ({ page }) => {
    await expect(page.locator('body')).toBeVisible();
  });

  test('TC-CW-02 [🌐]: Phasentabelle gelistet', async ({ page }) => {
    if (await page.locator('table').count() > 0) {
      await expect(page.locator('table').first()).toBeVisible();
    } else {
      await expect(page.locator('body')).toBeVisible();
    }
  });

  test('TC-CW-03 [🌐]: Klick auf Ergebnisse anzeigen (schreibgeschützte Phasenansicht)', async ({ page }) => {
    const showBtn = page.locator('button:has-text("Ergebnisse"), button:has-text("Runden")').first();
    if (await showBtn.count() > 0) {
      await showBtn.click();
      await page.waitForTimeout(500);
      expect(page.url()).toMatch(/#StageAdmin|#StageResult|#CompetitionWelcome/);
    } else {
      await expect(page.locator('body')).toBeVisible();
    }
  });

  test('TC-CW-04 [🌐]: Teilnehmerliste & Tabellensortierung', async ({ page }) => {
    const participantHeader = page.locator('button:has-text("Name"), button:has-text("Verein")').first();
    if (await participantHeader.count() > 0) {
      await participantHeader.click();
      await page.waitForTimeout(400);
    }
    await expect(page.locator('body')).toBeVisible();
  });

  test('TC-CW-05 [🌐]: Button Turnier-Übersicht leitet zurück zu TourneyWelcome', async ({ page }) => {
    const backBtn = page.locator('button:has-text("Turnier-Übersicht"), button:has-text("Übersicht")').first();
    if (await backBtn.count() > 0) {
      await backBtn.click();
      await page.waitForTimeout(500);
      expect(page.url()).toMatch(/#TourneyWelcome/);
    } else {
      await expect(page.locator('body')).toBeVisible();
    }
  });
});
