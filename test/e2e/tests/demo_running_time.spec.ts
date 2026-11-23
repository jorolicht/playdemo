import { test, expect } from '@playwright/test';
import { MainViewPage } from '../pages/MainViewPage';

/**
 * Demo-Turnier: laufende Spiele zeigen eine realistische Spielzeit (DemoManager.refreshRunningMatches).
 * Die Demo-Daten wurden zu einem festen Zeitpunkt aufgezeichnet; ohne Anpassung zeigte die Uhr die gesamte Zeit seitdem.
 */
test('TC-SI-12 [🌐]: Laufendes Spiel im Demo-Turnier zeigt wenige Minuten Spielzeit', async ({ page }) => {
  const main = new MainViewPage(page);
  await main.goto();
  await main.startDemo();
  await page.locator('tr', { hasText: 'D-Klasse Damen Einzel' }).locator('.comp-link').click();
  await page.locator('.stage-link', { hasText: 'Vorrunde' }).click();
  await page.evaluate(() => (window as any).appLoadPage('StageInput', ''));
  // Spiel 3 (Brandenburg – Fehr) läuft an Tisch 12
  const time = page.locator('#running-time-3');
  await expect(time).toBeVisible({ timeout: 10000 });
  await expect(time).toHaveText(/^0[3-9]:\d\d$/);
});
