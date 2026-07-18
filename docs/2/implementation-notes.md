# 実装ノート

## ステップ1-3: バックエンド（スキーマ拡張・フロー変更・テスト）

design.md の「実装ステップ」1〜3（バックエンド全体）を実装した。設計からの逸脱はなし。

### 変更ファイル

- `backend/src/main/java/com/example/chatbackend/FaqListComponent.java`（新規）: `FaqListComponent(List<String> titles) implements UiComponent` を追加。design.md のとおり。
- `backend/src/main/java/com/example/chatbackend/UiComponent.java`（変更）: `@JsonSubTypes` に `faq_list` → `FaqListComponent` を追加し、`permits` にも `FaqListComponent` を追加。
- `backend/src/main/java/com/example/chatbackend/MockChatService.java`（変更）:
  - 新規定数 `FAQ_ANSWER_PREFIX`（`"FAQ: "`）、`FAQ_RESOLVED_LABEL`（`"解決した"`）、`FAQ_UNRESOLVED_LABEL`（`"解決しないので問い合わせる"`）、`FAQ_UNRESOLVED_PREFIX` を追加。
  - `FaqEntry` レコード、`FAQ_ENTRIES`（請求／技術／アカウント／その他 × 各3件、計12件、タイトルは全て重複なし）、`FAQ_BY_TITLE`、`FAQ_TITLES_BY_CATEGORY` を追加。FAQ本文は design.md のプレースホルダ（`"..."`）ではなく、問い合わせ管理業務として自然な日本語の説明文を新規に作成した。
  - `inquiryConfirmation(String message)` 内にあったパースロジックを `parseInquirySummary(String message)` + `InquirySummary` レコードに切り出し。既存の表・選択肢の内容（列名・行・選択肢文言）は変更していない。`parseInquirySummary` は「解決しないので問い合わせる / ...」経由の呼び出しにも対応できるよう、`CATEGORY_ANSWER_PREFIX` で始まらない場合は `null` を返すガードを明示的に追加した（design.md の「message が...形式でなければ null を返す」という記述に沿った実装判断であり、逸脱ではない）。
  - `inquiryFlowResponse` の分岐順序を design.md のとおりに変更（`FAQ_ANSWER_PREFIX` → `FAQ_RESOLVED_LABEL` → `FAQ_UNRESOLVED_PREFIX` → `CATEGORY_ANSWER_PREFIX`(→ FAQ提示) → 以降既存どおり）。
  - `faqPresentation(InquirySummary summary)`、`faqDetailResponse(String message)`、`faqResolvedCompletion()` を新規追加。`inquiryConfirmation` は `InquirySummary` を受け取るシグネチャに変更。
- `backend/src/test/java/com/example/chatbackend/MockChatServiceTest.java`（変更）:
  - 既存の `inquirySummaryReturnsConfirmationWithTableAndChoices` を `inquirySummaryReturnsFaqPresentationWithFaqListAndChoices`（FAQ提示検証）に置き換え。
  - 新規テスト追加: 技術／アカウント／その他の3カテゴリそれぞれの累積文→FAQ3件検証（請求と合わせて4カテゴリ全て検証、AC-8）、FAQ詳細応答（`FAQ: <タイトル>` → 本文＋一覧再掲＋選択肢）、「解決した」→終了メッセージ・空components、「解決しないので問い合わせる」＋累積内容→既存の確認ステップ（要約表＋登録/やり直す/キャンセル）。
  - 既存の他のテスト（アサイニー別・カテゴリ別・日別・ウィザードの各ステップ・アラート系）は変更せず、全て緑であることを確認済み。
- `backend/src/test/java/com/example/chatbackend/ChatControllerTest.java`（変更）:
  - 既存の `chatReturnsConfirmationForInquirySummary` を `chatReturnsFaqPresentationForInquirySummary`（FAQ提示ステップの統合テスト）に置き換え。
  - design.md の「応答シナリオ（4パターン、AC-7）」を検証する統合テストを追加: `chatReturnsFaqDetailWithFaqListAndChoicesForFaqAnswer`（(b)）、`chatReturnsCompletionMessageForFaqResolved`（(d)）、`chatReturnsConfirmationForFaqUnresolvedWithSummary`（(c)）。(a) は置き換え後の `chatReturnsFaqPresentationForInquirySummary` が担う。
  - 既存の他のテストは変更していない。

