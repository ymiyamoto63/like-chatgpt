# テストレポート — サイドバー「ダッシュボード」ボタン追加

対象要件: `docs/pipeline/8/requirements.md`（AC-1〜AC-8）
対象設計: `docs/pipeline/8/design.md`
実装ノート: `docs/pipeline/8/implementation-notes.md`
検証日: 2026-07-20
対象コミット: `aabe41c`（"step 1/1: サイドバーにダッシュボードボタンと sendDashboardPrompt を追加 (#8)"）、比較基準: `5238ec1`

## Commands run

```
cd /home/miyam/like-chatgpt/frontend && npx vue-tsc -b
cd /home/miyam/like-chatgpt/frontend && npm run build
cd /home/miyam/like-chatgpt/backend && ./mvnw test
git diff 5238ec1 -- frontend/src/components/layout/Sidebar.vue
git diff 5238ec1 -- frontend/src/stores/conversationStore.ts
grep -n "サマリー\|ダッシュボード" -r backend/src/main/java/
git status --porcelain=v1
```

## Result

全項目 pass（自動検証範囲）。フロントエンド自動テストランナー未導入のため、AC-1〜6, AC-8 はコードの静的確認＋残る手動確認手順の提示で対応（下表参照）。検証中の一時変更なし、`git status` はクリーン（自分の変更なし、検証後も再確認済み）。

| 区分 | 結果 |
|---|---|
| `npx vue-tsc -b` | 成功（エラーなし、exit code 0） |
| `npm run build` | 成功（`vue-tsc -b && vite build` とも正常終了、`dist/` 出力あり） |
| `./mvnw test`（backend 非回帰） | `Tests run: 55, Failures: 0, Errors: 0, Skipped: 0` / `BUILD SUCCESS`（`ArchitectureTest` 4件含め全パス） |
| Sidebar.vue 静的確認（ボタン順序・disabled・クラス・非干渉diff） | 確認済み・要件どおり |
| conversationStore.ts 静的確認（新規会話→送信・トリガー文言一致） | 確認済み・要件どおり |

## Failures

なし。

## 詳細ログ（抜粋）

### `npx vue-tsc -b`
標準出力・標準エラーともに空、exit code 0（型エラーなし）。

### `npm run build`
```
> frontend@0.0.0 build
> vue-tsc -b && vite build

vite v7.3.6 building client environment for production...
transforming...
✓ 56 modules transformed.
rendering chunks...
computing gzip size...
dist/index.html                   0.46 kB │ gzip:  0.29 kB
dist/assets/index-DcdHohkB.css   28.87 kB │ gzip:  6.30 kB
dist/assets/index-B3i_ILfC.js   103.78 kB │ gzip: 37.52 kB
✓ built in 823ms
EXIT_CODE=0
```

### `./mvnw test`
```
[INFO] Results:
[INFO]
[INFO] Tests run: 55, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] BUILD SUCCESS
```
内訳: `ChatBackendApplicationTests`(1) / `KeywordMatchReplyGenerationAdapterTest`(22) / `RandomWalkMetricsGenerationAdapterTest`(3) / `CannedPhraseSuggestionAdapterTest`(7) / `SuggestControllerTest`(3) / `ChatControllerTest`(14) / `MonitoringControllerTest`(1) / `ArchitectureTest`(4)。
バックエンドは本タスクの非ゴール（要件・設計書で明言）どおり無変更であることを `git diff 5238ec1 -- backend/` 未実施だがコードレビューと mvnw test 結果から非回帰を確認。念のため差分も確認（下記コマンド）。

```
git diff 5238ec1 -- backend/
```
→ 出力なし（backend ディレクトリに差分なし。フロントエンドのみの変更という前提を裏付け）。

### `git diff 5238ec1 -- frontend/src/components/layout/Sidebar.vue`
`handleDashboard()` ハンドラ追加（`handleNewInquiry` の直後）と、「新規問い合わせ」ボタンと「システムモニタリング」ボタンの間への「ダッシュボード」ボタン追加のみが差分。既存ボタン（New Chat／新規問い合わせ／システムモニタリング／定型レポート各種）、会話リスト（選択・リネーム・削除）部分に diff なし。

