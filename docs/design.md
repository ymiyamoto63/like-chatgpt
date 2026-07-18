# 設計書: システム構成図モニタリング画面

対応する要件定義: [docs/requirements/system-monitoring-view.md](requirements/system-monitoring-view.md)

## Approach

サイドバーに新規ボタン「システムモニタリング」を追加し、押下すると `App.vue` のメイン領域が `ChatWindow` から新規コンポーネント `MonitoringView` に切り替わる（ルーター不使用・`v-if`/`v-else` の条件描画、切替状態は新規 Pinia ストア `monitoringStore` の `activeScreen` フラグで保持）。バックエンドは新規 `GET /api/monitoring/snapshot` を追加し、固定トポロジー（9ノード・12エッジ）と現在のCPU/メモリ/帯域使用率をひとつのJSONスナップショットとして返す。メトリクスは `MonitoringMetricsService`（`@Service` シングルトン）がインメモリの前回値マップを保持し、リクエストごとに1ティック分のランダムウォーク＋周期スパイクを計算して返す（チャット機能側は無変更でステートレスのまま）。フロントエンドは `monitoringApi.ts` が `chatApi.ts` と同じ方針（必須フィールド検証・未知フィールド無視・不正時は例外）で応答を検証し、`monitoringStore` が5秒間隔のポーリング（`setInterval`）を管理する。ポーリングの開始/停止は `MonitoringView.vue` の `onMounted`/`onUnmounted` に一本化し、画面がDOMに存在する間だけ通信が発生するようにする。構成図は `TopologyDiagram.vue` が固定座標（フロント側定数）でノード・エッジをSVG（`<rect>`/`<line>`/`<text>`、外部ライブラリ不使用）に自作描画し、CPU/メモリ/帯域の3段階しきい値（70%/90%）で枠色・線色を切り替える。既存のチャット関連ファイル（`ChatController`/`MockChatService`/`UiComponent`/`chatApi.ts`/`chat.ts`/`conversationStore.ts`）には一切変更を加えない。

## Alternatives considered

- **ポーリングの開始/停止を各サイドバーハンドラ（New Chat・会話選択・定型レポート・新規問い合わせ）に個別に `stopPolling()` を仕込む案**も検討した。しかし呼び出し漏れのリスクがあり、将来「チャットに戻る」導線が増えた場合に事故りやすい。`MonitoringView.vue` の `onMounted`/`onUnmounted` にポーリング制御を一本化し、サイドバー側は `activeScreen` フラグを立て替えるだけ（Vueのコンポーネントのマウント/アンマウントが自動的にポーリングの開始/停止に連動）とする方が単一責任で事故りにくいため、こちらを採用した。
- **スパイク対象（ノード/エッジ）ごとに独立した非同期スケジューラ（別スレッド・`@Scheduled`）でバックグラウンド更新する案**も検討したが、要件は「5秒ポーリングのたびに値が変動していればよい」（AC-2は連続呼び出しでの変動を検証するのみ）ため、リクエスト駆動（1回の `GET` 呼び出し＝1ティック）の方が実装・テストとも単純でスレッド安全性の考慮も最小限で済む。将来ポーリング間隔が変わっても挙動が破綻しない前提（画面表示中のみ・5秒固定）に合致するため、こちらを採用した。
- ノード・エッジの座標（レイアウト）を **バックエンドAPIレスポンスに含める案**も検討したが、要件は「固定座標レイアウト・表示専用」であり、座標は純粋にフロントの描画関心事である。バックエンドはid/labelとメトリクス値のみを返し、座標はフロント側の定数ファイル（`monitoringLayout.ts`）に持たせることで、既存の「モックデータはバックエンドで生成しフロントは描画に徹する」方針を保ちつつAPIスキーマを最小に保てる。

## Files affected

### バックエンド（新規）

