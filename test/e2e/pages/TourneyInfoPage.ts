import { Page, Locator, expect } from '@playwright/test';

export class TourneyInfoPage {
  readonly page: Page;
  readonly cardHeaderTitle: Locator;
  readonly btnAddComp: Locator;
  readonly btnEditTourney: Locator;
  readonly btnEditStammdaten: Locator;
  readonly btnPrintStammblatt: Locator;
  readonly btnToggleStatus: Locator;
  readonly btnDeleteTourney: Locator;
  readonly btnSaveLeave: Locator;
  readonly compTable: Locator;

  // DlgCompetition Locators
  readonly dlgCompModal: Locator;
  readonly inputCompName: Locator;
  readonly selectCompTyp: Locator;
  readonly selectCompCategory: Locator;
  readonly inputCompStartDate: Locator;
  readonly btnCompCreate: Locator;
  readonly btnCompCancel: Locator;
  readonly dlgCompFeedback: Locator;
  readonly compRows: Locator;

  // DlgExportTourney Locators
  readonly dlgExportModal: Locator;
  readonly dlgExportTitle: Locator;
  readonly dlgExportPrompt: Locator;
  readonly inputExportFilename: Locator;
  readonly btnExportApply: Locator;
  readonly btnExportLeaveWithout: Locator;
  readonly btnExportCancel: Locator;
  readonly dlgExportFeedback: Locator;
  readonly dlgExportOverwriteWarning: Locator;

  constructor(page: Page) {
    this.page = page;
    this.cardHeaderTitle = page.locator('.card-header h3');
    this.btnAddComp = page.locator('button[id*="BtnAddComp"], button:has-text("Wettbewerb hinzufügen")');
    this.btnEditTourney = page.locator('button[id*="BtnEditTourney"], button:has-text("Bearbeiten"), button:has-text("Edit")');
    this.btnEditStammdaten = page.locator('button[id*="BtnEditStammdaten"], button:has-text("Stammdaten"), button:has-text("bearbeiten")');
    this.btnPrintStammblatt = page.locator('button[id*="BtnPrintStammblatt"], button:has-text("PDF")');
    this.btnToggleStatus = page.locator('button[id*="BtnToggleStatus"]');
    this.btnDeleteTourney = page.locator('button[id*="BtnDeleteTourney"], button:has-text("Löschen"), button:has-text("Delete")');
    this.btnSaveLeave = page.locator('button[id*="BtnSaveLeave"], button:has-text("Speichern/Verlassen"), button:has-text("Verlassen")');
    this.compTable = page.locator('table');

    // DlgCompetition
    this.dlgCompModal = page.locator('div[id*="DlgCompetition"].modal');
    this.inputCompName = page.locator('input[id*="DlgCompetition_CompNameId"], input[placeholder*="Herren A"]');
    this.selectCompTyp = page.locator('select[id*="DlgCompetition_CompTypId"]');
    this.selectCompCategory = page.locator('select[id*="DlgCompetition_CompCategoryId"]');
    this.inputCompStartDate = page.locator('input[id*="DlgCompetition_StartDateId"]');
    this.btnCompCreate = page.locator('button[id*="DlgCompetition_ApplyId"], button:has-text("Erstellen")');
    this.btnCompCancel = page.locator('button[id*="DlgCompetition_CancelId"], button:has-text("Abbrechen")');
    this.dlgCompFeedback = page.locator('#dlgCompFeedback');
    this.compRows = page.locator('table tbody tr');

    // DlgExportTourney
    this.dlgExportModal = page.locator('div[id*="DlgExportTourney_ModalId"].modal');
    this.dlgExportTitle = page.locator('h5[id*="DlgExportTourney_TitleId"]');
    this.dlgExportPrompt = page.locator('div[id*="DlgExportTourney_ModalId"] .modal-body p');
    this.inputExportFilename = page.locator('input[id*="DlgExportTourney_InputFilenameId"]');
    this.btnExportApply = page.locator('button[id*="DlgExportTourney_ApplyId"]');
    this.btnExportLeaveWithout = page.locator('button[id*="DlgExportTourney_BtnLeaveWithoutExportId"]');
    this.btnExportCancel = page.locator('button[id*="DlgExportTourney_CancelId"]');
    this.dlgExportFeedback = page.locator('#dlgExportTourneyFeedback');
    this.dlgExportOverwriteWarning = page.locator('#dlgExportTourneyOverwriteWarning');
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

  async openAddCompetition() {
    await this.btnAddComp.click();
    await expect(this.inputCompName).toBeVisible();
  }

  async fillCompetition(name: string, startDate: string, typ?: string) {
    await this.inputCompName.fill(name);
    await this.inputCompStartDate.fill(startDate);
    if (typ) {
      await this.selectCompTyp.selectOption(typ);
    }
  }

  async saveCompetition() {
    await this.btnCompCreate.click();
  }
}
