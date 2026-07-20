# サイドバー「ダッシュボード」ボタン追加 — 実装設計書

対象要件: `docs/pipeline/8/requirements.md`

## Approach

`frontend/src/components/layout/Sidebar.vue` の「新規問い合わせ」ボタンと「システムモニタリング」ボタンの間に、「新規問い合わせ」ボタンと同一の Tailwind クラス（セカンダリボタン様式、`activeScreen` 連動ハイライトなし）で「ダッシュボード」ボタンを追加する。クリックハンドラ `handleDashboard` は既存の `handleNewInquiry`/`handleReport` と同じ形（`monitoringStore.showChat()` → ストアアクション呼び出し）に揃える。呼び出し先は `conversationStore.ts` に新設する専用アクション `sendDashboardPrompt()` とする。このアクションは `startInquiry()` と全く同じ構造（`createConversation()` → `sendMessage(TRIGGER)`）を持ち、モジュールスコープの固定文言定数 `DASHBOARD_TRIGGER = 'ダッシュボードを表示して'` を送信する。`sendReportPrompt(label)` を流用する代替案は取らない（理由は次節）。バックエンド・型定義・APIクライアント・`reportButtons.ts`・`CannedPhraseSuggestionAdapter` には一切手を入れない。

## Alternatives considered

- **既存 `sendReportPrompt(label: string)` を `sendReportPrompt('ダッシュボードを表示して')` としてそのまま流用する案 vs. 新規アクション `sendDashboardPrompt()` を追加する案**: `sendReportPrompt` は実装上「`createConversation()` に続けて任意の文字列を `sendMessage()` する」だけの汎用処理であり、機械的には流用可能（`REPORT_BUTTONS` 定数への依存は無い）。しかし本リポジトリの既存コードは、同じ「新規会話作成＋固定文言送信」という構造を持つ `startInquiry()` を、`sendReportPrompt(INQUIRY_TRIGGER)` の呼び出しに寄せず、**独立したアクションとして自分の固定文言定数 `INQUIRY_TRIGGER` を保持する形**で実装している（`conversationStore.ts:9,183-186`）。これは「ボタンの意味（新規問い合わせ／定型レポート／ダッシュボード）ごとに専用アクションと専用定数を持つ」という既存の設計判断であり、`sendReportPrompt` という名前を意味の異なる「ダッシュボード」呼び出しに流用すると、将来 `sendReportPrompt` の実装を「定型レポート」固有の都合（例: `REPORT_BUTTONS` との整合チェック等）で変更した際にダッシュボードボタンが意図せず巻き込まれるリスクがある。`startInquiry()` の前例に倣い、`sendDashboardPrompt()` を新設する方を採用する。

## API contract

変更なし。本タスクはフロントエンドのみで完結し、`POST /api/chat` のリクエスト・レスポンス形状、バックエンド実装（`KeywordMatchReplyGenerationAdapter` のシナリオGトリガー判定含む）には一切変更を加えない。送信するメッセージ文言「ダッシュボードを表示して」は既存トリガー条件（メッセージが「サマリー」または「ダッシュボード」を含む）に一致することを確認済み（`docs/pipeline/6/design.md` シナリオG仕様と整合）。

## ヘキサゴナル構成・ArchUnitへの影響

影響なし。本タスクはバックエンドを一切変更しないため、`backend/src/test/java/com/example/chatbackend/architecture/ArchitectureTest.java` のいずれのルール（レイヤー依存方向、domain の外部依存禁止等）にも抵触しない。フロントエンドのみの変更であり、ヘキサゴナル構成（domain/application/adapter の分離）が対象とするバックエンドのコード配置に変更はない。

## Files affected

- `frontend/src/stores/conversationStore.ts` — モジュールスコープ定数 `DASHBOARD_TRIGGER = 'ダッシュボードを表示して'` を追加（`INQUIRY_TRIGGER` の直後）。`actions` に `sendDashboardPrompt()` を追加（`startInquiry()` の直後に配置）。
- `frontend/src/components/layout/Sidebar.vue` — `<script setup>` に `handleDashboard()` ハンドラを追加（`handleNewInquiry` の直後）。テンプレートの「新規問い合わせ」ボタンと「システムモニタリング」ボタンの間に「ダッシュボード」ボタンを追加。

他ファイルへの変更はなし（バックエンド・型定義・`chatApi.ts`・`reportButtons.ts`・`docs/api/chat-response-schema.md` は変更しない）。

## Implementation steps