- `backend/src/main/java/com/example/chatbackend/MonitoringNode.java`: ノードのメトリクスDTO（record）
- `backend/src/main/java/com/example/chatbackend/MonitoringEdge.java`: エッジのメトリクスDTO（record）
- `backend/src/main/java/com/example/chatbackend/MonitoringSnapshot.java`: レスポンス全体のDTO（record）
- `backend/src/main/java/com/example/chatbackend/MonitoringMetricsService.java`: 固定トポロジー定義＋インメモリ前回値保持＋ランダムウォーク／周期スパイク生成ロジック
- `backend/src/main/java/com/example/chatbackend/MonitoringController.java`: `GET /api/monitoring/snapshot` エンドポイント
- `backend/src/test/java/com/example/chatbackend/MonitoringMetricsServiceTest.java`: サービス単体テスト（トポロジー形状・値域・変動をAC-1/AC-2相当で検証）
- `backend/src/test/java/com/example/chatbackend/MonitoringControllerTest.java`: MockMvcによるHTTPレベルテスト

### フロントエンド（新規）

- `frontend/src/types/monitoring.ts`: `MonitoringNode`/`MonitoringEdge`/`MonitoringSnapshot` 型定義
- `frontend/src/api/monitoringApi.ts`: `getMonitoringSnapshot()` とレスポンス検証（`MonitoringResponseFormatError`）
- `frontend/src/constants/monitoring.ts`: しきい値（70/90）・ポーリング間隔（5000ms）・3段階レベル判定関数
- `frontend/src/constants/monitoringLayout.ts`: ノードid→固定座標のマップ、ノード枠サイズ、SVG viewBox定数
- `frontend/src/stores/monitoringStore.ts`: 画面切替状態（`activeScreen`）＋モニタリングデータ・ポーリング制御
- `frontend/src/components/MonitoringView.vue`: モニタリング画面本体（タイトル・最終更新時刻・エラーバナー/エラー表示・ポーリングのライフサイクル管理）
- `frontend/src/components/TopologyDiagram.vue`: 構成図SVG描画（ノード＝ゲージ＋数値＋枠色、エッジ＝線色＋%ラベル）

### フロントエンド（変更）

- `frontend/src/App.vue`: `monitoringStore.activeScreen` に応じて `ChatWindow` / `MonitoringView` を条件描画
- `frontend/src/components/Sidebar.vue`: 「システムモニタリング」ボタンを追加（選択中スタイル付き）。既存4操作（New Chat・会話選択・定型レポート・新規問い合わせ）のハンドラ先頭で `monitoringStore.showChat()` を呼びチャット画面へ復帰させる

### ドキュメント（新規）

- `docs/monitoring-response-schema.md`: 新APIの応答JSONスキーマ・トポロジー定義を `docs/chat-response-schema.md` と同水準の粒度で文書化

### 変更なし（明示）

- `backend/src/main/java/com/example/chatbackend/ChatController.java` / `MockChatService.java` / `UiComponent.java` / 各 `*Component.java` / `ChatResponse.java`
- `frontend/src/api/chatApi.ts` / `frontend/src/types/chat.ts` / `frontend/src/stores/conversationStore.ts`
- `frontend/vite.config.ts`（`/api` プレフィックスの既存プロキシ設定が `/api/monitoring/snapshot` にもそのまま適用されるため変更不要）

## 詳細設計

### 新API: `GET /api/monitoring/snapshot`

リクエストボディなし。常に200・`MonitoringSnapshot` を返す（チャットAPIと異なり検証エラーで4xxになるケースはない＝GETでパラメータを取らないため）。

```
MonitoringSnapshot
{
  "nodes": MonitoringNode[],
  "edges": MonitoringEdge[]
}

MonitoringNode
{
  "id": string,             // 例: "web-1"
  "label": string,          // 表示名。例: "Web-1"
  "cpuPercent": number,     // 0〜100
  "memoryPercent": number   // 0〜100
}

MonitoringEdge
{
  "id": string,             // 例: "lb-web1"
  "sourceId": string,       // MonitoringNode.id を参照
  "targetId": string,       // MonitoringNode.id を参照
  "bandwidthPercent": number // 0〜100
}
```

固定トポロジー（9ノード・12エッジ、設計時に確定）:

