import { test, expect } from '@playwright/test';

/**
 * Statische Seiten mit eigener URL (wp-plugin/includes/static-pages.php, Rewrite-Regel):
 * /funktionen/, /preise/, /excel/, /plugin/, /historie/, /impressum/, /datenschutz/, /kontakt/, /agb/.
 */
const PAGES: Record<string, string> = {
  funktionen: 'Funktionen', preise: 'Support', excel: 'Excel', plugin: 'WordPress Plugin', historie: 'Versionshistorie',
  impressum: 'Impressum', datenschutz: 'Datenschutz', kontakt: 'Kontakt', agb: 'Geschäftsbedingungen',
};

test.describe('Statische Seiten mit eigener URL', () => {
  test('TC-STATIC-01 [🌐]: Jede URL liefert 200, Titel, Canonical und den Inhalt im Server-HTML und steht in der Sitemap', async ({ request, browserName }) => {
    test.skip(browserName !== 'chromium', 'Reine HTTP-Prüfung, browserunabhängig');
    const sitemap = await (await request.get('/wp-sitemap-tourneystatic-1.xml')).text();
    for (const [slug, title] of Object.entries(PAGES)) {
      const res = await request.get(`/${slug}/`);
      expect(res.status(), slug).toBe(200);
      const html = await res.text();
      const titles = html.match(/<title>[^<]*<\/title>/g) || [];
      expect(titles, slug).toHaveLength(1);
      expect(titles[0], slug).toContain(title);
      expect(html, slug).toMatch(new RegExp(`rel="canonical" href="[^"]*/${slug}/"`));
      expect(html, slug).toMatch(/<meta name="description" content="[^"]{20,}"/);
      const content = html.slice(html.indexOf('id="Main_ContentId"'), html.indexOf('id="Main_FooterId"'));
      expect(content, slug).toMatch(/<h1[^>]*>[^<]+<\/h1>/);
      expect(content.length, slug).toBeGreaterThan(1000);
      expect(sitemap, slug).toContain(`/${slug}/</loc>`);
    }
    expect((await request.get('/gibtsnicht-xyz/')).status()).toBe(404);
  });

  test('TC-STATIC-02 [🌐]: Direkter Aufruf öffnet die Seite in der App', async ({ page }) => {
    await page.goto('/preise/');
    await expect(page.locator('#wp-content-body')).toBeVisible({ timeout: 15000 });
    await expect(page.locator('.breadcrumb-item.active')).toBeVisible();
    expect(new URL(page.url()).pathname).toBe('/preise/');
  });

  test('TC-STATIC-03 [🌐]: Footer- und Navbar-Links haben echte URLs; Klick ändert die Adresszeile, Zurück führt zur Startseite', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('#MainView_BtnDemo')).toBeVisible({ timeout: 15000 });

    const impressum = page.locator('#Main_FooterId a[href$="/impressum/"]');
    await expect(impressum).toHaveCount(1);
    await expect(page.locator('#Main_NavbarId a[href$="/funktionen/"]').first()).toBeAttached();

    // Klick bleibt in der App (kein Neuladen) und setzt die URL
    await page.evaluate(() => { (window as any).__noReload = true; });
    await impressum.click();
    await expect(page).toHaveURL(/\/impressum\/$/);
    await expect(page.locator('#wp-content-body')).toBeVisible({ timeout: 10000 });
    expect(await page.evaluate(() => (window as any).__noReload)).toBe(true);

    // Zurück: Startseite, Vor: wieder Impressum
    await page.goBack();
    await expect(page).toHaveURL(/\/$/);
    await expect(page.locator('#MainView_BtnDemo')).toBeVisible({ timeout: 10000 });
    await page.goForward();
    await expect(page).toHaveURL(/\/impressum\/$/);
    await expect(page.locator('#wp-content-body')).toBeVisible({ timeout: 10000 });

    // Breadcrumb "Startseite" führt zurück auf "/"
    await page.locator('.breadcrumb a').first().click();
    await expect(page).toHaveURL(/\/$/);
    await expect(page.locator('#MainView_BtnDemo')).toBeVisible({ timeout: 10000 });
  });
});
