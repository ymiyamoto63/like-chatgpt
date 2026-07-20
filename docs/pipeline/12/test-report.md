# テストレポート: ノード別スパークライン＋履歴チャート（Issue #12）

対象要件定義: [requirements.md](./requirements.md)
対象設計ドキュメント: [design.md](./design.md)（「Test strategy」節・「AC mapping」節）
対象実装ノート: [implementation-notes.md](./implementation-notes.md)

## 重要な制約（結論に影響する事項）

**本セッションにはブラウザ操作手段（ヘッドレスブラウザ／Playwright・Puppeteer等）が一切提供されていない。** 環境調査の結果、以下を確認した。

- `frontend/package.json`の`devDependencies`にvitest等のテストランナー・Playwright/Puppeteerは含まれていない（プロジェクト自体が自動テストランナー未導入、要件で明記の通り）。
- リポジトリ・環境内にLinux向けの実行可能なChromium/Chrome/Firefoxバイナリは見つからなかった（`/mnt/c/...`配下にWindows側のChromeは存在するが、WSL2内のこのシェルから起動・操作する手段がない）。
- 本エージェントに与えられたツールはファイル読み書きとシェル実行のみで、画面操作・スクリーンショット取得の手段がない。

このため、design.mdの「Test strategy」節にある**手動確認シナリオ1〜9（AC-1〜AC-8に対応）は、実ブラウザでの目視確認を実施できていない**。要件定義書のAC-1〜AC-8はすべて検証方法が「手動確認」と明記されており、代替できる自動テストも本プロジェクトには存在しない。

代替として以下を実施した。
1. 型検査・ビルド（自動、実行済み・green）。
2. バックエンド／フロントエンド開発サーバーの起動確認とAPI疎通確認（実行済み）。
3. ビルド成果物（`dist`）に新規コンポーネントの文字列が含まれることの確認（実行済み）。
4. 全変更ファイルのコードレビュー（データフローの型的整合性、design.mdとの一致、AC単位の論理的追跡）。

**したがって本レポートの結論は「型検査・ビルドはPASS、手動確認シナリオ1〜9（AC-1〜AC-8）はコードレビューによる推定確認のみで、実ブラウザでの実施は未完了」である。** 実ブラウザでの最終確認は、ユーザー側またはブラウザ操作可能な環境で改めて実施することを強く推奨する。

## Commands run

```bash
# 型検査（bare `vue-tsc --noEmit` は使用せず、リポジトリの罠に従い `-b` を使用）
cd frontend && npx vue-tsc -b

# ビルド
cd frontend && npm run build

# バックエンド疎通確認（既存プロセスを流用。後述）
curl -s -m 5 http://localhost:8080/api/monitoring/snapshot

# フロントエンド開発サーバー起動・疎通確認
cd frontend && npm run dev   # バックグラウンド起動
curl -s -m 5 -o /dev/null -w "HTTP %{http_code}\n" http://localhost:5173/

# ビルド成果物に新規コンポーネントの文言が含まれるかの確認
grep -o "データがありません" frontend/dist/assets/*.js
grep -o "メモリ" frontend/dist/assets/*.js

# ポーリングによる値変化の確認（5秒間隔ポーリングでスナップショットが更新されることの裏付け）
curl -s -m 5 http://localhost:8080/api/monitoring/snapshot   # 1回目
sleep 6
curl -s -m 5 http://localhost:8080/api/monitoring/snapshot   # 2回目

# 作業ツリーに一時変更が残っていないことの確認
git status --porcelain
git diff --stat
```

## Result

| 項目 | 結果 |
|---|---|
| `npx vue-tsc -b` | **PASS**（エラーなし） |
| `npm run build` | **PASS**（`vue-tsc -b && vite build`、60 modules transformed、`dist/`生成成功） |
| バックエンドAPI疎通（`GET /api/monitoring/snapshot`） | **PASS**（既存起動中プロセスに対し200・9ノード12エッジの正常なJSONを確認。詳細は後述） |
| フロントエンド開発サーバー起動（`npm run dev`） | **PASS**（`http://localhost:5173/` へのアクセスでHTTP 200、`#app`を含むHTML返却を確認） |
| ビルド成果物への新規コンポーネント文言の混入確認 | **PASS**（`NodeHistoryModal.vue`の文言がバンドルに含まれることを確認） |
| ポーリング間でのメトリクス値変化確認（バックエンド側） | **PASS**（6秒間隔で全ノードの`cpuPercent`が変化することを確認。ランダムウォークが機能しており、フロントエンドの履歴蓄積ロジックが「毎回異なるサンプル」を積み上げる前提が成立することを裏付け） |
| 手動確認シナリオ1〜9（AC-1〜AC-8・ダークモード確認） | **未実施（実ブラウザでの目視確認）**。理由: 上記「重要な制約」参照。コードレビューによる代替確認は実施済み（後述） |
| 作業ツリーの一時変更の残留確認 | **PASS**（`git status --porcelain`・`git diff --stat`ともに差分なし。`MAX_HISTORY_SAMPLES`は36のまま変更していない） |

