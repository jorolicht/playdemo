/**
 * Helper to interact with the Docker MailCatcher REST API (http://localhost:1080).
 */
export class MailcatcherHelper {
  static baseUrl = 'http://localhost:1080';

  /**
   * Clears all caught messages from Mailcatcher.
   */
  static async clearMessages(): Promise<void> {
    try {
      await fetch(`${this.baseUrl}/messages`, { method: 'DELETE' });
    } catch (e) {
      console.warn('MailcatcherHelper.clearMessages failed:', e);
    }
  }

  /**
   * Retrieves all messages stored in Mailcatcher.
   */
  static async getMessages(): Promise<Array<{ id: number; recipients: string[]; subject: string }>> {
    const res = await fetch(`${this.baseUrl}/messages`);
    if (!res.ok) {
      throw new Error(`Mailcatcher getMessages failed: ${res.statusText}`);
    }
    return res.json();
  }

  /**
   * Polls Mailcatcher until an email for the specified recipient arrives.
   */
  static async waitForMessage(recipientEmail: string, timeoutMs = 15000): Promise<{ id: number; plainBody: string; subject: string }> {
    const start = Date.now();
    const cleanRecipient = recipientEmail.toLowerCase();
    while (Date.now() - start < timeoutMs) {
      const msgs = await this.getMessages();
      const match = msgs.find(m => m.recipients.some(r => r.toLowerCase().includes(cleanRecipient)));
      if (match) {
        const bodyRes = await fetch(`${this.baseUrl}/messages/${match.id}.plain`);
        const plainBody = await bodyRes.text();
        return { id: match.id, plainBody, subject: match.subject };
      }
      await new Promise(r => setTimeout(r, 400));
    }
    throw new Error(`No email found in Mailcatcher for ${recipientEmail} after ${timeoutMs}ms`);
  }

  /**
   * Extracts the verification URL from the plain-text body of the welcome email.
   */
  static extractVerificationUrl(plainBody: string): string | null {
    const match = plainBody.match(/https?:\/\/[^\s]+\/verification\/[^\s]*/i);
    return match ? match[0] : null;
  }
}
