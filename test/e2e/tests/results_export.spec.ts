import { test, expect, Page } from '@playwright/test';
import * as fs from 'fs';
import { MainViewPage } from '../pages/MainViewPage';

/**
 * UseCase 43: Export der Ergebnisse als CSV für Excel und Druckansicht (PDF über den Druckdialog) auf der Seite
 * „Platzierungen & Ergebnisse“ (pages.ResultList, shared.utils.Csv).
 *
 * Demo-Turnier: beendete Spiele ohne Freilose = Vorrunde (45) + Endrunde (1) von Wettbewerb 1 und 2 Gruppenspiele von
 * Wettbewerb 2.
 */
const MATCHES_FINISHED = 48;

/** Öffnet die Gesamtansicht der Ergebnisse im Demo-Turnier. */
async function openResults(page: Page) {
  const mainView = new MainViewPage(page);
  await mainView.goto();
  await mainView.startDemo();
  await expect(async () => {
    await page.evaluate(() => (window as any).appLoadPage('ResultList', ''));
    await expect(page.locator('[data-testid="results-export"]')).toBeVisible({ timeout: 1000 });
    await expect(page.locator('[data-testid="stats-row"]').first()).toBeAttached({ timeout: 1000 });
  }).toPass({ timeout: 15000 });
}

/** Lädt eine Auswertung herunter und liefert Dateiname und Inhalt. */
async function download(page: Page, kind: string): Promise<{ name: string; text: string }> {
  await page.locator('[data-testid="results-export"]').click();
  const [dl] = await Promise.all([page.waitForEvent('download'), page.locator(`[data-testid="export-${kind}"]`).click()]);
  return { name: dl.suggestedFilename(), text: fs.readFileSync((await dl.path())!, 'utf8') };
}

/** Zeilen einer CSV-Datei (ohne BOM und letzte Leerzeile). */
const lines = (text: string) => text.replace(/^﻿/, '').split('\r\n').filter(l => l !== '');

test.describe('UseCase 43: Export und Druckansicht der Ergebnisse', () => {

  test('TC-EXPORT-01 [🌐]: CSV-Dateien für Excel: Platzierungen, Spielergebnisse, Medaillenspiegel, Spielerstatistik', async ({ page }) => {
    await openResults(page);

    const places = await download(page, 'places');
    expect(places.name).toMatch(/_Platzierungen\.csv$/);
    expect(places.text.startsWith('﻿')).toBe(true);
    expect(lines(places.text)[0]).toBe('Wettbewerb;Platz;Name, Vorname;Verein');
    const shownPlaces = await page.locator('.comp-card-collapse tbody tr td:nth-child(2)').count();
    expect(lines(places.text).length - 1).toBe(shownPlaces);

    const matches = await download(page, 'matches');
    const m = lines(matches.text);
    expect(m[0]).toBe('Wettbewerb;Abschnitt;Runde;Teilnehmer A;Verein A;Teilnehmer B;Verein B;Sätze;Bälle');
    expect(m.length - 1).toBe(MATCHES_FINISHED);
    expect(m.slice(1).every(l => /;\d+:\d+;[\d: ]*$/.test(l))).toBe(true);
    expect(m.some(l => l.includes(';Viertelfinale;'))).toBe(true);
    expect(m.some(l => l.includes(';Gruppe '))).toBe(true);

    const medals = await download(page, 'medals');
    expect(lines(medals.text)[0]).toBe('Rang;Verein;Gold;Silber;Bronze;Gesamt');

    const stats = await download(page, 'stats');
    const s = lines(stats.text);
    expect(s[0]).toContain('Name, Vorname;Verein;Spiele;Siege;Niederlagen');
    expect(s.length - 1).toBe(await page.locator('[data-testid="stats-row"]').count());
  });

  test('TC-EXPORT-02 [🌐]: Druckansicht zeigt alle Auswertungen aufgeklappt, ohne Navigation', async ({ page }) => {
    await openResults(page);
    await expect(page.locator('#results-root .print-only').first()).toBeHidden();   // nur im Druck
    await page.evaluate(() => { (window as any).print = () => { (window as any).__printed = true; }; });
    await page.locator('[data-testid="results-print"]').click();
    expect(await page.evaluate(() => (window as any).__printed)).toBe(true);
    await expect(page.locator('body')).toHaveClass(/tourney-print-results/);

    await page.emulateMedia({ media: 'print' });
    await expect(page.locator('#results-pane-places')).toBeVisible();
    await expect(page.locator('#results-pane-medals')).toBeVisible();
    await expect(page.locator('#results-pane-stats')).toBeVisible();
    await expect(page.locator('#collapse-comp-1 tbody tr').first()).toBeVisible();
    await expect(page.locator('#results-root .nav-tabs')).toBeHidden();
    await expect(page.locator('[data-testid="results-export"]')).toBeHidden();
    await expect(page.locator('#results-root .print-only').first()).toBeVisible();
    // ausgeblendete Spalte „Längstes Spiel“ wird gedruckt
    await expect(page.locator('[data-testid="stats-longest-player"]').first()).toBeVisible();
    await expect(page.locator('#collapse-comp-1 .btn').first()).toBeHidden();   // Urkunden-Knopf nicht drucken
  });
});
