# チャット応答への「ダッシュボード応答」追加 — 実装設計書

対象要件: `docs/pipeline/6/requirements.md`

## Approach

`StatCardsComponent`（KPIカード列。カード配列 `cards` の各要素は `label`/`value` 必須＋`delta` 任意）と `DonutChartComponent`（`BarChartComponent` と同形状の `title`/`labels`/`values`）という2つの `UiComponent` 実装 record を `domain/chat/component/` に新設し、`UiComponent` の `@JsonSubTypes`/`permits` に追加する。`delta` は新設の `StatCard`（非 `UiComponent` の値オブジェクト record）に `@JsonInclude(Include.NON_NULL)` を付け、`null` の場合はJSONフィールド自体を省略する。`KeywordMatchReplyGenerationAdapter` に「サマリー」「ダッシュボード」キーワード分岐を、既存判定順序（カテゴリ/種別判定の直後・フォールバック直前）に追加し、要約テキスト＋`stat_cards`＋`donut_chart`＋`trend_chart`（`trend_chart` は `dailyTrendScenario` から抽出した `buildDailyTrendChart()` を共有呼び出し）から成る `dashboardScenario()` を新設する。フロントエンドは `types/chat.ts` に型追加、`chatApi.ts` に検証関数追加（`delta` はフィールド欠落・`null`・`string` のいずれも許容）、`StatCardsView.vue`（グリッド表示＋delta矢印/色分け）と `DonutChartView.vue`（`stroke-dasharray`によるSVGドーナツ、外部ライブラリ不使用）を新規作成し、`MessageBubble.vue` の判別描画チェーンに組み込む。既存5種のスキーマ・挙動・判定順序・ヘキサゴナル構成（`ReplyGenerationPort` 実装アダプタとしての位置づけ）は変更しない。

## Alternatives considered

- **ドーナツチャートの弧の描画方式**: SVG `<circle>` の `stroke-dasharray`/`stroke-dashoffset` によるリング描画（本設計採用）と、`<path>` の `A`（楕円弧）コマンドで扇形を描く方式の2案を検討した。`<path>` 方式は開始角・終了角から始点・終点座標を三角関数で計算し `large-arc-flag` を判定する必要があり実装・レビューコストが高い。`stroke-dasharray` 方式は「区間の弧長 = 比率 × 円周」の単純な比例計算のみで済み、合計0のケースも「全セグメント長0」で自然にゼロ除算を回避しやすい（三角関数を経由しないため `NaN` 座標が発生する経路がそもそもない）。`BarChartView.vue`/`TrendChartView.vue` が採用している「比率→パーセンテージ」という単純な計算方針とも一貫するため、`stroke-dasharray` 方式を採用する。
- **`delta` のJSON表現（`@JsonInclude(NON_NULL)` でフィールド省略 vs 明示的に`null`を出力）**: 制約節でどちらでもよいとされているが、`@JsonInclude(Include.NON_NULL)` を採用する。フィールド省略の方が「既存`TableComponent`等が全フィールド必須」という既存契約からの逸脱が視覚的に分かりやすく（欠落＝任意フィールド未設定、という素直な対応）、AC-4のテストでも「delta フィールードが存在しない」ことをそのまま検証できる。フロントエンドの検証（FR-6）はいずれの表現でも受理する設計にするため、将来的にこの方針を変えてもフロント側の追随は不要。

## API契約

`POST /api/chat` のレスポンス（`ChatResponse`）の形は変更しない（`reply: string`, `components: UiComponent[]`）。`UiComponent` 判別共用体に以下2種を追加する（既存5種のフィールド・シリアライズ結果は無変更）。

### `StatCardsComponent`（`"type": "stat_cards"`）

| フィールド | 型 | 必須 | 説明・制約 |
|---|---|---|---|
| `type` | `"stat_cards"` | ○ | 固定リテラル。自動付与される判別子 |
| `cards` | `StatCard[]` | ○ | KPIカードの配列。要素数は1以上 |

`StatCard`（ネストされたオブジェクト。`UiComponent` 自体ではない値オブジェクト）:

