<script setup lang="ts">
import { useConversationStore } from '../stores/conversationStore'
import { REPORT_BUTTONS } from '../constants/reportButtons'

const store = useConversationStore()

function handleNewChat() {
  store.createConversation()
}

function handleSelect(id: string) {
  store.selectConversation(id)
}

function handleReport(label: string) {
  store.sendReportPrompt(label)
}
</script>

<template>
  <aside class="sidebar">
    <div class="sidebar-top">
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
    </div>

    <div class="sidebar-reports">
      <p class="sidebar-reports-heading">定型レポート</p>
      <button
        v-for="label in REPORT_BUTTONS"
        :key="label"
        type="button"
        class="report-button"
        :disabled="store.isLoading"
        @click="handleReport(label)"
      >
        {{ label }}
      </button>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  display: flex;
  flex-direction: column;
  width: 240px;
  flex-shrink: 0;
  padding: 12px;
  border-right: 1px solid var(--border);
  box-sizing: border-box;
  min-height: 0;
}

.sidebar-top {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
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
  flex: 1;
  min-height: 0;
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

.sidebar-reports {
  flex-shrink: 0;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.sidebar-reports-heading {
  margin: 0 0 2px;
  font-size: 13px;
  color: var(--text);
}

.report-button {
  padding: 8px 12px;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--bg);
  color: var(--text-h);
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.report-button:hover:not(:disabled) {
  background: var(--code-bg);
}

.report-button:disabled {
  background: var(--code-bg);
  color: var(--text);
  cursor: not-allowed;
}
</style>
