# 設計書: モニタリング画面とチャットの連携（異常検知アラート）

対応する要件定義: [docs/requirements/monitoring-chat-alert.md](requirements/monitoring-chat-alert.md)

## Approach

`monitoringStore`（`frontend/src/stores/monitoringStore.ts`）のスナップショット取得成功時に、ノードごとのCPU・メモリ使用率およびエッジごとのトラフィック（帯域）使用率を、本機能専用の2段階しきい値（`frontend/src/constants/monitoringAlert.ts`の`ALERT_WARNING_THRESHOLD`=80／`ALERT_DANGER_THRESHOLD`=90。トポロジー図自体の色分けしきい値`constants/monitoring.ts`の`WARNING_THRESHOLD`=70／`DANGER_THRESHOLD`=90とは独立）と比較する検知処理を追加する（要件定義Q10で2段階化、Q11でエッジ対象化・複数通知化を追加決定）。対象（ノード／エッジ）ごとに直近で通知した重大度を`alertedTierByTargetId`（`Map<string, 'warning' | 'danger'>`）に記録し、同一水準への再通知を抑制する。80%未満に戻ると完全にクールダウン解除、90%未満（80%以上）に戻るとdangerのみ解除（warningとしての再通知はしない）。通知待ちアラートは`pendingAlerts`（`MonitoringAlert[]`）として対象ごとに1件ずつ複数保持し、同一対象のバナーが未クリックのままエスカレーション（warning→danger）した場合は配列内の該当要素を置き換える（バナーは増やさない）。

`MonitoringView.vue`は`pendingAlerts`の各要素を個別のバナーとして縦に並べて表示する。バナークリック時のハンドラは、新設する`conversationStore.createAlertConversation(replyText, choiceOptions)`アクションを呼び出し、バックエンド通信なしでアラート文＋`choices`コンポーネントを持つアシスタント発言を新規会話に直接追加したうえで`monitoringStore.consumePendingAlert(alert)`でクリックされたアラートのみを配列から除去し`monitoringStore.showChat()`で画面遷移する。アラート文・選択肢ラベルの文言組み立ては新設する`frontend/src/constants/monitoringAlert.ts`のヘルパー関数（`buildAlertReplyText`/`buildCauseAnalysisChoiceLabel`/`CLOSE_ALERT_LABEL`）に集約し、`MonitoringView.vue`・`monitoringStore.ts`の双方から参照できるようにする。

選択肢クリック後は既存の`ChoicesView`→`ChatWindow.handleChoiceSelect`→`conversationStore.sendChoice`→`dispatchUserMessage`→`POST /api/chat`という既存フローをそのまま流用する（新規コードなし）。バックエンドの`MockChatService`に、選択肢ラベル文字列に含まれるキーワード（「使用率が高い原因を教えて」＋「CPU」／「メモリ」／「トラフィック」、あるいは「閉じる」の完全一致）で分岐する`alertFlowResponse`メソッドを追加し、`generateResponse`の判定チェーンに`inquiryFlowResponse`の直後に組み込む。原因分析データはメトリクス種別（CPU／メモリ／トラフィック）ごとに固定の架空データ（プロセス別使用率・通信種別別帯域占有率の表＋棒グラフ）を返す、対象に依存しない汎用テンプレートとする。

この設計により、バックエンドは監視データやアラート概念を一切知らない（既存のステートレス・キーワードマッチ方針を維持）、新規UIコンポーネント型も追加しない、既存のチャット関連ファイル（`ChatController`/`ChatResponse`/`UiComponent`各実装/`chatApi.ts`/`types/chat.ts`）にも変更を加えない。

## Alternatives considered

