import { Page, Locator, expect } from '@playwright/test';

/**
 * Page Object for TourneyNew (Tournament creation & editing).
 */
export class TourneyNewPage {
  readonly page: Page;
  readonly inputName: Locator;
  readonly inputIdent: Locator;
  readonly inputOrganizer: Locator;
  readonly selectTyp: Locator;
  readonly inputStart: Locator;
  readonly inputEnd: Locator;
  readonly inputContactFname: Locator;
  readonly inputContactLname: Locator;
  readonly inputContactPhone: Locator;
  readonly inputContactEmail: Locator;
  readonly inputAddrDesc: Locator;
  readonly inputAddrStreet: Locator;
  readonly inputAddrZip: Locator;
  readonly inputAddrCity: Locator;
  readonly btnSave: Locator;
  readonly btnCreate: Locator;
  readonly validationFeedback: Locator;

  constructor(page: Page) {
    this.page = page;
    this.inputName = page.locator('#tn-name');
    this.inputIdent = page.locator('#tn-ident');
    this.inputOrganizer = page.locator('#tn-organizer');
    this.selectTyp = page.locator('#tn-typ');
    this.inputStart = page.locator('#tn-start');
    this.inputEnd = page.locator('#tn-end');
    this.inputContactFname = page.locator('#tn-contact-fname');
    this.inputContactLname = page.locator('#tn-contact-lname');
    this.inputContactPhone = page.locator('#tn-contact-phone');
    this.inputContactEmail = page.locator('#tn-contact-email');
    this.inputAddrDesc = page.locator('#tn-addr-desc');
    this.inputAddrStreet = page.locator('#tn-addr-street');
    this.inputAddrZip = page.locator('#tn-addr-zip');
    this.inputAddrCity = page.locator('#tn-addr-city');
    this.btnSave = page.locator('button[type="submit"], button:has-text("ERSTELLEN"), button:has-text("CREATE"), button:has-text("Turnier speichern"), button:has-text("Speichern")');
    this.btnCreate = page.locator('button[type="submit"]:has-text("ERSTELLEN"), button[type="submit"]:has-text("CREATE")');
    this.validationFeedback = page.locator('#validationFeedback');
  }

  async goto() {
    await this.page.goto('/');
    await this.page.waitForLoadState('domcontentloaded');
    await this.page.evaluate(() => {
      if (typeof (window as any).appLoadPage === 'function') {
        (window as any).appLoadPage('TourneyNew', '');
      }
    });
    await expect(this.inputName).toBeVisible();
  }

  async fillForm(data: {
    name: string;
    ident?: string;
    organizer?: string;
    startDate: string;
    endDate: string;
    contactFname?: string;
    contactLname?: string;
    contactEmail?: string;
    venueName?: string;
    street?: string;
    zip?: string;
    city?: string;
  }) {
    await this.inputName.fill(data.name);
    if (data.ident) await this.inputIdent.fill(data.ident);
    if (data.organizer) await this.inputOrganizer.fill(data.organizer);
    await this.inputStart.fill(data.startDate);
    await this.inputEnd.fill(data.endDate);
    if (data.contactFname) await this.inputContactFname.fill(data.contactFname);
    if (data.contactLname) await this.inputContactLname.fill(data.contactLname);
    if (data.contactEmail) await this.inputContactEmail.fill(data.contactEmail);
    if (data.venueName) await this.inputAddrDesc.fill(data.venueName);
    if (data.street) await this.inputAddrStreet.fill(data.street);
    if (data.zip) await this.inputAddrZip.fill(data.zip);
    if (data.city) await this.inputAddrCity.fill(data.city);
  }

  /**
   * Saves the form. The target (local in browser, cloud or current page) is already
   * determined when the creation is started (login state and plugin variant).
   */
  async save() {
    await this.page.locator('#DlgMsgbox_ModalId').waitFor({ state: 'hidden', timeout: 5000 }).catch(() => {});
    await this.page.locator('.modal-backdrop').waitFor({ state: 'hidden', timeout: 5000 }).catch(() => {});
    await this.btnSave.click();
  }
}
