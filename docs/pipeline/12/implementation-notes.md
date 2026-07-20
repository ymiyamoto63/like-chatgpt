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
