import { Page, Locator, expect } from '@playwright/test';

/**
 * Page Object Model representing CompetitionWelcome (Public View-Mode Competition Page).
 */
export class CompetitionWelcomePage {
  readonly page: Page;
  readonly cardHeaderTitle: Locator;
  readonly stagesTable: Locator;
  readonly participantsAccordion: Locator;

  constructor(page: Page) {
    this.page = page;
    this.cardHeaderTitle = page.locator('.card-header h3');
    this.stagesTable = page.locator('table').first();
    this.participantsAccordion = page.locator('#participantsAccordion');
  }

  async assertLoaded() {
    await expect(this.page).toHaveURL(/.*#CompetitionWelcome/);
  }
}
