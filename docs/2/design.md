# 実装設計書: 問い合わせ送信前のFAQ提示機能（Issue #2）

> 本書は承認済み [docs/2/requirements.md](requirements.md) に基づく実装設計書である。設計フェーズでの決定事項は本書を正とする。

## Approach

既存の新規問い合わせウィザードのステートレス設計（`MockChatService` が「直前のユーザー発言の定型文」だけで次の応答を決める方式）をそのまま踏襲し、内容入力後の累積文（`カテゴリ: X / 緊急度: Y / 内容: Z`）への応答を、既存の確認ステップから新設の「FAQ提示ステップ」に差し替える。FAQは4カテゴリ×3件をバックエンドの定数（`List<FaqEntry>` とタイトル/カテゴリの2種の `Map` インデックス）としてハードコードし、タイトル文字列をキーに本文・カテゴリを一意に解決する。FAQ一覧は既存の `UiComponent` 判別共用体に `FaqListComponent`（`type: "faq_list"`, `titles: string[]`）を追加して表現し、常に既存の `ChoicesComponent`（「解決した」「解決しないので問い合わせる」）と同じ応答内に同居させる。これにより、入力欄無効化ロジック（`choices` の有無で判定）は一切変更せずに済む。「解決しないので問い合わせる」選択時は、フロントエンドが会話履歴中の直近の累積文を再取得し、`解決しないので問い合わせる / カテゴリ: X / 緊急度: Y / 内容: Z` という新定型文を組み立てて送信することで、`ChatRequest`（`message` のみ）を変えずに既存の確認ステップへ合流させる。フロントエンドは `FaqListView.vue` を新設して `MessageBubble.vue` に描画分岐を追加し、`chatApi.ts` に検証、`conversationStore.ts` に送信ロジックを追加する。

## Alternatives considered

- **FAQ提示の状態保持方法**: (a) バックエンドに簡易セッション/キャッシュを持たせてFAQ提示中フラグを管理する、(b) 累積文＋定型文の組み合わせのみで完全にステートレスに判別する。既存方針（ステートレス厳守）およびIssue要件の制約に従い (b) を採用。
- **「解決しないので問い合わせる」の実装方式**: (a) 累積文をそのまま再送信し、Mock側で「2回目に同じ累積文が来たら確認ステップ」という回数ベースの判定をする、(b) 累積文の前にラベルを連結した新定型文を送る。(a) は「1回目のFAQ提示」と「再度FAQ一覧を見て未解決を選ぶ」を区別する情報がメッセージ自体に存在せず、ステートレスな文字列判別だけでは矛盾なく実装できない（FR-1は累積文そのものをFAQ提示のトリガーと定めているため）。(b) は新しいプレフィックスを導入するだけで既存キーワードと衝突せず、実装・テストも単純なため採用。
- **FAQデータの保持形式**: (a) カテゴリごとに `List<String>`（タイトルのみ）を持ち、本文は別の `Map<String,String>` で管理する、(b) タイトル・カテゴリ・本文を1つの `FaqEntry` レコードにまとめ、そこから2種のインデックス（タイトル→エントリ、カテゴリ→エントリ一覧）を導出する。(b) はタイトルの一意性（FR-5）をデータ構造レベルで表現しやすく、カテゴリ・本文の不整合が起きないため採用。

## API contract

エンドポイントは `POST /api/chat` のまま変更しない（`ChatRequest { message: string }` → `ChatResponse { reply: string, components: UiComponent[] }` という既存契約を維持）。変更は `components` 配列に含まれ得る `UiComponent` の判別共用体に新しい `type` を1つ追加することのみ。

### 新規コンポーネント: `FaqListComponent`（`"type": "faq_list"`）

| フィールド | 型 | 必須 | 説明・制約 |
|---|---|---|---|
| `type` | `"faq_list"` | ○ | 固定リテラル。Jacksonの `@JsonTypeInfo`/`@JsonSubTypes` により自動付与 |
| `titles` | `string[]` | ○ | FAQタイトルの一覧。要素数は1以上。表示順＝配列順。クリックで送信する定型文は `FAQ: <タイトル>`（表示ラベル＝送信文ではなく、`FAQ: ` プレフィックスを付与した文字列が送信文になる点が `choices` と異なる） |

```json
{
  "type": "faq_list",
  "titles": ["請求額が二重に表示される場合", "請求書の再発行方法", "支払い方法の変更手順"]
}
```

1つの応答に含まれる `faq_list` は常に1個（前提どおり）。`faq_list` を含む応答には必ず `choices`（`["解決した", "解決しないので問い合わせる"]`）が同じ `components` 配列に同居する（FR-1, FR-4）。この「同居保証」は本設計の中核であり、フロントエンドの入力欄無効化ロジックが `choices` の有無だけで判定できる根拠になる。