- **アラート吹き出しをバックエンド経由（`POST /api/chat`にトリガー文を送信）で生成する案**も検討した。しかし`MockChatService`に「現在どのノードが異常か」という監視データの知識を持たせる必要が生じ、既存のステートレス・キーワードマッチのみという設計方針から逸脱する。フロントエンドの`monitoringStore`が既に閾値判定のためsnapshotを保持しているため、そのままアラート文を組み立てられるフロントエンド生成案の方が実装コストが低く、要件定義のQ3決定（フロントエンドローカル生成）にも合致するため不採用とした。
- **アラート専用の新しいMessage role（例: `'system'`）を追加する案**も検討したが、既存の`Message`型は`role: 'user' | 'assistant'`のみで、`MessageBubble.vue`はrole問わず`components`があれば描画する。アラート文は「アシスタントが能動的に話しかけた」体裁として`role: 'assistant'`のメッセージをそのまま会話へpushするだけで表現でき、`Message`型・`MessageBubble.vue`の変更が一切不要になるため、新role追加は不採用とした（要件定義のQ3で確認済み）。
- **通知対象ノードごとに個別の原因分析データを用意する案**も検討したが、9ノード分のデータをハードコードするのは過剰であり、要件定義Q7の決定（メトリクス種別ごとの汎用テンプレート）に従い、CPU用・メモリ用の2パターンのみ実装する。
- **選択肢クリック時に送信する文字列を短い技術的トリガー文（例: `原因調査:CPU:web-1`）にする案**も検討したが、既存の`choices`コンポーネントは「表示ラベル＝送信文」という契約（`docs/chat-response-schema.md`）であり、技術的な文字列がボタンラベルとして見えるのはデモ上不自然。ノード名・メトリクス種別を含む自然な日本語文（例: 「Web-1のCPU使用率が高い原因を教えて」）をラベル兼トリガー文とすることで、既存の「カテゴリ: 請求」のような定型文パターンを踏襲しつつ、バックエンドのキーワードマッチ（「使用率が高い原因を教えて」＋「CPU」/「メモリ」）にも利用できる一石二鳥の設計とした。

## Files affected

### バックエンド（変更）

- `backend/src/main/java/com/example/chatbackend/MockChatService.java`: `alertFlowResponse`メソッド（原因分析2分岐＋「閉じる」分岐）を追加し、`generateResponse`の判定チェーンに組み込む
- `backend/src/test/java/com/example/chatbackend/MockChatServiceTest.java`: 新規分岐のテストケースを追加

### フロントエンド（新規）

- `frontend/src/constants/monitoringAlert.ts`: `MonitoringAlert`型（`AlertSeverity`/`AlertMetric`含む）、`ALERT_WARNING_THRESHOLD`/`ALERT_DANGER_THRESHOLD`定数、`buildAlertReplyText`/`buildCauseAnalysisChoiceLabel`関数、`CLOSE_ALERT_LABEL`定数

### フロントエンド（変更）

- `frontend/src/stores/monitoringStore.ts`: `alertedTierByTargetId`（クールダウン用Map）・`pendingAlerts`（配列）状態、`updateAlertState`（ノード・エッジの検知ロジック）・`evaluateAlertTarget`・`consumePendingAlert`アクションを追加。`fetchSnapshot`成功時に`updateAlertState`を呼び出す
- `frontend/src/stores/conversationStore.ts`: `createAlertConversation(replyText: string, choiceOptions: string[])`アクションを追加（新規会話作成＋タイトル設定＋アシスタントメッセージのバックエンド未経由push）
- `frontend/src/components/MonitoringView.vue`: `store.pendingAlerts`の`v-for`による複数バナー描画、クリックハンドラ（`conversationStore.createAlertConversation` → `monitoringStore.consumePendingAlert(alert)` → `monitoringStore.showChat`）を追加
- `frontend/src/components/TopologyDiagram.vue`: warning水準の配色をamber系からオレンジ系に変更（要件定義Q11。アラートバナーのwarning色と統一）

### ドキュメント（変更）

- `docs/chat-response-schema.md`: 「シナリオF: モニタリング連携アラート」節を追加し、原因分析・閉じるのトリガー文とレスポンス例を文書化。あわせて、アラート吹き出し自体はAPIを経由しないフロントエンド生成である旨を明記する

