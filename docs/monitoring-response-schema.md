# モニタリングAPI 応答JSONスキーマ契約文書

> 本書は `docs/requirements/system-monitoring-view.md`（FR-3, FR-11, AC-1, AC-2）および `docs/design.md`（「新API: `GET /api/monitoring/snapshot`」節）に基づき作成した、`GET /api/monitoring/snapshot` の応答JSONの契約文書である。

## 位置づけ

- 本APIはシステム構成図モニタリング画面専用のモックデータ供給APIである。実監視システムには接続せず、`MonitoringMetricsService` が固定トポロジーとインメモリの前回値からランダムウォーク＋周期スパイクで生成した値を返す。
- `docs/chat-response-schema.md`（`POST /api/chat` の契約文書）とは独立したスキーマであり、`ChatResponse`/`UiComponent` 系とは無関係のDTOで表現される。
- 本書に記載するサンプルJSONの構造は、`backend/src/test/java/com/example/chatbackend/MonitoringControllerTest.java` が検証する実際のレスポンス構造と一致する（値そのものは呼び出しごとに変動するため、サンプルは構造のみを示す）。

## エンドポイント

`GET /api/monitoring/snapshot`

- リクエストボディ・クエリパラメータなし。
- 常に200・`MonitoringSnapshot` を返す（チャットAPIと異なり、GETでパラメータを取らないため検証エラーによる4xxは発生しない）。
- 呼び出すたびにサーバ内部の前回値マップが1ティック分更新される（「1回のGET呼び出し＝1ティック」。詳細は `docs/design.md` の「詳細設計」節を参照）。同じ状態はサーバ上の全クライアントで共有され、リクエストごとの個別セッション分離はしない。

## トップレベル: `MonitoringSnapshot`

| フィールド | 型 | 必須 | 説明・制約 |
|---|---|---|---|
| `nodes` | `MonitoringNode[]` | ○ | 固定9件。ノードのメトリクス一覧 |
| `edges` | `MonitoringEdge[]` | ○ | 固定12件。エッジ（ノード間接続）のメトリクス一覧 |

```
MonitoringSnapshot
{
  "nodes": MonitoringNode[],
  "edges": MonitoringEdge[]
}
```

## `MonitoringNode`

| フィールド | 型 | 必須 | 説明・制約 |
|---|---|---|---|
| `id` | `string` | ○ | ノード識別子。例: `"web-1"`。下記トポロジー表のIDで固定 |
| `label` | `string` | ○ | 表示名。例: `"Web-1"` |
| `cpuPercent` | `number` | ○ | CPU使用率。0〜100の範囲に収まる（丸め誤差対策で毎ティック `clamp(0, 100)` を適用） |
| `memoryPercent` | `number` | ○ | メモリ使用率。0〜100の範囲に収まる |

```
MonitoringNode
{
  "id": string,
  "label": string,
  "cpuPercent": number,
  "memoryPercent": number
}
```

## `MonitoringEdge`

| フィールド | 型 | 必須 | 説明・制約 |
|---|---|---|---|
| `id` | `string` | ○ | エッジ識別子。例: `"lb-web1"` |
| `sourceId` | `string` | ○ | 接続元。`MonitoringNode.id` のいずれかと一致する |
| `targetId` | `string` | ○ | 接続先。`MonitoringNode.id` のいずれかと一致する |
| `bandwidthPercent` | `number` | ○ | 帯域使用率。0〜100の範囲に収まる |

```
MonitoringEdge
{
  "id": string,
  "sourceId": string,
  "targetId": string,
  "bandwidthPercent": number
}
```

## 固定トポロジー（9ノード・12エッジ）

トポロジー（ID・ラベル・接続関係）はサーバ再起動をまたいでも常に固定。値（`cpuPercent`/`memoryPercent`/`bandwidthPercent`）のみが呼び出しごとに変動する。

### ノード

| id | label | baseline CPU | baseline Memory |
|---|---|---|---|
| `internet` | Internet | 20 | 15 |
| `lb` | LB | 35 | 30 |
| `web-1` | Web-1 | 40 | 45 |
| `web-2` | Web-2 | 40 | 45 |
| `app-1` | App-1 | 45 | 50 |
| `app-2` | App-2 | 45 | 50 |
| `cache` | Cache | 30 | 55 |
| `db-primary` | DB Primary | 50 | 60 |
| `db-replica` | DB Replica | 35 | 55 |

baseline値はサーバ起動直後の初期値であり、ランダムウォークにより平均回帰的にこの値付近を推移する（スパイク中を除く）。

### エッジ

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

Web×2→App×2はフルメッシュ（4本）。

