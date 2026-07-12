<script setup lang="ts">
import { useConversationStore } from '../stores/conversationStore'

const store = useConversationStore()

function handleNewChat() {
  store.createConversation()
}

function handleSelect(id: string) {
  store.selectConversation(id)
}
</script>

<template>
  <aside class="sidebar">
    <button type="button" class="new-chat-button" @click="handleNewChat">
      New Chat
    </button>
    <ul class="conversation-list">
      <li
        v-for="conversation in store.conversations"
        :key="conversation.id"
        class="conversation-item"
        :class="{ active: conversation.id === store.activeConversationId }"
        @click="handleSelect(conversation.id)"
      >
        {{ conversation.title }}
      </li>
    </ul>
  </aside>
</template>

<style scoped>
.sidebar {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 240px;
  flex-shrink: 0;
  padding: 12px;
  border-right: 1px solid var(--border);
  box-sizing: border-box;
}

.new-chat-button {
  padding: 8px 12px;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--bg);
  color: var(--text-h);
  font: inherit;
  cursor: pointer;
}

.conversation-list {
  list-style: none;
  margin: 0;
  padding: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.conversation-item {
  padding: 8px 10px;
  border-radius: 6px;
  color: var(--text);
  cursor: pointer;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.conversation-item:hover {
  background: var(--code-bg);
}

.conversation-item.active {
  background: var(--accent-bg);
  color: var(--text-h);
}
</style>
