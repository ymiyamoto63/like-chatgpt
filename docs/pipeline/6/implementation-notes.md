# 実装ノート

対象設計書: `docs/pipeline/6/design.md`

## ステップ1: backendドメインrecord追加（FR-1, FR-2, FR-3）

### 変更ファイル

- 新規 `backend/src/main/java/com/example/chatbackend/domain/chat/component/StatCard.java`
  - `label`/`value`/`delta` のネスト値オブジェクトrecord。クラスに `@JsonInclude(JsonInclude.Include.NON_NULL)` を付与し、`delta` が `null` の場合はJSONフィールド自体を省略する（設計書「API契約」節どおり）。
- 新規 `backend/src/main/java/com/example/chatbackend/domain/chat/component/StatCardsComponent.java`
  - `cards: List<StatCard>` を持つ `UiComponent` 実装record。既存 `TableComponent`/`BarChartComponent` と同じ1行recordのスタイルに合わせた。
- 新規 `backend/src/main/java/com/example/chatbackend/domain/chat/component/DonutChartComponent.java`
  - `title`/`labels: List<String>`/`values: List<Double>` を持つ `UiComponent` 実装record。`BarChartComponent` と全く同じフィールド構成。
- 変更 `backend/src/main/java/com/example/chatbackend/domain/chat/component/UiComponent.java`
  - `@JsonSubTypes` に `stat_cards`（`StatCardsComponent`）/`donut_chart`（`DonutChartComponent`）を末尾追加。既存5件の記載順・内容は変更なし。
  - `permits` リストにも同2型を末尾追加。

### 検証

- `cd backend && ./mvnw -q compile` グリーン。

## ステップ2: `KeywordMatchReplyGenerationAdapter` にダッシュボードシナリオ追加（FR-4, FR-5）

### 変更ファイル

- 変更 `backend/src/main/java/com/example/chatbackend/adapter/out/reply/KeywordMatchReplyGenerationAdapter.java`
  - import追加: `DonutChartComponent`/`StatCard`/`StatCardsComponent`。
  - フィールド定数群（`DAILY_KEYWORD` 直後）に `SUMMARY_KEYWORD = "サマリー"`・`DASHBOARD_KEYWORD = "ダッシュボード"` を追加。両語が既存キーワード・新規問い合わせ/アラートフロー定型文と重複しないことをgrep確認済みである旨、および将来これらの語を含む定型文が追加された場合は再度の衝突確認が必要である旨のコメントを付した。
  - `dailyTrendScenario()` から `TrendChartComponent` 構築ロジック（日付ラベル生成・平均計算・丸め処理）を `private TrendChartComponent buildDailyTrendChart()` として抽出。`dailyTrendScenario()` は抽出後のメソッドを呼び出し、reply文言生成時の平均値は `trendChart.average()`（丸め後の値）を参照するよう変更したが、値・ロジック・定数は一切変更していない（既存 `chatReturnsDailyTrendScenarioForDailyKeyword` テストが無変更でパスすることで確認済み）。
  - `dashboardScenario()` を新設。設計書「API契約」節のモックデータ（`stat_cards` 4枚: `"+12%"`/`"-8%"`/`"±0pt"`/`delta`省略、`donut_chart`: 請求/技術/アカウント/その他=58/44/25/15、`trend_chart`: `buildDailyTrendChart()` 流用）を `List.of(statCards, donutChart, trendChart)` の順で格納し、reply文言は設計書どおり `"今月のサマリーです。主要な指標とカテゴリ内訳、日別の推移をまとめました。"` とした。
  - `generateReply()` の `categoryScenario()` 分岐直後・`fallbackScenario()` 直前に `if (message.contains(SUMMARY_KEYWORD) || message.contains(DASHBOARD_KEYWORD)) { return dashboardScenario(); }` を追加。

### 検証

- `cd backend && ./mvnw -q compile` グリーン。

## ステップ3: backendテスト追加・既存テスト回帰確認（AC-1, AC-2, AC-4, AC-10）

### 変更ファイル

- 変更 `backend/src/test/java/com/example/chatbackend/adapter/in/web/ChatControllerTest.java`
  - `chatReturnsDashboardScenarioForSummaryKeyword`（メッセージ「今月のサマリーを見せて」）を追加。
  - `chatReturnsDashboardScenarioForDashboardKeyword`（メッセージ「ダッシュボードを表示して」）を追加。
  - 両テストとも、`components[0].type == "stat_cards"`、`cards[0].delta == "+12%"`（存在すること）、`cards[3].delta` が `doesNotExist()`（省略されていること）、`components[1].type == "donut_chart"`、`labels`/`values` がともに4件、`components[2].type == "trend_chart"` を検証。
  - 既存の12テストは無変更。

### 検証

- `cd backend && ./mvnw test` 実行結果: `Tests run: 55, Failures: 0, Errors: 0, Skipped: 0` で全件グリーン。
  - `ChatControllerTest`: 14テスト（既存12＋新規2）全パス。
  - `KeywordMatchReplyGenerationAdapterTest`（既存, 22テスト）・`ArchitectureTest`（既存, 4テスト）を含む全テストクラスが回帰なし。

## 逸脱・スコープ外の扱い

- 設計書どおり、backend側のステップ1〜3のみを実施した。frontend側（ステップ4〜7）・手動確認（ステップ8）・`docs/api/chat-response-schema.md` 更新（ステップ9）は本タスクのスコープ外として未着手。
- 設計からの逸脱はなし。`dailyTrendScenario()` の抽出は設計書「Risks / edge cases」節の指示（ロジック・定数・丸め処理を一切変更しない）を厳守し、既存テストの回帰なしで確認済み。
