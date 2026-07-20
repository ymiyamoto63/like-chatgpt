# テストレポート: ストリーミング風タイプライター応答＋コンポーネントのフェードイン演出（Issue #10）

対象要件定義書: `docs/pipeline/10/requirements.md`（AC-1〜AC-9）
対象設計書: `docs/pipeline/10/design.md`
対象実装ノート: `docs/pipeline/10/implementation-notes.md`

変更サイド: フロントエンドのみ（`frontend/src/constants/typewriter.ts`、`frontend/src/composables/useTypewriterReveal.ts`、`frontend/src/stores/conversationStore.ts`、`frontend/src/components/chat/MessageBubble.vue`、`frontend/src/components/chat/ChatWindow.vue`）。バックエンド無変更のためバックエンドテストは対象外。

## 1. 自動検証（AC-9）

| コマンド | 結果 | 備考 |
|---|---|---|
| `cd frontend && npx vue-tsc -b` | **PASS**（exit code 0） | 出力なし（エラーなし） |
| `cd frontend && npm run build`（内部で `vue-tsc -b && vite build` を実行） | **PASS**（exit code 0） | `✓ 58 modules transformed.` / `✓ built in 886ms` / `dist/` 出力を確認 |

AC-9 は自動検証により満たされていることを確認した。

## 2. 静的検証（AC-1〜AC-8。コードレビューによる構造確認）

自動テストランナー（vitest等）が未導入のため、実装コード（`useTypewriterReveal.ts`、`conversationStore.ts`、`MessageBubble.vue`、`ChatWindow.vue`、`main.ts`）を精読し、設計書のデータフロー・機能要件（FR-1〜FR-9）と突き合わせて構造上ACを満たす実装になっているかを検証した。

- **AC-1（FR-1: 1文字ずつ時間差表示）** — 構造上満たす。`useTypewriterReveal.ts` の `startTyping()` が `Array.from(options.text)` でコードポイント単位に分割し、`setInterval`（`TYPEWRITER_CHAR_INTERVAL_MS = 20ms`）で1文字ずつ `displayedText` に追加している（`useTypewriterReveal.ts:64-80`）。`MessageBubble.vue` のテンプレートは `{{ displayedText }}` を描画（`MessageBubble.vue:102`）。
- **AC-2（FR-2: テキスト完了後にのみコンポーネントがフェードイン）** — 構造上満たす。`revealComponents()` は `showComponents.value = true` → `nextTick()` → `requestAnimationFrame()` → `componentsVisible.value = true` という二段階トグルで `transition-opacity duration-300` を発火させる（`useTypewriterReveal.ts:48-62`）。テンプレート側は `v-if="showComponents"` でコンポーネント領域自体をタイピング中はDOMに挿入しない（`MessageBubble.vue:104`）ため、テキスト表示中は見えない。
- **AC-3（FR-2, FR-6: 表示内容が従来どおり）** — 構造上満たす。`TableView`/`BarChartView`等の個別コンポーネント自体・propsの受け渡し（`:spec="component"`）は無改修（`MessageBubble.vue:113-129`、実装ノートにも「コンポーネント自体は無改修、ラッパーのみ追加」と明記）。フェードインはラッパー`<div>`にのみ適用され、内部データは非対象（FR-6準拠）。
- **AC-4/AC-5（FR-3: 復元メッセージは演出なし即時表示）** — 構造上満たす。`animatingMessageIds` は `main.ts` の `$subscribe`（`main.ts:12-17`）が `state.conversations`／`state.activeConversationId` の2フィールドのみを `savePersistedState` に渡す構造になっており、`animatingMessageIds` は永続化対象に一切含まれない。`conversationStore.ts` の `state()` も常に `animatingMessageIds: [] as string[]` で初期化する（`conversationStore.ts:75`、`persisted` から復元しない）。`MessageBubble.vue` は setup 実行時（マウント時）に一度だけ `shouldAnimate = props.message.role === 'assistant' && store.isMessageAnimating(props.message.id)` を非リアクティブな `const` として固定する（`MessageBubble.vue:38-39`）。復元メッセージ（会話切替復帰・リロード）はいずれも `animatingMessageIds` に未登録のため `isMessageAnimating` は `false` を返し、`shouldAnimate = false` → `displayedText`/`showComponents`/`componentsVisible` はすべて即時確定値（`ref(props.message.content)`／`ref(true)`／`ref(true)`）で初期描画から完成形表示される（`MessageBubble.vue:54-58`）。
- **AC-6（FR-5: 演出中は入力欄・選択肢ボタンが操作不可）** — 構造上満たす。`ChatWindow.vue` の `choices-enabled` 算出式は `message.id === latestMessageId && !store.isLoading && !store.isMessageAnimating(message.id)`（`ChatWindow.vue:43-47`）で `isMessageAnimating` を参照。`conversationStore.ts` の `isInputLocked` ゲッターも同じ `animatingMessageIds` を参照する式 `hasChoices(last) || this.animatingMessageIds.includes(last.id)`（`conversationStore.ts:85-93`）に拡張済み。両者が同一の状態（`animatingMessageIds`）を参照する設計になっており、設計書が懸念していた「片方だけロック漏れ」は構造的に発生しない。`MessageInput` の `disabled` は `store.isLoading || store.isInputLocked`（`ChatWindow.vue:66`）。
- **AC-7（FR-4: アラート起点会話にも演出適用）** — 構造上満たす。`conversationStore.ts` の `createAlertConversation` はアシスタントメッセージを `push` した直後に `this.markMessageAnimating(message.id)` を呼んでいる（`conversationStore.ts:224-236`、特に234-235行目）。`MonitoringView.vue` から `createAlertConversation` が呼ばれていることも確認済み。`MessageBubble` 側は経路に関わらず同じ `isMessageAnimating` 判定を行うため、通常応答と同様に演出が適用される構造になっている。
- **AC-8（FR-1, FR-9: エラー吹き出しにも演出適用・非クラッシュ）** — 構造上満たす。`dispatchUserMessage` の `ChatResponseFormatError` 分岐（`conversationStore.ts:190-193`）、その他エラー分岐（`conversationStore.ts:194-201`）のいずれも、エラーメッセージ `push` 直後に `markMessageAnimating` を呼んでおり、通常応答と同一経路で演出される。`v-html` 等の生HTML描画は全ソース中に存在しないことを `grep -rn "v-html" frontend/src` で確認済み（該当なし）。`displayedText` は `{{ }}` 補間のみで描画（`MessageBubble.vue:102`）。エラー時も `try/catch` で例外を捕捉しメッセージオブジェクトを生成するのみで、描画側が例外を投げる要素はない。
- **FR-8（空文字スキップ）** — 構造上満たす。`startTyping()` は `characters.length === 0` の場合 `setInterval` を起動せず直ちに `revealComponents()` を呼ぶ（`useTypewriterReveal.ts:64-69`）。
- **タイマーのクリーンアップ** — `onUnmounted` で `clearInterval`/`clearTimeout` を確実に行っている（`useTypewriterReveal.ts:87-89`）。ただし下記「軽微な所見」参照。

