# コードレビュー: ノード別スパークライン＋履歴チャート（Issue #12）

対象: `git diff 8e5209c50e43cea3b6432ba543302c62be5b7a2f..HEAD`
参照: [requirements.md](./requirements.md) / [design.md](./design.md) / [implementation-notes.md](./implementation-notes.md) / [test-report.md](./test-report.md)

## 結論

**指摘なし（blockingな欠陥は検出されず）。**

`git diff 8e5209c50e43cea3b6432ba543302c62be5b7a2f..HEAD --stat -- backend/` は0行であり、バックエンドは無変更であることを確認済み。`npx vue-tsc -b` / `npm run build` を実行し、型検査・ビルドがともにgreenであることも再確認した。テストレポートに記載の通り、AC-1〜AC-6・AC-8（アラート導線部分）・ダークモード判読性はPlaywrightによる実ブラウザ検証でPASS確認済み。AC-7（36件上限の実測）とAC-8のうちバックエンド停止時のエラーバナー表示のみコードレビューに留まっているが、以下の通りロジック上明確な欠陥は見当たらない。

## 指摘事項の確認観点ごとの結果

1. **世代カウンタ（`fetchGeneration`）とstaleレスポンス対策**（`frontend/src/stores/monitoringStore.ts` L50-68）
   `recordHistorySample(snapshot)`（L60）は、`requestGeneration !== this.fetchGeneration` によるstale判定（L54-56, `return`で早期リターン）の**後**、かつ成功パス内でのみ呼ばれている。catch節側の同様の判定（L63-65）にも影響していない。stale応答に対して誤って履歴が蓄積されるレースは存在しない。既存のポーリング／アラート／stale対策ロジックへの変更も皆無（`updateAlertState`の呼び出し位置・分岐構造は不変）。

2. **`nodeHistoryById`（`Map<string, MonitoringHistorySample[]>`）のPiniaリアクティビティ**（同ファイル L138-148）
   `series.push`/`series.shift`で配列を直接ミューテートした後`.set()`で書き戻すパターンは、Pinia option storeの`state`がVueの`reactive()`で深くラップされる前提のもとで機能する。実際に消費側（`TopologyDiagram.vue`の`nodes`computed内`sparklinePoints()`が`samples.length`/`samples.map`を直接読む、`NodeHistoryModal.vue`の`chartPoints`/`hasHistory`/`oldestTimeLabel`等が`props.history.length`/`props.history[i]`を直接読む）がいずれも配列の中身を直接参照しており、Vueの深いリアクティビティによって再評価される設計になっている。`alertedTierByTargetId`（スカラー値の`.set`のみ）とは厳密には異なるパターンだが、Vue 3のreactive Mapの標準的挙動として妥当であり、実ブラウザ検証（AC-1「Cacheノードが赤い波形として推移」、AC-3「モーダルのX軸最新時刻ラベルが進行」）でも実際に機能することが確認されている。

3. **`TopologyDiagram.vue`のゲージ帯グラフ削除と座標定数の維持**
   `gaugeFillClass`/`gaugeWidth`関数、`LEVEL_FILL_CLASS`、塗りつぶし`<rect>`2つは削除済みで、`grep`でリポジトリ全体を検索しても残存参照はゼロ（デッドコードなし）。`GAUGE_MARGIN`/`GAUGE_WIDTH`/`GAUGE_HEIGHT`（L32-34）、`LEVEL_STROKE_CLASS`（3段階色分け、L20-24）、CPU/MEM行の座標（`y+38`/`y+60`、テキスト位置`y+34`/`y+56`）、`{{ Math.round(node.cpuPercent) }}%`等の数値表示はいずれも欠落なく維持されている。

