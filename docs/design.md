# 実装設計書: 動的UI生成チャット応答（モック版）

> 本書は `docs/requirements.md`（動的UI生成チャット応答・要件定義書）を正本とし、既存コード（`frontend/src/{types,api,stores,components}`、`backend/src/main/java/com/example/chatbackend/*`）を実読したうえで作成した実装設計書である。前回パイプライン実行分（ChatGPT風試作アプリの初期実装設計）の内容を置き換える。

## Approach

- **応答スキーマの型**: バックエンドは `ChatResponse(reply, components)` を返す。`components` は `UiComponent`（Java 21 sealed interface）の配列で、`TableComponent` / `BarChartComponent` の2実装のみを `permits` する。Jackson の `@JsonTypeInfo(use = Id.NAME, include = As.PROPERTY, property = "type") + @JsonSubTypes` により、レコード自体に `type` フィールドを持たせずシリアライズ時に `"type":"table"` 等を自動付与する。これにより各レコードは純粋にデータフィールドのみを持つ（`ChatRequest`/`ChatResponse` が既にレコードである既存スタイルを踏襲）。
- **モックサービス**: `EchoService` を `MockChatService` に置き換える。`generateResponse(String message)` が入力文にキーワード（「担当」→担当者別シナリオ、「カテゴリ」「種別」→カテゴリ別シナリオ、どちらもマッチしなければ案内テキストのみ）でシナリオを判定し、`ChatResponse` を直接組み立てて返す。バックエンドは引き続きステートレス（インスタンスフィールドを持たず、リクエストごとに完結）。
- **フロントエンドの型と検証境界**: `types/chat.ts` に `TableComponent` / `BarChartComponent` / `UiComponentSpec`（判別共用体）を追加し、`Message` に任意の `components?: UiComponentSpec[]` を追加する。API応答の形は本質的に「将来LLMが返す信頼できないJSON」なので、`chatApi.ts` の `postChat` 内で `fetch` 直後に受信JSON（`unknown`）を検証し、妥当なら `ChatApiResponse` として返し、不正なら専用の `ChatResponseFormatError` を投げる。ストア（`conversationStore.ts`）はこの例外と通信エラー（既存の `fetch` failure / non-2xx）を区別してキャッチし、それぞれ異なる文言の吹き出しを追加する。これにより「解釈できない応答」（FR-7）と「通信エラー」（FR-8）の既存パス・新規パスが明確に分離される。
- **描画コンポーネント**: `TableView.vue`（`<table>` をテキスト補間のみで描画）と `BarChartView.vue`（棒グラフ）を新規作成し、`MessageBubble.vue` が `message.components` を順に振り分けて描画する。すべて `{{ }}` テキスト補間または算出済み数値の `:style` バインディングのみを用い、`v-html` は一切使用しない。
- **グラフ描画方式（重要な決定）**: 外部チャートライブラリは導入せず、`BarChartView.vue` は素の Vue テンプレート＋CSS（flexboxの横棒）でレンダリングする。`frontend/package.json` への依存追加は行わない。理由は下記「Alternatives considered」を参照。
- **JSONスキーマ契約文書**: `docs/chat-response-schema.md` を新規作成し、下記「応答JSONスキーマ（確定仕様）」の内容をベースに、フィールド定義・制約・3シナリオ分のサンプルJSONを記載する。このサンプルJSONは `ChatControllerTest` が検証する実際のレスポンスと一致させる（AC-8）。

## Alternatives considered

