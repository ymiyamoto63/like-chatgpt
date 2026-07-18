# コードレビュー結果: システム構成図モニタリング画面

対象要件: `docs/requirements/system-monitoring-view.md`（FR-1〜FR-11, AC-1〜AC-8）
対象設計: `docs/design.md`（システム構成図モニタリング画面）
対象実装記録: `docs/implementation-notes.md` の「システム構成図モニタリング画面」節（Step 1-3・4-5・6-7）
レビュー実施日: 2026-07-18
（本書は前回パイプライン実行分＝別機能「サイドバー定型レポートボタン」のレビュー結果を置き換える）

## 結論（サマリ）

**blocking correctness の指摘なし。** 自動テスト・型チェック・ビルドが通っている状態に加え、コードを実読した限り FR-1〜FR-11 の実装は要件・設計に整合しており、機能を壊す明確な欠陥は見つからなかった。non-blocking correctness の指摘が1件（ポーリングのレース条件、実害は小さいエッジケース）、stylistic の指摘が1件（ドキュメントの記述が実装完了後も未更新の箇所）ある。

---

## 指摘一覧

### 1. [non-blocking correctness] `frontend/src/stores/monitoringStore.ts`（`fetchSnapshot` / `startPolling` / `stopPolling`）— 画面を離れた際に発生中のfetchが後から古い値で状態を上書きするレース

- **内容**: `fetchSnapshot()` は `getMonitoringSnapshot()` の完了を待たずに呼び出し元へ制御を返す fire-and-forget 方式で、リクエストの識別・キャンセル・順序保証を一切行っていない。`MonitoringView.vue` の `onUnmounted` で `stopPolling()` を呼んでも、それは「次のインターバル発火を止める」だけで、**その時点で既に飛んでいる `fetch` はキャンセルされない**。ユーザーがモニタリング画面→チャット画面→モニタリング画面と短時間に往復操作した場合、以下の順で処理が完了しうる:
  1. 1回目の `MonitoringView` マウントで `startPolling()` → `fetchSnapshot()` 開始（レスポンスが遅延中と仮定）
  2. ユーザーがチャット画面に切替 → `onUnmounted` → `stopPolling()`（タイマーのみ停止、上記fetchは継続中）
  3. ユーザーが再度モニタリング画面に戻る → 2回目の `MonitoringView` マウント → `startPolling()` → 新しい `fetchSnapshot()` が開始しすぐ成功、`snapshot`/`lastUpdatedAt` が新しい値に更新される
  4. その直後に手順1の古いレスポンスが到着し、`fetchSnapshot()` の `catch` を通らず成功扱いで `this.snapshot` と `this.lastUpdatedAt` を**古い値で上書き**してしまう
  - 結果、画面上の構成図・最終更新時刻が一瞬（あるいはユーザーが気づかない形で）新しい値から古い値に巻き戻る。AC-4（「5秒ごとに...ページリロードなしに更新され、最終更新時刻が進む」）の「進む」という前提が短時間だけ崩れる。
- **根拠コード**: `frontend/src/stores/monitoringStore.ts` の `fetchSnapshot`（35-42行目付近）に応答の新旧を判定する仕組み（リクエストID・タイムスタンプ比較等）がない。
- **重大度の判断根拠**: 発生には「画面を数秒以内に複数回往復する」というやや作為的な操作が必要で、常時発生するものではなく、致命的なクラッシュやデータ不整合（0〜100範囲外の値など）には至らない。設計書・要件のいずれも明示的にこのシナリオを検証対象にしていない（AC-4はページリロードなしの継続更新を確認するのみ）ため non-blocking と判断した。
- **修正案**: `fetchSnapshot` にリクエスト世代カウンタ（例: `this.fetchSequence++` してから応答が最新の世代かどうかを確認してから代入）を持たせる、または `stopPolling()` 時に `AbortController` で fetch 自体を中断する。

### 2. [stylistic] `docs/monitoring-response-schema.md` 150-153行目 — 実装完了後もフロントエンド部分の記述が「予定」のままで未更新