| id | label | 階層 | baseline CPU | baseline Memory |
|---|---|---|---|---|
| `internet` | Internet | 0 | 20 | 15 |
| `lb` | LB | 1 | 35 | 30 |
| `web-1` | Web-1 | 2 | 40 | 45 |
| `web-2` | Web-2 | 2 | 40 | 45 |
| `app-1` | App-1 | 3 | 45 | 50 |
| `app-2` | App-2 | 3 | 45 | 50 |
| `cache` | Cache | 4 | 30 | 55 |
| `db-primary` | DB Primary | 4 | 50 | 60 |
| `db-replica` | DB Replica | 5 | 35 | 55 |

| id | source → target | baseline bandwidth |
|---|---|---|
| `internet-lb` | internet → lb | 30 |
| `lb-web1` | lb → web-1 | 35 |
| `lb-web2` | lb → web-2 | 35 |
| `web1-app1` | web-1 → app-1 | 30 |
| `web1-app2` | web-1 → app-2 | 20 |
| `web2-app1` | web-2 → app-1 | 20 |
| `web2-app2` | web-2 → app-2 | 30 |
| `app1-cache` | app-1 → cache | 25 |
| `app2-cache` | app-2 → cache | 25 |
| `app1-dbprimary` | app-1 → db-primary | 30 |
| `app2-dbprimary` | app-2 → db-primary | 30 |
| `dbprimary-dbreplica` | db-primary → db-replica | 40 |

Web×2→App×2 はフルメッシュ（4本）とし、単純な1対1接続よりもエッジ本数を増やしてボトルネック表現のデモ効果を高める。

### バックエンドクラス設計

- `MonitoringNode(String id, String label, double cpuPercent, double memoryPercent)` — record、`UiComponent` 系とは無関係の独立DTO（判別共用体不要）。
- `MonitoringEdge(String id, String sourceId, String targetId, double bandwidthPercent)` — record。
- `MonitoringSnapshot(List<MonitoringNode> nodes, List<MonitoringEdge> edges)` — record。
- `MonitoringController`: `@RestController`。コンストラクタで `MonitoringMetricsService` をDI。

  ```java
  @GetMapping("/api/monitoring/snapshot")
  public MonitoringSnapshot snapshot() {
      return monitoringMetricsService.getSnapshot();
  }
  ```

