# テストレポート — チャット応答への「ダッシュボード応答」追加

対象要件: `docs/pipeline/6/requirements.md`（AC-1〜AC-15）
対象設計書: `docs/pipeline/6/design.md`
対象実装ノート: `docs/pipeline/6/implementation-notes.md`
検証日: 2026-07-20

## Commands run

1. `cd backend && ./mvnw test`
2. `cd frontend && npx vue-tsc -b`
3. `cd frontend && npm run build`
4. `cd backend && nohup ./mvnw -q spring-boot:run -Dspring-boot.run.jvmArguments="-Dserver.port=8099" > <scratch>/backend-8099.log 2>&1 &`（自分専用インスタンス起動。port 8080は既存プロセス pid 364530 が保持しており触っていない）
5. `curl -s -X POST http://localhost:8099/api/chat -H 'Content-Type: application/json' -d '{"message": "..."}'`（下記シナリオ各種）
6. 検証後、自分が起動した port 8099 のプロセス（wrapper pid 451528, 実プロセス pid 451711）を `kill` で停止（port 8080 の既存プロセスには一切手を加えていない）
7. `frontend/src/api/chatApi.ts`・`frontend/src/components/dynamic-ui/StatCardsView.vue`・`frontend/src/components/dynamic-ui/DonutChartView.vue`・`frontend/src/components/chat/MessageBubble.vue`・`frontend/src/stores/conversationStore.ts`・`backend/src/main/java/.../domain/chat/component/UiComponent.java` の静的確認（Read）
8. `docs/api/chat-response-schema.md` とAC-4/curlレスポンスの内容比較

## Result

**全体: PASS（自動テスト・API検証の範囲）。手動確認（ブラウザ操作）が必要な項目11件は本レポートに手順を明記し、コード上の根拠は確認済みだが実ブラウザでの目視確認は未実施。**

- backend `./mvnw test`: **PASS**（`Tests run: 55, Failures: 0, Errors: 0, Skipped: 0`。`ChatControllerTest` 14件（既存12＋新規2）すべてパス）
- frontend `npx vue-tsc -b`: **PASS**（型エラーなし、exit code 0）
- frontend `npm run build`: **PASS**（`vue-tsc -b && vite build` 成功、`dist/` 出力完了）
- API応答検証（curl、port 8099）: **PASS**（ダッシュボードシナリオ・既存6シナリオとも期待どおり）
- スキーマ文書とレスポンスの一致確認: **PASS**（`trend_chart.labels` の日付以外は完全一致。日付は既存シナリオCと同じ「確認時点の固定サンプル」慣例）
- 失敗・回帰: **なし**
- `git status`: クリーン（自分の変更なし。`docs/pipeline/6/pipeline-state.md` は本検証以前からの差分で、テスト実施による変更ではない）

## Failures

なし。

## Acceptance criteria coverage

