# 実装設計: ノード別スパークライン＋履歴チャート

対象要件定義: [docs/pipeline/12/requirements.md](./requirements.md)

## Approach

`monitoringStore`にノードごとのCPU/メモリ履歴を保持するリングバッファ（`Map<nodeId, MonitoringHistorySample[]>`、1サンプルに`timestamp`/`cpuPercent`/`memoryPercent`を同居させ、上限36件でトリム）と、選択中ノードID（`selectedNodeId`）を追加する。`fetchSnapshot()`成功時にこのバッファへ1件追記する処理を追加するのみで、既存のポーリング／アラート／stale対策ロジックには触れない。`TopologyDiagram.vue`は既存のゲージ帯グラフ（`<rect>`塗りつぶし）を、履歴データを`Map`としてpropsで受け取り自作SVG `<polyline>`で描くスパークラインに置き換える（ノード矩形サイズ・座標・数値表示・3段階色分けは維持）。ノードの`<g>`要素にクリックハンドラを追加し`node-click`イベントを発火、`MonitoringView.vue`が受けて`store.selectNode(id)`を呼ぶ。新規`NodeHistoryModal.vue`（プレゼンテーショナル、props経由でノード情報と履歴を受け取り`close`をemit）を`MonitoringView.vue`に`v-if="selectedNode"`で条件描画し、`fixed inset-0`の自作オーバーレイ＋自作SVG折れ線チャート（CPU/メモリ2本、Y軸0-100%固定、凡例、X軸は最古〜最新のサンプル時刻ラベル）を表示する。チャット画面への切り替え（`showChat()`）で選択中ノードをクリアしてモーダルを自動的に閉じるが、履歴バッファ自体はストアの状態としてクリアされないためFR-9を追加ロジックなしで満たす。バックエンドは無変更。

## Alternatives considered

- **履歴データ構造**: 「CPU系列・メモリ系列を別々の配列で持つ」か「1サンプルにCPU/メモリ両方を同居させた配列を1本持つ」か → 後者を採用。ポーリングは常にCPU/メモリを同時取得するため両系列のタイムスタンプは必ず一致し、系列を分けても同じ長さ・同じ間隔になるだけで実質的な差がない。1本にまとめる方が「対象ごとに直近36件」というFR-2の意図（系列ごとの上限）を、より単純な実装（ノードごとに1つのトリム処理）で満たせる。
- **選択中ノードの状態保持場所**: 「`MonitoringView.vue`のローカル`ref`」か「`monitoringStore`の状態」か → 要件定義の影響範囲に「`monitoringStore.ts`に...選択中ノード（モーダル表示対象）の状態を追加」と明記されているためストアを採用。副次的に、`showChat()`遷移時にストア内で選択状態をクリアできる（サイドバーからの画面遷移とモーダルを閉じる処理を1箇所に集約できる）という利点もある。
- **モーダルのマウント方法**: `<Teleport to="body">`を使うか、`MonitoringView.vue`のテンプレート内に直接置くか → 直接配置を採用。本リポジトリに`Teleport`の使用実績がなく、祖先要素（`App.vue`のルート`div`、`MonitoringView.vue`の`overflow-y-auto`コンテナ）に`transform`/`filter`が無いため`position: fixed`は素直にビューポート基準でオーバーレイでき、`overflow-y-auto`にクリップされない。新しいパターンを持ち込まず「動くプロトタイプ優先」の方針に沿う。

## Files affected

