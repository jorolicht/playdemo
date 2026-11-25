/**
 * Lasttest für ein TurnierService-System (Playwright, echte Browser-Sitzungen).
 *
 * Je Turnier meldet sich ein Testkonto (lt01 …, Datei mit "benutzer,passwort" je Zeile) an, legt aus den
 * Demo-Daten ein Cloud-Turnier an und gibt Ergebnisse ein (Gruppen von „D-Klasse Damen Einzel“). Bei jedem dritten
 * Turnier arbeiten zwei weitere Bearbeiter mit: K.-o.-Endrunde von „S-Klasse Herren Einzel“ und Ergebnisliste/
 * Turnierseite (Urkunden). Dazu rufen Zuschauer die öffentliche Turnierseite ab. Gemessen werden die Antwortzeiten
 * je Aktion; parallel überwacht SSH den Server und bricht bei wenig freiem Speicher ab.
 *
 * Aufruf (in test/e2e):
 *   node load/loadtest.mjs --base https://turnier-service.info --users <datei> --tourneys 5 --minutes 6 \
 *        --spectators 3 --ssh turnier --out <ergebnis.json>
 * Die angelegten Turniere werden am Ende gelöscht.
 */
import { chromium } from '@playwright/test';
import { spawn } from 'child_process';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const here = path.dirname(fileURLToPath(import.meta.url));
const arg = (name, def) => { const i = process.argv.indexOf(`--${name}`); return i > 0 ? process.argv[i + 1] : def; };
const BASE = arg('base', 'https://turnier-service.info').replace(/\/$/, '');
const USERS = fs.readFileSync(arg('users'), 'utf8').trim().split('\n').map(l => l.split(','));
const TOURNEYS = Number(arg('tourneys', '5'));
const MINUTES = Number(arg('minutes', '6'));
const SPECTATORS = Number(arg('spectators', '3'));
const SSH = arg('ssh', '');
const OUT = arg('out', 'loadtest-result.json');
const MIN_FREE_MB = 150;

const t0 = Date.now();
const until = t0 + MINUTES * 60000;
let abortReason = '';
const metrics = {};      // Aktion -> [ms]
const errors = {};       // Aktion -> Anzahl
const errorSamples = [];
const server = [];       // Messpunkte des Servers
let skipped = 0;        // Spiele, die sich nicht speichern lassen (kein Fehler des Servers)

const sleep = ms => new Promise(r => setTimeout(r, ms));
const rnd = (a, b) => a + Math.random() * (b - a);
const running = () => !abortReason && Date.now() < until;

/** Misst eine Aktion; Fehler werden gezählt und nicht weitergeworfen. */
async function timed(name, fn) {
  const s = Date.now();
  try {
    const r = await fn();
    (metrics[name] ||= []).push(Date.now() - s);
    return r;
  } catch (e) {
    errors[name] = (errors[name] || 0) + 1;
    if (errorSamples.length < 30) errorSamples.push(`${name}: ${String(e.message || e).split('\n')[0].slice(0, 200)}`);
    return undefined;
  }
}

/** Server-Überwachung per SSH (alle 5 s): freier Speicher, Last, Apache-Prozesse, CPU je Container. */
function startMonitor() {
  if (!SSH) return null;
  const cmd = `while true; do echo "MON $(date +%s) $(awk '/MemAvailable/{print int($2/1024)}' /proc/meminfo) $(cut -d' ' -f1 /proc/loadavg) $(docker exec wp-gmp-instance sh -c 'ps -C apache2 --no-headers | wc -l') $(docker stats --no-stream --format '{{.Name}}={{.CPUPerc}}' wp-gmp-instance wp-db-instance playsrv-instance | tr '\\n' ' ')"; sleep 5; done`;
  const p = spawn('ssh', ['-o', 'BatchMode=yes', SSH, cmd]);
  p.stdout.on('data', d => String(d).split('\n').filter(l => l.startsWith('MON ')).forEach(l => {
    const [, ts, free, load, apache, ...cpu] = l.trim().split(/\s+/);
    const point = { t: Math.round((Number(ts) * 1000 - t0) / 1000), freeMb: Number(free), load: Number(load), apache: Number(apache),
                    cpu: Object.fromEntries(cpu.map(x => x.split('=')).map(([k, v]) => [k.replace('-instance', ''), parseFloat(v)])) };
    server.push(point);
    if (point.freeMb && point.freeMb < MIN_FREE_MB && !abortReason) abortReason = `freier Speicher ${point.freeMb} MB < ${MIN_FREE_MB} MB`;
  }));
  return p;
}