- **内容**: 「フロントエンドの安全なフォールバックについて」節で `monitoringApi.ts` を「実装ステップ4以降で追加予定」と表現しているが、これは Step 1-3（バックエンドのみ実装した段階）で書かれた文面がそのまま残ったもので、実際には Step 4-5 で `frontend/src/api/monitoringApi.ts` は既に実装済みである。内容自体（検証方針）は実装と一致しており誤りではないが、「追加予定」という未来形の記述が実装完了後の現在の文書として古い。
- **影響**: 機能には影響しない。ドキュメントの鮮度の問題のみ。
- **修正案**: 「実装ステップ4以降で追加予定」を削除し、`monitoringApi.ts` の実装済み検証ロジックへの参照に更新する（`docs/chat-response-schema.md` の記述粒度に合わせる）。

---

## 確認済み（指摘なし）の観点

- **FR-1（画面切替・会話状態維持）**: `App.vue` は `monitoringStore.activeScreen` で `ChatWindow`/`MonitoringView` を `v-if`/`v-else` 切替するのみで、`conversationStore` 側には一切触れない。`conversationStore.ts` 自体に diff がないことを `git diff --stat` で確認済み。会話状態はストアに保持されているため画面切替で失われない。
- **FR-2（チャットへの復帰導線）**: `Sidebar.vue` の既存4ハンドラ（`handleNewChat`/`handleSelect`/`handleReport`/`handleNewInquiry`）先頭で `monitoringStore.showChat()` を呼んでおり、いずれの操作でもチャット画面に戻る。
- **FR-3（新規GETエンドポイント・固定トポロジー）**: `MonitoringController`/`MonitoringMetricsService` が9ノード・12エッジの固定トポロジーと0〜100のCPU/メモリ/帯域を返すことを `MonitoringMetricsServiceTest`・`MonitoringControllerTest` で検証済み、実装コードもトポロジー表と一致することを確認した。
- **FR-4（自作SVG・外部ライブラリ不使用）**: `TopologyDiagram.vue` は `<svg>`/`<rect>`/`<line>`/`<text>` のみで構成され、`package.json` diff にもチャート系ライブラリの追加はない（Tailwind本体のみ）。
- **FR-5/FR-6（3段階色分け・しきい値70/90）**: `constants/monitoring.ts` の `getMonitoringLevel` が `>=90 danger`, `>=70 warning`, それ以外 `normal` の順で判定しており要件の閾値と一致。ノードは `Math.max(cpuPercent, memoryPercent)` で枠色、CPU/メモリ個別にゲージ色を決定しており設計通り。エッジは `bandwidthPercent` 単体で判定。
- **FR-7（ポーリングライフサイクル・5秒間隔）**: `MonitoringView.vue` の `onMounted`/`onUnmounted` に `startPolling`/`stopPolling` を一本化。`startPolling` は `pollTimerId !== null` の二重登録防止ガードがあり、`stopPolling` は確実に `clearInterval` して `null` に戻す。タイマーリークは確認されなかった（指摘1のレースは「タイマー」ではなく「発生中のfetch」の話であり、リークではない）。
- **FR-8（最終更新時刻表示）**: `lastUpdatedLabel` が `toLocaleTimeString('ja-JP', { hour12: false, ... })` でHH:mm:ss形式を生成し、未取得時は `--:--:--` を表示。
- **FR-9（ポーリング失敗時の挙動）**: `fetchSnapshot` の `catch` は `hasError = true` のみを設定し `snapshot` を書き換えないため直前データを維持する。`MonitoringView.vue` は `hasError && snapshot` でバナー、`hasError && !snapshot` で全面エラー表示を出し分けており、初回取得失敗時とその後の失敗時で要件通りの出し分けができている。
- **FR-10（応答検証・未知フィールド無視）**: `monitoringApi.ts` の `isValidMonitoringNode`/`isValidMonitoringEdge`/`validateMonitoringSnapshot` は必須フィールドの型・有限性のみを検証し、余分なフィールドは無視する。不正なら `MonitoringResponseFormatError` を投げ、`fetchSnapshot` の `catch` でエラー扱いになる。`chatApi.ts` と同一方針であることを確認した。
- **FR-11（ランダムウォーク＋周期スパイク・値域）**: `MonitoringMetricsService.updateState` は最終的に必ず `clamp(0, 100)` を適用しており、`MonitoringMetricsServiceTest.repeatedCallsVaryWhileStayingWithinRange`（30回連続呼び出し）で値域逸脱がないことを確認済み。スパイクは `spikeEndsAtTick`/`nextSpikeEligibleAtTick` の単一状態で管理され、`processSpikeLifecycle` が「アクティブなスパイクがある間は新規スパイクを開始しない」（`spikeEndsAtTick < 0` を新規開始条件に含む）ことで多重スパイクの競合を防いでいる。`getSnapshot()` が `synchronized` であることも確認し、複数リクエストによる状態破壊のリスクはない。
- **ゼロ除算・NaN**: `TopologyDiagram.vue` の `gaugeWidth` は固定の `GAUGE_WIDTH` を分母に使わず、`percent/100` の掛け算のみで除算対象が定数のため、ゼロ除算の余地はない。
- **SVG座標とトポロジーの不整合**: `constants/monitoringLayout.ts` の `NODE_POSITIONS` のキーと `MonitoringMetricsService` のノードIDは現状完全に一致していることを確認した（`internet`/`lb`/`web-1`/`web-2`/`app-1`/`app-2`/`cache`/`db-primary`/`db-replica`）。未知IDに対する防御的スキップ（`flatMap` で `[]` を返す）も設計書通り実装されている。
- **既存チャット機能への回帰**: `git diff --stat` で `conversationStore.ts` と `ChatController.java` に差分がないことを確認した。`MockChatService.java`/`UiComponent.java`/`chatApi.ts`/`chat.ts` には差分があるが、内容は「日別トレンドグラフ（`trend_chart`）」という別機能によるものであり、モニタリング機能に起因する変更ではない（設計書が明示した「モニタリング機能のために変更しない」対象ファイルへの意図しない影響はない）。バックエンド全26件・フロントエンドビルドがグリーンであることもテストレポートで確認済み。
- **既存パターンとの整合**: `monitoringApi.ts` は `chatApi.ts` と同じ検証関数の分割方針・エラークラス命名（`MonitoringResponseFormatError` / `ChatResponseFormatError`）。`monitoringStore.ts` は `conversationStore.ts` と同じ Pinia Options API 形式。色分けは `TrendChartView.vue` の `rose-500` と統一感のある配色。過剰な抽象化やライブラリ導入は見られず、複雑さは要件相応。
- **セキュリティ**: 新規エンドポイントはパラメータを取らないGETで、認証・永続化は要件通り対象外。フロントの応答検証は `v-html` 等を使わずテキスト補間のみで描画しており、XSSの経路はない。

