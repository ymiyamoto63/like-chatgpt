# テスト検証レポート: 問い合わせ送信前のFAQ提示機能（Issue #2）

検証対象ブランチ: `feature/2-inquiry-faq-suggestion`
検証日: 2026-07-18

## Commands run

```
cd backend && ./mvnw test
cd frontend && npm run build
cd backend && (nohup ./mvnw spring-boot:run &)   # 手動確認用に起動、確認後 pkill -f "spring-boot:run" で終了
curl -X POST -H "Content-Type: application/json" -d '{"message":"..."}' http://localhost:8080/api/chat  # 各シナリオ
```

補足: フロントエンドには自動テストフレームワークが未導入のため、`chatApi.ts` の `isValidFaqListComponent` の検証ロジック（AC-10相当）のみ、esbuildで一時的にNode実行可能な形にバンドルし、`postChat()` に不正な `faq_list`（`titles: []` および数値混入）を含む応答を fetch モックで注入して例外送出を確認した（一時スクリプトはスクラッチパッドに作成し、リポジトリには一切変更を加えていない）。ブラウザ・ヘッドレスブラウザ操作ツールはこの環境に存在しないため、実際のUI目視確認（AC-1〜AC-6, AC-12の見た目・操作感）は実施できなかった。

## Result

- バックエンド: `./mvnw test` → **BUILD SUCCESS**、49件全て green（失敗0、エラー0）。うち `MockChatServiceTest` 22件、`ChatControllerTest` 12件、`SuggestControllerTest` 3件、`MonitoringMetricsServiceTest` 3件、`MockSuggestServiceTest` 7件、`MonitoringControllerTest` 1件、`ChatBackendApplicationTests` 1件。
- フロントエンド: `npm run build`（`vue-tsc -b && vite build`）→ 型エラー・ビルドエラーなく成功（`✓ built in 782ms`）。
- 手動 `curl` 検証: AC-1, AC-2, AC-3, AC-5, AC-7(a)〜(d), AC-8（4カテゴリ）, AC-9（既存レポート・アラート回帰なし）, AC-11（サンプルJSON一致）に対応する全シナリオで期待どおりの応答を確認。
- ブラウザでの目視確認（AC-6前半・後半の一部, AC-12の一部）は環境制約により未実施。詳細は下記ACカバレッジ表参照。

総合判定: **自動テスト（AC-7, AC-8, AC-9の自動テスト部分）はPASS。手動確認が必要なACのうち、curlで検証可能な範囲はPASS。ブラウザ目視が必要な部分は未検証（実施不可）。バグ・失敗は検出されなかった。**

## Failures

なし。バックエンドの自動テスト、フロントエンドのビルド、手動curl検証のいずれにおいても失敗・不一致は検出されなかった。

## Acceptance criteria coverage

