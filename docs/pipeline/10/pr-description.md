# ストリーミング風タイプライター応答＋コンポーネントのフェードイン演出

## 概要
アシスタント応答のテキストを1文字ずつ時間差で表示するタイプライター風アニメーションと、その完了後にUIコンポーネント（表・グラフ等）をフェードインさせる演出を実装しました。ChatGPT風の「じわじわ生成されている」体感をフロントエンドの表示層だけで疑似実装するもので、バックエンド・API仕様は一切変更していません。

## 変更内容

### 新規ファイル
- **`frontend/src/constants/typewriter.ts`**: タイプライター表示の定数（文字間隔 20ms、フェード所要時間 300ms）
- **`frontend/src/composables/useTypewriterReveal.ts`**: テキスト段階表示 → コンポーネント一括フェードイン のフェーズ機構とタイマー制御を担当する composable

### 改修ファイル
- **`frontend/src/stores/conversationStore.ts`**:
  - `animatingMessageIds: string[]` state を追加（演出中のメッセージ ID を追跡）
  - `markMessageAnimating()` / `settleMessageAnimation()` action を追加
  - `isMessageAnimating()` getter を追加
  - `isInputLocked` getter を拡張（演出中も入力欄をロック）
  - `dispatchUserMessage` の成功・失敗パスで `markMessageAnimating` を呼び出し
  - `createAlertConversation` でも `markMessageAnimating` を呼び出し
  - `deleteConversation` で削除会話のメッセージ ID を `animatingMessageIds` から回収

- **`frontend/src/components/chat/MessageBubble.vue`**:
  - `useTypewriterReveal` composable を統合
  - 新規到着メッセージで `message.content` を段階的に表示
  - テキスト表示完了後、`message.components` をまとめてフェードイン表示
  - 復元メッセージ（会話切替・リロード）では演出なしで即時全体表示

- **`frontend/src/components/chat/ChatWindow.vue`**:
  - `choices-enabled` の算出式に `!store.isMessageAnimating(message.id)` を追加
  - 選択肢ボタンが演出完了まで操作不可のままになる

## 設計のポイント

1. **演出適用範囲の限定（新規到着メッセージのみ）**
   - `animatingMessageIds` はストアの独立フィールドで、`main.ts` の永続化処理（`$subscribe`）が最初から拾わない構造
   - 会話切替・ページ再読み込みで復元されたメッセージは `animatingMessageIds` に未登録 → 演出なしで即座に全体表示

2. **タイマー管理**
   - `useTypewriterReveal` は `onUnmounted` で `setInterval`/`setTimeout` を確実にクリーンアップ
   - ネットワーク非同期処理を含まない純粋ローカルタイマーのため、stale 上書きレースは原理的に発生しない

3. **選択肢・入力欄の一括制御**
   - `ChatWindow.vue` の `choicesEnabled` と `conversationStore.ts` の `isInputLocked` が同じ `isMessageAnimating()` を参照
   - 片方だけロック漏れが起きない設計

4. **XSS対策の維持**
   - テンプレートは `{{ displayedText }}` の補間展開のみ使用、`v-html` は使用なし

## テスト結果サマリー

### 自動検証（型検査・ビルド）
- ✅ `npx vue-tsc -b`: **PASS** （exit code 0）
- ✅ `npm run build`: **PASS** （exit code 0、`dist/` 出力確認）

### 静的検証（コードレビュー）
- AC-1〜AC-8（受け入れ基準）: 要件を満たす構造で実装されていることをコードレビューで確認
- **Blocking 指摘: なし**
- Non-blocking 指摘 3件（軽微、AC には影響なし）

### 手動確認
ブラウザ操作に基づく確認は、マージ後にユーザー側で実施（テストレポート記載のチェックリスト参照）

## レビュー結果
**このまま publish 可。** Blocking な指摘なし。設計書のデータフロー・決定記録（D-1〜D-3）どおりに実装されており、AC-1〜AC-9 を満たす構造を確認。

---

**Closes #10**
