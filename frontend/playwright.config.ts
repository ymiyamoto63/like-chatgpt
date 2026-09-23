import { defineConfig, devices } from '@playwright/test'

// quality-gate M-10（アクセシビリティ）の計測元。axe-core の critical / serious を 0 件に保つ。
export default defineConfig({
  testDir: './e2e',
  // axe-core の結果（M-10 の成果物）は reports/axe-results.json に書き出す（e2e/axe-report.ts）。
  // テストレポートを同じ名前で出すと、axe の結果と取り違えて送ってしまう
  reporter: [['list'], ['json', { outputFile: '../reports/playwright-results.json' }]],
  globalSetup: './e2e/global-setup.ts',
  globalTeardown: './e2e/global-teardown.ts',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
    trace: 'on-first-retry',
  },
  // 検査先を指定しなければ dev server を起動する。起動済みならそれを使う
  webServer: process.env.E2E_BASE_URL
    ? undefined
    : {
        command: 'npm run dev -- --port 5173 --strictPort',
        url: 'http://localhost:5173',
        reuseExistingServer: true,
      },
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        // 導入済みの Chromium を使う場合に指定する（Playwright が同梱版を取得できない環境向け）
        launchOptions: process.env.E2E_CHROMIUM
          ? { executablePath: process.env.E2E_CHROMIUM }
          : {},
      },
    },
  ],
})
