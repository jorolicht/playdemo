import { test, expect } from '@playwright/test';
import { MainViewPage } from '../pages/MainViewPage';

/**
 * Demo tourney, competition 1 "S-Klasse Herren Einzel": stage 3 Vorrunde (round robin, finished)
 * and stage 4 Endrunde (knockout, only one quarter-final played: Bert, Ali lost 2:3).
 */
test.describe('Ergebnisliste: Urkundenphase über Radio-Button in CompetitionInfo wählen', () => {

  test('TC-RL-01 [👤]: Auswahl der Urkundenphase ändert das Ranking in der Ergebnisliste', async ({ page }) => {
    const mainView = new MainViewPage(page);
    await mainView.goto();
    await mainView.startDemo();

    const radio = (stageId: number) => page.locator(`#CompetitionInfo_RadioCert-${stageId}`);
    const rankingRows = page.locator('.comp-card-collapse tbody tr');

    /** Selects the certificate stage in CompetitionInfo and opens the expanded ResultList. */
    async function selectCertStage(stageId: number) {
      // The demo loads asynchronously, so retry until the stage list is rendered
      await expect(async () => {
        await page.evaluate(() => (window as any).appLoadPage('CompetitionInfo', '1'));
        await expect(radio(stageId)).toBeVisible({ timeout: 1000 });
      }).toPass({ timeout: 10000 });
      await radio(stageId).click();
      await expect(radio(stageId)).toBeChecked();
      await page.evaluate(() => (window as any).appLoadPage('ResultList', '1'));
      await page.evaluate(() => (window as any).toggleAllCards(true));
    }

    // Vorrunde: complete round robin ranking
    await selectCertStage(3);
    await expect(rankingRows).toHaveCount(10);
    await expect(rankingRows.first()).toContainText('Elb-Rösel, Sam');

    // Endrunde: knockout ranking, only the quarter-final loser is placed so far (shared 5th)
    await selectCertStage(4);
    await expect(rankingRows).toHaveCount(1);
    await expect(rankingRows.first()).toContainText('5.');
    await expect(rankingRows.first()).toContainText('Bert, Ali');
  });
});
