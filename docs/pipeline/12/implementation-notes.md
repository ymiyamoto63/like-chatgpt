# Implementation Notes

## ステップ1・2: 型・定数の追加、monitoringStore.tsの履歴蓄積ロジック

### 変更ファイル

- `frontend/src/types/monitoring.ts`: `MonitoringHistorySample`型（`timestamp`/`cpuPercent`/`memoryPercent`）を追加。
- `frontend/src/constants/monitoring.ts`: `MAX_HISTORY_SAMPLES = 36`定数を追加（コメント付き）。
- `frontend/src/stores/monitoringStore.ts`:
  - import追加: `constants/monitoring`から`MAX_HISTORY_SAMPLES`、`types/monitoring`から`MonitoringHistorySample`。
  - state追加: `nodeHistoryById: new Map<string, MonitoringHistorySample[]>()`、`selectedNodeId: null as string | null`。
  - アクション追加: `recordHistorySample(snapshot)`（ノードごとに1サンプル追記し`MAX_HISTORY_SAMPLES`超過分を`shift`でトリム）、`selectNode(nodeId)`、`clearSelectedNode()`。
  - `fetchSnapshot()`の成功パス内、`this.hasError = false`の直後・`this.updateAlertState(snapshot)`の直前に`this.recordHistorySample(snapshot)`呼び出しを追加。既存の分岐構造・catch節・`fetchGeneration`判定ロジックは変更なし。
  - `showChat()`に`this.selectedNodeId = null`を追加（モーダル自動クローズ用）。`showMonitoring()`は無変更。

### 設計からの逸脱

なし。design.mdの「ストア設計（monitoringStore.ts）」節の記述通りに実装。

### スコープ外

- `TopologyDiagram.vue`のスパークライン化・クリックイベント（ステップ3）
- `NodeHistoryModal.vue`新規作成（ステップ4）
- `MonitoringView.vue`の結線（ステップ5）

いずれも本ステップでは着手していない（design.mdの実装ステップ3〜5に該当）。

### 検証結果

- `cd frontend && npx vue-tsc -b` → エラーなし（成功）
- `cd frontend && npm run build` → ビルド成功（`vue-tsc -b && vite build`、58 modules transformed、成功）

## ステップ3: TopologyDiagram.vueのスパークライン化とクリックイベント

### 変更ファイル

- `frontend/src/components/monitoring/TopologyDiagram.vue`:
  - props追加: `historyByNodeId: Map<string, MonitoringHistorySample[]>`（`../../types/monitoring`から`MonitoringHistorySample`をimport）。
  - emit追加: `defineEmits<{ (e: 'node-click', nodeId: string): void }>()`。
  - `gaugeFillClass`/`gaugeWidth`関数、CPU/MEM行の塗りつぶし`<rect>`2つを削除。代わりに`valueToY`/`sparklinePoints`関数を追加し、`nodes`computed内で各ノードに`cpuSparklinePoints`/`memorySparklinePoints`を算出して付与。
  - テンプレート: 既存の背景トラック`<rect>`はそのまま残し、その上にCPU行・MEM行それぞれ`<g :transform="translate(...)">`でノードのローカル座標系に平行移動した`<polyline>`（`LEVEL_STROKE_CLASS`で3段階色分け）を追加。数値テキスト表示は変更なし。
  - ノードの`<g v-for="node in nodes">`に`class="cursor-pointer"`と`@click="emit('node-click', node.id)"`を追加（エッジ側の`<g>`は無変更）。
  - `gaugeFillClass`のみで使われていた`LEVEL_FILL_CLASS`定数は、削除後は未使用となり`noUnusedLocals`エラーになるため合わせて削除（design.mdに明記はないが、設計の意図（塗りつぶしrect廃止）から自然に導かれる差分）。
- `frontend/src/components/monitoring/MonitoringView.vue`:
  - `TopologyDiagram`に`:history-by-node-id="store.nodeHistoryById"`と`@node-click="handleNodeClick"`を追加。
  - 仮実装として`handleNodeClick(_nodeId: string)`（本体は何もしない、TODOコメントのみ）を追加。引数名を`_nodeId`にすることで`noUnusedParameters`エラーを回避。モーダル結線は後続ステップ（4・5）で実装予定。

### 設計からの逸脱