- `MonitoringMetricsService`: `@Service`（シングルトンBean。インスタンスフィールドとして状態を保持する＝チャット機能のステートレス方針の明示的な例外。サーバ再起動でリセットされてよい旨をクラスコメントに明記する）。

  内部状態（コンストラクタで baseline から初期化）:
  - `Map<String, MetricState> nodeCpuStates`, `Map<String, MetricState> nodeMemoryStates`, `Map<String, MetricState> edgeBandwidthStates`（`MetricState` は current値・baseline値を保持する小さな可変クラス、またはプリミティブ配列で代用可）
  - スパイク管理用の単一状態: `String activeSpikeTargetKey`（null可）、`Instant spikeEndsAt`、`Instant nextSpikeEligibleAt`

  定数（すべて `private static final`。要件の未決定事項の推奨仮置き値を採用）:
  - `RANDOM_WALK_STEP = 3.0`（1ティックあたりの最大変動幅）
  - `MEAN_REVERSION_FACTOR = 0.05`（baselineへ緩やかに回帰させ、値が0/100付近に張り付き続けるのを防ぐ）
  - `SPIKE_MEAN_INTERVAL_TICKS = 12`（5秒ポーリング前提で12ティック≒60秒に1回）
  - `SPIKE_INTERVAL_JITTER_TICKS = 6`（次スパイクまでの間隔を `6〜18` ティック＝30〜90秒の一様乱数とし、平均60秒を実現）
  - `SPIKE_DURATION_TICKS = 3`（5秒×3＝15秒間持続）
  - `SPIKE_PEAK_MIN = 78.0`, `SPIKE_PEAK_MAX = 98.0`（警戒/危険域に達するピーク値の範囲）
  - `SPIKE_DECAY_FACTOR = 0.4`（スパイク終了後、`current = current - (current - baseline) * DECAY_FACTOR` を毎ティック適用し数ティックでbaseline付近まで戻す）

  `public synchronized MonitoringSnapshot getSnapshot()` の処理（1回の呼び出し＝1ティック。`synchronized` は将来的な並行リクエストに備えた保険）:
  1. スパイクが有効かつ `now >= spikeEndsAt` なら `activeSpikeTargetKey = null` にして減衰フェーズへ移行（対象は「減衰中」フラグを別途持つ、または単純に「スパイク対象ではなくなった直後は `current` がbaselineより十分離れているので、通常のランダムウォーク＋平均回帰だけでも数ティックでbaseline付近に戻る」設計とし、専用の減衰フェーズは持たず `MEAN_REVERSION_FACTOR` を流用してもよい。ただし観測性を優先し、スパイク終了直後のみ `SPIKE_DECAY_FACTOR` による強めの回帰を1〜2ティック適用する設計とする）。
  2. スパイクが無効かつ `now >= nextSpikeEligibleAt` なら、全ノードの cpu/memory 系列と全エッジの bandwidth 系列（合計 `9*2 + 12 = 30` 系列）から一様乱数で1つ選び、選んだ系列が属するノードの場合は cpu・memory 両方を同時にスパイク対象にする（ノード枠色は両者の最悪値で決まるため、片方だけだと分かりにくい）。スパイクのピーク値を `SPIKE_PEAK_MIN〜SPIKE_PEAK_MAX` から乱数決定し、`spikeEndsAt` を `SPIKE_DURATION_TICKS` 後に設定、`nextSpikeEligibleAt` を次回分（`SPIKE_MEAN_INTERVAL_TICKS ± SPIKE_INTERVAL_JITTER_TICKS`）に更新する。
  3. 全系列について、スパイク対象なら現在値をピーク値方向へ強めに寄せる（例: `current += (peak - current) * 0.5` ＋小さなランダムノイズ）、非対象なら通常のランダムウォーク（`current += uniform(-STEP, STEP) + (baseline - current) * MEAN_REVERSION_FACTOR`）を適用し、最後に `clamp(current, 0, 100)` する。
  4. 更新後の全状態から `MonitoringNode`/`MonitoringEdge` の新規インスタンス一覧を組み立てて `MonitoringSnapshot` として返す（内部の可変Mapをそのまま外部に渡さない＝直列化中の競合を避ける）。

  この設計により「1回の `GET` 呼び出し＝1ティック」という単純化を採用する（バックエンドは実時刻でスケジューリングせず、フロントが5秒間隔でポーリングし続ける限り要件どおりのペースになる）。これは意図的な単純化であり、リスク節に明記する。

### フロントエンド構成

- `types/monitoring.ts`: `MonitoringNode` / `MonitoringEdge` / `MonitoringSnapshot` インターフェース（バックエンドのJSON構造とフィールド名を一致させる）。
- `api/monitoringApi.ts`: `chatApi.ts` と同一方針・同一構造。
  - `export class MonitoringResponseFormatError extends Error {}`
  - `isValidMonitoringNode(value): value is MonitoringNode`（`id`/`label` が string、`cpuPercent`/`memoryPercent` が finite number）
  - `isValidMonitoringEdge(value): value is MonitoringEdge`（`id`/`sourceId`/`targetId` が string、`bandwidthPercent` が finite number）
  - `validateMonitoringSnapshot(value): MonitoringSnapshot`（`nodes`/`edges` が配列であること・各要素が上記を満たすことを検証。値域(0〜100)チェックは既存 `chatApi.ts` の数値検証（`bar_chart.values` 等）が範囲チェックをしていないことに合わせ、本APIでも型・有限性のみ検証し範囲チェックはしない）
  - `export async function getMonitoringSnapshot(): Promise<MonitoringSnapshot>`（`fetch('/api/monitoring/snapshot')`、`!response.ok` は `Error`、それ以外は `validateMonitoringSnapshot`）