| AC | 内容（要約） | 検証方法（指定） | 実施内容 | 判定 |
|---|---|---|---|---|
| AC-1 | 「今月のサマリーを見せて」で3コンポーネント（stat_cards→donut_chart→trend_chart）が1吹き出しに表示される | 手動確認 | API側根拠: curl（port 8099）で`components`が`[stat_cards, donut_chart, trend_chart]`の順で返ることを確認済み（下記「API検証詳細」参照）。ブラウザでの実際の吹き出し内表示（1つの応答として並ぶ見た目）は未実施。**手動確認手順**: `npm run dev`（port 5173）＋`./mvnw spring-boot:run`（port 8080、既存プロセスがあれば停止して起動し直す）を起動し、チャット欄に「今月のサマリーを見せて」を送信。応答吹き出し内にKPIカード4枚・ドーナツチャート・トレンドチャートが縦に並んで表示され、崩れがないことを目視確認する。 | 手動確認要（API根拠はPASS） |
| AC-2 | 「ダッシュボード」を含む別言い回しでも同様の応答 | 自動テスト＋手動確認 | 自動テスト: `ChatControllerTest.chatReturnsDashboardScenarioForDashboardKeyword`が`./mvnw test`でPASS。API検証: curlで「ダッシュボードを表示して」への応答が「今月のサマリーを見せて」と同一構造・内容であることを確認済み（下記参照）。画面表示は上記AC-1と同じ手動確認手順で代替。 | 自動テストPASS／画面表示は手動確認要 |
| AC-3 | 既存シナリオA〜C（担当・カテゴリ・種別・日別）の応答内容が変わらない | 自動テスト＋手動確認 | 自動テスト: `./mvnw test`で既存`ChatControllerTest`全件（担当者別・カテゴリ別・日別含む）回帰なくPASS。curl再確認: 「担当者別の問い合わせ件数を教えて」「カテゴリ別の内訳を教えて」「問い合わせ種別の内訳を教えて」「日別の推移を教えて」の4文で応答`reply`・`components[].type`・`average`（6.4）が期待どおり（下記参照）。画面表示の目視は手動確認手順（AC-1と同様の起動手順で該当文言を送信）。 | 自動テストPASS／画面表示は手動確認要 |
| AC-4 | 統合テストで`stat_cards`/`donut_chart`の必須フィールドと`delta`存在を確認 | 自動テスト（MockMvc） | `ChatControllerTest`の2新規テストが`components[0].type=="stat_cards"`、`cards[0].delta`存在、`cards[3].delta`が`doesNotExist()`（省略）、`components[1].type=="donut_chart"`、`labels`/`values`件数一致、`components[2].type=="trend_chart"`を検証しPASS。 | PASS |
| AC-5 | 新規問い合わせフロー・アラート応答が変わらない | 自動テスト＋手動確認 | 自動テスト: `./mvnw test`で既存フロー系テスト（新規問い合わせ各ステップ、FAQ、解決/未解決分岐、バリデーション）が回帰なくPASS。curl再確認: 「新規問い合わせ」→「問い合わせのカテゴリを選んでください」(`choices`)、「カテゴリ: 請求」→「緊急度を選んでください」(`choices`)、「CPU使用率が高い原因を教えて」→アラート応答（`table`,`bar_chart`）が本変更前と同内容であることを確認（下記参照）。画面表示の目視は手動確認手順。 | 自動テストPASS／画面表示は手動確認要 |
| AC-6 | 未知typeでエラーフォールバック | 手動確認（一時改変で再現） | 一時的なコード改変は実施せず、静的確認で代替した。`chatApi.ts`の`isValidUiComponentSpec`は`type`が`table`/`bar_chart`/`choices`/`trend_chart`/`faq_list`/`stat_cards`/`donut_chart`の7値以外なら`return false`（139行目末尾のデフォルトreturn）。`validateChatApiResponse`は`components.every(isValidUiComponentSpec)`が`false`なら`ChatResponseFormatError`を`throw`（chatApi.ts:148-150）。`conversationStore.ts`の`dispatchUserMessage`はこの例外を`catch`し`'応答を表示できませんでした'`のメッセージを追加（164-167行目、components省略＝空表示）。MessageBubbleに渡る前に弾かれるためクラッシュ経路がないことをコードトレースで確認。**手動確認手順（未実施）**: 一時的に`chatApi.ts`の`isValidUiComponentSpec`冒頭に`if (record.type === 'stat_cards') return false`等を差し込み未知type受信を模擬→ブラウザで「今月のサマリーを見せて」を送信→吹き出しに「応答を表示できませんでした」が表示されクラッシュしないことを確認→改変をrevert（`git diff`で差分なしを確認）。 | 静的確認PASS／実ブラウザでのクラッシュ非発生の目視は手動確認要 |
| AC-7 | 必須フィールド不足（`cards`0件、`values`件数不一致等）でエラーフォールバック | 手動確認（一時改変で再現） | 静的確認: `isValidStatCardsComponent`は`cards.length > 0`を必須とし0件なら`false`（chatApi.ts:88-91）。`isValidDonutChartComponent`は`values.length !== labels.length`なら`false`（chatApi.ts:101-102、`isValidBarChartComponent`と同型）。いずれもAC-6と同じ経路で`ChatResponseFormatError`→エラーフォールバックにつながることをコードトレースで確認。**手動確認手順（未実施）**: バックエンドの`dashboardScenario()`を一時的に`cards`を空配列に、または`donut_chart`の`values`を`labels`より1件少ない配列に差し替え→ブラウザで送信→エラーフォールバック表示を確認→revert。 | 静的確認PASS／実ブラウザ確認は手動確認要 |
| AC-8 | `donut_chart`の`values`合計0でもクラッシュ・表示崩れなし | 手動確認（一時改変で再現） | 静的確認・机上トレース: `DonutChartView.vue`の`segments` computed（26-43行目）は`ratio = total.value > 0 ? value / total.value : 0`（ゼロ除算を三項演算子でガード）、`total===0`なら全`ratio=0`→`segmentLength=0`→`dasharray="0 251.32..."`（`CIRCUMFERENCE = 2*Math.PI*40`）となり色付きセグメントは描画されず下地グレーリングのみ表示される。三角関数を経由しない`stroke-dasharray`方式のため`NaN`座標が発生する経路が存在しないことをコードから確認（設計書の想定どおり）。凡例側も`Math.round(0*100)=0`で`0%`表示となり例外は発生しない。**手動確認手順（未実施）**: バックエンドの`dashboardScenario()`内`DonutChartComponent`の`values`を一時的に`[0.0, 0.0, 0.0, 0.0]`に差し替え→ブラウザで送信→ドーナツが（色付きセグメントなしで）グレーの下地リングのみ・凡例が全て0%で表示されクラッシュ・崩れがないことを確認→revert。 | 静的確認PASS／実ブラウザ確認は手動確認要 |
| AC-9 | `npx vue-tsc -b`・`npm run build`成功 | 自動テスト | 両コマンドともexit code 0でPASS（詳細は上記「Result」参照）。 | PASS |
| AC-10 | `./mvnw test`全件成功 | 自動テスト | `Tests run: 55, Failures: 0, Errors: 0, Skipped: 0`でPASS。 | PASS |
| AC-11 | スキーマ文書に新2type定義・サンプル・トリガー・判定順序が記載され実レスポンスと一致 | 手動確認 | `docs/api/chat-response-schema.md`のシナリオGサンプルJSON（267-295行目）と、curl実レスポンス（port 8099、「今月のサマリーを見せて」「ダッシュボードを表示して」いずれも同一）を突き合わせ、`reply`文言・`stat_cards.cards`全4件（`label`/`value`/`delta`の値と`cards[3]`の`delta`省略）・`donut_chart`の`title`/`labels`/`values`・`trend_chart`の`title`/`values`/`average`(6.4)が完全一致することを確認した。`trend_chart.labels`のみ、文書は`["7/2",...,"7/15"]`という固定サンプル値、実レスポンス（確認日2026-07-20基準）は`["7/7",...,"7/20"]`と日付が異なるが、これは既存シナリオC節も同様に過去確認時点の日付のまま据え置く既存ドキュメント慣例（実装ノート記載どおり）であり齟齬ではない。判定順序表（310行目）・フィールド定義表（131-182行目）・「7値以外の場合」への更新（464行目）も内容と整合していることを確認。 | PASS |
| AC-12 | `delta`が`+`始まりで緑・上矢印 | 手動確認 | 静的確認: `StatCardsView.vue`の`deltaTrend`（9-20行目）は`delta.startsWith('+')`で`'up'`を返し、テンプレート（43-55行目）は`trend==='up'`のとき`text-emerald-600 dark:text-emerald-400`クラス＋`▲`記号を表示。APIレスポンスにも`cards[0]`に`"delta": "+12%"`が実在することをcurlで確認済み。**手動確認手順（未実施）**: ブラウザで「今月のサマリーを見せて」を送信し、1枚目のKPIカード（新規問い合わせ件数）が緑色文字＋`▲ +12%`で表示されることを目視確認する。 | 静的確認PASS／目視は手動確認要 |
| AC-13 | `delta`が`-`始まりで赤・下矢印 | 手動確認 | 静的確認: 同ファイルで`delta.startsWith('-')`は`'down'`を返し、`text-rose-600 dark:text-rose-400`＋`▼`を表示。APIレスポンスの`cards[1]`に`"delta": "-8%"`が実在。**手動確認手順（未実施）**: 2枚目のKPIカード（平均初回応答時間）が赤色文字＋`▼ -8%`で表示されることを目視確認する。 | 静的確認PASS／目視は手動確認要 |
| AC-14 | `delta`が`+`/`-`以外、または省略でプレーン表示 | 手動確認 | 静的確認: `+`/`-`いずれでもない場合は`'flat'`を返し矢印記号を出さず`text-zinc-500 dark:text-zinc-400`のプレーン表示（テンプレート52-53行目のv-if/v-else-ifに`'flat'`分岐がないため矢印なし）。`delta`が`undefined`/`null`の場合は`deltaTrend`が`null`を返し、`v-if="card.trend"`によりdelta行自体が非表示（矢印・色・文字列いずれも出ない）。APIレスポンスの`cards[2]`（`"delta": "±0pt"`）・`cards[3]`（`delta`キー自体なし）が実在しこの2パターンをカバー。**手動確認手順（未実施）**: 3枚目（顧客満足度）が矢印なしグレー文字で`±0pt`と表示され、4枚目（解決率）はdelta行自体が表示されないことを目視確認する。 | 静的確認PASS／目視は手動確認要 |
| AC-15 | delta付き/なしカード混在でもクラッシュせず両方正しく表示 | 手動確認 | 静的確認: `cards` computed（22-27行目）は`props.spec.cards.map(...)`で各カードを独立して処理するため、1レスポンス内の4枚（up/down/flat/欠落の混在）を単一のv-forループで問題なく描画できる（例外を投げる分岐がない）。実際のAPIレスポンスがまさにこの4パターン混在（AC-12〜14参照）であることをcurlで確認済み。**手動確認手順（未実施）**: 「今月のサマリーを見せて」の応答で4枚のカードが全てクラッシュなく、それぞれAC-12〜14どおりの見た目で並んで表示されることを目視確認する。 | 静的確認PASS／目視は手動確認要 |