---

## 参考: スコープ外の観察事項（指摘ではない）

作業ツリーには本レビュー対象（モニタリング機能）以外に、Tailwind CSS移行・チャット側の日別トレンドグラフ機能（`TrendChartComponent.java`/`TrendChartView.vue` 等）に由来する変更も混在している（`git status` 参照）。これらは今回のレビュー対象外として指示されたファイル群であり、本レポートでは評価していない。ただし、モニタリング機能が「変更しない」と明言している `conversationStore.ts`・`ChatController.java` には実際に差分がないことは確認済みであり、モニタリング機能自体がこれらの混在した変更の原因ではないことは確認した。

---

## 再レビュー（指摘対応後）

再レビュー実施日: 2026-07-18
対象: 上記指摘1・2への修正（`frontend/src/stores/monitoringStore.ts` の `fetchGeneration` 世代カウンタ導入、`docs/monitoring-response-schema.md` の未来形記述の現在形への修正）

### 結論

**blocking correctness の指摘なし。指摘1・2はいずれも正しく解消されている。** 新たな欠陥の持ち込みも確認されなかった。修正後の `npx vue-tsc -b` は EXIT_CODE=0 で型エラーなし。

### 指摘1（レース条件）への対応の検証

修正後の `frontend/src/stores/monitoringStore.ts` は以下の実装になっている。

```ts
startPolling() {
  if (this.pollTimerId !== null) {
    return
  }
  this.fetchGeneration += 1
  this.fetchSnapshot()
  this.pollTimerId = window.setInterval(() => this.fetchSnapshot(), POLL_INTERVAL_MS)
},
async fetchSnapshot() {
  const requestGeneration = this.fetchGeneration
  try {
    const snapshot = await getMonitoringSnapshot()
    if (requestGeneration !== this.fetchGeneration) {
      return
    }
    this.snapshot = snapshot
    this.lastUpdatedAt = Date.now()
    this.hasError = false
  } catch {
    if (requestGeneration !== this.fetchGeneration) {
      return
    }
    this.hasError = true
  }
},
```

