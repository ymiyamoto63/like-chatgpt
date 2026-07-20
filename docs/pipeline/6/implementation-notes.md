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

## ステップ4: frontend型定義追加（FR-1, FR-6）

### 変更ファイル

- 変更 `frontend/src/types/chat.ts`
  - `StatCard`（`label: string; value: string; delta?: string`）、`StatCardsComponent`（`type: 'stat_cards'; cards: StatCard[]`）、`DonutChartComponent`（`type: 'donut_chart'; title: string; labels: string[]; values: number[]`）を、既存`FaqListComponent`直後に既存インタフェースと同じ1行フィールド列挙のスタイルで追加。
  - `UiComponentSpec`判別共用体の末尾に`StatCardsComponent`/`DonutChartComponent`を追加。

### 検証

- `cd frontend && npx vue-tsc -b` グリーン。

## ステップ5: frontend検証ロジック追加（FR-6）

### 変更ファイル

- 変更 `frontend/src/api/chatApi.ts`
  - import追加: `DonutChartComponent`/`StatCard`/`StatCardsComponent`。
  - `isValidStatCard`を新設: `label`/`value`が`string`であること、`delta`は`undefined`・`null`・`string`のいずれかであることを検証。
  - `isValidStatCardsComponent`を新設: `cards`が1件以上の配列かつ全要素が`isValidStatCard`を満たすことを検証。
  - `isValidDonutChartComponent`を新設: 既存`isValidBarChartComponent`と同型（`title`がstring、`labels`が1件以上のstring配列、`values`が`labels`と同数のfinite number配列）。
  - `isValidUiComponentSpec`の`type`判別分岐に`'stat_cards'`/`'donut_chart'`を既存5分岐の末尾に追加。既存の「不正なら応答全体をエラーフォールバック（部分成功なし）」という方針・構造は変更していない。

### 検証

- `cd frontend && npx vue-tsc -b` グリーン。

## ステップ6: frontend描画コンポーネント新規作成（FR-7）

### 変更ファイル

- 新規 `frontend/src/components/dynamic-ui/StatCardsView.vue`
  - `props.spec.cards`を`grid grid-cols-2 sm:grid-cols-4 gap-2`でグリッド表示。各カードは`label`（zinc-500系の小見出し）・`value`（zinc-900系の強調表示）・`delta`行（該当時のみ）の3段構成で、既存`BarChartView`/`TableView`と同じzinc/violet基調の配色トーンに合わせた。
  - `deltaTrend(delta: string | undefined | null): 'up' | 'down' | 'flat' | null`関数を実装: `undefined`/`null` → `null`、先頭`'+'` → `'up'`、先頭`'-'` → `'down'`、それ以外 → `'flat'`。`computed`の`cards`で各カードに`trend`を付与するかたちで利用（設計書の「computedで用意する」という記述を、`deltaTrend`自体は純粋関数、それを用いる導出データを`computed`にする形で解釈した）。
  - `trend === 'up'`は`text-emerald-600 dark:text-emerald-400`＋`▲`、`'down'`は`text-rose-600 dark:text-rose-400`＋`▼`（既存`TopologyDiagram.vue`のemerald=正常/rose=危険という配色慣例を踏襲）、`'flat'`は`text-zinc-500 dark:text-zinc-400`で矢印なし、`trend`が`null`の場合は`v-if="card.trend"`によりdelta行自体を描画しない。`v-html`は不使用。長い`label`/`value`/`delta`文字列は`truncate`ではみ出しを防止。
