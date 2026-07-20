# 実装設計書: ストリーミング風タイプライター応答＋コンポーネントのフェードイン演出（Issue #10）

対象要件定義書: `docs/pipeline/10/requirements.md`

## Approach（アプローチ概要）

- アシスタントの応答メッセージについて、`MessageBubble.vue` の描画をローカルな「タイプライター表示 → コンポーネント一括フェードイン」のフェーズ機構に置き換える。文字送り・フェーズ遷移のタイマー制御は新規 composable `frontend/src/composables/useTypewriterReveal.ts` に切り出し、`MessageBubble.vue` はこの composable を消費するだけにする。
- 「このメッセージは今回のセッションで新規到着したか、復元されたか」の判定は **`Message` 型やlocalStorage永続化データには一切持たせず**、Pinia ストア (`conversationStore.ts`) 側に `animatingMessageIds: string[]`（現在アニメーション中＝タイピングまたはフェードイン未完了のメッセージ ID 一覧）という独立した state を追加して管理する。アシスタントメッセージを生成する箇所（`dispatchUserMessage` の成功/失敗パス、`createAlertConversation`）で ID を登録し、`MessageBubble` 側で演出が完了した時点でストアの action `settleMessageAnimation(id)` を呼んで登録を外す。
- `main.ts` の `$subscribe` は既に `state.conversations` と `state.activeConversationId` の2フィールドだけを取り出して `savePersistedState` に渡している（`isLoading` も同様に除外されている）。`animatingMessageIds` をこの2フィールドの外に置くことで、**構造的に** localStorage への混入を避ける（`isConversation` バリデーションの緩さに依存しない設計）。
- フェードイン演出は Tailwind の `transition-opacity` ユーティリティクラスとブール値の二段階トグル（`opacity-0` → 次フレームで `opacity-100`）で実現する。`<Transition>` コンポーネントや `<style>` ブロックによる CSS 定義は使わない（このリポジトリのコンポーネントは一貫して Tailwind ユーティリティのみで構成されており、制約セクションにも「既存の `transition-*` の流儀に合わせる」と明記されているため）。
- 「新規到着メッセージか復元メッセージか」の判定は `MessageBubble` の `setup` 実行時（マウント時）に一度だけ `store.isMessageAnimating(message.id)` を読み、ローカルの非リアクティブな `const shouldAnimate` に固定する。復元メッセージは元々 `animatingMessageIds` に登録されないため常に `false` となり、テキスト・コンポーネントとも初期描画から完成形で即時表示される。
- 選択肢／FAQボタンの活性化 (`ChatWindow.vue` の `choicesEnabled`) と `MessageInput` の活性化 (`conversationStore.ts` の `isInputLocked`) は、どちらも同じ `store.isMessageAnimating(id)` を参照するよう改修し、二重管理による不整合（片方だけロック漏れ）を防ぐ。

## Alternatives considered

- **演出フラグの置き場所**: `Message` 型に `isNew`/`animated` 等のフラグを足し、`conversationPersistence.ts` の保存直前にフラグを取り除く案も検討した。しかし `isConversation` の型ガードは構造的な緩いチェックしか行わないため、実装漏れがあれば静かに localStorage に混入するリスクが残る。今回採用した「Pinia state 側の完全に別フィールドとして持ち、`$subscribe` が最初から拾わない」設計は、実装ミスをしても混入し得ない構造的な安全性がある点で優る。
- **フェードインの実装方式**: Vue の `<Transition>` コンポーネント＋ `<style scoped>` のトランジションクラス定義も検討した。挙動としては初回マウント時にデフォルトで enter トランジションを再生しない（`appear` 未指定時）性質が「復元メッセージは演出なし」に自然に合致し魅力的だったが、このリポジトリには `<style>` ブロックを使うコンポーネントが一つもなく、フェードイン以外はすべて Tailwind ユーティリティクラスのみで完結している。制約セクションが明示的に「既存の `transition-*` の流儀に合わせることが望ましい」としているため、既存流儀を優先し Tailwind クラスの二段階トグルを採用した。

## API contract

該当なし。今回はフロントエンドのみの変更であり、`POST /api/chat` のリクエスト/レスポンス形状・通信方式（1回のJSON応答）は変更しない（FR-7, AC-9 の型検査・ビルドで裏付け）。`docs/api/*.md` の更新も不要。

## Files affected