| フィールド | 型 | 必須 | 説明・制約 |
|---|---|---|---|
| `label` | `string` | ○ | カード見出し |
| `value` | `string` | ○ | 表示値。数値も表示用文字列として格納する（既存`TableComponent`の`rows`と同様の方針） |
| `delta` | `string` | 任意 | 前月比等の増減を表す表示用文字列（例: `"+12%"`）。値が`null`の場合はバックエンドで `@JsonInclude(Include.NON_NULL)` によりJSON上フィールド自体を省略する。フロントエンドの検証は「フィールドが存在しない」「値が`string`」のいずれも有効とみなす |

```json
{
  "type": "stat_cards",
  "cards": [
    { "label": "新規問い合わせ件数", "value": "142件", "delta": "+12%" },
    { "label": "平均初回応答時間", "value": "3.2時間", "delta": "-8%" },
    { "label": "顧客満足度", "value": "4.3/5.0", "delta": "±0pt" },
    { "label": "解決率", "value": "92%" }
  ]
}
```

上記サンプルは1応答内に「増加」「減少」「増減なし（`+`/`-`で始まらない文字列）」「`delta`省略」の4パターンを混在させたもので、AC-12〜15を1レスポンスで検証できる構成にしてある。

### `DonutChartComponent`（`"type": "donut_chart"`）

`BarChartComponent` と同じフィールド構成。`labels`/`values`の比率からフロントエンドがドーナツの弧を計算する。

| フィールド | 型 | 必須 | 説明・制約 |
|---|---|---|---|
| `type` | `"donut_chart"` | ○ | 固定リテラル。自動付与される判別子 |
| `title` | `string` | ○ | グラフ見出し |
| `labels` | `string[]` | ○ | 区分ラベル。要素数は1以上 |
| `values` | `number[]` | ○ | `labels`と同数。JSON number（`List<Double>`保持のため小数点付きで出力される） |

```json
{
  "type": "donut_chart",
  "title": "カテゴリ別内訳",
  "labels": ["請求", "技術", "アカウント", "その他"],
  "values": [58.0, 44.0, 25.0, 15.0]
}
```

### ダッシュボード応答（シナリオG）全体サンプル

トリガー: メッセージ文字列に「サマリー」または「ダッシュボード」を含む（新規問い合わせフロー・アラートフローいずれにも一致しない場合に限る）。

```json
{
  "reply": "今月のサマリーです。主要な指標とカテゴリ内訳、日別の推移をまとめました。",
  "components": [
    {
      "type": "stat_cards",
      "cards": [
        { "label": "新規問い合わせ件数", "value": "142件", "delta": "+12%" },
        { "label": "平均初回応答時間", "value": "3.2時間", "delta": "-8%" },
        { "label": "顧客満足度", "value": "4.3/5.0", "delta": "±0pt" },
        { "label": "解決率", "value": "92%" }
      ]
    },
    {
      "type": "donut_chart",
      "title": "カテゴリ別内訳",
      "labels": ["請求", "技術", "アカウント", "その他"],
      "values": [58.0, 44.0, 25.0, 15.0]
    },
    {
      "type": "trend_chart",
      "title": "日別問い合わせ件数（直近14日間）",
      "labels": ["7/2", "7/3", "7/4", "7/5", "7/6", "7/7", "7/8", "7/9", "7/10", "7/11", "7/12", "7/13", "7/14", "7/15"],
      "values": [8.0, 5.0, 3.0, 9.0, 6.0, 4.0, 7.0, 10.0, 6.0, 5.0, 8.0, 3.0, 6.0, 9.0],
      "average": 6.4
    }
  ]
}
```

`donut_chart` の値合計（58+44+25+15=142）は `stat_cards` の1枚目「新規問い合わせ件数: 142件」と一致させ、モックデータとしての整合性を持たせている（制約節「整合性のある架空データであれば足りる」を踏襲）。`trend_chart` は既存`dailyTrendScenario`のデータをそのまま流用するため直近14日合計（89件）とは一致しないが、対象期間が異なる指標（月次 vs 直近14日）であるため矛盾ではない。

### `docs/api/chat-response-schema.md` の更新方針（FR-8）