規模が小さいため2ステップで実施する。各ステップ完了時点で `cd frontend && npx vue-tsc -b` が通ることを確認する。

1. **`conversationStore.ts` に `sendDashboardPrompt()` を追加（FR-2, FR-3の土台）**
   - `INQUIRY_TRIGGER` 定数の直後に `export const DASHBOARD_TRIGGER = 'ダッシュボードを表示して'` を追加する（`startInquiry` と同じく `export` して他所からの参照・将来のテスト追加に備える。現状 `Sidebar.vue` からは直接参照しないが、`INQUIRY_TRIGGER` の既存パターンに揃える）。
   - `actions` の `startInquiry()` の直後に以下を追加する。

     ```ts
     async sendDashboardPrompt() {
       this.createConversation()
       await this.sendMessage(DASHBOARD_TRIGGER)
     },
     ```

   - `createConversation()` は同期処理で `activeConversationId` を確定させるため、直後の `sendMessage()` は必ずこの新規会話に対して送信される（`sendReportPrompt`/`startInquiry` と同じ保証。AC-3 に対応するコメントを既存2アクション同様の粒度で付す）。
   - 確認: `cd frontend && npx vue-tsc -b`。

2. **`Sidebar.vue` にボタンとハンドラを追加（FR-1, FR-3, FR-4）**
   - `<script setup>` の `handleNewInquiry` 関数の直後に以下を追加する。

     ```ts
     function handleDashboard() {
       monitoringStore.showChat()
       store.sendDashboardPrompt()
     }
     ```

   - テンプレート内、「新規問い合わせ」ボタン（`@click="handleNewInquiry"`）の直後・「システムモニタリング」ボタンの直前に、「新規問い合わせ」ボタンと**同一の class 文字列**（`w-full rounded-lg border border-zinc-200 bg-white px-3 py-2 text-sm font-medium text-zinc-700 shadow-sm transition-colors hover:bg-zinc-100 disabled:cursor-not-allowed disabled:opacity-50 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-200 dark:hover:bg-zinc-700`）・`:disabled="store.isLoading"`・`@click="handleDashboard"` を持つボタンを追加し、ラベルを「ダッシュボード」とする。`monitoringStore.activeScreen` に連動する `:class` の三項式は付与しない（「システムモニタリング」ボタンのハイライトロジックは踏襲しない）。
   - 確認: `cd frontend && npx vue-tsc -b && npm run build`。

## Risks / edge cases

- **連打・多重送信**: `sendDashboardPrompt()` 実行中は `dispatchUserMessage()` 内で `this.isLoading = true` がセットされ、`Sidebar.vue` 側で `:disabled="store.isLoading"` によりボタンが無効化される。ただし `createConversation()` は `sendMessage()` 呼び出し前の同期処理であり `isLoading` を経由しないため、理論上は「クリック直後・`isLoading` が `true` になる直前」の極めて短い時間差で二重クリックが割り込む可能性がゼロではない。これは `sendReportPrompt`/`startInquiry` も全く同じ構造で抱えている既存の性質であり、本タスク固有の新規リスクではないため追加対策は行わない（AC-5 の検証は通常のクリック速度での手動確認で足りる）。
- **モニタリング画面からの遷移**: `monitoringStore.showChat()` を先に呼ぶことで `activeScreen` が `'chat'` に切り替わり、以降の `store.sendDashboardPrompt()` によるメッセージ・応答がチャット画面上に表示される。順序（`showChat()` が `sendDashboardPrompt()` より先）を誤ると、応答自体は生成されるがモニタリング画面が表示されたままになり AC-4 を満たさない点に注意。
- **進行中の会話を汚さないこと（AC-3）**: `sendDashboardPrompt()` は必ず `createConversation()` を先に呼ぶため、既存のアクティブ会話にメッセージが追加されることはない。`sendMessage()` 単体を先に会話選択済みの状態で呼ぶ実装に変更しないこと。
- **`isLoading` 中の他ボタンとの相互作用**: 「ダッシュボード」ボタン押下中（`isLoading === true`）は「新規問い合わせ」「定型レポート」ボタンも同時に無効化される（いずれも同じ `store.isLoading` を参照するため）。これは意図した挙動であり、既存ボタン同士の相互作用に変化はない（AC-8）。
- **`DASHBOARD_TRIGGER` とシナリオGトリガーの一致**: 文言「ダッシュボードを表示して」は部分一致で「ダッシュボード」を含むためシナリオGがトリガーされる。誤って別の言い回しに変更するとフォールバック応答（シナリオD）になってしまうため、`docs/pipeline/6/requirements.md`/`design.md` に記載のトリガー文字列（「サマリー」または「ダッシュボード」を含む）との整合を implementer 側で再確認すること。

