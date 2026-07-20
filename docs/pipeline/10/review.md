# レビュー結果: ストリーミング風タイプライター応答＋コンポーネントのフェードイン演出（Issue #10）

対象diff: `git diff cda30f375b3825b3862e32035674d378312028e6..HEAD -- frontend/`
（`frontend/src/components/chat/ChatWindow.vue`、`frontend/src/components/chat/MessageBubble.vue`、`frontend/src/composables/useTypewriterReveal.ts`（新規）、`frontend/src/constants/typewriter.ts`（新規）、`frontend/src/stores/conversationStore.ts`）

参照資料: `docs/pipeline/10/requirements.md`、`docs/pipeline/10/design.md`、`docs/pipeline/10/test-report.md`、`docs/lessons-learned.md`

## レビュー観点と確認内容

- **タイマークリーンアップ・stale上書きレース（プロジェクトの重点確認事項）**: `useTypewriterReveal.ts` は `setInterval`/`setTimeout` の ID を `onUnmounted` で確実に `clearInterval`/`clearTimeout` している。ネットワーク非同期処理を伴わない純粋ローカルタイマーであり、monitoringStore で問題になった「世代カウンタが必要な in-flight レスポンスの上書き」に相当する構造は存在しない。
- **`revealComponents()` の `nextTick()`/`requestAnimationFrame()` チェーンが `clearTimers()` の追跡対象外**であることを確認した（後述、non-blocking）。
- `conversationStore.ts`: `animatingMessageIds` は `state()` 内で常に `[]` 初期化、`main.ts` の `$subscribe` が `state.conversations`／`state.activeConversationId` の2フィールドのみを `savePersistedState` に渡す構造を確認し、localStorage に混入しないことを確認した。`markMessageAnimating` の呼び出しは `dispatchUserMessage` の成功パス・2つの catch 分岐、`createAlertConversation` すべてで漏れなく呼ばれている。`deleteConversation` は削除会話のメッセージ ID を `animatingMessageIds` からも回収している。
- `MessageBubble.vue`: `shouldAnimate` は setup 時に一度だけ確定する非リアクティブな値であり、`v-for :key="message.id"` によりメッセージごとに新規インスタンスが作られるため、同一インスタンスの使い回しによる不整合は発生しない。`v-html` は使用していない（grep で確認済み）。`TableView` 等の個別コンポーネントの props・データは無改修。
- `ChatWindow.vue`: `choices-enabled` の算出式に `!store.isMessageAnimating(message.id)` が正しく追加されている。

上記の結果、コードの実装ロジックを実際に読んで追跡した限り、**FR-1〜FR-9 および AC-1〜AC-9 の要求を満たさない具体的なバグは見つからなかった**。

## Blocking な指摘

なし。

## Non-blocking な指摘

1. **`frontend/src/composables/useTypewriterReveal.ts` の `revealComponents()`（48〜62行目付近）**: `nextTick()` → `requestAnimationFrame()` のコールバックチェーンが `clearTimers()`（`onUnmounted` で呼ばれる）の追跡対象（`intervalId`/`timeoutId`）に含まれていない。最後の文字が表示されて `showComponents.value = true` になった直後、`requestAnimationFrame` コールバックが実行される前のごく短い window（1フレーム未満）でコンポーネントがアンマウントされた場合、アンマウント後に `componentsVisible.value = true` の代入と `settle()`（`store.settleMessageAnimation(id)` の呼び出しを含む）が実行され得る。これは設計書「リスク・エッジケース」節が明記する「アンマウント時は `onSettled` を呼ばず、再訪時は演出をやり直す」という意図的仕様と、この極めて狭い競合状態下でのみ整合しなくなる（再訪時に演出なしで即時全体表示されてしまう）。影響方向は「演出が省略される」という無害側のズレであり、AC-1〜AC-9 のいずれにも違反しない。テストレポートも同じ所見を「軽微」と判定しており、実装を独自に追跡した結果も同じ結論に達した。対応するなら `requestAnimationFrame` の ID も保持して `onUnmounted` でキャンセルする形が考えられるが、必須ではない。

2. **`TYPEWRITER_COMPONENT_FADE_MS`（`frontend/src/constants/typewriter.ts`）と `MessageBubble.vue` の `duration-300`（106行目）の二重管理**: フェードインの所要時間が定数ファイルとTailwindユーティリティクラスの文字列という2箇所に独立して定義されており、値がコメントでのみ紐付けられている。将来どちらか一方だけ変更すると、`setTimeout` の完了タイミングと実際のCSSトランジション完了タイミングがずれ、コンポーネントのopacityアニメーションが完了しないうちに `settle()` されてしまう（見た目上は軽微な不整合で、機能的な破綻はない）。定数化自体は設計書の意図通りであり、コメントでの明記もされているため許容範囲だが、将来の変更時に見落とされるリスクとして記録する。

3. **`conversationStore.ts` の `dispatchUserMessage`**（167〜205行目）における既知の構造的な話（Issue #10 で新規導入されたものではないが、今回の変更で影響範囲が広がった点）: `postChat` の await 中に対象会話が `deleteConversation` で削除された場合、`conversation` 変数は削除前に捕捉したオブジェクト参照のままのため、応答到着後に `conversation.messages.push(...)` と `this.markMessageAnimating(message.id)` が実行され、`animatingMessageIds` に「もはやどの会話にも属さないメッセージID」が残留し得る（`deleteConversation` のクリーンアップは削除時点の `messages` しか見ないため、削除後に追加されたこの ID は回収されない）。既存の「削除済み会話への push」自体は本Issue以前からの挙動でスコープ外だが、今回 `animatingMessageIds` という新しい配列にも同じ経路でリークが波及する点は認識しておくとよい。実害は配列に孤立IDが残る程度で、UIの誤動作には直結しない（該当IDを参照する `MessageBubble` インスタンスがそもそも存在しないため）。プロトタイプの許容範囲として blocking にはしない。

## 総合判定

**このまま publish 可。** blocking な指摘はなし。設計書のデータフロー・決定記録（D-1〜D-3）どおりに実装されており、AC-1〜AC-9 を満たす構造になっていることを確認した。non-blocking の3件は将来の改善候補として記録するのみで、修正必須ではない。