- **棒グラフ描画: 手組みCSS/SVG vs. 軽量チャートライブラリ（Chart.js, frappe-charts 等）**: 要件は「静的な棒グラフ・インタラクティブ機能なし・1種類のみ」に限定されており、将来的な拡張（他のチャート種別）は明示的にNon-goal。ライブラリは複数チャート種別・スケール・アニメーション・ツールチップ等を含み、今回使わない機能のためにバンドルサイズと依存（サプライチェーン・XSS観点でのレビュー対象）が増える。素の `<div>`＋`:style="{ width: pct + '%' }"`（`pct` はこちらで計算した数値、レスポンス由来の文字列をそのままCSSに埋め込むことはしない）で要件を過不足なく満たせるため、依存追加ゼロの手組み方式を採用する。将来インタラクティブ機能や折れ線・円グラフが必要になった時点でライブラリ導入を再検討すればよい。
- **Jacksonポリモーフィズム: `As.PROPERTY`（型情報を実行時に注入）vs. `As.EXISTING_PROPERTY`（レコードに`type`フィールドを持たせる）**: `As.EXISTING_PROPERTY` だとレコードのコンストラクタで毎回 `"table"` 等のリテラルを渡す必要があり冗長かつタイプミスの余地がある。バックエンドはレスポンス生成のみ（デシリアライズ不要）なので `As.PROPERTY` でJackson側に型情報注入を任せ、レコードはデータフィールドのみに保つ。
- **表セルの型: 全セル `String` 統一 vs. `String|Number` 混在**: 件数のような数値も表示上は文字列として扱い `rows: List<List<String>>` に統一する。混在型にするとフロント側の検証（各セルが `string` か `number` かを都度判定）が複雑になる一方、静的な表示専用データという用途では得られる利益がない。数値もサービス側で文字列化してから詰める。

## 応答JSONスキーマ（確定仕様・`docs/chat-response-schema.md` に転記する内容）

```
ChatResponse（トップレベル、常にこの形。旧 {"reply": string} 形式は廃止）
{
  "reply": string,                 // 必須。UIコンポーネントの有無に関わらず常に非nullの文字列
  "components": UiComponent[]      // 必須。0件以上の配列。フィールド自体は常に存在し、null にはならない（非マッチ時は []）
}

UiComponent = TableComponent | BarChartComponent   // "type" で判別する共用体。現時点でこの2種類のみ

TableComponent
{
  "type": "table",                 // 固定リテラル
  "columns": string[],             // 列見出し。要素数 >= 1
  "rows": string[][]               // 各要素（行）の長さは必ず columns.length と一致。セル値は表示用文字列（数値も文字列化）
}

BarChartComponent
{
  "type": "bar_chart",              // 固定リテラル
  "title": string,                  // グラフ見出し
  "labels": string[],                // 棒のラベル。要素数 >= 1
  "values": number[]                 // labels と同数。JSON number（本モックでは非負整数のみ使用、スキーマ上は小数も許容）
}
```

### モックデータ（固定・3シナリオ）

- **シナリオA: 担当者別（FR-4a）** — トリガー: メッセージに「担当」を含む
  - `reply`: `"今月の担当者別の問い合わせ件数をまとめました。"`
  - `components[0]` (table): `columns=["担当者","件数"]`, `rows=[["佐藤","12"],["鈴木","9"],["田中","7"],["高橋","5"]]`
  - `components[1]` (bar_chart): `title="担当者別 問い合わせ件数"`, `labels=["佐藤","鈴木","田中","高橋"]`, `values=[12,9,7,5]`
- **シナリオB: カテゴリ別（FR-4b）** — トリガー: メッセージに「カテゴリ」または「種別」を含む
  - `reply`: `"カテゴリ別の問い合わせ件数をまとめました。"`
  - `components[0]` (table): `columns=["カテゴリ","件数"]`, `rows=[["請求","15"],["技術","11"],["アカウント","6"],["その他","4"]]`
  - `components[1]` (bar_chart): `title="カテゴリ別 問い合わせ件数"`, `labels=["請求","技術","アカウント","その他"]`, `values=[15,11,6,4]`
- **シナリオC: 非マッチ（FR-5）**
  - `reply`: `"『今月の問い合わせ件数を担当者別にまとめて』のように聞いてください。表やグラフでお答えします。"`
  - `components`: `[]`

判定順序: 「担当」を含むか → シナリオA。含まなければ「カテゴリ」または「種別」を含むか → シナリオB。どちらも含まなければ → シナリオC。

## フロントエンドの検証・フォールバック方式（FR-7 / AC-5）

