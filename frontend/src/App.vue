<script setup lang="ts">
import { onMounted } from 'vue'
import { useConversationStore } from './stores/conversationStore'
import ChatWindow from './components/ChatWindow.vue'
import Sidebar from './components/Sidebar.vue'

const store = useConversationStore()

onMounted(() => {
  if (store.conversations.length === 0) {
    store.createConversation()
  }
})
</script>

<template>
  <div class="app">
    <header class="app-header">
      <h2>Like ChatGPT</h2>
    </header>
    <div class="app-body">
      <Sidebar />
      <main class="app-main">
        <ChatWindow />
      </main>
    </div>
  </div>
</template>

<style scoped>
.app {
  display: flex;
  flex-direction: column;
  height: 100vh;
}

.app-header {
  padding: 12px 16px;
  border-bottom: 1px solid var(--border);
}

.app-header h2 {
  margin: 0;
}

.app-body {
  flex: 1;
  display: flex;
  min-height: 0;
}

.app-main {
  flex: 1;
  min-height: 0;
}
</style>