- `LEVEL_FILL_CLASS`定数の削除（上記の通り、`noUnusedLocals: true`設定下でのビルドエラー回避のため）。design.mdの記述通りに`gaugeFillClass`/`gaugeWidth`と塗りつぶし`<rect>`を削除した結果として必要になった差分であり、意図の変更はない。
- それ以外はdesign.mdの「TopologyDiagram.vue設計」節の記述通り。

### スコープ外

- `NodeHistoryModal.vue`新規作成（ステップ4）
- `MonitoringView.vue`のモーダル本結線（`selectedNode`/`selectedNodeHistory` computed、`store.selectNode`呼び出し、`NodeHistoryModal`描画）（ステップ5）
- 手動確認シナリオのうちノードクリックでモーダルが開く動作確認（モーダル未実装のためステップ5以降で実施）

### 検証結果

- `cd frontend && npx vue-tsc -b` → エラーなし（成功）
- `cd frontend && npm run build` → ビルド成功（`vue-tsc -b && vite build`、58 modules transformed、成功）
- `npm run dev`でdevサーバー起動を確認（`VITE v7.3.6 ready`、エラーなしで起動）。ただし本セッションはブラウザ操作ツールを持たないため、スパークライン描画・3段階色分け・クリック時の無エラーについてのブラウザ上での目視確認は実施できていない。コードレビュー上は design.md 通りの実装であり、型検査・ビルドはエラーなしで通過している。ユーザー側での目視確認（`npm run dev` + バックエンド起動）を推奨する。

## ステップ4: NodeHistoryModal.vueの新規作成

### 変更ファイル

- `frontend/src/components/monitoring/NodeHistoryModal.vue`（新規）:
  - props: `node: { id, label, cpuPercent, memoryPercent }`、`history: MonitoringHistorySample[]`。emit: `close`。
  - 開閉制御は持たず、マウント＝表示として実装（呼び出し側の`v-if`前提）。
  - 閉じる操作3種を実装: 右上の閉じるボタン（`@click="emit('close')"`）、背景オーバーレイの`@click.self="emit('close')"`、`onMounted`/`onUnmounted`での`keydown`リスナー登録・解除（Escapeキーで`emit('close')`）。
  - チャートSVG: `viewBox="0 0 480 220"`固定、プロット領域を`PLOT_MARGIN_*`定数で余白確保（左32/右8/上12/下28）。Y軸0〜100%固定、横グリッド線3本（0%/50%/100%）とY軸ラベルを描画。X軸は明示目盛りなしで、プロット領域左下・右下に最古・最新サンプル時刻（`toLocaleTimeString('ja-JP', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false })`、`MonitoringView.vue`の`lastUpdatedLabel`と同じフォーマット）を表示。
  - CPU/メモリの折れ線は、TopologyDiagram.vueの`sparklinePoints`と同様のロジック（`valueToY`/`chartPoints`関数、1件のみなら水平線、0件なら空文字列）をこのコンポーネント専用のプロット領域サイズ（`PLOT_WIDTH`/`PLOT_HEIGHT`）でローカルに実装（共有インポートはしていない）。
  - メトリクス識別用固定色（CPU=`stroke-sky-500 dark:stroke-sky-400`、メモリ=`stroke-violet-500 dark:stroke-violet-400`）をコンポーネント内ローカル定数`METRIC_STROKE_CLASS`として定義（`constants/monitoring.ts`には追加していない）。
  - 凡例（● CPU／● メモリ、対応する固定色のドット）をチャート下に表示。
  - `history`が空配列の場合は`hasHistory` computedが`false`となり、SVGを描画せず「データがありません」テキストのみを表示する防御を実装。

### 設計からの逸脱

なし。design.mdの「NodeHistoryModal.vue設計」節の記述通りに実装。

### スコープ外

- 他コンポーネント（`MonitoringView.vue`）からの呼び出し・結線（`selectedNode`/`selectedNodeHistory` computed、`handleModalClose`、`<NodeHistoryModal>`のテンプレート配置）は本ステップでは未着手（design.mdの実装ステップ5で別途実施予定）。
- 本コンポーネントは現時点でどこからも参照されていないため、既存の画面表示・ポーリング・スパークライン等の挙動に変化はない。

### 検証結果

