import { test, expect } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';
import { MainViewPage } from '../pages/MainViewPage';

/**
 * UseCase 42: Spielerstatistik als Reiter der Seite „Platzierungen & Ergebnisse“ (pages.ResultList,
 * shared.utils.PlayerStats).
 *
 * Die Erwartung wird unabhängig aus den Spielen des Demo-Turniers (server/public/data/demo_tourney.json) berechnet:
 * gewertet sind Spiele mit Status 2 (beendet mit Sieger) nicht gelöschter Abschnitte; Freilose (Status 3) und offene
 * Spiele zählen nicht.
 */
const DEMO = path.resolve(__dirname, '../../../server/public/data/demo_tourney.json');

type Exp = { games: number; wins: number; setsWon: number; setsLost: number; ttrSum: number; ttrCnt: number };

/** Erwartete Statistik je Spielername ("Nachname, Vorname") aus der Demo-Datei. */
function expected(): Map<string, Exp> {
  const t = JSON.parse(fs.readFileSync(DEMO, 'utf8')).tourney.tourney;
  const players = new Map<number, any>(t.players.filter((p: any) => p).map((p: any) => [p.id, p]));
  const name = (id: number) => { const p = players.get(id); return `${p.lastName.trim()}, ${p.firstName.trim()}`; };
  const ttr = (id: number) => players.get(id)?.meta?.ttr || 0;
  const res = new Map<string, Exp>();
  const get = (id: number) => {
    const n = name(id);
    if (!res.has(n)) res.set(n, { games: 0, wins: 0, setsWon: 0, setsLost: 0, ttrSum: 0, ttrCnt: 0 });
    return res.get(n)!;
  };
  const comps = new Set(t.competitions.filter((c: any) => c && !c.deleted).map((c: any) => c.id));
  for (const s of t.stages.filter((s: any) => s && !s.deleted && comps.has(s.coId))) {
    for (const m of s.matches || []) {
      const f = m.content.split('^');
      const gr = m.stageFormat === 'RR' || m.stageFormat === 'GR';
      const [st, s1, s2] = gr ? [f[13], +f[14], +f[15]] : m.stageFormat === 'KO' ? [f[11], +f[12], +f[13]] : [f[9], +f[10], +f[11]];
      if (st !== '2' || s1 === s2) continue;
      const a = +f[1].replace(/\D/g, ''), b = +f[2].replace(/\D/g, '');
      for (const [p, o, sw, sl] of [[a, b, s1, s2], [b, a, s2, s1]] as const) {
        const e = get(p);
        e.games++; e.wins += sw > sl ? 1 : 0; e.setsWon += sw; e.setsLost += sl;
        if (ttr(p) > 0 && ttr(o) > 0) {
          e.ttrSum += (sw > sl ? 1 : 0) - 1 / (1 + Math.pow(10, (ttr(o) - ttr(p)) / 150));
          e.ttrCnt++;
        }
      }
    }
  }
  return res;
}

test.describe('UseCase 42: Spielerstatistik', () => {

  test('TC-STAT-01 [👤]: Spiele, Siege, Sätze und TTR-Veränderung je Spieler; längstes Spiel', async ({ page }) => {
    const mainView = new MainViewPage(page);
    await mainView.goto();
    await mainView.startDemo();

    await expect(async () => {
      await page.evaluate(() => (window as any).appLoadPage('ResultList', ''));
      await page.locator('[data-testid="results-tab-stats"]').click({ timeout: 1000 });
      await expect(page.locator('[data-testid="stats-row"]').first()).toBeVisible({ timeout: 1000 });
    }).toPass({ timeout: 15000 });

    const exp = expected();
    const rows = page.locator('[data-testid="stats-row"]');
    await expect(rows).toHaveCount(exp.size);
    const shown = await rows.evaluateAll(rs => rs.map(r => {
      const txt = (k: string) => r.querySelector(`[data-testid="stats-${k}"]`)!.textContent!.trim();
      return { name: txt('name'), games: +txt('games'), winloss: txt('winloss'), sets: txt('sets'), ttr: txt('ttr'), longest: txt('longest-player') };
    }));
    for (const s of shown) {
      const e = exp.get(s.name)!;
      expect(e, s.name).toBeTruthy();
      expect(s.games, s.name).toBe(e.games);
      expect(s.winloss, s.name).toBe(`${e.wins}:${e.games - e.wins}`);
      expect(s.sets, s.name).toBe(`${e.setsWon}:${e.setsLost}`);
      const d = Math.round(e.ttrSum * 16);
      expect(s.ttr, s.name).toBe(e.ttrCnt === 0 ? '–' : d > 0 ? `+${d}` : `${d}`);
      expect(s.longest, s.name).toMatch(/^\d+:\d+ gegen .+\(.+\)$/);
    }
    // Sortierung nach Siegen absteigend
    const wins = shown.map(s => +s.winloss.split(':')[0]);
    expect(wins).toEqual([...wins].sort((a, b) => b - a));
    await expect(page.locator('[data-testid="stats-longest"]')).toContainText(/Längstes Spiel des Turniers: .+ – .+ \d+:\d+/);
  });
});