- 新規 `frontend/src/components/dynamic-ui/DonutChartView.vue`
  - SVG `viewBox="0 0 100 100"`、中心`(50,50)`、半径`R=40`（`RADIUS`定数）、`stroke-width=16`（`STROKE_WIDTH`定数）。下地の薄グレーリング（`stroke-zinc-200 dark:stroke-zinc-700`）に続けて、`labels`の数だけ`<circle>`を重ね描画。`<g transform="rotate(-90 50 50)">`でグループ全体を回転させ12時起点にした。
  - `total = values.reduce((sum, value) => sum + value, 0)`、`ratio = total > 0 ? value / total : 0`、`segmentLength = ratio * circumference`（`circumference = 2 * Math.PI * RADIUS`）、`stroke-dasharray="${segmentLength} ${circumference - segmentLength}"`、`stroke-dashoffset={-cumulativeLength}`を`computed`の`segments`で算出（三角関数を経由しないため`total === 0`でも`NaN`座標は発生せず、下地リングのみが表示される＝AC-8のゼロ除算対策）。
  - 色は`PALETTE`（`violet`/`emerald`/`orange`/`sky`/`rose`/`amber`の6色、各`stroke-*-500 dark:stroke-*-400`とスウォッチ用`bg-*-500 dark:bg-*-400`の完全なクラス文字列リテラルをペアで保持）を`index % PALETTE.length`で循環使用。動的な文字列結合でクラス名を生成すると Tailwind の静的解析に拾われない懸念があるため、`TopologyDiagram.vue`の`LEVEL_STROKE_CLASS`と同様、配列要素として完全なクラス文字列を列挙する方式にした（設計書に直接の指定はないが、既存コンポーネントの慣例に合わせた実装判断）。
  - 凡例はSVG横（`sm:flex-row`）または縦積み（モバイル幅）で、色スウォッチ（`<span class="rounded-full">`）＋`label`（`truncate`ではみ出し防止）＋比率（`Math.round(ratio * 100)}%`）を表示。パレット循環時の視認性低下・凡例はみ出し対策はいずれも設計書「Risks / edge cases」節の記述どおり、要件外の動的パレット生成等は行わずTailwindクラスでの最低限の防止に留めた。

### 検証

- `cd frontend && npx vue-tsc -b` グリーン。

## ステップ7: `MessageBubble.vue`への描画分岐統合（FR-7）

### 変更ファイル

- 変更 `frontend/src/components/chat/MessageBubble.vue`
  - `StatCardsComponent`/`DonutChartComponent`の型importと`StatCardsView`/`DonutChartView`のコンポーネントimportを追加。
  - `isStatCardsComponent`/`isDonutChartComponent`を既存4関数（`isTableComponent`等）と同じ形式（`component.type === '...'`の型ガード）で`isFaqListComponent`の直後に追加。
  - テンプレートの`v-if`/`v-else-if`チェーンに`<StatCardsView v-else-if="isStatCardsComponent(component)" :spec="component" />`・`<DonutChartView v-else-if="isDonutChartComponent(component)" :spec="component" />`を、設計書の推奨どおり`TrendChartView`分岐の直後（`ChoicesView`分岐の直前）に挿入。既存5分岐（`table`/`bar_chart`/`trend_chart`/`choices`/`faq_list`）の並び順・記述は変更していない。

### 検証

- `cd frontend && npx vue-tsc -b` グリーン。
- `cd frontend && npm run build` グリーン（`vue-tsc -b && vite build`、`dist/`出力まで成功。AC-9）。

## 逸脱・スコープ外の扱い

- 設計書どおり、backend側のステップ1〜3、frontend側のステップ4〜7（型定義・検証ロジック・描画コンポーネント・`MessageBubble.vue`統合）を実施した。手動確認（ステップ8）、`docs/api/chat-response-schema.md`更新（ステップ9）は本タスクのスコープ外として未着手。
- 設計からの逸脱: ステップ6のドーナットチャート配色パレットの具体的な6色（`violet`/`emerald`/`orange`/`sky`/`rose`/`amber`）と、StatCardsViewのdelta色（emerald=up/rose=down）は設計書に色名の直接指定がなかったため、既存`TopologyDiagram.vue`・`TrendChartView.vue`の配色慣例に合わせて実装フェーズで決定した。それ以外（グリッドレイアウト・SVG構造・ゼロ除算対策・凡例内容・判定順序）は設計書の記述どおり。
- フロントエンドに自動テストランナーが未導入のため、本ステップの検証は`npx vue-tsc -b`と`npm run build`のグリーン維持で完結させた（`pipeline-config.md`の`testing`節どおり）。手動確認シナリオ（AC-1, 2, 3, 5, 6, 7, 8, 11〜15）はステップ8のスコープとして未実施。

