import { Page, Locator, expect } from '@playwright/test';

export class CompetitionInfoPage {
  readonly page: Page;
  readonly cardHeaderTitle: Locator;
  readonly btnStartStage: Locator;
  readonly btnEditComp: Locator;
  readonly btnToggleStatus: Locator;
  readonly btnDeleteComp: Locator;
  readonly stagesTable: Locator;
  readonly participantsAccordion: Locator;

  constructor(page: Page) {
    this.page = page;
    this.cardHeaderTitle = page.locator('.card-header h3');
    this.btnStartStage = page.locator('button[id*="BtnStartStage"], button:has-text("Austragungsphase")');
    this.btnEditComp = page.locator('button[id*="BtnEditComp"]');
    this.btnToggleStatus = page.locator('button[id*="BtnToggleStatus"]');
    this.btnDeleteComp = page.locator('button[id*="BtnDeleteComp"]');
    this.stagesTable = page.locator('table').first();
    this.participantsAccordion = page.locator('#participantsAccordion');
  }

  async goto(compId: string = '') {
    await this.page.goto('/');
    await this.page.waitForLoadState('domcontentloaded');
    await this.page.evaluate((id) => {
      if (typeof (window as any).appLoadPage === 'function') {
        (window as any).appLoadPage('CompetitionInfo', id);
      }
    }, compId);
  }
}
