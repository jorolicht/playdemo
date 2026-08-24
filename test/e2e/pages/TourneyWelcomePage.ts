import { Page, Locator, expect } from '@playwright/test';

/**
 * Page Object Model representing TourneyWelcome (Public View-Mode Tournament Page).
 */
export class TourneyWelcomePage {
  readonly page: Page;
  readonly cardHeaderTitle: Locator;
  readonly mapImage: Locator;
  readonly dsgvoMapBadge: Locator;
  readonly compTable: Locator;

  constructor(page: Page) {
    this.page = page;
    this.cardHeaderTitle = page.locator('.card-header h3');
    this.mapImage = page.locator('img[alt="Veranstaltungsort Karte"]');
    this.dsgvoMapBadge = page.locator('.badge:has-text("DSGVO-konform")');
    this.compTable = page.locator('table');
  }

  async assertLoaded() {
    await expect(this.page).toHaveURL(/.*#TourneyWelcome/);
  }
}