### 応答シナリオ（4パターン、AC-7に対応）

| 受信メッセージ | 応答 |
|---|---|
| `カテゴリ: <値> / 緊急度: <値> / 内容: <本文>`（累積形式） | reply: `"ご登録の前に、関連するFAQをご確認ください。"` + `components`: `[faq_list(該当カテゴリの3件), choices(["解決した","解決しないので問い合わせる"])]` |
| `FAQ: <タイトル>` | reply: FAQ本文 + `components`: `[faq_list(同カテゴリの3件・一覧再掲), choices(["解決した","解決しないので問い合わせる"])]` |
| `解決した` | reply: 終了メッセージ + `components: []` |
| `解決しないので問い合わせる / カテゴリ: <値> / 緊急度: <値> / 内容: <本文>` | reply: `"以下の内容で登録してよろしいですか？"` + `components`: `[table(要約), choices(["登録する","やり直す","キャンセル"])]`（既存の確認応答と同一） |

### エラー応答の扱い

`ChatController`/`ChatRequest` は変更しないため、HTTPレベルのエラー応答（400 Bad Request等）は既存のまま変化しない。`faq_list` 固有のバリデーションエラー（例: `titles` が0件）はHTTPエラーにはならず、フロントエンドの `chatApi.ts` 側の応答検証で不正と判定し、既存方針どおりチャット上のエラー吹き出し（「応答を表示できませんでした」）にフォールバックする（AC-10）。

## FAQデータ設計

`MockChatService.java` 内に非公開の静的定数として保持する（DB・外部ファイルは使わない、Non-goals）。

```java
private record FaqEntry(String category, String title, String body) {}

private static final List<FaqEntry> FAQ_ENTRIES = List.of(
    // 請求
    new FaqEntry("請求", "請求額が二重に表示される場合", "..."),
    new FaqEntry("請求", "請求書の再発行方法", "..."),
    new FaqEntry("請求", "支払い方法の変更手順", "..."),
    // 技術
    new FaqEntry("技術", "ログインできない場合の対処法", "..."),
    new FaqEntry("技術", "アプリが起動しない・強制終了する場合", "..."),
    new FaqEntry("技術", "APIエラーコードの意味一覧", "..."),
    // アカウント
    new FaqEntry("アカウント", "メールアドレスの変更方法", "..."),
    new FaqEntry("アカウント", "退会・アカウント削除の手続き", "..."),
    new FaqEntry("アカウント", "複数端末でのログイン可否", "..."),
    // その他
    new FaqEntry("その他", "営業時間・問い合わせ窓口について", "..."),
    new FaqEntry("その他", "利用規約・プライバシーポリシーの確認方法", "..."),
    new FaqEntry("その他", "サービスの障害情報の確認方法", "..."));

private static final Map<String, FaqEntry> FAQ_BY_TITLE =
    FAQ_ENTRIES.stream().collect(Collectors.toMap(FaqEntry::title, e -> e));

private static final Map<String, List<String>> FAQ_TITLES_BY_CATEGORY =
    FAQ_ENTRIES.stream().collect(Collectors.groupingBy(
        FaqEntry::category, LinkedHashMap::new,
        Collectors.mapping(FaqEntry::title, Collectors.toList())));
```

- 「請求」カテゴリの3件は要件定義書のUIフロー例（FAQ提示のサンプル会話）と一致させ、契約文書・テストの記述と食い違わないようにする。
- 「その他」には汎用FAQ（営業時間、規約確認方法、障害情報確認方法）を用意し、Q9の決定（全カテゴリで提示）を満たす。
- **一意性（FR-5）**: `FAQ_ENTRIES` の12件のタイトルは実装時にすべて相異なる文字列にする（`FAQ_BY_TITLE` 構築時に `Collectors.toMap` が重複キーで例外を投げるため、重複があれば起動時に検知できる＝一種のガード）。
- カテゴリからタイトル一覧を引く（FAQ提示・一覧再掲用）: `FAQ_TITLES_BY_CATEGORY.get(category)`。
- タイトルからカテゴリ・本文を引く（FAQ詳細用）: `FAQ_BY_TITLE.get(title)` → `FaqEntry.category()` / `FaqEntry.body()`。

## MockChatServiceのフロー変更設計

### 新規定数

```java
private static final String FAQ_ANSWER_PREFIX = "FAQ: ";
private static final String FAQ_RESOLVED_LABEL = "解決した";
private static final String FAQ_UNRESOLVED_LABEL = "解決しないので問い合わせる";
private static final String FAQ_UNRESOLVED_PREFIX = FAQ_UNRESOLVED_LABEL + " / ";
```