- 検証は **メッセージ単位・全体検証**。`components` 配列内の要素を1つでも解釈できなければ、そのメッセージ全体を「解釈できない応答」として扱う（部分的に描画できるコンポーネントだけ表示する、という部分成功は行わない。要件の「解釈できない応答を受け取った場合」という記述と、既存の通信エラー時と同じ「吹き出し1つで代替」というパターンに合わせるため）。
- 検証場所: `chatApi.ts` の `postChat` 内、`fetch` 成功後・`response.json()` 直後。
  - `reply` が `string` でない → 不正
  - `components` が配列でない → 不正
  - `components` の各要素について、`type === 'table'` なら `columns: string[]`（非空）かつ `rows: string[][]`（各行の長さが `columns.length` と一致）を確認、`type === 'bar_chart'` なら `title: string`、`labels: string[]`（非空）、`values: number[]`（`labels` と同数、各要素が有限数）を確認。`type` がこの2値以外、またはいずれの条件も満たさない要素があれば不正
  - 不正と判定した場合は `ChatResponseFormatError`（新規の `Error` サブクラス）を throw する。正常時は検証済みの `ChatApiResponse { reply, components }` を返す
- `conversationStore.ts` の `sendMessage` は `postChat` 呼び出しを try/catch し、`catch` 節で `err instanceof ChatResponseFormatError` を判定する:
  - `true` の場合 → アシスタント吹き出しとして `"応答を表示できませんでした"` を追加（新規、FR-7専用文言）
  - `false`（`fetch` 自体の失敗、`response.ok === false` によるエラー等）の場合 → 既存どおり `"エラー: 応答を取得できませんでした"` を追加（FR-8、退行させない）
- これによりアプリはどちらのケースでも例外を外side（Vueのレンダリングやグローバルハンドラ）へ漏らさず、通常のメッセージ追加と同じ経路でエラー吹き出しを積むため、画面全体は操作可能なまま維持される（AC-5の「画面全体は操作可能なまま維持される」を担保）。
- `MessageBubble.vue` / `TableView.vue` / `BarChartView.vue` 側では追加のスキーマ検証は行わない（ストアに積まれる `components` は既に検証済みという契約にする。二重実装を避けるため）。ただし `MessageBubble.vue` の振り分けは `type` の網羅的な分岐（`table` → `TableView`、`bar_chart` → `BarChartView`、それ以外は何も描画しない）とし、万一未検証データが渡っても描画側でクラッシュしない構造にしておく。

## Files affected

### バックエンド

- `backend/src/main/java/com/example/chatbackend/UiComponent.java`（新規） — `sealed interface UiComponent permits TableComponent, BarChartComponent`。`@JsonTypeInfo(use=Id.NAME, include=As.PROPERTY, property="type")` + `@JsonSubTypes({@JsonSubTypes.Type(value=TableComponent.class, name="table"), @JsonSubTypes.Type(value=BarChartComponent.class, name="bar_chart")})`
- `backend/src/main/java/com/example/chatbackend/TableComponent.java`（新規） — `record TableComponent(List<String> columns, List<List<String>> rows) implements UiComponent {}`
- `backend/src/main/java/com/example/chatbackend/BarChartComponent.java`（新規） — `record BarChartComponent(String title, List<String> labels, List<Double> values) implements UiComponent {}`
- `backend/src/main/java/com/example/chatbackend/ChatResponse.java`（変更） — `record ChatResponse(String reply, List<UiComponent> components) {}` に変更（フィールド追加）
- `backend/src/main/java/com/example/chatbackend/MockChatService.java`（新規、`EchoService.java` を置き換え） — `@Service`。`generateResponse(String message): ChatResponse` を公開。内部にキーワード定数と3シナリオ組み立てのprivateメソッドを持つ
- `backend/src/main/java/com/example/chatbackend/EchoService.java`（削除）
- `backend/src/main/java/com/example/chatbackend/ChatController.java`（変更） — コンストラクタ注入を `EchoService` → `MockChatService` に変更し、`chat()` は `mockChatService.generateResponse(request.message())` をそのまま返す
- `backend/src/test/java/com/example/chatbackend/EchoServiceTest.java`（削除）
- `backend/src/test/java/com/example/chatbackend/MockChatServiceTest.java`（新規） — 3シナリオの単体テスト（後述）
- `backend/src/test/java/com/example/chatbackend/ChatControllerTest.java`（変更） — 旧 `chatReturnsEchoedReply` を撤去し、新スキーマに対応したMockMvcテストへ差し替え（後述）。`chatReturnsBadRequestForBlankMessage` はそのまま維持（AC-9の退行防止）

