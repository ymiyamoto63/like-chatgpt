# 設計書: サイドバー定型レポートボタン

対応する要件定義: [docs/requirements/sidebar-report-buttons.md](requirements/sidebar-report-buttons.md)

## Approach

`Sidebar.vue` を「上部（New Chat + 履歴、可変高さ・内部スクロール）」「下部（定型レポートボタン、高さ固定・下端ピン留め）」の2つの `<div>` セクションに分割し、Flexbox の `flex: 1; min-height: 0` パターン（本リポジトリで `app-main` / `chat-window` / `messages` に既に使われている慣習）で実現する。ボタン定義（ラベル＝送信文の固定配列）は新規ファイル `frontend/src/constants/reportButtons.ts` に切り出す。ボタン押下時の「新規会話作成→ラベルを送信」という2ステップの組み合わせは、`conversationStore.ts` に新規アクション `sendReportPrompt(label: string)` を追加し、既存の `createConversation()` と `sendMessage()` をこの順で同期的に呼び出すことで実現する（`createConversation()` は非同期処理を含まないため、`sendMessage()` が読む `activeConversation` は必ず新規作成された会話を指す）。コンポーネント側は他の既存ハンドラ（`handleNewChat` 等）と同様、ストアアクションを呼ぶだけの薄い実装とする。バックエンド・APIクライアント・型定義には一切手を入れない。

## Alternatives considered

- **ストアに新規アクションを足さず、`Sidebar.vue` 側で `store.createConversation(); store.sendMessage(label)` を直接2行呼ぶ**案も検討した。しかし本リポジトリの既存慣習は「複数ステートを跨ぐ操作ロジックはストアのアクションに閉じ込め、コンポーネントは1アクション呼び出しのみ行う」（`handleNewChat`, `handleSelect` 等）であるため、一貫性を優先しストア側にアクションを追加する設計とした。呼び出し順序が結果の正しさ（AC-4）を左右する処理でもあり、その不変条件をコメント付きでストア内に明文化した方が事故が起きにくい。
- ボタン定義を `Sidebar.vue` の `<script setup>` 内に直接書く案も検討したが、FR-7（フロントエンド内の固定定義であることを明示）および Non-goals の「ボタンの増減はコード修正で対応」を素直に満たすため、変更箇所が一目でわかる専用ファイルに切り出す。既存の `types/`, `api/`, `stores/` のような関心ごとのフォルダ分割にも沿う。

## Files affected

- `frontend/src/constants/reportButtons.ts`（新規）: 定型レポートボタンのラベル（＝送信文）を固定配列としてエクスポート
- `frontend/src/stores/conversationStore.ts`（変更）: `sendReportPrompt(label: string)` アクションを追加（既存の `createConversation()` / `sendMessage()` は無変更）
- `frontend/src/components/Sidebar.vue`（変更）: テンプレートを上下2セクションに分割し、下部に定型レポートボタンを追加。スタイルを2セクション対応に調整
- バックエンド・`frontend/src/api/chatApi.ts`・`frontend/src/types/chat.ts`: 変更なし

## 実装詳細

### `frontend/src/constants/reportButtons.ts`（新規）

```ts
export const REPORT_BUTTONS: readonly string[] = [
  '当月担当者別問い合わせ',
  '当月カテゴリ別問い合わせ',
]
```

- ラベル文字列そのものが送信文になる（FR-7, Q5）。要素追加・削除・文言変更は本ファイルの配列を編集するのみで完結する。
- 各ラベルは一意なので `v-for` の `:key` にラベル自身をそのまま使う（余分な `id` フィールドは導入しない＝過度な作り込みをしない）。

### `frontend/src/stores/conversationStore.ts`（変更）

`actions` に以下を追加する（既存の2アクションはそのまま）:

```ts
async sendReportPrompt(label: string) {
  // createConversation() は同期処理のみで activeConversationId を確定させるため、
  // 直後に呼ぶ sendMessage() は必ずこの新規会話に対して送信される（AC-4）。
  this.createConversation()
  await this.sendMessage(label)
},
```

- `createConversation()` / `sendMessage()` の中身は変更しない。
- `sendMessage()` 内の `isLoading` 制御・エラーハンドリング・タイトル設定ロジックはそのまま流用されるため、FR-4/FR-5/FR-6 の大部分は既存コードのみで満たされる。

### `frontend/src/components/Sidebar.vue`（変更）

テンプレート構造（イメージ）:

```html
<aside class="sidebar">
  <div class="sidebar-top">
    <button type="button" class="new-chat-button" @click="handleNewChat">
      New Chat
    </button>
    <ul class="conversation-list">
      <!-- 既存の v-for はそのまま -->
    </ul>
  </div>

  <div class="sidebar-reports">
    <p class="sidebar-reports-heading">定型レポート</p>
    <button
      v-for="label in REPORT_BUTTONS"
      :key="label"
      type="button"
      class="report-button"
      :disabled="store.isLoading"
      @click="handleReport(label)"
    >
      {{ label }}
    </button>
  </div>
</aside>
```