- **元のシナリオの再確認**: 指摘1のシナリオ（モニタリング画面→チャット→モニタリング画面と往復し、古い1回目のfetchが新しい2回目のfetch成功後に到着する）を手順ごとに追跡した。
  1. 1回目マウント: `startPolling()` → `fetchGeneration` が `0→1`。`fetchSnapshot()` が `requestGeneration=1` を捕捉して開始（応答保留中と仮定）。
  2. チャットへ切替: `stopPolling()` → `pollTimerId` をクリアするのみで `fetchGeneration` は変更しない（インターバルの新規発火を止めるだけなので妥当）。
  3. 再度モニタリングへ切替: 2回目マウント → `startPolling()` は `pollTimerId === null`（`stopPolling` でリセット済み）を確認した上で `fetchGeneration` を `1→2` に進め、`requestGeneration=2` の新しい `fetchSnapshot()` を開始・成功。
  4. 手順1の古い応答が後から到着 → `catch`を通らず成功パスに入るが、`requestGeneration(1) !== this.fetchGeneration(2)` が成立するため `return` し、`snapshot`/`lastUpdatedAt`/`hasError` のいずれも書き換えない。
  - 指摘した「新しい値→古い値への巻き戻り」は発生しないことをコードトレースで確認した。**指摘1は解消されている。**
- **エラーフラグの更新漏れがないか**: 失敗パス（`catch`）でも同様に `requestGeneration !== this.fetchGeneration` を確認してから `hasError = true` を設定しており、世代が古い失敗応答で誤って新しいデータ表示中にエラーバナーを出してしまうケースも防がれている（成功・失敗の両方に同じガードが対称的に入っている）。
- **世代カウンタの取り違えがないか**: `fetchGeneration` は `startPolling()` 内、`pollTimerId !== null` の二重登録防止ガードの**後**でインクリメントされている。すなわち、既にポーリング中に `startPolling()` が再度呼ばれても（呼ばれる経路は現状ないが）世代は進まず、進行中の正常な取得を誤って無効化することはない。一方、`stopPolling()` → `startPolling()` という「実際に画面を離れて戻る」経路では確実に世代が進み、古い応答が正しく破棄される。世代を上げるタイミングと「新しい取得セッションの開始」が一致しており、取り違えは確認されなかった。
- **`stopPolling()` との整合**: `stopPolling()` 自体は `fetchGeneration` に触れず、タイマー停止のみを担当している。役割分担が明確で（タイマー制御は `pollTimerId`、応答の新旧判定は `fetchGeneration`）、双方が独立して正しく機能することを確認した。
- **残存する軽微な注意点（新規の blocking/non-blocking 指摘ではなく、今回のfixの範囲外にある既知のトレードオフとして記録）**: 同一世代内で複数の `fetchSnapshot()` が同時に in-flight になるケース（例: ネットワーク遅延が5秒のポーリング間隔を超え、前回リクエストが完了する前に次のインターバルが発火した場合）は、`requestGeneration` が両方とも同じ値になるため今回のガードでは区別できず、後から完了したレスポンスが先に完了したレスポンスを上書きする（＝到着順で上書きされる）点は変わらない。これは今回報告した「画面往復によるレース」とは異なる、より発生条件の狭い別のエッジケースであり、今回の修正が対象とした指摘の範囲外である。5秒間隔・モックAPIというこの機能の性質上、実運用での発生可能性は低いと判断し、追加対応を要求するものではない（参考情報として記録するのみ）。

### 指摘2（ドキュメントの未来形記述）への対応の検証

`docs/monitoring-response-schema.md` 152行目は以下のとおり現在形に修正されている。

> フロントエンドはAPIクライアント層（`monitoringApi.ts`）で、`nodes`/`edges` が配列であること、各要素の必須フィールドの型（...）を検証している。

「実装ステップ4以降で追加予定」という未来形の記述は削除され、実装済みの検証ロジックを指す現在形の記述に置き換わっている。同ドキュメント内に他の「予定」表記が残っていないかを `grep` で確認し、該当なしを確認した。**指摘2は解消されている。**

### 総評

2件の指摘はいずれも意図通りに解消されており、修正によって新たなロジック上の欠陥（世代カウンタの取り違え、`stopPolling` との不整合、エラーフラグの更新漏れ）は持ち込まれていない。blocking の指摘はなし。
