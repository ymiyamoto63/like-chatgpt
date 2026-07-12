<script setup lang="ts">
import { computed, ref } from 'vue'

const emit = defineEmits<{
  send: [content: string]
}>()

defineProps<{
  disabled?: boolean
}>()

const text = ref('')

const canSend = computed(() => text.value.trim().length > 0)

function submit() {
  if (!canSend.value) {
    return
  }
  emit('send', text.value.trim())
  text.value = ''
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    submit()
  }
}
</script>

<template>
  <div class="message-input">
    <textarea
      v-model="text"
      class="input"
      rows="1"
      placeholder="メッセージを入力..."
      :disabled="disabled"
      @keydown="onKeydown"
    />
    <button
      type="button"
      class="send-button"
      :disabled="!canSend || disabled"
      @click="submit"
    >
      送信
    </button>
  </div>
</template>

<style scoped>
.message-input {
  display: flex;
  gap: 8px;
  padding: 12px;
  border-top: 1px solid var(--border);
}

.input {
  flex: 1;
  resize: none;
  padding: 8px 12px;
  border: 1px solid var(--border);
  border-radius: 8px;
  font: inherit;
  color: var(--text-h);
  background: var(--bg);
}

.send-button {
  padding: 8px 16px;
  border: none;
  border-radius: 8px;
  background: var(--accent);
  color: #fff;
  font: inherit;
  cursor: pointer;
}

.send-button:disabled {
  background: var(--code-bg);
  color: var(--text);
  cursor: not-allowed;
}
</style>
