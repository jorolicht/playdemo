import { test, expect, Page } from '@playwright/test';
import path from 'path';
import { MainViewPage } from '../pages/MainViewPage';

/**
 * ClickTT in der Turnierübersicht (TourneyInfo): Dropdown "ClickTT" im Card-Header mit Import, Update und Export.
 * Ersetzt den früheren Reiter "ClickTT Update" der Administration und den Button "Lade ClickTT-Daten" in TourneyNew.
 * Das Demo-Turnier (Sportart TT) enthält bereits eine ClickTT-XML.
 */

const xmlFile = path.resolve(__dirname, '../../data/ClickTTDemo.xml');

/** Startet das Demo-Turnier und öffnet das Dropdown "ClickTT". */
async function openMenu(page: Page) {
  const mainView = new MainViewPage(page);
  await mainView.goto();
  await mainView.startDemo();
  await page.locator('#TourneyInfo_BtnClickTT').click();
  await expect(page.locator('#TourneyInfo_BtnCttImport')).toBeVisible();
}

test.describe('UseCase 25: ClickTT in der Turnierübersicht', () => {

  test('TC-CTT-01 [👤]: Dropdown "ClickTT" vor "Bearbeiten" mit Import, Update, Export; kein ClickTT-Reiter in der Administration', async ({ page }) => {
    await openMenu(page);
    const items = page.locator('ul[aria-labelledby="TourneyInfo_BtnClickTT"] .dropdown-item');
    await expect(items).toHaveText(['Import', 'Update', 'Export']);
    // Dropdown steht vor "Bearbeiten"
    const order = await page.evaluate(() => {
      const dd = document.getElementById('TourneyInfo_BtnClickTT')!;
      const edit = document.getElementById('TourneyInfo_BtnEditTourney')!;
      return !!(dd.compareDocumentPosition(edit) & Node.DOCUMENT_POSITION_FOLLOWING);
    });
    expect(order).toBe(true);
    await page.evaluate(() => (window as any).appLoadPage('TourneyAdmin', 'CTT'));
    await expect(page.locator('.nav-tabs')).toBeVisible({ timeout: 5000 });
    await expect(page.locator('.nav-tabs')).not.toContainText('ClickTT');
    await expect(page.locator('#clickttUpdateFile')).toHaveCount(0);
  });

  test('TC-CTT-02 [👤]: Import öffnet den ClickTT-Dialog und liest eine XML-Datei ein', async ({ page }) => {
    await openMenu(page);
    await page.locator('#TourneyInfo_BtnCttImport').click();
    await expect(page.locator('#DlgClickTT_ModalId')).toBeVisible({ timeout: 5000 });
    await page.locator('#DlgClickTT_FileInputId').setInputFiles(xmlFile);
    await expect(page.locator('#DlgClickTT_ResultId')).toContainText('Wettbewerbe gefunden', { timeout: 5000 });
    await expect(page.locator('#DlgClickTT_ApplyId')).toBeVisible();
    await page.locator('#DlgClickTT_CancelId').click();
    await expect(page.locator('#DlgClickTT_ModalId')).not.toBeVisible({ timeout: 5000 });
  });

  test('TC-CTT-03 [👤]: Update zeigt unter der Turnier-Card den Abgleich und liest eine XML-Datei ein, Schließen blendet ihn aus', async ({ page }) => {
    let msg = '';
    page.on('dialog', d => { msg = d.message(); d.accept(); });
    await openMenu(page);
    await page.locator('#TourneyInfo_BtnCttUpdate').click();
    const panel = page.locator('#ctt-panel');
    await expect(panel).toHaveAttribute('data-mode', 'UPDATE', { timeout: 5000 });
    await expect(panel).toContainText('ClickTT Update');
    await page.locator('#clickttUpdateFile').setInputFiles(xmlFile);
    // im Demo-Turnier haben alle Spieler bereits eine Lizenznummer
    await expect.poll(() => msg, { timeout: 5000 }).toContain('keine Spieler ohne ID/Lizenznummer');
    await expect(page.locator('#ctt-panel')).toHaveAttribute('data-mode', 'UPDATE');
    await page.locator('#TourneyInfo_BtnCttClose').click();
    await expect(page.locator('#ctt-panel')).toHaveCount(0);
  });

  test('TC-CTT-06 [👤]: Update ordnet einen Spieler ohne Lizenznummer einem ClickTT-Spieler zu', async ({ page }) => {
    let msg = '';
    page.on('dialog', d => { msg = d.message(); d.accept(); });
    const mainView = new MainViewPage(page);
    await mainView.goto();
    await mainView.startDemo();
    // neuen Spieler ohne Lizenznummer in einem Einzelwettbewerb anmelden
    await page.locator('tr', { hasText: 'B-Klasse Damen Einzel' }).locator('.comp-link').click();
    await page.evaluate(() => (window as any).appLoadPage('PlayerRegistration', ''));
    await page.locator('[id^="PlayerRegistration_BtnAddParticipant"]').first().click();
    await page.locator('#DlgAddSingle_InputLastId').fill('Zuordnungstest');
    await page.locator('#DlgAddSingle_InputFirstId').fill('Erika');
    await page.locator('#DlgAddSingle_InputClubId').fill('TTC Test');
    await page.locator('#DlgAddSingle_ApplyId').click();
    await expect(page.locator('#DlgAddSingle_ModalId')).not.toBeVisible({ timeout: 5000 });

    await page.evaluate(() => (window as any).appLoadPage('TourneyInfo', ''));
    await page.locator('#TourneyInfo_BtnClickTT').click();
    await page.locator('#TourneyInfo_BtnCttUpdate').click();
    const sel = page.locator('#ctt-panel select[id^="select-assign-"]');
    await expect(sel).toHaveCount(1, { timeout: 5000 });
    const lic = await sel.locator('option').nth(1).getAttribute('value');
    await sel.selectOption(lic!);
    await page.locator('#save-assignments-btn').click();
    await expect.poll(() => msg, { timeout: 5000 }).toContain('Erfolgreich');
    await expect(page.locator('#ctt-panel')).toHaveCount(0);
    // erneutes Update: kein Spieler mehr ohne Zuordnung
    await page.locator('#TourneyInfo_BtnClickTT').click();
    await page.locator('#TourneyInfo_BtnCttUpdate').click();
    await expect(page.locator('#ctt-panel')).toHaveAttribute('data-mode', 'UPDATE', { timeout: 5000 });
    await expect(page.locator('#ctt-panel select[id^="select-assign-"]')).toHaveCount(0);
  });

  test('TC-CTT-04 [👤]: Export erzeugt nach Auswahl der Abschnitte die Ergebnis-XML', async ({ page }) => {
    await openMenu(page);
    await page.locator('#TourneyInfo_BtnCttExport').click();
    await expect(page.locator('#ctt-panel')).toHaveAttribute('data-mode', 'EXPORT', { timeout: 5000 });
    await page.locator('#generate-ctt-results-btn').click();
    await expect(page.locator('.ctt-stage-checkbox').first()).toBeVisible({ timeout: 5000 });
    const download = page.waitForEvent('download');
    await page.locator('#download-ctt-results-btn').click();
    expect((await download).suggestedFilename()).toMatch(/^clicktt_export_ergebnisse_\d+\.xml$/);
  });

  test('TC-CTT-05 [👤]: Kein ClickTT-Dropdown bei anderer Sportart, kein ClickTT-Button in TourneyNew', async ({ page }) => {
    const mainView = new MainViewPage(page);
    await mainView.goto();
    await mainView.startFullTourney();
    await expect(page.locator('#tn-name')).toBeVisible({ timeout: 5000 });
    await expect(page.locator('button', { hasText: /ClickTT/i })).toHaveCount(0);
    const now = new Date(Date.now() + 5 * 86400000).toISOString().split('T')[0];
    await page.locator('#tn-name').fill(`Sonstiges ${Date.now()}`);
    await page.locator('#tn-typ').selectOption('UNKNOWN');
    await page.locator('#tn-start').fill(now);
    await page.locator('#tn-end').fill(now);
    await page.locator('#TourneyNew_BtnSave').click();
    await expect(page.locator('#TourneyInfo_BtnEditTourney')).toBeVisible({ timeout: 10000 });
    await expect(page.locator('#TourneyInfo_BtnClickTT')).toHaveCount(0);
  });
});