- 「`UiComponent`（判別共用体）」節の冒頭文「現時点でサポートするのは...5種類のみ」を7種類に更新し、`StatCardsComponent`・`DonutChartComponent`の節（フィールド定義表＋サンプルJSON、上記と同一内容）を`FaqListComponent`節の後に追記する。
- 「サンプルJSON（4シナリオ）」の見出しを「サンプルJSON（5シナリオ）」に変え、シナリオD（非マッチ）の直前に「シナリオG: ミニダッシュボード応答」節を新設し、上記トリガー説明＋全体サンプルJSONを掲載する（既存シナリオA〜Fの記載パターンに倣う）。
- シナリオD節末尾の「判定順序」段落を「...含まなければ「カテゴリ」または「種別」を含むか → シナリオB。含まなければ「サマリー」または「ダッシュボード」を含むか → シナリオG。いずれも含まなければ → シナリオD。」に更新する。
- 「フロントエンドの安全なフォールバック・将来拡張について」節の「各要素の`type`ごとの必須フィールド」列挙に `stat_cards`（`cards`、1件以上、各要素`label`/`value`必須・`delta`任意）と `donut_chart`（`title`/`labels`/`values`、`bar_chart`と同条件）を追記し、「5値以外の場合」を「7値以外の場合」に更新する。
- 本ドキュメントのサンプルJSONは実際に`ChatControllerTest`が検証するレスポンスおよび起動後の`curl`結果と一致させる（既存方針の踏襲、AC-11）。

## Files affected

### backend（新規）
- `backend/src/main/java/com/example/chatbackend/domain/chat/component/StatCard.java` — `label`/`value`/`delta`（`@JsonInclude(Include.NON_NULL)`）のネスト値オブジェクト record
- `backend/src/main/java/com/example/chatbackend/domain/chat/component/StatCardsComponent.java` — `cards: List<StatCard>` の `UiComponent` record
- `backend/src/main/java/com/example/chatbackend/domain/chat/component/DonutChartComponent.java` — `title`/`labels`/`values` の `UiComponent` record（`BarChartComponent`と同形状）

### backend（変更）
- `backend/src/main/java/com/example/chatbackend/domain/chat/component/UiComponent.java` — `@JsonSubTypes`に`stat_cards`/`donut_chart`追加、`permits`に2型追加
- `backend/src/main/java/com/example/chatbackend/adapter/out/reply/KeywordMatchReplyGenerationAdapter.java` — `SUMMARY_KEYWORD`/`DASHBOARD_KEYWORD`定数追加、`dailyTrendScenario()`から`buildDailyTrendChart()`抽出、`dashboardScenario()`新設、`generateReply()`のカテゴリ/種別判定直後・フォールバック直前に分岐追加

### backend（テスト、変更）
- `backend/src/test/java/com/example/chatbackend/adapter/in/web/ChatControllerTest.java` — ダッシュボードキーワード（「サマリー」「ダッシュボード」双方）の統合テスト2件追加

### frontend（変更）
- `frontend/src/types/chat.ts` — `StatCard`/`StatCardsComponent`/`DonutChartComponent`型追加、`UiComponentSpec`共用体に組み込み
- `frontend/src/api/chatApi.ts` — `isValidStatCard`/`isValidStatCardsComponent`/`isValidDonutChartComponent`追加、`isValidUiComponentSpec`に分岐追加
- `frontend/src/components/chat/MessageBubble.vue` — 型ガード2関数追加、テンプレートに`v-else-if`2分岐追加、コンポーネントimport追加

### frontend（新規）
- `frontend/src/components/dynamic-ui/StatCardsView.vue` — KPIカードのグリッド表示。`delta`の符号判定（矢印・色）
- `frontend/src/components/dynamic-ui/DonutChartView.vue` — 自前SVGドーナツチャート（`stroke-dasharray`方式）

### ドキュメント（変更）
- `docs/api/chat-response-schema.md` — 上記「`docs/api/chat-response-schema.md`の更新方針」節のとおり追記

### 影響なし（確認済み）
- `frontend/src/constants/monitoringAlert.ts` / `frontend/src/constants/reportButtons.ts` — grepで「サマリー」「ダッシュボード」の文字列が既存キーワード・定型文と重複しないことを確認済み。両ファイルとも変更不要
- `backend/src/test/java/com/example/chatbackend/architecture/ArchitectureTest.java` — 新規record・分岐追加は既存パッケージ構成内（`domain.chat.component`/`adapter.out.reply`）にとどまり、4ルールいずれにも抵触しないため変更不要