- `constants/monitoring.ts`:
  ```ts
  export const WARNING_THRESHOLD = 70
  export const DANGER_THRESHOLD = 90
  export const POLL_INTERVAL_MS = 5000
  export type MonitoringLevel = 'normal' | 'warning' | 'danger'
  export function getMonitoringLevel(value: number): MonitoringLevel { ... }
  ```
- `constants/monitoringLayout.ts`: `NODE_WIDTH`/`NODE_HEIGHT`/`VIEWBOX_WIDTH`/`VIEWBOX_HEIGHT` と `NODE_POSITIONS: Record<string, { x: number; y: number }>`（下記「SVGレイアウトの座標方針」の値をそのまま定数化）。
- `stores/monitoringStore.ts`（Pinia、`conversationStore.ts` と同じ Options API 形式に合わせる）:
  ```ts
  state: () => ({
    activeScreen: 'chat' as 'chat' | 'monitoring',
    snapshot: null as MonitoringSnapshot | null,
    lastUpdatedAt: null as number | null,
    hasError: false,
    pollTimerId: null as number | null,
  }),
  actions: {
    showMonitoring() { this.activeScreen = 'monitoring' },
    showChat() { this.activeScreen = 'chat' },
    startPolling() {
      if (this.pollTimerId !== null) return   // 二重登録防止
      this.fetchSnapshot()                    // 表示開始時に即座取得（FR-7）
      this.pollTimerId = window.setInterval(() => this.fetchSnapshot(), POLL_INTERVAL_MS)
    },
    stopPolling() {
      if (this.pollTimerId !== null) {
        window.clearInterval(this.pollTimerId)
        this.pollTimerId = null
      }
    },
    async fetchSnapshot() {
      try {
        this.snapshot = await getMonitoringSnapshot()
        this.lastUpdatedAt = Date.now()
        this.hasError = false
      } catch {
        this.hasError = true               // snapshot は書き換えない＝直前データ維持（FR-9）
      }
    },
  },
  ```
- `components/MonitoringView.vue`: `onMounted(() => store.startPolling())` / `onUnmounted(() => store.stopPolling())` でポーリングのライフサイクルを一本化。テンプレートはタイトル・`最終更新: HH:mm:ss`（`store.lastUpdatedAt` を `Date` から整形。初回未取得時は `--:--:--`）・エラーバナー（`hasError && snapshot` の場合のみ表示、直前データのSVGは表示したまま）・初回取得失敗時の全面エラー表示（`hasError && !snapshot`）・`TopologyDiagram`（`snapshot` がある場合のみ）を条件描画する。
- `components/TopologyDiagram.vue`: `props: { snapshot: MonitoringSnapshot }`。`NODE_POSITIONS` にIDが存在しないノード/エッジは描画をスキップする（クラッシュ防止の防御的実装。詳細はリスク節）。ノードは `getMonitoringLevel(Math.max(cpuPercent, memoryPercent))` で枠色を決定、CPU・メモリそれぞれのミニゲージ（幅=値/100の`<rect>`、色は各値独自の `getMonitoringLevel`）と数値ラベルを表示。エッジは `getMonitoringLevel(bandwidthPercent)` で線色を決定し中間点に%ラベルを表示。SVGの重なり順は「エッジの`<g>`を先に描画→ノードの`<g>`を後に描画」とし、エッジの端点は各ノード矩形の中心座標で単純に結び、ノード矩形がその上に重なることで視覚的に矩形境界から線が出ているように見せる（複雑な交点計算をしない）。色は `normal→emerald-500`, `warning→amber-500`, `danger→rose-500`（`dark:` バリアント併用、既存 `TrendChartView.vue` の `rose-500` 平均線と統一感のある配色）。

### SVGレイアウトの座標方針

`viewBox="0 0 960 480"`。ノード矩形サイズ `NODE_WIDTH=140, NODE_HEIGHT=76`。列（階層）ごとにx座標を固定し、同一列内の複数ノードはy座標で振り分ける（列中央基準）。

