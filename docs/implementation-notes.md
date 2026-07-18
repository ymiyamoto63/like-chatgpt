# Implementation Notes: 動的UI生成チャット応答

> このドキュメントは今回の機能（動的UI生成チャット応答・モック版）向けに新規作成した。前回パイプライン実行分（別機能）の内容は置き換えた。

## ステップ1: バックエンド 応答スキーマの型定義

- `backend/src/main/java/com/example/chatbackend/UiComponent.java`（新規） — `sealed interface UiComponent permits TableComponent, BarChartComponent`。`@JsonTypeInfo(use=Id.NAME, include=As.PROPERTY, property="type")` + `@JsonSubTypes`（`table`/`bar_chart`）
- `backend/src/main/java/com/example/chatbackend/TableComponent.java`（新規） — `record TableComponent(List<String> columns, List<List<String>> rows) implements UiComponent`
- `backend/src/main/java/com/example/chatbackend/BarChartComponent.java`（新規） — `record BarChartComponent(String title, List<String> labels, List<Double> values) implements UiComponent`（設計書通り `values` は `List<Double>`）
- `backend/src/main/java/com/example/chatbackend/ChatResponse.java`（変更） — `record ChatResponse(String reply, List<UiComponent> components)` に変更

逸脱なし。設計書の型定義をそのまま実装した。

## ステップ2: バックエンド モックサービス実装

- `backend/src/main/java/com/example/chatbackend/MockChatService.java`（新規） — `@Service`。`generateResponse(String message)` が「担当」→シナリオA、「カテゴリ」/「種別」→シナリオB、非マッチ→シナリオCを判定し `ChatResponse` を組み立てて返す。ステートレス（インスタンスフィールドなし）。データ値は設計書「モックデータ（固定・3シナリオ）」節の値をそのまま使用
- `backend/src/main/java/com/example/chatbackend/EchoService.java`（削除）
- `backend/src/test/java/com/example/chatbackend/MockChatServiceTest.java`（新規） — 担当者キーワード、カテゴリキーワード、種別キーワード、非マッチの4パターンで `reply` 文言・`components` の型/件数/値を検証
- `backend/src/test/java/com/example/chatbackend/EchoServiceTest.java`（削除）

逸脱なし。「種別」キーワードのテストケースを追加（設計書の3シナリオのうちシナリオBのトリガーは「カテゴリ」「種別」の2語のため、両方の分岐を確認する目的で追加。スコープ内の網羅性向上であり逸脱ではない）。

## ステップ3: バックエンド コントローラー更新と統合テスト

- `backend/src/main/java/com/example/chatbackend/ChatController.java`（変更） — コンストラクタ注入を `EchoService` → `MockChatService` に変更し、`chat()` は `mockChatService.generateResponse(request.message())` の戻り値（`ChatResponse`）をそのまま返す
- `backend/src/test/java/com/example/chatbackend/ChatControllerTest.java`（変更） — 旧 `chatReturnsEchoedReply` を撤去し、以下に差し替え:
  - `chatReturnsAssigneeScenarioForAssigneeKeyword` — 担当者系キーワードで200、`components[0].type=="table"`・`components[1].type=="bar_chart"` を含め、`reply`・`columns`・`rows`・`title`・`labels`・`values` の値まで `jsonPath` で検証
  - `chatReturnsCategoryScenarioForCategoryKeyword` — カテゴリ系キーワードでシナリオBのデータを検証
  - `chatReturnsGuidanceTextWithoutComponentsForNonMatchingMessage` — 「こんにちは」で `components` が空配列、`reply` に「エコー」を含まないことを検証
  - `chatReturnsBadRequestForBlankMessage` — 既存のまま維持（AC-9の退行防止）

逸脱なし。

## 検証結果

- `./mvnw.cmd test`（PowerShellがPATH上に無かったため `/c/Windows/System32/WindowsPowerShell/v1.0` を一時的にPATHへ追加して実行。プロジェクトファイルの変更ではない）
- 結果: `Tests run: 9, Failures: 0, Errors: 0, Skipped: 0` — BUILD SUCCESS
- 設計書の懸念点（Risks / edge cases:「`type` フィールドの自動注入の検証漏れ」）について、`ChatControllerTest` の `jsonPath("$.components[0].type", is("table"))` / `jsonPath("$.components[1].type", is("bar_chart"))` のアサーションが green で通ったことにより、Jacksonの `@JsonTypeInfo(As.PROPERTY)` が実際に `"type":"table"` / `"type":"bar_chart"` をシリアライズ結果に出力することを確認済み

## スコープ外（今回の実装範囲に含めなかったもの）

- フロントエンド一式（型定義・APIクライアント・ストア・描画コンポーネント）— 設計書ステップ5〜9
- `docs/chat-response-schema.md` の作成（AC-8）— 設計書ステップ4

## ステップ4: 応答JSONスキーマ契約文書の作成（AC-8）

- `docs/chat-response-schema.md`（新規） — 位置づけ（将来LLM出力契約／現状はモック実装が生成）、`ChatResponse`/`TableComponent`/`BarChartComponent` のフィールド定義表・制約、3シナリオ分のサンプルJSON、フロントエンドの安全なフォールバック方針と将来の `type` 拡張の進め方を記載