| ファイル | 変更内容 |
|---|---|
| `frontend/src/constants/typewriter.ts`（新規） | 文字送り間隔・フェード所要時間の定数を定義 |
| `frontend/src/composables/useTypewriterReveal.ts`（新規） | タイプライター表示～コンポーネントフェードインのフェーズ状態機械とタイマー制御を実装する composable |
| `frontend/src/stores/conversationStore.ts` | `animatingMessageIds` state、`markMessageAnimating`/`settleMessageAnimation` action、`isMessageAnimating` getter を追加。`isInputLocked` getter を拡張。`dispatchUserMessage`・`createAlertConversation`・`deleteConversation` を改修 |
| `frontend/src/components/chat/MessageBubble.vue` | composable を統合し、`message.content` を段階的に表示、`message.components` をまとめてフェードイン表示するようテンプレートを改修 |
| `frontend/src/components/chat/ChatWindow.vue` | `choicesEnabled` の算出式に `!store.isMessageAnimating(message.id)` を追加 |

変更なし（明示的に対象外であることを確認済み）:
- `frontend/src/types/chat.ts`: `Message`/`Conversation` 型は変更しない（演出状態を型に持たせない設計のため）。
- `frontend/src/stores/conversationPersistence.ts`: 永続化対象データの構造自体に手を入れないため変更不要。
- `frontend/src/components/chat/MessageInput.vue`: `disabled` prop の受け渡し方は変えず、呼び出し元 (`ChatWindow.vue`) が渡す `store.isInputLocked` の中身が拡張されるだけなので、このファイル自体は無変更。
- `frontend/src/components/dynamic-ui/*.vue`（`TableView` 等）: 内部データ・表示ロジックは無変更（FR-6）。フェードインは `MessageBubble.vue` 側でこれらをまとめて包むラッパー要素に対して行う。

## 新規モジュール: `useTypewriterReveal` composable

責務: 1メッセージ分の「文字送り表示 → コンポーネント一括フェードイン → 完了」のフェーズ遷移とタイマー管理を、呼び出し元コンポーネントのライフサイクルに紐づけて行う。ネットワーク通信は一切行わない純粋なローカルタイマー処理。

```ts
// frontend/src/composables/useTypewriterReveal.ts
export interface UseTypewriterRevealOptions {
  text: string
  hasComponents: boolean
  onSettled: () => void // フェーズが完全に完了した時点で一度だけ呼ばれる
}

export interface UseTypewriterReveal {
  displayedText: Ref<string>       // 現在時点で表示すべきテキスト（先頭からの部分文字列）
  showComponents: Ref<boolean>     // コンポーネント領域を DOM に挿入するか（v-if 用）
  componentsVisible: Ref<boolean>  // 挿入済みコンポーネント領域の opacity クラスを 100 にするか
}

export function useTypewriterReveal(
  options: UseTypewriterRevealOptions,
): UseTypewriterReveal
```

内部フェーズ（外部には公開しない実装詳細）:

1. `typing`: `Array.from(text)`（サロゲートペア対策）で文字配列を作り、`setInterval` で `TYPEWRITER_CHAR_INTERVAL_MS` ごとに1文字ずつ `displayedText` に追加。`text === ''` の場合はタイマーを起動せず即座に次フェーズへ（FR-8）。
2. `revealing-components`: `hasComponents === false` なら何もせず直ちに `done` へ。`true` なら `showComponents.value = true` にして次の描画で opacity-0 のコンポーネント領域を DOM に挿入し、`nextTick()` → `requestAnimationFrame()` を挟んで `componentsVisible.value = true` に切り替えて Tailwind の `transition-opacity` を発火させる。その後 `TYPEWRITER_COMPONENT_FADE_MS`（Tailwind 側の `duration-300` と値を一致させる）だけ `setTimeout` で待つ。
3. `done`: `options.onSettled()` を一度だけ呼び出す。
4. `onUnmounted()` フックをこの composable 内で登録し、`setInterval`/`setTimeout` の ID を確実に `clearInterval`/`clearTimeout` する。**アンマウント時は `onSettled` を呼ばない**（会話切替でメッセージが表示途中のまま離脱した場合、ストア側の `animatingMessageIds` に ID を残したままにし、次に同じメッセージが再マウントされたときは最初から演出をやり直す、という仕様として明示的に統一する。詳細は「リスク」参照）。

`text.role !== 'assistant'`（ユーザーメッセージ）や、演出対象外（復元メッセージ）の場合、`MessageBubble.vue` はそもそもこの composable を呼び出さない。

## データフロー

### 新規メッセージ到着（通常応答・エラー応答・アラート起点いずれも共通）