### 定型文の衝突確認（FR-10）

| 新定型文 | 既存キーワード・定型文との関係 |
|---|---|
| `FAQ: <タイトル>` | `担当`/`カテゴリ`/`種別`/`日別`/`使用率が高い原因を教えて`/`CPU`/`メモリ`/`トラフィック`/`閉じる` のいずれの部分文字列も含まない固定プレフィックス。`inquiryFlowResponse` 内で `startsWith(FAQ_ANSWER_PREFIX)` により最優先で判別されるため、タイトル文字列が仮に「カテゴリ」等を含んでいてもフロー判定には影響しない（本文内容としてのみ扱われる） |
| `解決した` | 既存の `登録する`/`やり直す`/`キャンセル`/カテゴリ・緊急度ラベルのいずれとも文字列が一致しない新規の完全一致トリガー |
| `解決しないので問い合わせる / カテゴリ: ... / 緊急度: ... / 内容: ...` | `カテゴリ: ` から始まらない（`解決しないので問い合わせる` から始まる）ため、既存の `message.startsWith(CATEGORY_ANSWER_PREFIX)` 分岐とは独立に判別できる。「カテゴリ」「緊急度」という語は含むが、これは既存の累積文と同じであり、判別は常にプレフィックス一致（`startsWith`）で行うため誤マッチしない |
| 累積文 `カテゴリ: X / 緊急度: Y / 内容: Z`（FAQ提示ステップのトリガーに変更） | 既存どおり `カテゴリ` `種別` 等の一般キーワードより先に `inquiryFlowResponse` で判定するため、レポートシナリオへの誤マッチは従来どおり発生しない |

いずれの新定型文も、`generateResponse()` のトップレベル判定順序（`inquiryFlowResponse` → `alertFlowResponse` → `日別` → `担当` → `カテゴリ`/`種別` → フォールバック）における**最優先ブロック内**で完結して判別されるため、既存シナリオ（レポート・アラート・ウィザードの他ステップ）の判定順序・挙動には影響しない。

### `parseInquirySummary` への共通化

既存の `inquiryConfirmation(String message)` 内にあったパース処理（`カテゴリ: X / 緊急度: Y / 内容: Z` を分解する処理）を、次の共通メソッドへ切り出す。FAQ提示ステップと「解決しないので問い合わせる」経由の確認ステップの両方から呼び出す。

```java
private record InquirySummary(String category, String urgency, String content) {}

private InquirySummary parseInquirySummary(String message) {
    // message が "カテゴリ: X / 緊急度: Y / 内容: Z" 形式でなければ null を返す
    // （既存 inquiryConfirmation() のパースロジックをそのまま移植）
}
```

### `inquiryFlowResponse` の新しい分岐順序

```java
private ChatResponse inquiryFlowResponse(String message) {
    if (message.equals(INQUIRY_TRIGGER) || message.equals(RETRY_LABEL)) {
        return inquiryCategoryQuestion();
    }
    if (message.startsWith(FAQ_ANSWER_PREFIX)) {
        return faqDetailResponse(message);              // FR-4（未知タイトルは null→フォールバックへ）
    }
    if (message.equals(FAQ_RESOLVED_LABEL)) {
        return faqResolvedCompletion();                  // FR-6
    }
    if (message.startsWith(FAQ_UNRESOLVED_PREFIX)) {
        InquirySummary summary =
            parseInquirySummary(message.substring(FAQ_UNRESOLVED_PREFIX.length()));
        return summary != null ? inquiryConfirmation(summary) : null;   // FR-7
    }
    if (message.startsWith(CATEGORY_ANSWER_PREFIX)) {
        InquirySummary summary = parseInquirySummary(message);
        if (summary != null) {
            return faqPresentation(summary);              // FR-1（変更点: 確認ステップ→FAQ提示）
        }
        return inquiryUrgencyQuestion();
    }
    if (message.startsWith(URGENCY_ANSWER_PREFIX)) {
        return inquiryContentPrompt();
    }
    if (message.equals(SUBMIT_LABEL)) {
        return inquiryCompletion();
    }
    if (message.equals(CANCEL_LABEL)) {
        return inquiryCancellation();
    }
    return null;
}
```

### 新規メソッド

