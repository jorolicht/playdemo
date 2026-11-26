import { test, expect } from '@playwright/test';
import { MainViewPage } from '../pages/MainViewPage';

/**
 * UseCase 41: Medaillenspiegel (Vereinswertung) als Reiter der Seite „Platzierungen & Ergebnisse“ (pages.ResultList,
 * shared.utils.MedalTable).
 *
 * Demo-Turnier: Wettbewerb 1 „S-Klasse Herren Einzel“ hat die beendete Vorrunde (Abschnitt 3) und die laufende Endrunde
 * (Abschnitt 4); die übrigen Wettbewerbe sind nicht abgeschlossen. Gewertet werden nur abgeschlossene Wettbewerbe bzw.
 * solche mit beendetem Urkundenabschnitt.
 */
test.describe('UseCase 41: Medaillenspiegel', () => {

  test('TC-MEDAL-01 [👤]: Medaillenspiegel zählt nur Wettbewerbe mit endgültigen Platzierungen', async ({ page }) => {
    const mainView = new MainViewPage(page);
    await mainView.goto();
    await mainView.startDemo();

    const radio = (stageId: number) => page.locator(`#CompetitionInfo_RadioCert-${stageId}`);
    const openMedals = async () => {
      await page.evaluate(() => (window as any).appLoadPage('ResultList', ''));
      await page.locator('[data-testid="results-tab-medals"]').click();
      await expect(page.locator('#results-pane-medals')).toBeVisible();
    };

    // Ohne Auswahl gilt der letzte Abschnitt (Endrunde, läuft noch) → nichts gewertet
    await expect(async () => {
      await page.evaluate(() => (window as any).appLoadPage('CompetitionInfo', '1'));
      await expect(radio(3)).toBeVisible({ timeout: 1000 });
    }).toPass({ timeout: 10000 });
    await openMedals();
    await expect(page.locator('[data-testid="medal-empty"]')).toBeVisible();
    await expect(page.locator('[data-testid="medal-counted"]')).toContainText('0 von 4');

    // Urkundenabschnitt = beendete Vorrunde → Wettbewerb 1 zählt
    await page.evaluate(() => (window as any).appLoadPage('CompetitionInfo', '1'));
    await radio(3).click();
    await expect(radio(3)).toBeChecked();
    await openMedals();
    await expect(page.locator('[data-testid="medal-counted"]')).toContainText('1 von 4');

    // Erwartung aus der Platzierungsliste von Wettbewerb 1: Vereine der Plätze 1–3
    const placeRows = page.locator('#collapse-comp-1 tbody tr');
    const placed = await placeRows.evaluateAll(rows => rows.map(r => ({
      place: parseInt((r.querySelector('td')?.textContent || '').replace(/\D+/g, ' ').trim().split(' ')[0] || '0', 10),
      club: (r.querySelectorAll('td')[2]?.textContent || '').trim(),
    })));
    const expected = new Map<string, number[]>();
    for (const p of placed.filter(p => p.place >= 1 && p.place <= 3 && p.club)) {
      const m = expected.get(p.club) || [0, 0, 0];
      m[p.place - 1]++;
      expected.set(p.club, m);
    }
    expect(expected.size).toBeGreaterThan(0);

    const rows = page.locator('[data-testid="medal-row"]');
    await expect(rows).toHaveCount(expected.size);
    const shown = await rows.evaluateAll(rs => rs.map(r => ({
      rank: r.querySelector('[data-testid="medal-rank"]')!.textContent!.trim(),
      club: r.querySelector('[data-testid="medal-club"]')!.textContent!.trim(),
      m: ['gold', 'silver', 'bronze'].map(k => Number(r.querySelector(`[data-testid="medal-${k}"]`)!.textContent)),
      total: Number(r.querySelector('[data-testid="medal-total"]')!.textContent),
    })));
    for (const s of shown) {
      expect(expected.get(s.club), s.club).toEqual(s.m);
      expect(s.total).toBe(s.m[0] + s.m[1] + s.m[2]);
    }
    // Reihenfolge: Gold, dann Silber, dann Bronze absteigend; erster Rang ist 1.
    expect(shown[0].rank).toBe('1.');
    for (let i = 1; i < shown.length; i++) {
      const [a, b] = [shown[i - 1].m, shown[i].m];
      expect(a[0] > b[0] || (a[0] === b[0] && (a[1] > b[1] || (a[1] === b[1] && a[2] >= b[2])))).toBe(true);
    }

    // Einzelner Wettbewerb: kein Medaillenspiegel
    await page.evaluate(() => (window as any).appLoadPage('ResultList', '1'));
    await expect(page.locator('[data-testid="results-tab-medals"]')).toHaveCount(0);
  });
});