### 補足: バックエンドプロセスについて

作業開始時点で`localhost:8080`はすでに稼働中の`ChatBackendApplication`（PID 466525、デバッグエージェント付きJavaプロセス、親プロセスは別のシェル/セッション）が掴んでいた。指示に従い起動元を確認し、既存プロセスを停止・上書きせず、そのまま流用してAPI疎通確認を行った（`GET /api/monitoring/snapshot`が9ノード・12エッジの正常なレスポンスを返すことを確認済み）。フロントエンド開発サーバー（`npm run dev`、port 5173）は本セッションで新規起動し、確認後に停止した（バックエンドは他セッションの利用を想定し停止していない）。

## Failures

なし（自動実行できた範囲＝型検査・ビルド・サーバー疎通確認・ビルド成果物確認では失敗を検出しなかった）。

ただし、手動確認シナリオ1〜9は実ブラウザで未実施のため、**実際の描画・インタラクションに問題がないことは保証できていない**。特に以下はコードレビューだけでは確定できないため、実施を強く推奨する。

- スパークライン・モーダルチャートの実際の視覚的な判読性（座標計算が正しくても、SVGの実描画・CSSクラスの実際の色・線の太さ等はブラウザでしか確認できない）。
- モーダルの3種類の閉じる操作（ボタン／背景クリック／Escapeキー）の実際のイベント発火・伝播（`@click.self`の挙動はブラウザのイベントバブリング仕様に依存する）。
- チャット画面↔モニタリング画面往復時の実際のVueコンポーネントのマウント/アンマウントとPiniaストアの状態保持（コードレビュー上は`nodeHistoryById`をどのアクションもクリアしないため保持される設計だが、実際の画面遷移での確認はしていない）。
- ライト/ダークテーマ切り替え時の実際の配色コントラスト・可読性。
- `MAX_HISTORY_SAMPLES`を一時的に小さくした際の実際のブラウザ上でのスパークライン件数・メモリ使用量の挙動（AC-7）。

## Acceptance criteria coverage

要件定義書のAC-1〜AC-8はすべて検証方法「手動確認」に指定されている。以下は各ACについて、(a) 実ブラウザでの確認状況、(b) 代替として実施したコードレビューの内容、(c) 未実施のブラウザでの具体的な確認手順、をまとめたもの。