```java
private ChatResponse faqPresentation(InquirySummary summary) {
    List<String> titles = FAQ_TITLES_BY_CATEGORY.getOrDefault(summary.category(), List.of());
    return new ChatResponse(
        "ご登録の前に、関連するFAQをご確認ください。",
        List.of(new FaqListComponent(titles),
                new ChoicesComponent(List.of(FAQ_RESOLVED_LABEL, FAQ_UNRESOLVED_LABEL))));
}

private ChatResponse faqDetailResponse(String message) {
    String title = message.substring(FAQ_ANSWER_PREFIX.length());
    FaqEntry entry = FAQ_BY_TITLE.get(title);
    if (entry == null) {
        return null; // 未知タイトル: 呼び出し元は他シナリオへフォールバック（エッジケース。下記リスク参照）
    }
    List<String> titles = FAQ_TITLES_BY_CATEGORY.getOrDefault(entry.category(), List.of());
    return new ChatResponse(
        entry.body(),
        List.of(new FaqListComponent(titles),
                new ChoicesComponent(List.of(FAQ_RESOLVED_LABEL, FAQ_UNRESOLVED_LABEL))));
}

private ChatResponse faqResolvedCompletion() {
    return new ChatResponse(
        "解決してよかったです。またご不明な点があればお気軽にお尋ねください。", List.of());
}
```

`inquiryConfirmation(String message)` は `inquiryConfirmation(InquirySummary summary)` にシグネチャ変更し、`TableComponent`/`ChoicesComponent`（`登録する`/`やり直す`/`キャンセル`）を返す中身は変更しない。

### `UiComponent.java` の変更

```java
@JsonSubTypes({
    @JsonSubTypes.Type(value = TableComponent.class, name = "table"),
    @JsonSubTypes.Type(value = BarChartComponent.class, name = "bar_chart"),
    @JsonSubTypes.Type(value = ChoicesComponent.class, name = "choices"),
    @JsonSubTypes.Type(value = TrendChartComponent.class, name = "trend_chart"),
    @JsonSubTypes.Type(value = FaqListComponent.class, name = "faq_list")
})
public sealed interface UiComponent
    permits TableComponent, BarChartComponent, ChoicesComponent, TrendChartComponent, FaqListComponent {
}
```

新規ファイル `FaqListComponent.java`:

```java
public record FaqListComponent(List<String> titles) implements UiComponent {
}
```

## フロントエンド設計

### 型定義（`types/chat.ts`）

```ts
export interface FaqListComponent {
  type: 'faq_list'
  titles: string[]
}

export type UiComponentSpec =
  | TableComponent
  | BarChartComponent
  | ChoicesComponent
  | TrendChartComponent
  | FaqListComponent
```

### 検証（`chatApi.ts`、FR-11・AC-10）

`isValidChoicesComponent` と同じ形の検証関数を追加し、`isValidUiComponentSpec` の分岐に組み込む。

```ts
function isValidFaqListComponent(value: unknown): value is FaqListComponent {
  const { titles } = value as Record<string, unknown>
  return isStringArray(titles) && titles.length > 0
}
// isValidUiComponentSpec 内に分岐追加:
// if (record.type === 'faq_list') { return isValidFaqListComponent(record) }
```

`type` が5値（`table`/`bar_chart`/`choices`/`trend_chart`/`faq_list`）以外、または `titles` が空・非文字列を含む場合は不正となり、既存方針どおり応答全体をエラー吹き出しにフォールバックする（部分描画はしない）。

### 共有定数（新規 `frontend/src/constants/faq.ts`）

`constants/monitoringAlert.ts` と同じ「バックエンドの同名定数と値を一致させる」パターンを踏襲する。

```ts
// バックエンド（MockChatService.java）の同名定数と値を一致させること
// （言語をまたぐため自動同期はできない）
export const FAQ_ANSWER_PREFIX = 'FAQ: '
export const FAQ_RESOLVED_LABEL = '解決した'
export const FAQ_UNRESOLVED_LABEL = '解決しないので問い合わせる'
```

`FaqListView.vue` と `conversationStore.ts` の双方がこれを import する（文言の二重管理を避ける）。

### 描画コンポーネント（新規 `FaqListView.vue`）

`ChoicesView.vue` と同じ props/emit 形（`spec`/`disabled` → `select`）を持つが、ボタンではなくリンク風テキストの縦一覧として描画する点が異なる。

```ts
defineProps<{ spec: FaqListComponent; disabled?: boolean }>()
const emit = defineEmits<{ select: [option: string] }>()
```

- `spec.titles` を `<button type="button">` の縦並び（`flex flex-col`）としてレンダリングする。見た目は下線付きテキスト（例: `underline decoration-violet-300 text-violet-700`）とし、`ChoicesView` のボタン（枠線・角丸の pill）とは視覚的に区別する。`v-html` は使わずテキスト展開のみ（XSS対策、既存方針）。
- クリック時は `emit('select', FAQ_ANSWER_PREFIX + title)` で `FAQ: <タイトル>` を送出する（ここで初めてプレフィックスを付与する。`ChoicesComponent` の「表示ラベル＝送信文」とは異なる仕様である点をコンポーネント内コメントで明記する）。
- `disabled` 時はボタンを `disabled` 属性で無効化し、既存 `ChoicesView` と同じトーンで視覚的に区別する（下線を消す・色を薄くする等）。

