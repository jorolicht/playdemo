import { test, expect, Page } from '@playwright/test';

/**
 * CompetitionInfo: Dialog "Wettbewerb bearbeiten".
 * Kategorie und Typ sind gesperrt, Beginn muss im Turnierzeitraum liegen, Name darf keinem anderen Wettbewerb entsprechen.
 * Demo-Turnier: 01.04.2030 – 02.04.2030; "B-Klasse Damen Einzel" hat noch keinen Wettbewerbsabschnitt.
 */
async function openEdit(page: Page) {
  await page.goto('/');
  await page.locator('#MainView_BtnDemo').click();
  await page.locator('tr', { hasText: 'B-Klasse Damen Einzel' }).locator('.comp-link').click();
  await page.locator('[id^="CompetitionInfo_BtnEditComp"]').click();
  await expect(page.locator('#DlgCompetition_ModalId')).toBeVisible({ timeout: 10000 });
}

test.describe('CompetitionInfo: Wettbewerb bearbeiten', () => {
  test('TC-CI-15 [👤]: Kategorie und Wettbewerbstyp sind beim Bearbeiten gesperrt (auch ohne Abschnitt)', async ({ page }) => {
    await openEdit(page);
    await expect(page.locator('#DlgCompetition_CompCategoryId')).toBeDisabled();
    await expect(page.locator('#DlgCompetition_CompTypId')).toBeDisabled();
    await expect(page.locator('#DlgCompetition_LockHintId')).toBeVisible();
    await expect(page.locator('#DlgCompetition_CompNameId')).toBeEditable();
  });

  test('TC-CI-16 [👤]: Beginn außerhalb des Turnierzeitraums wird abgelehnt', async ({ page }) => {
    await openEdit(page);
    page.once('dialog', d => d.accept());
    await page.locator('#DlgCompetition_StartDateId').fill('2030-04-05T10:00');
    await page.locator('#DlgCompetition_ApplyId').click();
    await expect(page.locator('#dlgCompFeedback')).toContainText(/Zeitraum/);
    await expect(page.locator('#DlgCompetition_ModalId')).toBeVisible();
  });

  test('TC-CI-17 [👤]: Umbenennen auf einen bestehenden Wettbewerbsnamen wird abgelehnt, neuer Name wird übernommen', async ({ page }) => {
    await openEdit(page);
    page.once('dialog', d => d.accept());
    await page.locator('#DlgCompetition_CompNameId').fill('s-klasse herren einzel');
    await page.locator('#DlgCompetition_ApplyId').click();
    await expect(page.locator('#DlgCompetition_CompNameId')).toHaveClass(/is-invalid/);
    await expect(page.locator('#DlgCompetition_ModalId')).toBeVisible();

    await page.locator('#DlgCompetition_CompNameId').fill('B-Klasse Damen Einzel neu');
    await page.locator('#DlgCompetition_ApplyId').click();
    await expect(page.locator('#DlgCompetition_ModalId')).not.toBeVisible({ timeout: 5000 });
    await expect(page.locator('body')).toContainText('B-Klasse Damen Einzel neu');
  });
});
