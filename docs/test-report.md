# テストレポート: システム構成図モニタリング画面

対象要件: `docs/requirements/system-monitoring-view.md`（受け入れ基準 AC-1〜AC-8）
対象実装: `docs/implementation-notes.md` の「システム構成図モニタリング画面」節（Step 1-3・4-5・6-7）
検証実施日: 2026-07-18

> 本ファイルは前機能（サイドバー定型レポートボタン）のレポートを上書きしたものであり、最新の検証結果のみを反映する。

## Commands run

1. バックエンド自動テスト（全件）
   ```
   cd backend
   ./mvnw.cmd test
   ```
2. フロントエンド型検証（lessons-learned.mdの教訓どおり bare `--noEmit` ではなく `-b` を使用）
   ```
   cd frontend
   npx vue-tsc -b
   ```
3. フロントエンド本番ビルド
   ```
   cd frontend
   npm run build
   ```
4. API実挙動確認（バックエンドを別ポート8081で起動）
   ```
   cd backend
   SERVER_PORT=8081 ./mvnw.cmd spring-boot:run
   curl http://localhost:8081/api/monitoring/snapshot   (複数回)
   ```
   検証後、起動した java プロセス（PID 3908、`Get-NetTCPConnection -LocalPort 8081` で特定）のみを `Stop-Process` で停止。8080番ポートの既存プロセス（本タスクと無関係）には一切触れていない。

## Result

- バックエンド: **全26件パス**（既存25件＋今回追加した1件）。`BUILD SUCCESS`。
- フロントエンド型検証: **EXIT_CODE=0**（エラーなし）。
- フロントエンドビルド: **成功**（`vite v7.3.6`、45 modules transformed、`dist/` 生成）。
- API実挙動確認: **正常**。スキーマ一致・値域0〜100・呼び出しごとの値変動を実機で確認。
- AC-3〜AC-8（ブラウザでの手動確認項目）: **未実施**（自動検証不能。下記「手動確認手順（未実施）」に具体的ステップを整理）。

## 追加したテスト

既存の `MonitoringMetricsServiceTest`（2件）・`MonitoringControllerTest`（1件）はAC-1・AC-2の基本的な値域・変動検証をカバーしていたが、以下の観点が手薄だったため追加した。

1. `backend/src/test/java/com/example/chatbackend/MonitoringMetricsServiceTest.java`
   - 新規 `firstSnapshotMatchesFixedTopologyIdsLabelsAndConnections()` — 設計書・`docs/monitoring-response-schema.md` の固定トポロジー表（9ノードのid/label、12エッジのid/source/target）と実際のスナップショットが完全一致することを検証（従来は件数とID集合への包含のみで、具体的なid/label/接続関係までは未検証だった）。
2. `backend/src/test/java/com/example/chatbackend/MonitoringControllerTest.java`
   - `snapshotReturnsFixedTopologyWithMetricsInRange()` に `nodes[8]`（末尾ノード）・`edges[11]`（末尾エッジ）の型・値域アサーションを追加（従来は `nodes[0]`/`edges[0]` の先頭要素のみの確認で、配列全体のフィールド網羅性が担保されていなかった）。

いずれもグリーン。既存テスト構成・スタイル（AssertJ / MockMvc + jsonPath、既存の `MockChatServiceTest`/`ChatControllerTest` と同スタイル）を踏襲した。

## 実行結果の詳細

### 1. バックエンド `./mvnw.cmd test`

```
[INFO] Running com.example.chatbackend.ChatBackendApplicationTests
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.example.chatbackend.ChatControllerTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.example.chatbackend.MockChatServiceTest
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.example.chatbackend.MonitoringControllerTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.example.chatbackend.MonitoringMetricsServiceTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0

[INFO] Results:
[INFO] Tests run: 26, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

既存の `ChatBackendApplicationTests`/`ChatControllerTest`/`MockChatServiceTest` も含めて全件グリーンであり、モニタリング機能の追加によるチャット機能側の回帰は確認されなかった。

### 2. フロントエンド `npx vue-tsc -b`

`EXIT_CODE=0`。エラー出力なし。

### 3. フロントエンド `npm run build`

```
> frontend@0.0.0 build
> vue-tsc -b && vite build

