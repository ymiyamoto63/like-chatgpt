# レビュー結果 — チャット応答への「ダッシュボード応答」追加 (#6)

対象ブランチ: `feature/6-dashboard-response`
対象diff: `git diff 813c5d936972b1489a40129ace2717fd4a9fb005..HEAD`
参照: `docs/pipeline/6/requirements.md`（AC-1〜AC-15）, `docs/pipeline/6/design.md`, `docs/pipeline/6/implementation-notes.md`, `docs/pipeline/6/test-report.md`

## 結論

**blocking指摘: 0件、non-blocking指摘: 0件。**

diffを実コードで突き合わせて確認した結果、設計書・要件・実装ノートと実装内容に齟齬はなく、既存挙動への非回帰も確認できた。修正は不要と判断する。

## 確認した観点と結果

### 1. `dailyTrendScenario()` → `buildDailyTrendChart()` 抽出リファクタリング（AC-3の要）
- `KeywordMatchReplyGenerationAdapter.java` の diff を確認。日付ラベル生成ループ・`DAILY_TREND_VALUES`・平均計算（`stream().average()`）・丸め処理（`Math.round(average * 10) / 10.0`）は一切変更なく、そのまま `buildDailyTrendChart()` に移動しているのみ。
- `dailyTrendScenario()` は移動後 `trendChart.average()`（= 丸め後の `roundedAverage` と同値）を参照する形に書き換わっているが、値は完全に同一であり、reply文言の生成結果に差異は生じない。
- 既存 `ChatControllerTest`（`average` が `6.4` であることを検証する既存テスト）は無変更のままで、実装ノート・テストレポートともに回帰なしパスを報告しており、diff上もロジック変更がないことと整合する。

### 2. delta符号判定（FR-2a/2b）
- バックエンド: `StatCard`（`label`/`value`/`delta`）に `@JsonInclude(Include.NON_NULL)` を付与し、`delta = null` の場合はJSONフィールド自体が省略される。`dashboardScenario()` のモックデータで `"+12%"`/`"-8%"`/`"±0pt"`/`delta`省略（`null`）の4パターンを用意しており、AC-12〜15の検証データとして揃っている。
- フロントエンド: `StatCardsView.vue` の `deltaTrend()` は `undefined`/`null` → `null`（プレーン、delta行非表示）、`'+'`始まり → `'up'`（緑・▲）、`'-'`始まり → `'down'`（赤・▼）、それ以外 → `'flat'`（プレーン、delta文字列は表示するが矢印・色なし）を返しており、FR-2a/2bの仕様と完全に一致する。
- `isValidStatCard`（`chatApi.ts`）の `delta` 検証は `undefined`/`null`/`string` のみ許容し、数値等の他の型は不正と判定されることを実際にロジックを抜き出して動作確認済み（`delta: 12` → `false`）。

### 3. DonutChartView のゼロ除算対策（AC-8）
- `total = values.reduce(...)`、`ratio = total > 0 ? value / total : 0` という三項演算子でゼロ除算をガードしており、三角関数を経由しない `stroke-dasharray` 方式のため `total === 0` でも `NaN` 座標が発生する経路がない。`total === 0` の場合は全セグメントの `dasharray` が `"0 251.32..."` となり、色付きセグメントは描画されず下地グレーリングのみが残る。凡例側も `Math.round(0 * 100) = 0` で例外なく `0%` 表示される。設計書の想定どおりで、クラッシュ・NaN描画のリスクはない。
- `stroke-dashoffset={-cumulativeLength}` による累積オフセット・`<g transform="rotate(-90 50 50)">` による12時起点化も、ドーナツ/パイチャートの標準的な実装パターンとして妥当。

### 4. フロントエンド検証ロジック（chatApi.ts）
- `isValidStatCardsComponent`（`cards` 1件以上・各要素 `isValidStatCard`）、`isValidDonutChartComponent`（`isValidBarChartComponent` と同型: `title` string、`labels` 1件以上のstring配列、`values` が `labels` と同数のfinite number配列）とも、既存5種の検証関数と同じ書き方・粒度で実装されており、過不足はない。
- `isValidUiComponentSpec` の分岐末尾に `stat_cards`/`donut_chart` が追加され、未知の `type`（7値以外）は既存どおり `return false` に落ちる。`validateChatApiResponse` は `components.every(isValidUiComponentSpec)` が `false` なら例外を投げ応答全体をエラーフォールバックする、という既存の「部分成功なし」方針も変更されていない。

### 5. 既存挙動の非回帰
- `UiComponent.java` の `@JsonSubTypes` 既存5件・`permits` 既存5件は記載順・内容とも無変更で、新2型は末尾に追記されているのみ。
- `generateReply()` の分岐挿入位置は `categoryScenario()` 分岐の直後・`fallbackScenario()` の直前で、設計書・要件書（FR-5）の指定どおり。既存分岐（新規問い合わせ→アラート→日別→担当→カテゴリ/種別）は行単位で無変更。
- `MessageBubble.vue` の既存5分岐（`table`/`bar_chart`/`trend_chart`/`choices`/`faq_list`）の並び順・記述は無変更で、新2分岐は `TrendChartView` の直後・`ChoicesView` の直前に追加されているのみ。
- `frontend/src/constants/monitoringAlert.ts`・`reportButtons.ts`（フロント⇔バック同期定数）、`ArchitectureTest.java` はいずれも diff に含まれておらず、grepでも「サマリー」「ダッシュボード」との文字列重複は見つからない。
- `.vscode/settings.json`（gitStatusの未追跡ファイル）はdiffに含まれていないことを確認した。

### 6. 設計書・スキーマ文書との整合
- `docs/api/chat-response-schema.md` の追記内容（フィールド定義表・サンプルJSON・判定順序表・フォールバック節の「7値以外」への更新）は設計書「API契約」節・「更新方針」節の指定と一字一句一致。実装ノート記載のとおり、`trend_chart.labels` の日付部分のみ既存シナリオC同様「確認時点の固定サンプル」であり、これは既存ドキュメント慣例の踏襲であって齟齬ではない。

### 7. XSS・命名・スタイル
- `StatCardsView.vue`・`DonutChartView.vue` とも `v-html` は使用しておらず、テキスト・数値はすべてテキストバインディング（`{{ }}`）で描画している。
- Tailwindクラスは動的な文字列結合を避け、`TopologyDiagram.vue` の `LEVEL_STROKE_CLASS` と同様に完全なクラス文字列をパレット配列として保持する方式を採用しており、既存コードの慣例と整合し、Tailwindの静的解析対象から外れる懸念もない。

## 補足（指摘ではないが留意点として記載）

- テストレポートに記載のとおり、AC-1/2/3/5/6/7/8/12〜15 はブラウザでの実目視確認が一部未実施（コードトレース・API応答レベルでの確認のみ）。これはレビュー観点では「テストが検証していない領域」だが、コードロジック自体は上記1〜3の突き合わせにより正しいと判断でき、blocking指摘とはしない。パイプライン運用者側で必要に応じてブラウザでの最終確認を行うことを推奨する。
