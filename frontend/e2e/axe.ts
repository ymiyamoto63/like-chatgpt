import { expect, test, type Page } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import { writeAxeResult } from './axe-report'

/** ライト・ダークの両方を検査する。配色はテーマごとに別の値で、片方の合格は他方を保証しない。 */
export const COLOR_SCHEMES = ['light', 'dark'] as const

/**
 * M-10 の判定対象となる違反（impact が critical / serious）が 0 件であることを検査する。
 *
 * 結果は判定の前に書き出す。違反があって検査が落ちたときこそ、
 * その違反を quality-gate に届ける必要がある。
 */
export async function expectNoBlockingViolations(page: Page): Promise<void> {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'])
    .analyze()
  writeAxeResult(test.info().testId, results)

  const blocking = results.violations.filter(
    (v) => v.impact === 'critical' || v.impact === 'serious',
  )
  expect(blocking, JSON.stringify(blocking, null, 2)).toEqual([])
}
