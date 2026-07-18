<script setup lang="ts">
import type { FaqListComponent } from '../types/chat'
import { FAQ_ANSWER_PREFIX } from '../constants/faq'

defineProps<{
  spec: FaqListComponent
  disabled?: boolean
}>()

const emit = defineEmits<{
  select: [option: string]
}>()

// ChoicesComponent とは異なり、表示ラベル（タイトル）そのものではなく
// FAQ_ANSWER_PREFIX を付与した定型文を送信文として送出する
function handleClick(title: string) {
  emit('select', FAQ_ANSWER_PREFIX + title)
}
</script>

<template>
  <div class="flex flex-col items-start gap-1">
    <button
      v-for="title in spec.titles"
      :key="title"
      type="button"
      class="text-left text-sm text-violet-700 underline decoration-violet-300 underline-offset-2 transition-colors hover:text-violet-900 hover:decoration-violet-500 disabled:cursor-not-allowed disabled:text-zinc-400 disabled:decoration-transparent disabled:hover:text-zinc-400 dark:text-violet-300 dark:decoration-violet-700 dark:hover:text-violet-200 dark:hover:decoration-violet-500 dark:disabled:text-zinc-600 dark:disabled:hover:text-zinc-600"
      :disabled="disabled"
      @click="handleClick(title)"
    >
      {{ title }}
    </button>
  </div>
</template>