### `MessageBubble.vue` の変更

```ts
import FaqListView from './FaqListView.vue'
function isFaqListComponent(component: UiComponentSpec): component is FaqListComponent {
  return component.type === 'faq_list'
}
```

テンプレートに分岐を追加:

```html
<FaqListView
  v-else-if="isFaqListComponent(component)"
  :spec="component"
  :disabled="!choicesEnabled"
  @select="emit('select', $event)"
/>
```

`ChoicesView` と同じ `emit('select', $event)` 経路を再利用するため、`ChatWindow.vue`（`@select="handleChoiceSelect"` → `store.sendChoice(option)`）は変更不要。

### 入力欄無効化ロジックへの組み込み方

`conversationStore.ts` の `isInputLocked` ゲッター（`hasChoices(last)` を見るだけの既存実装）は**変更しない**。理由: 本設計は「1つの応答に含まれる `faq_list` は必ず `choices` と同居する」ことを契約上保証する（前掲API契約参照）ため、FAQ提示中・FAQ詳細表示中は常に最新アシスタント発言に `choices` が含まれ、既存の `hasChoices` 判定だけで入力欄が正しく無効化される（AC-6前半）。過去発言中のFAQリンクの無効化は、`MessageBubble.vue` が受け取る既存の `choicesEnabled`（＝「最新のアシスタント発言かどうか」を表す既存の真偽値）を `FaqListView` にもそのまま `disabled="!choicesEnabled"` として渡すだけで実現でき（AC-6後半）、`ChatWindow.vue` 側の変更も不要。

### `conversationStore.ts` の変更

FAQリンククリック（`FAQ: <タイトル>`）と「解決した」は、既存の `sendChoice(option)` → `dispatchUserMessage(option)` の素通しパスでそのまま動作する（変更不要）。「解決しないので問い合わせる」のみ、累積内容を含む定型文への組み立てが必要（FR-7・FR-9）。

```ts
import { FAQ_UNRESOLVED_LABEL } from '../constants/faq'

const INQUIRY_SUMMARY_PATTERN =
  /^カテゴリ: (請求|技術|アカウント|その他) \/ 緊急度: (高|中|低) \/ 内容: .+$/

// 会話履歴中の直近の累積文（内容入力ステップで送信された定型文）を探す。
// フロントの一時状態を持たず、常に conversation.messages（永続化済み）から復元する（FR-9）。
function findLastInquirySummary(conversation: Conversation): string | null {
  const userMessages = conversation.messages.filter((m) => m.role === 'user')
  for (let i = userMessages.length - 1; i >= 0; i--) {
    if (INQUIRY_SUMMARY_PATTERN.test(userMessages[i].content)) {
      return userMessages[i].content
    }
  }
  return null
}
```

`sendChoice` を次のように変更する:

```ts
async sendChoice(option: string) {
  if (option === FAQ_UNRESOLVED_LABEL) {
    const conversation = this.activeConversation
    const summary = conversation ? findLastInquirySummary(conversation) : null
    const content = summary ? `${FAQ_UNRESOLVED_LABEL} / ${summary}` : option
    await this.dispatchUserMessage(content)
    return
  }
  await this.dispatchUserMessage(option)
}
```

これにより、ユーザー吹き出しには `解決しないので問い合わせる / カテゴリ: 請求 / 緊急度: 高 / 内容: ...` という組み立て後の文字列がそのまま表示される（既存の内容入力ステップの累積文表示と同じ透明性のパターン）。会話切り替え・リロード後も `conversation.messages` は `conversationPersistence.ts` 経由でlocalStorageに永続化されているため、`findLastInquirySummary` は問題なく直近の累積文を復元できる（AC-12）。

## Files affected