### 軽微な所見（ACの合否には影響しないが記録）

- `useTypewriterReveal.ts` の `revealComponents()` 内で使われる `nextTick()`／`requestAnimationFrame()` のコールバックチェーンは、`onUnmounted` の `clearTimers()` が追跡・キャンセルする対象（`intervalId`／`timeoutId`）に含まれていない。理論上、最後の文字が表示されて `revealComponents()` が呼ばれた直後（`showComponents.value = true` 後、`requestAnimationFrame` コールバックが実行される前）というごく狭い窓でコンポーネントがアンマウントされた場合、アンマウント後に `componentsVisible.value = true` の設定と `settle()`（`onSettled` 経由で `store.settleMessageAnimation(id)`）が実行される可能性がある。これは設計書「リスク・エッジケース」節が明示する「アンマウント時は `onSettled` を呼ばない（会話切替で再訪時は演出をやり直す）」という意図的仕様と、この極めて狭い競合状態下でのみ整合しない。ただし影響は「再訪時に演出が再生されず即座に全体表示される」という、ユーザー体験上むしろ無害な方向のズレであり、AC-1〜AC-9のいずれにも違反しない（AC-4は「演出完了を待ってから切り替える」手順を前提としており、この競合状態はテスト対象外と設計書にも明記されている）。対応の要否は実装者・レビュアーの判断に委ねる。

### 静的検証で発見した問題

**なし。** AC-1〜AC-8はいずれも実装コードの構造上、要件を満たす形になっていることを確認した。上記「軽微な所見」はAC不合格には該当しない。

## 3. 手動確認手順（AC-1〜AC-8）

本エージェントはブラウザ操作ができないため、以下はユーザーが実施するためのチェックリストである（設計書「テスト戦略」節の手順をそのまま整理）。**いずれも未実施**。

事前準備: `cd frontend && npm run dev` でフロントエンドを起動し、バックエンドも起動しておく（AC-8のみ意図的に通信エラーを発生させる）。

