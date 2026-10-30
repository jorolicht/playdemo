import { test, expect } from '@playwright/test';
import { MainViewPage } from '../pages/MainViewPage';
import { TourneyNewPage } from '../pages/TourneyNewPage';
import { TourneyInfoPage } from '../pages/TourneyInfoPage';

/**
 * Helper to format date to YYYY-MM-DD
 */
function formatDate(d: Date): string {
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

test.describe('UseCase 5.4: Lokaler Modus - Export- und Verlassen-Dialog (DlgExportTourney)', () => {
  let mainPage: MainViewPage;
  let newPage: TourneyNewPage;
  let infoPage: TourneyInfoPage;

  test.beforeEach(async ({ page }) => {
    mainPage = new MainViewPage(page);
    newPage = new TourneyNewPage(page);
    infoPage = new TourneyInfoPage(page);
  });

  test('TC-EXP-01 [👤]: Lokal-Optionen -> Turnier exportieren (JSON) öffnet Dialog mit vorausgefülltem Dateinamen und validiert Eingabe', async ({ page }) => {
    await mainPage.goto();

    // 1. Enter Local Mode via Option 1
    await mainPage.startFullTourney();

    await expect(newPage.inputName).toBeVisible({ timeout: 5000 });

    const now = new Date();
    const start = new Date(now.getTime() + 5 * 24 * 60 * 60 * 1000);
    const end = new Date(start.getTime() + 2 * 24 * 60 * 60 * 1000);
    const startDateStr = formatDate(start);
    const endDateStr = formatDate(end);
    const tourneyName = `ExportTest ${Date.now()}`;

    await newPage.fillForm({
      name: tourneyName,
      ident: 'EXP-01',
      organizer: 'TTC Export e.V.',
      startDate: startDateStr,
      endDate: endDateStr,
    });

    await newPage.save();
    await expect(infoPage.cardHeaderTitle).toBeVisible({ timeout: 7000 });

    // 2. Open "Lokal-Optionen" dropdown and click "Turnier exportieren (JSON)"
    const offlineDropdown = page.locator('#offlineDropdown');
    await expect(offlineDropdown).toBeVisible();
    await offlineDropdown.click();

    const exportMenuItem = page.locator('a[id*="ExportLocalId"]');
    await expect(exportMenuItem).toBeVisible();
    await exportMenuItem.click();

    // 3. Verify DlgExportTourney modal opens
    await expect(infoPage.dlgExportModal).toBeVisible({ timeout: 5000 });

    // 4. Verify default filename prefill pattern: tourney_<safeName>_<startDate>_export.json
    const expectedDateStr = startDateStr.replace(/-/g, '');
    const filenameVal = await infoPage.inputExportFilename.inputValue();
    expect(filenameVal).toContain('tourney_ExportTest');
    expect(filenameVal).toContain(expectedDateStr);
    expect(filenameVal).toMatch(/\.json$/);

    // In export-only mode, "Ohne Export verlassen" button should NOT be visible
    await expect(infoPage.btnExportLeaveWithout).not.toBeVisible();

    // 5. Test validation: clear filename and click apply
    await infoPage.inputExportFilename.fill('');
    await infoPage.btnExportApply.click();
    await expect(infoPage.dlgExportFeedback).toBeVisible();

    // 6. Test cancel: click cancel button, modal closes, user stays on TourneyInfo
    await infoPage.btnExportCancel.click();
    await expect(infoPage.dlgExportModal).not.toBeVisible({ timeout: 3000 });
    await expect(infoPage.cardHeaderTitle).toBeVisible();

    // 7. Test successful download trigger
    await offlineDropdown.click();
    await exportMenuItem.click();
    await expect(infoPage.dlgExportModal).toBeVisible();

    const [download] = await Promise.all([
      page.waitForEvent('download', { timeout: 7000 }),
      infoPage.btnExportApply.click(),
    ]);

    expect(download.suggestedFilename()).toContain('tourney_ExportTest');
    expect(download.suggestedFilename()).toMatch(/\.json$/);
    await expect(infoPage.dlgExportModal).not.toBeVisible({ timeout: 3000 });
    await expect(infoPage.cardHeaderTitle).toBeVisible();
  });

  test('TC-EXP-02 [👤]: Klick auf Speichern/Verlassen im lokalen Modus speichert, zeigt den Speicherort an und führt zur Startseite', async ({ page }) => {
    await mainPage.goto();

    // 1. Enter Local Mode via Option 1
    await mainPage.startFullTourney();

    await expect(newPage.inputName).toBeVisible({ timeout: 5000 });

    const now = new Date();
    const start = new Date(now.getTime() + 6 * 24 * 60 * 60 * 1000);
    const end = new Date(start.getTime() + 1 * 24 * 60 * 60 * 1000);
    const tourneyName = `SaveLeaveTest ${Date.now()}`;

    await newPage.fillForm({
      name: tourneyName,
      ident: 'LEAVE-01',
      organizer: 'TTC SaveLeave e.V.',
      startDate: formatDate(start),
      endDate: formatDate(end),
    });

    await newPage.save();
    await expect(infoPage.cardHeaderTitle).toBeVisible({ timeout: 7000 });

    // 2. Click Speichern/Verlassen button
    await expect(infoPage.btnSaveLeave).toBeVisible();
    await infoPage.btnSaveLeave.click();

    // 3. Seit 07f0d57: kein Dialog "Turnier verlassen" mehr, sondern Speichern und Hinweis auf den Speicherort
    //    (DlgMsgbox "Speichern & Verlassen" mit "Speicherort: lokal im Browser" und nur OK); kein Datei-Download
    let downloadTriggered = false;
    const downloadListener = () => { downloadTriggered = true; };
    page.on('download', downloadListener);

    const msgbox = page.locator('#DlgMsgbox_ModalId');
    await expect(msgbox).toBeVisible({ timeout: 5000 });
    await expect(page.locator('#DlgMsgbox_TitleId')).toHaveText(/Speichern & Verlassen|Save & Leave/);
    await expect(page.locator('#DlgMsgbox_BodyId')).toHaveText(/Speicherort: "lokal" im Browser|Storage location: "local" in browser/);
    await expect(infoPage.dlgExportModal).not.toBeVisible();
    const buttons = msgbox.locator('.modal-footer button');
    await expect(buttons).toHaveCount(1);

    // 4. OK führt zur Startseite
    await buttons.first().click();
    await expect(msgbox).not.toBeVisible({ timeout: 3000 });
    await expect(infoPage.cardHeaderTitle).not.toBeVisible({ timeout: 7000 });
    await expect(page.locator('.hero-section').first()).toBeVisible({ timeout: 7000 });

    page.off('download', downloadListener);
    expect(downloadTriggered).toBe(false);
  });

  test('TC-EXP-03 [👤]: Verlassen der Turnierseite über Logo/Home im lokalen Modus öffnet Dialog "Turnier verlassen" und erlaubt Verlassen', async ({ page }) => {
    await mainPage.goto();

    // 1. Enter Local Mode via Option 1
    await mainPage.startFullTourney();

    await expect(newPage.inputName).toBeVisible({ timeout: 5000 });

    const now = new Date();
    const start = new Date(now.getTime() + 7 * 24 * 60 * 60 * 1000);
    const end = new Date(start.getTime() + 1 * 24 * 60 * 60 * 1000);
    const tourneyName = `NavLeaveTest ${Date.now()}`;

    await newPage.fillForm({
      name: tourneyName,
      ident: 'NAV-01',
      organizer: 'TTC NavLeave e.V.',
      startDate: formatDate(start),
      endDate: formatDate(end),
    });

    await newPage.save();
    await expect(infoPage.cardHeaderTitle).toBeVisible({ timeout: 7000 });

    // 2. Click Logo / Brand in Navbar to navigate away to MainView
    const brandLink = page.locator('a.navbar-brand');
    await brandLink.click();

    // 3. Verify DlgExportTourney modal opens with leave prompt and 2 buttons
    await expect(infoPage.dlgExportModal).toBeVisible({ timeout: 5000 });
    await expect(infoPage.dlgExportTitle).toHaveText(/Turnier verlassen|Leave tournament/i);
    await expect(infoPage.dlgExportPrompt).toHaveText(/Das aktuelle Turnier wird in der Cloud bzw. lokal gespeichert\.|The current tournament will be saved in the cloud or locally\./i);
    await expect(infoPage.inputExportFilename).not.toBeVisible();

    const footerButtons = infoPage.dlgExportModal.locator('.modal-footer button');
    await expect(footerButtons).toHaveCount(2);
    await expect(infoPage.btnExportCancel).toHaveText(/ABBRECHEN|CANCEL/i);
    await expect(infoPage.btnExportApply).toHaveText(/VERLASSEN|LEAVE/i);

    // 4. Click "VERLASSEN"
    await infoPage.btnExportApply.click();

    // 5. Verify successful navigation away from tourney without download
    await expect(infoPage.dlgExportModal).not.toBeVisible({ timeout: 3000 });
    await expect(infoPage.cardHeaderTitle).not.toBeVisible({ timeout: 7000 });
    await expect(page.locator('.hero-section').first()).toBeVisible({ timeout: 7000 });
  });

  test('TC-EXP-04 [👤]: Beim Export mit bereits existierendem Dateinamen erscheint eine Überschreiben-Warnung und erfordert Bestätigung', async ({ page }) => {
    await mainPage.goto();

    // 1. Enter Local Mode via Option 1
    await mainPage.startFullTourney();

    await expect(newPage.inputName).toBeVisible({ timeout: 5000 });

    const now = new Date();
    const start = new Date(now.getTime() + 8 * 24 * 60 * 60 * 1000);
    const end = new Date(start.getTime() + 1 * 24 * 60 * 60 * 1000);
    const tourneyName = `OverwriteTest ${Date.now()}`;

    await newPage.fillForm({
      name: tourneyName,
      ident: 'OVR-01',
      organizer: 'TTC Overwrite e.V.',
      startDate: formatDate(start),
      endDate: formatDate(end),
    });

    await newPage.save();
    await expect(infoPage.cardHeaderTitle).toBeVisible({ timeout: 7000 });

    const offlineDropdown = page.locator('#offlineDropdown');
    const exportMenuItem = page.locator('a[id*="ExportLocalId"]');

    // 2. Perform first export
    await offlineDropdown.click();
    await exportMenuItem.click();
    await expect(infoPage.dlgExportModal).toBeVisible({ timeout: 5000 });
    const originalFilename = await infoPage.inputExportFilename.inputValue();

    const [firstDownload] = await Promise.all([
      page.waitForEvent('download', { timeout: 7000 }),
      infoPage.btnExportApply.click(),
    ]);
    expect(firstDownload.suggestedFilename()).toBe(originalFilename);
    await expect(infoPage.dlgExportModal).not.toBeVisible({ timeout: 3000 });

    // 3. Attempt second export with the same filename
    await offlineDropdown.click();
    await exportMenuItem.click();
    await expect(infoPage.dlgExportModal).toBeVisible({ timeout: 5000 });
    expect(await infoPage.inputExportFilename.inputValue()).toBe(originalFilename);

    // Initial click: should trigger overwrite warning instead of downloading immediately
    await infoPage.btnExportApply.click();

    // 4. Verify overwrite warning appears and button changes to overwrite
    await expect(infoPage.dlgExportOverwriteWarning).toBeVisible();
    await expect(infoPage.btnExportApply).toHaveText(/Überschreiben|Overwrite/);
    await expect(infoPage.dlgExportModal).toBeVisible(); // modal remains open

    // 5. If user changes the filename, warning resets
    await infoPage.inputExportFilename.fill('brand_new_filename.json');
    await expect(infoPage.dlgExportOverwriteWarning).not.toBeVisible();
    await expect(infoPage.btnExportApply).toHaveText(/Exportieren|Export/);

    // 6. Change back to existing filename and click Apply -> warning shows again -> click Overwrite -> download proceeds
    await infoPage.inputExportFilename.fill(originalFilename);
    await infoPage.btnExportApply.click();
    await expect(infoPage.dlgExportOverwriteWarning).toBeVisible();
    await expect(infoPage.btnExportApply).toHaveText(/Überschreiben|Overwrite/);

    // Confirm overwrite
    const [secondDownload] = await Promise.all([
      page.waitForEvent('download', { timeout: 7000 }),
      infoPage.btnExportApply.click(),
    ]);
    expect(secondDownload.suggestedFilename()).toBe(originalFilename);
    await expect(infoPage.dlgExportModal).not.toBeVisible({ timeout: 3000 });
  });
});


