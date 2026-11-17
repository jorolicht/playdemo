import { test, expect } from '@playwright/test';

/**
 * Mannschaftswettbewerb: Teilnehmer-Accordion in CompetitionInfo und Tabelle in PlayerRegistration
 * zeigen Mannschafts-Bezeichnungen ("Mannschaftsliste", "Mannschaftsname"/"Mannschaft", "Ø TTR").
 */
test.describe('Mannschaftswettbewerb: Überschriften der Teilnehmerlisten', () => {
  test('TC-CI-13 [👤]: CompetitionInfo und Anmeldung zeigen Mannschafts-Überschriften', async ({ page }) => {
    await page.goto('/');
    await page.locator('#MainView_BtnDemo').click();
    await expect(page.locator('td', { hasText: 'S-Klasse Herren Einzel' }).first()).toBeVisible({ timeout: 15000 });

    // Mannschaftswettbewerb anlegen
    await page.locator('[id^="TourneyInfo_BtnAddComp"]').click();
    await page.locator('#DlgCompetition_CompNameId').fill('Mannschaft Test');
    await page.locator('#DlgCompetition_CompTypId').selectOption('TEAM4');
    await page.locator('#DlgCompetition_ApplyId').click();
    await expect(page.locator('#DlgCompetition_ModalId')).not.toBeVisible({ timeout: 5000 });

    await page.locator('tr', { hasText: 'Mannschaft Test' }).locator('.comp-link').click();
    const acc = page.locator('#headingParticipants');
    await expect(acc).toContainText('Mannschaftsliste', { timeout: 5000 });
    const ciHead = page.locator('#collapseParticipants thead');
    await expect(ciHead).toContainText(/Mannschaftsname/i);
    await expect(ciHead).toContainText(/Ø TTR/i);
    await expect(ciHead).not.toContainText(/Name, Vorname/i);

    // Anmeldung
    await page.evaluate(() => (window as any).appLoadPage('PlayerRegistration', ''));
    const prHead = page.locator('thead').first();
    await expect(prHead).toContainText(/Mannschaft/i, { timeout: 5000 });
    await expect(prHead).toContainText(/Ø TTR/i);
    await expect(prHead).not.toContainText(/Name, Vorname/i);
  });

  test('TC-CI-14 [👤]: Einzelwettbewerb behält "Teilnehmerliste", "Name, Vorname" und "TTR"', async ({ page }) => {
    await page.goto('/');
    await page.locator('#MainView_BtnDemo').click();
    await page.locator('tr', { hasText: 'S-Klasse Herren Einzel' }).first().locator('.comp-link').click();
    await expect(page.locator('#headingParticipants')).toContainText('Teilnehmerliste', { timeout: 15000 });
    await expect(page.locator('#collapseParticipants thead')).toContainText(/Name, Vorname/i);
  });
});