vite v7.3.6 building client environment for production...
✓ 45 modules transformed.
dist/index.html                  0.46 kB │ gzip:  0.29 kB
dist/assets/index-ClkQIbXb.css  23.19 kB │ gzip:  5.30 kB
dist/assets/index-DqC4veOt.js   89.48 kB │ gzip: 33.55 kB
✓ built in 2.58s
EXIT_CODE=0
```

### 4. API実挙動確認（`SERVER_PORT=8081`）

- 起動直後、`curl http://localhost:8081/api/monitoring/snapshot` は `HTTP_CODE=200`。
- レスポンス構造は `docs/monitoring-response-schema.md` と一致: `nodes` 9件（`id`/`label`/`cpuPercent`/`memoryPercent`）、`edges` 12件（`id`/`sourceId`/`targetId`/`bandwidthPercent`）。id・label・接続関係（`internet→lb→web-1/2→app-1/2→cache/db-primary→db-replica` のフルメッシュ含む）もドキュメント記載の表と一致。
- 短間隔（1〜2秒間隔）で計4回連続呼び出しした結果、全ノード・全エッジの数値が呼び出しごとに変動していることを確認（例: `internet.cpuPercent` が `20.06 → 23.94 → 22.99 → 21.53` のように毎回異なる値）。すべての値は目視で0〜100の範囲内。
- 8080番ポートには本タスクと無関係な既存プロセスが `404` を返す状態で待ち受けていたため、指示どおり `SERVER_PORT=8081` で別プロセスとして起動した。検証後、`Get-NetTCPConnection -LocalPort 8081` で特定した自プロセス（PID 3908、`java.exe`）のみを `Stop-Process -Force` で停止し、`curl` が接続拒否（`000`）になることを確認して停止を確認した。8080番の既存プロセスには一切干渉していない。

## Failures

なし。バックエンド全テスト・型検証・ビルド・API実機確認のいずれにも失敗はなかった。

## 手動確認手順（未実施）

以下はブラウザでの目視確認が必要なため、本検証（自動化エージェント）では実施していない。実装者または次工程担当者が下記手順で確認すること。

**前提**: `cd backend && ./mvnw.cmd spring-boot:run`（8080番）と `cd frontend && npm run dev` を起動し、Viteの `/api` プロキシ経由でブラウザからバックエンドへ到達できる状態にする。

- **AC-3**（サイドバーのボタンでモニタリング画面に切り替わり、構成図・ゲージ・数値が表示される。チャットに戻ると元の会話がそのまま表示される）
  1. チャット画面で何か1往復会話しておく。
  2. サイドバーの「システムモニタリング」ボタンを押す → メイン領域が構成図（9ノード・12エッジのSVG）に切り替わり、選択中の見た目になることを確認。
  3. サイドバーの「New Chat」または既存会話の選択、あるいは定型レポートボタンを押す → チャット画面に戻り、手順1で行った会話がそのまま表示されていることを確認。

- **AC-4**（5秒ごとに数値・ゲージが更新され、最終更新時刻が進む）
  1. モニタリング画面を開いたまま30秒程度観察し、5秒間隔でノードの数値・ゲージ幅が変化し、画面上部の「最終更新: HH:mm:ss」が進むことを確認（ページリロードなしで更新されること）。

- **AC-5**（黄・赤への色変化とその後の正常色復帰）
  1. モニタリング画面を数分間開いたままにし、いずれかのノード枠またはエッジが黄（70%以上）または赤（90%以上）に変化し、その後緑に戻ることを確認。
  2. 発生が遅い場合は `MonitoringMetricsService` の `SPIKE_MEAN_INTERVAL_TICKS`/`SPIKE_DURATION_TICKS` を一時的に小さくして確認を早めてよい（確認後は既定値に戻すこと）。

- **AC-6**（バックエンド停止時のエラーバナー維持、再起動での自動復帰）
  1. モニタリング画面表示中にバックエンドプロセスを停止する。
  2. 直前の構成図がそのまま表示され続け、接続エラーバナーが表示されることを確認。
  3. バックエンドを再起動し、次のポーリング成功時にバナーが消えて更新が再開することを確認。

