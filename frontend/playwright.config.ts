import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './tests/e2e',
  use: { baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173' },
  webServer: process.env.E2E_BASE_URL ? undefined : { command: 'npm run dev', port: 5173, reuseExistingServer: true },
});
