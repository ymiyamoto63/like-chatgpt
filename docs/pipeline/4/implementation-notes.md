# Implementation Notes

## step 1-2

対象: `docs/pipeline/4/design.md` の Implementation steps 1〜2（ステップ3以降は未実装）。

### ステップ1: ArchUnit 依存追加

- `backend/pom.xml`: `com.tngtech.archunit:archunit-junit5` を test scope で追加。バージョンは実装時点（2026-07-19）の Maven Central 確認で 1.3.x 系最新安定版である `1.3.2` を明示指定（Spring Boot Parent BOM に依存管理が無いため）。Java 21 / JUnit 5 と互換。

### ステップ2: `dto` → `domain` パッケージ移動

`git mv` でファイル移動し、パッケージ宣言のみ変更（クラス名・フィールド・アノテーション・コメントは不変）。

- `dto/chat/{ChatRequest,ChatResponse}.java` → `domain/chat/{ChatRequest,ChatResponse}.java`
- `dto/component/{UiComponent,TableComponent,BarChartComponent,ChoicesComponent,TrendChartComponent,FaqListComponent}.java` → `domain/chat/component/*.java`
- `dto/monitoring/{MonitoringSnapshot,MonitoringNode,MonitoringEdge}.java` → `domain/monitoring/*.java`
- `dto/suggest/{SuggestRequest,SuggestResponse}.java` → `domain/suggest/*.java`
- `repository/FaqEntry.java` → `domain/faq/FaqEntry.java`
- 旧 `dto/` パッケージ（サブパッケージ含む）は空になったため削除済み。`repository/` は `FaqRepository.java` が残っているため未削除（設計どおり、ステップ3以降で対応）。

参照更新（import のみ、ロジック変更なし）:
- `main`: `controller/{ChatController,SuggestController,MonitoringController}.java`、`service/{MockChatService,MockSuggestService,MonitoringMetricsService}.java`、`repository/FaqRepository.java`（`FaqEntry` が同一パッケージから外に移動したため、新規に `import com.example.chatbackend.domain.faq.FaqEntry;` を追加）
- `test`: `service/MockChatServiceTest.java`、`service/MonitoringMetricsServiceTest.java`

機械確認: `grep -rl 'chatbackend\.dto\.' backend/src` の結果はゼロ件（更新漏れなし）。`find backend/src -path '*/dto/*'` もゼロ件。

### 検証結果

- `cd backend && ./mvnw -q compile` → 成功（警告・エラーなし）
- `cd backend && ./mvnw test` → `Tests run: 49, Failures: 0, Errors: 0, Skipped: 0` で BUILD SUCCESS（既存6テストクラス全件 green）

### 設計からの逸脱

なし。API・JSON 形式（`@JsonTypeInfo`/`@JsonSubTypes` の `name` 値、`@NotBlank` 等）は一切変更していない。

### スコープ外（今回未実装）

design.md の Implementation steps 3〜9（FAQ 出力ポート抽出、chat/suggest/monitoring のヘキサゴナル化、ArchUnit ルールテスト本体、ドキュメント追随、全体検証）は本タスクの指示によりステップ1〜2のみに限定したため未実装。
