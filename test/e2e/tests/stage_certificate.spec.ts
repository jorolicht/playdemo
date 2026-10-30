import { test, expect, Page } from '@playwright/test';

/**
 * Stage-Reiter "Urkunden": gleiche Ansicht wie "Ergebnisse" (je Format) mit Urkunden-Button pro Spieler.
 * Demo-Turnier, Wettbewerb "S-Klasse Herren Einzel": Vorrunde (Jeder gegen Jeden, beendet), Endrunde (K.o.).
 */

/** Startet das Demo-Turnier, markiert die n-te Stage des Wettbewerbs als Urkunden-Stage und öffnet deren Reiter Urkunden. */
async function openCertificateTab(page: Page, stageIndex: number) {
  await page.goto('/');
  await page.locator('#MainView_BtnDemo').click();
  await page.locator('td', { hasText: 'S-Klasse Herren Einzel' }).first().locator('span[onclick^="appLoadPage"]').click();
  const radio = page.locator('input[name="stage-certificate-radio"]').nth(stageIndex);
  await expect(radio).toBeVisible({ timeout: 15000 });
  if (!(await radio.isChecked())) await radio.click();
  await expect(radio).toBeChecked();
  // Über den Baum der Turnierinfo: Wettbewerb und Stage aufklappen, Reiter Urkunden anklicken
  await page.evaluate(() => (window as any).appLoadPage('TourneyInfo', ''));
  await page.locator('tr', { hasText: 'S-Klasse Herren Einzel' }).first().locator('[role="button"]').click();
  await page.locator('tr.table-light [role="button"]').nth(stageIndex).click();
  await page.locator('tr.table-light button[id$="-StageCertificate"]').click();
  await expect(page.locator('.nav-tabs .nav-link.active[href*="StageCertificate"]')).toBeVisible({ timeout: 10000 });
}

test.describe('Stage-Reiter Urkunden', () => {
  test('TC-CERT-TAB-01 [👤]: Jeder gegen Jeden: Ergebnistabelle mit Urkunden-Button je Platzierung', async ({ page }) => {
    await openCertificateTab(page, 0);
    const rows = page.locator('.stage-content tbody tr');
    await expect(rows.first()).toBeVisible();
    // Jede Zeile mit Platzierung hat einen Urkunden-Button; kein Button "Nächste Stage"
    expect(await page.locator('.stage-content .cert-btn').count()).toBe(await rows.count());
    await expect(page.locator('.stage-content [id^="StageResult_"]')).toHaveCount(0);

    // Button öffnet die Urkunde mit Name und Platz
    const name = (await rows.first().locator('td').nth(1).innerText()).trim();
    await rows.first().locator('.cert-btn').click();
    await expect(page.locator('#CertEdit_Rank')).toHaveValue('1', { timeout: 10000 });
    await expect(page.locator('body')).toContainText(name);
  });

  test('TC-CERT-TAB-02 [👤]: K.o.-System: Turnierbaum mit Urkunden-Button nur bei feststehender Platzierung', async ({ page }) => {
    await openCertificateTab(page, 1);
    await expect(page.locator('#tournamentWrapper')).toBeVisible();
    // Buttons nur bei Spielern, deren Endplatzierung im jeweiligen Spiel feststeht (Verlierer bzw. Finale)
    const certBtns = page.locator('#tournamentWrapper .cert-btn');
    const finished = await page.locator('#tournamentWrapper .bracket-player-row.loser').count();
    expect(await certBtns.count()).toBeLessThanOrEqual(finished + 1);
    await expect(page.locator('.stage-content [id^="StageResult_"]')).toHaveCount(0);
  });
});