- **AC-7**（チャット画面表示中は新APIへのリクエストが発生しない）
  1. ブラウザDevToolsのNetworkタブを開いた状態でチャット画面を数十秒観察し、`/api/monitoring/snapshot` へのリクエストが一切発生しないことを確認。

- **AC-8**（必須フィールド欠落応答時にクラッシュせずエラー扱いになる）
  1. DevToolsのローカルオーバーライド機能（または `MonitoringMetricsService` を一時改変）で `/api/monitoring/snapshot` のレスポンスから必須フィールド（例: `nodes[0].cpuPercent`）を欠落させる。
  2. フロントエンドがJSエラーでクラッシュせず、バナーまたは全面エラー表示になることを確認。確認後は変更を元に戻す。

## 受け入れ基準カバレッジ

| AC | 内容 | 判定 | 根拠 |
|---|---|---|---|
| AC-1 | GETエンドポイントで9ノード・12エッジ、全メトリクス0〜100のJSON | **合格（自動テスト＋実機確認）** | `MonitoringMetricsServiceTest.firstSnapshotReturnsFixedTopologyWithAllMetricsInRange`（ノード9/エッジ12・値域）、`firstSnapshotMatchesFixedTopologyIdsLabelsAndConnections`（今回追加、id/label/接続の完全一致）、`MonitoringControllerTest`（HTTPレベル、先頭・末尾要素の型・値域）がいずれもグリーン。加えて `curl http://localhost:8081/api/monitoring/snapshot` の実レスポンスでも構造・値域を確認済み。 |
| AC-2 | 連続呼び出しで値が変動し常に0〜100 | **合格（自動テスト＋実機確認）** | `MonitoringMetricsServiceTest.repeatedCallsVaryWhileStayingWithinRange`（30回連続呼び出しで全値0〜100かつ少なくとも1系列が変化）がグリーン。加えて `curl` を4回連続実行し、全ノード・全エッジの値が毎回変動していることを実機で確認済み。 |
| AC-3 | サイドバーで画面切替、構成図・ゲージ・数値表示、チャット復帰時に会話維持 | **手動確認待ち** | 自動検証不能。手動確認手順を上記に記載。`App.vue`/`Sidebar.vue`/`MonitoringView.vue`/`TopologyDiagram.vue` の実装（`docs/implementation-notes.md` Step 6-7）はコードレビューベースでは要件を満たす構成になっているが、ブラウザでの実機確認は未実施。 |
| AC-4 | 5秒ごとの非リロード更新・最終更新時刻の進行 | **手動確認待ち** | 自動検証不能。`monitoringStore.ts` の `startPolling`（`window.setInterval`, `POLL_INTERVAL_MS=5000`）はコード上確認済みだが、ブラウザでの実挙動確認は未実施。 |
| AC-5 | 黄・赤への変化とその後の正常復帰 | **手動確認待ち** | 自動検証不能。バックエンド側のスパイクロジックは自動テストの30回連続呼び出しで統計的に発火していると推定されるが（`SPIKE_MEAN_INTERVAL_TICKS=12±6`）、色変化の目視確認は未実施。 |
| AC-6 | バックエンド停止時のエラーバナー維持と再起動後の自動復帰 | **手動確認待ち** | 自動検証不能。`monitoringStore.fetchSnapshot()` の `catch` で `hasError=true` のみ設定し `snapshot` を書き換えない実装（コードレビューで確認）だが、ブラウザでの実機確認は未実施。 |
| AC-7 | チャット画面表示中は新APIへのリクエストが発生しない | **手動確認待ち** | 自動検証不能。DevTools Networkタブでの確認が必須（設計書にも明記）。`MonitoringView.vue` の `onMounted`/`onUnmounted` によるポーリング一本化はコードレビューで確認済みだが、実機確認は未実施。 |
| AC-8 | 必須フィールド欠落応答時にクラッシュせずエラー扱い | **手動確認待ち** | 自動検証不能（フロントに自動テスト基盤なし、新規導入もタスク範囲外）。`monitoringApi.ts` の `isValidMonitoringNode`/`isValidMonitoringEdge`/`validateMonitoringSnapshot` はコードレビューで要件どおりの検証ロジックであることを確認したが、実際の不正応答を用いたブラウザでの動作確認は未実施。 |

