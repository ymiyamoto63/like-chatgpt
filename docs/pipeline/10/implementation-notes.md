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

## ステップ3: ストア改修

### 変更ファイル

- `frontend/src/stores/conversationStore.ts`
  - `state()` に `animatingMessageIds: [] as string[]` を追加。`isLoading` と同様、`persisted` から復元せず常に空配列で初期化する（`main.ts` の `$subscribe` は `state.conversations`／`state.activeConversationId` のみを保存対象としており、このファイルには一切手を入れていないため、構造的に localStorage には混入しない）。
  - action `markMessageAnimating(id: string)`（`animatingMessageIds.push(id)`）、`settleMessageAnimation(id: string)`（`indexOf` で見つけて `splice` で除去、未登録IDなら何もしない）を追加。
  - getter `isMessageAnimating`（`(state) => (id: string) => state.animatingMessageIds.includes(id)`）を設計書の式どおりに追加。
  - `isInputLocked` getter を `!!last && last.role === 'assistant' && (hasChoices(last) || this.animatingMessageIds.includes(last.id))` に拡張（設計書の式をそのまま適用）。
  - `deleteConversation(id)`: `splice` の戻り値（削除された `Conversation`）から `messages` の ID 集合 (`Set`) を作り、`animatingMessageIds` を `filter` でその集合に含まれないものだけに絞り込む処理を追加。
  - `dispatchUserMessage`: 成功パス（`postChat` 経由）、`ChatResponseFormatError` の catch 分岐、それ以外のエラーの catch 分岐の3箇所すべてで、`createMessage('assistant', ...)` の戻り値を `message` 変数に保持してから `conversation.messages.push(message)` → `this.markMessageAnimating(message.id)` の順で呼ぶよう変更。
  - `createAlertConversation`: 同様に `message` 変数へ保持 → `push` → `markMessageAnimating(message.id)` を追加。

### markMessageAnimating を呼ぶよう改修した箇所一覧

1. `dispatchUserMessage` 成功パス（`postChat` の応答メッセージ push 直後）
2. `dispatchUserMessage` catch 分岐（`ChatResponseFormatError` 発生時のメッセージ push 直後）
3. `dispatchUserMessage` catch 分岐（その他エラー発生時のメッセージ push 直後）
4. `createAlertConversation`（アラート起点の選択肢付きメッセージ push 直後）

### 設計からの逸脱

なし。設計書「実装ステップ」ステップ3および「データフロー」節の記述どおりに実装した。`Message`/`Conversation` 型、`conversationPersistence.ts`、`main.ts` は変更していない。

### 今回のスコープ外（未実施）

- `MessageBubble.vue`（composable統合）、`ChatWindow.vue`（`choicesEnabled` 拡張）の改修はステップ4・5で実施予定。現時点では追加した `isMessageAnimating` getter・`markMessageAnimating`/`settleMessageAnimation` action はどのビュー層からも未使用。

### 検証結果

- `cd frontend && npx vue-tsc -b` を実行し、エラーなく完了（出力なし）。
- `git status --porcelain` で `frontend/src/stores/conversationStore.ts` のみが変更されていることを確認（`main.ts`・型定義・永続化ロジックへの変更なし）。