## ステップ9: `docs/api/chat-response-schema.md` 更新（FR-8, AC-11）

### 変更ファイル

- 変更 `docs/api/chat-response-schema.md`
  - 「`UiComponent`（判別共用体）」節冒頭の対応type列挙を5種類→7種類（`stat_cards`・`donut_chart`追加）に更新。
  - `FaqListComponent`節の後に`StatCardsComponent`（`type`/`cards`のフィールド定義表＋ネスト`StatCard`（`label`/`value`/`delta`）のフィールド定義表＋符号判定仕様（`+`始まり=緑・上矢印／`-`始まり=赤・下矢印／それ以外=プレーン／省略=プレーン）＋サンプルJSON）と`DonutChartComponent`（`type`/`title`/`labels`/`values`のフィールド定義表＋サンプルJSON）の節を新設（設計書「API契約」節と一字一句同一の内容）。
  - 「サンプルJSON（4シナリオ）」を「サンプルJSON（5シナリオ）」に変更し、シナリオD（非マッチ）の直前に「シナリオG: ミニダッシュボード応答」節を新設。トリガー説明（「サマリー」または「ダッシュボード」を含む。新規問い合わせフロー・アラートフロー非該当時）と全体サンプルJSON、モックデータの整合性に関する注記を記載。
  - シナリオD節末尾の「判定順序」段落を、「カテゴリ」または「種別」判定の後・シナリオD判定の前に「含まなければ「サマリー」または「ダッシュボード」を含むか → シナリオG。」を挿入する形に更新（設計書の指定どおり、シナリオF側の「判定順序への影響」段落には手を加えていない）。
  - 「フロントエンドの安全なフォールバック・将来拡張について」節の必須フィールド列挙に`stat_cards`（`cards`、1件以上、各要素`label`/`value`必須・`delta`任意）と`donut_chart`（`title`/`labels`/`values`、`bar_chart`と同条件）を追記し、「5値以外の場合」を「7値以外の場合」に更新。

### サンプルJSONの実レスポンス確認（AC-11）

- port 8080は既存の`java`プロセス（デバッガアタッチ待ち`suspend=y`のプロセス、pid 364530、他フェーズ/セッションが起動したものと推測）が保持しており、`curl`で疎通確認したところ「サマリー」を含むメッセージに対してもフォールバック応答（ダッシュボード機能を含まない旧ビルド相当）を返したため、この既存プロセスは検証に使用せず、そのまま停止せずに残した（呼び出し元への報告事項）。
- 代わりに`cd backend && ./mvnw -q spring-boot:run -Dspring-boot.run.jvmArguments="-Dserver.port=8099"`で自分専用のインスタンスをバックグラウンド起動し、`curl -s -X POST http://localhost:8099/api/chat -H 'Content-Type: application/json' -d '{"message":"今月のサマリーを見せて"}'`および`{"message":"ダッシュボードを表示して"}`の2通りで実レスポンスを取得。両者とも同一の`components`配列（`stat_cards`→`donut_chart`→`trend_chart`の順、`stat_cards`の内容・`donut_chart`の`labels`/`values`）であることを確認し、`trend_chart`の日付ラベルを除く全フィールドをスキーマ文書のシナリオGサンプルに一字一句反映した。確認後、起動した自分のプロセス（port 8099）は`kill`で停止済み。port 8080の既存プロセスには一切手を加えていない。
- `trend_chart`の`labels`は実行時点の日付に依存するため、実際の`curl`結果は`["7/7", ..., "7/20"]`（確認実施日2026-07-20基準）だったが、既存シナリオC節も過去確認時点の日付（`["7/2", ..., "7/15"]`）のまま据え置かれている既存ドキュメントの慣例に合わせ、シナリオGのサンプルも設計書「API契約」節に記載の`["7/2", ..., "7/15"]`をそのまま採用した（日付以外の`reply`・`stat_cards`・`donut_chart`・`trend_chart`の`title`/`values`/`average`は`curl`結果と完全一致）。

### 検証

- 上記`curl`確認（実レスポンスとの一致確認）。
- ドキュメントのみの変更のため、backend/frontendのビルド・テストコマンドは実行不要（変更なし）。