### 変更なし（明示）

- `backend/src/main/java/com/example/chatbackend/ChatController.java` / `ChatResponse.java` / `UiComponent.java` / 各既存`*Component.java` / `MonitoringController.java` / `MonitoringMetricsService.java`
- `frontend/src/api/chatApi.ts` / `frontend/src/api/monitoringApi.ts` / `frontend/src/types/chat.ts` / `frontend/src/types/monitoring.ts` / `frontend/src/components/ChatWindow.vue` / `frontend/src/components/MessageBubble.vue` / `frontend/src/components/ChoicesView.vue` / `frontend/src/components/MessageInput.vue`（新規UIコンポーネント型を追加しないため、描画層・検証層は無改修で成立する）

## 詳細設計

### `frontend/src/constants/monitoringAlert.ts`（新規）

```ts
export type AlertSeverity = 'warning' | 'danger'
export type AlertMetric = 'cpu' | 'memory' | 'traffic'
export type AlertMetricLabel = 'CPU' | 'メモリ' | 'トラフィック'

export interface MonitoringAlert {
  // ノードのidまたはエッジのid（両者は衝突しない前提）
  targetId: string
  // ノードのlabel、またはエッジの場合は「LB〜Web-1間」のような区間表記
  targetLabel: string
  metric: AlertMetric
  metricLabel: AlertMetricLabel
  value: number
  severity: AlertSeverity
}

// このアラート機能専用のしきい値（constants/monitoring.tsの色分けしきい値とは別軸）
export const ALERT_WARNING_THRESHOLD = 80
export const ALERT_DANGER_THRESHOLD = 90

export const CAUSE_ANALYSIS_KEYWORD = '使用率が高い原因を教えて'
export const CLOSE_ALERT_LABEL = '閉じる'

export function buildAlertReplyText(alert: MonitoringAlert): string {
  const value = Math.round(alert.value)
  if (alert.severity === 'danger') {
    return `${alert.targetLabel} の${alert.metricLabel}使用率が${value}%（危険水準）に達しました。原因を確認しますか？`
  }
  return `${alert.targetLabel} の${alert.metricLabel}使用率が${value}%（警告水準）です。念のため原因を確認しますか？`
}

export function buildCauseAnalysisChoiceLabel(alert: MonitoringAlert): string {
  return `${alert.targetLabel}の${alert.metricLabel}${CAUSE_ANALYSIS_KEYWORD}`
}
```

（当初のdangerのみ設計を要件定義Q10で2段階化、Q11でエッジ（トラフィック）を対象に追加。原因分析の選択肢文言・バックエンド分岐は重大度によらずメトリクス種別のみで判定する）

`CAUSE_ANALYSIS_KEYWORD`をフロントの定数として1箇所に定義し、バックエンド（`MockChatService`）の同名キーワード文字列（Java側の定数）と値を一致させる。両者は言語をまたぐため自動同期はできないが、コメントで対応関係を明記する（リスク節参照）。

### `frontend/src/stores/monitoringStore.ts`（変更）

