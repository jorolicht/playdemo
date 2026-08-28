import { Page, Locator, expect } from '@playwright/test';

export class StageAdminPage {
  readonly page: Page;
  readonly selectSystem: Locator;
  readonly inputNoGroups: Locator;
  readonly btnStartDraw: Locator;
  readonly btnStartStage: Locator;
  readonly roundsTable: Locator;

  constructor(page: Page) {
    this.page = page;
    this.selectSystem = page.locator('select[id*="SelectSystem"]');
    this.inputNoGroups = page.locator('input[id*="InputNoGroups"]');
    this.btnStartDraw = page.locator('button:has-text("Auslosung"), button[id*="BtnDraw"]');
    this.btnStartStage = page.locator('button:has-text("Phase"), button[id*="BtnStart"]');
    this.roundsTable = page.locator('table');
  }

  async goto(stageId: string = '') {
    await this.page.goto('/');
    await this.page.waitForLoadState('domcontentloaded');
    await this.page.evaluate((id) => {
      if (typeof (window as any).appLoadPage === 'function') {
        (window as any).appLoadPage('StageAdmin', id);
      }
    }, stageId);
  }
}
