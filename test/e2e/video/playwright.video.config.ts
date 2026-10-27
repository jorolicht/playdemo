import { defineConfig, devices } from '@playwright/test';
import dotenv from 'dotenv';
import path from 'path';

dotenv.config({ path: path.resolve(__dirname, '..', '.env') });

/**
 * Eigene Konfiguration für die Aufnahme des Werbevideos (nicht Teil der normalen E2E-Tests).
 * Aufruf über make_video.sh.
 */
export default defineConfig({
  testDir: '.',
  testMatch: 'promo.spec.ts',
  timeout: 5 * 60 * 1000,
  workers: 1,
  reporter: [['list']],
  use: {
    ...devices['Desktop Chrome'],
    baseURL: process.env.WP_URL || 'https://localhost',
    ignoreHTTPSErrors: true,
    locale: 'de-DE',
  },
});