| id | x（矩形左上） | y（矩形左上） |
|---|---|---|
| `internet` | 20 | 202 |
| `lb` | 180 | 202 |
| `web-1` | 340 | 60 |
| `web-2` | 340 | 344 |
| `app-1` | 500 | 60 |
| `app-2` | 500 | 344 |
| `cache` | 660 | 60 |
| `db-primary` | 660 | 344 |
| `db-replica` | 820 | 202 |

`width="100%"` + `viewBox` のみでスケーリングし、`preserveAspectRatio` はデフォルト（`xMidYMid meet`）のまま固定座標レイアウトを維持する。エッジの座標はAPIレスポンスの `sourceId`/`targetId` を上記マップで引いた中心点同士を結ぶことで導出し、バックエンドは座標を一切持たない。

## Implementation steps

1. **バックエンド: DTO＋メトリクス生成サービス**。`MonitoringNode`/`MonitoringEdge`/`MonitoringSnapshot` レコードと `MonitoringMetricsService`（固定トポロジー・ランダムウォーク・周期スパイク）を実装。`MonitoringMetricsServiceTest` を新規作成し、(a) `getSnapshot()` の初回呼び出しでノード9件・エッジ12件・全メトリクスが `[0, 100]` に収まること（AC-1相当）、(b) 連続呼び出し（例: 30回程度ループ）で少なくとも1系列は値が変化すること、かつ全呼び出しを通じて値域が常に `[0, 100]` に収まること（AC-2相当）を検証。
   検証: `cd backend && ./mvnw test -Dtest=MonitoringMetricsServiceTest`

2. **バックエンド: コントローラ**。`MonitoringController`（`GET /api/monitoring/snapshot`）を追加。`MonitoringControllerTest`（`@SpringBootTest` + `MockMvc`）を新規作成し、200応答・`$.nodes`/`$.edges` のサイズと代表フィールドの型を `jsonPath` で確認。既存の `ChatControllerTest`・`MockChatServiceTest` が引き続きグリーンであることも合わせて確認（新規Beanの追加でSpringコンテキスト起動が壊れていないことの回帰確認）。
   検証: `cd backend && ./mvnw test`（全テストがグリーン）

3. **APIスキーマのドキュメント化**。`docs/monitoring-response-schema.md` を新規作成し、上記「新API」節の内容（エンドポイント・フィールド一覧・トポロジー表）を記述する。
   検証: レビューのみ（自動検証なし。実際に起動したバックエンドへ `curl http://localhost:8080/api/monitoring/snapshot` して記載内容と一致することを目視確認）

4. **フロントエンド: 型・定数・APIクライアント**。`types/monitoring.ts`・`constants/monitoring.ts`・`constants/monitoringLayout.ts`・`api/monitoringApi.ts` を新規作成。
   検証: `cd frontend && npx vue-tsc -b`（型エラーなし。lessons-learned に基づき bare `vue-tsc --noEmit` は使わない）

5. **フロントエンド: モニタリングストア**。`stores/monitoringStore.ts` を新規作成（`activeScreen`/`snapshot`/`lastUpdatedAt`/`hasError`/`pollTimerId` の状態と `showMonitoring`/`showChat`/`startPolling`/`stopPolling`/`fetchSnapshot` アクション）。
   検証: `npx vue-tsc -b`

6. **フロントエンド: 構成図描画コンポーネント**。`components/TopologyDiagram.vue`（SVG描画・3段階色分け・ミニゲージ）と `components/MonitoringView.vue`（タイトル・最終更新時刻・エラーバナー/全面エラー・ポーリングのライフサイクル）を新規作成。この時点ではまだサイドバー/App.vueから到達できないため、一時的に `App.vue` で `<MonitoringView />` を直接描画する等して単体で見た目を確認してもよい（後続ステップで正式な切替に置き換える）。
   検証: `npx vue-tsc -b` に加え、`cd backend && ./mvnw spring-boot:run` と `cd frontend && npm run dev` を起動し、ブラウザで構成図・ゲージ・数値・枠色/線色が表示されることを目視確認（AC-3前半）