### 実施したテスト・実行結果

- `cd backend && ./mvnw test` を実行し、全49件（`MockChatServiceTest` 22件、`ChatControllerTest` 12件を含む）が `BUILD SUCCESS` で緑であることを確認した。

### スコープ外（未実施）

- design.md の実装ステップ4以降（`docs/chat-response-schema.md` 更新、フロントエンド一式）は本タスクのスコープ外のため未実施。

## ステップ4: chat-response-schema.md更新

design.md の「実装ステップ」4（`docs/chat-response-schema.md` 更新）を実施した。設計からの逸脱はなし。

### 変更ファイル

- `docs/chat-response-schema.md`（変更）:
  - `UiComponent`（判別共用体）の説明文を「4種類のみ」→「5種類のみ」に更新（`table`・`bar_chart`・`choices`・`trend_chart`・`faq_list`）。
  - `TrendChartComponent` の直後に新規 `FaqListComponent`（`"type": "faq_list"`）のセクションを追加。design.md の「API contract」節のフィールド定義（`type`/`titles`）表とJSON構造例、および `faq_list` と `choices` の同居保証の説明を、既存コンポーネント（`TableComponent`等）と同じ記述パターンで追記した。
  - シナリオE（新規問い合わせフロー）を更新。「内容入力後の累積文への応答」が以前は確認ステップに直結していた旨、現在はFAQ提示ステップに差し替わっている旨を説明文に明記し、受信メッセージ／応答の表に design.md の「応答シナリオ（4パターン、AC-7対応）」表の内容（累積文→FAQ提示、`FAQ: <タイトル>`→FAQ詳細、`解決した`→終了、`解決しないので問い合わせる / <累積文>`→確認）をそのまま反映した。あわせて「FAQ提示応答」「FAQ詳細応答」「解決応答」「確認応答」の4つのサンプルJSONセクションを追加（既存の「確認応答」セクションのトリガー文言は `解決しないので問い合わせる / カテゴリ: 請求 / 緊急度: 高 / 内容: 請求額が二重になっている` に更新）。
  - シナリオDの判定順序の説明文（「新規問い合わせフローの定型文（シナリオE群）」を最優先で判定する旨）は既存の記述のまま維持（FAQ関連の新定型文もすべて `inquiryFlowResponse` 内で判定されるため、判定順序の記述自体に変更は不要と判断）。
  - フロントエンドの安全なフォールバック節の検証項目一覧に `faq_list` なら `titles`（1件以上）を追加し、「4値以外」→「5値以外」に更新。
  - なお design.md の Files affected 表では「シナリオEの表・サンプルJSON更新」と書かれていたため、当初検討した独立の「シナリオE'」セクションは採用せず、既存のシナリオEセクション内に統合する形で追記した（デザイン文書の意図に忠実な構成とするため）。

### curl検証結果

- バックエンドを実際に起動（`cd backend && ./mvnw spring-boot:run`）し、`curl -s -X POST -H "Content-Type: application/json" -d '{"message":"..."}' http://localhost:8080/api/chat` で以下4パターンを問い合わせ、レスポンスJSONがドキュメントに追記したサンプルJSONと一字一句一致することを確認した。
  1. `カテゴリ: 請求 / 緊急度: 高 / 内容: 請求額が二重になっている` → `faq_list`(請求カテゴリ3件) + `choices(["解決した","解決しないので問い合わせる"])`
  2. `FAQ: 請求額が二重に表示される場合`（`MockChatService.java` の `FAQ_ENTRIES` に実在するタイトルを使用）→ FAQ本文 + `faq_list`（一覧再掲） + `choices`
  3. `解決した` → 終了メッセージ + `components: []`
  4. `解決しないので問い合わせる / カテゴリ: 請求 / 緊急度: 高 / 内容: 請求額が二重になっている` → 既存の確認応答（`table` + `choices(["登録する","やり直す","キャンセル"])`）
  - 検証中、ポート8080が別プロセス（旧ビルドと思われるjavaプロセス）に占有されていたため一度終了させてから起動し直した。検証後は起動したバックエンドプロセスを終了させ、ポートを解放した。

### スコープ外（未実施）

- design.md の実装ステップ5〜7（フロントエンド一式）は本タスクのスコープ外のため未実施。