| AC | 内容 | ブラウザでの確認 | コードレビューによる代替確認 |
|----|------|------------------|------------------------------|
| AC-1 | 数十秒表示でゲージバーがスパークラインに置き換わり推移が見える | **未実施** | `TopologyDiagram.vue`のテンプレートを確認し、旧`gaugeFillClass`/塗りつぶし`<rect>`（帯グラフ）が完全に削除され、`<polyline :points="node.cpuSparklinePoints">`/`memorySparklinePoints`に置き換わっていることをソースコードで確認（L183-230）。`sparklinePoints()`は`historyByNodeId`の蓄積サンプル数に応じてX座標を等間隔配置しており、サンプルが増えるほど折れ線の解像度が上がる設計。ロジック上は妥当だが実描画確認は未実施。 |
| AC-2 | ノードクリックでモーダルが開き、CPU/メモリ2本の折れ線が1チャートに重畳表示 | **未実施** | ノード`<g>`に`@click="emit('node-click', node.id)"`（TopologyDiagram.vue L160）→`MonitoringView.vue`の`handleNodeClick`が`store.selectNode(nodeId)`を呼ぶ→`selectedNode`computedが`store.snapshot.nodes`から`find`→`NodeHistoryModal`に`v-if="selectedNode"`で描画、という一連のデータフローを型・参照レベルで確認。`NodeHistoryModal.vue`は`cpuPoints`/`memoryPoints`の2本の`<polyline>`を同一`<svg>`内に固定色（sky/violet）で重ねて描画し、凡例（●CPU/●メモリ）も実装されている（L164-209）。ロジック上は要件を満たす実装だが実描画・実クリック動作は未確認。 |
| AC-3 | モーダル表示中5秒以上待つとチャートが更新される | **未実施** | ポーリング（`POLL_INTERVAL_MS=5000`、`startPolling`）は`MonitoringView.vue`の`onMounted`/`onUnmounted`で開始/停止され、モーダルの開閉には一切関与しない設計（`fetchSnapshot`成功時に無条件で`recordHistorySample`→`nodeHistoryById.set`）。`selectedNodeHistory`はcomputedで`store.nodeHistoryById.get(...)`を参照するため、ストア更新のたびに再評価されモーダルへ反映される設計。加えてバックエンドAPIを6秒間隔で2回叩き、実際に`cpuPercent`等の値が変化することを確認済み（例: web-1が92.5%→93.3%）。データが実際に動くこと自体は裏付けたが、モーダルチャートへの反映（Vueの再描画）は未確認。 |
| AC-4 | モーダルを閉じる操作でモーダルが閉じ通常表示に戻る | **未実施** | `NodeHistoryModal.vue`に3種の閉じる操作を確認: 右上ボタン`@click="emit('close')"`（L91-97）、背景オーバーレイ`@click.self="emit('close')"`（L86、`.self`修飾子でパネル内クリックでは閉じない設計）、`onMounted`でのEscapeキー`keydown`リスナー登録・`onUnmounted`での解除（L74-80）。いずれも`close`emit→`MonitoringView.vue`の`handleModalClose`→`store.clearSelectedNode()`→`selectedNodeId=null`→`v-if="selectedNode"`が`false`化、という一貫したフローをソースコード上で確認。実イベント発火・伝播（特に`.self`修飾子の挙動）はブラウザでのみ確定できるため未確認。 |
| AC-5 | 履歴1件しかない直後でもスパークライン・モーダルチャートがエラーにならず描画される | **未実施** | `TopologyDiagram.vue`の`sparklinePoints()`・`NodeHistoryModal.vue`の`chartPoints()`はいずれも`samples.length === 0`で空文字列、`=== 1`で水平線（ゼロ除算回避）を返す分岐を実装済み（TopologyDiagram.vue L107-113、NodeHistoryModal.vue L42-47）。`NodeHistoryModal.vue`は`hasHistory`が`false`の場合SVG自体を描画せず「データがありません」というテキストのみを表示する防御も確認（L100-105）。ロジック上ゼロ除算・例外は発生しない設計だが、実際にブラウザのコンソールにエラーが出ないことの確認は未実施。 |
| AC-6 | チャット画面往復後も蓄積済み履歴がスパークラインに反映される／モーダルを開いた状態でチャットへ切り替えるとモーダルが自動的に閉じる | **未実施** | `monitoringStore.ts`の`showChat()`は`selectedNodeId = null`のみをセットし、`nodeHistoryById`を一切クリアしない（L32-35）。`showMonitoring()`も無変更で状態リセットを行わない。モーダルの表示制御は`MonitoringView.vue`の`v-if="selectedNode"`（`selectedNode`は`store.selectedNodeId`に依存）のため、`showChat()`実行時に自動的に`false`化されモーダルが閉じる設計をソースコード上で確認。Piniaストアはシングルトンでコンポーネントのマウント/アンマウントとは独立して状態を保持するため、`MonitoringView.vue`が一度アンマウントされても`nodeHistoryById`のMapは保持され続ける設計だが、実際の画面遷移（サイドバー操作）での確認は未実施。 |
| AC-7 | 3分以上表示しても履歴が36件上限を超えて増え続けない | **未実施**（`MAX_HISTORY_SAMPLES`を一時的に小さくしたブラウザでの確認は未実施） | `recordHistorySample()`（monitoringStore.ts L138-148）は`series.push(...)`後に`if (series.length > MAX_HISTORY_SAMPLES) { series.shift() }`という明確なFIFOトリム処理を持つ。`MAX_HISTORY_SAMPLES`は`constants/monitoring.ts`で`36`と定義（変更していないことを`git diff`で確認済み）。ロジックは単純かつ正しいと判断できるが、本セッションでは実行可能なテストランナーがなく（`import.meta.env`等Vite依存でNode単体実行も不可）、実データでの上限到達・トリム挙動そのものは自動でも手動でも実行確認できていない。 |
| AC-8 | 既存モニタリング機能（トポロジー・色分け・ポーリング・エラーバナー・アラートバナー）が退行しない | **未実施** | `git diff 8e5209c..HEAD --stat`相当の変更ファイル一覧を確認済み（本報告冒頭の変更範囲通り）。`MonitoringView.vue`のアラートバナー（`store.pendingAlerts`）・エラーバナー（`store.hasError`）・`onMounted`/`onUnmounted`のポーリング開始/停止ロジックは今回のdiffで一切変更されていない（`git diff`上、追加は`import`・computed 2件・`handleNodeClick`実装変更・`handleModalClose`追加・テンプレートへの`NodeHistoryModal`追加のみ）。`TopologyDiagram.vue`のノード9件・エッジ12件の描画ロジック（`nodes`/`edges`computed、`NODE_POSITIONS`参照）、3段階色分け（`LEVEL_STROKE_CLASS`/`getMonitoringLevel`）はゲージ帯グラフ削除以外は無変更であることをソースコード上確認。バックエンドAPI（`GET /api/monitoring/snapshot`）を実際に叩き、9ノード・12エッジの正常なレスポンスが返ることを確認済み（既存機能の疎通は生きている）。ただし実際のブラウザ描画・アラートバナークリック導線・意図的なバックエンド停止時のエラーバナー表示は未確認。 |
| （AC番号なし・画面UIフロー要件） | ライト/ダーク両テーマでスパークライン・モーダルチャートが判読できる | **未実施** | スパークラインは既存の`LEVEL_STROKE_CLASS`（`dark:`バリアント込み、既存パターン踏襲）を流用（新規追加なし）。モーダルチャートの新規固定色は`stroke-sky-500 dark:stroke-sky-400`（CPU）／`stroke-violet-500 dark:stroke-violet-400`（メモリ）としてダークバリアントが明示的に定義されていることをソースコード上確認（NodeHistoryModal.vue L12-15、L202/L206の凡例ドットも同様に`dark:`バリアントあり）。グリッド線・軸ラベルも`stroke-zinc-200 dark:stroke-zinc-700`／`fill-zinc-500 dark:fill-zinc-400`とダークバリアント込みで実装されている。実際のコントラスト・判読性はブラウザでのみ確認可能。 |