## Test strategy

- **自動テスト（AC-7）**: `cd frontend && npx vue-tsc -b` および `cd frontend && npm run build` の成功をステップ1・2それぞれの完了後、および最終確認として実行する。フロントエンドに自動テストランナーは未導入のため（`pipeline-config.md` テスト方針を踏襲）、それ以外はすべて手動確認とする。
- **手動確認シナリオ**（`npm run dev` ＋ `./mvnw spring-boot:run` で起動、もしくは既存の起動手順に従う）:
  1. サイドバーを開き、「新規問い合わせ」「ダッシュボード」「システムモニタリング」の順でボタンが並んでいることを目視確認する（AC-1）。
  2. チャット画面で「ダッシュボード」ボタンを押し、新規会話が作成・アクティブ化され、ユーザー吹き出しに「ダッシュボードを表示して」、続けてアシスタント吹き出しに KPI カード（`stat_cards`）・ドーナツチャート（`donut_chart`）・トレンドチャート（`trend_chart`）の3コンポーネントが表示されることを確認する（AC-2）。
  3. 既存の会話（メッセージが1件以上ある会話）を選択した状態で「ダッシュボード」ボタンを押し、選択していた会話にはメッセージが追加されず、別の新規会話（会話一覧の先頭）に手順2の内容が送信されていることを確認する（AC-3）。
  4. サイドバーの「システムモニタリング」ボタンを押してモニタリング画面に切り替えたあと、「ダッシュボード」ボタンを押し、チャット画面に切り替わったうえで手順2と同じ応答が表示されることを確認する（AC-4）。
  5. 「ダッシュボード」ボタンを押した直後（応答待ち中、送信中インジケータ等で `isLoading` が `true` であることを目視確認できる間）に連打し、会話が1つしか作成されないこと、およびボタンの見た目が無効状態（`disabled:opacity-50` の薄い表示）になっていることを確認する（AC-5）。
  6. チャット画面表示中・モニタリング画面表示中の両方で「ダッシュボード」ボタンの見た目を確認し、`activeScreen` の値によらず常に「新規問い合わせ」ボタンと同じ配色（色反転やアクティブハイライトが付かない）であることを確認する（AC-6）。
  7. `cd frontend && npx vue-tsc -b` と `cd frontend && npm run build` を実行し、いずれも成功することを確認する（AC-7）。
  8. 既存の「新規問い合わせ」ボタン、「定型レポート」各ボタン、「システムモニタリング」ボタン、会話履歴の選択・リネーム（鉛筆アイコン）・削除（✕アイコン）を一通り操作し、本変更前と挙動・見た目が変わっていないことを確認する（AC-8）。

## AC mapping

| AC | 内容（要約） | 実装ステップ | 検証（テスト戦略） |
|---|---|---|---|
| AC-1 | 「新規問い合わせ」「ダッシュボード」「システムモニタリング」の順で表示 | ステップ2（テンプレートへのボタン挿入位置） | 手動確認シナリオ1 |
| AC-2 | 押下で新規会話作成・固定文言送信・3コンポーネント応答表示 | ステップ1（`sendDashboardPrompt`）, ステップ2（`handleDashboard`） | 手動確認シナリオ2 |
| AC-3 | 進行中の会話を汚さず別の新規会話に送信 | ステップ1（`createConversation()` を `sendMessage()` より先に同期実行） | 手動確認シナリオ3 |
| AC-4 | モニタリング画面表示中に押すとチャット画面に切替＋応答表示 | ステップ2（`monitoringStore.showChat()` を先に呼ぶ順序） | 手動確認シナリオ4 |
| AC-5 | `isLoading` 中は無効化、連打しても会話は1つ | ステップ2（`:disabled="store.isLoading"`） | 手動確認シナリオ5 |
| AC-6 | `activeScreen` 連動ハイライトなし、常に「新規問い合わせ」と同じ見た目 | ステップ2（`:class` 三項式を付与せず固定クラスのみ使用） | 手動確認シナリオ6 |
| AC-7 | `vue-tsc -b` / `npm run build` 成功 | ステップ1, 2 | 自動テスト（各ステップ後および最終、手動確認シナリオ7） |
| AC-8 | 既存ボタン・会話履歴機能の挙動が変わらない | ステップ1, 2（既存アクション・既存ボタンのコード・クラスを変更しない） | 手動確認シナリオ8 |