スクリプト側の追加:

```ts
import { REPORT_BUTTONS } from '../constants/reportButtons'

function handleReport(label: string) {
  store.sendReportPrompt(label)
}
```

- `handleReport` は `sendReportPrompt` が返す Promise を await しない（`ChatWindow.vue` の `handleSend` が `store.sendMessage` を await しないのと同じ流儀）。
- 多重送信防止（FR-6/AC-6）はボタンの `:disabled="store.isLoading"` のみで実現する。`MessageInput.vue` の送信ボタンも `:disabled` 属性のみで二重送信を防いでおり（`submit()` 内に `isLoading` の追加チェックはない）、disabled 属性が付いた `<button>` はブラウザがネイティブに click イベントを発火させないため、これで十分。ハンドラ内に追加の `if (store.isLoading) return` は入れない（既存パターンとの一貫性、過剰実装を避ける）。

### CSS（`Sidebar.vue` の `<style scoped>`）

現状の `.sidebar` は `display:flex; flex-direction:column; gap:8px` で `New Chat` ボタンと `.conversation-list` を直接の子として並べている。これを次の構成に変更する。

```css
.sidebar {
  display: flex;
  flex-direction: column;
  width: 240px;
  flex-shrink: 0;
  padding: 12px;
  border-right: 1px solid var(--border);
  box-sizing: border-box;
  min-height: 0; /* 内部スクロールを機能させるため（app-main / chat-window と同じ慣習） */
}

.sidebar-top {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.conversation-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  /* list-style / margin / padding / display / flex-direction / gap は既存のまま */
}

.sidebar-reports {
  flex-shrink: 0;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--border); /* 要件の「セクション境界」線 */
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.sidebar-reports-heading {
  margin: 0 0 2px;
  font-size: 13px;
  color: var(--text);
}

.report-button {
  padding: 8px 12px;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--bg);
  color: var(--text-h);
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.report-button:hover:not(:disabled) {
  background: var(--code-bg);
}

.report-button:disabled {
  background: var(--code-bg);
  color: var(--text);
  cursor: not-allowed;
}
```

- `.new-chat-button` / `.conversation-item` 等の既存スタイルは変更しない。
- `.report-button` は `.new-chat-button` とほぼ同じトーンだが、`:disabled` 時の見た目を `MessageInput.vue` の `.send-button:disabled` と揃える。
- レイアウトの要点: `.sidebar` に `min-height: 0` を追加し、`.sidebar-top` に `flex: 1; min-height: 0` を与えることで下部セクション（`.sidebar-reports`、`flex-shrink: 0`）の高さを確保しつつ、上部の残り高さを `.sidebar-top` いっぱいに広げる。さらに `.conversation-list` 自体にも `flex: 1; min-height: 0; overflow-y: auto` を設定し、`New Chat` ボタンの高さを除いた領域だけがスクロールするようにする（現状の実装は `.conversation-list` に `flex: 1` が無く、履歴が多いと内部スクロールではなくサイドバー全体があふれる可能性がある潜在的な不具合だったため、今回のレイアウト再設計の中で合わせて修正する）。

## Implementation steps

1. **定型レポートボタン定義ファイルの追加**
   `frontend/src/constants/reportButtons.ts` を作成し、`REPORT_BUTTONS` 配列（上記2ラベル）をエクスポートする。
   検証: `cd frontend && npx vue-tsc -b` がエラーなく通ること（`vue-tsc --noEmit` 単体は使わない。プロジェクト参照 `-b` モードのみが実際に型検査する）。

2. **ストアに `sendReportPrompt` アクションを追加**
   `conversationStore.ts` の `actions` に上記の `sendReportPrompt(label: string)` を追加する。既存の2アクションのコードは変更しない。
   検証: `npx vue-tsc -b` を実行しエラーがないこと。加えてコードレビューで「`createConversation()` の呼び出しが `sendMessage()` より必ず先に、かつ間に `await` を挟まず実行されている」ことを確認する（AC-4 の担保根拠）。

3. **`Sidebar.vue` を2セクションレイアウトに変更**
   テンプレートを `.sidebar-top` / `.sidebar-reports` に分割し、`REPORT_BUTTONS` を import してレポートボタンを描画、`handleReport` をストアの `sendReportPrompt` に接続、`:disabled="store.isLoading"` を付与。CSS を上記のとおり更新する。
   検証: `npx vue-tsc -b`（または `npm run build`）がエラーなく通ること。加えて `npm run dev` でブラウザ表示し、レイアウトが崩れていないこと（AC-1, AC-8 の一次確認）。

