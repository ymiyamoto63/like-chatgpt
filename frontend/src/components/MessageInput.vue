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
  <div class="shrink-0 border-t border-zinc-200 dark:border-zinc-800">
    <div class="mx-auto flex max-w-3xl items-end gap-2 px-5 py-4">
      <textarea
        v-model="text"
        class="min-h-0 flex-1 resize-none rounded-xl border border-zinc-300 bg-white px-4 py-2.5 text-[15px] text-zinc-900 placeholder-zinc-400 transition-shadow outline-none focus:border-violet-500 focus:ring-2 focus:ring-violet-500/30 disabled:cursor-not-allowed disabled:bg-zinc-100 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-100 dark:placeholder-zinc-500 dark:disabled:bg-zinc-800"
        rows="1"
        placeholder="メッセージを入力..."
        :disabled="disabled"
        @keydown="onKeydown"
      />
      <button
        type="button"
        class="rounded-xl bg-violet-600 px-5 py-2.5 text-sm font-medium text-white shadow-sm transition-colors hover:bg-violet-500 disabled:cursor-not-allowed disabled:bg-zinc-200 disabled:text-zinc-400 dark:disabled:bg-zinc-800 dark:disabled:text-zinc-600"
        :disabled="!canSend || disabled"
        @click="submit"
      >
        送信
      </button>
    </div>
  </div>
</template>
