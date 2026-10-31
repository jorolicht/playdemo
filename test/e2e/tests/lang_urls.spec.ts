import { test, expect } from '@playwright/test';

/**
 * Sprachfassungen (wp-plugin/includes/lang.php, static-pages.php): Deutsch unter /…, Englisch unter /en/…,
 * mit hreflang, <html lang>, Sprachumschalter und gemerkter Sprache für Seiten ohne Sprach-URL.
 */
const EN_PAGES: Record<string, string> = {
  features: 'funktionen', pricing: 'preise', excel: 'excel', plugin: 'plugin', history: 'historie',
  imprint: 'impressum', privacy: 'datenschutz', contact: 'kontakt', terms: 'agb',
};

test.describe('Sprachfassungen /en/', () => {
  test('TC-LANG-01 [🌐]: /en/ und /en/<seite>/ liefern Englisch mit lang, Canonical, hreflang und Sitemap', async ({ request, browserName }) => {
    test.skip(browserName !== 'chromium', 'Reine HTTP-Prüfung, browserunabhängig');
    const home = await (await request.get('/en/')).text();
    expect(home).toContain('<html lang="en-GB"');
    expect(home).toMatch(/<h1[^>]*>Organize table tennis tournaments/);
    expect(home).toMatch(/rel="canonical" href="[^"]*\/en\/"/);
    expect(home).toContain('property="og:locale" content="en_GB"');
    const de = await (await request.get('/')).text();
    expect(de).toContain('<html lang="de"');
    expect(de).toMatch(/hreflang="en" href="[^"]*\/en\/"/);

    const sitemap = await (await request.get('/wp-sitemap-tourneystatic-1.xml')).text();
    for (const [en, deSlug] of Object.entries(EN_PAGES)) {
      const res = await request.get(`/en/${en}/`);
      expect(res.status(), en).toBe(200);
      const html = await res.text();
      expect(html, en).toContain('<html lang="en-GB"');
      expect(html, en).toMatch(new RegExp(`rel="canonical" href="[^"]*/en/${en}/"`));
      expect(html, en).toMatch(new RegExp(`hreflang="de" href="[^"]*/${deSlug}/"`));
      expect(html, en).toMatch(new RegExp(`hreflang="x-default" href="[^"]*/${deSlug}/"`));
      expect(sitemap, en).toContain(`/en/${en}/</loc>`);
      const deHtml = await (await request.get(`/${deSlug}/`)).text();
      expect(deHtml, deSlug).toMatch(new RegExp(`hreflang="en" href="[^"]*/en/${en}/"`));
    }
    expect((await request.get('/en/impressum/')).status()).toBe(404);
  });

  test('TC-LANG-02 [🌐]: Besuch von /en/ merkt Englisch; deutsche URL zeigt Deutsch, Seiten ohne Sprach-URL die gemerkte Sprache', async ({ page }) => {
    await page.goto('/en/');
    await expect(page.locator('#MainView_BtnDemo')).toBeVisible({ timeout: 15000 });
    await expect(page.locator('h1')).toContainText('Organize table tennis tournaments');
    expect(await page.evaluate(() => localStorage.getItem('tourney_lang'))).toBe('en');
    await expect(page.locator('#Main_FooterId a[href$="/en/imprint/"]')).toHaveCount(1);

    // Deutsche URL: Deutsch, Wahl bleibt gemerkt
    await page.goto('/');
    await expect(page.locator('h1')).toContainText('Tischtennis-Turniere', { timeout: 15000 });
    expect(await page.evaluate(() => localStorage.getItem('tourney_lang'))).toBe('en');
    // Englisch gewählt, deutsche Seite: Hinweis auf die englische Fassung
    await expect(page.locator('#Navbar_LangSwitch a.fw-bold')).toHaveText('DE', { timeout: 15000 });
    await expect(page.locator('#Navbar_LangHint')).toBeVisible();

    // Seite ohne Sprach-URL (Login): gemerkte Sprache Englisch
    await page.goto('/login/');
    await expect(page.locator('#Navbar_LangSwitch a.fw-bold')).toHaveText('EN', { timeout: 15000 });
  });

  test('TC-LANG-03 [🌐]: Umschalter führt zur Gegenstück-URL und merkt die Wahl', async ({ page }) => {
    await page.goto('/impressum/');
    const en = page.locator('#Navbar_LangSwitch a[hreflang="en"]');
    await expect(en).toHaveAttribute('href', /\/en\/imprint\/$/, { timeout: 15000 });
    await en.click();
    await expect(page).toHaveURL(/\/en\/imprint\/$/);
    await expect(page.locator('.breadcrumb-item.active')).toHaveText('Imprint', { timeout: 15000 });

    await page.locator('#Navbar_LangSwitch a[hreflang="de"]').click();
    await expect(page).toHaveURL(/\/impressum\/$/);
    await expect(page.locator('.breadcrumb-item.active')).toHaveText('Impressum', { timeout: 15000 });
    expect(await page.evaluate(() => localStorage.getItem('tourney_lang'))).toBe('de');
  });

  test('TC-LANG-04 [🌐]: Hinweis auf die englische Fassung nur für englischsprachige Besucher ohne gemerkte Wahl', async ({ browser }) => {
    const enCtx = await browser.newContext({ locale: 'en-US' });
    const page = await enCtx.newPage();
    await page.goto('/');
    const hint = page.locator('#Navbar_LangHint');
    await expect(hint).toBeVisible({ timeout: 15000 });
    await expect(hint.locator('a')).toHaveAttribute('href', /\/en\/$/);
    // Schließen merkt Deutsch, ohne neu zu laden
    await page.evaluate(() => { (window as any).__noReload = true; });
    await hint.locator('.btn-close').click();
    await expect(hint).toHaveCount(0);
    expect(await page.evaluate(() => localStorage.getItem('tourney_lang'))).toBe('de');
    expect(await page.evaluate(() => (window as any).__noReload)).toBe(true);
    await enCtx.close();

    const deCtx = await browser.newContext({ locale: 'de-DE' });
    const dePage = await deCtx.newPage();
    await dePage.goto('/');
    await expect(dePage.locator('#Navbar_LangSwitch')).toBeVisible({ timeout: 15000 });
    await expect(dePage.locator('#Navbar_LangHint')).toHaveCount(0);
    await deCtx.close();
  });
});
