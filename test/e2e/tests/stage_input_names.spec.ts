import { test, expect } from '@playwright/test';
import { MainViewPage } from '../pages/MainViewPage';

/**
 * Spielphase, Reiter „Eingabe“: Einzel werden als „Nachname, Vorname“ angezeigt; ist das länger als 20 Zeichen, wird
 * der Vorname auf den ersten Buchstaben mit Punkt gekürzt. Im Spielinfo-Dialog stehen die Namen immer vollständig
 * (pages.Stage.StageInput, shared.utils.PlayerName).
 *
 * Demo-Turnier, D-Klasse Damen Einzel, Vorrunde, Spiel 3: Brandenburg, Mark – Fehr, Luzie. Für die Kürzung bekommt
 * Spielerin 59 (Fehr, Luzie) beim Laden der Demo-Daten den Nachnamen „Müller-Lüdenscheidt“ (26 Zeichen mit Vorname).
 */
test('TC-SI-13 [🌐]: Eingabe zeigt „Nachname, Vorname“, über 20 Zeichen gekürzt; Spielinfo voll ausgeschrieben', async ({ page }) => {
  await page.route('**/demo_tourney.json*', async route => {
    const res = await route.fetch();
    const data = await res.json();
    const p = data.tourney.tourney.players.find((x: any) => x && x.id === 59);
    p.lastName = 'Müller-Lüdenscheidt';
    await route.fulfill({ response: res, json: data });
  });

  const main = new MainViewPage(page);
  await main.goto();
  await main.startDemo();
  await page.locator('tr', { hasText: 'D-Klasse Damen Einzel' }).locator('.comp-link').click();
  await page.locator('.stage-link', { hasText: 'Vorrunde' }).click();
  await page.evaluate(() => (window as any).appLoadPage('StageInput', ''));

  await expect(page.locator('#nameA-3')).toHaveText('Brandenburg, Mark', { timeout: 10000 });
  await expect(page.locator('#nameB-3')).toHaveText('Müller-Lüdenscheidt, L.');
  // alle Einzelnamen im Format „Nachname, Vorname“ bzw. gekürzt, höchstens 20 Zeichen oder mit gekürztem Vornamen
  const names = await page.locator('[id^="nameA-"], [id^="nameB-"]').allTextContents();
  for (const n of names.map(s => s.trim()).filter(s => s.includes(','))) {
    expect(n.length <= 20 || /, \S\.$/.test(n), n).toBe(true);
  }

  await page.locator('button[id$="InfoMatchBtn-3"]').click();
  const dlg = page.locator('.modal.show');
  await expect(dlg).toBeVisible();
  await expect(dlg).toContainText('Brandenburg, Mark');
  await expect(dlg).toContainText('Müller-Lüdenscheidt, Luzie');
});
