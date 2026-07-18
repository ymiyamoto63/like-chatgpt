<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { postSuggest } from '../api/suggestApi'

const SUGGEST_DEBOUNCE_MS = 300

const emit = defineEmits<{
  send: [content: string]
}>()

const props = defineProps<{
  disabled?: boolean
}>()

const text = ref('')
const textareaRef = ref<HTMLTextAreaElement | null>(null)

// 選択肢や応答待ちで無効化された入力欄が再び有効になったタイミング
// （例: 新規問い合わせで「問い合わせ内容を入力してください。」表示後）でフォーカスを戻す
watch(
  () => props.disabled,
  async (now, prev) => {
    if (prev && !now) {
      await nextTick()
      textareaRef.value?.focus()
    }
  },
)

const canSend = computed(() => text.value.trim().length > 0)

// --- 入力補完（ゴーストテキスト） ---
const suggestion = ref<string | null>(null)
const isComposing = ref(false)
let debounceTimer: ReturnType<typeof setTimeout> | undefined
// デバウンス後に応答が前後しても、最新の入力に対応する候補だけを反映するための世代番号
let requestSeq = 0

function clearSuggestion() {
  suggestion.value = null
  requestSeq++
  if (debounceTimer !== undefined) {
    clearTimeout(debounceTimer)
    debounceTimer = undefined
  }
}

async function fetchSuggestion(query: string) {
  const seq = ++requestSeq
  try {
    const completion = await postSuggest(query)
    // 補完は補助機能: 応答が古い場合・入力が変わった場合は捨てる
    if (seq === requestSeq && text.value === query) {
      suggestion.value = completion
    }
  } catch {
    // API失敗時は候補を出さないだけでエラー表示はしない
    if (seq === requestSeq) {
      suggestion.value = null
    }
  }
}

function scheduleSuggest() {
  suggestion.value = null
  if (debounceTimer !== undefined) {
    clearTimeout(debounceTimer)
    debounceTimer = undefined
  }
  if (props.disabled || isComposing.value || text.value.length === 0) {
    return
  }
  const query = text.value
  debounceTimer = setTimeout(() => {
    debounceTimer = undefined
    void fetchSuggestion(query)
  }, SUGGEST_DEBOUNCE_MS)
}

watch(text, scheduleSuggest)

watch(
  () => props.disabled,
  (now) => {
    if (now) {
      clearSuggestion()
    }
  },
)

function acceptSuggestion() {
  if (suggestion.value === null) {
    return
  }
  text.value = text.value + suggestion.value
  const textarea = textareaRef.value
  if (textarea) {
    void nextTick(() => {
      textarea.setSelectionRange(text.value.length, text.value.length)
    })
  }
}

onBeforeUnmount(() => {
  if (debounceTimer !== undefined) {
    clearTimeout(debounceTimer)
  }
})

function submit() {
  if (!canSend.value) {
    return
  }
  emit('send', text.value.trim())
  text.value = ''
  clearSuggestion()
}

function onKeydown(event: KeyboardEvent) {
  if (event.isComposing) {
    return
  }
  if (event.key === 'Tab' && suggestion.value !== null) {
    event.preventDefault()
    acceptSuggestion()
    return
  }
  if (event.key === 'Escape' && suggestion.value !== null) {
    event.preventDefault()
    clearSuggestion()
    return
  }
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    submit()
  }
}

function onCompositionStart() {
  isComposing.value = true
  clearSuggestion()
}

function onCompositionEnd() {
  isComposing.value = false
  // v-modelの反映とcompositionendの発火順は環境依存のため、
  // 変換確定後のテキストが確実に入った後で候補取得を仕掛け直す
  void nextTick(scheduleSuggest)
}
</script>

<template>
  <div class="shrink-0 border-t border-zinc-200 dark:border-zinc-800">
    <div class="mx-auto flex max-w-3xl items-end gap-2 px-5 py-4">
      <div class="relative flex-1">
        <!-- ゴーストテキスト: textareaと同じ字送りになるよう invisible な入力済みテキストで
             オフセットし、続きの候補だけを薄色で重ねる -->
        <div
          v-if="suggestion !== null"
          aria-hidden="true"
          class="pointer-events-none absolute inset-0 overflow-hidden border border-transparent px-4 py-2.5 text-[15px] whitespace-pre-wrap break-words"
        >
          <span class="invisible">{{ text }}</span><span class="text-zinc-400 dark:text-zinc-500">{{ suggestion }}</span>
        </div>
        <textarea
          ref="textareaRef"
          v-model="text"
          class="min-h-0 w-full resize-none rounded-xl border border-zinc-300 bg-white px-4 py-2.5 text-[15px] text-zinc-900 placeholder-zinc-400 transition-shadow outline-none focus:border-violet-500 focus:ring-2 focus:ring-violet-500/30 disabled:cursor-not-allowed disabled:bg-zinc-100 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-100 dark:placeholder-zinc-500 dark:disabled:bg-zinc-800"
          rows="1"
          placeholder="メッセージを入力..."
          :disabled="disabled"
          @keydown="onKeydown"
          @compositionstart="onCompositionStart"
          @compositionend="onCompositionEnd"
        />
      </div>
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