7. **フロントエンド: 画面切替の結線**。`App.vue`（`monitoringStore.activeScreen` による条件描画）・`Sidebar.vue`（「システムモニタリング」ボタン追加＋既存4操作ハンドラの先頭に `monitoringStore.showChat()` を追加）を変更。ステップ6の暫定描画があれば削除する。
   検証: `npx vue-tsc -b` かつ `npm run build`（lessons-learnedの教訓どおり最終的に本番ビルドも通ることを確認）。手動確認: サイドバーのボタンで画面が切り替わること、チャットに戻ると会話がそのまま残っていること（AC-3全体）、モニタリング画面表示中のみDevToolsのNetworkタブに `/api/monitoring/snapshot` が出ること（AC-7）

8. **結合の手動受け入れ確認（AC-4, AC-5, AC-6, AC-8）**。バックエンド・フロントエンドを起動した状態で以下を確認する。
   - AC-4: モニタリング画面を数十秒開いたままにし、5秒ごとに数値・ゲージ・最終更新時刻がページリロードなしに更新されることを確認。
   - AC-5: 数分間観察し、いずれかのノード枠またはエッジがいったん黄（70%以上）または赤（90%以上）に変化し、その後正常色（緑）に戻ることを確認。時間がかかる場合は `MonitoringMetricsService` の `SPIKE_MEAN_INTERVAL_TICKS`/`SPIKE_DURATION_TICKS` を一時的に小さくして確認を早めてよい（確認後は既定値に戻す）。
   - AC-6: モニタリング画面表示中にバックエンドプロセスを停止し、直前の図が維持されたままエラーバナーが表示されることを確認。バックエンドを再起動し、次のポーリング成功時にバナーが消えて更新が再開することを確認。
   - AC-8: `MonitoringMetricsService`（または一時的な検証用エンドポイント／ブラウザDevToolsのローカルオーバーライド機能）で応答から必須フィールド（例: `nodes[0].cpuPercent`）を一時的に欠落させ、フロントエンドがクラッシュせずエラー表示（バナーまたは全面エラー）になることを確認する。確認後は元に戻す。

## Risks / edge cases

- **「1回のGET呼び出し＝1ティック」という単純化**: バックエンドは実時刻ベースのスケジューラを持たず、フロントの5秒ポーリング頻度に依存してスパイクの体感ペース（平均60秒に1回・15秒持続）が決まる設計とした。手動テストやデバッグ目的で `curl` 等を高頻度・低頻度で叩くと、要件文の秒数どおりのペースにはならない（AC-2は自動テストで値の変動と値域のみを検証しており時間軸は検証対象外なので問題ないが、AC-5の手動確認は実際にフロントの5秒ポーリング経由で行うこと）。
- **バックエンドの状態共有と並行アクセス**: `MonitoringMetricsService` はSpringのシングルトンBeanでミュータブルな状態を持つ（チャット機能のステートレス方針の明示的例外）。複数タブ・複数ユーザーが同時にポーリングした場合、全員が同じグローバルな乱数系列・スパイクを共有する（本デモの想定内。個別セッションごとの状態分離はスコープ外）。`getSnapshot()` は `synchronized` とし、同時リクエストによる状態破壊を防ぐ。
- **ノードID不整合**: バックエンドのトポロジー定義（`MonitoringMetricsService`）とフロントの座標定義（`constants/monitoringLayout.ts`）は独立したハードコードであり、片方だけを変更すると描画が崩れる。`TopologyDiagram.vue` は未知のノードID/エッジIDを検出したら例外を投げずに黙ってスキップする防御的実装とし、クラッシュは避けるが表示欠落には気づきにくい。トポロジーを変更する際は両ファイルを必ず同時に更新することをコード上のコメントで明記する。
- **値のクランプ**: ランダムウォーク＋平均回帰＋スパイクの組み合わせで丸め誤差により `0` 未満や `100` 超になり得るため、必ず最終ステップで `clamp(0, 100)` を適用すること。特にスパイクのピーク方向への強い補正とランダムノイズの組み合わせ箇所で範囲外に出ないことをテストで担保する（実装ステップ1のテストで担保）。
- **色の判別性（非機能要件）**: ライト/ダーク両テーマで `emerald-500`/`amber-500`/`rose-500`（`dark:` バリアント込み）が判別できることを手動確認する。色だけに依存しないよう、ゲージ・数値ラベルを必ず併記する設計になっていることを実装時に崩さないこと。
- **既存チャット機能への影響ゼロの担保**: `ChatController`/`MockChatService`/`UiComponent`/`chatApi.ts`/`chat.ts`/`conversationStore.ts` は変更しない設計だが、`@SpringBootTest` を使う `ChatControllerTest` は同一Springコンテキストで新規Bean（`MonitoringController`/`MonitoringMetricsService`）も起動対象になる。新規Beanの初期化が失敗するとチャット側のテストも巻き添えで失敗するため、ステップ2で `./mvnw test`（全テスト）を必ず実行し回帰がないことを確認する。
- **ポーリングの二重登録・リーク**: `startPolling()` は `pollTimerId !== null` を必ずガードし、`stopPolling()` は `MonitoringView.vue` の `onUnmounted` で確実に呼ばれる設計とする。将来的にモニタリング画面表示中にブラウザタブを閉じる/リロードするケースはブラウザ側がタイマーを破棄するため考慮不要（Non-goalsの「タブ非表示時の停止」は対象外のまま）。