## Implementation steps

各ステップ完了時点で `./mvnw -q compile`（backend側ステップ）または `npx vue-tsc -b`（frontend側ステップ）が通ることを確認しながら進める。

1. **backendドメインrecord追加（FR-1, FR-2, FR-3）**
   - `StatCard.java`・`StatCardsComponent.java`・`DonutChartComponent.java`を新規作成。
   - `UiComponent.java`の`@JsonSubTypes`/`permits`に2型を追加。
   - 確認: `cd backend && ./mvnw -q compile`。既存の`@JsonSubTypes`5件・`permits`5件の記載順・内容は変更しない（末尾に追記）。

2. **`KeywordMatchReplyGenerationAdapter`にダッシュボードシナリオ追加（FR-4, FR-5）**
   - `SUMMARY_KEYWORD = "サマリー"`、`DASHBOARD_KEYWORD = "ダッシュボード"`定数を追加（既存の`ASSIGNEE_KEYWORD`等と同じ並びのフィールド群に追記。将来これらの語を含む定型文が新規問い合わせ/アラートフローに追加された場合は再度の衝突確認が必要である旨のコメントを付す）。
   - `dailyTrendScenario()`から`TrendChartComponent`構築ロジック（日付ラベル生成・平均計算）を`private TrendChartComponent buildDailyTrendChart()`として抽出し、`dailyTrendScenario()`はこれを呼び出す形にリファクタリングする（reply文言・ロジック・値は一切変えない）。
   - `dashboardScenario()`を新設: `StatCardsComponent`（本設計「API契約」節のモックデータ）＋`DonutChartComponent`（同）＋`buildDailyTrendChart()`の3コンポーネントを`List.of(statCards, donutChart, trendChart)`の順で`components`に格納し、`reply`は`"今月のサマリーです。主要な指標とカテゴリ内訳、日別の推移をまとめました。"`とする。
   - `generateReply()`の`if (message.contains(CATEGORY_KEYWORD) || message.contains(TYPE_KEYWORD)) { return categoryScenario(); }`の直後・`return fallbackScenario();`の直前に`if (message.contains(SUMMARY_KEYWORD) || message.contains(DASHBOARD_KEYWORD)) { return dashboardScenario(); }`を追加する。
   - 確認: `cd backend && ./mvnw -q compile`。

3. **backendテスト追加・既存テスト回帰確認（AC-1, AC-2, AC-4, AC-10）**
   - `ChatControllerTest`に`chatReturnsDashboardScenarioForSummaryKeyword`（メッセージ「今月のサマリーを見せて」）と`chatReturnsDashboardScenarioForDashboardKeyword`（メッセージ「ダッシュボードを表示して」）の2テストを追加。両テストとも`components[0].type`が`stat_cards`、`components[1].type`が`donut_chart`、`components[2].type`が`trend_chart`であること、`stat_cards`の`cards[0].delta`が`"+12%"`（存在すること）、`cards[3]`に`delta`キー自体が存在しないこと（`jsonPath("$.components[0].cards[3].delta").doesNotExist()`）、`donut_chart`の`labels`/`values`件数一致を検証する。
   - 確認: `cd backend && ./mvnw test`（全件成功。既存9テストの回帰なし=AC-3, AC-5, AC-10）。

4. **frontend型定義追加（FR-1, FR-6）**
   - `types/chat.ts`に`StatCard`（`label: string; value: string; delta?: string`）・`StatCardsComponent`（`type: 'stat_cards'; cards: StatCard[]`）・`DonutChartComponent`（`type: 'donut_chart'; title: string; labels: string[]; values: number[]`）を追加し、`UiComponentSpec`共用体に組み込む。
   - 確認: `cd frontend && npx vue-tsc -b`。

5. **frontend検証ロジック追加（FR-6）**
   - `chatApi.ts`に`isValidStatCard`（`label`/`value`が`string`、`delta`は`undefined`または`null`または`string`のいずれかであることを許容）・`isValidStatCardsComponent`（`cards`が1件以上の配列かつ全要素`isValidStatCard`）・`isValidDonutChartComponent`（`isValidBarChartComponent`と同型の検証）を追加し、`isValidUiComponentSpec`に`'stat_cards'`/`'donut_chart'`の分岐を追加する。
   - 確認: `cd frontend && npx vue-tsc -b`。

