import { test, expect } from '@playwright/test';

/**
 * Social-Media-Buttons im Footer. Die Links kommen aus server/docker/<env>/.env (SOCIAL_*_URL)
 * über die data-Attribute des Param-Elements; der Test richtet sich nach der aktuellen Konfiguration.
 */
test.describe('Footer: Social-Media-Buttons', () => {

  test('TC-FT-01 [🌐]: Buttons erscheinen genau für die konfigurierten Netzwerke mit korrektem Link', async ({ page }) => {
    await page.goto('/');
    const footer = page.locator('footer.footer');
    await expect(footer).toBeVisible({ timeout: 10000 });

    const param = page.locator('#Main_ParamId');
    const networks = [
      { label: 'Facebook', attr: 'data-socialfacebook', icon: 'bi-facebook' },
      { label: 'X', attr: 'data-socialx', icon: 'bi-twitter-x' },
      { label: 'Instagram', attr: 'data-socialinstagram', icon: 'bi-instagram' },
    ];

    let configured = 0;
    for (const n of networks) {
      const url = (await param.getAttribute(n.attr)) ?? '';
      const link = footer.locator(`a[data-social="${n.label}"]`);
      if (url) {
        configured++;
        await expect(link).toHaveAttribute('href', url);
        await expect(link).toHaveAttribute('target', '_blank');
        await expect(link).toHaveAttribute('rel', /noopener/);
        await expect(link).toHaveAttribute('aria-label', new RegExp(n.label));
        await expect(link.locator(`i.${n.icon}`)).toHaveCount(1);
      } else {
        await expect(link).toHaveCount(0);
      }
    }
    // Ohne konfigurierte Links kein leerer Social-Bereich
    await expect(footer.locator('.footer-social')).toHaveCount(configured > 0 ? 1 : 0);
  });
});