検証方法・結果:
- `UiComponent.java` / `TableComponent.java` / `BarChartComponent.java` / `ChatResponse.java` / `MockChatService.java` / `ChatControllerTest.java` を実読し、設計書「応答JSONスキーマ（確定仕様）」節・「モックデータ（固定・3シナリオ）」節との相違がないことを確認した
- さらに実機確認として、バックエンド（`./mvnw.cmd spring-boot:run`）を起動し、`ChatControllerTest` と同一の3入力文（担当者系／カテゴリ系／非マッチ）を実際に `curl` で送信し、得られた生JSONをそのままサンプルとしてドキュメントに転記した。`values` は `List<Double>` 実装のため `12.0` のように小数点付きでシリアライズされることを実レスポンスで確認し、その旨を制約欄に明記した
- 起動中に既存の別プロセス（`.vscode` 拡張のJREから起動していた、ポート8080を占有する旧ビルドのSpring Bootプロセス）が旧 `EchoService` の応答を返していたため、いったんそのプロセスを停止してから改めて現行コードでビルド・起動し直して確認した（実装コードの変更ではなく、検証環境の整理）
- 設計書・実装コード・ドキュメントの間に相違点は見つからなかった（コードが設計書通りであり、ドキュメントもその両方と一致している）

逸脱・スコープ外: なし。本ステップはドキュメント作成のみで、コード変更は行っていない。

## ステップ5: フロントエンド 型定義更新

- `frontend/src/types/chat.ts`（変更） — `TableComponent`（`type: 'table'`, `columns: string[]`, `rows: string[][]`）、`BarChartComponent`（`type: 'bar_chart'`, `title: string`, `labels: string[]`, `values: number[]`）、これらの判別共用体 `UiComponentSpec` を追加。`Message` に `components?: UiComponentSpec[]` を追加

逸脱なし。設計書通り。

## ステップ6: フロントエンド APIクライアントの検証ロジック実装

- `frontend/src/api/chatApi.ts`（変更） — 旧 `ChatResponse { reply: string }` を `ChatApiResponse { reply: string; components: UiComponentSpec[] }` に置き換え、`ChatResponseFormatError extends Error` を新規追加。`postChat` は `response.json()`（`unknown` 扱い）直後に `validateChatApiResponse` を通し、`reply` が `string` であること、`components` が配列であること、各要素が `type` に応じて `table`（`columns: string[]` 非空・`rows: string[][]` で各行長が `columns.length` と一致）または `bar_chart`（`title: string`・`labels: string[]` 非空・`values: number[]` が `labels` と同数かつ全要素 `Number.isFinite`）の条件を満たすことを検証。いずれか不正なら `ChatResponseFormatError` を throw、妥当なら検証済み `ChatApiResponse` を返す。`!response.ok` 時の既存の `Error` throw（通信エラー系）はそのまま変更していない
- 実装補助として `isStringArray` / `isValidTableComponent` / `isValidBarChartComponent` / `isValidUiComponentSpec` の型ガード関数をモジュール内に追加（設計書に明示の記述はないが、「型ガード関数」の使用は設計書ステップ6の記述と整合する）

逸脱なし。

## ステップ7: フロントエンド ストア更新

- `frontend/src/stores/conversationStore.ts`（変更） — `createMessage` に第3引数 `components?: UiComponentSpec[]` を追加し `Message` に渡すよう変更。`sendMessage` は `postChat` の戻り値から `{ reply, components }` を分割代入し、アシスタントメッセージ生成時に `components` を渡す。`catch (err)` で `err instanceof ChatResponseFormatError` を判定し、`true` なら「応答を表示できませんでした」、`false`（既存の通信エラー・非2xx等）なら従来通り「エラー: 応答を取得できませんでした」の吹き出しを追加（FR-8の退行なし）

逸脱なし。

## 検証結果（ステップ5〜7）

- `frontend/` で `npx vue-tsc --noEmit` を実行し、EXIT_CODE=0（エラーなし）を確認した
- 実行環境の補足: Bash実行環境（Git Bash）のPATHにNode.jsが含まれていなかったため、`/c/nvm4w/nodejs` を一時的にPATHへ追加して実行した（プロジェクトファイルの変更ではない）
- バックエンドを起動しての手動確認（実際の応答形状の目視確認、Vue devtoolsでの`components`格納確認）は本ステップの確認方法として設計書に記載されているが、次ステップ（描画コンポーネント）が未実装のため画面上での確認はスコープ外とし、型チェックの通過のみで本ステップの完了条件とした

## スコープ外（ステップ5〜7の範囲に含めなかったもの）

- `frontend/src/components/TableView.vue` / `BarChartView.vue`（新規）、`MessageBubble.vue`（変更）— 設計書ステップ8
- 結合の手動確認一式（設計書ステップ9、AC-1, 2, 3, 5, 6, 7）

## ステップ8: フロントエンド 描画コンポーネント新規作成とMessageBubble組み込み