- `frontend/src/types/monitoring.ts`: `MonitoringHistorySample`型を追加。
- `frontend/src/constants/monitoring.ts`: `MAX_HISTORY_SAMPLES = 36`定数を追加。
- `frontend/src/stores/monitoringStore.ts`: `nodeHistoryById`（`Map<string, MonitoringHistorySample[]>`）・`selectedNodeId`状態を追加。`recordHistorySample()`アクション新規追加し`fetchSnapshot()`から呼び出し。`selectNode()`/`clearSelectedNode()`アクション追加。`showChat()`に選択解除を追記。
- `frontend/src/components/monitoring/TopologyDiagram.vue`: `historyByNodeId`propを追加。ゲージ帯グラフ描画（`gaugeFillClass`/`gaugeWidth`と塗りつぶし`<rect>`2つ）を、スパークライン用の座標計算関数（`sparklinePoints`/`valueToY`）と`<polyline>`描画に置き換え。ノードの`<g>`にクリックハンドラ・`node-click`イベントemitを追加。
- `frontend/src/components/monitoring/NodeHistoryModal.vue`（新規）: モーダルダイアログ＋履歴チャートSVG。props: `node`, `history`。emit: `close`。
- `frontend/src/components/monitoring/MonitoringView.vue`: `store.snapshot`から選択中ノードを導出する`computed`、`store.nodeHistoryById`を`TopologyDiagram`と`NodeHistoryModal`に渡す配線、クリック/クローズハンドラを追加。

バックエンド・`docs/api/*.md`は変更しない。

## 型・定数設計

`frontend/src/types/monitoring.ts`に追記:

```ts
export interface MonitoringHistorySample {
  timestamp: number
  cpuPercent: number
  memoryPercent: number
}
```

`frontend/src/constants/monitoring.ts`に追記:

```ts
// ノードごとのCPU/メモリ履歴リングバッファの最大保持件数（5秒間隔ポーリングで3分相当）
export const MAX_HISTORY_SAMPLES = 36
```

## ストア設計（monitoringStore.ts）

state追加:

```ts
nodeHistoryById: new Map<string, MonitoringHistorySample[]>(),
selectedNodeId: null as string | null,
```

（`alertedTierByTargetId`と同じ「Pinia option storeでMapをstateに持ち`.set`/`.get`で操作する」既存パターンを踏襲。Vueのreactive proxyがMapの値であるArrayも再帰的にラップするため、取得した配列への`push`/`shift`もリアクティブに検知される。）

アクション追加:

```ts
recordHistorySample(snapshot: MonitoringSnapshot) {
  const timestamp = Date.now()
  for (const node of snapshot.nodes) {
    const series = this.nodeHistoryById.get(node.id) ?? []
    series.push({ timestamp, cpuPercent: node.cpuPercent, memoryPercent: node.memoryPercent })
    if (series.length > MAX_HISTORY_SAMPLES) {
      series.shift()
    }
    this.nodeHistoryById.set(node.id, series)
  }
},
selectNode(nodeId: string) {
  this.selectedNodeId = nodeId
},
clearSelectedNode() {
  this.selectedNodeId = null
},
```

`fetchSnapshot()`内、`this.hasError = false`の直後・`this.updateAlertState(snapshot)`の直前に`this.recordHistorySample(snapshot)`を追加する（既存の成功パスの分岐構造・catch節・世代カウンタ判定は一切変更しない）。

`showChat()`を以下のように変更（モーダルが開いていれば閉じる。`showMonitoring()`は変更不要）:

```ts
showChat() {
  this.activeScreen = 'chat'
  this.selectedNodeId = null
},
```

履歴データ自体（`nodeHistoryById`）はどのアクションでもクリアしないため、FR-9（画面切替をまたいだ履歴保持）は追加実装なしで自然に満たされる。

## TopologyDiagram.vue設計

props変更:

```ts
const props = defineProps<{
  snapshot: MonitoringSnapshot
  historyByNodeId: Map<string, MonitoringHistorySample[]>
}>()
```

emit追加:

```ts
const emit = defineEmits<{ (e: 'node-click', nodeId: string): void }>()
```

既存の`GAUGE_MARGIN`/`GAUGE_WIDTH`/`GAUGE_HEIGHT`の座標定数はそのまま流用し、位置（`y+38`のCPU行、`y+60`のMEM行、テキスト位置）も変更しない。塗りつぶし用の`gaugeFillClass`/`gaugeWidth`関数と、CPU/MEM行の2つの塗りつぶし`<rect>`（現在値幅の帯グラフ）を削除し、代わりに以下を追加する:

