import { test, expect } from '@playwright/test';

/**
 * SEO der Website-Variante (includes/seo.php): Prüft das Server-HTML ohne JavaScript,
 * so wie es Crawler und Link-Vorschauen (Facebook, X, WhatsApp) sehen.
 */
test.describe('SEO (Server-HTML)', () => {
  test.beforeEach(async ({ browserName }) => {
    test.skip(browserName !== 'chromium', 'Reine HTTP-Prüfung, browserunabhängig');
  });

  test('TC-SEO-01 [🌐]: Startseite hat genau einen Titel, Description, Open Graph, Twitter Card und JSON-LD', async ({ request }) => {
    const html = await (await request.get('/')).text();
    const titles = html.match(/<title>[^<]*<\/title>/g) || [];
    expect(titles).toHaveLength(1);
    expect(titles[0]).toContain('Tischtennis-Turniere');
    expect(html).toMatch(/<meta name="description" content="[^"]{50,}"/);
    for (const prop of ['og:title', 'og:description', 'og:url', 'og:image', 'og:type']) {
      expect(html).toContain(`property="${prop}"`);
    }
    expect(html).toContain('name="twitter:card" content="summary_large_image"');
    const jsonld = [...html.matchAll(/<script type="application\/ld\+json">([^<]*)<\/script>/g)].map(m => JSON.parse(m[1]));
    expect(jsonld.map(j => j['@type'])).toEqual(expect.arrayContaining(['WebApplication', 'Organization']));

    // Vorschaubild ist erreichbar
    const image = html.match(/property="og:image" content="([^"]+)"/)![1];
    const res = await request.get(image);
    expect(res.status()).toBe(200);
    expect(res.headers()['content-type']).toContain('image/');
  });

  test('TC-SEO-02 [🌐]: Login, Registrierung, Verifizierung und Vorlagen sind noindex und nicht in der Sitemap', async ({ request }) => {
    for (const path of ['/login/', '/registration/', '/verification/']) {
      const html = await (await request.get(path)).text();
      expect(html, path).toMatch(/name='robots' content='[^']*noindex/);
      expect(html, path).not.toContain('property="og:title"');
    }
    const pages = await (await request.get('/wp-sitemap-posts-page-1.xml')).text();
    for (const slug of ['login', 'registration', 'verification', 'template']) {
      expect(pages, slug).not.toContain(`/${slug}/`);
    }
  });

  test('TC-SEO-03 [🌐]: Keine Benutzer-Sitemap, Autorenarchive leiten auf die Startseite, keine öffentliche Benutzerliste', async ({ request }) => {
    const index = await (await request.get('/wp-sitemap.xml')).text();
    expect(index).not.toContain('sitemap-users');
    const author = await request.get('/?author=1', { maxRedirects: 0 });
    expect(author.status()).toBe(301);
    expect(author.headers()['location']).not.toContain('/author/');
    expect((await request.get('/wp-json/wp/v2/users')).status()).toBe(404);
  });

  test('TC-SEO-04 [🌐]: Turnierseite hat Titel und Beschreibung aus den Turnierdaten', async ({ request }) => {
    const sitemap = await (await request.get('/wp-sitemap-posts-tourney-1.xml')).text();
    const url = sitemap.match(/<loc>([^<]+)<\/loc>/)?.[1];
    test.skip(!url, 'Kein öffentliches Turnier vorhanden');
    const html = await (await request.get(url!)).text();
    expect((html.match(/<title>[^<]*<\/title>/g) || [])).toHaveLength(1);
    expect(html).toContain('property="og:type" content="article"');
    expect(html).toMatch(/<meta name="description" content="[^"]{20,}"/);
  });

  test('TC-SEO-05 [🌐]: Startseite liefert ihren Inhalt im Server-HTML, der Client ersetzt ihn beim Start', async ({ request, page }) => {
    // Ohne JavaScript: Überschrift und Karten der Startseite stehen bereits im HTML
    const html = await (await request.get('/')).text();
    const content = html.slice(html.indexOf('id="Main_ContentId"'), html.indexOf('id="Main_FooterId"'));
    expect(content).toMatch(/<h1[^>]*>[^<]{5,}<\/h1>/);
    expect((content.match(/<h3 /g) || []).length).toBe(4);
    expect(content).not.toContain('Wird geladen');

    // Mit JavaScript: vollständige Startseite, Platzhalter-Buttons ersetzt, genau eine Überschrift
    await page.goto('/');
    await expect(page.locator('#MainView_BtnDemo')).toBeVisible({ timeout: 15000 });
    await expect(page.locator('#Main_ContentId span.btn.disabled')).toHaveCount(0);
    await expect(page.locator('#Main_ContentId h1')).toHaveCount(1);
  });
});