6. **frontend描画コンポーネント新規作成（FR-7）**
   - `StatCardsView.vue`: `props.spec.cards`をグリッド表示（Tailwind `grid grid-cols-2 sm:grid-cols-4 gap-2`等、既存`BarChartView`/`TableView`の配色トーンに合わせる）。各カードの`delta`について、先頭文字`'+'` → 緑色＋`▲`、`'-'` → 赤色＋`▼`、それ以外の非空文字列 → 色・矢印なしのプレーン表示、`delta`が`undefined`/`null` → 矢印・色・文字列いずれも非表示、という判定関数`deltaTrend(delta: string | undefined | null): 'up' | 'down' | 'flat' | null`をcomputedで用意する。`v-html`は使用しない。
   - `DonutChartView.vue`: SVG `viewBox="0 0 100 100"`、中心`(50,50)`、半径`R=40`、線幅`stroke-width=16`の`<circle>`を土台（薄いグレーの下地リング）として描画し、`total = values.reduce((a,b)=>a+b,0)`のもと各`label`ごとに`ratio = total > 0 ? value / total : 0`、`segmentLength = ratio * circumference`（`circumference = 2 * Math.PI * R`）を計算、`stroke-dasharray="${segmentLength} ${circumference - segmentLength}"`・`stroke-dashoffset={-cumulativeLength}`（`cumulativeLength`は直前までのセグメント長の累積）を持つ`<circle>`を`labels`の数だけ重ね描画し、円全体を`transform="rotate(-90 50 50)"`で12時方向起点にする。`total === 0`の場合は全セグメントの`ratio`が0になり`segmentLength`も0となるため、色付きセグメントは描画されず下地リングのみが表示される（三角関数を使わないため`NaN`座標が発生する経路がなく、AC-8のゼロ除算対策を満たす）。色は6色程度の固定パレットを`index % palette.length`で循環使用する。凡例として`label`と`比率(%)`または`value`をリスト表示する。
   - 確認: `cd frontend && npx vue-tsc -b`。

7. **`MessageBubble.vue`への描画分岐統合（FR-7）**
   - `isStatCardsComponent`/`isDonutChartComponent`の型ガード関数を既存4関数と同じ形式で追加。
   - テンプレートの`v-if`/`v-else-if`チェーンに`<StatCardsView v-else-if="isStatCardsComponent(component)" :spec="component" />`・`<DonutChartView v-else-if="isDonutChartComponent(component)" :spec="component" />`を追加（既存分岐の並び順は変更しない。挿入位置は`TrendChartView`分岐の直後を推奨）。
   - 確認: `cd frontend && npx vue-tsc -b && npm run build`（AC-9）。

8. **手動確認（AC-1, 2, 3, 5, 6, 7, 8, 11〜15）**
   - README手順でbackend/frontendを起動し、「テスト戦略」節のシナリオを実施する。

9. **`docs/api/chat-response-schema.md`更新（FR-8, AC-11）**
   - 「API契約」節の「`docs/api/chat-response-schema.md`の更新方針」に従い追記する。追記内容が実際のレスポンス（ステップ3のテスト・ステップ8のcurl確認結果）と一字一句一致することを確認する。

## Risks / edge cases

