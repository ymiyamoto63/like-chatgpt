<script setup lang="ts">
import { computed } from 'vue'
import { useConversationStore } from '../../stores/conversationStore'
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
  <div class="flex h-full min-h-0 flex-col">
    <div class="min-h-0 flex-1 overflow-y-auto">
      <div class="mx-auto flex max-w-3xl flex-col gap-4 px-5 py-6">
        <div
          v-if="messages.length === 0"
          class="flex flex-col items-center gap-2 pt-24 text-center"
        >
          <p class="text-lg font-medium text-zinc-900 dark:text-zinc-100">
            こんにちは
          </p>
          <p class="text-sm text-zinc-500 dark:text-zinc-400">
            メッセージを送信して会話を始めましょう
          </p>
        </div>
        <MessageBubble
          v-for="message in messages"
          :key="message.id"
          :message="message"
          :choices-enabled="message.id === latestMessageId && !store.isLoading"
          @select="handleChoiceSelect"
        />
        <div
          v-if="store.isLoading"
          class="flex items-center gap-1.5 px-1 text-sm text-zinc-400 dark:text-zinc-500"
        >
          <span
            class="size-1.5 animate-bounce rounded-full bg-current [animation-delay:-0.3s]"
          ></span>
          <span
            class="size-1.5 animate-bounce rounded-full bg-current [animation-delay:-0.15s]"
          ></span>
          <span class="size-1.5 animate-bounce rounded-full bg-current"></span>
          <span class="ml-1">応答を生成中</span>
        </div>
      </div>
    </div>
    <MessageInput
      :disabled="store.isLoading || store.isInputLocked"
      @send="handleSend"
    />
  </div>
</template>
