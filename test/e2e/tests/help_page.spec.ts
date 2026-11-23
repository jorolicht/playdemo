import { test, expect } from '@playwright/test';

/**
 * UseCase 31: Hilfe & Benutzerhandbuch (site/content/hilfe.html und hilfe_en.html, Screenshots aus test/e2e/docs).
 */
test.describe('UseCase 31: Hilfe & Benutzerhandbuch', () => {

  test('TC-HELP-01 [🌐]: Menüpunkt „Hilfe“ öffnet das Handbuch mit Inhaltsverzeichnis und allen Bildern', async ({ page }) => {
    await page.goto('/');
    await page.locator('#navbarMain a.nav-link', { hasText: /^Hilfe$/ }).click();
    await expect(page).toHaveURL(/\/hilfe\/$/);
    const toc = page.locator('.hlp-toc a');
    await expect(toc).toHaveCount(18);
    // jeder Eintrag des Inhaltsverzeichnisses hat ein Ziel
    for (const href of await toc.evaluateAll(as => as.map(a => (a as HTMLAnchorElement).getAttribute('href')))) {
      await expect(page.locator(href!), href!).toHaveCount(1);
    }
    // alle Screenshots geladen
    const imgs = page.locator('.hlp-fig img');
    await expect(imgs).toHaveCount(20);
    for (const img of await imgs.all()) {
      await img.scrollIntoViewIfNeeded();
      await expect.poll(() => img.evaluate((el: HTMLImageElement) => el.complete ? el.naturalWidth : 0)).toBeGreaterThan(0);
    }
  });

  test('TC-HELP-02 [🌐]: /hilfe/ und /en/help/ indexierbar, per hreflang verknüpft und in der Sitemap', async ({ request }) => {
    for (const [url, title, other] of [['/hilfe/', 'Hilfe &amp; Benutzerhandbuch', '/en/help/'], ['/en/help/', 'Help &amp; User Guide', '/hilfe/']]) {
      const res = await request.get(url);
      expect(res.status(), url).toBe(200);
      const html = await res.text();
      expect(html, url).toContain(title);
      expect(html, url).not.toMatch(/<meta name=.robots. content=.[^']*noindex/);
      expect(html, url).toMatch(new RegExp(`rel="canonical" href="[^"]*${url}"`));
      expect(html, url).toMatch(new RegExp(`hreflang="[a-z-]+" href="[^"]*${other}"`));
    }
    const sitemap = await (await request.get('/wp-sitemap-tourneystatic-1.xml')).text();
    expect(sitemap).toMatch(/<loc>[^<]*\/hilfe\/<\/loc>/);
    expect(sitemap).toMatch(/<loc>[^<]*\/en\/help\/<\/loc>/);
  });

  test('TC-HELP-03 [🌐]: Englische Fassung mit englischen Screenshots', async ({ page }) => {
    await page.goto('/en/help/');
    await expect(page.locator('.hlp-toc a')).toHaveCount(18);
    const imgs = page.locator('.hlp-fig img');
    await expect(imgs).toHaveCount(20);
    for (const img of await imgs.all()) {
      expect(await img.getAttribute('src')).toContain('/pages/help/');
      await img.scrollIntoViewIfNeeded();
      await expect.poll(() => img.evaluate((el: HTMLImageElement) => el.complete ? el.naturalWidth : 0)).toBeGreaterThan(0);
    }
  });
});
