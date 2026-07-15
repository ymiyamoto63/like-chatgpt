<script setup lang="ts">
import type { ChoicesComponent } from '../types/chat'

defineProps<{
  spec: ChoicesComponent
  disabled?: boolean
}>()

const emit = defineEmits<{
  select: [option: string]
}>()
</script>

<template>
  <div class="choices">
    <button
      v-for="option in spec.options"
      :key="option"
      type="button"
      class="choice-button"
      :disabled="disabled"
      @click="emit('select', option)"
    >
      {{ option }}
    </button>
  </div>
</template>

<style scoped>
.choices {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.choice-button {
  padding: 6px 12px;
  border: 1px solid var(--border);
  border-radius: 16px;
  background: var(--bg);
  color: var(--text-h);
  font: inherit;
  cursor: pointer;
}

.choice-button:hover:not(:disabled) {
  background: var(--accent-bg);
}

.choice-button:disabled {
  color: var(--text);
  cursor: not-allowed;
  opacity: 0.6;
}
</style>