```ts
state: () => ({
  // ...既存...
  alertedTierByTargetId: new Map<string, AlertSeverity>(),
  pendingAlerts: [] as MonitoringAlert[],
}),
actions: {
  // ...既存...
  async fetchSnapshot() {
    // ...既存の成功パスの最後に追加...
    this.snapshot = snapshot
    this.lastUpdatedAt = Date.now()
    this.hasError = false
    this.updateAlertState(snapshot)
    // ...
  },
  updateAlertState(snapshot: MonitoringSnapshot) {
    const nodeLabelById = new Map(snapshot.nodes.map((node) => [node.id, node.label]))
    for (const node of snapshot.nodes) {
      const metric = node.memoryPercent > node.cpuPercent ? 'memory' : 'cpu'
      this.evaluateAlertTarget({
        targetId: node.id,
        targetLabel: node.label,
        metric,
        metricLabel: metric === 'cpu' ? 'CPU' : 'メモリ',
        value: Math.max(node.cpuPercent, node.memoryPercent),
      })
    }
    for (const edge of snapshot.edges) {
      this.evaluateAlertTarget({
        targetId: edge.id,
        targetLabel: `${nodeLabelById.get(edge.sourceId)}〜${nodeLabelById.get(edge.targetId)}間`,
        metric: 'traffic',
        metricLabel: 'トラフィック',
        value: edge.bandwidthPercent,
      })
    }
  },
  evaluateAlertTarget(candidate: Omit<MonitoringAlert, 'severity'>) {
    const currentTier =
      candidate.value >= ALERT_DANGER_THRESHOLD ? 'danger'
        : candidate.value >= ALERT_WARNING_THRESHOLD ? 'warning' : null
    const previousTier = this.alertedTierByTargetId.get(candidate.targetId) ?? null

    if (currentTier === null) {
      this.alertedTierByTargetId.delete(candidate.targetId)  // 完全クールダウン解除
      return
    }
    if (currentTier === 'warning' && previousTier === 'danger') {
      // dangerから80%台へ降格：通知済み扱いはwarningへ引き継ぎ、再通知しない
      this.alertedTierByTargetId.set(candidate.targetId, 'warning')
      return
    }
    if (currentTier === previousTier) {
      return  // 同一水準の継続：再通知しない
    }
    // ここに来るのは (null → warning/danger) または (warning → danger) のみ
    this.alertedTierByTargetId.set(candidate.targetId, currentTier)
    const alert = { ...candidate, severity: currentTier }
    const existingIndex = this.pendingAlerts.findIndex(
      (pending) => pending.targetId === candidate.targetId,
    )
    if (existingIndex >= 0) {
      this.pendingAlerts[existingIndex] = alert  // エスカレーション時は置き換え
    } else {
      this.pendingAlerts.push(alert)
    }
  },
  consumePendingAlert(alert: MonitoringAlert) {
    this.pendingAlerts = this.pendingAlerts.filter(
      (pending) => pending.targetId !== alert.targetId,
    )
  },
},
```

通知待ちアラートは対象（ノード／エッジ）ごとに最大1件で、複数対象分を同時に保持できる（要件定義Q11で1件制限から変更）。同一対象のバナーが未クリックのままwarning→dangerへエスカレーションした場合は、配列内の該当要素を新しい重大度の内容で置き換えるため、バナーの総数は増えない。

### `frontend/src/stores/conversationStore.ts`（変更）

```ts
createAlertConversation(replyText: string, choiceOptions: string[]) {
  this.createConversation()
  const conversation = this.activeConversation
  if (!conversation) {
    return
  }
  conversation.title = replyText.slice(0, TITLE_MAX_LENGTH)
  conversation.messages.push(
    createMessage('assistant', replyText, [
      { type: 'choices', options: choiceOptions },
    ]),
  )
},
```

既存の`createMessage`ヘルパー・`TITLE_MAX_LENGTH`・`Conversation`/`Message`型をそのまま再利用する。`isLoading`は変更しない（バックエンド通信がないため）。既存の`isInputLocked`ゲッター（最新メッセージがassistant＋choices持ちなら入力欄無効化）はこのメッセージに対してもそのまま機能する。

### `frontend/src/components/MonitoringView.vue`（変更）

```vue
<script setup lang="ts">
import { useConversationStore } from '../stores/conversationStore'
import {
  buildAlertReplyText,
  buildCauseAnalysisChoiceLabel,
  CLOSE_ALERT_LABEL,
} from '../constants/monitoringAlert'

const conversationStore = useConversationStore()

function handleAlertBannerClick(alert: MonitoringAlert) {
  conversationStore.createAlertConversation(buildAlertReplyText(alert), [
    buildCauseAnalysisChoiceLabel(alert),
    CLOSE_ALERT_LABEL,
  ])
  store.consumePendingAlert(alert)
  store.showChat()
}
</script>
```