### フロントエンド

- `frontend/src/types/chat.ts`（変更） — `TableComponent` / `BarChartComponent` / `UiComponentSpec`（判別共用体）を追加し、`Message` に `components?: UiComponentSpec[]` を追加
- `frontend/src/api/chatApi.ts`（変更） — `ChatResponse` インターフェースを `ChatApiResponse { reply: string; components: UiComponentSpec[] }` に拡張、`ChatResponseFormatError` クラスを追加、`postChat` 内で受信JSONを検証する処理を追加
- `frontend/src/stores/conversationStore.ts`（変更） — `createMessage` に第3引数 `components?: UiComponentSpec[]` を追加、`sendMessage` の成功時に `components` をメッセージへ渡す、`catch` を `ChatResponseFormatError` と其の他で分岐
- `frontend/src/components/TableView.vue`（新規） — props: `spec: TableComponent`。`<table><thead><tr><th v-for>...</th></tr></thead><tbody><tr v-for row><td v-for cell>{{ cell }}</td></tr></tbody></table>`
- `frontend/src/components/BarChartView.vue`（新規） — props: `spec: BarChartComponent`。タイトル＋ラベルごとの横棒（`:style` で算出済みwidth%を指定）＋数値表示。最大値0（または空配列）時は幅0%にフォールバック
- `frontend/src/components/MessageBubble.vue`（変更） — 既存のテキスト表示の下に `message.components` を `v-for` で回し、`component.type === 'table'` なら `TableView`、`'bar_chart'` なら `BarChartView` を描画
- `frontend/package.json`（変更なし） — 新規チャートライブラリは追加しない（Approach参照）

### ドキュメント

- `docs/chat-response-schema.md`（新規） — 応答JSONスキーマの契約文書。本設計書の「応答JSONスキーマ（確定仕様）」節の内容＋3シナリオのサンプルJSON（`ChatControllerTest` の実際のレスポンスと一致させる）を記載

## Implementation steps

1. **バックエンド: 応答スキーマの型定義**
   - 対象: `UiComponent.java`（新規）, `TableComponent.java`（新規）, `BarChartComponent.java`（新規）, `ChatResponse.java`（変更）
   - 内容: sealed interface + Jackson の `@JsonTypeInfo`/`@JsonSubTypes` を設定し、`ChatResponse` に `components` フィールドを追加する
   - 確認方法: `./mvnw -q compile`（Windowsは `mvnw.cmd`）が成功すること

2. **バックエンド: モックサービス実装**
   - 対象: `MockChatService.java`（新規）, `EchoService.java`（削除）, `MockChatServiceTest.java`（新規）, `EchoServiceTest.java`（削除）
   - 内容: キーワード判定（「担当」→シナリオA、「カテゴリ」/「種別」→シナリオB、非マッチ→シナリオC）とモックデータ組み立てロジックを実装する。上記「モックデータ」節の値をそのまま使用する
   - 確認方法: `MockChatServiceTest` で以下を検証し green にする — (a) 「今月の問い合わせ件数を担当者別にまとめて」→ `components` に table・bar_chart が1件ずつ含まれ、値がシナリオA通り、(b) 「カテゴリ別の問い合わせ件数を見せて」→ シナリオB通り、(c) 「こんにちは」→ `reply` が案内文・`components` が空配列

3. **バックエンド: コントローラー更新と統合テスト**
   - 対象: `ChatController.java`（変更）, `ChatControllerTest.java`（変更）
   - 内容: `ChatController` の依存を `MockChatService` に差し替える。`ChatControllerTest` に AC-3, AC-4, AC-9 に対応するMockMvcテストを追加・整理する（少なくとも: 担当者系キーワードで200・`components[0].type=="table"`・`components[1].type=="bar_chart"` を `jsonPath` で確認、カテゴリ系キーワードで別データが返る確認、非マッチで `components` が空配列であり `reply` に「エコー」を含まない確認、空白メッセージで400のまま = 既存の `chatReturnsBadRequestForBlankMessage` を維持）
   - 確認方法: `./mvnw test` が全件green。加えて `./mvnw spring-boot:run` 起動状態で `curl -X POST localhost:8080/api/chat -H "Content-Type: application/json" -d "{\"message\":\"担当者別に見せて\"}"` 等を打ち、実レスポンスJSONを目視確認する

