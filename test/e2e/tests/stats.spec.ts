import { test, expect, Page } from '@playwright/test';
import { execSync } from 'child_process';
import { LoginPage } from '../pages/LoginPage';

/**
 * Nutzungsstatistik (tourney/v1/stats-hit, Tabelle wp_tourney_stats, Shortcode [tourney_stats]).
 * Die Zähler liegen in der gemeinsamen Datenbank und werden auch von parallel laufenden Tests erhöht
 * (z. B. Homepage-Aufrufe). Daher werden die Meldungen der eigenen Seite mitgezählt und in der
 * Datenbank nur Zuwächse geprüft; seriell und nur in Chromium.
 */
const WP = 'docker exec wp-cli-instance wp --path=/var/www/html --allow-root';

/** Liest den Zähler eines Ereignisses für einen Tag (Standard heute; 0, wenn keine Zeile existiert). */
function hits(event: string, day = 'CURDATE()'): number {
  const out = execSync(`${WP} db query "SELECT hits FROM wp_tourney_stats WHERE day = ${day} AND event = '${event}'" --skip-column-names`).toString().trim();
  return out ? parseInt(out, 10) : 0;
}

/** Zählt die Statistik-Meldungen (stats-hit) einer Seite je Ereignis. */
function trackBeacons(page: Page): Record<string, number> {
  const sent: Record<string, number> = {};
  page.on('request', req => {
    const m = req.url().match(/\/stats-hit\?e=(\w+)/);
    if (m) sent[m[1]] = (sent[m[1]] || 0) + 1;
  });
  return sent;
}

test.describe('Nutzungsstatistik', () => {
  test.describe.configure({ mode: 'serial' });

  test.beforeEach(async ({ browserName }) => {
    test.skip(browserName !== 'chromium', 'Zähler in gemeinsamer Datenbank');
    // Rate-Limit-Transients zurücksetzen (alle Tests laufen von derselben IP-Adresse)
    execSync(`${WP} eval 'global $wpdb; $wpdb->query("DELETE FROM {$wpdb->options} WHERE option_name LIKE \\"_transient%pd_stats_ip_%\\"");'`);
  });

  test('TC-STAT-01 [🌐]: Homepage-Aufruf zählt einmal pro Browsersitzung', async ({ browser }) => {
    const before = hits('home');
    const ctx1 = await browser.newContext();
    const page1 = await ctx1.newPage();
    const sent1 = trackBeacons(page1);
    await page1.goto('/');
    await expect(page1.locator('#MainView_BtnDemo')).toBeVisible({ timeout: 15000 });
    await expect.poll(() => sent1.home).toBe(1);
    await expect.poll(() => hits('home')).toBeGreaterThanOrEqual(before + 1);

    // Neu laden in derselben Sitzung meldet nicht erneut
    await page1.reload();
    await expect(page1.locator('#MainView_BtnDemo')).toBeVisible({ timeout: 15000 });
    await page1.waitForTimeout(1000);
    expect(sent1.home).toBe(1);
    await ctx1.close();

    // Neue Browsersitzung meldet wieder
    const ctx2 = await browser.newContext();
    const page2 = await ctx2.newPage();
    const sent2 = trackBeacons(page2);
    await page2.goto('/');
    await expect.poll(() => sent2.home).toBe(1);
    await ctx2.close();
  });

  test('TC-STAT-02 [🌐]: Klicks auf Demo, Turnier anlegen und Wettbewerb starten werden gezählt', async ({ browser }) => {
    // Je Button eine frische Sitzung, da die App nach dem Start beim erneuten Aufruf das Turnier wiederherstellt
    for (const [btn, event] of [['BtnDemo', 'demo'], ['BtnOption1', 'tourney'], ['BtnOption2', 'comp']]) {
      const before = hits(event);
      const ctx = await browser.newContext();
      const page = await ctx.newPage();
      const sent = trackBeacons(page);
      await page.goto('/');
      const button = page.locator(`#MainView_${btn}`);
      await expect(button).toBeVisible({ timeout: 15000 });
      await button.click();
      await expect.poll(() => sent[event]).toBe(1);
      await expect.poll(() => hits(event)).toBeGreaterThanOrEqual(before + 1);
      await ctx.close();
    }
  });

  test('TC-STAT-03 [🌐]: Unbekannte Ereignisse und Bots werden ignoriert', async ({ request }) => {
    expect((await request.post('/wp-json/tourney/v1/stats-hit?e=bogus')).status()).toBe(204);
    expect(hits('bogus')).toBe(0);
    // Bot-Meldung darf den heutigen Zähler nicht ändern
    const before = hits('tourney');
    expect((await request.post('/wp-json/tourney/v1/stats-hit?e=tourney', { headers: { 'User-Agent': 'Googlebot/2.1' } })).status()).toBe(204);
    expect(hits('tourney')).toBe(before);
  });

  test('TC-STAT-04 [👑]: Shortcode zeigt Tages- und Wochentabelle nur für Administratoren', async () => {
    // Testwert an einem Tag im 30-Tage-Fenster setzen und danach den alten Wert wiederherstellen
    const day = '(CURDATE() - INTERVAL 20 DAY)';
    const old = hits('demo', day);
    execSync(`${WP} db query "REPLACE INTO wp_tourney_stats VALUES (${day}, 'demo', 4711)"`);
    try {
      const render = (user: number, week: boolean) => execSync(
        `${WP} eval 'wp_set_current_user(${user}); ${week ? '$_GET["stats"] = "week"; ' : ''}echo do_shortcode("[tourney_stats]");'`).toString();
      const dayHtml = render(1, false);
      expect((dayHtml.match(/<tr>/g) || []).length).toBe(32); // Kopf + 30 Tage + Summe
      expect(dayHtml).toContain('>4711</td>');
      expect(render(1, true)).toMatch(/KW \d{2}\/\d{4}/);
      expect(render(0, false).trim()).toBe('');
    } finally {
      execSync(`${WP} db query "${old ? `REPLACE INTO wp_tourney_stats VALUES (${day}, 'demo', ${old})` : `DELETE FROM wp_tourney_stats WHERE day = ${day} AND event = 'demo'`}"`);
    }
  });

  test('TC-STAT-05 [👑]: WordPress-Seite mit [tourney_stats] zeigt die Statistik im Theme (auch bei FWS)', async ({ page }) => {
    const adminUser = process.env.WP_ADMIN_USER;
    const adminPassword = process.env.WP_ADMIN_PASSWORD;
    test.skip(!adminUser || !adminPassword, 'WP_ADMIN_USER/WP_ADMIN_PASSWORD nicht gesetzt (Rolle administrator)');
    // Private Testseite mit dem Shortcode anlegen
    const id = execSync(`${WP} post create --post_type=page --post_title=StatistikTest --post_status=private --post_content='[tourney_stats]' --porcelain`).toString().trim();
    try {
      // Anonym: private Seite nicht sichtbar
      await page.goto(`/?page_id=${id}`);
      await expect(page.locator('.tourney-stats')).toHaveCount(0);

      const loginPage = new LoginPage(page);
      await loginPage.goto();
      await loginPage.login(adminUser!, adminPassword!);
      await expect(loginPage.successAlert).toBeVisible({ timeout: 10000 });
      await page.goto(`/?page_id=${id}`);
      await expect(page.locator('.tourney-stats table')).toBeVisible({ timeout: 10000 });
      await page.locator('.tourney-stats a', { hasText: 'Pro Woche' }).click();
      await expect(page.locator('.tourney-stats td').first()).toContainText('KW');
    } finally {
      execSync(`${WP} post delete ${id} --force`);
    }
  });
});
