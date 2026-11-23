import { test, expect, Page } from '@playwright/test';
import path from 'path';
import { MainViewPage } from '../pages/MainViewPage';

/**
 * Screenshots für das Benutzerhandbuch (site/content/hilfe.html), Teil 1: Turnierleitung im lokalen Demo-Turnier.
 * Keine Anmeldung nötig, es erscheinen nur Demo-Daten.
 */

const OUT = path.resolve(__dirname, '../../../site/content/hilfe');

/** Blendet Elemente aus, die nicht ins Handbuch gehören (WordPress-Leiste, Cookie-/News-Hinweise). */
async function clean(page: Page) {
  await page.addStyleTag({ content: '#wpadminbar{display:none!important} html{margin-top:0!important}' });
}

/** Speichert einen Screenshot des sichtbaren Bereichs bzw. eines Elements. */
async function shot(page: Page, name: string, target?: string) {
  await page.waitForTimeout(600);
  if (target) await page.locator(target).first().screenshot({ path: `${OUT}/${name}.png` });
  else await page.screenshot({ path: `${OUT}/${name}.png` });
}

test('Handbuch: Turnierleitung (Demo-Turnier)', async ({ page, browser }) => {
  const main = new MainViewPage(page);
  await main.goto();
  await clean(page);
  await shot(page, '01-startseite');

  // Demo-Turnier
  await main.startDemo();
  await expect(page.locator('.comp-link').first()).toBeVisible({ timeout: 15000 });
  await shot(page, '03-turnieruebersicht');

  await page.locator('#myTourneysDropdown').click();
  await shot(page, '02-meine-turniere');
  await page.keyboard.press('Escape');
  await page.locator('#myTourneysDropdown').click();

  await page.locator('button[id*="BtnAddComp"]').click();
  await expect(page.locator('.modal.show')).toBeVisible();
  await shot(page, '05-wettbewerb-dialog', '.modal.show .modal-content');
  await page.locator('.modal.show [id$="CancelId"]').click();
  await expect(page.locator('.modal.show')).toHaveCount(0);

  // Teilnehmer und Spieler
  await page.evaluate(() => (window as any).appLoadPage('PlayerRegistration', ''));
  await page.locator('select[id$="SelectComp"]').selectOption({ index: 1 });
  await shot(page, '07-spieler-anmeldung');
  await page.evaluate(() => (window as any).appLoadPage('TourneyInfo', ''));
  await page.locator('tr', { hasText: 'S-Klasse Herren Einzel' }).locator('.comp-link').click();
  await shot(page, '06-wettbewerb');

  // K.-o.-Endrunde: Auslosung und Turnierbaum
  await page.locator('.stage-link', { hasText: 'Endrunde' }).click();
  await page.evaluate(() => (window as any).appLoadPage('StageDraw', ''));
  await shot(page, '10-auslosung');
  await page.evaluate(() => (window as any).appLoadPage('StageResult', ''));
  await shot(page, '13-ko-baum');

  // Gruppen-Vorrunde: Ergebniseingabe, Tabellen, Spielzettel
  await page.evaluate(() => (window as any).appLoadPage('TourneyInfo', ''));
  await page.locator('tr', { hasText: 'D-Klasse Damen Einzel' }).locator('.comp-link').click();
  await page.locator('.stage-link', { hasText: 'Vorrunde' }).click();
  await page.evaluate(() => (window as any).appLoadPage('StageInput', ''));
  await shot(page, '11-ergebniseingabe');
  await page.evaluate(() => (window as any).appLoadPage('StageResult', ''));
  await shot(page, '12-gruppentabelle');
  await page.evaluate(() => (window as any).appLoadPage('StageScoreSheet', ''));
  await shot(page, '14-spielzettel');

  // neue Spielphase: Dialog und Spielsystem
  await page.evaluate(() => (window as any).appLoadPage('TourneyInfo', ''));
  await page.locator('tr', { hasText: 'B-Klasse Damen Einzel' }).locator('.comp-link').click();
  await page.locator('button[id*="BtnStartStage"]').click();
  await page.locator('input[id*="DlgStageStart_InputNameId"]').fill('Vorrunde');
  await shot(page, '08-spielphase-starten', '.modal.show .modal-content');
  await page.locator('button[id*="DlgStageStart_ApplyId"]').click();
  await expect(page.locator('#DlgStageStart_ModalId')).not.toBeVisible({ timeout: 5000 });
  await shot(page, '09-spielsystem');

  // Ergebnisliste und Urkunde
  await page.evaluate(() => (window as any).appLoadPage('ResultList', ''));
  await shot(page, '16-ergebnisliste');
  await page.evaluate(() => (window as any).appLoadPage('Certificate', 'Ehfer, Maik|S-Klasse Herren Einzel|1'));
  await shot(page, '15-urkunde');

  // neues Turnier anlegen (Formular) in einem frischen Browser ohne lokales Turnier
  const ctx = await browser.newContext({ ignoreHTTPSErrors: true, locale: 'de-DE', viewport: { width: 1280, height: 800 } });
  const p2 = await ctx.newPage();
  const main2 = new MainViewPage(p2);
  await main2.goto();
  await clean(p2);
  await main2.startFullTourney();
  await expect(p2.locator('#Main_ContentId input').first()).toBeVisible({ timeout: 10000 });
  await shot(p2, '04-turnier-anlegen');
  await ctx.close();
});