4. **`NodeHistoryModal.vue`の閉じる操作3種**
   ボタン（L91-97）・背景クリック`.self`修飾子（L86）・Escapeキー（`onMounted`でリスナー登録 L74-76、`onUnmounted`で確実に解除 L78-80）すべて実装されている。リスナーの登録・解除は`window`に対して1回ずつのみで、多重登録・リークの余地はない（コンポーネントは`v-if="selectedNode"`によりマウント/アンマウントが1対1で対応する設計）。

5. **ゼロ除算・空配列・1件のみのケースの防御**
   `TopologyDiagram.vue`の`sparklinePoints()`（L102-121）と`NodeHistoryModal.vue`の`chartPoints()`（L40-56）は、`length === 0`で空文字列、`length === 1`で水平線（`(samples.length - 1)`によるゼロ除算を回避）という同一ロジックを両方に実装しており一貫している。`NodeHistoryModal.vue`はさらに`hasHistory`が`false`の場合SVG自体を描画せず「データがありません」を表示する追加の防御を持つ（L100-105、L58）。ロジックの重複はあるが（後述の軽微な指摘）、防御自体に欠落はない。

6. **FR-9（画面切替をまたいだ履歴保持）・FR-10（エッジ対象外）・FR-2（36件上限）**
   - FR-9: `showChat()`は`selectedNodeId`のみクリアし`nodeHistoryById`はクリアしない（L32-35）。`MonitoringView.vue`はApp.vueで`v-else`により条件マウントされる（`App.vue` L36）が、Piniaストアはシングルトンでコンポーネントのライフサイクルと独立するため、履歴は保持される。実ブラウザ検証でも「モーダルのX軸開始時刻が画面往復後も完全一致」を確認済み。
   - FR-10: エッジ用の`<g v-for="edge in edges">`（`TopologyDiagram.vue` L131）にはクリックハンドラは付与されておらず、クリックハンドラはノード用の`<g v-for="node in nodes">`（L160）にのみ存在する。
   - FR-2: `recordHistorySample()`は`push`後に`if (series.length > MAX_HISTORY_SAMPLES) { series.shift() }`という単純なFIFOトリムを持つ（L138-148）。1回のポーリングで1件しか追加されないため`if`で十分（`while`である必要はない）。

7. **エッジ`<g>`への誤クリックハンドラ付与**
   上記6のFR-10確認の通り、エッジ側`<g>`にクリックハンドラの付与はない。

8. **型定義・定数の配置**
   `MonitoringHistorySample`は`frontend/src/types/monitoring.ts`（L20-24）、`MAX_HISTORY_SAMPLES`は`frontend/src/constants/monitoring.ts`（L4-5）に設計通り配置されている。モーダル内のローカル固定色（`METRIC_STROKE_CLASS`、CPU=sky/メモリ=violet）は`NodeHistoryModal.vue`内のローカル定数として定義されており（L12-15）、`constants/monitoring.ts`への漏れ出しはない（`grep`で確認済み）。

## 軽微な所感（blockingではない）

- `TopologyDiagram.vue`の`sparklinePoints()`と`NodeHistoryModal.vue`の`chartPoints()`はロジックがほぼ同一（`valueToY`のスケール先の高さが異なるのみ）で、コードが重複している。設計書自身もこの重複を認識した上で「新規に描画ロジックを書く」判断をしており、対象コンポーネントの座標系（スパークラインはGAUGE_HEIGHT基準、モーダルはPLOT_HEIGHT基準）が異なるため共通化コストと利益が見合わない小規模な複製と判断でき、指摘とはしない。

## 未実施の検証について

test-report.mdに記載の通り、AC-7（`MAX_HISTORY_SAMPLES`を一時的に小さくした実ブラウザでのリングバッファ上限到達確認）とAC-8のうちバックエンド停止時のエラーバナー表示は実ブラウザでは未実施でコードレビューに留まっている。上記6・7で確認した通りロジック上の欠陥はなく、エラーバナー機構自体も本diffで無変更のため、リスクは低いと判断する。ただし実施が容易な項目のため、公開前に実施することを推奨する（レビュー観点としては新規リスクなし）。