## サンプルJSON（構造のみ。値は呼び出しごとに変動する）

```json
{
  "nodes": [
    { "id": "internet", "label": "Internet", "cpuPercent": 21.4, "memoryPercent": 14.8 },
    { "id": "lb", "label": "LB", "cpuPercent": 36.1, "memoryPercent": 29.3 },
    { "id": "web-1", "label": "Web-1", "cpuPercent": 41.7, "memoryPercent": 46.2 },
    { "id": "web-2", "label": "Web-2", "cpuPercent": 39.5, "memoryPercent": 44.1 },
    { "id": "app-1", "label": "App-1", "cpuPercent": 46.0, "memoryPercent": 49.8 },
    { "id": "app-2", "label": "App-2", "cpuPercent": 44.3, "memoryPercent": 51.2 },
    { "id": "cache", "label": "Cache", "cpuPercent": 30.9, "memoryPercent": 55.4 },
    { "id": "db-primary", "label": "DB Primary", "cpuPercent": 51.1, "memoryPercent": 60.6 },
    { "id": "db-replica", "label": "DB Replica", "cpuPercent": 34.2, "memoryPercent": 54.9 }
  ],
  "edges": [
    { "id": "internet-lb", "sourceId": "internet", "targetId": "lb", "bandwidthPercent": 29.6 },
    { "id": "lb-web1", "sourceId": "lb", "targetId": "web-1", "bandwidthPercent": 35.8 },
    { "id": "lb-web2", "sourceId": "lb", "targetId": "web-2", "bandwidthPercent": 34.4 },
    { "id": "web1-app1", "sourceId": "web-1", "targetId": "app-1", "bandwidthPercent": 30.5 },
    { "id": "web1-app2", "sourceId": "web-1", "targetId": "app-2", "bandwidthPercent": 19.7 },
    { "id": "web2-app1", "sourceId": "web-2", "targetId": "app-1", "bandwidthPercent": 20.3 },
    { "id": "web2-app2", "sourceId": "web-2", "targetId": "app-2", "bandwidthPercent": 29.9 },
    { "id": "app1-cache", "sourceId": "app-1", "targetId": "cache", "bandwidthPercent": 25.1 },
    { "id": "app2-cache", "sourceId": "app-2", "targetId": "cache", "bandwidthPercent": 24.6 },
    { "id": "app1-dbprimary", "sourceId": "app-1", "targetId": "db-primary", "bandwidthPercent": 30.2 },
    { "id": "app2-dbprimary", "sourceId": "app-2", "targetId": "db-primary", "bandwidthPercent": 29.4 },
    { "id": "dbprimary-dbreplica", "sourceId": "db-primary", "targetId": "db-replica", "bandwidthPercent": 40.7 }
  ]
}
```

## メトリクス変動ロジックの概要（FR-11, AC-2）

- `MonitoringMetricsService`（`@Service` シングルトンBean）がノードのCPU/メモリ、エッジの帯域幅、合計30系列（9ノード×2＋12エッジ）の前回値をインメモリで保持する。サーバ再起動でリセットされる（チャット機能のステートレス方針の明示的な例外）。
- 通常時は各系列に対して1ティックあたり最大±3.0のランダムウォークと、baseline値への緩やかな平均回帰（回帰係数0.05）を適用する。
- 平均12ティック（±6ティックの一様乱数ジッタ、実質6〜18ティック）に1回、30系列から一様乱数で1系列を選び、選んだ系列が属するノードであればCPU・メモリ両方を、エッジであれば帯域幅を、3ティックの間ピーク値（78〜98の範囲で乱数決定）方向へ強く引き寄せるスパイクを発生させる。スパイク終了後は2ティックの間、係数0.4の強めの平均回帰を適用してbaseline付近へ戻す。
- すべての系列は最終的に `clamp(0, 100)` される。これにより連続呼び出しで値は変動しつつ、常に0〜100の範囲に収まる（AC-2）。

## フロントエンドの安全なフォールバックについて

- フロントエンドはAPIクライアント層（`monitoringApi.ts`）で、`nodes`/`edges` が配列であること、各要素の必須フィールドの型（`id`/`label`/`sourceId`/`targetId` が `string`、`cpuPercent`/`memoryPercent`/`bandwidthPercent` が有限の `number`）を検証している。値域（0〜100）チェックは行わない（`docs/chat-response-schema.md` の既存の数値検証方針と同様、型・有限性のみを検証する）。
- 検証に失敗した場合はクラッシュせずエラー表示（バナーまたは全面エラー）にフォールバックする（FR-9, FR-10, AC-8）。未知の追加フィールドは無視する。
