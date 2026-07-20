import { nextTick, onUnmounted, ref } from 'vue'
import type { Ref } from 'vue'
import { TYPEWRITER_CHAR_INTERVAL_MS, TYPEWRITER_COMPONENT_FADE_MS } from '../constants/typewriter'

export interface UseTypewriterRevealOptions {
  text: string
  hasComponents: boolean
  onSettled: () => void
}

export interface UseTypewriterReveal {
  displayedText: Ref<string>
  showComponents: Ref<boolean>
  componentsVisible: Ref<boolean>
}

// アシスタント応答1メッセージ分の「文字送り表示 → コンポーネント一括フェードイン → 完了」
// というフェーズ遷移とタイマー管理を、呼び出し元コンポーネントのライフサイクルに紐づけて行う。
// ネットワーク通信は一切行わない純粋なローカルタイマー処理。
export function useTypewriterReveal(options: UseTypewriterRevealOptions): UseTypewriterReveal {
  const displayedText = ref('')
  const showComponents = ref(false)
  const componentsVisible = ref(false)

  let intervalId: ReturnType<typeof setInterval> | undefined
  let timeoutId: ReturnType<typeof setTimeout> | undefined
  let settled = false

  function clearTimers(): void {
    if (intervalId !== undefined) {
      clearInterval(intervalId)
      intervalId = undefined
    }
    if (timeoutId !== undefined) {
      clearTimeout(timeoutId)
      timeoutId = undefined
    }
  }

  function settle(): void {
    if (settled) {
      return
    }
    settled = true
    options.onSettled()
  }

  function revealComponents(): void {
    if (!options.hasComponents) {
      settle()
      return
    }
    showComponents.value = true
    void nextTick(() => {
      requestAnimationFrame(() => {
        componentsVisible.value = true
        timeoutId = setTimeout(() => {
          settle()
        }, TYPEWRITER_COMPONENT_FADE_MS)
      })
    })
  }

  function startTyping(): void {
    const characters = Array.from(options.text)
    if (characters.length === 0) {
      revealComponents()
      return
    }
    let index = 0
    intervalId = setInterval(() => {
      displayedText.value += characters[index]
      index += 1
      if (index >= characters.length) {
        clearInterval(intervalId)
        intervalId = undefined
        revealComponents()
      }
    }, TYPEWRITER_CHAR_INTERVAL_MS)
  }

  startTyping()

  // アンマウント時は onSettled を呼ばない。会話切替でメッセージが表示途中のまま
  // 離脱した場合、ストア側の animatingMessageIds に ID を残したままにし、次に同じ
  // メッセージが再マウントされたときは最初から演出をやり直す、という仕様のため。
  onUnmounted(() => {
    clearTimers()
  })

  return { displayedText, showComponents, componentsVisible }
}