1. `conversationStore` のアクション（`dispatchUserMessage` の try/catch、または `createAlertConversation`）がアシスタントメッセージを `conversation.messages` に push した直後、同じ処理内で `this.markMessageAnimating(message.id)` を呼び `animatingMessageIds` に ID を追加する。
2. Vue が再描画し、`ChatWindow.vue` の `v-for` に新しい `MessageBubble` インスタンスがマウントされる。
3. `MessageBubble` の `setup()` 実行時、`message.role === 'assistant' && store.isMessageAnimating(message.id)` を一度だけ評価し `shouldAnimate` に固定する（新規到着なので `true`）。
4. `shouldAnimate === true` なので `useTypewriterReveal` を呼び出し、`typing` フェーズが開始（`text` が空なら即スキップ）。
5. 文字送り完了 → コンポーネントがあれば `revealing-components` フェーズでフェードイン、なければ直ちに `done`。
6. `done` 到達時、composable が `onSettled` コールバックを呼び、`MessageBubble` はストアの `settleMessageAnimation(message.id)` を実行して `animatingMessageIds` から ID を除去する。
7. `ChatWindow.vue` の `choicesEnabled` 算出（`store.isMessageAnimating(message.id)` を参照）と `conversationStore.isInputLocked` ゲッター（同様に参照）がリアクティブに再評価され、選択肢ボタン・`MessageInput` が有効化される（選択肢を含むメッセージの場合は既存仕様どおり選択肢が押されるまで `MessageInput` はロックされ続ける）。

### 復元メッセージ（会話切替・ページ再読み込み）

1. 会話切替: `ChatWindow.vue` の `messages` computed が別会話の `messages` 配列を参照するようになり、`v-for :key="message.id"` により旧メッセージ群の `MessageBubble` はアンマウント、新しい会話のメッセージ群がマウントされる。
2. ページ再読み込み: `conversationStore` の state は `loadPersistedState()` から初期化されるが、`animatingMessageIds` は `state()` 内で常に空配列として初期化される（永続化対象外のため復元されない）。
3. いずれの場合も `MessageBubble` マウント時に `store.isMessageAnimating(message.id)` は `false`（ID が登録されていないため）となり、`shouldAnimate = false` に固定される。composable は呼び出さず、`displayedText` は `message.content` を、`showComponents`/`componentsVisible` は常時 `true` を直接使う（別ブランチのローカル computed で処理し、opacity の値が初期描画から `100` のまま変化しないため Tailwind の `transition-opacity` があっても視覚的なフェードは発生しない）。

## 実装ステップ

各ステップの末尾で `cd frontend && npx vue-tsc -b` を実行しエラーがないことを確認する。

1. **定数ファイルの追加**: `frontend/src/constants/typewriter.ts` に `TYPEWRITER_CHAR_INTERVAL_MS`（例: 20）、`TYPEWRITER_COMPONENT_FADE_MS`（300。`MessageBubble.vue` 側の Tailwind `duration-300` と値を合わせるコメントを付す）をエクスポートする。既存コードへの参照はまだ発生しないため、`vue-tsc -b` が通ることのみ確認。
2. **composable の実装**: `frontend/src/composables/useTypewriterReveal.ts` を新規作成し、上記インターフェースとフェーズ遷移・クリーンアップを実装する。まだどこからも呼び出さない状態で型検査のみ通すことを確認する。
3. **ストアの改修**: `conversationStore.ts` に `animatingMessageIds: string[]` state、`markMessageAnimating(id)`/`settleMessageAnimation(id)` action、`isMessageAnimating` getter（`(state) => (id: string) => state.animatingMessageIds.includes(id)`）を追加する。`isInputLocked` getter を `!!last && last.role === 'assistant' && (hasChoices(last) || this.animatingMessageIds.includes(last.id))` に拡張する。`dispatchUserMessage` の成功パス・2つの catch 分岐、および `createAlertConversation` それぞれで、アシスタントメッセージを push した直後に `this.markMessageAnimating(message.id)` を呼ぶ（push した `Message` オブジェクトの `id` を変数に保持してから呼ぶ）。`deleteConversation` では、削除される会話の `messages` に含まれる ID を `animatingMessageIds` からも除去する（放置による配列肥大化防止）。この時点では `MessageBubble.vue`/`ChatWindow.vue` は未改修なので、追加した getter/state は未使用でも型検査・ビルドが通ることを確認する。
4. **`MessageBubble.vue` の改修**: `useConversationStore` と `useTypewriterReveal` を import。`shouldAnimate` を setup 時に一度だけ確定するロジックを実装し、`shouldAnimate` の真偽で「composable 経由の段階表示」と「即時全体表示」の2経路に分岐する `displayedText`/`showComponents`/`componentsVisible` を用意する。テンプレートを、`{{ message.content }}` の直接展開から `{{ displayedText }}` に変更し、`message.components` の `v-for` をまとめて包むラッパー `<div v-if="showComponents" class="mt-3 transition-opacity duration-300" :class="componentsVisible ? 'opacity-100' : 'opacity-0'">` を導入する（既存の個々の `mt-3 whitespace-normal` は各コンポーネントの縦間隔調整用として維持しつつ、ラッパーの位置づけを整理する）。`vue-tsc -b`・`npm run build` を確認する。
5. **`ChatWindow.vue` の改修**: `choices-enabled` の算出式を `message.id === latestMessageId && !store.isLoading && !store.isMessageAnimating(message.id)` に変更する。`vue-tsc -b`・`npm run build` を確認する。
6. **手動確認と最終ビルド確認**: 「テスト戦略」の手動確認手順を AC-1〜AC-8 それぞれについて実施し、最後に `cd frontend && npx vue-tsc -b && npm run build` を通しで実行し AC-9 を確認する。