4. **応答JSONスキーマ契約文書の作成（AC-8）**
   - 対象: `docs/chat-response-schema.md`（新規）
   - 内容: 本設計書の「応答JSONスキーマ（確定仕様）」節と「モックデータ」節の内容を転記し、フィールド定義表・制約・3シナリオ分のサンプルJSON（ステップ3で実際に得られたレスポンスと一字一句一致させる）を記載する。将来LLMがこの形式で出力する契約であること、`type` 以外の未知フィールドを含む拡張や新しい `type` 追加時はフロント側が安全にフォールバックする前提であることも明記する
   - 確認方法: ステップ3のテスト・curl結果とドキュメント内サンプルJSONを突き合わせ、一致することを目視確認する（AC-8）

5. **フロントエンド: 型定義更新**
   - 対象: `frontend/src/types/chat.ts`（変更）
   - 内容: `TableComponent` / `BarChartComponent` / `UiComponentSpec` を追加し、`Message.components?: UiComponentSpec[]` を追加する
   - 確認方法: `npx vue-tsc --noEmit`（または `npm run build` のtsc部分）がこの時点でエラーなく通ること（他ファイル未変更のため既存コードとの型不整合は起きない想定）

6. **フロントエンド: APIクライアントの検証ロジック実装**
   - 対象: `frontend/src/api/chatApi.ts`（変更）
   - 内容: `ChatApiResponse` 型・`ChatResponseFormatError` クラス・受信JSONの型ガード/検証関数を実装し、`postChat` が検証済みデータを返すか `ChatResponseFormatError` を投げるようにする
   - 確認方法: `npx vue-tsc --noEmit` 通過。加えてバックエンドを起動した状態でブラウザの開発者ツール（Network/Console）から一時的に `postChat('担当者別に見せて')` 相当の呼び出し結果をログ出力する等で、検証後のオブジェクト形状を目視確認する

7. **フロントエンド: ストア更新**
   - 対象: `frontend/src/stores/conversationStore.ts`（変更）
   - 内容: `createMessage` に `components` 引数を追加、`sendMessage` 成功時に渡す、`catch` を `ChatResponseFormatError` とその他で分岐し、それぞれ異なる文言のアシスタント吹き出しを追加する
   - 確認方法: `npx vue-tsc --noEmit` 通過。バックエンド起動状態で実際にメッセージを送信し、`activeConversation.messages` の末尾（Vue devtools等）に `components` が入っていることを確認

8. **フロントエンド: 描画コンポーネント新規作成とMessageBubble組み込み**
   - 対象: `frontend/src/components/TableView.vue`（新規）, `frontend/src/components/BarChartView.vue`（新規）, `frontend/src/components/MessageBubble.vue`（変更）
   - 内容: 上記Approach通りに実装する。`v-html` を使用しないこと、`BarChartView` の幅計算は自前で算出した数値のみを `:style` に渡すことを徹底する
   - 確認方法: フロント・バックエンド双方を起動し、「今月の問い合わせ件数を担当者別にまとめて」を送信して表と棒グラフが吹き出し内に表示されることを目視確認（AC-1の前半）