テンプレートに`v-for="alert in store.pendingAlerts"`（`:key="alert.targetId"`）のバナー群（`buildAlertReplyText(alert)`相当の文言＋クリック領域）を、既存のエラーバナーと同じ位置（タイトル行の下、`TopologyDiagram`の上）に縦に並べて追加する。`alert.severity`に応じて配色を切り替える（danger→`rose`系、warning→`orange`系。トポロジー図の枠色・ゲージ色のwarningもamber系からorange系へ変更して統一）ことで、重大度を視覚的に区別する。

### `backend/src/main/java/com/example/chatbackend/MockChatService.java`（変更）

```java
private static final String CAUSE_ANALYSIS_KEYWORD = "使用率が高い原因を教えて";
private static final String CPU_METRIC_KEYWORD = "CPU";
private static final String MEMORY_METRIC_KEYWORD = "メモリ";
private static final String TRAFFIC_METRIC_KEYWORD = "トラフィック";
private static final String CLOSE_ALERT_LABEL = "閉じる";

public ChatResponse generateResponse(String message) {
    ChatResponse inquiryResponse = inquiryFlowResponse(message);
    if (inquiryResponse != null) {
        return inquiryResponse;
    }
    ChatResponse alertResponse = alertFlowResponse(message);
    if (alertResponse != null) {
        return alertResponse;
    }
    // ...既存の日別/担当/カテゴリ/フォールバック判定...
}

private ChatResponse alertFlowResponse(String message) {
    if (message.contains(CAUSE_ANALYSIS_KEYWORD)) {
        if (message.contains(CPU_METRIC_KEYWORD)) {
            return cpuCauseAnalysis();
        }
        if (message.contains(MEMORY_METRIC_KEYWORD)) {
            return memoryCauseAnalysis();
        }
        if (message.contains(TRAFFIC_METRIC_KEYWORD)) {
            return trafficCauseAnalysis();
        }
    }
    if (message.equals(CLOSE_ALERT_LABEL)) {
        return alertClosed();
    }
    return null;
}

private ChatResponse cpuCauseAnalysis() {
    TableComponent table = new TableComponent(
            List.of("プロセス", "CPU使用率"),
            List.of(
                    List.of("batch-worker", "42%"),
                    List.of("api-server", "28%"),
                    List.of("log-agent", "15%"),
                    List.of("other", "7%")));
    BarChartComponent barChart = new BarChartComponent(
            "プロセス別 CPU使用率",
            List.of("batch-worker", "api-server", "log-agent", "other"),
            List.of(42.0, 28.0, 15.0, 7.0));
    return new ChatResponse(
            "直近のCPU使用率上昇の要因を分析しました。バッチ処理プロセスの負荷が主な要因です。",
            List.of(table, barChart));
}

private ChatResponse memoryCauseAnalysis() {
    TableComponent table = new TableComponent(
            List.of("プロセス", "メモリ使用率"),
            List.of(
                    List.of("cache-layer", "38%"),
                    List.of("api-server", "25%"),
                    List.of("session-store", "19%"),
                    List.of("other", "10%")));
    BarChartComponent barChart = new BarChartComponent(
            "プロセス別 メモリ使用率",
            List.of("cache-layer", "api-server", "session-store", "other"),
            List.of(38.0, 25.0, 19.0, 10.0));
    return new ChatResponse(
            "直近のメモリ使用率上昇の要因を分析しました。キャッシュ層のメモリ消費増加が主な要因です。",
            List.of(table, barChart));
}

private ChatResponse trafficCauseAnalysis() {
    TableComponent table = new TableComponent(
            List.of("通信種別", "帯域占有率"),
            List.of(
                    List.of("file-transfer", "45%"),
                    List.of("api-calls", "27%"),
                    List.of("db-replication", "18%"),
                    List.of("other", "10%")));
    BarChartComponent barChart = new BarChartComponent(
            "通信種別別 帯域占有率",
            List.of("file-transfer", "api-calls", "db-replication", "other"),
            List.of(45.0, 27.0, 18.0, 10.0));
    return new ChatResponse(
            "直近のトラフィック増加の要因を分析しました。大容量ファイル転送による帯域占有が主な要因です。",
            List.of(table, barChart));
}

private ChatResponse alertClosed() {
    return new ChatResponse("承知しました。", List.of());
}
```

