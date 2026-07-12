import { defineStore } from 'pinia'
import { ChatResponseFormatError, postChat } from '../api/chatApi'
import type { Conversation, Message, UiComponentSpec } from '../types/chat'

const TITLE_MAX_LENGTH = 20

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

export const useConversationStore = defineStore('conversation', {
  state: () => ({
    conversations: [] as Conversation[],
    activeConversationId: null as string | null,
    isLoading: false,
  }),
  getters: {
    activeConversation(state): Conversation | null {
      return (
        state.conversations.find((c) => c.id === state.activeConversationId) ??
        null
      )
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
    async sendMessage(content: string) {
      const trimmed = content.trim()
      if (!trimmed) {
        return
      }

      let conversation = this.activeConversation
      if (!conversation) {
        this.createConversation()
        conversation = this.activeConversation
      }
      if (!conversation) {
        return
      }

      if (conversation.messages.length === 0) {
        conversation.title = trimmed.slice(0, TITLE_MAX_LENGTH)
      }

      conversation.messages.push(createMessage('user', trimmed))

      this.isLoading = true
      try {
        const { reply, components } = await postChat(trimmed)
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
  },
})
