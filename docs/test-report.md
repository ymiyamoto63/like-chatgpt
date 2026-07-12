# テスト検証レポート: サイドバー定型レポートボタン

対象要件: [docs/requirements/sidebar-report-buttons.md](requirements/sidebar-report-buttons.md)（AC-1〜AC-8）
対象実装: `frontend/src/constants/reportButtons.ts`（新規）、`frontend/src/stores/conversationStore.ts`（`sendReportPrompt` 追加）、`frontend/src/components/Sidebar.vue`（2セクション化）

> 本書は今回の機能（サイドバー定型レポートボタン）に対する検証結果である。前回パイプライン実行分（別機能: 動的UI生成チャット応答）の内容を置き換える。
>
> 検証実施日: 2026-07-12

## 前提確認

- `frontend/package.json` の `scripts` に `test` 系コマンドはなく、`vitest` / `jest` 等のテストランナーは devDependencies に存在しない。既存 `*.test.*` / `*.spec.*` ファイルも0件（`find` で確認）。→ 新規テストランナー導入はスコープ外（設計書 Test strategy 節の方針どおり）とし、以下の方針で検証した。
  1. `npx vue-tsc -b` / `npm run build` による型検査・ビルド確認（`docs/lessons-learned.md` の指摘どおり、bare `vue-tsc --noEmit` は使用していない）
  2. 実装コードの全文読み合わせによる静的検証（AC-1〜AC-8全項目）
  3. **AC-4（既存会話が汚染されず新規会話にのみ送信される）と `sendReportPrompt` フローについては、テストランナーを新規導入せずに、リポジトリに既存の `esbuild`（vite の依存として既に `node_modules` に存在）で `conversationStore.ts` を実行時にトランスパイルし、実 Pinia ストア・実ストアコードに対して `fetch` のみモックして動作させる Node スクリプトを作成・実行し、実コードの挙動を実測した**（スクリプトはリポジトリ外のスクラッチパスに置き、リポジトリには追加していない）。

## Commands run

```
# 型検査・ビルド
cd frontend && npx vue-tsc -b
cd frontend && npm run build

# 既存テスト有無の確認
cd frontend && find . -iname "*.test.*" -o -iname "*.spec.*"   # node_modules除外、0件
grep -i "vitest|jest|test" frontend/package.json                # マッチなし

# 実ストアコードに対する動的検証（esbuildでconversationStore.tsをその場でトランスパイルし、実Pinia+モックfetchで実行）
node <scratchpad>/store-check.cjs       # AC-4本体・sendReportPromptフロー・タイトル・キーワード一致
node <scratchpad>/store-check-ac6.cjs   # ストア自体に多重送信ガードが無いことの実測（AC-6の担保根拠の確認）
```

## Result

| 項目 | 結果 |
|---|---|
| `npx vue-tsc -b`（frontend） | PASS（EXIT_CODE=0、エラー0件） |
| `npm run build`（frontend） | PASS（`vite v7.3.6`、`41 modules transformed`、`dist/` 生成、`built in 638ms`） |
| ストア動的検証（store-check.cjs） | PASS（全アサーション成功。詳細は下記） |
| ストア動的検証（store-check-ac6.cjs） | 実測完了（バグではなく設計どおりの挙動を確認。詳細は AC-6 の項参照） |
| 既存テストスイート | 該当なし（フロントエンドにテストランナー未導入。バックエンドは本改修で無変更のため再実行不要と判断） |

失敗（コード上の不具合）は**検出されなかった**。全体として実装は要件・設計と整合している。ただし AC-1, 2, 3, 5, 6, 7, 8 は要件書自身が「検証方法: 手動確認」と明記しており、本レポートでは**コードレビューによる静的検証**にとどまる（ブラウザでの実機確認は未実施）。

## 動的検証の詳細（AC-4 / sendReportPromptフロー）

`conversationStore.ts` の実コードを `esbuild.transformSync`（TS→CJS）でその場でトランスパイルし、実 `pinia`（`createPinia`/`setActivePinia`）上でストアを生成、`global.fetch` のみを `MockChatService.java` のキーワード判定ロジックを模したモックに差し替えて実行した（`postChat` 以降の実装・`sendMessage`/`sendReportPrompt`/`createMessage` は全て本物のコード）。

