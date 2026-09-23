import { beforeEach, describe, expect, it } from 'vitest'
import { loadPersistedState, savePersistedState } from './conversationPersistence'

const STORAGE_KEY = 'like_chatgpt.conversations.v1'

describe('conversationPersistence', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('保存した状態を読み戻せる', () => {
    const state = {
      conversations: [{ id: 'c1', title: '新しいチャット', messages: [] }],
      activeConversationId: 'c1',
    }
    savePersistedState(state as never)

    expect(loadPersistedState()).toEqual(state)
  })

  it('保存が無ければ null を返す', () => {
    expect(loadPersistedState()).toBeNull()
  })

  it('JSON として壊れていれば null を返す', () => {
    localStorage.setItem(STORAGE_KEY, '{broken')

    expect(loadPersistedState()).toBeNull()
  })

  it('conversations が配列でなければ null を返す', () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ conversations: 'x' }))

    expect(loadPersistedState()).toBeNull()
  })

  it('形式の合わない会話は捨て、存在しない activeConversationId は null にする', () => {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({
        conversations: [{ id: 'c1', title: 't', messages: [] }, { id: 2 }, null],
        activeConversationId: 'missing',
      }),
    )

    expect(loadPersistedState()).toEqual({
      conversations: [{ id: 'c1', title: 't', messages: [] }],
      activeConversationId: null,
    })
  })
})
