# コードレビュー報告書: 動的UI生成チャット応答（モック版）

> 対象: `docs/requirements.md`（FR-1〜10, AC-1〜9）／`docs/design.md`（実装設計書）と、実装済みコード一式の突合レビュー。
> レビュー実施日: 2026-07-11
> 本プロジェクトはGitリポジトリではないため、`git diff` の代わりに設計書の「Files affected」に列挙された各ファイルを実読して確認した（前回パイプライン実行分＝別機能のレビュー結果は本書で置き換える）。

## 前提確認

- `docs/lessons-learned.md` の教訓（bare `vue-tsc --noEmit` は実質ノーオペレーションであり `vue-tsc -b` / `npm run build` で確認すべき）を踏まえ、本レビューでも `npx vue-tsc -b` を実行し EXIT_CODE=0 を確認した。実装ノート（`implementation-notes.md`）にも同教訓の適用（ステップ8での型ガード関数シグネチャ修正、`Record<string, unknown>` → `unknown`）が記録されており、以後の実装で同じ問題は再発していない。

## 総合判定

**承認（Approve）** — コード上のcorrectness欠陥は見つからなかった。バックエンド（`UiComponent`/`TableComponent`/`BarChartComponent`/`ChatResponse`/`MockChatService`/`ChatController`とそのテスト）、フロントエンド（`types/chat.ts`/`chatApi.ts`/`conversationStore.ts`/`MessageBubble.vue`/`TableView.vue`/`BarChartView.vue`）とも設計書の記述通りに実装されており、要件（FR-1〜10）・受け入れ基準（AC-1〜9のうちコードで検証可能な範囲）・XSS/堅牢性の非機能要件を満たしている。唯一、コード外のドキュメント（`README.md`）に機能追加前の「エコー応答」の説明が残置されている点のみ、ドキュメント整合性の観点で修正を推奨する（軽微・任意）。

## Correctness findings（必須修正）

**なし。** ロジックエラー・境界値の誤り・競合状態・リソースリーク・エラーパスの欠陥は見つからなかった。個別に確認した主要ポイントは以下の通り。

- `BarChartView.vue`: `maxValue = Math.max(0, ...spec.values)` により、値が全て0または空配列でも0除算せず `widthPercent` は0にフォールバックする（設計書のRisksで指摘された0除算対策が正しく実装されている）。
- `chatApi.ts` の `validateChatApiResponse`: `reply` の型・`components` の配列性・各要素の `type` 別必須フィールド（`table`→`columns`非空/`rows`各行長一致、`bar_chart`→`title`/`labels`非空/`values`が`labels`と同数かつ全要素`Number.isFinite`）を「全体検証（all-or-nothing）」で行っており、設計書の「メッセージ単位・全体検証」方針と一致している。不正時は`ChatResponseFormatError`をthrowし、ストア側で通信エラー（FR-8）と表示不能エラー（FR-7）を`instanceof`で正しく分岐している。
- `MockChatService`: インスタンスフィールドを持たずステートレス。キーワード判定順序（「担当」→シナリオA、「カテゴリ」/「種別」→シナリオB、非マッチ→シナリオC）が設計書・契約文書と一致。
- Jacksonの`@JsonTypeInfo(As.PROPERTY)`によるtype自動付与は`ChatControllerTest`の`jsonPath("$.components[0].type", is("table"))`等で実際に検証されており、レコードに`type`フィールドを持たせない設計が機能している。
- `EchoService.java`/`EchoServiceTest.java`は削除済みで、バックエンド・フロントエンドのソースコード中に`EchoService`や旧`{"reply": string}`形式への参照は残っていない（grep で確認）。
- `docs/chat-response-schema.md`のサンプルJSON（3シナリオ）は`ChatControllerTest`の期待値・`MockChatService`の実装値と完全に一致している（AC-8を満たす）。
- `MessageBubble.vue`は型ガード関数（`isTableComponent`/`isBarChartComponent`）による網羅的分岐で、万一未検証データが来ても何も描画せずクラッシュしない構造になっている。`v-html`は全ファイルでの使用ゼロ（grep確認）、`BarChartView.vue`の`:style`にはレスポンス由来の文字列ではなく自前計算した`widthPercent`（number）のみを渡しており、XSS防止の非機能要件を満たしている。
- AC-9（空白送信の拒否）: バックエンド`ChatRequest.@NotBlank`、フロント`MessageInput.vue`の`canSend`（trim後の長さ判定）とも既存のまま退行していない。
- AC-7（会話切替後の再描画）: `ChatWindow.vue`の`messages`は`store.activeConversation?.messages`のcomputedであり、`components`を含む`Message`が保持されたまま再評価されるため、追加のキャッシュ破棄なしに再描画される構造になっている。

## 軽微な指摘（Stylistic/Minor — 修正は任意）

1. **`README.md`（プロジェクトルート）に旧エコー応答の説明が残置されている**
   - 該当箇所: `README.md:3`「バックエンドは受け取ったメッセージをそのまま返すエコー応答のモック実装です。」／`README.md:35`「エコー応答が表示されれば疎通確認は完了です。」
   - 内容: 本機能により`EchoService`は削除され、`POST /api/chat`はもうエコー応答を返さない（要件のNon-goalsにも「従来のエコー応答の維持（本機能で廃止する）」と明記されている）。しかしREADMEはこの変更を反映しておらず、記載通りに操作した利用者は「エコー応答が表示される」ことを期待してしまい、実際の表示（要約テキスト＋表／グラフ、またはシナリオC・非マッチ時の案内文）と食い違う。
   - 影響: 機能追加の直接的な受け入れ基準（AC-1〜9）には含まれないファイルであり、動作へのリグレッションではないためcorrectness指摘ではなくドキュメント整合性の指摘として記載する。デモや新規参加者向けの案内としては修正が望ましい。
   - 対応案: 「エコー応答」という記述を、実際の動作（キーワードに応じた要約テキスト＋表／棒グラフ、非マッチ時は案内文）に更新する。

2. **`TableView.vue`の`:key="column"` / `BarChartView.vue`の`:key="bar.label"`は値の重複時に衝突しうる**
   - 該当箇所: `TableView.vue:14`（`v-for="column in spec.columns" :key="column"`）、`BarChartView.vue:24`（`v-for="bar in bars" :key="bar.label"`）
   - 内容: 現状の固定モックデータ（担当者名・カテゴリ名はいずれも一意）では問題は顕在化しないが、将来LLMが同名の列見出しや同名ラベルを含む応答を返した場合、Vueの`key`が重複しレンダリングの不整合（DOM再利用の誤り等）が起きうる。クラッシュには至らない軽微な表示上のリスクであり、現行のNon-goals（実LLM未接続）の範囲では実害はない。
   - 対応案（任意）: `column`の代わりにインデックスを、`bar.label`の代わりに`index`を`key`に使う（`TableView.vue`のセル側は既に`cellIndex`をkeyにしており一貫性の観点でも統一が望ましい）。

## 検証環境の確認事実

- `frontend`で`npx vue-tsc -b`を再実行し EXIT_CODE=0 を本レビューでも確認済み。
- バックエンドのMaven実行はレビュー環境（Git Bash、PowerShellがPATH外）の制約で再実行できなかったが、`ChatControllerTest`/`MockChatServiceTest`のテストコードを実読し、アサーション内容が`MockChatService`の実装値・`docs/chat-response-schema.md`のサンプルと矛盾しないことを確認した（`test-report.md`記載の9/9 green の実行結果とも整合）。