### 手動確認手順（未実施分・実施者向け）

design.mdの「Test strategy」節に記載の手順1〜9をそのまま踏襲する。要点のみ再掲する。

1. `docker compose up`または`cd backend && ./mvnw spring-boot:run`（8080）＋`cd frontend && npm run dev`（5173）を起動し、ブラウザで`http://localhost:5173`を開く。サイドバーから「システムモニタリング」に切り替える。
2. 表示直後（履歴1件）でエラーなくスパークライン・枠色・数値が出ることを確認（AC-5）。ブラウザの開発者コンソールにエラーが出ていないことも確認する。
3. 30秒〜1分放置し、各ノードのCPU/MEM表示が折れ線（スパークライン）で推移していることを確認（AC-1）。
4. いずれかのノードをクリックし、モーダルが開きCPU/メモリ2本の折れ線＋凡例が表示されることを確認（AC-2）。
5. モーダルを開いたまま10〜15秒待ち、折れ線が伸びる（新サンプルが追加される）ことを確認（AC-3）。
6. 閉じるボタン／背景クリック／Escapeキーそれぞれ単独でモーダルが閉じることを確認（AC-4、3パターンとも個別に検証）。
7. サイドバーでチャット画面に切り替え→再度モニタリング画面に戻り、蓄積済みの波形が連続していることを確認。モーダルを開いた状態でチャットに切り替えるとモーダルが自動的に閉じることも確認（AC-6）。
8. `frontend/src/constants/monitoring.ts`の`MAX_HISTORY_SAMPLES`を一時的に`5`等に変更し、`npm run build`（または`npm run dev`のHMR）で反映後、短時間でスパークライン・モーダルチャートの表示件数が5件で頭打ちになり増え続けないことを確認する（AC-7）。**確認後は必ず36に戻し、`git diff`で差分が残っていないことを確認すること。**
9. 既存機能の退行確認: トポロジー全体（9ノード12エッジ）表示、エッジ帯域表示＋3段階色分け、ノード枠3段階色分け、5秒ごとのポーリング更新、バックエンド停止時のエラーバナー、アラートバナーのクリック導線（危険域到達を待つか、動作確認を早めたい場合はバックエンドのモック生成間隔調整を検討）を確認（AC-8）。
10. OS/ブラウザのダークモードを切り替え、ライト/ダーク両方でスパークライン・モーダルチャートの折れ線・グリッド線・凡例が判読できることを確認する。

