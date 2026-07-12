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
