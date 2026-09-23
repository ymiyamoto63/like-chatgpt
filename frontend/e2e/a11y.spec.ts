import { test } from '@playwright/test'
import { COLOR_SCHEMES, expectNoBlockingViolations } from './axe'

/**
 * quality-gate M-10（アクセシビリティ）の計測元。
 *
 * バックエンドを起動せずに画面を描くため、API はここで応答を差し替える。
 */
test.beforeEach(async ({ page }) => {
  await page.route('**/api/suggest', (route) => route.fulfill({ json: { completion: null } }))
})

for (const scheme of COLOR_SCHEMES) {
  test(`チャット画面（${scheme}）に重大なアクセシビリティ違反がない`, async ({ page }) => {
    await page.emulateMedia({ colorScheme: scheme })
    await page.goto('/')
    await page.getByRole('heading', { name: 'Like ChatGPT' }).waitFor()

    await expectNoBlockingViolations(page)
  })
}