## リスク・エッジケース

- **localStorage 汚染の再発防止**: `animatingMessageIds` は `conversationStore` の state ではあるが `Conversation`/`Message` の中には存在しない独立フィールドであり、`main.ts` の `$subscribe` は `state.conversations` と `state.activeConversationId` だけを取り出して保存している。今回の変更ではこの2フィールドの中身（`Message`/`Conversation` 型）に手を入れないため、構造的に localStorage には混入しない。実装時、誤って `Message` 型やその配列に演出用フィールドを追加しないこと。
- **演出中の会話切替**: アニメーションが完了する前に別会話へ切り替えると `MessageBubble` はアンマウントされるが、`settleMessageAnimation` は呼ばれない（composable は `onUnmounted` でタイマー破棄のみ行う）ため `animatingMessageIds` に ID が残る。元の会話に戻ると `MessageBubble` が再マウントされ、`shouldAnimate` は再び `true` と判定され、**演出は最初からやり直される**。これは仕様として意図的に統一した挙動であり、テスト時は「演出完了を待ってから会話を切り替える」手順（AC-4 の確認手順）と区別すること。
- **連続送信の防止**: `isInputLocked` を演出中も `true` にすることで、`MessageInput` はテキスト表示中～フェードイン完了まで送信不可のままになる。これにより「演出中に次のメッセージを送って `animatingMessageIds` に複数の ID が同時に積み上がる」状況は通常操作では発生しない。ただし `createAlertConversation`（モニタリング画面からの遷移）は独立した経路で新規会話を作成するため、理論上は別会話に対して同時に複数の演出が走り得るが、それぞれ独立した会話・独立した `MessageBubble` インスタンスなので競合しない。
- **タイマーのクリーンアップ**: 本機能はネットワーク通信を伴わない純粋なローカル `setInterval`/`setTimeout` のみで構成されており、過去のレビュー教訓にあるような「ポーリング等の非同期処理が画面遷移後に古い結果で state を上書きする」レースは原理的に発生しない（サーバー応答の順序入れ替わりが起きようがない）。ただし念のため、`useTypewriterReveal` は `onUnmounted` で必ず `clearInterval`/`clearTimeout` を行い、アンマウント後にタイマーコールバックが発火して意図しない state 変更を起こさないことを保証する。
- **サロゲートペア・絵文字**: 文字送りは `Array.from(text)` で Unicode コードポイント単位に分割し、`text.charAt(i)`/`text[i]` によるサロゲートペア破壊を避ける。
- **`choicesEnabled` と `isInputLocked` の整合性**: 両方とも同じ `store.isMessageAnimating(id)` を参照する設計にすることで、「選択肢だけ押せてしまうが入力欄はロックされている」ような不整合を防ぐ。実装時は両ファイルで同一のゲッターを呼び出していることをコードレビューで確認する。
- **`animatingMessageIds` の肥大化**: 演出完了前に会話が削除されると ID が回収されないまま残り得るため、`deleteConversation` 実行時に該当会話のメッセージ ID を `animatingMessageIds` からも除去する。
- **空文字・コンポーネントなしメッセージ**: `text === ''` かつ `hasComponents === false` のケース（理論上のみ）でも `typing`→`revealing-components`→`done` が同期的〜ごく短時間で完了し、`MessageInput` がロックされ続けることはない。
- **`v-html` 不使用の確認**: `displayedText` はテンプレートの `{{ }}` 補間でのみ描画し、`v-html` やその他の生 HTML 挿入 API は使用しない（既存実装も同様のため変更なし）。