- `frontend/src/components/TableView.vue`（新規） — props `spec: TableComponent`。`<table><thead>` に `columns` を `v-for`＋`{{ }}` テキスト補間で列見出しとして描画、`<tbody>` に `rows` を二重 `v-for`＋`{{ }}` でセル描画。`v-html` 不使用。横に長い表向けに `.table-view` へ `overflow-x: auto` を付与
- `frontend/src/components/BarChartView.vue`（新規） — props `spec: BarChartComponent`。`computed` の `maxValue`（`Math.max(0, ...spec.values)`、空配列でも `0` にフォールバック）と `bars`（`labels` を基準に `label`/`value`/`widthPercent` を算出済みの配列）を用意し、テンプレートは `title` を `{{ }}` で表示、`bars` を `v-for` して各行にラベル（`{{ }}`）・横棒（`:style="{ width: bar.widthPercent + '%' }"`。`widthPercent` は自前計算した `number` のみを渡し、レスポンス由来の文字列をそのまま `:style` に埋め込むことはしていない）・数値（`{{ }}`）を描画。`maxValue` が `0`（全値0または空配列）の場合は `widthPercent` が `0` になり0除算を回避
- `frontend/src/components/MessageBubble.vue`（変更） — 既存の `{{ message.content }}` の下に `message.components` を `v-for`（`key` はindex）し、判別共用体をテンプレート内の `===` 比較のみで直接絞り込ませるとvue-tscが正しくナローイングしない懸念があった（設計書のRisks節記載の懸念）ため、設計書の代替案どおり型ガード関数 `isTableComponent(c): c is TableComponent` / `isBarChartComponent(c): c is BarChartComponent` を `<script setup>` 内に追加し、`v-if="isTableComponent(component)"` → `TableView`、`v-else-if="isBarChartComponent(component)"` → `BarChartView`、それ以外（未知の `type`）は何も描画しない、という構成にした。スタイルは `.component-wrapper { margin-top: 10px; white-space: normal; }` を追加し、既存の `.bubble { white-space: pre-wrap }` の中でもテーブル・グラフのレイアウトが崩れないようにした（`--border` / `--accent` / `--accent-bg` / `--code-bg` / `--text-h` など既存のCSS変数のみを使用し、他コンポーネントの配色規約に合わせた）

### 逸脱: `frontend/src/api/chatApi.ts` の型ガード関数シグネチャ修正（ステップ8のスコープ外だが、`npm run build` を green にするために必要だった最小修正）

- 現象: `npx vue-tsc --noEmit`（引数なし）はexit 0で通過するが、これはルートの `tsconfig.json` が `"files": []` で参照先を `-b`（ビルドモード）以外では解決しないため、実質0ファイルしかチェックしていない（`--listFiles` で確認）ことが判明した。実際にビルドで使われる `npm run build`（内部で `vue-tsc -b` を実行）や `npx vue-tsc --noEmit -p tsconfig.app.json` を実行すると、ステップ6で追加された `isValidTableComponent(value: Record<string, unknown>): value is TableComponent` / `isValidBarChartComponent(value: Record<string, unknown>): value is BarChartComponent` が `TS2677`（型述語の型がパラメータの型に代入可能でない。`TableComponent`/`BarChartComponent` に文字列インデックスシグネチャが無いため）でコンパイルエラーになっていた。ステップ8で新規追加した3ファイルとは無関係の、ステップ6由来の既存不具合であり、当時の確認方法（引数なしの `vue-tsc --noEmit`）が実質チェックしていなかったために見逃されていたものと判断した
- 対応: `frontend/src/api/chatApi.ts` の該当2関数のパラメータ型を `Record<string, unknown>` から `unknown` に変更し、関数内で `value as Record<string, unknown>` に変換してから分割代入する形に変更した（外部からの呼び出し方・戻り値の型・検証ロジックの中身は一切変更していない。シグネチャの型のみの修正）
- 判断理由: 設計書ステップ8の確認方法（`npx vue-tsc --noEmit` と `npm run build` の両方をgreenにする）を満たすために不可欠であり、修正内容がステップ8の新規コードとは独立した1ファイル・2行相当の型シグネチャ修正にとどまるため、その場で最小修正として実施した。本来はステップ6の担当範囲の不具合であるため、後続の担当者への申し送り事項として明記する

## 検証結果（ステップ8）

- `frontend/` で `rm -rf node_modules/.tmp && npx vue-tsc -b` → EXIT_CODE=0
- `frontend/` で `npm run build`（内部で `vue-tsc -b && vite build`）→ 成功、`dist/` 生成（`vite v7.3.6`、`40 modules transformed`、`built in 1.71s`）
- `frontend/` で `npx vue-tsc --noEmit` および `npx vue-tsc --noEmit -p tsconfig.app.json` → いずれもEXIT_CODE=0
- `grep -r "v-html" frontend/src` → 0件（XSS対策の非機能要件を満たすことを確認）
- 実行環境の補足: Bash実行環境のPATHにNode.jsが含まれていなかったため、`/c/nvm4w/nodejs` を一時的にPATHへ追加して実行した（プロジェクトファイルの変更ではない）
- バックエンド・フロントエンド両方を起動しての目視確認（設計書ステップ8の確認方法の後半）は、依頼元の指示により本ステップではスコープ外とし、ビルド・型チェックのgreenをもって完了条件とした（手動E2E確認は設計書ステップ9で実施予定）

## スコープ外（ステップ8の範囲に含めなかったもの）

- 結合の手動確認一式（設計書ステップ9、AC-1, 2, 3, 5, 6, 7）— フロント・バックエンド起動状態でのブラウザ目視確認

## レビュー指摘対応（`docs/review.md` 軽微な指摘 1・2）

- `README.md`（変更） — 冒頭の説明文（旧「エコー応答のモック実装」）を「`POST /api/chat`で構造化JSON応答（`{reply, components[]}`）を返すキーワードマッチのモック実装」に更新し`docs/chat-response-schema.md`への参照を追加。「疎通確認方法」節の「エコー応答が表示されれば疎通確認は完了です」を、「今月の問い合わせ件数を担当者別にまとめて」の送信例と、担当者別／カテゴリ別／非マッチ時の実際の表示内容（表・棒グラフ・案内テキスト）の説明に更新。既存の見出し構成・トーンは変更せず、該当2箇所のみの最小修正
- `frontend/src/components/TableView.vue`（変更） — `<th v-for="column in spec.columns" :key="column">` を `<th v-for="(column, columnIndex) in spec.columns" :key="columnIndex">` に変更（列見出しの重複衝突対策）。行・セルの `key`（`rowIndex`/`cellIndex`）は元々index-baseだったため変更なし
- `frontend/src/components/BarChartView.vue`（変更） — `<div v-for="bar in bars" :key="bar.label">` を `<div v-for="(bar, barIndex) in bars" :key="barIndex">` に変更（ラベル重複衝突対策）

