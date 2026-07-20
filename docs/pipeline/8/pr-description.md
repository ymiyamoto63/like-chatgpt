## サマリー

サイドバーの「新規問い合わせ」ボタンと「システムモニタリング」ボタンの間に「ダッシュボード」ボタンを追加しました。ボタン押下時は、新規会話を作成して「ダッシュボードを表示して」というメッセージを送信し、バックエンドの既存ミニダッシュボード応答（KPI カード・ドーナツチャート・トレンドチャート）をワンクリック呼び出しできるようになります。

## 変更点

### フロントエンド（のみ）

- **`frontend/src/stores/conversationStore.ts`**:
  - `DASHBOARD_TRIGGER = 'ダッシュボードを表示して'` 定数を追加（`INQUIRY_TRIGGER` の直後）
  - `sendDashboardPrompt()` アクションを追加（`startInquiry()` の直後）。新規会話を作成して固定文言を送信

- **`frontend/src/components/layout/Sidebar.vue`**:
  - `handleDashboard()` ハンドラを追加（`handleNewInquiry` の直後）
  - 「新規問い合わせ」ボタンと「システムモニタリング」ボタンの間に「ダッシュボード」ボタンを挿入
  - ボタンは「新規問い合わせ」ボタンと同じセカンダリボタンスタイル
  - `:disabled="store.isLoading"` で応答待ち中に無効化

### 変更しない領域
- バックエンド全般（シナリオ G のトリガー「ダッシュボード」は既に実装済み）
- `frontend/src/types/chat.ts`、`frontend/src/api/chatApi.ts`（型・検証済み）
- `docs/api/chat-response-schema.md`（応答スキーマは変更なし）

## テスト結果

### 自動検証（完了・全パス）

- ✅ `cd frontend && npx vue-tsc -b` — 型エラーなし（exit code 0）
- ✅ `cd frontend && npm run build` — ビルド成功、`dist/` 出力あり
- ✅ `cd backend && ./mvnw test` — バックエンド非回帰テスト 55/55 パス（ArchitectureTest 4 件含む）

### 静的確認（完了・全パス）

- ✅ `Sidebar.vue`: ボタン順序（新規問い合わせ→ダッシュボード→システムモニタリング）、`:disabled="store.isLoading"` 属性、クラス一致、既存ボタン・会話リスト操作に変更なし
- ✅ `conversationStore.ts`: 新規会話作成 → メッセージ送信の順序（AC-3 保証）、トリガー文言がバックエンドシナリオ G 条件（"ダッシュボード"を含む）に一致
- ✅ バックエンド: キーワード「ダッシュボード」は既存実装で検出確認済み

## 残る手動確認項目

ブラウザ手動確認（`npm run dev` + `./mvnw spring-boot:run` で起動後）:

- AC-1: サイドバーに正しい順序で3ボタンが表示されること
- AC-2: ダッシュボードボタン押下で新規会話作成・固定文言送信・KPI カード＋ドーナツチャート＋トレンドチャートが表示されること
- AC-3: 既存会話選択中にボタン押下しても、その会話にはメッセージが追加されず、別の新規会話に送信されること
- AC-4: モニタリング画面表示中にボタン押下すると、チャット画面に自動切替＋応答が表示されること
- AC-5: 応答待ち中（`isLoading`）はボタンが無効化され、連打しても会話が1つだけ作成されること
- AC-6: ボタンが `activeScreen` 連動ハイライトなし、常に「新規問い合わせ」と同じ見た目であること
- AC-8: 既存の「新規問い合わせ」「定型レポート」「システムモニタリング」ボタン、会話選択・リネーム・削除の挙動が変わらないこと

詳細な手動確認手順は `/home/miyam/like-chatgpt/docs/pipeline/8/test-report.md` の「残る手動確認手順」セクションを参照してください。

---

Closes #8

🤖 Generated with [Claude Code](https://claude.com/claude-code)