## 追記: 実ブラウザでの検証結果（パイプライン実行者による補完）

test-engineerサブエージェントにはブラウザ操作手段がなかったため、パイプライン実行者（オーケストレーター）が別途Playwright＋Chromiumをスクラッチパッド配下に一時インストールし（プロジェクトの`package.json`・lockfileは無変更）、`npm run dev`（5173）と既存起動中のバックエンド（8080、PID 466525・ユーザーのIDEデバッグセッション、無変更で流用）に対して実際にブラウザ操作を行った。スクリーンショットは `/tmp/claude-1001/.../scratchpad/pw-check/shots/` に保存（一時ディレクトリのためリポジトリには含まれない）。

| シナリオ | 結果 | 確認内容 |
|---|---|---|
| AC-5（履歴1件時の初期描画） | **PASS** | 画面表示直後にスパークライン・ノード枠色・数値がエラーなく描画（コンソールエラー0件） |
| AC-1（スパークラインへの置き換え・推移表示） | **PASS** | 全9ノードのCPU/MEM行が`<polyline>`（18本）に置き換わっており、ゲージ帯グラフは消滅。15秒経過でCacheノードがdanger化し赤い波形として推移が視覚的に確認できた |
| AC-2（ノードクリック→モーダル、CPU/MEM2本重畳） | **PASS** | ノードクリックでモーダルが開き、Y軸0-100%固定・CPU（青）/メモリ（紫）の2本の折れ線＋凡例が表示された |
| AC-3（モーダル表示中のリアルタイム更新） | **PASS** | モーダルを開いたまま6秒待機し、X軸の最新時刻ラベルが`16:48:46`→`16:48:49`に進行（折れ線が伸長）したことを確認 |
| AC-4（閉じる操作3種） | **PASS**（3種とも個別確認） | Escapeキー／右上✕ボタン／背景クリックのいずれでもモーダルが単独で閉じることを確認（`div.fixed.inset-0`の存在数が1→0） |
| AC-6（画面往復後の履歴保持） | **PASS** | 20秒蓄積→モーダルのX軸開始時刻を記録→チャット画面へ切替（モーダル自動クローズも確認）→モニタリングへ復帰→同ノード再クリックでX軸開始時刻が完全一致（巻き戻っていない＝履歴保持） |
| ダークモード判読性 | **PASS** | `prefers-color-scheme: dark`エミュレーションでスパークライン・ノード枠・アラートバナーとも視認可能 |
| AC-8（既存機能の退行） | **PASS（部分）** | アラートバナークリック→新規会話作成→チャット画面遷移（既存の`monitoring-chat-alert`機能）がエラーなく動作。トポロジー全体（9ノード）・エッジ表示・ポーリングも継続動作を確認。**バックエンド停止時のエラーバナー表示は今回未実施**（既存起動中プロセスがユーザーのIDEデバッグセッションのため停止を避けた） |
| AC-7（36件上限のリングバッファ） | **未実施（コードレビューのみ）** | `MAX_HISTORY_SAMPLES`一時変更による実ブラウザでの上限到達確認は行わず、コードレビュー（`recordHistorySample`のFIFO `shift()`ロジック）による確認に留めた |

全スクリプトの実行中、`console`のerror・`pageerror`はいずれも0件だった。なお、ヘッドレスChromiumに日本語フォントが入っていないため画面キャプチャ上で日本語テキストが文字化け（tofu）して見えるが、これは検証環境のフォント欠如による表示上の問題であり、既存の無関係なUI文言（サイドバーの会話名等）でも同様に発生している。アプリケーションのバグではない。

## 総合サマリ（更新後）

- **自動検証（型検査・ビルド）: PASS**（`npx vue-tsc -b`・`npm run build`ともにエラーなし）。
- **サーバー疎通確認: PASS**。
- **実ブラウザでの手動確認: AC-1〜AC-6・AC-8（アラート導線部分）・ダークモード判読性は実施しPASS。** AC-7（リングバッファ上限の実データ確認）と、AC-8のうちバックエンド停止時のエラーバナー表示のみ、コードレビューに留まり実ブラウザでは未実施。
- 上記2点（AC-7の実測、バックエンド停止時のエラーバナー）は、リスクは低いと判断する（ロジックが単純かつ既存の`system-monitoring-view.md`で実装済みのエラーバナー機構自体は今回変更していないため）が、念のため公開前に確認することを推奨する。

再度実施する際は、上記「手動確認手順」セクションをそのままチェックリストとして利用できる。