```ts
function valueToY(value: number): number {
  const clamped = Math.min(100, Math.max(0, value))
  return GAUGE_HEIGHT - (clamped / 100) * GAUGE_HEIGHT
}

function sparklinePoints(
  history: MonitoringHistorySample[] | undefined,
  metric: 'cpuPercent' | 'memoryPercent',
): string {
  const samples = history ?? []
  if (samples.length === 0) {
    return ''
  }
  if (samples.length === 1) {
    // 1件のみの場合はエラーにせず、水平な短い線として描画する（FR-4）
    const y = valueToY(samples[0][metric])
    return `0,${y} ${GAUGE_WIDTH},${y}`
  }
  return samples
    .map((sample, index) => {
      const x = (index / (samples.length - 1)) * GAUGE_WIDTH
      const y = valueToY(sample[metric])
      return `${x},${y}`
    })
    .join(' ')
}
```

`nodes`computedに`cpuSparklinePoints`/`memorySparklinePoints`を追加（`sparklinePoints(props.historyByNodeId.get(node.id), 'cpuPercent' | 'memoryPercent')`で算出）。

テンプレートは、既存の背景トラック`<rect>`（`fill-zinc-200 dark:fill-zinc-700`）はそのまま「スパークラインの背景」として残し、その上に塗りつぶし`<rect>`の代わりに以下のような`<polyline>`を重ねる（CPU行・MEM行それぞれ、`<g :transform="...">`でノードのローカル座標系に平行移動してから`sparklinePoints`のローカル座標をそのまま使う）:

```html
<g :transform="`translate(${node.x + GAUGE_MARGIN}, ${node.y + 38})`">
  <polyline
    :points="node.cpuSparklinePoints"
    fill="none"
    stroke-width="1.5"
    stroke-linecap="round"
    stroke-linejoin="round"
    :class="LEVEL_STROKE_CLASS[node.cpuLevel]"
  />
</g>
```
（MEM行は`translate(..., ${node.y + 60})`、`node.memorySparklinePoints`、`node.memoryLevel`を使う。）

現在値の%テキスト表示（`{{ Math.round(node.cpuPercent) }}%`等）はそのまま維持する。

ノードクリック: 既存の`<g v-for="node in nodes" :key="node.id">`（ノード矩形とテキストをまとめている`<g>`）に`class="cursor-pointer"`と`@click="emit('node-click', node.id)"`を追加する。エッジ側の`<g>`には触れない（FR-10: エッジはクリック対象にしない）。

## NodeHistoryModal.vue設計（新規: frontend/src/components/monitoring/NodeHistoryModal.vue）

Props/Emit:

```ts
const props = defineProps<{
  node: { id: string; label: string; cpuPercent: number; memoryPercent: number }
  history: MonitoringHistorySample[]
}>()
const emit = defineEmits<{ (e: 'close'): void }>()
```

開閉制御: 本コンポーネント自体は「マウントされている＝開いている」という設計とし、開閉フラグは持たない（`MonitoringView.vue`側の`v-if="selectedNode"`で表示/非表示を制御する。マウント/アンマウントで`onMounted`/`onUnmounted`のキーイベントリスナー登録・解除も自然に対になる）。

閉じる操作（FR-8、複数用意）:
- 右上の閉じるボタン（`type="button" @click="emit('close')"`）
- 背景オーバーレイのクリック（`@click.self="emit('close')"`をオーバーレイ`div`に付与し、モーダル本体パネルへのクリックでは閉じないよう`.self`修飾子を使う）
- Escapeキー（`onMounted`で`window.addEventListener('keydown', onKeydown)`、`onUnmounted`で解除。`onKeydown`は`event.key === 'Escape'`で`emit('close')`）

テンプレート骨格:

```html
<div
  class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
  @click.self="emit('close')"
>
  <div class="w-full max-w-lg rounded-lg bg-white p-5 shadow-xl dark:bg-zinc-900">
    <div class="mb-3 flex items-center justify-between">
      <h3 class="text-sm font-semibold text-zinc-900 dark:text-zinc-100">{{ node.label }}</h3>
      <button type="button" class="text-zinc-500 hover:text-zinc-800 dark:hover:text-zinc-200" @click="emit('close')">
        ✕
      </button>
    </div>
    <!-- チャートSVG -->
    <!-- 凡例 -->
  </div>
</div>
```

