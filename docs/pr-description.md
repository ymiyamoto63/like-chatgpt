# PR Title

システム構成図モニタリング画面とトレンドチャートを追加

# PR Body

## Summary
- サイドバーから切り替えられるシステム構成図モニタリング画面を新設。バックエンドの新規 `GET /api/monitoring/snapshot` が固定トポロジー（3層Web構成・9ノード/12エッジ）に対しランダムウォーク＋周期的スパイクでCPU/メモリ/帯域使用率のモックメトリクスを生成し、フロントエンドは自作SVG（`TopologyDiagram.vue`）でゲージと3段階色分け（正常/警戒/危険）を描画、表示中のみ5秒間隔でポーリングする。既存の `POST /api/chat` 契約には変更を加えていない。
- あわせて、日別問い合わせ件数の推移を平均線付きで表示する新シナリオ（トレンドチャート）を追加し、既存UIの手書きCSSをTailwind CSS 4ベースへ移行した。
- 本PRには先行してマージ済みの過去2機能（サイドバー定型レポートボタン: `17be5b0`、新規問い合わせウィザード（choicesコンポーネント）: `a125a89`）も含まれる（`feature/sidebar-report-buttons` ブランチの累積差分としてPR化しているため）。

## 変更内容（コミット単位）
1. **トレンドチャート表示とUIをTailwind CSSへ移行**: `MockChatService`/`UiComponent`/新規 `TrendChartComponent`（バックエンド）、`TrendChartView.vue`（フロントエンド）、既存コンポーネント群のTailwind化、`docs/chat-response-schema.md` の更新。
2. **システム構成図モニタリング画面を追加**: `MonitoringNode`/`MonitoringEdge`/`MonitoringSnapshot`/`MonitoringMetricsService`/`MonitoringController`（バックエンド新規）、`monitoringStore.ts`/`monitoringApi.ts`/`TopologyDiagram.vue`/`MonitoringView.vue`（フロントエンド新規）、`App.vue`・`Sidebar.vue` の画面切替配線、要件定義・設計・実装ノート・レビュー・テストレポート（`docs/`)。

## レビュー対応
- レビューで指摘された `monitoringStore.ts` のポーリング停止後の stale レスポンスによる状態上書きレースは、`fetchGeneration` 世代カウンタの導入で解消済み（`docs/lessons-learned.md` に教訓として記録、再テスト・再レビューでグリーン確認）。レビューにblocking指摘は残っていない。

## Test plan
- [x] バックエンド自動テスト全件グリーン（`cd backend && ./mvnw.cmd test` — 26件、既存25件＋モニタリング関連1件追加、失敗なし）
- [x] フロントエンド型検証グリーン（`cd frontend && npx vue-tsc -b`、bare `--noEmit` は既知の理由により不使用）
- [x] フロントエンド本番ビルド成功（`cd frontend && npm run build`）
- [x] API実挙動確認（別ポート8081で起動し `curl /api/monitoring/snapshot` を複数回実行、スキーマ一致・値域0〜100・呼び出しごとの値変動を確認）
- [ ] AC-3（サイドバーでの画面切替・チャット復帰時の会話維持）— 手順は `docs/test-report.md` に記載済み、ブラウザでの手動確認は未実施
- [ ] AC-4（5秒間隔のポーリング更新・最終更新時刻の進行）— 同上、未実施
- [ ] AC-5（黄・赤への色変化とその後の正常復帰）— 同上、未実施
- [ ] AC-6（バックエンド停止時のエラーバナー維持・再起動での自動復帰）— 同上、未実施
- [ ] AC-7（チャット画面表示中は新APIへのリクエストが発生しない）— 同上、未実施
- [ ] AC-8（必須フィールド欠落応答時にクラッシュせずエラー扱い）— 同上、未実施

詳細な検証コマンド・結果・未実施の手動確認手順は `docs/test-report.md` を参照。

🤖 Generated with [Claude Code](https://claude.com/claude-code)
