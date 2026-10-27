import { test, expect, Locator, Page } from '@playwright/test';
import fs from 'fs';
import path from 'path';

/**
 * Nimmt das Werbevideo (Demo-Turnier) auf. Wird von make_video.sh gestartet, das vorher die
 * Sprachdateien erzeugt (out/voice/durations.json) und danach Video, Sprache und Musik mischt.
 * Ergebnis: out/raw.webm und out/timeline.json (Startzeiten der Sprechertexte relativ zum Videobeginn).
 */

const OUT = path.resolve(__dirname, 'out');
const durations: Record<string, number> = JSON.parse(fs.readFileSync(path.join(OUT, 'voice', 'durations.json'), 'utf-8'));

/** Sichtbarer Mauszeiger mit Klick-Effekt, da Playwright den echten Cursor nicht aufzeichnet. */
const cursorScript = () => {
  const install = () => {
    if (document.getElementById('__promo_cursor')) return;
    const style = document.createElement('style');
    style.textContent = `
      #__promo_cursor { position: fixed; left: 0; top: 0; z-index: 2147483647; pointer-events: none;
        transform: translate(-100px, -100px); filter: drop-shadow(1px 2px 2px rgba(0,0,0,.35)); }
      .__promo_ripple { position: fixed; z-index: 2147483646; pointer-events: none; width: 44px; height: 44px;
        margin: -22px 0 0 -22px; border-radius: 50%; border: 3px solid #f59e0b; background: rgba(245,158,11,.25);
        animation: __promo_ripple .6s ease-out forwards; }
      @keyframes __promo_ripple { from { transform: scale(.3); opacity: 1; } to { transform: scale(1.4); opacity: 0; } }`;
    document.head.appendChild(style);
    const cursor = document.createElement('div');
    cursor.id = '__promo_cursor';
    cursor.innerHTML = '<svg width="24" height="30" viewBox="0 0 14 20"><path d="M0,0 L0,17 L4.5,12.5 L7.5,19 L10,18 L7,11.5 L13,11.5 Z" fill="#fff" stroke="#111" stroke-width="1.2" stroke-linejoin="round"/></svg>';
    document.documentElement.appendChild(cursor);
    document.addEventListener('mousemove', e => {
      cursor.style.transform = `translate(${e.clientX}px, ${e.clientY}px)`;
    }, true);
    document.addEventListener('mousedown', e => {
      const ripple = document.createElement('div');
      ripple.className = '__promo_ripple';
      ripple.style.left = `${e.clientX}px`;
      ripple.style.top = `${e.clientY}px`;
      document.documentElement.appendChild(ripple);
      setTimeout(() => ripple.remove(), 700);
    }, true);
  };
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', install);
  else install();
};