- **`dailyTrendScenario()`リファクタリングによる既存シナリオCの回帰**: `buildDailyTrendChart()`への抽出時にロジック・定数（`DAILY_TREND_DAYS`/`DAILY_TREND_VALUES`/`DAILY_TREND_LABEL_FORMAT`）・平均の丸め処理を一切変更しないこと。既存`ChatControllerTest.chatReturnsDailyTrendScenarioForDailyKeyword`（`average`が`6.4`であること等）が抽出後も無変更でパスすることで検証する。
- **`@JsonSubTypes`/`permits`の追加順序・値の誤り**: `permits`リストに新型を追加し忘れるとコンパイルエラーになる（sealed interfaceのため機械的に検出される）。`@JsonSubTypes`の`name`属性のtypo（`stat_cards`/`donut_chart`）はコンパイルエラーにならず実行時までJSON上の`type`不一致に気づけないため、ステップ3のMockMvcテストで`type`文字列を明示的にアサートし検出する。
- **`delta`の`null`表現の一貫性**: `@JsonInclude(Include.NON_NULL)`は`StatCard`レコード単位のクラスアノテーションなので、`label`/`value`が`null`になることは想定していない（元々`String`必須フィールド）。誤って`label`に`null`を渡した場合もフィールド自体が省略されてしまいフロント検証が「フィールド欠落＝型不一致」として不正判定するため、これは安全側に倒れる（クラッシュせずエラーフォールバックになる）が、実装時に`label`/`value`へは必ず非nullの値を渡すこと。
- **キーワード衝突**: 「サマリー」「ダッシュボード」は既存キーワード・新規問い合わせフロー定型文・アラートフロー定型文のいずれとも文字列上重複しないことをgrepで確認済み（本ドキュメント「影響なし（確認済み）」節）。将来的な定型文追加時の再確認要否をコード内コメントに残す（実装ステップ2参照）。
- **ドーナツチャートの色パレット枯渇**: `labels`が固定パレットの色数を超える場合は色が循環（重複）する。本タスクのモックデータは4区分なので問題にならないが、将来的にlabels数が増える運用になった場合は視認性が下がる可能性がある点をコメントで残す程度に留め、要件外の対応（動的パレット生成等）は行わない。
- **凡例のレイアウト崩れ**: `label`文字列が長い場合、`StatCardsView`のグリッドや`DonutChartView`の凡例でテキストが折り返し/はみ出す可能性がある。今回のモックデータでは短い文字列のみを使用するため実害はないが、`overflow-hidden`/`whitespace-normal`等、既存コンポーネント同様の一般的なTailwindクラスで最低限の崩れ防止を行う。
- **AC-6/7/8の手動確認手順**: 未知type・不正フィールド・合計0のケースは通常のキーワード分岐では発生しないため、要件が指示するとおり一時的なコード改変（例: `dashboardScenario()`内で一時的に`type`未実装のダミーコンポーネントに差し替える、または`DonutChartComponent`の`values`を一時的に`[0.0, 0.0, 0.0, 0.0]`に差し替える）で状態を再現し、確認後に元に戻す。恒久的な変更としてコミットしないこと（`pipeline/4/design.md`のArchUnit確認手順と同様の「一時改変→確認→revert」パターンを踏襲）。

## Test strategy

- **backend自動テスト**: `ChatControllerTest`に追加する2テスト（「今月のサマリーを見せて」「ダッシュボードを表示して」）で、`components`配列の`type`順序（`stat_cards`→`donut_chart`→`trend_chart`）、`stat_cards.cards`の必須フィールド（`label`/`value`）と少なくとも1件の`delta`存在、`donut_chart`の`labels`/`values`件数一致を検証する（AC-4）。既存9テスト（担当者別・カテゴリ別・日別・非マッチ・新規問い合わせフロー各ステップ・バリデーション）を無変更のまま`./mvnw test`で実行し、回帰がないことを確認する（AC-3, AC-5, AC-10）。
- **frontend自動検証**: `npx vue-tsc -b`と`npm run build`のグリーン維持（AC-9）。フロントエンドに自動テストランナーは未導入のため、それ以外はすべて手動確認とする（`pipeline-config.md`の`testing`節を踏襲）。
- **手動確認シナリオ**（`npm run dev`＋`./mvnw spring-boot:run`で起動）:
  1. 「今月のサマリーを見せて」を送信し、要約テキスト＋KPIカード4枚＋ドーナツチャート＋トレンドチャートが1吹き出し内に表示されることを確認（AC-1）。
  2. 「ダッシュボードを表示して」等、別の言い回しでも同様の応答が返ることを確認（AC-2）。
  3. 既存代表シナリオ（「担当」「カテゴリ」「種別」「日別」を含む文、新規問い合わせフローの各ステップ、モニタリングアラート応答）を実施し、見た目・内容が本変更前と変わらないことを確認（AC-3, AC-5）。
  4. KPIカードのdeltaが`"+12%"`（緑・上矢印）、`"-8%"`（赤・下矢印）、`"±0pt"`（プレーン）、省略（プレーン）の4パターンがそれぞれ仕様どおり描画され、混在してもクラッシュしないことを確認（AC-12, AC-13, AC-14, AC-15）。
  5. リスク節記載の一時改変により、未知type受信時のエラーフォールバック（AC-6）、必須フィールド不足時のエラーフォールバック（AC-7）、`donut_chart`の`values`合計0時に描画がクラッシュ・崩れしないこと（AC-8）を確認し、確認後に改変を元に戻す。
  6. `docs/api/chat-response-schema.md`に追記した内容と、手順1・3で実際に確認したレスポンス内容が一致することを確認する（AC-11）。