| ファイル | 変更内容 |
|---|---|
| `backend/src/main/java/com/example/chatbackend/FaqListComponent.java` | 新規。`FaqListComponent(List<String> titles)` レコード |
| `backend/src/main/java/com/example/chatbackend/UiComponent.java` | `@JsonSubTypes` に `faq_list`→`FaqListComponent` を追加、`permits` にも追加 |
| `backend/src/main/java/com/example/chatbackend/MockChatService.java` | FAQデータ（`FaqEntry`/`FAQ_ENTRIES`/`FAQ_BY_TITLE`/`FAQ_TITLES_BY_CATEGORY`）、新定数、`parseInquirySummary`/`InquirySummary` 共通化、`faqPresentation`/`faqDetailResponse`/`faqResolvedCompletion` 追加、`inquiryConfirmation` のシグネチャ変更、`inquiryFlowResponse` の分岐順序変更 |
| `backend/src/test/java/com/example/chatbackend/MockChatServiceTest.java` | 既存 `inquirySummaryReturnsConfirmationWithTableAndChoices` をFAQ提示検証に置き換え、FAQ詳細・4カテゴリ分の提示・「解決した」・「解決しないので問い合わせる」→確認、の各テストを追加 |
| `backend/src/test/java/com/example/chatbackend/ChatControllerTest.java` | 既存 `chatReturnsConfirmationForInquirySummary` をFAQ提示検証に置き換え、AC-7 (a)〜(d) のMockMvc統合テストを追加 |
| `docs/chat-response-schema.md` | `FaqListComponent` の契約追記、シナリオEの表・サンプルJSON更新（累積文→FAQ提示に変更）、FAQ関連の新シナリオ行・サンプル追加、判定順序の記述更新 |
| `frontend/src/types/chat.ts` | `FaqListComponent` 型追加、`UiComponentSpec` union拡張 |
| `frontend/src/api/chatApi.ts` | `isValidFaqListComponent` 追加、`isValidUiComponentSpec` に分岐追加 |
| `frontend/src/constants/faq.ts` | 新規。`FAQ_ANSWER_PREFIX`/`FAQ_RESOLVED_LABEL`/`FAQ_UNRESOLVED_LABEL` |
| `frontend/src/components/FaqListView.vue` | 新規。FAQタイトルのリンク風縦一覧描画コンポーネント |
| `frontend/src/components/MessageBubble.vue` | `isFaqListComponent` ガードと `FaqListView` 描画分岐を追加 |
| `frontend/src/stores/conversationStore.ts` | `findLastInquirySummary`/`INQUIRY_SUMMARY_PATTERN` 追加、`sendChoice` に「解決しないので問い合わせる」分岐を追加 |

## 実装ステップ

1. **バックエンド: `UiComponent` 判別共用体の拡張**（`FaqListComponent.java` 新規作成、`UiComponent.java` へ `faq_list` 追加）。この時点では未使用のため既存挙動に影響なし。検証: `./mvnw test`（既存テストが全て緑のまま）。
2. **バックエンド: FAQデータとフロー変更**（`MockChatService.java` の定数・データ・`faqPresentation`/`faqDetailResponse`/`faqResolvedCompletion`/`parseInquirySummary` 追加、`inquiryFlowResponse` 分岐変更、`inquiryConfirmation` シグネチャ変更）。あわせて `MockChatServiceTest.java` を更新（既存テストの置き換え＋新規テスト: FR-1/2/4/5/6/7/10、AC-8の4カテゴリ×3件検証を含む）。検証: `./mvnw test`。
3. **バックエンド: `ChatControllerTest.java` のMockMvc統合テスト更新**（AC-7の(a)〜(d)を含む一連のシナリオ）。検証: `./mvnw test`。
4. **ドキュメント: `docs/chat-response-schema.md` 更新**（`FaqListComponent` 契約、シナリオE更新、FAQサンプルJSON追加）。検証: 手動で実際に起動したバックエンドへ `curl` し、ステップ2・3のテストと一字一句一致することを確認（AC-11）。
5. **フロントエンド: 型と応答検証**（`types/chat.ts` の型追加、`chatApi.ts` の `isValidFaqListComponent` 追加）。検証: `npm run build`。
6. **フロントエンド: 描画コンポーネント**（`constants/faq.ts` 新規、`FaqListView.vue` 新規、`MessageBubble.vue` に描画分岐追加）。検証: `npm run build` ＋ 開発サーバーでの目視確認（リンク風表示・選択肢との視覚的区別）。
7. **フロントエンド: ストアの送信ロジック**（`conversationStore.ts` に `findLastInquirySummary`・`sendChoice` 分岐追加）。検証: `npm run build` ＋ 手動確認でAC-1〜AC-6、AC-12のシナリオを一通り実施。

各ステップ終了時点でリポジトリはビルド可能な状態を保つ（バックエンドはステップ1〜3の各時点で `./mvnw test` が通り、フロントエンドはステップ5〜7の各時点で `npm run build` が通る）。

## Risks / edge cases