/** Name einer REST-Anfrage für die Statistik, z. B. "api:POST stages-sync". */
function apiName(req) {
  const m = req.url().match(/\/wp-json\/(?:tourney\/v1\/)?([^?]+)/);
  return m ? `api:${req.method()} ${m[1]}` : null;
}

/** Misst alle REST-Anfragen einer Seite (Serverzeit bis zum Ende der Antwort) und zählt Fehlerstatus. */
function trackApi(page) {
  page.on('requestfinished', async req => {
    const name = apiName(req);
    if (!name) return;
    const res = await req.response().catch(() => null);
    if (res && res.status() >= 400) {
      errors[name] = (errors[name] || 0) + 1;
      if (errorSamples.length < 30) errorSamples.push(`${name}: HTTP ${res.status()}`);
      return;
    }
    const tm = req.timing();
    if (tm.responseEnd > 0) (metrics[name] ||= []).push(Math.round(tm.responseEnd));
  });
  page.on('requestfailed', req => {
    const name = apiName(req);
    if (name) { errors[name] = (errors[name] || 0) + 1; if (errorSamples.length < 30) errorSamples.push(`${name}: ${req.failure()?.errorText}`); }
  });
}

/** Neuer angemeldeter Browser-Kontext. */
async function login(browser, user, pass) {
  const ctx = await browser.newContext({ ignoreHTTPSErrors: true, locale: 'de-DE', viewport: { width: 1280, height: 900 } });
  const page = await ctx.newPage();
  page.setDefaultTimeout(30000);
  trackApi(page);
  const ok = await timed('login', async () => {
    // Anmeldeseite der App (eine Seite /login/ gibt es nicht auf jeder Installation)
    await page.goto(`${BASE}/`);
    await page.waitForFunction(() => typeof window.appLoadPage === 'function');
    await page.evaluate(() => window.appLoadPage('UserLogin', ''));
    await page.locator('input[id*="LoginId"]').fill(user.includes('@') ? user : `${user}@lasttest.invalid`);
    await page.locator('input[id*="PasswordId"]').fill(pass);
    await page.locator('button[id*="BtnLogin"]').click();
    for (let i = 0; i < 60; i++) {
      if ((await ctx.cookies()).some(c => c.name.startsWith('wordpress_logged_in'))) return true;
      await sleep(250);
    }
    throw new Error(`login ${user}: kein Cookie`);
  });
  if (!ok) { await ctx.close(); return null; }
  await page.goto(`${BASE}/`);
  await page.waitForFunction(() => typeof window.appLoadPage === 'function');
  return { ctx, page };
}

/** Legt aus den Demo-Daten ein Cloud-Turnier an (wie der Import in der Turnier-Administration). */
async function createTourney(page, i, user) {
  const demo = JSON.parse(fs.readFileSync(path.resolve(here, '../../../wp-plugin/data/demo_tourney.json'), 'utf8'));
  const t = demo.tourney.tourney;
  t.wpId = 0;
  t.name = `Lasttest ${i} ${Date.now().toString(36)}`;
  // muss dem Veranstalter des Kontos entsprechen (Schreibrecht, tourney_user_can_edit_post)
  t.organizer = `Lasttest-Verein ${user.replace(/^lt/, '')}`;
  return await timed('createTourney', () => page.evaluate(async (t) => {
    const p = document.getElementById('Main_ParamId');
    const home = (p.dataset.homeurl || '').replace(/\/$/, '');
    const hdrs = { 'Content-Type': 'application/json', 'X-WP-Nonce': p.dataset.nonce || '' };
    const post = async (route, body, postId = 0) => {
      const r = await fetch(`${home}/wp-json/tourney/v1/${route}${postId ? `?postId=${postId}` : ''}`,
        { method: 'POST', headers: hdrs, body: JSON.stringify(body), credentials: 'include' });
      if (!r.ok) throw new Error(`${route}: ${r.status} ${(await r.text()).slice(0, 120)}`);
      return r.json();
    };
    const res = await post('create', t);
    const id = res.pageId;
    await post('clubs-sync', { version: 0, clubs: t.clubs }, id);
    await post('players-sync', { version: 0, players: t.players }, id);
    await post('competitions-sync', { events: t.competitions.filter(c => c) }, id);
    await post('stages-sync', { events: t.stages.filter(s => s) }, id);
    return { slug: res.slug, postId: id };
  }, t));
}