逸脱: なし。review.mdの「対応案」通りの修正。バックエンド変更なし。

検証結果:
- `frontend/` で `npm run build`（内部で `vue-tsc -b && vite build`）→ 成功、`dist/` 生成（`vite v7.3.6`、`40 modules transformed`、`built in 653ms`）。Bash実行環境のPATHにNode.jsが含まれていなかったため`/c/nvm4w/nodejs`を一時的にPATHへ追加して実行（プロジェクトファイルの変更ではない）
- バックエンド変更なしのためMavenテストは再実行していない

## 2026-07-12 — サイドバー定型レポートボタン — Step 1-2

対応する設計書: `docs/design.md`（サイドバー定型レポートボタン）。本エントリは同設計書の実装ステップ1・2のみを対象とする（`Sidebar.vue` の変更は別ステップ・別担当）。

- `frontend/src/constants/reportButtons.ts`（新規） — `REPORT_BUTTONS: readonly string[]` を `export`。値は設計書どおり `'当月担当者別問い合わせ'` と `'当月カテゴリ別問い合わせ'` の2件。`frontend/src/constants/` ディレクトリは本ファイルの作成により新規に生まれた（既存ディレクトリなし）
- `frontend/src/stores/conversationStore.ts`（変更） — `actions` に `async sendReportPrompt(label: string)` を追加。`this.createConversation()` を呼んだ直後（`await` を挟まず）に `await this.sendMessage(label)` を呼ぶ設計書どおりの実装。`createConversation()` / `sendMessage()` 本体は無変更。順序不変条件（AC-4の担保根拠）を説明する日本語コメントを設計書の記述どおりそのまま付与した

逸脱: なし。設計書の実装詳細（コード例）をそのまま反映した。

## 検証結果（Step 1-2）

- `cd frontend && npx vue-tsc -b` を実行し `EXIT_CODE=0`（エラーなし）を確認
- 実行環境の補足: Bash実行環境（Git Bash）のPATHにNode.jsが含まれていなかったため、`/c/nvm4w/nodejs` を一時的にPATHへ追加して実行した（プロジェクトファイルの変更ではない。過去エントリと同様の対応）
- `lessons-learned.md` の指摘どおり、引数なしの `vue-tsc --noEmit` は使わず `-b`（ビルドモード）で検査した

## スコープ外（Step 1-2の範囲に含めなかったもの）

- `frontend/src/components/Sidebar.vue` の変更（テンプレート2分割・レポートボタン描画・CSS）— 設計書の実装ステップ3
- 手動でのAC-1〜AC-8通し確認 — 設計書の実装ステップ4（`Sidebar.vue` 未実装のため実施不可）

## 2026-07-12 — サイドバー定型レポートボタン — Step 3

対応する設計書: `docs/design.md`（サイドバー定型レポートボタン）。本エントリは実装ステップ3（`Sidebar.vue` の変更）のみを対象とする。ステップ1・2（`reportButtons.ts` 新規作成・`conversationStore.ts` への `sendReportPrompt` 追加）は前エントリの通り実施済みで、本ステップでは変更していない。

- `frontend/src/components/Sidebar.vue`（変更） — テンプレートを `.sidebar-top`（New Chat ボタン＋会話履歴一覧、既存の `handleNewChat`/`handleSelect`・アクティブ表示ロジックは無変更）と `.sidebar-reports`（「定型レポート」見出し＋`REPORT_BUTTONS` を `v-for` した2つのボタン）の2セクションに分割。新規 `handleReport(label)` を追加し、`REPORT_BUTTONS` を import、各ボタンの `@click` から `store.sendReportPrompt(label)` を呼び出す（`Promise` は await しない。`MessageInput.vue` 等の既存の流儀と同一）。各ボタンには `:disabled="store.isLoading"` を付与し、`v-for` の `:key` はラベル文字列自身を使用。CSSは設計書の記載通り、`.sidebar` に `min-height: 0` を追加、`.sidebar-top` を `flex: 1; min-height: 0` のフレックスコンテナ化、`.conversation-list` に `flex: 1; min-height: 0; overflow-y: auto` を追加（既存にはなかった内部スクロール対応。設計書が明記する意図的な差分）、`.sidebar-reports`（`flex-shrink: 0`・上部との境界線 `border-top`）、`.sidebar-reports-heading`、`.report-button`（`.new-chat-button` と同トーン、`:hover:not(:disabled)` と `:disabled` は `MessageInput.vue` の `.send-button` に合わせた配色）を新規追加した。`.new-chat-button`／`.conversation-item` 系の既存スタイルは変更していない。

逸脱: なし。設計書の実装詳細（テンプレート例・CSS例）をそのまま反映した。

## 検証結果（Step 3）

- `cd frontend && npx vue-tsc -b` → `EXIT_CODE=0`（エラーなし）
- `cd frontend && npm run build`（内部で `vue-tsc -b && vite build`）→ 成功、`dist/` 生成（`vite v7.3.6`、`41 modules transformed`、`built in 2.12s`）
- 実行環境の補足: Bash実行環境（Git Bash）のPATHにNode.jsが含まれていなかったため、`/c/nvm4w/nodejs` を一時的にPATHへ追加して実行した（プロジェクトファイルの変更ではない。過去エントリと同様の対応）
- `lessons-learned.md` の指摘どおり、引数なしの `vue-tsc --noEmit` は使わず `-b`（ビルドモード）で検査した
- ブラウザでの手動確認（AC-1〜AC-8の通し確認、設計書の実装ステップ4）は本ステップの依頼範囲外のため実施していない