## AC mapping

| AC | 内容（要約） | 実装ステップ | 検証（テスト戦略） |
|---|---|---|---|
| AC-1 | 「今月のサマリーを見せて」で3コンポーネントが1吹き出しに表示される | ステップ2（`dashboardScenario`）, 6, 7（描画） | 手動確認シナリオ1 |
| AC-2 | 「ダッシュボード」を含む別言い回しでも同様の応答 | ステップ2（`SUMMARY_KEYWORD`/`DASHBOARD_KEYWORD`のsubstring一致） | 自動テスト（ステップ3の`chatReturnsDashboardScenarioForDashboardKeyword`）＋手動確認シナリオ2 |
| AC-3 | 既存シナリオA〜Cの応答内容が変わらない | ステップ2（既存分岐は変更せず末尾に追記、`dailyTrendScenario`はロジック不変のリファクタのみ） | 自動テスト（既存`ChatControllerTest`のシナリオA/B/C相当3件が回帰なくパス）＋手動確認シナリオ3 |
| AC-4 | 統合テストで`stat_cards`/`donut_chart`の必須フィールドと`delta`存在を確認 | ステップ1, 2, 3 | 自動テスト（ステップ3の2テスト、MockMvc） |
| AC-5 | 新規問い合わせフロー・アラート応答が変わらない | ステップ2（挿入位置をカテゴリ/種別判定の直後・フォールバック直前に限定し、両フローの分岐より後段） | 自動テスト（既存フロー系テスト回帰なし）＋手動確認シナリオ3 |
| AC-6 | 未知typeでエラーフォールバック | ステップ5（`isValidUiComponentSpec`の`type`網羅、7値以外はfalse） | 手動確認シナリオ5（一時改変で未知type再現） |
| AC-7 | 必須フィールド不足でエラーフォールバック | ステップ5（`isValidStatCardsComponent`/`isValidDonutChartComponent`の件数・型検証） | 手動確認シナリオ5 |
| AC-8 | `donut_chart`合計0でもクラッシュ・崩れなし | ステップ6（`DonutChartView.vue`の`total > 0 ? ... : 0`ガード） | 手動確認シナリオ5（一時的に`values`を`[0,0,0,0]`へ差し替え） |
| AC-9 | `vue-tsc -b`・`npm run build`成功 | ステップ4, 5, 6, 7 | frontend自動検証（各ステップ後および最終） |
| AC-10 | `./mvnw test`全件成功 | ステップ1, 2, 3 | backend自動テスト（`./mvnw test`） |
| AC-11 | スキーマ文書に新2type定義・サンプル・トリガー・判定順序が記載され実レスポンスと一致 | ステップ9 | 手動確認シナリオ6 |
| AC-12 | `delta`が`+`始まりで緑・上矢印 | ステップ2（モックデータに`"+12%"`カード）, 6（`deltaTrend`判定） | 手動確認シナリオ4 |
| AC-13 | `delta`が`-`始まりで赤・下矢印 | ステップ2（モックデータに`"-8%"`カード）, 6 | 手動確認シナリオ4 |
| AC-14 | `delta`が`+`/`-`以外、または省略でプレーン表示 | ステップ2（モックデータに`"±0pt"`カード＋delta省略カード）, 6 | 手動確認シナリオ4 |
| AC-15 | delta付き/なしカード混在でもクラッシュせず表示 | ステップ2（4枚中1枚のみdelta省略の混在データ）, 6, 7 | 手動確認シナリオ4 |