9. **結合の手動確認一式（AC-1, 2, 3, 5, 6, 7）**
   - 対象: コード変更なし（検証のみ）
   - 内容:
     - AC-1: 「今月の問い合わせ件数を担当者別にまとめて」→ 要約テキスト＋表＋棒グラフ表示を確認
     - AC-2: 「カテゴリ別の問い合わせ件数を見せて」→ AC-1と異なるデータの表＋棒グラフを確認
     - AC-3: 「こんにちは」→ 表・グラフなしの案内テキストのみ、「エコー: 」が含まれないことを確認
     - AC-5: 一時的な手段（例: `MockChatService` の1シナリオを `"type":"pie_chart"` 等の未知typeに書き換えて再起動する、または `chatApi.ts` の検証関数呼び出し箇所に一時的なテスト用不正データを注入する）で、「応答を表示できませんでした」の吹き出しが表示され、他のUI操作（入力・送信・会話切替）が可能なままであることを確認したのち、変更を元に戻す
     - AC-6: バックエンドを停止した状態で送信し、「エラー: 応答を取得できませんでした」が表示されることを確認（既存動作の非退行確認）
     - AC-7: 表・グラフを含む会話から別の会話へSidebarで切り替え、元の会話に戻った際に表・グラフが再描画されることを確認

## Risks / edge cases

- **`type` フィールドの自動注入の検証漏れ**: `As.PROPERTY` 方式はレコードに `type` フィールドが存在しないため、シリアライズ結果に本当に `"type":"table"` 等が出力されるかをステップ3で必ずJSON実物を見て確認すること（ユニットテストの `jsonPath("$.components[0].type")` アサーションで担保する）
- **既存テスト・ファイルの消し忘れ**: `EchoService.java` / `EchoServiceTest.java` を削除し忘れると、コンパイルは通っても「エコー」応答という廃止済み仕様のテストが残存し続け、AC-3（エコー応答が返らないこと）の意図と矛盾する。削除を明示的なステップの一部として実施すること
- **表・グラフの配列長不整合**: `rows` の各行長が `columns.length` と異なる、`labels` と `values` の長さが異なるデータをバックエンドが誤って返した場合、フロントの検証ロジックがこれを不正とみなしエラー吹き出しにフォールバックする（クラッシュはしない）。手動確認の対象ではないが、モックデータ作成時に長さを揃えることを徹底する
- **棒グラフの0除算**: `values` が全て0または空配列の場合に最大値0で除算しないよう、`BarChartView.vue` 側でガードすること
- **`vue-tsc` でのテンプレート内の判別共用体の絞り込み**: `v-for` 内で `component.type === 'table'` による分岐を行う際、SFCの型チェックで正しく `TableComponent` に絞り込まれない場合は、算出プロパティや型ガード関数（`isTableComponent(c): c is TableComponent`）を用意して対応する
- **フロント・バックエンド間の破壊的変更**: `POST /api/chat` のレスポンス形式が非互換に変わるため、フロント・バックエンドの新旧バージョンを混在させて動作確認しないこと（要件の前提通り互換維持は不要だが、手動確認時に両方を最新にしてから起動する）
- **XSS再発防止**: 表セル・グラフのラベル/タイトルは必ずテキスト補間（`{{ }}`）で描画し、`v-html` や `innerHTML` を使わないこと。棒グラフの `:style` に渡す値は自前で計算した数値（0〜100の幅%）のみとし、レスポンス由来の文字列をそのままstyle文字列に埋め込まないこと

## Test strategy

**バックエンド（自動テスト、AC-4・AC-9）**
- 単体テスト（`MockChatServiceTest`）: 担当者系キーワード・カテゴリ系キーワード・非マッチの3パターンで `reply` 文言と `components` の型・件数・値を検証
- 統合テスト（`ChatControllerTest`, MockMvc）: 担当者系キーワード入力で `components` に `table` と `bar_chart` を含む構造化JSONが返ることを `jsonPath` で確認、非マッチ入力で `components` が空配列であることを確認、空白のみメッセージで400が返る既存動作（AC-9）を維持
- `./mvnw test` で全テストgreenを確認

**フロントエンド（手動確認、AC-1・2・3・5・6・7）**
- 実装ステップ9に記載の手順一式を、フロント・バックエンド両方を起動した状態で通しで実施する
- ブラウザ開発者ツールのConsoleでVueのエラー・警告が出ていないこと（特にAC-5確認時）も併せて確認する

**契約文書の整合性確認（手動確認、AC-8）**
- `docs/chat-response-schema.md` のサンプルJSONと、`ChatControllerTest` のアサーション対象JSON・実際のcurl結果を突き合わせ、フィールド名・型・値が一致することを確認する