`CAUSE_ANALYSIS_KEYWORD`の文字列値（`"使用率が高い原因を教えて"`）はフロントエンドの`monitoringAlert.ts`の`CAUSE_ANALYSIS_KEYWORD`と一致させる。`CLOSE_ALERT_LABEL`の値（`"閉じる"`）も同様。

判定順序: `inquiryFlowResponse`（新規問い合わせフロー）→`alertFlowResponse`（本機能）→日別→担当→カテゴリ／種別→フォールバック。既存キーワード（担当・カテゴリ・種別・日別・カテゴリ:/緊急度:/登録する/やり直す/キャンセル）と本機能の新規キーワード（使用率が高い原因を教えて・CPU・メモリ・トラフィック・閉じる）に文字列としての重複はないため、この順序自体は判定結果に影響しないが、既存の`inquiryFlowResponse`優先という設計コメントに倣い、新規フローも独立した優先チェックとして明示的に配置する。

## Implementation steps

1. **バックエンド: 応答分岐の追加**。`MockChatService`に`alertFlowResponse`・`cpuCauseAnalysis`・`memoryCauseAnalysis`・`trafficCauseAnalysis`・`alertClosed`を追加し、`generateResponse`に組み込む。`MockChatServiceTest`に以下のテストケースを追加: (a) 「Web-1のCPU使用率が高い原因を教えて」→CPU分析データ、(b) 「DB Primaryのメモリ使用率が高い原因を教えて」→メモリ分析データ、(c) 「LB〜Web-1間のトラフィック使用率が高い原因を教えて」→トラフィック分析データ、(d) 「閉じる」→終了メッセージ・components空、(e) 既存の担当/カテゴリ/日別/新規問い合わせ系のテストが引き続きグリーンであること（回帰確認）。
   検証: `cd backend && ./mvnw test -Dtest=MockChatServiceTest`、続けて `./mvnw test`（全件）

2. **フロントエンド: アラート文言ヘルパー**。`frontend/src/constants/monitoringAlert.ts`を新規作成（`MonitoringAlert`型・`buildAlertReplyText`・`buildCauseAnalysisChoiceLabel`・`CAUSE_ANALYSIS_KEYWORD`・`CLOSE_ALERT_LABEL`）。
   検証: `cd frontend && npx vue-tsc -b`

3. **フロントエンド: 検知ロジック**。`monitoringStore.ts`に`alertedTierByTargetId`/`pendingAlerts`状態と`updateAlertState`/`evaluateAlertTarget`/`consumePendingAlert`アクションを追加し、`fetchSnapshot`成功パスから呼び出す。
   検証: `npx vue-tsc -b`

4. **フロントエンド: アラート会話生成アクション**。`conversationStore.ts`に`createAlertConversation`を追加。
   検証: `npx vue-tsc -b`

5. **フロントエンド: バナーUIと画面遷移**。`MonitoringView.vue`にバナーの条件描画とクリックハンドラを追加。
   検証: `npx vue-tsc -b` に加え、バックエンド・フロントエンドを起動し、`MonitoringMetricsService`のスパイク間隔を一時的に短縮（`SPIKE_MEAN_INTERVAL_TICKS`等）して異常発生を早め、バナー表示→クリック→チャット遷移→アラート吹き出し表示までを目視確認（確認後は既定値に戻す）

