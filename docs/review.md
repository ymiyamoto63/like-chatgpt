# コードレビュー: サイドバー定型レポートボタン

対象要件: [docs/requirements/sidebar-report-buttons.md](requirements/sidebar-report-buttons.md)（AC-1〜AC-8）
対象設計: [docs/design.md](design.md)
対象diff: `frontend/src/constants/reportButtons.ts`（新規）、`frontend/src/stores/conversationStore.ts`（`sendReportPrompt` 追加）、`frontend/src/components/Sidebar.vue`（2セクション化）

レビュー実施日: 2026-07-12
（本書は前回パイプライン実行分＝別機能「動的UI生成チャット応答」のレビュー結果を置き換える）

## 総合判定

**問題なし（correctness-level の指摘事項なし）。**

`git diff` を実読し、要件（FR-1〜FR-7, AC-1〜AC-8）・設計書（Approach, 実装詳細, CSSパターン）と突き合わせて検証したが、ロジック誤り・レイアウト崩壊・既存挙動の回帰につながる欠陥は見つからなかった。`npx vue-tsc -b` を再実行し EXIT_CODE=0 を確認済み（`lessons-learned.md` の指摘どおり `-b` モードを使用、node は `/c/nvm4w/nodejs` を PATH に追加）。

## 確認した主な観点と結論

1. **AC-4（既存会話が汚染されない）**: `sendReportPrompt` は `this.createConversation()`（同期）→ `await this.sendMessage(label)` の順で呼ばれる。`createConversation()` が `activeConversationId` を同期的に確定させ、`sendMessage()` 内の `activeConversation` ゲッター参照は最初の `await postChat(...)` に到達するまで同期実行されるため、間に他の処理が割り込む余地はない。したがって送信は必ず新規会話に対して行われる。設計書のコメントどおりで実装も一致している。
2. **メッセージの会話取り違え（応答が誤った会話に着地するリスク）**: `sendMessage()` は `await` の前後を通じてローカル変数 `conversation`（クロージャで捕捉した参照）に対して `messages.push` している。応答待ち中にユーザーが別会話へ切り替えても、応答は正しく元のレポート会話に追加される（`this.activeConversation` を再参照していない）。この既存ロジックは無変更で、今回の変更でも壊れていない。
3. **AC-6（多重送信防止）**: `Sidebar.vue` の `:disabled="store.isLoading"` のみに依存し、`handleReport`/`sendReportPrompt` 内に `isLoading` の追加ガードはない。これはテストレポートが指摘した点と一致し、設計書 Risks 節にも明記された意図的な設計判断である。実際の挙動を検証すると、`isLoading = true` の代入は `sendMessage()` 内で最初の `await` に到達する前（同期区間）に行われるため、1回のクリックイベント処理が完了する時点で既に `isLoading` は `true` になっている。ブラウザの `click` イベントはタスクごとに処理され、Vue のリアクティブな DOM 更新（`disabled` 属性への反映）はマイクロタスクとして次のタスクが処理される前にフラッシュされるため、通常のユーザー操作（連打・ダブルクリック）で `disabled` 反映前に2回目の `click` がすり抜ける実害は起きにくい。ただし、これはブラウザのイベントループの挙動に依存した保護であり、ストア側にアプリケーションレベルの明示的なガード（多重防御）が無い点は設計判断として妥当だが、将来的な変更（例: `handleReport` を async 化して await を挟む等）で容易に壊れうる脆さを内包している。**correctness-level のバグではなく、既知・許容済みの設計トレードオフとして minor 扱いとする**（新規の欠陥ではない）。
4. **レイアウト（AC-1, AC-8）**: `.sidebar { min-height:0 }` → `.sidebar-top { flex:1; min-height:0 }` → `.conversation-list { flex:1; min-height:0; overflow-y:auto }`、`.sidebar-reports { flex-shrink:0 }` という Flexbox の入れ子構成を確認した。親側 `App.vue` の `.app-body { flex:1; min-height:0; display:flex }`（row方向、`align-items` 既定 `stretch`）により `.sidebar` の高さが `.app-body` いっぱいに伸びることも確認済みで、上部のみが内部スクロールし下部の2ボタンが常に表示され続けるという要件どおりのレイアウトが成立する構造になっている。`.conversation-list` への `flex:1; min-height:0; overflow-y:auto` の追加は設計書が明記する意図的な既存バグ修正であり、AC-7 の回帰にはならない（履歴が少ないときは見た目上の変化なし）。
5. **AC-7（既存挙動の維持）**: `handleNewChat` / `handleSelect` の実装本体、`.new-chat-button` / `.conversation-item` 系スタイルは無変更であることを diff で確認した。
6. **CSS カスタムプロパティの使用**: 新規追加スタイル（`.sidebar-reports`, `.report-button` 等）は `var(--border)`, `var(--bg)`, `var(--text)`, `var(--text-h)`, `var(--code-bg)` のみを使用しており、ハードコードされた色は無い。`.report-button:disabled` の配色は `MessageInput.vue` の `.send-button:disabled` と一致しており、設計書の記述と実装が合致している。
7. **ボタン定義とラベルの一致（FR-3, FR-7）**: `REPORT_BUTTONS` の値がテンプレートの表示・`store.sendReportPrompt(label)` への送信文字列の両方にそのまま使われており、表示文言と送信文言がズレる余地は構造的にない。
8. **タイトル20文字制限（AC-5）**: `sendMessage()` 内の `trimmed.slice(0, TITLE_MAX_LENGTH)`（無変更）に両ラベルとも20文字以内で収まるため、そのまま全文がタイトルになる。

## 型検査・ビルド確認

- `cd frontend && npx vue-tsc -b` を実行し EXIT_CODE=0 を再確認（node は `/c/nvm4w/nodejs` を PATH に追加して実行）。

## 指摘事項

### Correctness-level
なし。

### Minor / 参考情報（対応不要、将来の申し送り事項）
- `sendReportPrompt` / `handleReport` は多重送信防止をストア内部のフラグチェックではなく `:disabled` 属性のみに依存している（テストレポートで既出、設計書で明示的に許容された判断）。ブラウザのネイティブ挙動に守られており通常操作では問題にならないが、将来 `handleReport` を async 化するなど呼び出し経路が変わった場合に静かに崩れる可能性があるため、変更時は本トレードオフを意識すること。
- レポートボタン押下は常に新規会話を作成する仕様（Q2 決定どおり）のため、直前に空の「New Chat」会話を作ってから未入力のままレポートボタンを押すと、空のまま使われない会話が履歴に残る。これは本改修固有の問題ではなく、既存の `New Chat` ボタン単体でも起こりうる既存挙動であり、要件のスコープ外。