## API検証詳細（curl、port 8099）

### ダッシュボードシナリオ（AC-1, 2, 4, 11 のAPI側根拠）

- `POST /api/chat {"message":"今月のサマリーを見せて"}` と `{"message":"ダッシュボードを表示して"}` は同一の応答を返す。
- `reply`: `"今月のサマリーです。主要な指標とカテゴリ内訳、日別の推移をまとめました。"`
- `components[0]`: `type=stat_cards`、`cards`は4件（`+12%`/`-8%`/`±0pt`/delta省略の順、値は設計書どおり）
- `components[1]`: `type=donut_chart`、`title=カテゴリ別内訳`、`labels=[請求,技術,アカウント,その他]`、`values=[58.0,44.0,25.0,15.0]`
- `components[2]`: `type=trend_chart`、`title=日別問い合わせ件数（直近14日間）`、`average=6.4`

### 既存シナリオの非回帰確認（AC-3, 5）

| 入力 | `reply` | `components[].type` |
|---|---|---|
| 担当者別の問い合わせ件数を教えて | 今月の担当者別の問い合わせ件数をまとめました。 | table, bar_chart |
| カテゴリ別の内訳を教えて | カテゴリ別の問い合わせ件数をまとめました。 | table, bar_chart |
| 問い合わせ種別の内訳を教えて | カテゴリ別の問い合わせ件数をまとめました。 | table, bar_chart |
| 日別の推移を教えて | 直近14日間の日別問い合わせ件数をまとめました。平均は1日あたり約6.4件です。 | trend_chart（average=6.4） |
| こんにちは | 『今月の問い合わせ件数を担当者別にまとめて』のように聞いてください。表やグラフでお答えします。 | （空） |
| 新規問い合わせ | 問い合わせのカテゴリを選んでください。 | choices |
| カテゴリ: 請求 | 緊急度を選んでください。 | choices |
| CPU使用率が高い原因を教えて | 直近のCPU使用率上昇の要因を分析しました。バッチ処理プロセスの負荷が主な要因です。 | table, bar_chart |
| 今日の天気は？（非該当フォールバック） | 『今月の問い合わせ件数を担当者別にまとめて』のように聞いてください。表やグラフでお答えします。 | （空） |

