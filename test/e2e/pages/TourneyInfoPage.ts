import { Page, Locator, expect } from '@playwright/test';

export class TourneyInfoPage {
  readonly page: Page;
  readonly cardHeaderTitle: Locator;
  readonly btnAddComp: Locator;
  readonly btnEditStammdaten: Locator;
  readonly btnPrintStammblatt: Locator;
  readonly btnToggleStatus: Locator;
  readonly btnDeleteTourney: Locator;
  readonly compTable: Locator;

  constructor(page: Page) {
    this.page = page;
    this.cardHeaderTitle = page.locator('.card-header h3');
    this.btnAddComp = page.locator('button[id*="BtnAddComp"], button:has-text("Wettbewerb hinzufügen")');
    this.btnEditStammdaten = page.locator('button[id*="BtnEditStammdaten"], button:has-text("Stammdaten"), button:has-text("bearbeiten")');
    this.btnPrintStammblatt = page.locator('button[id*="BtnPrintStammblatt"], button:has-text("PDF")');
    this.btnToggleStatus = page.locator('button[id*="BtnToggleStatus"]');
    this.btnDeleteTourney = page.locator('button[id*="BtnDeleteTourney"]');
    this.compTable = page.locator('table');
  }

  async goto() {
    await this.page.goto('/');
    await this.page.waitForLoadState('domcontentloaded');
    await this.page.evaluate(() => {
      if (typeof (window as any).appLoadPage === 'function') {
        (window as any).appLoadPage('TourneyInfo', '');
      }
    });
  }
}
