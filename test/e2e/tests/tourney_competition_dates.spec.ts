import { test, expect } from '@playwright/test';
import { MainViewPage } from '../pages/MainViewPage';
import { TourneyNewPage } from '../pages/TourneyNewPage';
import { TourneyInfoPage } from '../pages/TourneyInfoPage';

/**
 * Format Date object to YYYY-MM-DD
 */
function formatDate(d: Date): string {
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

test.describe('UseCase 5.3: Turnier- & Wettbewerbserstellung mit Datum-Validierung', () => {
  let mainPage: MainViewPage;
  let newPage: TourneyNewPage;
  let infoPage: TourneyInfoPage;

  test.beforeEach(async ({ page }) => {
    mainPage = new MainViewPage(page);
    newPage = new TourneyNewPage(page);
    infoPage = new TourneyInfoPage(page);
  });

  test('TC-TI-09 [👤]: Anlegen eines Turniers in der Vergangenheit ist nicht erlaubt', async ({ page }) => {
    await mainPage.goto();

    // Start TourneyNew via Option1 (startFullTourney)
    await mainPage.startFullTourney();

    await expect(newPage.inputName).toBeVisible({ timeout: 5000 });

    // Fill tournament with past date
    await newPage.fillForm({
      name: 'Vergangenheitsturnier Test',
      ident: 'PAST-01',
      organizer: 'TTC Vergangenheit',
      startDate: '2020-01-01',
      endDate: '2020-01-05',
    });

    await newPage.save();

    // Verify error feedback is displayed
    await expect(newPage.validationFeedback).toBeVisible();
    await expect(newPage.validationFeedback).toContainText('Das Startdatum darf nicht in der Vergangenheit liegen.');

    // Ensure we did not navigate away
    await expect(newPage.inputName).toBeVisible();
  });

  test('TC-TI-10 [👤]: Anlegen eines Turniers in der Zukunft mit 5 Tagen Laufzeit & 3 Wettbewerben im Zeitraum', async ({ page }) => {
    await mainPage.goto();

    // 1. Enter Local Mode via Option 1
    await mainPage.startFullTourney();

    await expect(newPage.inputName).toBeVisible({ timeout: 5000 });

    // 2. Compute dynamic dates: 10 days in future, 5 days duration
    const now = new Date();
    const start = new Date(now.getTime() + 10 * 24 * 60 * 60 * 1000);
    const end = new Date(start.getTime() + 4 * 24 * 60 * 60 * 1000); // 5 days total: day 0 to 4

    const startDateStr = formatDate(start);
    const endDateStr = formatDate(end);
    const tourneyName = `Zukunftsturnier 5 Tage ${Date.now()}`;

    await newPage.fillForm({
      name: tourneyName,
      ident: 'FUT-5D',
      organizer: 'TTC Zukunft e.V.',
      startDate: startDateStr,
      endDate: endDateStr,
      contactFname: 'Max',
      contactLname: 'Mustermann',
      contactEmail: 'max@zukunft.de',
      venueName: 'Zukunftshalle',
      street: 'Sportweg 1',
      zip: '80331',
      city: 'München',
    });

    await newPage.save();

    // 3. Verify navigation to TourneyInfo
    await expect(page.locator('.card-header h3')).toBeVisible({ timeout: 7000 });
    await expect(page.locator('body')).toContainText(tourneyName);

    // 4. Test: Check that competitions CANNOT be created outside the tournament period
    // 4a. Attempt creation BEFORE tournament start date (start - 1 day)
    const beforeDate = new Date(start.getTime() - 1 * 24 * 60 * 60 * 1000);
    const dateBeforeStr = `${formatDate(beforeDate)}T10:00`;

    let alertTriggered = false;
    let alertMessage = '';
    page.once('dialog', async (dialog) => {
      alertTriggered = true;
      alertMessage = dialog.message();
      await dialog.accept();
    });

    await infoPage.openAddCompetition();
    await infoPage.fillCompetition('Ungültig Vorher', dateBeforeStr);
    await infoPage.saveCompetition();

    await page.waitForTimeout(500);
    // Verify alert or inline feedback error was triggered
    const errRegex = /Das Datum des Wettbewerbs muss im Zeitraum des Turniers liegen|The competition date must be within the tournament period/;
    const feedbackVisible = await infoPage.dlgCompFeedback.isVisible().catch(() => false);
    if (feedbackVisible) {
      await expect(infoPage.dlgCompFeedback).toContainText(errRegex);
    } else {
      expect(alertTriggered).toBeTruthy();
      expect(alertMessage).toMatch(errRegex);
    }

    // Cancel / Close dialog
    await infoPage.btnCompCancel.click();
    await page.waitForTimeout(400);

    // 4b. Attempt creation AFTER tournament end date (end + 1 day)
    const afterDate = new Date(end.getTime() + 1 * 24 * 60 * 60 * 1000);
    const dateAfterStr = `${formatDate(afterDate)}T10:00`;

    alertTriggered = false;
    alertMessage = '';
    page.once('dialog', async (dialog) => {
      alertTriggered = true;
      alertMessage = dialog.message();
      await dialog.accept();
    });

    await infoPage.openAddCompetition();
    await infoPage.fillCompetition('Ungültig Nachher', dateAfterStr);
    await infoPage.saveCompetition();

    await page.waitForTimeout(500);
    const feedbackVisibleAfter = await infoPage.dlgCompFeedback.isVisible().catch(() => false);
    if (feedbackVisibleAfter) {
      await expect(infoPage.dlgCompFeedback).toContainText(errRegex);
    } else {
      expect(alertTriggered).toBeTruthy();
      expect(alertMessage).toMatch(errRegex);
    }

    // Cancel / Close dialog
    await infoPage.btnCompCancel.click();
    await page.waitForTimeout(400);

    // 5. Create 3 valid competitions within the tournament period
    // Comp 1: Day 1 (startDate at 09:00)
    const comp1Date = `${startDateStr}T09:00`;
    await infoPage.openAddCompetition();
    await infoPage.fillCompetition('Herren A Einzel Tag 1', comp1Date, 'SINGLE');
    await infoPage.saveCompetition();
    await page.waitForTimeout(600);
    await expect(infoPage.compTable).toContainText('Herren A Einzel Tag 1');

    // Comp 2: Day 3 (start + 2 days at 14:00)
    const day3Date = new Date(start.getTime() + 2 * 24 * 60 * 60 * 1000);
    const comp2Date = `${formatDate(day3Date)}T14:00`;
    await infoPage.openAddCompetition();
    await infoPage.fillCompetition('Damen Einzel Tag 3', comp2Date, 'SINGLE');
    await infoPage.saveCompetition();
    await page.waitForTimeout(600);
    await expect(infoPage.compTable).toContainText('Damen Einzel Tag 3');

    // Comp 3: Day 5 (endDate at 18:00)
    const comp3Date = `${endDateStr}T18:00`;
    await infoPage.openAddCompetition();
    await infoPage.fillCompetition('Mixed Doppel Tag 5', comp3Date, 'DOUBLE');
    await infoPage.saveCompetition();
    await page.waitForTimeout(600);
    await expect(infoPage.compTable).toContainText('Mixed Doppel Tag 5');

    // 6. Assert all 3 competitions are listed in the tournament overview
    await expect(infoPage.compTable.locator('tbody tr')).toHaveCount(3);
    await expect(infoPage.compTable).toContainText('Herren A Einzel Tag 1');
    await expect(infoPage.compTable).toContainText('Damen Einzel Tag 3');
    await expect(infoPage.compTable).toContainText('Mixed Doppel Tag 5');
  });

  test('TC-TI-11 [👤]: Lokaler Modus - Nach Neuanlage eines Turniers sind "Bearbeiten", "Löschen" und "Wettbewerb hinzufügen" aktiv und klickbar', async ({ page }) => {
    await mainPage.goto();

    // 1. Enter Local Mode via Option 1
    await mainPage.startFullTourney();

    await expect(newPage.inputName).toBeVisible({ timeout: 5000 });

    const now = new Date();
    const start = new Date(now.getTime() + 5 * 24 * 60 * 60 * 1000);
    const end = new Date(start.getTime() + 2 * 24 * 60 * 60 * 1000);
    const startDateStr = formatDate(start);
    const endDateStr = formatDate(end);
    const tourneyName = `Lokales Testturnier ${Date.now()}`;

    // 2. Fill and save tournament in local mode
    await newPage.fillForm({
      name: tourneyName,
      ident: 'LOC-01',
      organizer: 'Lokaler Verein',
      startDate: startDateStr,
      endDate: endDateStr,
      contactFname: 'Erika',
      contactLname: 'Muster',
      venueName: 'Sporthalle Nord',
      street: 'Nordstr. 12',
      zip: '10115',
      city: 'Berlin',
    });

    await newPage.save();

    // 3. Verify navigation to TourneyInfo
    await expect(page.locator('.card-header h3')).toBeVisible({ timeout: 7000 });
    await expect(page.locator('body')).toContainText(tourneyName);

    // 4. Assert buttons are NOT disabled
    await expect(infoPage.btnEditTourney).toBeEnabled();
    await expect(infoPage.btnDeleteTourney).toBeEnabled();
    await expect(infoPage.btnAddComp).toBeEnabled();

    // 5. Test clicking "Bearbeiten" (DlgEditTourney)
    await infoPage.btnEditTourney.click();
    const editModal = page.locator('div[id*="DlgEditTourney"].modal');
    await expect(editModal).toBeVisible({ timeout: 5000 });

    // Update venue name in edit dialog
    const venueInput = page.locator('input[id*="DlgEditTourney_AddrDescId"]');
    await venueInput.fill('Sporthalle Nord Aktualisiert');
    const applyBtn = page.locator('button[id*="DlgEditTourney_ApplyId"]');
    await applyBtn.click();
    await expect(editModal).not.toBeVisible({ timeout: 5000 });

    // 6. Test clicking "Wettbewerb hinzufügen" (DlgCompetition)
    await infoPage.btnAddComp.click();
    await expect(infoPage.dlgCompModal).toBeVisible({ timeout: 5000 });
    await infoPage.fillCompetition('Herren B Einzel', `${startDateStr}T10:00`, 'SINGLE');
    await infoPage.saveCompetition();
    await page.waitForTimeout(500);
    await expect(infoPage.compTable).toContainText('Herren B Einzel');

    // 7. Verify competition action buttons in table are also enabled
    const compEditBtn = infoPage.compTable.locator('tbody tr button.btn-outline-secondary').first();
    const compDeleteBtn = infoPage.compTable.locator('tbody tr button.btn-outline-danger').first();
    await expect(compEditBtn).toBeEnabled();
    await expect(compDeleteBtn).toBeEnabled();

    // 8. Attempt adding a duplicate competition with the same name "Herren B Einzel"
    await infoPage.btnAddComp.click();
    await expect(infoPage.dlgCompModal).toBeVisible({ timeout: 5000 });
    await infoPage.fillCompetition('Herren B Einzel', `${startDateStr}T11:00`, 'SINGLE');

    let duplicateAlertTriggered = false;
    let duplicateAlertMsg = '';
    page.once('dialog', async (dialog) => {
      duplicateAlertTriggered = true;
      duplicateAlertMsg = dialog.message();
      await dialog.accept();
    });

    await infoPage.saveCompetition();
    await page.waitForTimeout(500);

    const dupRegex = /Ein Wettbewerb mit diesem Namen existiert bereits|A competition with this name already exists/;
    const feedbackVisible = await infoPage.dlgCompFeedback.isVisible().catch(() => false);
    if (feedbackVisible) {
      await expect(infoPage.dlgCompFeedback).toContainText(dupRegex);
    } else {
      expect(duplicateAlertTriggered).toBeTruthy();
      expect(duplicateAlertMsg).toMatch(dupRegex);
    }

    // Cancel dialog and verify that no duplicate competition was added
    await infoPage.btnCompCancel.click();
    await page.waitForTimeout(400);
    await expect(infoPage.compTable.locator('tbody tr')).toHaveCount(1);
  });

  test('TC-TI-12 [👤]: UseCase TourneyNew: Button "ERSTELLEN" öffnet Auswahldialog (lokal vs Cloud), wobei Cloud für nicht angemeldete User ausgegraut ist', async ({ page }) => {
    await mainPage.goto();

    // Start TourneyNew via Option1 (startFullTourney)
    await mainPage.startFullTourney();

    await expect(newPage.inputName).toBeVisible({ timeout: 5000 });

    // 1. Verify create button has text "ERSTELLEN"
    await expect(newPage.btnCreate).toBeVisible();
    await expect(newPage.btnCreate).toHaveText(/ERSTELLEN|CREATE/);

    const now = new Date();
    const start = new Date(now.getTime() + 12 * 24 * 60 * 60 * 1000);
    const end = new Date(start.getTime() + 2 * 24 * 60 * 60 * 1000);
    const tourneyName = `ModeDialog Test ${Date.now()}`;

    await newPage.fillForm({
      name: tourneyName,
      ident: 'MODE-01',
      organizer: 'TTC Mode Test e.V.',
      startDate: formatDate(start),
      endDate: formatDate(end),
    });

    // 2. Click "ERSTELLEN" to trigger mode dialog
    await newPage.btnCreate.click();

    // 3. Verify DlgTourneyNewMode modal appears
    await expect(newPage.dlgModeModal).toBeVisible({ timeout: 5000 });

    // 4. Verify Local radio is checked and Cloud radio is disabled for anonymous user
    await expect(newPage.dlgRadioLocal).toBeChecked();
    await expect(newPage.dlgRadioCloud).toBeDisabled();
    await expect(newPage.dlgModeModal).toContainText(/Turnier lokal erstellen|Create tournament locally/);
    await expect(newPage.dlgModeModal).toContainText(/Turnier in Cloud erstellen|Create tournament in Cloud/);
    await expect(newPage.dlgModeModal).toContainText(/Anmeldung erforderlich|Login required/);

    // 5. Test Cancel: modal closes, form data remains
    await newPage.dlgModeCancel.click();
    await expect(newPage.dlgModeModal).not.toBeVisible({ timeout: 3000 });
    await expect(newPage.inputName).toHaveValue(tourneyName);

    // 6. Click ERSTELLEN again and confirm with Apply
    await newPage.btnCreate.click();
    await expect(newPage.dlgModeModal).toBeVisible();
    await newPage.dlgModeApply.click();

    // 7. Verify navigation to TourneyInfo
    await expect(page.locator('.card-header h3')).toBeVisible({ timeout: 7000 });
    await expect(page.locator('body')).toContainText(tourneyName);

    // Verify storage slug is "local"
    const storageSlug = await page.evaluate(() => sessionStorage.getItem('tourney_slug') || localStorage.getItem('tourney_slug'));
    expect(storageSlug).toBe('local');
  });

  test('TC-TI-13 [👤]: UseCase TourneyNew: Im Bearbeiten-Modus (edit) lautet der Button "Turnier speichern" und speichert direkt ohne Dialog', async ({ page }) => {
    await mainPage.goto();

    // 1. Create a local tournament
    await mainPage.startFullTourney();

    const now = new Date();
    const start = new Date(now.getTime() + 15 * 24 * 60 * 60 * 1000);
    const end = new Date(start.getTime() + 2 * 24 * 60 * 60 * 1000);
    const tourneyName = `EditMode Test ${Date.now()}`;

    await newPage.fillForm({
      name: tourneyName,
      ident: 'EDIT-01',
      organizer: 'TTC Edit Test e.V.',
      startDate: formatDate(start),
      endDate: formatDate(end),
    });

    await newPage.save();
    await expect(page.locator('.card-header h3')).toBeVisible({ timeout: 7000 });

    // 2. Open TourneyNew in edit mode
    await page.evaluate(() => {
      if (typeof (window as any).appLoadPage === 'function') {
        (window as any).appLoadPage('TourneyNew', 'edit');
      }
    });

    // 3. Verify submit button text is "Turnier speichern" / "Save Tournament"
    const btnSaveSubmit = page.locator('button[type="submit"]');
    await expect(btnSaveSubmit).toBeVisible({ timeout: 5000 });
    await expect(btnSaveSubmit).toHaveText(/Turnier speichern|Save Tournament/);

    // 4. Update tournament name and save
    const updatedName = `${tourneyName} (Aktualisiert)`;
    await newPage.inputName.fill(updatedName);
    await btnSaveSubmit.click();

    // 5. Verify DlgTourneyNewMode modal did NOT appear and updates are saved directly
    await expect(newPage.dlgModeModal).not.toBeVisible();
    await expect(page.locator('.card-header h3')).toBeVisible({ timeout: 7000 });
    await expect(page.locator('body')).toContainText(updatedName);
  });
});