4. **手動での受け入れ基準（AC-1〜AC-8）通し確認**
   `npm run dev`（および既存手順どおりバックエンドを起動）でアプリを開き、以下を順に確認する。コード変更は行わない検証専用ステップ。
   - AC-1: サイドバーが上下に分かれ、下部に2ボタン・上部に New Chat と履歴が表示される
   - AC-2 / AC-3: 各ボタン押下で新規会話が作成され、ラベルと同文のユーザー吹き出し→表＋棒グラフの応答が表示される
   - AC-4: 既存会話でやり取り済みの状態からレポートボタンを押し、その既存会話にメッセージが追加されず、新規会話側にのみ追加されることを確認
   - AC-5: レポート実行後に履歴一覧の先頭にボタンラベルがタイトルの会話が追加され、他会話に切り替えてから戻っても表＋グラフが再表示される
   - AC-6: 送信直後（ローディング中）にレポートボタンが非活性表示になり、連打しても会話が1つしか増えないこと
   - AC-7: New Chat・会話選択・手入力送信（`MessageInput`）が従来どおり動作すること
   - AC-8: 会話履歴を多数（十数件程度）作った状態で、上部履歴領域のみが内部スクロールし、下部の2ボタンが常に画面内に固定表示され続けること

## Risks / edge cases

- **連打・多重クリック**: `sendReportPrompt` → `sendMessage` の同期プレフィックス（trim チェック〜`isLoading = true` 代入まで）は `await` を挟まず一気に実行されるため、単一のクリックイベント処理中に他のクリックが割り込むことはない。加えてボタンの `:disabled` 属性が `isLoading` 中はネイティブに click を無視するため、二重登録の実害はない（AC-6 は主にこの `:disabled` 表示の確認）。
- **呼び出し順序**: `createConversation()` を `sendMessage()` より後に呼んだり、間に非同期処理を挟んだりすると AC-4 が壊れる（送信内容が旧会話に紛れ込む）。ストア内の1アクションに閉じ込めることでこの順序をコンポーネント側の実装ミスから守っている。
- **既存の潜在バグ修正との混同**: `.conversation-list` に `flex: 1; min-height: 0` を追加する変更は、今回のレイアウト再設計に必然的に含まれる修正であり、既存挙動（AC-7）を壊すものではないことをレビュー時に明記する。見た目上は「履歴が少ないときは変化なし、多いときに正しく内部スクロールするようになる」だけの差分。
- **ラベル文言の一致**: ボタンのラベル文字列とストアに渡す送信文字列は同一である必要がある（FR-3, AC-2/3 の前提）。`REPORT_BUTTONS` の値をテンプレートの表示にも送信にもそのまま使う実装にすることで、文言のズレが構造的に発生しないようにする。
- **タイトルの20文字トリム**: 両ラベルとも20文字以内のため既存の `TITLE_MAX_LENGTH` ロジックで全文がそのままタイトルになる（要件の前提どおり、追加対応不要）。ラベルを将来変更・追加する際に20文字を超えるとタイトルが末尾切り詰めされる点は仕様どおりの既存挙動であり、本設計では対処しない。
- **バックエンドのキーワードマッチ**: `MockChatService.java` 側で「担当」「カテゴリ」というキーワードにマッチする実装を確認済み（要件書の前提どおり）。バックエンドは変更しないため、ここが崩れるとボタンの応答内容（AC-2/AC-3）が意図と異なる結果になるが、本設計のスコープ外（Non-goals）。
- **テスト基盤なし**: 本リポジトリには `vitest` 等のテストランナーが導入されておらず、単体テストの実行手段がない。要件書 AC-4 は「自動テスト（ストア単体）または手動確認」を許容しているため、本設計では手動確認のみで受け入れる。新たにテストランナーを導入することは本タスクのスコープ外（過剰な作り込みを避ける方針に従う）。

## Test strategy

- **型検査**: 各実装ステップ後に `cd frontend && npx vue-tsc -b`（または `npm run build`）を実行し、0件エラーであることを都度確認する。`vue-tsc --noEmit` 単体は使わない（過去の失敗事例: `docs/lessons-learned.md` 参照。ルートの `tsconfig.json` が `files: []` のため、`-b` を付けないと0ファイル検査で常に成功してしまう）。
- **手動確認（結合）**: 上記「Implementation steps」の手順4で AC-1〜AC-8 を通しで確認する。特に AC-4（既存会話が汚染されない）と AC-6（連打で会話が増えない）は見落としやすいため個別に手順化して確認する。
- **回帰確認**: AC-7 として、New Chat ボタン・会話一覧クリックでの切り替え・`MessageInput` からの手入力送信が本改修前と同じ挙動であることを明示的に確認する（既存の `handleNewChat` / `handleSelect` / `sendMessage` のロジックは無変更なので回帰リスクは低いが、レイアウト変更によるクリック領域のズレ等がないかは目視確認する）。
- **自動テストは追加しない**: テストランナー未導入のため（Risks 節を参照）。