## テスト戦略

### 自動（AC-9）
- 各実装ステップ完了時、および全ステップ完了後に `cd frontend && npx vue-tsc -b` と `cd frontend && npm run build` を実行し、エラーなく完了することを確認する。

### 手動（AC-1〜AC-8。フロントエンド自動テストランナー未導入のため、要件の検証タグどおり手動確認とする。E2E（Playwright 等）は未導入のため今回追加しない）
- **AC-1**: `npm run dev` で起動し、メッセージを送信。アシスタント応答テキストが一括表示されず、先頭から1文字ずつ時間差で表示されることを目視確認する。
- **AC-2**: 表またはグラフを含む応答（例:「今月の問い合わせ件数を担当者別にまとめて」）を送信し、テキスト表示中はコンポーネント領域が見えず、テキスト表示完了後にのみコンポーネントがフェードインで出現することを確認する。
- **AC-3**: 表とグラフの両方を含む応答（例:「今月の問い合わせ件数を担当者別にまとめて」等ダッシュボード系プロンプト）を送信し、演出完了後の表示内容（列・値・グラフの数値等）が本設計変更前と変わらないことを確認する。
- **AC-4**: 1つの会話でアシスタント応答（表・グラフ含む）の演出が完了するのを待った後、サイドバーで別会話に切り替え、再度元の会話に戻る。過去メッセージが演出なしで即座に全体表示されることを確認する。
- **AC-5**: 会話にいくつかメッセージを送った状態でブラウザをリロードし、localStorage から復元された会話のメッセージが演出なしで即座に全体表示されることを確認する。
- **AC-6**: 応答テキストの文字送り表示中、および表示完了後コンポーネントのフェードインが終わるまでの間、`MessageInput` に文字を入力して送信ボタンを押しても送信できない（またはボタンが disabled 状態である）こと、当該メッセージの選択肢／FAQボタンがクリックできないことを確認する。フェードイン完了後は通常どおり送信・選択ができることを確認する。
- **AC-7**: モニタリング画面でアラートバナーをクリックしてチャット画面へ遷移し、アラート文＋選択肢のメッセージが同様にタイプライター表示＋フェードイン演出されることを確認する。
- **AC-8**: バックエンドを一時停止する、または DevTools でネットワークをブロックする等して通信エラーを発生させ、エラーメッセージ吹き出し（「エラー: 応答を取得できませんでした」等）が他のアシスタント応答と同様にタイプライター表示されること、画面がクラッシュ（白画面・コンソール例外での描画停止等）しないことを確認する。

## AC マッピング

| AC | 内容（要約） | 実装ステップ | テスト戦略項目 |
|---|---|---|---|
| AC-1 | 応答テキストが1文字ずつ時間差表示される | ステップ2（composable）, ステップ4（MessageBubble統合） | 手動: AC-1 |
| AC-2 | テキスト完了後にのみコンポーネントがフェードイン、表示中は見えない | ステップ2, ステップ4 | 手動: AC-2 |
| AC-3 | 表+グラフ併存時も演出後の表示内容が従来どおり | ステップ4（コンポーネント自体は無改修、ラッパーのみ追加） | 手動: AC-3 |
| AC-4 | 会話切替→復帰で過去メッセージは演出なし即時表示 | ステップ3（`animatingMessageIds` が永続化されない設計）, ステップ4（`shouldAnimate` 判定） | 手動: AC-4 |
| AC-5 | リロード復元時は演出なし即時表示 | ステップ3, ステップ4 | 手動: AC-5 |
| AC-6 | 演出中は入力欄・選択肢ボタンが操作不可、完了後は操作可能 | ステップ3（`isInputLocked` 拡張）, ステップ5（`choicesEnabled` 拡張） | 手動: AC-6 |
| AC-7 | アラート起点会話にも同様の演出が適用される | ステップ3（`createAlertConversation` での `markMessageAnimating` 呼び出し）, ステップ4 | 手動: AC-7 |
| AC-8 | エラー吹き出しにも演出適用、画面はクラッシュしない | ステップ3（`dispatchUserMessage` の catch 分岐での `markMessageAnimating` 呼び出し）, ステップ4 | 手動: AC-8 |
| AC-9 | `vue-tsc -b` と `npm run build` がエラーなく完了 | 全ステップ（各ステップ末尾で確認） | 自動: `npx vue-tsc -b` / `npm run build` |
