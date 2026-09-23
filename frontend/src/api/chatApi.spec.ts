import { afterEach, describe, expect, it, vi } from 'vitest'
import { ChatResponseFormatError, postChat } from './chatApi'

function mockFetch(body: unknown, init: { ok?: boolean; status?: number } = {}) {
  const fetchMock = vi.fn().mockResolvedValue({
    ok: init.ok ?? true,
    status: init.status ?? 200,
    json: () => Promise.resolve(body),
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

describe('postChat', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('メッセージを POST し、検証済みの応答を返す', async () => {
    const fetchMock = mockFetch({
      reply: 'まとめました。',
      components: [
        { type: 'table', columns: ['担当者', '件数'], rows: [['佐藤', '12']] },
        { type: 'bar_chart', title: '件数', labels: ['佐藤'], values: [12] },
        { type: 'choices', options: ['はい', 'いいえ'] },
        { type: 'trend_chart', title: '推移', labels: ['1月'], values: [3], average: 3 },
        { type: 'faq_list', titles: ['パスワードを忘れた'] },
        { type: 'stat_cards', cards: [{ label: '件数', value: '12', delta: '+2' }] },
        { type: 'donut_chart', title: '内訳', labels: ['A'], values: [1] },
      ],
    })

    const result = await postChat('こんにちは')

    expect(fetchMock).toHaveBeenCalledWith('/api/chat', expect.objectContaining({ method: 'POST' }))
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ message: 'こんにちは' })
    expect(result.reply).toBe('まとめました。')
    expect(result.components).toHaveLength(7)
  })

  it('HTTP エラーのときは例外を投げる', async () => {
    mockFetch({}, { ok: false, status: 500 })

    await expect(postChat('x')).rejects.toThrow('status 500')
  })

  it.each([
    ['オブジェクトでない', null],
    ['reply が文字列でない', { reply: 1, components: [] }],
    ['components が配列でない', { reply: 'a', components: {} }],
    ['未知のコンポーネント', { reply: 'a', components: [{ type: 'unknown' }] }],
    [
      '行と列の数が合わない表',
      { reply: 'a', components: [{ type: 'table', columns: ['a', 'b'], rows: [['1']] }] },
    ],
    [
      'ラベルと値の数が合わない棒グラフ',
      { reply: 'a', components: [{ type: 'bar_chart', title: 't', labels: ['a'], values: [] }] },
    ],
    [
      '平均が数値でない推移グラフ',
      {
        reply: 'a',
        components: [{ type: 'trend_chart', title: 't', labels: ['a'], values: [1], average: 'x' }],
      },
    ],
    ['空の選択肢', { reply: 'a', components: [{ type: 'choices', options: [] }] }],
    [
      'value が文字列でない統計カード',
      { reply: 'a', components: [{ type: 'stat_cards', cards: [{ label: 'a', value: 1 }] }] },
    ],
  ])('形式が不正な応答（%s）は ChatResponseFormatError にする', async (_name, body) => {
    mockFetch(body)

    await expect(postChat('x')).rejects.toBeInstanceOf(ChatResponseFormatError)
  })
})