シナリオと結果:
1. 会話Aを作成し「こんにちは」を送信 → 会話Aに user+assistant の2メッセージ（想定どおり）
2. `sendReportPrompt('当月担当者別問い合わせ')` を実行
   - 会話数が1件増加（新規会話が作成された）
   - `activeConversationId` が会話Aとは異なるIDに変わった
   - **会話Aのメッセージ数は2件のまま変化なし**（AC-4の中核: 既存会話が汚染されない）
   - 新規会話には user メッセージ（`content === '当月担当者別問い合わせ'`）→ assistant メッセージの2件が追加
   - 新規会話の `title === '当月担当者別問い合わせ'`（20文字以内のためトリムなし全文一致、FR-5想定どおり）
   - `fetch` に渡された送信文字列がラベルと完全一致（FR-3の「同一の文字列」要件を確認）
   - 応答文言に「担当者別」を含み、`components` が2件（table/bar_chart相当）返る（「担当」キーワードにマッチしたシナリオが選ばれたことを確認）
3. `sendReportPrompt('当月カテゴリ別問い合わせ')` も同様に新規会話が作成され、タイトルがラベル一致、応答が「カテゴリ別」シナリオになることを確認
4. `sendReportPrompt` 実行中（`fetch` 待機中）に `store.isLoading === true` であること、完了後に `false` に戻ることを確認（FR-6の前提となる `isLoading` 制御自体は正しく機能）

実行結果（抜粋）:
```
PASS: all store-level assertions succeeded
Total conversations created: 4
fetchCalls: ["こんにちは","当月担当者別問い合わせ","当月カテゴリ別問い合わせ","当月担当者別問い合わせ"]
```

失敗したアサーションは0件。

## AC-6 の追加実測（ストア自体の多重送信ガードの有無）

`sendReportPrompt` を `await` を挟まず2回連続で呼び出す（＝`:disabled` によるクリック無効化が効かなかった場合を模擬）と、会話が **2件** 作成されることを実測した。

```
conversations created by 2 unguarded rapid calls: 2
```

これはバグではなく、設計書 Risks/edge cases 節に明記された設計どおりの挙動である（「多重送信防止は `:disabled` 属性のみで実現し、ハンドラ内に `if (isLoading) return` は入れない」）。すなわち **AC-6（連打しても会話が1つしか作られない）の担保は完全にブラウザのネイティブ挙動（`disabled` 属性が付いた `<button>` は `click` イベントを発火しない）に依存しており、Node上のストア単体テストでは検証しきれない**。`Sidebar.vue` 側で `:disabled="store.isLoading"` が実装されていること（コード確認済み、下記参照）は確認できるが、実際にブラウザで連打しても1件しか増えないことは**手動でのブラウザ確認が必須**。

## 受け入れ基準カバレッジ