test('Werbevideo Demo-Turnier', async ({ browser }, testInfo) => {
  const context = await browser.newContext({
    baseURL: testInfo.project.use.baseURL,
    ignoreHTTPSErrors: true,
    locale: 'de-DE',
    viewport: { width: 1280, height: 720 },
    deviceScaleFactor: 1.5,
    recordVideo: { dir: path.join(OUT, 'raw'), size: { width: 1920, height: 1080 } },
  });
  await context.addInitScript(cursorScript);
  const page = await context.newPage();
  const t0 = Date.now(); // Videoaufnahme beginnt mit dem Öffnen der Seite
  const segments: { id: string; startMs: number }[] = [];

  /** Startet einen Sprechertext (Zeitstempel für das Mischen) und liefert ein Promise, das nach seiner Dauer erfüllt ist. */
  const narrate = (id: string): Promise<void> => {
    segments.push({ id, startMs: Date.now() - t0 });
    return page.waitForTimeout(durations[id] * 1000);
  };
  const pause = (ms: number) => page.waitForTimeout(ms);

  /** Bewegt den Mauszeiger gleichmäßig auf die Mitte des Elements. */
  const moveTo = async (loc: Locator, steps = 25) => {
    const box = await loc.boundingBox();
    if (!box) throw new Error(`moveTo: Element nicht sichtbar: ${loc}`);
    await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2, { steps });
  };
  const click = async (loc: Locator) => {
    await moveTo(loc);
    await pause(250);
    await loc.click();
  };
  const type = async (loc: Locator, text: string) => {
    await click(loc);
    await page.keyboard.type(text, { delay: 110 });
  };
  const scrollToCenter = async (loc: Locator) => {
    await loc.evaluate(e => e.scrollIntoView({ behavior: 'smooth', block: 'center' }));
    await pause(900);
  };
  const scrollBy = async (y: number) => {
    await page.evaluate(dy => window.scrollBy({ top: dy, behavior: 'smooth' }), y);
    await pause(1000);
  };

  // --- 1) Startseite -------------------------------------------------------------------------
  await page.goto('/');
  const btnDemo = page.locator('#MainView_BtnDemo');
  await expect(btnDemo).toBeVisible({ timeout: 15000 });
  await pause(500);
  const trimStartMs = Date.now() - t0; // leere Ladephase am Anfang abschneiden
  await page.mouse.move(640, 360);
  await pause(2500); // Intro-Jingle ohne Sprache

  let n = narrate('start');
  await moveTo(page.locator('nav a', { hasText: 'Funktionen' }).first(), 40);
  await pause(4000);
  await moveTo(btnDemo, 40);
  await n;
  await pause(400);
  await click(btnDemo);

  // --- 2) Turnierinfo ------------------------------------------------------------------------
  const compCell = page.locator('td', { hasText: 'S-Klasse Herren Einzel' }).first();
  await expect(compCell).toBeVisible({ timeout: 15000 });
  await pause(700);
  n = narrate('tourney');
  await moveTo(page.locator('.card-header', { hasText: /Turnier/ }).first(), 30);
  await pause(2500);
  await moveTo(page.locator('tr', { hasText: 'S-Klasse Herren Einzel' }).locator('.badge').last(), 30);
  await pause(1500);
  await moveTo(page.locator('tr', { hasText: 'B-Klasse Damen Einzel' }).locator('.badge').last(), 20);
  await pause(1500);
  await moveTo(compCell, 30);
  await n;
  await pause(300);
  await click(compCell);

  // --- 3) Wettbewerbsinfo --------------------------------------------------------------------
  const endrunde = page.locator('tr', { hasText: 'Endrunde' }).first();
  await expect(endrunde).toBeVisible({ timeout: 15000 });
  await pause(600);
  n = narrate('competition');
  await moveTo(page.locator('tr', { hasText: 'Vorrunde' }).first().locator('td').first(), 30);
  await pause(3500);
  await moveTo(endrunde.locator('td').first(), 20);
  await pause(2000);
  const btnShow = endrunde.locator('button', { hasText: 'Anzeigen' });
  await moveTo(btnShow, 30);
  await n;
  await pause(300);
  await click(btnShow);

  // --- 4) Wettbewerbsphase: Reiter, Tischeingabe, Ergebnis, Demo-Ergebnisse ------------------
  const tab = (name: string) => page.locator('.nav-tabs a', { hasText: name });
  await expect(tab('Eingabe')).toBeVisible({ timeout: 15000 });
  await pause(600);
  n = narrate('tabs');
  await pause(1700);
  for (const name of ['Administration', 'Auslosung', 'Eingabe', 'Ergebnisse', 'Schiri']) {
    await moveTo(tab(name), 12);
    await pause(550);
  }
  await n;
  await click(tab('Eingabe'));

  const table5 = page.locator('#table_5');
  await expect(table5).toBeVisible({ timeout: 15000 });
  await pause(500);
  await scrollToCenter(table5);
  n = narrate('table');
  await pause(1200);
  await type(table5, '3');
  await page.keyboard.press('Tab'); // change-Event startet das Spiel
  await moveTo(page.locator('#running-time-5'), 20);
  await n;
  await pause(1500); // laufende Zeit sichtbar

  n = narrate('result');
  for (const [i, v] of ['5', '-8', '9', '7'].entries()) {
    await type(page.locator(`#input_5_${i + 1}`), v);
  }
  await pause(400);
  await click(page.locator('[id^="StageInput_SaveMatchBtn-5"]'));
  await n;
  await pause(800);

  const btnDemoScores = page.locator('button[id*="BtnDemoScores"]');
  await scrollToCenter(btnDemoScores);
  n = narrate('demo');
  await click(btnDemoScores);
  await pause(1200);
  await scrollBy(450);
  await n;
  await pause(800);

  // --- 5) Ergebnisse: K.o.-Baum --------------------------------------------------------------
  await scrollBy(-2000);
  await click(tab('Ergebnisse'));
  await expect(page.locator('text=Halbfinale').first()).toBeVisible({ timeout: 15000 });
  await pause(500);
  n = narrate('ko');
  await scrollBy(300);
  await pause(800);
  await scrollBy(300);
  await n;
  await pause(1500);

  // --- 6) Schiri: Schiedsrichterzettel mit QR-Code -------------------------------------------
  await scrollBy(-2000);
  await click(tab('Schiri'));
  await expect(page.locator('.RefereeQRCode').first()).toBeAttached({ timeout: 15000 });
  // Im Demo-Modus erzeugt die App keine QR-Codes (kein Play-Server); für das Video wie im Cloud-Modus erzeugen
  await page.evaluate(async () => {
    const w = window as any;
    if (typeof w.QRCode === 'undefined') {
      await new Promise<void>(resolve => {
        const s = document.createElement('script');
        s.src = 'https://cdnjs.cloudflare.com/ajax/libs/qrcodejs/1.0.0/qrcode.min.js';
        s.onload = () => resolve();
        document.head.appendChild(s);
      });
    }
    document.querySelectorAll<HTMLElement>('.RefereeQRCode').forEach(el => {
      if (el.innerHTML.trim()) return;
      const url = `${location.origin}/srv/referee?gameNo=${el.dataset.gameNo}`;
      new w.QRCode(el, { width: 80, height: 80 }).makeCode(url);
    });
  });
  await pause(500);
  n = narrate('schiri');
  await moveTo(page.locator('.RefereeQRCode').first(), 40);
  await pause(3500);
  await scrollBy(250);
  await n;
  await pause(3000); // Outro-Musik

  const endMs = Date.now() - t0;
  const video = page.video()!;
  await context.close();
  await video.saveAs(path.join(OUT, 'raw.webm'));
  fs.writeFileSync(path.join(OUT, 'timeline.json'), JSON.stringify({ trimStartMs, endMs, segments }, null, 2));
});
