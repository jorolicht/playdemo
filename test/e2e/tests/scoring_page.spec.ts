import { test, expect } from '@playwright/test';

/**
 * UseCase 34: Statische Seite „Wertungssystem“ (site/content/wertung.html und wertung_en.html), verlinkt aus der Hilfe.
 *
 * Das Server-HTML enthält die Seite schon; der Client ersetzt sie beim Start (#wp-content-body). Geklickt und geprüft
 * wird erst danach, sonst überschreibt die Übernahme durch den Client die Navigation. Stellt der Client die Seite beim Start
 * ein zweites Mal dar, kann ein früher Klick verloren gehen; er wird dann wiederholt (toPass).
 */
test.describe('UseCase 34: Wertungssystem', () => {

  test('TC-WRT-01 [🌐]: Hilfe verweist auf das Wertungssystem; die Seite hat Inhaltsverzeichnis und alle Abschnitte', async ({ page }) => {
    await page.goto('/hilfe/');
    const link = page.locator('#wp-content-body [data-testid="hlp-scoring-link"] a');
    await expect(link).toHaveAttribute('href', '/wertung/', { timeout: 15000 });
    await expect(async () => {
      await link.click();
      await expect(page).toHaveURL(/\/wertung\/$/, { timeout: 2000 });
    }).toPass({ timeout: 15000 });
    await expect(page.locator('#wp-content-body .wrt-title')).toHaveText('Wertungssystem', { timeout: 10000 });
    const toc = page.locator('.wrt-toc a');
    await expect(toc).toHaveCount(11);
    for (const href of await toc.evaluateAll(as => as.map(a => (a as HTMLAnchorElement).getAttribute('href')))) {
      await expect(page.locator(href!), href!).toHaveCount(1);
    }
    // Kurzschreibweise und Tabellenkriterien
    await expect(page.locator('#kurzschreibweise tr', { hasText: '12:10' })).toContainText('10');
    await expect(page.locator('#tabelle')).toContainText('Balldifferenz');
    await expect(page.locator('#spielsysteme')).toContainText('Bundessystem (Werner-Scheffler)');
    // Verweis in Abschnitt 9 der Hilfe
    await page.goto('/hilfe/');
    await expect(page.locator('#wp-content-body #ergebnisse a[href="/wertung/#tabelle"]')).toHaveCount(1, { timeout: 15000 });
  });

  test('TC-WRT-02 [🌐]: /wertung/ und /en/scoring/ indexierbar, per hreflang verknüpft und in der Sitemap', async ({ request }) => {
    for (const [url, title, other] of [['/wertung/', 'Wertungssystem', '/en/scoring/'], ['/en/scoring/', 'Scoring System', '/wertung/']]) {
      const res = await request.get(url);
      expect(res.status(), url).toBe(200);
      const html = await res.text();
      expect(html, url).toContain(title);
      expect(html, url).not.toMatch(/<meta name=.robots. content=.[^']*noindex/);
      expect(html, url).toMatch(new RegExp(`rel="canonical" href="[^"]*${url}"`));
      expect(html, url).toMatch(new RegExp(`hreflang="[a-z-]+" href="[^"]*${other}"`));
    }
    const sitemap = await (await request.get('/wp-sitemap-tourneystatic-1.xml')).text();
    expect(sitemap).toMatch(/<loc>[^<]*\/wertung\/<\/loc>/);
    expect(sitemap).toMatch(/<loc>[^<]*\/en\/scoring\/<\/loc>/);
  });

  test('TC-WRT-03 [🌐]: Englische Hilfe verweist auf die englische Fassung', async ({ page }) => {
    await page.goto('/en/help/');
    const link = page.locator('#wp-content-body [data-testid="hlp-scoring-link"] a');
    await expect(link).toHaveAttribute('href', '/en/scoring/', { timeout: 15000 });
    await expect(async () => {
      await link.click();
      await expect(page).toHaveURL(/\/en\/scoring\/$/, { timeout: 2000 });
    }).toPass({ timeout: 15000 });
    await expect(page.locator('#wp-content-body .wrt-title')).toHaveText('Scoring System', { timeout: 10000 });
    await expect(page.locator('.wrt-toc a')).toHaveCount(11);
    await expect(page.locator('#tabelle')).toContainText('point difference');
  });
});