## スコープ外（Step 3の範囲に含めなかったもの）

- 手動でのAC-1〜AC-8通し確認 — 設計書の実装ステップ4（フロントエンド・バックエンド起動状態でのブラウザ目視確認）

# システム構成図モニタリング画面

## 2026-07-17 — システム構成図モニタリング画面 — Step 1-3（バックエンド一式＋APIドキュメント）

対応する設計書: `docs/design.md`（システム構成図モニタリング画面）。対応する要件定義: `docs/requirements/system-monitoring-view.md`（FR-3, FR-11, AC-1, AC-2）。本エントリは実装ステップ1〜3（バックエンドDTO・メトリクス生成サービス・コントローラ・自動テスト・APIスキーマ文書）のみを対象とする。フロントエンド一式（ステップ4以降）は別担当・別エントリとする。

- `backend/src/main/java/com/example/chatbackend/MonitoringNode.java`（新規） — `record MonitoringNode(String id, String label, double cpuPercent, double memoryPercent)`。設計書どおり `UiComponent` 系とは無関係の独立DTO
- `backend/src/main/java/com/example/chatbackend/MonitoringEdge.java`（新規） — `record MonitoringEdge(String id, String sourceId, String targetId, double bandwidthPercent)`
- `backend/src/main/java/com/example/chatbackend/MonitoringSnapshot.java`（新規） — `record MonitoringSnapshot(List<MonitoringNode> nodes, List<MonitoringEdge> edges)`
- `backend/src/main/java/com/example/chatbackend/MonitoringController.java`（新規） — `@RestController`。コンストラクタDIで `MonitoringMetricsService` を受け取り、`GET /api/monitoring/snapshot` で `getSnapshot()` をそのまま返す。設計書のコード例どおり
- `backend/src/main/java/com/example/chatbackend/MonitoringMetricsService.java`（新規） — `@Service` シングルトンBean。固定トポロジー（9ノード・12エッジ、設計書の表のとおりのid/label/baseline値）をコンストラクタで初期化し、ノードCPU・ノードメモリ・エッジ帯域の3つの `Map<String, MetricState>`（`MetricState` は `baseline`/`current`/`decayTicksRemaining` を持つ可変クラス）で前回値を保持する。`getSnapshot()` は `synchronized`。定数（`RANDOM_WALK_STEP=3.0`、`MEAN_REVERSION_FACTOR=0.05`、`SPIKE_MEAN_INTERVAL_TICKS=12`、`SPIKE_INTERVAL_JITTER_TICKS=6`、`SPIKE_DURATION_TICKS=3`、`SPIKE_PEAK_MIN=78.0`、`SPIKE_PEAK_MAX=98.0`、`SPIKE_DECAY_FACTOR=0.4`）は設計書の仮置き値をそのまま採用。1呼び出し＝1ティックのカウンタ管理で、平均12ティック±6ティックの一様乱数間隔で30系列（9ノード×2＋12エッジ）から一様乱数で1系列を選び、選択がノード系列ならCPU・メモリ両方を、エッジ系列なら帯域幅を3ティックの間ピーク値（78〜98の一様乱数）へ強く引き寄せ、スパイク終了後2ティックは係数0.4の強めの平均回帰を適用する。すべての系列は毎ティック `clamp(0, 100)` を適用
- `backend/src/test/java/com/example/chatbackend/MonitoringMetricsServiceTest.java`（新規） — `MockChatServiceTest.java` と同スタイル（AssertJ、フィールド初期化で `new MonitoringMetricsService()` を直接インスタンス化）。(a) 初回 `getSnapshot()` でノード9件・エッジ12件・全エッジの `sourceId`/`targetId` がノードID集合に含まれること・全メトリクスが `[0, 100]` に収まることを検証（AC-1相当）、(b) 30回連続呼び出しし、毎回全メトリクスが `[0, 100]` に収まることに加え、初回と最終回を比較して少なくとも1系列は値が変化していることを検証（AC-2相当）
- `backend/src/test/java/com/example/chatbackend/MonitoringControllerTest.java`（新規） — `ChatControllerTest.java` と同スタイル（`@SpringBootTest` + `@AutoConfigureMockMvc` + `MockMvc`）。`GET /api/monitoring/snapshot` が200を返し、`$.nodes`/`$.edges` のサイズ（9/12）と代表フィールド（`nodes[0]`/`edges[0]` の各フィールドの型・値域）を `jsonPath` で確認
- `docs/monitoring-response-schema.md`（新規） — `docs/chat-response-schema.md` と同水準の文体・構成（位置づけ→エンドポイント→トップレベルスキーマ→各DTOのフィールド表→固定トポロジー表→サンプルJSON→変動ロジック概要→フロントエンドの安全なフォールバック）で、設計書の「新API」節・「バックエンドクラス設計」節の内容を文書化

逸脱:
- 設計書はスパイク管理の内部状態として `Instant spikeEndsAt` / `Instant nextSpikeEligibleAt`（実時刻）という型を明記していたが、実装では `long spikeEndsAtTick` / `long nextSpikeEligibleAtTick`（呼び出し回数ベースのティックカウンタ）とした。設計書自身が同じ節の冒頭で「1回の呼び出し＝1ティック」と明言し、Risks節でも「バックエンドは実時刻ベースのスケジューラを持たず、フロントの5秒ポーリング頻度に依存してペースが決まる」と明記しているため、実時刻 `Instant` ではなくティックカウンタで管理する方が設計意図（実時刻スケジューラなし）に忠実であり、かつ自動テスト（30回連続呼び出し）が実行時間に依存せず決定論的に動作する。これは設計書の文言上の型指定からの逸脱だが、設計書自身が明記する意図に沿わせるための意図的な選択
- それ以外は設計書の詳細設計節（定数値・トポロジー表・アルゴリズム手順）をそのまま反映した