## Test strategy

- **バックエンド自動テスト（JUnit / AssertJ、既存 `MockChatServiceTest`/`ChatControllerTest` と同じ構成に合わせる）**:
  - `MonitoringMetricsServiceTest`: トポロジー形状（ノード9件・エッジ12件、エッジの `sourceId`/`targetId` がすべて存在するノードIDであること）、初回スナップショットの全メトリクスが `[0, 100]` に収まること（AC-1）、複数回連続呼び出しで値が変動しつつ常に値域内であること（AC-2）。
  - `MonitoringControllerTest`: `MockMvc` で `GET /api/monitoring/snapshot` が200を返し、レスポンスJSONの構造（`nodes`/`edges` のサイズ・代表フィールドの型）が期待どおりであること（AC-1のHTTPレベル確認）。
  - 回帰確認として `./mvnw test` をステップ2以降で必ず全件実行し、既存の `ChatControllerTest`/`MockChatServiceTest`/`ChatBackendApplicationTests` がグリーンのままであることを確認する。
- **フロントエンド型検証**: 本リポジトリに自動テストランナー（vitest等）は未導入のため、`npx vue-tsc -b`（lessons-learnedの教訓どおり、bareの `vue-tsc --noEmit` は使わない）と最終的な `npm run build` を型・ビルドの唯一の自動チェックとする。各実装ステップの直後に `npx vue-tsc -b` を実行し早期に型不整合を検知する。
- **手動確認（AC-3〜AC-8）**: `./mvnw spring-boot:run` と `npm run dev`（Viteの `/api` プロキシ経由でバックエンドに到達）を起動した状態で、実装ステップ6〜8に記載した手順に沿って画面切替・ポーリング・色変化・エラーハンドリングを目視確認する。特にAC-7（チャット画面表示中は新APIへのリクエストが発生しない）はブラウザDevToolsのNetworkタブでの確認が必須（自動テスト化しない）。
- **AC-8（不正応答時のフロントエンドの安全性）の具体的手順**: `MonitoringMetricsService.getSnapshot()` を一時的に改変して必須フィールド（例: `cpuPercent`）を欠いたJSONを返すようにする、またはブラウザDevToolsのローカルオーバーライド機能で `/api/monitoring/snapshot` のレスポンスを一時的に改変し、`monitoringApi.ts` の検証によってフロントエンドがクラッシュせずエラー表示（バナーまたは全面エラー）になることを確認する。確認後は変更を必ず元に戻す。