チャートSVG（自作、既存`TrendChartView.vue`と同様のライブラリ非依存の自作方式を踏襲。ただし折れ線チャートなので新規に描画ロジックを書く）:

- 固定`viewBox`（例: `0 0 480 220`）、`width="100%"`。
- Y軸0〜100%固定。プロット領域の上下に軸ラベル（`0%`/`50%`/`100%`）と横グリッド線3本を描画してY軸のスケールを可読にする。
- X軸: 明示的な時間目盛りは設けず、プロット領域の左下・右下に最古サンプル・最新サンプルの時刻（`toLocaleTimeString('ja-JP', { hour12: false, ... })`、`MonitoringView.vue`の`lastUpdatedLabel`と同じフォーマットを踏襲）をテキストラベルとして表示する（FR-6の「X軸は蓄積済み履歴データの範囲を表示する」を満たす最小実装）。
- CPU/メモリの2本の折れ線は、スパークラインと同じ`sparklinePoints`相当のロジック（プロット領域の幅・高さにスケール、1件のみなら水平な短い線）をチャート用のサイズで再計算する。ノードのメトリクス種別ごとの色分け（severityベース）とは別に、メトリクス識別用の固定色を新規に定義する（例: CPU=`stroke-sky-500 dark:stroke-sky-400`、メモリ=`stroke-violet-500 dark:stroke-violet-400`）。この2色はコンポーネント内のローカル定数として定義する（他コンポーネントと共有しないため`constants/monitoring.ts`には追加しない）。
- 凡例: チャート下に「● CPU」「● メモリ」を上記固定色のドット＋ラベルで表示。

履歴が空（理論上は発生しないが防御的に）の場合は「データがありません」という短いテキストのみを表示し、SVG座標計算でのゼロ除算・エラーを避ける。

## MonitoringView.vue設計

追加のcomputed:

```ts
const selectedNode = computed(() => {
  if (store.selectedNodeId === null || !store.snapshot) {
    return null
  }
  return store.snapshot.nodes.find((node) => node.id === store.selectedNodeId) ?? null
})
const selectedNodeHistory = computed(() =>
  store.selectedNodeId !== null ? (store.nodeHistoryById.get(store.selectedNodeId) ?? []) : [],
)
```

ハンドラ追加:

```ts
function handleNodeClick(nodeId: string) {
  store.selectNode(nodeId)
}
function handleModalClose() {
  store.clearSelectedNode()
}
```

テンプレート変更:

```html
<TopologyDiagram
  v-if="store.snapshot"
  :snapshot="store.snapshot"
  :history-by-node-id="store.nodeHistoryById"
  @node-click="handleNodeClick"
/>
<NodeHistoryModal
  v-if="selectedNode"
  :node="selectedNode"
  :history="selectedNodeHistory"
  @close="handleModalClose"
/>
```

（`selectedNode`が`null`になった場合〈選択中ノードIDが存在しないスナップショットになった等の防御的ケース含む〉、モーダルは自動的にアンマウントされる。）

## 実装ステップ

各ステップ後に`cd frontend && npx vue-tsc -b`と`npm run build`が通る状態を維持する。