| AC | 内容（要約） | 検証方法（doc指定） | 実施内容 | 結果 |
|---|---|---|---|---|
| AC-1 | 累積文送信でFAQ提示ステップ（3件リンク＋2択）が表示される | 手動確認 | curlで `カテゴリ: 請求 / 緊急度: 高 / 内容: 請求額が二重になっている` を送信し、`faq_list`(3件) + `choices(["解決した","解決しないので問い合わせる"])` が返ることを確認（APIレベル）。フロントの実描画（リンク風表示等）はブラウザツール不在のため未確認。ソースコード（`FaqListView.vue`）の描画ロジックは目視レビュー済み | **部分PASS**（APIレベル確認済み、UI描画の実ブラウザ目視は未実施） |
| AC-2 | FAQリンククリックでFAQ詳細＋一覧＋2択が再掲される | 手動確認 | curlで `FAQ: 請求額が二重に表示される場合` を送信し、FAQ本文＋同カテゴリ`faq_list`一覧再掲＋`choices`が返ることを確認（APIレベル）。実際のクリック操作によるUI遷移は未確認 | **部分PASS** |
| AC-3 | FAQ詳細後、別FAQを続けて閲覧できる | 手動確認 | 上記AC-2応答に一覧が再掲されること（同カテゴリ3件）をcurlで確認。別タイトルでの`FAQ: <タイトル>`再送も同様に正しく詳細を返すことを確認（`ChatControllerTest`/`MockChatServiceTest`で他タイトルも検証済み） | **PASS**（API/ロジックレベル） |
| AC-4 | 「解決した」→終了メッセージ・通常チャット継続 | 手動確認 | curlで `解決した` を送信し、終了メッセージ＋`components: []`を確認。`components`が空のため既存の`hasChoices`ロジックにより入力欄が有効化される設計（ソースコードレビューで確認）。実際のブラウザでの入力欄有効化・継続入力の目視は未実施 | **部分PASS** |
| AC-5 | 「解決しないので問い合わせる」→確認ステップ→登録→完了 | 手動確認 | curlで `解決しないので問い合わせる / カテゴリ: 請求 / 緊急度: 高 / 内容: 請求額が二重になっている` を送信し、既存の確認応答（`table`+`choices(["登録する","やり直す","キャンセル"])`）と一致することを確認。続けて `登録する` を送信し受付番号つき完了メッセージ（`INQ-0001`）を確認 | **PASS**（API/ロジックレベル） |
| AC-6 | FAQ提示中の入力欄・送信ボタン無効化、過去発言のFAQリンク・選択肢の無効化表示 | 手動確認 | ソースコードレビュー: `conversationStore.ts`の`isInputLocked`は`hasChoices`のみで判定（変更なし）、`faq_list`は必ず`choices`と同居するため設計上入力欄無効化は機能するはず。`MessageBubble.vue`は`FaqListView`にも`:disabled="!choicesEnabled"`を渡している。ただし実際のブラウザでの見た目・操作感（無効化スタイル、クリック不能であること）は未確認 | **未検証（実施不可）** — ブラウザ操作ツールがこの環境にないため。手動検証手順: `npm run dev`と`./mvnw spring-boot:run`を起動し、ブラウザで新規問い合わせ→カテゴリ→緊急度→内容入力を実施しFAQ提示画面を表示、(1)テキスト入力欄・送信ボタンがdisabled状態であること、(2)FAQリンクをクリックして次のメッセージを表示させた後、直前（最新でなくなった）発言内のFAQリンク・選択肢ボタンが視覚的に無効化表示（薄い色・下線消失）されクリックしても反応しないことを目視・操作確認する |
| AC-7 | `POST /api/chat`統合テストで(a)〜(d)が契約どおり返る | 自動テスト（MockMvc） | `./mvnw test`実行、`ChatControllerTest`の`chatReturnsFaqPresentationForInquirySummary`(a)、`chatReturnsFaqDetailWithFaqListAndChoicesForFaqAnswer`(b)、`chatReturnsConfirmationForFaqUnresolvedWithSummary`(c)、`chatReturnsCompletionMessageForFaqResolved`(d)が全てgreenであることを確認。加えてcurlでも同内容を再現し一致を確認 | **PASS** |
| AC-8 | 4カテゴリすべてでFAQ3件が返る | 自動テスト | `MockChatServiceTest`の`inquirySummaryReturnsFaqPresentationWithFaqListAndChoices`（請求）、`inquirySummaryForTechnicalCategoryReturnsTechnicalFaqs`（技術）、`inquirySummaryForAccountCategoryReturnsAccountFaqs`（アカウント）、`inquirySummaryForOtherCategoryReturnsOtherFaqs`（その他）が全てgreen。curlでも4カテゴリ全てで3件ずつ返ることを確認 | **PASS** |
| AC-9 | 既存レポート・アラート・ウィザード他ステップへの誤マッチ・退行なし | 自動テスト＋手動確認 | `./mvnw test`で既存の`assigneeKeywordReturnsAssigneeScenario`、`categoryKeywordReturnsCategoryScenario`、`typeKeywordAlsoReturnsCategoryScenario`、`dailyKeywordReturnsDailyTrendScenario`、`cpuAlertCauseQuestionReturnsCpuCauseAnalysis`等のアラート系、ウィザードの各ステップ（カテゴリ/緊急度/キャンセル/登録/やり直す）のテストが全てgreen。加えてcurlで担当者別・カテゴリ別・日別・CPUアラート・閉じるの各応答が変更前と同じ内容で返ることを実機確認 | **PASS** |
| AC-10 | 不正な`faq_list`（タイトル0件等）はエラー吹き出しにフォールバックしクラッシュしない | 自動テストまたは手動確認 | フロントに自動テストフレームワーク未導入のため、`chatApi.ts`をesbuildで一時バンドルし、`postChat()`に(1)`titles: []`、(2)`titles`に数値混入、を含む応答を注入して`ChatResponseFormatError`が送出されることを実行確認。さらに`conversationStore.ts`の`dispatchUserMessage`で該当エラーを`catch`し「応答を表示できませんでした」という吹き出しに変換する実装をソースコードで確認（クラッシュせず`try/catch`で握りつぶす設計）。実際のブラウザでエラー吹き出しが表示されアプリがクラッシュしないことの目視確認は未実施 | **部分PASS**（検証ロジックの実行確認は実施、実ブラウザでの表示確認は未実施） |
| AC-11 | 契約文書のFAQ定義・サンプルが実応答と一致 | 手動確認（curl比較） | `docs/chat-response-schema.md`に記載の4サンプルJSON（FAQ提示応答／FAQ詳細応答／解決応答／確認応答）と、curlで取得した実際のレスポンスJSONを突き合わせ、全て一字一句一致することを確認 | **PASS** |
| AC-12 | リロード・会話切替後もFAQリンク／未解決フローが継続できる | 手動確認 | ソースコードレビュー: `findLastInquirySummary`は`conversation.messages`（`conversationPersistence.ts`経由でlocalStorageに永続化）から直近の累積文を復元する設計であることを確認（`localStorage.setItem`/`getItem`の実装箇所を確認済み）。バックエンドはステートレスのため、リロード後も同じメッセージを再送すれば同じ応答が返ることをAPIレベルで論理的に確認。ただし実際にブラウザでリロード・会話切替操作を行い、FAQリンククリックや「解決しないので問い合わせる」が継続動作することの目視確認は未実施 | **未検証（実施不可）** — 手動検証手順: ブラウザでFAQ提示ステップまで進めた状態でページをリロード（またはサイドバーで別会話に切り替えてから再度戻る）し、(1)会話履歴にFAQ提示メッセージが残っていること、(2)最新のFAQ提示発言内のFAQリンクをクリックしてFAQ詳細が正しく表示されること、(3)「解決しないので問い合わせる」を押して確認ステップに正しく合流する（要約表の内容がリロード前と一致する）ことを確認する |

## 補足・所見

- 実装ノート（`docs/2/implementation-notes.md`）に記載された内容と実際のソースコード・テスト内容は一致しており、記録に誇張や虚偽は見られなかった。
- design.md の「AC mapping」節どおり、AC-7・AC-8はバックエンド自動テストで、AC-9は既存＋新規の自動テストで裏付けられている。
- ブラウザ操作ツールがサンドボックス環境に存在しないため、AC-1・AC-2・AC-4・AC-6・AC-10・AC-12のうち「実際の画面表示・クリック操作」に関わる部分は検証できていない。これは実装側の問題ではなく検証環境の制約であるが、リリース判断のためには別途人間による実機（ブラウザ）確認が必要である。
- 発見したバグ・不具合は無し。修正は不要。
