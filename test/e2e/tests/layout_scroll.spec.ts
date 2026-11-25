import { test, expect, Page } from '@playwright/test';

/**
 * Seitenlayout der Website-Variante (tourney_render_direct_blank_page in wp-plugin/tourney.php):
 * nur das Fenster scrollt (ein senkrechter Scrollbalken, auch mit WordPress-Admin-Leiste), die App füllt genau das
 * Fenster – kurze Seiten haben keinen Scrollbalken, der Footer steht dann am unteren Fensterrand.
 */

const USER = process.env.WP_ADMIN_USER || '';
const PASS = process.env.WP_ADMIN_PASSWORD || '';

/** Scrollzustand: welche Elemente senkrecht scrollen, Dokumenthöhe, Unterkante des Footers. */
async function layout(page: Page) {
  await expect(page.locator('#Main_FooterId footer')).toBeVisible({ timeout: 15000 });
  return await page.evaluate(() => {
    const scrolls = (e: Element) => /(auto|scroll)/.test(getComputedStyle(e).overflowY) && e.scrollHeight > e.clientHeight + 1;
    const scrollers = [...document.querySelectorAll('body, body *')].filter(scrolls).map(e => e.tagName + (e.id ? '#' + e.id : ''));
    const f = document.getElementById('Main_FooterId')!.getBoundingClientRect();
    return { scrollers, docH: document.documentElement.scrollHeight, vh: innerHeight, footerBottom: Math.round(f.bottom + scrollY) };
  });
}

test.describe('Layout: Scrollbalken und Footer', () => {
  test.use({ viewport: { width: 1280, height: 900 } });

  test('TC-LAYOUT-01 [🌐]: Kurze Seite ohne Scrollbalken, Footer am Fensterrand bzw. direkt unter dem Inhalt', async ({ page }) => {
    await page.goto('/');
    const l = await layout(page);
    expect(l.scrollers).toEqual([]);          // body scrollt nicht selbst
    expect(l.docH).toBeLessThanOrEqual(l.vh); // passt ins Fenster
    expect(l.footerBottom).toBeLessThanOrEqual(l.vh);
  });

  test('TC-LAYOUT-02 [🌐]: Lange Seite: nur das Fenster scrollt, Footer am Ende', async ({ page }) => {
    await page.goto('/kontakt/');
    const l = await layout(page);
    expect(l.scrollers).toEqual([]);
    expect(l.docH).toBeGreaterThan(l.vh);
    expect(l.footerBottom).toBe(l.docH);
  });

  test('TC-LAYOUT-03 [👤]: Mit WordPress-Admin-Leiste nur ein Scrollbalken', async ({ page, browserName }) => {
    test.skip(!USER || !PASS, 'WP_ADMIN_USER/WP_ADMIN_PASSWORD nicht gesetzt');
    test.skip(browserName !== 'chromium', 'Anmeldung nur in Chromium');
    await page.goto('/login/');
    await page.locator('input[id*="LoginId"]').fill(USER);
    await page.locator('input[id*="PasswordId"]').fill(PASS);
    await page.locator('button[id*="BtnLogin"]').click();
    await expect.poll(async () => (await page.context().cookies()).some(c => c.name.startsWith('wordpress_logged_in')), { timeout: 10000 }).toBe(true);
    await page.goto('/kontakt/');
    await expect(page.locator('#wpadminbar')).toBeAttached({ timeout: 15000 });
    const l = await layout(page);
    expect(l.scrollers).toEqual([]);
    await page.goto('/');
    const s = await layout(page);
    expect(s.scrollers).toEqual([]);
    expect(s.docH).toBeLessThanOrEqual(s.vh);   // Admin-Leiste ist eingerechnet
  });
});
