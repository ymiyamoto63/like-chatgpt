<script setup lang="ts">
import { computed } from 'vue'
import { useConversationStore } from '../stores/conversationStore'
import MessageBubble from './MessageBubble.vue'
import MessageInput from './MessageInput.vue'

const store = useConversationStore()

const messages = computed(() => store.activeConversation?.messages ?? [])

const latestMessageId = computed(
  () => messages.value[messages.value.length - 1]?.id ?? null,
)

function handleSend(content: string) {
  store.sendMessage(content)
}

function handleChoiceSelect(option: string) {
  store.sendChoice(option)
}
</script>

<template>
  <div class="chat-window">
    <div class="messages">
      <p v-if="messages.length === 0" class="empty">
        メッセージを送信して会話を始めましょう
      </p>
      <MessageBubble
        v-for="message in messages"
        :key="message.id"
        :message="message"
        :choices-enabled="message.id === latestMessageId && !store.isLoading"
        @select="handleChoiceSelect"
      />
      <p v-if="store.isLoading" class="loading">応答を生成中...</p>
    </div>
    <MessageInput
      :disabled="store.isLoading || store.isInputLocked"
      @send="handleSend"
    />
  </div>
</template>

<style scoped>
.chat-window {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.messages {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
}

.empty {
  text-align: center;
  color: var(--text);
}

.loading {
  text-align: left;
  color: var(--text);
  font-style: italic;
}
</style>