いずれも本変更前の既存挙動（`ChatControllerTest`の既存アサーション内容）と一致しており、回帰なし。

## 手動確認が必要な項目のまとめ（ブラウザ操作）

AC-1, AC-2（画面表示）, AC-3, AC-5（画面表示）, AC-6, AC-7, AC-8, AC-12, AC-13, AC-14, AC-15 の11項目は、ブラウザでの実際の描画・一時改変によるエラーフォールバック確認を伴うため、本セッションのツール（Bash/curl/静的読み取り）では実施できなかった。上表の各行に具体的な手動確認手順を記載した。いずれもAPI応答の内容・検証ロジック・描画ロジックの静的確認（コードトレース）では期待どおりの実装になっていることを確認済みであり、実装上の懸念は見当たらない。

## 補足: port 8080 の既存プロセスについて

検証開始時点で port 8080 は既存の `java` プロセス（pid 364530）が保持していた。`pipeline-config.md`の指示どおりこのプロセスには一切触れず、自分の検証は `-Dserver.port=8099` で起動した専用インスタンスで実施した。検証完了後、起動した2プロセス（maven wrapper pid 451528、実行時javaプロセス pid 451711）を `kill` で停止し、port 8099 が解放されたことを確認済み。port 8080 の既存プロセスは検証前後で変化なし。