- **既存テストの破壊的変更**: `MockChatServiceTest.inquirySummaryReturnsConfirmationWithTableAndChoices` と `ChatControllerTest.chatReturnsConfirmationForInquirySummary` は、累積文への応答が確認ステップからFAQ提示ステップに変わることで**既存アサーションどおりには通らなくなる**（意図した破壊的変更）。実装ステップ2・3で必ず更新すること。放置すると既存テストが赤くなる。
- **FAQタイトルの一意性が崩れた場合**: `FAQ_BY_TITLE` の構築に `Collectors.toMap` を使うため、実装時に重複タイトルを入れると起動時（クラス初期化時）に `IllegalStateException` で即座に検知できる。将来FAQデータを追加編集する際もこの制約は自動的に維持される。
- **未知のFAQタイトルを含む `FAQ: <タイトル>` 受信時**: `faqDetailResponse` が `null` を返し、`inquiryFlowResponse` 全体としても `null` となるため、後続の一般キーワード判定（`日別`/`担当`/`カテゴリ`/`種別`）やフォールバック応答に落ちる。通常のUI操作では発生しない（フロントは常にバックエンドが返したタイトルのみを送信する）が、タイトル文字列がたまたま「カテゴリ」等を含む場合に誤って集計シナリオへ流れる可能性はゼロではない。実装するFAQタイトル文言が既存キーワードを含まないことをレビュー時に確認する。
- **「解決しないので問い合わせる」の累積文が見つからない場合**: `findLastInquirySummary` が `null` を返すケース（通常フローでは発生しない防御的分岐）では、素の `解決しないので問い合わせる` を送信する設計とした。バックエンド側はこの形式（`/` 区切りの累積内容を伴わない）を `inquiryFlowResponse` のどの分岐にもマッチさせない設計であるため、フォールバック応答（案内文）に落ちる。これは異常系の緩やかな劣化として許容し、通常のUI操作経路（FAQ提示ステップは必ず内容入力ステップの直後にのみ到達する）では起こらないことをテストで裏付ける。
- **「やり直す」経由の再FAQ提示**: 確認ステップの「やり直す」でカテゴリ選択からやり直した場合、内容入力後に再びFAQ提示ステップを通る（要件の前提どおり）。`findLastInquirySummary` は常に「直近の」一致を採用するため、複数回ウィザードを回しても最新の累積文が使われ、古い累積文と混線しない。
- **`faq_list` と `choices` の同居契約が崩れた場合**: 将来の実装・改修で `faqPresentation`/`faqDetailResponse` のいずれかが誤って `choices` を含めずに `faq_list` のみを返すと、入力欄無効化ロジック（`hasChoices` ベース）が機能しなくなる（AC-6の回帰）。この契約はテスト（AC-7関連の統合テスト）で `components[1].type === "choices"` を明示的に検証することで担保する。
- **フロントエンドの検証には自動テストフレームワーク未導入**: `npm run build`（`vue-tsc -b && vite build`）は型検査とビルドのみで、`isValidFaqListComponent` 等のロジックの単体テストは自動化できない。AC-10の検証は手動確認（後述）に頼る。

## Test strategy

### 自動テスト（バックエンド、`./mvnw test`）

- **AC-7**: `ChatControllerTest` にMockMvc統合テストを追加し、以下を検証する。
  - (a) 累積文 `カテゴリ: 請求 / 緊急度: 高 / 内容: ...` → `reply` が案内文、`components[0].type == "faq_list"` かつ `titles` が請求カテゴリの3件と一致、`components[1].type == "choices"` かつ `options == ["解決した","解決しないので問い合わせる"]`
  - (b) `FAQ: 請求額が二重に表示される場合` → `reply` がFAQ本文、`components[0]` が同カテゴリの `faq_list`（一覧再掲）、`components[1]` が同じ2択の `choices`
  - (c) `解決しないので問い合わせる / カテゴリ: 請求 / 緊急度: 高 / 内容: ...` → 既存の確認応答と同一（`table` + `choices(["登録する","やり直す","キャンセル"])`）
  - (d) `解決した` → 終了メッセージ、`components` は空配列
- **AC-8**: `MockChatServiceTest` に4カテゴリ（請求／技術／アカウント／その他）それぞれについて、累積文送信時の `faq_list.titles` がそのカテゴリの3件ちょうどであることを検証するテストを追加する（パラメータ化 or 4つの個別テスト）。
- **AC-9**: 既存の `MockChatServiceTest`/`ChatControllerTest` の全ケース（担当者別・カテゴリ別・日別・非マッチ・ウィザードのカテゴリ/緊急度/キャンセル/登録・アラート系）が変更後も緑であることを確認する。加えて、FAQ関連の新定型文（`FAQ: ...`, `解決した`, `解決しないので問い合わせる / ...`）を送っても `assigneeScenario`/`categoryScenario`/`alertFlowResponse` 等の既存シナリオへ誤って分岐しないことを検証するテストを追加する。