- `cd frontend && npx vue-tsc -b` → エラーなし（成功）
- `cd frontend && npm run build` → ビルド成功（`vue-tsc -b && vite build`、58 modules transformed、成功）
- `git status --porcelain` → `frontend/src/components/monitoring/NodeHistoryModal.vue`の新規追加のみ。既存ファイルへの変更なし。

## ステップ5: MonitoringView.vueの結線

### 変更ファイル

- `frontend/src/components/monitoring/MonitoringView.vue`:
  - `import NodeHistoryModal from './NodeHistoryModal.vue'`を追加。
  - computed追加: `selectedNode`（`store.selectedNodeId`と`store.snapshot`から選択中ノードを`find`で導出、該当なしは`null`）、`selectedNodeHistory`（`store.nodeHistoryById.get(store.selectedNodeId)`、未選択時は空配列）。
  - 仮のno-op実装だった`handleNodeClick`を`store.selectNode(nodeId)`を呼ぶ実装に置き換え、`handleModalClose`（`store.clearSelectedNode()`）を新規追加。
  - テンプレートに`<NodeHistoryModal v-if="selectedNode" :node="selectedNode" :history="selectedNodeHistory" @close="handleModalClose" />`を追加（`TopologyDiagram`の直後）。既存の`TopologyDiagram`の`@node-click="handleNodeClick"`配線・アラートバナー・エラーバナー・ポーリング（`onMounted`/`onUnmounted`）ロジックは無変更。

### 設計からの逸脱

なし。design.mdの「MonitoringView.vue設計」節の記述通りに実装。

### スコープ外

なし（design.mdの実装ステップは本ステップで全5ステップ完了）。

### 検証結果

- `cd frontend && npx vue-tsc -b` → エラーなし（成功）
- `cd frontend && npm run build` → ビルド成功（`vue-tsc -b && vite build`、60 modules transformed、成功）
- `npm run dev` → `VITE v7.3.6 ready`、エラーなしで起動を確認。ただし本セッションはブラウザ操作ツールを持たないため、ノードクリック→モーダル表示→チャート描画→3種の閉じる操作→チャット画面往復後の履歴継続、といった一連のUIフローのブラウザ上での目視確認は実施できていない。型チェック・ビルドは通過しており、コードレビュー上もdesign.mdの記述通りにデータフロー（`store.selectedNodeId`→`selectedNode`/`selectedNodeHistory`→`NodeHistoryModal`のprops）が型として矛盾なく繋がっていることを確認した。requirements.mdの「Test strategy」に記載の手動確認シナリオ1〜9は、ユーザー側で`docker compose up`または`mvn spring-boot:run` + `npm run dev`を起動し、ブラウザで実施することを推奨する。
- `git diff --stat` → 変更は`frontend/src/components/monitoring/MonitoringView.vue`のみ（24行追加・2行削除）。design.mdで想定されていない意図しない変更は含まれていない。

## 全ステップ完了サマリ

design.md「実装ステップ」節の5ステップすべてが完了した。design.mdの「Files affected」に記載された全ファイルの変更/新規作成状況は以下の通り、すべて対応済み。

| ファイル | 対応状況 |
|---|---|
| `frontend/src/types/monitoring.ts` | 変更済み（ステップ1: `MonitoringHistorySample`型追加） |
| `frontend/src/constants/monitoring.ts` | 変更済み（ステップ1: `MAX_HISTORY_SAMPLES`定数追加） |
| `frontend/src/stores/monitoringStore.ts` | 変更済み（ステップ2: `nodeHistoryById`/`selectedNodeId`state、`recordHistorySample`/`selectNode`/`clearSelectedNode`アクション、`showChat()`の選択解除追加） |
| `frontend/src/components/monitoring/TopologyDiagram.vue` | 変更済み（ステップ3: `historyByNodeId` prop、`node-click` emit、スパークライン描画への置き換え） |
| `frontend/src/components/monitoring/NodeHistoryModal.vue` | 新規作成済み（ステップ4） |
| `frontend/src/components/monitoring/MonitoringView.vue` | 変更済み（ステップ3で仮配線、ステップ5で本結線完了） |

バックエンド・`docs/api/*.md`は設計通り無変更。各ステップで`npx vue-tsc -b`・`npm run build`が通ることを確認済み。ブラウザでの手動確認（AC-1〜AC-8に対応する手動確認シナリオ1〜9）は本セッションでは実施できていないため、ユーザー側での最終確認を推奨する。