- [ ] **AC-1**: メッセージを送信し、アシスタント応答テキストが一括表示されず、先頭から1文字ずつ時間差で表示されることを目視確認する。
- [ ] **AC-2**: 表またはグラフを含む応答（例:「今月の問い合わせ件数を担当者別にまとめて」）を送信し、テキスト表示中はコンポーネント領域が見えず、テキスト表示完了後にのみコンポーネントがフェードインで出現することを確認する。
- [ ] **AC-3**: 表とグラフの両方を含む応答（例: ダッシュボード系プロンプト）を送信し、演出完了後の表示内容（列・値・グラフの数値等）が本変更前と変わらないことを確認する。
- [ ] **AC-4**: 1つの会話でアシスタント応答（表・グラフ含む）の演出完了を待った後、サイドバーで別会話に切り替え、再度元の会話に戻る。過去メッセージが演出なしで即座に全体表示されることを確認する。
- [ ] **AC-5**: 会話にいくつかメッセージを送った状態でブラウザをリロードし、localStorageから復元された会話のメッセージが演出なしで即座に全体表示されることを確認する。
- [ ] **AC-6**: 応答テキストの文字送り表示中、およびコンポーネントのフェードイン完了までの間、`MessageInput`に文字を入力して送信ボタンを押しても送信できない（またはボタンがdisabled状態である）こと、当該メッセージの選択肢／FAQボタンがクリックできないことを確認する。フェードイン完了後は通常どおり送信・選択できることを確認する。
- [ ] **AC-7**: モニタリング画面でアラートバナーをクリックしてチャット画面へ遷移し、アラート文＋選択肢のメッセージが同様にタイプライター表示＋フェードイン演出されることを確認する。
- [ ] **AC-8**: バックエンドを一時停止する、またはDevToolsでネットワークをブロックする等して通信エラーを発生させ、エラーメッセージ吹き出し（「エラー: 応答を取得できませんでした」等）が他のアシスタント応答と同様にタイプライター表示されること、画面がクラッシュ（白画面・コンソール例外での描画停止等）しないことを確認する。

## 4. 受け入れ基準カバレッジ一覧

| AC | 内容（要約） | 検証区分 | 判定 |
|---|---|---|---|
| AC-1 | 応答テキストが1文字ずつ時間差表示 | 手動確認（タグどおり） | 静的検証: 構造上満たす。手動: 未実施（手順記載済み） |
| AC-2 | テキスト完了後にのみコンポーネントがフェードイン、表示中は見えない | 手動確認 | 静的検証: 構造上満たす。手動: 未実施（手順記載済み） |
| AC-3 | 表+グラフ併存時も表示内容が従来どおり | 手動確認 | 静的検証: 構造上満たす。手動: 未実施（手順記載済み） |
| AC-4 | 会話切替→復帰で過去メッセージは演出なし即時表示 | 手動確認 | 静的検証: 構造上満たす。手動: 未実施（手順記載済み） |
| AC-5 | リロード復元時は演出なし即時表示 | 手動確認 | 静的検証: 構造上満たす。手動: 未実施（手順記載済み） |
| AC-6 | 演出中は入力欄・選択肢ボタンが操作不可、完了後は操作可能 | 手動確認 | 静的検証: 構造上満たす。手動: 未実施（手順記載済み） |
| AC-7 | アラート起点会話にも同様の演出が適用される | 手動確認 | 静的検証: 構造上満たす。手動: 未実施（手順記載済み） |
| AC-8 | エラー吹き出しにも演出適用、画面はクラッシュしない | 手動確認 | 静的検証: 構造上満たす。手動: 未実施（手順記載済み） |
| AC-9 | `vue-tsc -b` と `npm run build` がエラーなく完了 | 自動テスト | **PASS**（実行済み・上記コマンド結果参照） |

## 5. 総合判定

**テスト green（自動検証は全てPASS、静的検証で構造上の問題は発見されず）。**

- 自動検証（AC-9）: `npx vue-tsc -b`・`npm run build` いずれもエラーなく完了（exit code 0）。
- 静的検証（AC-1〜AC-8）: コードレビューにより、いずれも要件（FR-1〜FR-9）を満たす構造で実装されていることを確認した。問題点は発見されなかった（軽微な所見1件のみ、ACには不合格の影響なし）。
- 手動確認（AC-1〜AC-8）: 本エージェントはブラウザ操作ができないため未実施。上記チェックリストに従いユーザー側での目視確認が必要。