スコープ外（本ステップの範囲に含めなかったもの）:
- フロントエンド一式（`types/monitoring.ts`、`api/monitoringApi.ts`、`constants/monitoring.ts`・`constants/monitoringLayout.ts`、`stores/monitoringStore.ts`、`components/MonitoringView.vue`、`components/TopologyDiagram.vue`、`App.vue`/`Sidebar.vue` の結線） — 設計書の実装ステップ4〜7
- 手動でのAC-3〜AC-8通し確認 — フロントエンド未実装のため実施不可（設計書の実装ステップ6〜8）
- `curl` による起動中バックエンドへの目視確認（設計書ステップ3の検証手順の一部） — ローカル環境の8080番ポートに別プロセス（本タスクとは無関係の既存プロセス）が待ち受けており、そのプロセスを停止させる権限がないため未実施。かわりに `MonitoringControllerTest`（`MockMvc` による実際のHTTPレベル統合テスト、Spring全体を起動して検証）でレスポンス構造を確認しており、内容面の検証は担保されている

検証結果:
- `cd backend && ./mvnw.cmd test`（全件） → `Tests run: 25, Failures: 0, Errors: 0, Skipped: 0` で `BUILD SUCCESS`。既存の `ChatBackendApplicationTests`（1件）・`ChatControllerTest`（9件）・`MockChatServiceTest`（12件）もグリーンのまま（新規Bean追加によるSpringコンテキスト起動の回帰なしを確認）。新規の `MonitoringControllerTest`（1件）・`MonitoringMetricsServiceTest`（2件）もグリーン
- 実行環境の補足: Bash実行環境（Git Bash）のPATHに `powershell.exe` を含むディレクトリが含まれておらず `mvnw.cmd` が起動できなかったため、`/c/Windows/System32/WindowsPowerShell/v1.0` と `/c/Windows/System32` を一時的にPATHへ追加して実行した（プロジェクトファイルの変更ではない）

## 2026-07-17 — システム構成図モニタリング画面 — Step 4-5（フロントエンド 型・定数・APIクライアント・ストア）

対応する設計書: `docs/design.md`（システム構成図モニタリング画面）の実装ステップ4・5。ステップ1〜3（バックエンド一式）は前エントリの通り実装済みで、本ステップでは変更していない。既存ファイル（`App.vue`/`Sidebar.vue` 等）は未変更（結線はステップ7で別途実施）。

- `frontend/src/types/monitoring.ts`（新規） — `MonitoringNode`（`id`/`label`: `string`、`cpuPercent`/`memoryPercent`: `number`）、`MonitoringEdge`（`id`/`sourceId`/`targetId`: `string`、`bandwidthPercent`: `number`）、`MonitoringSnapshot`（`nodes: MonitoringNode[]`、`edges: MonitoringEdge[]`）のインターフェース定義。設計書・`docs/monitoring-response-schema.md` のフィールド名・型とそのまま一致させた
- `frontend/src/constants/monitoring.ts`（新規） — `WARNING_THRESHOLD = 70`、`DANGER_THRESHOLD = 90`、`POLL_INTERVAL_MS = 5000`、`MonitoringLevel = 'normal' | 'warning' | 'danger'`、`getMonitoringLevel(value): MonitoringLevel`（`danger`→`warning`→`normal` の順にしきい値判定）を設計書のコード例のまま実装
- `frontend/src/constants/monitoringLayout.ts`（新規） — `NODE_WIDTH=140`/`NODE_HEIGHT=76`/`VIEWBOX_WIDTH=960`/`VIEWBOX_HEIGHT=480` と、設計書「SVGレイアウトの座標方針」節の9ノード分の座標表をそのまま `NODE_POSITIONS: Record<string, { x: number; y: number }>` として定数化。Risks節（ノードID不整合）を踏まえ、バックエンドのトポロジー定義と独立したハードコードである旨・変更時は両ファイルを同時更新すべき旨のコメントを付与
- `frontend/src/api/monitoringApi.ts`（新規） — `chatApi.ts` と同一方針・同一コードスタイル。`MonitoringResponseFormatError extends Error`、`isValidMonitoringNode`/`isValidMonitoringEdge`（`unknown` を受け取り、必須フィールドの型・有限性のみ検証。値域(0〜100)チェックはしない＝`chatApi.ts` の既存方針・設計書157行目の記述どおり）、`validateMonitoringSnapshot`（`nodes`/`edges` が配列かつ全要素が妥当であることを検証、不正なら `MonitoringResponseFormatError` を throw）、`getMonitoringSnapshot()`（`fetch('/api/monitoring/snapshot')`、`!response.ok` は通信エラー系の `Error` を throw、成功時は `validateMonitoringSnapshot` を通す）
- `frontend/src/stores/monitoringStore.ts`（新規） — `conversationStore.ts` と同じPinia Options API形式。`state`: `activeScreen`（`'chat' | 'monitoring'`、初期値 `'chat'`）、`snapshot`（`MonitoringSnapshot | null`）、`lastUpdatedAt`（`number | null`）、`hasError`（`boolean`）、`pollTimerId`（`number | null`）。`actions`: `showMonitoring()`/`showChat()`（フラグ切替のみ）、`startPolling()`（`pollTimerId !== null` なら二重登録防止で即return、`fetchSnapshot()` を即時1回呼んでから `window.setInterval` で `POLL_INTERVAL_MS` ごとに登録）、`stopPolling()`（`pollTimerId !== null` なら `window.clearInterval` して `null` に戻す）、`fetchSnapshot()`（成功時は `snapshot`/`lastUpdatedAt` を更新し `hasError = false`、失敗時は `catch` で `hasError = true` のみ設定し `snapshot` は書き換えない＝直前データ維持でFR-9を満たす）。設計書のコード例をそのまま反映した

