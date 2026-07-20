# 実装ノート

## サイドバー「ダッシュボード」ボタン追加

設計書: `docs/pipeline/8/design.md`（Implementation steps 1・2）

### 変更ファイル

- `frontend/src/stores/conversationStore.ts`（修正）
  - `INQUIRY_TRIGGER` 定数の直後に `export const DASHBOARD_TRIGGER = 'ダッシュボードを表示して'` を追加。
  - `actions.startInquiry()` の直後に `sendDashboardPrompt()` を追加。`startInquiry()`/`sendReportPrompt()` と同型（`createConversation()` → `sendMessage(DASHBOARD_TRIGGER)`）とし、`sendReportPrompt` のコメント文言に倣い「必ず新規会話に送信される」旨のコメントを付した（AC-3対応）。
- `frontend/src/components/layout/Sidebar.vue`（修正）
  - `<script setup>` の `handleNewInquiry()` の直後に `handleDashboard()`（`monitoringStore.showChat()` + `store.sendDashboardPrompt()`）を追加。
  - テンプレートの「新規問い合わせ」ボタンと「システムモニタリング」ボタンの間に、「新規問い合わせ」ボタンと同一の Tailwind クラス・`:disabled="store.isLoading"` を持つ「ダッシュボード」ボタンを追加。`monitoringStore.activeScreen` 連動のハイライト（`:class` 三項式）は付与していない（FR-4/AC-6）。

### 設計からの逸脱

なし。設計書の Implementation steps・コードスニペットをそのまま適用した。

### スコープ外（対応しなかった事項）

- バックエンド、`frontend/src/types/chat.ts`、`frontend/src/api/chatApi.ts`、`frontend/src/constants/reportButtons.ts`、`.vscode/settings.json` には一切変更を加えていない（設計書・要件の非ゴールどおり）。
- 手動確認シナリオ（AC-1〜6, AC-8）は本実装作業のスコープでは未実施。実施が必要な場合は別途手動確認を行うこと。

### 検証結果

- `cd frontend && npx vue-tsc -b` — 成功（エラーなし）。
- `cd frontend && npm run build` — 成功（`vue-tsc -b && vite build` ともに正常終了、dist 出力あり）。
