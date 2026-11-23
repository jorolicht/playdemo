import { test, expect } from '@playwright/test';

/**
 * UseCase 31: Hilfe & Benutzerhandbuch (site/content/hilfe.html, Screenshots aus test/e2e/docs).
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

  test('TC-HELP-02 [🌐]: /hilfe/ indexierbar und in der Sitemap; englischer Platzhalter /en/help/ mit noindex', async ({ request }) => {
    const de = await request.get('/hilfe/');
    expect(de.status()).toBe(200);
    const deHtml = await de.text();
    expect(deHtml).toContain('Hilfe &amp; Benutzerhandbuch');
    expect(deHtml).not.toMatch(/<meta name=.robots. content=.[^']*noindex/);
    expect(deHtml).toMatch(/rel="canonical" href="[^"]*\/hilfe\/"/);
    expect(deHtml).not.toContain('hreflang="en"');

    const en = await request.get('/en/help/');
    expect(en.status()).toBe(200);
    const enHtml = await en.text();
    expect(enHtml).toContain('Help &amp; User Guide');
    expect(enHtml).toMatch(/<meta name=.robots. content=.[^']*noindex/);

    const sitemap = await (await request.get('/wp-sitemap-tourneystatic-1.xml')).text();
    expect(sitemap).toMatch(/<loc>[^<]*\/hilfe\/<\/loc>/);
    expect(sitemap).not.toContain('/en/help/');
  });
});