逸脱: なし。設計書「フロントエンド構成」節のコード例・型定義をそのまま実装した。

検証結果:
- `cd frontend && npx vue-tsc -b`（lessons-learnedの教訓どおりbareの `--noEmit` は使わず `-b` を使用） → `EXIT_CODE=0`（エラーなし）。既存ファイルは無変更のため既存ビルドへの影響もないことを確認
- 実行環境の補足: Bash実行環境（Git Bash）のPATHにNode.jsが含まれていなかったため、`/c/nvm4w/nodejs` を一時的にPATHへ追加して実行した（プロジェクトファイルの変更ではない。過去エントリと同様の対応）
- `docs/design.md` が本ステップ着手前の時点で既に作業ツリー上で変更されていた（`git diff --stat` で226行追加・163行削除を確認）。これは本ステップの担当範囲外であり、本ステップでは `docs/design.md` を一切編集していない（`Read` のみで `Edit`/`Write` は未使用）。おそらく先行するバックエンド担当（ステップ1〜3）による変更と推測されるが、原因調査はスコープ外としている

スコープ外（本ステップの範囲に含めなかったもの）:
- `frontend/src/components/MonitoringView.vue`・`frontend/src/components/TopologyDiagram.vue`（新規） — 設計書の実装ステップ6
- `App.vue`/`Sidebar.vue` の画面切替結線 — 設計書の実装ステップ7
- 手動でのAC-3〜AC-8通し確認 — 描画コンポーネント・画面切替が未実装のため実施不可

## 2026-07-17 — システム構成図モニタリング画面 — Step 6-7（構成図SVG・モニタリング画面・App/Sidebarの結線）

対応する設計書: `docs/design.md`（システム構成図モニタリング画面）の実装ステップ6・7。ステップ1〜5（バックエンド一式、`types/monitoring.ts`・`api/monitoringApi.ts`・`constants/monitoring.ts`・`constants/monitoringLayout.ts`・`stores/monitoringStore.ts`）は前2エントリの通り実装済みで、本ステップでは変更していない。ステップ6の「App.vueで暫定描画して単体確認」は行わず、ステップ7の正式な結線まで一括で実装した（暫定コードの追加・削除の往復を避けるための判断。最終的な成果物は設計書の内容と一致）。

- `frontend/src/components/TopologyDiagram.vue`（新規） — `props: { snapshot: MonitoringSnapshot }`。`computed` の `nodes`/`edges` で `NODE_POSITIONS` に座標が無いID（不整合）は `flatMap` で黙ってスキップ（Risks節の防御的実装方針どおり）。ノードは `<rect>`（枠色=`getMonitoringLevel(Math.max(cpuPercent, memoryPercent))`）＋ラベル＋CPU/MEMそれぞれのミニゲージ（背景`<rect>`＋幅=`value/100`の塗り`<rect>`、塗り色は各値ごとの `getMonitoringLevel`）＋数値`%`ラベルを描画。エッジは `<line>`（色=`getMonitoringLevel(bandwidthPercent)`）＋中間点に白背景`<rect>`＋`%`ラベルの`<text>`を描画。SVGの重なり順は設計書どおり「エッジの`<g>`を先→ノードの`<g>`を後」。色は `normal→emerald-500`、`warning→amber-500`、`danger→rose-500`（`dark:` バリアント併用、`TrendChartView.vue` の `rose-500` と統一感のある配色）を `fill-*`/`stroke-*`のクラスマップとして実装
- `frontend/src/components/MonitoringView.vue`（新規） — `useMonitoringStore()` を使用。`onMounted(() => store.startPolling())` / `onUnmounted(() => store.stopPolling())` でポーリングのライフサイクルを一本化。テンプレートはタイトル「システムモニタリング」・`最終更新: HH:mm:ss`（`computed` の `lastUpdatedLabel` が `store.lastUpdatedAt` を `toLocaleTimeString('ja-JP', { hour12: false, ... })` で整形、`null` 時は `--:--:--`）・エラーバナー（`hasError && snapshot` の場合のみ、直前データの `TopologyDiagram` は表示したまま上部に表示）・初回取得失敗の全面エラー表示（`hasError && !snapshot`）・`TopologyDiagram`（`snapshot` がある場合のみ）を条件描画
- `frontend/src/App.vue`（変更） — `useMonitoringStore()` を追加インポートし、`<main>` 内の `<ChatWindow />` を `<ChatWindow v-if="monitoringStore.activeScreen === 'chat'" /><MonitoringView v-else />` に変更。既存の `useConversationStore`／`onMounted` の会話初期化ロジックは無変更（会話状態は切替で失われない＝FR-1）
- `frontend/src/components/Sidebar.vue`（変更） — `useMonitoringStore()` を追加インポート。既存4ハンドラ（`handleNewChat`/`handleSelect`/`handleReport`/`handleNewInquiry`）の先頭に `monitoringStore.showChat()` を追加（設計書どおり）。新規 `handleShowMonitoring()`（`monitoringStore.showMonitoring()` のみ）を追加し、「新規問い合わせ」ボタンの直後・会話履歴リストの直前に「システムモニタリング」ボタンを新設。選択中スタイルは既存の会話選択中スタイル（`bg-violet-100`/`text-violet-900`＋`dark:`バリアント）に枠線を加えたトーンで表現し、`monitoringStore.activeScreen === 'monitoring'` の場合のみ適用

