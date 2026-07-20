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
