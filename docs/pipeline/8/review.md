# レビュー結果 — サイドバー「ダッシュボード」ボタン追加 (#8)

対象ブランチ: `feature/8-sidebar-dashboard-button`
比較差分: `git diff 5238ec1cc1d2a77cbf643ba2c6530eb78c9f92f4..HEAD`
レビュー日: 2026-07-20

## レビュー範囲

- `git status` / `git diff` ともにワーキングツリーはクリーンで、コミット済み変更（4件のドキュメント/実装コミット）のみが対象。
- 実コード差分は `frontend/src/components/layout/Sidebar.vue`（+13行）と `frontend/src/stores/conversationStore.ts`（+7行）のみ。他は `docs/pipeline/8/` 配下のドキュメントのみで、`backend/`・`frontend/src/types/chat.ts`・`frontend/src/api/chatApi.ts`・`frontend/src/constants/reportButtons.ts`・`.vscode/settings.json` に差分なし（FR-5・制約を遵守）。
- `.vscode/settings.json` は untracked のままでコミットされていないことを確認（制約違反なし）。

## 個別確認結果

1. **`sendDashboardPrompt()` の同型性（FR-2/FR-3）**: `createConversation()`（同期・`activeConversationId`を即確定）→ `await sendMessage(DASHBOARD_TRIGGER)` という構造で、`startInquiry()`/`sendReportPrompt()` と完全に同型。`dispatchUserMessage()` は捕捉した `conversation` オブジェクト参照に対して push するため、await中に `activeConversationId` が変わっても誤った会話にメッセージが混入することはない（既存実装のまま、本タスクによる新規リスクなし）。連打対策も `isLoading` セットまでの同期実行チェーン（クリック→`sendDashboardPrompt`→`sendMessage`→`dispatchUserMessage`の`isLoading=true`代入）がJSの1ターン内で完結するため、既存2ボタンと同水準の安全性。
2. **モニタリング画面切替に伴うレース（lessons-learned 2026-07-18観点）**: `handleDashboard()` は `monitoringStore.showChat()` → `store.sendDashboardPrompt()` の順で、既存の `handleNewInquiry`/`handleReport`/`handleSelect`/`handleNewChat` と完全に同一パターン。`showChat()` はポーリング停止を伴わない同期的な `activeScreen` 切替のみで、この性質は本タスク以前から変わらない（`monitoringStore.ts` に差分なし）。チャット送信（`postChat`）とモニタリングの `fetchSnapshot`（世代カウンタ`fetchGeneration`で保護）は独立した非同期処理であり、相互に上書きし合う経路は存在しない。新規リスクなし。
3. **送信文言とバックエンドトリガーの一致**: `DASHBOARD_TRIGGER = 'ダッシュボードを表示して'` は `KeywordMatchReplyGenerationAdapter.java` の `DASHBOARD_KEYWORD = "ダッシュボード"` を部分文字列として含み、`message.contains(DASHBOARD_KEYWORD)` が真になりシナリオGが発火することをソースで確認済み。
4. **Sidebar.vue のボタン配置・スタイル**: 「新規問い合わせ」→「ダッシュボード」→「システムモニタリング」の順（AC-1）。`class` 文字列は「新規問い合わせ」ボタンと完全一致、`:disabled="store.isLoading"` あり（AC-5）、`activeScreen` 連動の `:class` 三項式なし（AC-6）。既存ボタン（New Chat／新規問い合わせ／システムモニタリング／定型レポート）・会話リスト（選択・リネーム・削除）部分の diff はゼロで非干渉（AC-8）。
5. **FR-5遵守**: 上記の通り diff は Sidebar.vue と conversationStore.ts のみで、バックエンド・型定義・APIクライアント・`reportButtons.ts`・`.vscode/settings.json` に変更なし。
6. **XSS/その他**: `v-html` 不使用、危険な動的評価なし。命名（`DASHBOARD_TRIGGER`、`sendDashboardPrompt`、`handleDashboard`）は既存の `INQUIRY_TRIGGER`/`startInquiry`/`handleNewInquiry` パターンに整合。

## 指摘事項

**blocking: 0件、non-blocking: 0件**

指摘なし。`sendReportPrompt()` を流用せず新規アクション `sendDashboardPrompt()` を追加した点はコード量としては数行の重複だが、設計書に明記された理由（意味ごとに専用アクション・専用定数を持つという既存の設計方針に揃える）に基づく妥当な判断であり、指摘には値しない。
