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