逸脱:
- 設計書ステップ6は「この時点ではまだサイドバー/App.vueから到達できないため、一時的に `App.vue` で `<MonitoringView />` を直接描画する等して単体で見た目を確認してもよい（後続ステップで正式な切替に置き換える）」とあるが、本タスクの依頼範囲がステップ6〜7を一括で対象としていたため、暫定描画は追加せずステップ7の正式な結線を直接実装した。設計書は「してもよい（任意）」という表現であり必須ではないため、逸脱ではなく許容された代替経路と判断した
- それ以外は設計書「フロントエンド構成」節・「SVGレイアウトの座標方針」節のコード例・仕様をそのまま反映した

スコープ外（本ステップの範囲に含めなかったもの）:
- バックエンドを起動してのブラウザ目視確認（ステップ6検証手順後半・ステップ7手動確認・ステップ8のAC-4〜AC-8通し確認）— タスク指示により「ビルドとタイプチェックまででよい（結合確認はテストフェーズが行う）」とされたため未実施
- 上記以外の既存ファイル（`chatApi.ts`/`chat.ts`/`conversationStore.ts`/`vite.config.ts` 等）は一切変更していない（設計書の「変更なし（明示）」節どおり）

検証結果:
- `cd frontend && npx vue-tsc -b`（lessons-learnedの教訓どおりbareの `--noEmit` は使わず `-b` を使用） → `EXIT_CODE=0`（エラーなし）
- `cd frontend && npm run build`（内部で `vue-tsc -b && vite build`） → 成功、`dist/` 生成（`vite v7.3.6`、`45 modules transformed`、`built in 1.98s`）
- 実行環境の補足: Bash実行環境（Git Bash）のPATHにNode.jsが含まれていなかったため、`/c/Users/miyam/AppData/Local/nvm/v22.14.0` を一時的にPATHへ追加して実行した（プロジェクトファイルの変更ではない。過去エントリと同様の対応。今回は過去エントリに記載の `/c/nvm4w/nodejs` が存在しなかったため別のnvmインストールパスを使用した）

## 2026-07-18 — システム構成図モニタリング画面 — レビュー指摘対応（`docs/review.md` 指摘1・2）

対応する `docs/review.md`: 指摘1（non-blocking correctness、ポーリング停止後の古いfetch応答による状態上書きレース）・指摘2（stylistic、`docs/monitoring-response-schema.md` のフロントエンド実装状況の記述が未来形のまま）。

- `frontend/src/stores/monitoringStore.ts`（変更） — `state` に `fetchGeneration: number`（初期値 `0`）を追加。`startPolling()` の先頭（`fetchSnapshot()` 呼び出し前）で `fetchGeneration` をインクリメントするよう変更。`fetchSnapshot()` は呼び出し開始時点の `fetchGeneration` を `requestGeneration` としてローカルに保持し、`await getMonitoringSnapshot()` の成功後・`catch` 内のいずれでも `requestGeneration !== this.fetchGeneration` なら（＝応答到着までの間に新たな `startPolling()` が呼ばれ世代が進んでいれば）`snapshot`/`lastUpdatedAt`/`hasError` を一切書き換えず早期returnする。`stopPolling()` は元のまま変更していない（`startPolling()` 側の世代インクリメントのみで、レビュー指摘のシナリオ「画面離脱→再訪問」を再現すると新しい世代の `startPolling()` 呼び出し時点で古い世代の応答が確実に破棄されるため十分と判断し、`stopPolling()` 側に追加のインクリメントは入れず最小の変更にとどめた）
- `docs/monitoring-response-schema.md`（変更） — 「フロントエンドの安全なフォールバックについて」節の「`monitoringApi.ts`、実装ステップ4以降で追加予定」を「`monitoringApi.ts`」に修正（未来形の言及を削除）し、「検証する想定である」「フォールバックする想定」をそれぞれ「検証している」「フォールバックする」の現在形に修正して実装済みの事実として記述するよう更新した

逸脱: なし。review.mdの修正案（世代カウンタ方式・ドキュメントの現状記述への更新）どおりに実施した。

検証結果:
- `cd frontend && npx vue-tsc -b`（lessons-learnedの教訓どおりbareの `--noEmit` は使わず `-b` を使用） → `EXIT_CODE=0`（エラーなし）
- `cd frontend && npm run build`（内部で `vue-tsc -b && vite build`） → 成功、`dist/` 生成（`vite v7.3.6`、`45 modules transformed`、`built in 868ms`）
- 実行環境の補足: Bash実行環境（Git Bash）のPATHにNode.jsが含まれていなかったため、`/c/Users/miyam/AppData/Local/nvm/v22.14.0` を一時的にPATHへ追加して実行した（プロジェクトファイルの変更ではない。過去エントリと同様の対応）
- バックエンド変更なしのためMavenテストは再実行していない

スコープ外（本対応の範囲に含めなかったもの）:
- 上記2ファイル以外の変更（依頼範囲外）
- `AbortController` によるfetch自体のキャンセル（review.mdの修正案は世代カウンタ方式とAbortController方式の2案を挙げていたが、既存コードスタイルへの馴染みやすさを優先し前者を採用したため未実施）