6. **結合の手動受け入れ確認**。要件定義のAC-1〜AC-9を一通り手動確認する。特にAC-3（原因分析）・AC-4（閉じる）はクリックして応答を確認、AC-5（クールダウン）・AC-6（複数同時）はスパイク間隔短縮下で確認する。

7. **ドキュメント更新**。`docs/chat-response-schema.md`に「シナリオF: モニタリング連携アラート」を追記。
   検証: レビューのみ（実際に`curl`等でバックエンドの応答を確認し記載内容と一致させる）

8. **ビルド確認**。`cd frontend && npm run build`（lessons-learnedの教訓どおり最終的に本番ビルドも通ることを確認）。

## Risks / edge cases

- **フロント・バックエンド間のキーワード文字列の二重管理**: `CAUSE_ANALYSIS_KEYWORD`（`"使用率が高い原因を教えて"`）と`CLOSE_ALERT_LABEL`（`"閉じる"`）は、TypeScript側（`monitoringAlert.ts`）とJava側（`MockChatService.java`）にそれぞれ定数として定義する。既存の新規問い合わせフローも同様の二重管理（フロントの`INQUIRY_TRIGGER`とバックエンドの`INQUIRY_TRIGGER`定数）を行っており、本プロジェクトが許容している既存パターンに従う。値を変更する際は両ファイルを同時に更新する必要があることをコード上のコメントで明記する。
- **クールダウン状態の永続性**: `alertedTierByTargetId`/`pendingAlerts`は`monitoringStore`のメモリ内状態であり、`conversationStore`と異なりlocalStorageに永続化しない。ページリロードで消える（既存のモニタリング機能自体がリロードでリセットされる設計に揃えている）。
- **同一ノードでCPU・メモリ両方が閾値超過した場合の取りこぼし**: `updateAlertState`は「値が大きい方」のみを主対象メトリクスとして通知する（要件定義FR-4）。もう一方のメトリクスも閾値超過中でも別アラートにはならない（対象単位でクールダウンに入るため）。これは要件定義で確定済みの仕様（1対象1アラート）であり意図的な単純化。
- **バナー放置時の挙動**: `pendingAlerts`の各アラートはユーザーがそのバナーをクリックするまで保持され続ける（モニタリング画面を離れて戻ってきても再表示される）。対象ごとに独立して保持するため、あるバナーの放置が他の対象の検知・通知を妨げることはない（要件定義Q11で1件制限を撤廃）。値がしきい値未満に戻ってもバナー自体は消えない（通知として明示的なクリックを要求する）。
- **アラートの最大件数**: 対象はノード9件＋エッジ12件の計21件のため、理論上のバナー最大数は21。スパイクは同時に1系列のみのため実運用でここまで並ぶことはなく、件数制限は設けない。
- **既存機能への影響**: `MockChatService.generateResponse`の判定チェーン変更により既存シナリオの判定順序が変わるが、キーワードの重複がないため既存の`MockChatServiceTest`・`ChatControllerTest`の結果に影響しないことをステップ1のテスト実行で確認する。

## Test strategy

- **バックエンド自動テスト（JUnit / AssertJ、既存`MockChatServiceTest`と同じ構成）**: 本設計の「Implementation steps」ステップ1に記載のテストケースを追加。既存全テスト（`./mvnw test`）がグリーンのままであることを回帰確認する。
- **フロントエンド型検証**: `npx vue-tsc -b`（bareの`vue-tsc --noEmit`は使わない、既存lessons-learnedの教訓どおり）と最終的な`npm run build`。
- **手動確認**: `./mvnw spring-boot:run`と`npm run dev`を起動し、要件定義のAC-1〜AC-9を確認する。スパイク発生を待つ時間を短縮するため、`MonitoringMetricsService`の`SPIKE_MEAN_INTERVAL_TICKS`/`SPIKE_DURATION_TICKS`を一時的に小さい値に変更して確認し、確認後は既定値に戻す（既存`system-monitoring-view.md`の確認手法を踏襲）。