| AC | 内容(要約) | 検証方法 | 結果 |
|---|---|---|---|
| AC-1 | サイドバー上下2分割、上部New Chat+履歴、下部に2レポートボタン表示 | コード確認（`Sidebar.vue` テンプレート・CSS: `.sidebar-top`/`.sidebar-reports`、`REPORT_BUTTONS` を `v-for`）＋ `App.vue`/`style.css` の高さ連鎖（`height:100vh`→`.app-body{flex:1;min-height:0}`→`.sidebar`はflex子として伸長）の確認 | コード検証済み（手動ブラウザ確認推奨） |
| AC-2 | 「当月担当者別問い合わせ」押下→新規会話・同名ユーザー吹き出し→担当者別表＋棒グラフ応答 | コード確認（`sendReportPrompt`→`sendMessage`の実フロー）＋ 動的検証スクリプトで新規会話作成・ラベル一致送信・「担当」キーワードマッチ・応答2コンポーネントを実測。バックエンド`MockChatService.java`のキーワード「担当」とラベルの前方一致を確認 | 自動（ストア動的検証）で実証済み。実際のバックエンドAPI経由でのUI描画は手動ブラウザ確認推奨 |
| AC-3 | 「当月カテゴリ別問い合わせ」押下→カテゴリ別表＋棒グラフ応答 | 同上（動的検証スクリプトで「カテゴリ」キーワードマッチと応答文言を実測） | 自動（ストア動的検証）で実証済み。UI描画は手動ブラウザ確認推奨 |
| AC-4 | 既存会話でやり取りした状態でレポートボタン押下→既存会話は変化なし、新規会話にのみ送信 | 動的検証スクリプト（実ストアコード実行）で実測: 既存会話のメッセージ数不変・新規`activeConversationId`への切替・新規会話にのみメッセージ追加を確認 | **PASS（自動テストで実証済み）** |
| AC-5 | レポート実行後、履歴先頭にボタンラベルがタイトルの会話が追加。切替後も再表示可 | コード確認（`sendMessage`のタイトル設定ロジック`conversation.messages.length===0`時に`trimmed.slice(0,20)`）＋動的検証で`title===label`を実測。会話配列は`unshift`のため先頭追加も確認（`createConversation`実装）。「切替後の再表示」はメッセージが`conversation.messages`に永続保持される設計（メモリ内）のためロジック上再現される | コード検証済み・タイトル一致は自動テストで実証。切替後の画面再表示は手動ブラウザ確認推奨 |
| AC-6 | 応答待ち中はボタン無効化、連打しても会話1つのみ | コード確認: `Sidebar.vue`の`:disabled="store.isLoading"`実装済み。動的検証で**ストア自体には多重送信ガードがなく、防止策が`:disabled`属性のみに依存する**ことを実測 | コード検証済み（設計どおり）。**実際の連打時の挙動はブラウザでのネイティブ`disabled`動作に依存するため手動確認が必須** |
| AC-7 | New Chat・会話選択・手入力送信の既存挙動が変わらない | コード確認: `handleNewChat`/`handleSelect`/`sendMessage`本体は無変更（diffで確認）。`MessageInput.vue`/`ChatWindow.vue`も無変更 | コード検証済み（回帰リスク低）。手動ブラウザ確認推奨 |
| AC-8 | 会話履歴が多数でも上部のみスクロール、下部ボタンは常時表示 | コード確認: `.sidebar{min-height:0}`→`.sidebar-top{flex:1;min-height:0}`→`.conversation-list{flex:1;min-height:0;overflow-y:auto}`、`.sidebar-reports{flex-shrink:0}`という設計書どおりのFlexboxパターン（`app-main`/`chat-window`と同じ既存慣習）を確認 | コード検証済み。実際の多数件でのスクロール挙動は手動ブラウザ確認推奨 |

## バックエンドのキーワードマッチ前提の確認

`backend/src/main/java/com/example/chatbackend/MockChatService.java` を確認した。

```java
private static final String ASSIGNEE_KEYWORD = "担当";
private static final String CATEGORY_KEYWORD = "カテゴリ";
...
if (message.contains(ASSIGNEE_KEYWORD)) { return assigneeScenario(); }
if (message.contains(CATEGORY_KEYWORD) || message.contains(TYPE_KEYWORD)) { return categoryScenario(); }
```

- 「当月担当者別問い合わせ」は `"担当"` を部分文字列として含む → `assigneeScenario()` に一致
- 「当月カテゴリ別問い合わせ」は `"カテゴリ"` を部分文字列として含む → `categoryScenario()` に一致

要件書の前提（「前提（調査により確認）」節）どおりであることをコードレベルで再確認した。バックエンドは本改修で変更されていないため（`git diff --stat` でバックエンドファイルの変更なしを確認）、退行リスクはない。

## 手動確認が必要な残項目（実施推奨事項）

以下はコードレビューでは代替できず、`npm run dev` + バックエンド起動状態でのブラウザ目視確認が必要（設計書 Implementation steps 4 と同内容）:

- AC-1: レイアウトが崩れず上下2分割で表示されるか
- AC-2/AC-3: 実際にボタンを押して表＋棒グラフが正しく描画されるか
- AC-5: 他会話に切り替えて戻った際に表＋グラフが再表示されるか
- AC-6: 連打（特にネットワーク遅延がある実環境）で本当に会話が1つしか増えないか
- AC-7: 目視での既存機能（New Chat／会話選択／手入力送信）の回帰確認
- AC-8: 会話履歴を十数件作った状態でのスクロール挙動

## 結論

- 型検査・ビルド: PASS（エラー0件）
- ストアロジック（AC-4中核部分）: 実コードに対する動的検証でPASS、不具合なし
- コード上の不具合: 検出されなかった
- AC-6の多重送信防止はストア内部ガードではなく`:disabled`属性のみに依存する設計（設計書に明記済みの既知の設計判断であり、バグではない）。ブラウザでの実機連打確認が唯一の最終担保手段である点を申し送る
- AC-1, 2, 3, 5, 6, 7, 8 はコードレビューでは「実装が要件を満たす作りになっている」ことまでは確認できたが、要件書自身が定める検証方法（手動確認）を代替するものではないため、ブラウザでの目視確認を推奨する