## 補足

- フロントエンドには自動テスト基盤（vitest等）が導入されていないため、本タスクの指示どおり新規導入は行わず、型検証（`vue-tsc -b`）とビルド（`npm run build`）のみを自動チェックとした。
- `lessons-learned.md` の教訓（bare `vue-tsc --noEmit` は0ファイルしかチェックしないため使わない）を適用し、`npx vue-tsc -b` および `npm run build`（内部で `vue-tsc -b` を実行）で検証した。

## 再検証（レビュー指摘対応後）

対応差分: (1) `frontend/src/stores/monitoringStore.ts` — `startPolling()` ごとにインクリメントする `fetchGeneration` 世代カウンタを追加し、`fetchSnapshot()` の `await` 解決時に `requestGeneration !== this.fetchGeneration` なら結果（成功・失敗いずれも）を破棄するよう変更（`stopPolling()`→再度`startPolling()`が短時間で起きた場合に、古いin-flightリクエストの応答が新しい世代の状態を上書きするレースコンディションの修正）。(2) `docs/monitoring-response-schema.md` — 未来形の記述を実装済みの現在形に修正（ドキュメントのみ、コード変更なし）。

### Commands run

```
cd frontend
npx vue-tsc -b
npm run build

cd backend
./mvnw.cmd test
```

### Result

| コマンド | 結果 |
|---|---|
| `npx vue-tsc -b` | EXIT_CODE=0（エラーなし） |
| `npm run build` | 成功（`vite v7.3.6`、45 modules transformed、`built in 927ms`、`dist/` 再生成） |
| `./mvnw.cmd test`（全件） | `Tests run: 26, Failures: 0, Errors: 0, Skipped: 0` — `BUILD SUCCESS`（バックエンドは無変更のため件数・内訳とも前回と同一。回帰なしを再確認） |

失敗: なし。

### コードレビュー所見（`monitoringStore.ts` の修正内容）

- `fetchGeneration` は `startPolling()` 内でのみインクリメントされ、`fetchSnapshot()` は呼び出し開始時点の世代番号を `requestGeneration` として保持し、`await` 解決後に現在の世代と比較して不一致なら成功・失敗いずれの分岐でも早期returnして状態を書き換えない実装になっている（成功時の `snapshot`/`lastUpdatedAt`/`hasError=false` 更新、失敗時の `hasError=true` 設定の両方をガード）。
- `stopPolling()` 自体は `fetchGeneration` をインクリメントしないため、「ポーリング停止中に古いリクエストが解決した場合」は世代不一致にならず状態が書き換わりうるが、`MonitoringView.vue` は `onUnmounted` で `stopPolling()` を呼ぶのみでコンポーネント自体がアンマウントされるため実害はない（画面に表示されない状態への書き込みであり、次回 `onMounted`→`startPolling()` 時に `fetchGeneration` がインクリメントされ、以降のin-flight応答は正しくフィルタされる）。指摘された「古いレスポンスが新しい世代の状態を上書きする」レースは解消されていることをコードレビューで確認した。
- 型検証・ビルドの通過により、TypeScript上の整合性（`fetchGeneration: number` の状態追加、比較ロジックの型）にも問題がないことを確認した。

### AC判定への影響

AC-1〜AC-8のいずれの判定にも変化なし。

- AC-1・AC-2: バックエンド無変更のため引き続き**合格**（自動テスト26件グリーン、内容は前回と同一）。
- AC-3〜AC-8: 引き続き**手動確認待ち**。今回の修正はポーリングのレースコンディション対策であり、主にAC-4（5秒ごとの更新）・AC-6（バックエンド停止/再起動時の復帰）の裏側の堅牢性に関わる変更だが、ブラウザでの実機確認（画面を素早く切り替えた際に古いデータで上書きされないこと）は本検証では実施していない。上記「手動確認手順（未実施）」のAC-4・AC-6確認時に、あわせてモニタリング画面を素早く開閉（`showMonitoring`→即座に`showChat`→即座に`showMonitoring`）しても表示が壊れない・古いデータで上書きされないことの確認を推奨する。
