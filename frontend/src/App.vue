<script setup lang="ts">
import { onMounted } from 'vue'
import { useConversationStore } from './stores/conversationStore'
import { useMonitoringStore } from './stores/monitoringStore'
import ChatWindow from './components/chat/ChatWindow.vue'
import MonitoringView from './components/monitoring/MonitoringView.vue'
import Sidebar from './components/layout/Sidebar.vue'

const store = useConversationStore()
const monitoringStore = useMonitoringStore()

onMounted(() => {
  if (store.conversations.length === 0) {
    store.createConversation()
  }
})
</script>

<template>
  <div
    class="flex h-full flex-col bg-white text-zinc-700 antialiased dark:bg-zinc-950 dark:text-zinc-300"
  >
    <header
      class="flex h-14 shrink-0 items-center border-b border-zinc-200 px-5 dark:border-zinc-800"
    >
      <h1
        class="text-sm font-semibold tracking-tight text-zinc-900 dark:text-zinc-100"
      >
        Like ChatGPT
      </h1>
    </header>
    <div class="flex min-h-0 flex-1">
      <Sidebar />
      <main class="min-h-0 flex-1">
        <ChatWindow v-if="monitoringStore.activeScreen === 'chat'" />
        <MonitoringView v-else />
      </main>
    </div>
  </div>
</template>