1. **型・定数の追加**: `types/monitoring.ts`に`MonitoringHistorySample`、`constants/monitoring.ts`に`MAX_HISTORY_SAMPLES`を追加。既存コードへの参照はまだ発生しないため、追加のみでビルドは変わらず通る。
2. **ストアの履歴蓄積ロジック**: `monitoringStore.ts`に`nodeHistoryById`/`selectedNodeId`state、`recordHistorySample`/`selectNode`/`clearSelectedNode`アクションを追加し、`fetchSnapshot()`から`recordHistorySample`を呼び出す。`showChat()`に選択解除を追記。既存コンポーネントは未接続のためこの時点で挙動は変わらない（ビルド確認のみ）。
3. **TopologyDiagram.vueのスパークライン化とクリックイベント**: `historyByNodeId` prop・`node-click` emitを追加し、ゲージ描画を`<polyline>`ベースのスパークライン描画に置き換える。この時点では呼び出し元（`MonitoringView.vue`）がまだ新しいpropを渡していないため型エラーになる想定（propが必須になるため）→ 本ステップ内で`MonitoringView.vue`側の呼び出し箇所も最小限（`:history-by-node-id="store.nodeHistoryById"`の配線のみ、クリックハンドラは仮に何もしない関数か次ステップで実装）更新し、ビルドが通る状態にする。ここで手動確認: スパークラインが描画され、既存のゲージバーが表示されないこと・3段階色分けが維持されていることを確認。
4. **NodeHistoryModal.vueの新規作成**: props/emit・オーバーレイ・閉じる操作3種（ボタン/背景クリック/Esc）・折れ線チャートSVG（CPU/メモリ2本、Y軸0-100%固定、凡例、X軸時刻ラベル）を実装する。この時点では未接続の単体コンポーネントとして作成し、型チェック・ビルドが通ることのみ確認する。
5. **結線（MonitoringView.vue）**: `selectedNode`/`selectedNodeHistory` computed、`handleNodeClick`/`handleModalClose`を実装し、`TopologyDiagram`の`@node-click`と`NodeHistoryModal`の描画・`@close`を配線する。ここで手動確認シナリオ（後述）を一通り実施する。

## Risks / edge cases

- **`sparklinePoints`の1件時の描画**: 水平線として描画するため、履歴が1件しかない直後は「フラットな線」に見える（実際の変動ではない）。FR-4は「点または短い線」を許容しており仕様上問題ないが、テスト時に「変動していないように見える」ことをバグと誤認しないよう手動確認シナリオに明記する。
- **`Map`のPinia reactivity**: 既存の`alertedTierByTargetId`と同じパターンのため実績はあるが、`series.push`/`series.shift`で配列を直接ミューテートした後に`.set()`を呼ぶことで、Mapの値取得だけに依存する呼び出し元（`computed`内の`.get()`）でも再描画が確実に走るようにする（配列参照は変わらないが、値の中身がreactive proxy経由でミューテートされるため検知される設計。念のため`.set()`を明示的に呼びトリガーを確実にする）。
- **ノードクリックのヒットエリア**: ノードの`<g>`全体（矩形＋テキスト）にクリックを付けるため、テキスト要素上のクリックでも発火する。誤操作防止のための特別なガードは要件上不要（Non-goalsにツールチップ等の追加インタラクションは含まれない）。
- **モーダル表示中のポーリング**: `MonitoringView.vue`のポーリング開始/停止ロジック（`onMounted`/`onUnmounted`）は変更しないため、モーダルの開閉に関わらずポーリングは継続する（FR-7は自動的に満たされる。モーダルは`store.snapshot`/`store.nodeHistoryById`の変化に対してcomputed経由で自動再描画される）。
- **`selectedNodeId`が指す対象がスナップショットから消える場合**: 本機能のトポロジーは9ノード固定（バックエンド無変更）のため実運用上発生しないが、`selectedNode`computedが`null`を返す設計により、万一発生してもエラーにならずモーダルが自動的に閉じる。
- **ダークモード**: スパークラインは既存の`LEVEL_STROKE_CLASS`（`dark:`バリアント込み）を流用するため追加対応不要。モーダルチャートの新規固定色（CPU=sky, メモリ=violet）は`dark:`バリアントを明示的に指定する。
- **メモリ増加**: 9ノード×2系列×36件≈648サンプル程度であり要件定義の「重大な非機能要件: なし」との判断通り軽微。AC-7の確認は`MAX_HISTORY_SAMPLES`を一時的に小さい値（例: 5）に変更して動作確認する手動確認手順とする。

## Test strategy

自動テストランナー未導入のため、`npx vue-tsc -b`・`npm run build`によるビルド健全性確認に加えて、以下の手動確認シナリオで受け入れ基準を検証する（README記載の疎通確認手順に準じ、`docker compose up` または `mvn spring-boot:run` + `npm run dev` でフロント・バックエンドを起動し、ブラウザでモニタリング画面を開く）。

