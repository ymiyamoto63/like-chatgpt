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
  <div class="flex flex-wrap gap-1.5">
    <button
      v-for="option in spec.options"
      :key="option"
      type="button"
      class="rounded-full border border-zinc-300 bg-white px-3.5 py-1.5 text-sm text-zinc-800 transition-colors hover:border-violet-400 hover:bg-violet-50 disabled:cursor-not-allowed disabled:opacity-50 disabled:hover:border-zinc-300 disabled:hover:bg-white dark:border-zinc-600 dark:bg-zinc-900 dark:text-zinc-200 dark:hover:border-violet-500 dark:hover:bg-violet-500/10 dark:disabled:hover:border-zinc-600 dark:disabled:hover:bg-zinc-900"
      :disabled="disabled"
      @click="emit('select', option)"
    >
      {{ option }}
    </button>
  </div>
</template>