### `git diff 5238ec1 -- frontend/src/stores/conversationStore.ts`
`DASHBOARD_TRIGGER` 定数の追加（`INQUIRY_TRIGGER` の直後）と、`sendDashboardPrompt()` アクションの追加（`startInquiry()` の直後）のみが差分。既存アクション（`sendReportPrompt`／`startInquiry`／`dispatchUserMessage` 等）に変更なし。

### バックエンドのトリガーキーワード突き合わせ
`backend/.../KeywordMatchReplyGenerationAdapter.java`:
```java
private static final String SUMMARY_KEYWORD = "サマリー";
private static final String DASHBOARD_KEYWORD = "ダッシュボード";
...
if (message.contains(SUMMARY_KEYWORD) || message.contains(DASHBOARD_KEYWORD)) {
    return dashboardScenario();
}
```
フロント側 `DASHBOARD_TRIGGER = 'ダッシュボードを表示して'` は部分文字列として `"ダッシュボード"` を含むため `message.contains(DASHBOARD_KEYWORD)` が真になり、シナリオG（`dashboardScenario()`）が発火することをコード上確認した。

## Acceptance criteria coverage

| AC | 内容 | 検証方法（要件書指定） | 結果 | 根拠 |
|---|---|---|---|---|
| AC-1 | 「新規問い合わせ」「ダッシュボード」「システムモニタリング」の順でボタン表示 | 手動確認 | 静的確認で pass（残る目視確認は手動確認手順を参照） | `Sidebar.vue` テンプレートで「新規問い合わせ」ボタン（89行目付近）→「ダッシュボード」ボタン（91〜98行目、新規追加）→「システムモニタリング」ボタン（99〜110行目）の順に配置されていることをソース上で確認 |
| AC-2 | 押下で新規会話作成・固定文言送信・3コンポーネント応答表示 | 手動確認 | 静的確認で pass（応答の実描画は手動確認要） | `handleDashboard()` → `store.sendDashboardPrompt()` → `createConversation()`（同期でactiveConversationId確定）→ `sendMessage(DASHBOARD_TRIGGER)`。トリガー文言がバックエンドのシナリオGを発火することを上記で確認済み。`stat_cards`/`donut_chart`/`trend_chart` の3コンポーネント応答自体は Issue #6/PR #7 で実装済み・検証済み（本タスクの非ゴール）につき、実描画確認は下記手動確認手順で実施要 |
| AC-3 | 進行中の会話にはメッセージが追加されず別の新規会話に送信される | 手動確認（自動テストランナー未導入のため） | 静的確認で pass（挙動の目視確認は手動確認要） | `sendDashboardPrompt()` は `createConversation()`（新規会話を作成し `activeConversationId` を新会話IDに更新）を `sendMessage()` より先に同期実行するため、後続の `sendMessage()` は必ず新規会話に対して送信される。`sendReportPrompt`/`startInquiry` と同一構造であることをコードで確認 |
| AC-4 | モニタリング画面表示中に押すとチャット画面に切替＋応答表示 | 手動確認 | 静的確認で pass（画面切替の目視確認は手動確認要） | `handleDashboard()` 内で `monitoringStore.showChat()` が `store.sendDashboardPrompt()` より先に呼ばれる順序をソースで確認（`handleNewInquiry`/`handleReport` と同一パターン） |
| AC-5 | `isLoading` 中はボタン無効化、連打しても会話は1つ | 手動確認 | 静的確認で pass（連打時の目視確認は手動確認要） | 追加されたボタンに `:disabled="store.isLoading"` が付与されていることをソースで確認（`新規問い合わせ`ボタンと同一属性）。`isLoading` は `dispatchUserMessage()` 内で `true`/`false` が制御される既存ロジックをそのまま利用しており変更なし |
| AC-6 | `activeScreen` 連動ハイライトなし、常に「新規問い合わせ」と同じ見た目 | 手動確認 | 静的確認で pass | 追加ボタンの `class` 文字列が「新規問い合わせ」ボタンと完全一致（`w-full rounded-lg border border-zinc-200 bg-white px-3 py-2 text-sm font-medium text-zinc-700 shadow-sm transition-colors hover:bg-zinc-100 disabled:cursor-not-allowed disabled:opacity-50 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-200 dark:hover:bg-zinc-700`）であり、`:class` の三項式（「システムモニタリング」ボタンが持つ `activeScreen` 連動ハイライト）が付与されていないことをソースで確認 |
| AC-7 | `npx vue-tsc -b` / `npm run build` 成功 | 自動テスト（コマンド実行） | **pass** | 上記コマンド実行結果（両方 exit code 0、`dist/` 出力あり）で実際に確認済み |
| AC-8 | 既存ボタン・会話履歴機能の挙動が変わらない | 手動確認 | 静的確認で pass（実操作での目視確認は手動確認要） | `git diff 5238ec1 -- frontend/src/components/layout/Sidebar.vue` で、既存ボタン（New Chat／新規問い合わせ／システムモニタリング／定型レポート各種）・会話リスト（選択・リネーム・削除）部分に diff がないことを確認。`git diff 5238ec1 -- frontend/src/stores/conversationStore.ts` でも既存アクション（`sendReportPrompt`/`startInquiry`/`dispatchUserMessage`等）に変更がないことを確認。加えて `git diff 5238ec1 -- backend/` が空であることを確認し、バックエンド側の非回帰も `./mvnw test`（55件全パス）で裏付け |

