import type { Conversation } from '../types/chat'

const STORAGE_KEY = 'like_chatgpt.conversations.v1'

export interface PersistedConversationState {
  conversations: Conversation[]
  activeConversationId: string | null
}

function isConversation(value: unknown): value is Conversation {
  if (typeof value !== 'object' || value === null) {
    return false
  }
  const c = value as Record<string, unknown>
  return (
    typeof c.id === 'string' &&
    typeof c.title === 'string' &&
    Array.isArray(c.messages)
  )
}

// 破損データや旧形式のデータを読んだ場合は null を返し、呼び出し側は初期状態で起動する
export function loadPersistedState(): PersistedConversationState | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) {
      return null
    }
    const parsed: unknown = JSON.parse(raw)
    if (typeof parsed !== 'object' || parsed === null) {
      return null
    }
    const state = parsed as Record<string, unknown>
    if (!Array.isArray(state.conversations)) {
      return null
    }
    const conversations = state.conversations.filter(isConversation)
    const activeConversationId =
      typeof state.activeConversationId === 'string' &&
      conversations.some((c) => c.id === state.activeConversationId)
        ? state.activeConversationId
        : null
    return { conversations, activeConversationId }
  } catch {
    return null
  }
}

export function savePersistedState(state: PersistedConversationState): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state))
  } catch {
    // 容量超過等で保存に失敗してもチャット自体は継続できるため握りつぶす
  }
}
