<script setup lang="ts">
import { useConversationStore } from '../stores/conversationStore'
import { useMonitoringStore } from '../stores/monitoringStore'
import { REPORT_BUTTONS } from '../constants/reportButtons'

const store = useConversationStore()
const monitoringStore = useMonitoringStore()

function handleNewChat() {
  monitoringStore.showChat()
  store.createConversation()
}

function handleSelect(id: string) {
  monitoringStore.showChat()
  store.selectConversation(id)
}

function handleReport(label: string) {
  monitoringStore.showChat()
  store.sendReportPrompt(label)
}

function handleNewInquiry() {
  monitoringStore.showChat()
  store.startInquiry()
}

function handleShowMonitoring() {
  monitoringStore.showMonitoring()
}
</script>

<template>
  <aside
    class="flex w-64 shrink-0 flex-col border-r border-zinc-200 bg-zinc-50 p-3 dark:border-zinc-800 dark:bg-zinc-900/60"
  >
    <div class="flex min-h-0 flex-1 flex-col gap-2">
      <button
        type="button"
        class="w-full rounded-lg bg-violet-600 px-3 py-2 text-sm font-medium text-white shadow-sm transition-colors hover:bg-violet-500 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-violet-600"
        @click="handleNewChat"
      >
        New Chat
      </button>
      <button
        type="button"
        class="w-full rounded-lg border border-zinc-200 bg-white px-3 py-2 text-sm font-medium text-zinc-700 shadow-sm transition-colors hover:bg-zinc-100 disabled:cursor-not-allowed disabled:opacity-50 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-200 dark:hover:bg-zinc-700"
        :disabled="store.isLoading"
        @click="handleNewInquiry"
      >
        新規問い合わせ
      </button>
      <button
        type="button"
        class="w-full rounded-lg border px-3 py-2 text-sm font-medium shadow-sm transition-colors"
        :class="
          monitoringStore.activeScreen === 'monitoring'
            ? 'border-violet-600 bg-violet-100 text-violet-900 dark:border-violet-500 dark:bg-violet-500/15 dark:text-violet-200'
            : 'border-zinc-200 bg-white text-zinc-700 hover:bg-zinc-100 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-200 dark:hover:bg-zinc-700'
        "
        @click="handleShowMonitoring"
      >
        システムモニタリング
      </button>
      <ul class="mt-2 min-h-0 flex-1 space-y-0.5 overflow-y-auto">
        <li
          v-for="conversation in store.conversations"
          :key="conversation.id"
          class="cursor-pointer truncate rounded-lg px-3 py-2 text-sm transition-colors"
          :class="
            conversation.id === store.activeConversationId
              ? 'bg-violet-100 font-medium text-violet-900 dark:bg-violet-500/15 dark:text-violet-200'
              : 'text-zinc-600 hover:bg-zinc-200/60 dark:text-zinc-400 dark:hover:bg-zinc-800'
          "
          @click="handleSelect(conversation.id)"
        >
          {{ conversation.title }}
        </li>
      </ul>
    </div>

    <div
      class="mt-3 shrink-0 border-t border-zinc-200 pt-3 dark:border-zinc-800"
    >
      <p
        class="mb-2 px-1 text-xs font-medium tracking-wide text-zinc-400 dark:text-zinc-500"
      >
        定型レポート
      </p>
      <div class="flex flex-col gap-0.5">
        <button
          v-for="label in REPORT_BUTTONS"
          :key="label"
          type="button"
          class="w-full rounded-lg px-3 py-2 text-left text-sm text-zinc-600 transition-colors hover:bg-zinc-200/60 disabled:cursor-not-allowed disabled:opacity-50 dark:text-zinc-400 dark:hover:bg-zinc-800"
          :disabled="store.isLoading"
          @click="handleReport(label)"
        >
          {{ label }}
        </button>
      </div>
    </div>
  </aside>
</template>