/** Öffnet die Turnierseite (vollständiger Seitenaufruf) und die Turnierübersicht. */
async function openTourney(page, slug) {
  await timed('pageLoad', async () => {
    await page.goto(`${BASE}/${slug}/`);
    await page.waitForFunction(() => typeof window.appLoadPage === 'function');
    await page.evaluate(() => window.appLoadPage('TourneyInfo', ''));
    await page.locator('.comp-link').first().waitFor();
  });
}

/** Öffnet die Ergebniseingabe einer Stage. */
async function openInput(page, slug, comp, stage) {
  await openTourney(page, slug);
  await page.locator('tr', { hasText: comp }).locator('.comp-link').click();
  const links = page.locator('.stage-link', { hasText: stage });
  await links.first().waitFor();
  for (let k = (await links.count()) - 1; k >= 0; k--) {
    await links.nth(k).click();
    try { await page.locator('[id^="input_"]').first().waitFor({ timeout: 5000 }); return true; } catch { /* nächste */ }
    await page.evaluate(() => window.appLoadPage('CompetitionInfo', ''));
  }
  return false;
}

/** Gibt Ergebnisse ein, solange offene Spiele da sind; Denkpause zwischen den Eingaben. */
async function enterResults(page, slug, comp, stage) {
  if (!(await timed('openInput', () => openInput(page, slug, comp, stage)))) return;
  const skip = new Set();   // Spiele, die sich nicht speichern lassen (z. B. Gegner im K.-o. noch offen)
  while (running()) {
    const ids = await page.locator('input[id^="input_"][id$="_1"]:not([disabled])').evaluateAll(es => es.map(e => e.id));
    const game = ids.map(id => id.split('_')[1]).find(g => !skip.has(g));
    if (!game) return;
    const sets = Math.random() < 0.5 ? ['5', '7', '9'] : ['-6', '8', '-9', '4', '9'];
    for (let s = 0; s < sets.length; s++) {
      const f = page.locator(`#input_${game}_${s + 1}`);
      if (await f.count()) await f.fill(sets[s]);
    }
    const btn = page.locator('tr', { has: page.locator(`#input_${game}_1`) }).locator('button.btn-success');
    if (!(await btn.isEnabled().catch(() => false))) { skip.add(game); skipped++; continue; }
    await timed('saveResult', async () => {
      await btn.click();
      // gespeichert, sobald der Satzstand in der Oberfläche steht (Synchronisation läuft verzögert, siehe api:*)
      await page.waitForFunction(g => (document.getElementById(`sets_${g}`)?.textContent || '').trim() !== '', game, { timeout: 30000 });
    });
    await sleep(rnd(8000, 20000));
  }
}

/** Bearbeiter ohne Eingabe: Turnierseite, Ergebnisliste (Urkunden) und Übersicht im Wechsel. */
async function browse(page, slug) {
  while (running()) {
    await openTourney(page, slug);
    await timed('resultList', async () => {
      await page.evaluate(() => window.appLoadPage('ResultList', ''));
      await page.locator('#Main_ContentId').waitFor();
    });
    await sleep(rnd(15000, 30000));
  }
}

/** Zuschauer: ruft die öffentliche Turnierseite ohne Anmeldung ab. */
async function spectator(slug) {
  await sleep(rnd(0, 20000));
  while (running()) {
    await timed('spectatorGet', async () => {
      const r = await fetch(`${BASE}/${slug}/`, { headers: { 'User-Agent': 'TurnierService-Lasttest' } });
      if (!r.ok) throw new Error(`GET ${r.status}`);
      await r.text();
    });
    await sleep(rnd(20000, 40000));
  }
}

