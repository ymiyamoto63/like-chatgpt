import { defineStore } from 'pinia'
import { ChatResponseFormatError, postChat } from '../api/chatApi'
import { loadPersistedState } from './conversationPersistence'
import type { Conversation, Message, UiComponentSpec } from '../types/chat'

const TITLE_MAX_LENGTH = 20

export const INQUIRY_TRIGGER = '新規問い合わせ'
const CATEGORY_ANSWER_PATTERN = /^カテゴリ: (請求|技術|アカウント|その他)$/
const URGENCY_ANSWER_PATTERN = /^緊急度: (高|中|低)$/

function createMessage(
  role: Message['role'],
  content: string,
  components?: UiComponentSpec[],
): Message {
  return {
    id: crypto.randomUUID(),
    role,
    content,
    createdAt: Date.now(),
    components,
  }
}

function hasChoices(message: Message): boolean {
  return message.components?.some((c) => c.type === 'choices') ?? false
}

// 新規問い合わせフローの内容入力ステップ中なら、バックエンド（ステートレス）が
// 確認応答を組み立てられるよう全回答を累積した定型文を返す。フロー外なら null
function buildInquirySummary(
  conversation: Conversation,
  content: string,
): string | null {
  const userMessages = conversation.messages.filter((m) => m.role === 'user')
  const last = userMessages[userMessages.length - 1]
  if (!last || !URGENCY_ANSWER_PATTERN.test(last.content)) {
    return null
  }
  for (let i = userMessages.length - 2; i >= 0; i--) {
    const answer = userMessages[i].content
    if (CATEGORY_ANSWER_PATTERN.test(answer)) {
      return `${answer} / ${last.content} / 内容: ${content}`
    }
  }
  return null
}

export const useConversationStore = defineStore('conversation', {
  state: () => {
    const persisted = loadPersistedState()
    return {
      conversations: persisted?.conversations ?? ([] as Conversation[]),
      activeConversationId: persisted?.activeConversationId ?? null,
      isLoading: false,
    }
  },
  getters: {
    activeConversation(state): Conversation | null {
      return (
        state.conversations.find((c) => c.id === state.activeConversationId) ??
        null
      )
    },
    isInputLocked(): boolean {
      const messages = this.activeConversation?.messages ?? []
      const last = messages[messages.length - 1]
      return !!last && last.role === 'assistant' && hasChoices(last)
    },
  },
  actions: {
    createConversation() {
      const conversation: Conversation = {
        id: crypto.randomUUID(),
        title: 'New Chat',
        messages: [],
      }
      this.conversations.unshift(conversation)
      this.activeConversationId = conversation.id
      return conversation.id
    },
    selectConversation(id: string) {
      this.activeConversationId = id
    },
    deleteConversation(id: string) {
      const index = this.conversations.findIndex((c) => c.id === id)
      if (index === -1) {
        return
      }
      this.conversations.splice(index, 1)
      if (this.activeConversationId === id) {
        this.activeConversationId = this.conversations[0]?.id ?? null
      }
    },
    renameConversation(id: string, title: string) {
      const trimmed = title.trim()
      if (!trimmed) {
        return
      }
      const conversation = this.conversations.find((c) => c.id === id)
      if (conversation) {
        conversation.title = trimmed.slice(0, TITLE_MAX_LENGTH)
      }
    },
    async sendMessage(content: string) {
      const trimmed = content.trim()
      if (!trimmed) {
        return
      }

      const conversation = this.activeConversation
      const summary = conversation
        ? buildInquirySummary(conversation, trimmed)
        : null
      await this.dispatchUserMessage(summary ?? trimmed)
    },
    async sendChoice(option: string) {
      await this.dispatchUserMessage(option)
    },
    async dispatchUserMessage(content: string) {
      let conversation = this.activeConversation
      if (!conversation) {
        this.createConversation()
        conversation = this.activeConversation
      }
      if (!conversation) {
        return
      }

      if (conversation.messages.length === 0) {
        conversation.title = content.slice(0, TITLE_MAX_LENGTH)
      }

      conversation.messages.push(createMessage('user', content))

      this.isLoading = true
      try {
        const { reply, components } = await postChat(content)
        conversation.messages.push(createMessage('assistant', reply, components))
      } catch (err) {
        if (err instanceof ChatResponseFormatError) {
          conversation.messages.push(
            createMessage('assistant', '応答を表示できませんでした'),
          )
        } else {
          conversation.messages.push(
            createMessage('assistant', 'エラー: 応答を取得できませんでした'),
          )
        }
      } finally {
        this.isLoading = false
      }
    },
    async sendReportPrompt(label: string) {
      // createConversation() は同期処理のみで activeConversationId を確定させるため、
      // 直後に呼ぶ sendMessage() は必ずこの新規会話に対して送信される（AC-4）。
      this.createConversation()
      await this.sendMessage(label)
    },
    async startInquiry() {
      this.createConversation()
      await this.sendMessage(INQUIRY_TRIGGER)
    },
    // モニタリング画面の異常検知アラートをきっかけに、バックエンド通信なしで
    // アシスタント発言（アラート文＋選択肢）を新規会話へ直接追加する
    createAlertConversation(replyText: string, choiceOptions: string[]) {
      this.createConversation()
      const conversation = this.activeConversation
      if (!conversation) {
        return
      }
      conversation.title = replyText.slice(0, TITLE_MAX_LENGTH)
      conversation.messages.push(
        createMessage('assistant', replyText, [
          { type: 'choices', options: choiceOptions },
        ]),
      )
    },
  },
})