1. モニタリング画面を開き、直後（履歴1件）にスパークライン・ノード枠色・数値表示がエラーなく描画されることを確認（AC-5）。
2. 数十秒〜1分程度表示し続け、各ノードのCPU/メモリ表示がゲージバーではなくスパークライン（折れ線）になっており、推移が視覚的にわかることを確認（AC-1）。
3. いずれかのノード（矩形部分・テキスト部分どちらでも）をクリックし、モーダルが開いてCPU/メモリの2本の折れ線が1つのチャートに重ねて表示され、凡例でCPU/メモリが判別できることを確認（AC-2）。
4. モーダルを開いたまま5秒以上（できれば10〜15秒）待ち、チャートに新しいサンプルが追加され折れ線が伸びることを確認（AC-3）。
5. モーダルの閉じるボタン・背景クリック・Escapeキーそれぞれで、モーダルが閉じて通常のモニタリング画面表示に戻ることを確認（AC-4）。
6. サイドバーからチャット画面に切り替え、再度モニタリング画面に戻り、切り替え前に蓄積されていたスパークラインの履歴（波形の連続性）が引き継がれていることを確認（AC-6）。モーダルを開いた状態でチャットへ切り替えた場合にモーダルが自動的に閉じることも合わせて確認する。
7. `MAX_HISTORY_SAMPLES`を一時的に5などの小さい値に変更してビルドし、3分未満の短時間でもリングバッファの上限が効いて古いサンプルから破棄される（スパークライン・モーダルチャートの表示件数が増え続けないこと、ブラウザのメモリ使用量が単調増加し続けないこと）を確認後、値を36に戻す（AC-7）。
8. 既存機能の退行確認: トポロジー全体表示（9ノード・12エッジ）、エッジの帯域表示＋3段階色分け、ノード枠の3段階色分け、5秒ごとのポーリング更新、意図的なバックエンド停止時のエラーバナー表示、アラートバナーのクリック導線（原因分析チャット遷移）が本機能追加前と同様に動作することを確認（AC-8）。
9. Chrome/Firefox等でOS/ブラウザのダークモードを切り替え、ライト/ダーク両方でスパークライン・モーダルチャートの折れ線・グリッド線・凡例が判読できることを確認（画面・UIフローの要件）。

本リポジトリにE2E（Playwright等）は未導入のため、E2Eスモークテストの追加は行わない（導入自体がスコープ外）。

## AC mapping

| AC | 内容 | 実装ステップ | 検証方法 |
|----|------|--------------|----------|
| AC-1 | 数十秒表示でゲージバーがスパークラインに置き換わり推移が見える | Step 3（TopologyDiagramのスパークライン化） | 手動確認シナリオ 2 |
| AC-2 | ノードクリックでモーダルが開き、CPU/メモリ2本の折れ線が1チャートに重畳表示 | Step 3（クリックemit）, Step 4（NodeHistoryModal作成）, Step 5（結線） | 手動確認シナリオ 3 |
| AC-3 | モーダル表示中5秒以上待つとチャートが更新される | Step 2（`recordHistorySample`）, Step 5（結線によるcomputedの自動再評価） | 手動確認シナリオ 4 |
| AC-4 | モーダルを閉じる操作でモーダルが閉じ通常表示に戻る | Step 4（NodeHistoryModalの閉じる操作3種）, Step 5（`handleModalClose`結線） | 手動確認シナリオ 5 |
| AC-5 | 履歴1件しかない直後でもスパークライン・モーダルチャートがエラーにならず描画される | Step 3（`sparklinePoints`の1件時分岐）, Step 4（モーダルチャート側の同ロジック・空配列防御） | 手動確認シナリオ 1 |
| AC-6 | チャット画面往復後も蓄積済み履歴がスパークラインに反映される | Step 2（`showChat()`で履歴自体はクリアしない設計、`selectedNodeId`のみクリア） | 手動確認シナリオ 6 |
| AC-7 | 3分以上表示しても履歴が36件上限を超えて増え続けない | Step 2（`recordHistorySample`のリングバッファトリム） | 手動確認シナリオ 7 |
| AC-8 | 既存モニタリング機能（トポロジー・色分け・ポーリング・エラーバナー・アラートバナー）が退行しない | 全ステップ（既存ロジック非破壊であることをステップごとのビルド確認で担保） | 手動確認シナリオ 8 |