/** Ein Turnier mit seinen Bearbeitern und Zuschauern. */
async function tourney(browser, i, created) {
  const [user, pass] = USERS[(i - 1) % USERS.length];
  await sleep((i - 1) * 3000);   // gestaffelter Start
  const main = await login(browser, user, pass);
  if (!main) return;
  const t = await createTourney(main.page, i, user);
  if (!t) { await main.ctx.close(); return; }
  created.push({ ...t, page: main.page });
  const jobs = [enterResults(main.page, t.slug, 'D-Klasse Damen Einzel', 'Vorrunde')];
  const contexts = [main.ctx];
  if (i % 3 === 0) {
    const ko = await login(browser, user, pass);
    if (ko) { contexts.push(ko.ctx); jobs.push(enterResults(ko.page, t.slug, 'S-Klasse Herren Einzel', 'Endrunde')); }
    const viewer = await login(browser, user, pass);
    if (viewer) { contexts.push(viewer.ctx); jobs.push(browse(viewer.page, t.slug)); }
  }
  for (let s = 0; s < SPECTATORS; s++) jobs.push(spectator(t.slug));
  await Promise.all(jobs);
  // Haupteingeber ohne offene Spiele: bis zum Ende weiter anschauen
  if (running()) await browse(main.page, t.slug);
  for (const c of contexts.slice(1)) await c.close();
}

/** Kennzahlen einer Messreihe. */
function stats(a) {
  const s = [...a].sort((x, y) => x - y);
  const q = p => s[Math.min(s.length - 1, Math.floor(p * s.length))];
  return { n: s.length, p50: q(0.5), p95: q(0.95), max: s[s.length - 1] };
}

function report() {
  const ops = Object.fromEntries([...new Set([...Object.keys(metrics), ...Object.keys(errors)])]
    .map(k => [k, { ...(metrics[k] ? stats(metrics[k]) : { n: 0 }), errors: errors[k] || 0 }]));
  const srv = server.length ? {
    minFreeMb: Math.min(...server.map(p => p.freeMb)), maxLoad: Math.max(...server.map(p => p.load)),
    maxApache: Math.max(...server.map(p => p.apache)),
    maxCpu: Object.fromEntries(['wp-gmp', 'wp-db', 'playsrv'].map(k => [k, Math.max(...server.map(p => p.cpu[k] || 0))])),
  } : {};
  return { tourneys: TOURNEYS, minutes: MINUTES, spectatorsPerTourney: SPECTATORS, abortReason, skippedGames: skipped, ops, server: srv, errorSamples, timeline: server };
}

const monitor = startMonitor();
const browser = await chromium.launch();
const created = [];
const progress = setInterval(() => {
  const r = report();
  const last = server[server.length - 1] || {};
  console.log(`[${Math.round((Date.now() - t0) / 1000)}s] frei=${last.freeMb}MB last=${last.load} apache=${last.apache} ` +
    Object.entries(r.ops).map(([k, v]) => `${k}:${v.n}/${v.p95 ?? '-'}ms${v.errors ? `/E${v.errors}` : ''}`).join(' ') +
    (abortReason ? ` ABBRUCH: ${abortReason}` : ''));
}, 30000);

await Promise.all(Array.from({ length: TOURNEYS }, (_, k) => tourney(browser, k + 1, created).catch(e => {
  errors.tourney = (errors.tourney || 0) + 1; errorSamples.push(`tourney ${k + 1}: ${e.message}`);
})));
clearInterval(progress);

// Aufräumen: angelegte Turniere löschen
for (const c of created) {
  await c.page.evaluate(async (id) => {
    const p = document.getElementById('Main_ParamId');
    const home = (p.dataset.homeurl || '').replace(/\/$/, '');
    await fetch(`${home}/wp-json/tourney/v1/delete?postId=${id}`, { method: 'DELETE', headers: { 'X-WP-Nonce': p.dataset.nonce || '' }, credentials: 'include' });
  }, c.postId).catch(() => { errors.cleanup = (errors.cleanup || 0) + 1; });
}
await browser.close();
monitor?.kill();
const r = report();
fs.writeFileSync(OUT, JSON.stringify(r, null, 2));
console.log(JSON.stringify({ ...r, timeline: undefined }, null, 2));
console.log(`gelöscht: ${created.length} Turniere`);