### 手動確認

- **AC-1〜AC-6, AC-12**: 実際にフロントエンド＋バックエンドを起動し、要件定義書のUIフロー例（請求カテゴリ）をなぞって、FAQ提示→FAQ詳細→別FAQ閲覧→解決した（終了・通常チャット継続確認）、および 解決しないので問い合わせる→確認→登録する→完了、の一連を目視確認する。あわせてFAQ提示中・FAQ詳細表示中はテキスト入力欄・送信ボタンが無効であること、過去発言中のFAQリンク・選択肢ボタンが無効化表示されることを確認する（AC-6）。リロード・別会話への切替→復帰後もFAQリンククリックと「解決しないので問い合わせる」からの合流ができることを確認する（AC-12）。
- **AC-10**: フロントエンドに自動テストフレームワークが無いため手動で確認する。開発時に一時的に `MockChatService` の `faqPresentation`（または任意のFAQ応答生成メソッド）が空の `titles`（`List.of()`）を持つ `FaqListComponent` を返すよう改変し、フロントエンドが「応答を表示できませんでした」等のエラー吹き出しにフォールバックし、アプリがクラッシュしないことを確認したうえで改変を元に戻す。あるいはブラウザDevToolsのネットワークオーバーライド機能で `POST /api/chat` の応答JSONを不正な `faq_list`（`titles: []` や `titles` に数値混入）に差し替えて同様に確認してもよい。
- **AC-11**: 実際に起動したバックエンドへ `curl` で上記4シナリオを問い合わせ、`docs/chat-response-schema.md` に追記したサンプルJSONと一字一句一致することを確認する（既存の他シナリオと同じ検証方法）。

## AC mapping

| AC | 内容（要約） | 対応する実装ステップ | 検証方法（Test strategy対応箇所） |
|---|---|---|---|
| AC-1 | 累積文送信でFAQ提示ステップ（3件リンク＋2択）が表示される | ステップ2（`faqPresentation`）、ステップ6・7（`FaqListView`/`ChoicesView`描画、送信ロジック） | 手動確認（AC-1〜AC-6, AC-12） |
| AC-2 | FAQリンククリックでFAQ詳細＋一覧＋2択が再掲される | ステップ2（`faqDetailResponse`）、ステップ6（`FaqListView` の `FAQ: <タイトル>` 送信）、ステップ7（既存 `sendChoice` 素通し経路） | 手動確認 |
| AC-3 | FAQ詳細後、再掲一覧から別FAQを続けて閲覧できる | ステップ2（`faqDetailResponse` が常に同カテゴリ一覧を再掲） | 手動確認 |
| AC-4 | 「解決した」→終了メッセージ・以降通常チャット継続 | ステップ2（`faqResolvedCompletion`、`components` 空により `hasChoices` が偽となり入力欄が自動的に有効化） | 手動確認 |
| AC-5 | 「解決しないので問い合わせる」→確認ステップ→登録→完了 | ステップ2（`FAQ_UNRESOLVED_PREFIX` 分岐で既存 `inquiryConfirmation` 再利用）、ステップ7（`sendChoice` の累積文組み立て） | 手動確認 |
| AC-6 | FAQ提示中の入力欄無効化・過去発言の無効化表示 | ステップ6・7（`choices` 同居契約により既存 `isInputLocked`/`hasChoices` を無改修で流用、`FaqListView` への `disabled` 伝播） | 手動確認 |
| AC-7 | `POST /api/chat` 統合テストで4シナリオが契約どおり | ステップ2・3（`MockChatServiceTest`/`ChatControllerTest` 追加） | 自動テスト（AC-7） |
| AC-8 | 4カテゴリすべてでFAQ3件が返る | ステップ2（`FAQ_TITLES_BY_CATEGORY`＋テスト） | 自動テスト（AC-8） |
| AC-9 | 既存レポート・アラート・ウィザード他ステップへの誤マッチ・退行がない | ステップ2・3（分岐順序設計＋既存テスト維持＋新規非マッチテスト） | 自動テスト（AC-9）＋手動確認 |
| AC-10 | 不正な `faq_list` はエラー吹き出しにフォールバックしクラッシュしない | ステップ5（`isValidFaqListComponent`） | 手動確認（AC-10。自動テスト基盤未導入のため） |
| AC-11 | 契約文書のFAQ定義・サンプルが実応答と一致 | ステップ4（`chat-response-schema.md` 更新） | 手動確認（AC-11、`curl`比較） |
| AC-12 | リロード・会話切替後もFAQリンク／未解決フローが継続できる | ステップ7（`findLastInquirySummary` が永続化済み `conversation.messages` から復元） | 手動確認（AC-12） |
