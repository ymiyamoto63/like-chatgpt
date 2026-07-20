# 実装ノート（Issue #10）

## ステップ1-2: 定数＋composable

### 作成ファイル

- `frontend/src/constants/typewriter.ts`（新規）
  - `TYPEWRITER_CHAR_INTERVAL_MS`（20）: 文字送り間隔。
  - `TYPEWRITER_COMPONENT_FADE_MS`（300）: コンポーネント領域のフェードイン所要時間。`MessageBubble.vue` 側で使う予定の Tailwind `duration-300` と値を一致させる旨のコメントを付した。
  - 既存の `frontend/src/constants/*.ts`（`monitoring.ts`・`monitoringAlert.ts` 等）の流儀に合わせ、`export const` を直接並べる形式・日本語コメントとした。
- `frontend/src/composables/useTypewriterReveal.ts`（新規、`composables/` ディレクトリごと新設）
  - 設計書「新規モジュール: `useTypewriterReveal` composable」節のインターフェース（`UseTypewriterRevealOptions`／`UseTypewriterReveal`）をそのまま実装。
  - フェーズ遷移: `typing`（`Array.from(text)` でコードポイント分割し `setInterval` で1文字ずつ `displayedText` に追加。`text === ''` はタイマーを起動せず即座に次フェーズへスキップ）→ `revealing-components`（`hasComponents === false` なら即 `done`。`true` なら `showComponents.value = true` → `nextTick()` → `requestAnimationFrame()` → `componentsVisible.value = true` → `TYPEWRITER_COMPONENT_FADE_MS` の `setTimeout` 待ち）→ `done`（`options.onSettled()` を一度だけ呼ぶ。`settled` フラグで多重呼び出しを防止）。
  - `onUnmounted()` をこの composable 内で登録し、`setInterval`/`setTimeout` の ID を確実に `clearInterval`/`clearTimeout` する。アンマウント時は `onSettled` を呼ばない（設計どおり、会話切替で表示途中のまま離脱した場合は次回再マウント時に演出をやり直す仕様）。
  - `verbatimModuleSyntax: true`（`@vue/tsconfig` 由来）のため、`Ref` 型は `import type { Ref } from 'vue'` として分離インポート。

### 設計からの逸脱

なし。設計書のインターフェース・フェーズ遷移・クリーンアップ方針をそのまま実装した。

### 今回のスコープ外（未実施）

- `conversationStore.ts`・`MessageBubble.vue`・`ChatWindow.vue` の改修（ステップ3〜5）は未実施。この時点では両ファイルとも他モジュールから未使用。
- composable の呼び出し元がまだ存在しないため、実際のブラウザ上でのタイマー挙動・フェードイン挙動の目視確認（手動確認AC）は次ステップ以降で実施する。

### 検証結果

- `cd frontend && npx vue-tsc -b` を実行し、エラーなく完了（出力なし）。