### 残る手動確認手順（AC-1〜6, AC-8。ブラウザ操作が必要なため本検証では未実施）

前提: `cd frontend && npm run dev`、`cd backend && ./mvnw spring-boot:run`（もしくは既存の起動手順）でアプリを起動し、ブラウザでアクセスする。

1. **AC-1**: サイドバーを開き、上から「New Chat」「新規問い合わせ」「ダッシュボード」「システムモニタリング」の順にボタンが並んでいることを目視確認する。
2. **AC-2**: チャット画面で「ダッシュボード」ボタンを押す。新規会話が会話一覧の先頭に作成・アクティブ化され、ユーザー吹き出しに「ダッシュボードを表示して」、続けてアシスタント吹き出しに KPI カード（`stat_cards`）・ドーナツチャート（`donut_chart`）・トレンドチャート（`trend_chart`）の3コンポーネントが表示されることを確認する。
3. **AC-3**: メッセージが1件以上ある既存の会話を選択した状態で「ダッシュボード」ボタンを押す。選択していた会話にメッセージが追加されていないこと、会話一覧の先頭に新規会話が作られ、そちらに手順2の内容が送信されていることを確認する。
4. **AC-4**: 「システムモニタリング」ボタンを押してモニタリング画面に切り替えたあと、「ダッシュボード」ボタンを押す。チャット画面に自動的に切り替わり、手順2と同じ応答が表示されることを確認する。
5. **AC-5**: 「ダッシュボード」ボタンを押した直後（応答待ち中、ローディング表示等で `isLoading` が `true` と分かる間）に連打する。会話が1つしか作成されないこと、ボタンの見た目が `disabled:opacity-50` の薄い表示になり操作不能であることを確認する。
6. **AC-6**: チャット画面表示中とモニタリング画面表示中の両方で「ダッシュボード」ボタンの配色を確認し、`activeScreen` の値によらず常に「新規問い合わせ」ボタンと同じ配色（色反転・アクティブハイライトなし）であることを確認する。
7. **AC-8**: 「新規問い合わせ」ボタン、「定型レポート」各ボタン、「システムモニタリング」ボタン、会話履歴の選択・リネーム（鉛筆アイコン）・削除（✕アイコン）を一通り操作し、本変更前と挙動・見た目が変わっていないことを確認する。

## その他の確認事項

- 検証で一時的なコード変更は行っていない。検証前後で `git status --porcelain=v1` は空（自分による変更なし）であることを確認済み。
- port 8080 等のバックエンドプロセスは今回の検証では起動していない（`vue-tsc`/`build`/`mvnw test` のみで完結。手動確認手順の実施時は、既存プロセスに触れず必要なら別ポートで自分のプロセスを起動・終了すること）。
