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

## step 3-4

対象: `docs/pipeline/4/design.md` の Implementation steps 3〜4（ステップ1〜2は完了済み、ステップ5以降は未実装）。

### ステップ3: FAQ 出力ポート＋アダプタの抽出（FR-4）

- 新規: `application/port/out/FaqQueryPort.java`（`findByTitle` / `findTitlesByCategory` を定義するIF）。
- `repository/FaqRepository.java` を `git mv` で `adapter/out/faq/InMemoryFaqQueryAdapter.java` に移動し、`FaqQueryPort` を実装するよう変更（クラスコメント・ハードコードFAQデータ・検索ロジックは一字一句維持、`@Repository` も維持）。
- `service/MockChatService.java`（この時点ではまだ旧パッケージ）のコンストラクタ引数・フィールドを `FaqRepository` → `FaqQueryPort` に変更（`faqRepository` → `faqQueryPort` にリネーム、ロジックは不変）。
- `service/MockChatServiceTest.java` のインスタンス生成を `new MockChatService(new InMemoryFaqQueryAdapter())` に更新。

### ステップ4: chat 機能のヘキサゴナル化（FR-2, FR-3）

- 新規: `application/port/in/GenerateChatReplyUseCase.java`（`ChatResponse generateReply(ChatRequest request)`）。
- 新規: `application/port/out/ReplyGenerationPort.java`（`ChatResponse generateReply(String message)`）。
- 新規: `application/service/ChatService.java`（Spring アノテーションなしのPOJO。`GenerateChatReplyUseCase` を実装し `replyGenerationPort.generateReply(request.message())` に委譲するのみ）。
- `service/MockChatService.java` を `git mv` で `adapter/out/reply/KeywordMatchReplyGenerationAdapter.java` に移動。クラス名・コンストラクタ名を変更し `ReplyGenerationPort` を実装、`generateResponse(String)` → `generateReply(String)`（`@Override` 付与）にリネーム。定数（アラートキーワード・フロントエンド同期コメント含む）・全ロジックは値・コメントとも一字一句変更なし。`@Service` → `@Component` に変更（設計の対応表どおり）。
- `controller/ChatController.java` を `adapter/in/web/ChatController.java` に `git mv` し、依存を `GenerateChatReplyUseCase` に変更（フィールド名 `generateChatReplyUseCase`、呼び出しは `generateChatReplyUseCase.generateReply(request)`）。
- 新規: `config/ApplicationConfiguration.java`（`@Configuration`、`GenerateChatReplyUseCase` の `@Bean` メソッドのみ。suggest/monitoring 分はステップ5〜6の対象のため未追加）。
- テスト移動: `service/MockChatServiceTest.java` → `adapter/out/reply/KeywordMatchReplyGenerationAdapterTest.java`（`git mv`）。パッケージ宣言・クラス名・インスタンス生成の型を `KeywordMatchReplyGenerationAdapter` に変更し、メソッド呼び出しを `generateReply(...)` に変更。アサーション内容は一切変更なし。フィールド名 `mockChatService` はそのまま維持（変数名リネームは指示になかったため据え置き）。
- テスト移動: `controller/ChatControllerTest.java` → `adapter/in/web/ChatControllerTest.java`（`git mv`）。パッケージ宣言のみ変更、内容（アサーション・リクエスト）は不変。

### 検証結果

- `cd backend && ./mvnw -q compile` → 成功（警告・エラーなし）
- `cd backend && ./mvnw test` → `Tests run: 49, Failures: 0, Errors: 0, Skipped: 0` で BUILD SUCCESS（既存テスト含め全件 green。`KeywordMatchReplyGenerationAdapterTest` 22件、`ChatControllerTest` 12件を含む）

### 設計からの逸脱

なし。API・JSON 形式は一切変更していない。

### 既知の副作用（今回スコープ外・要対応は次ステップ）

`service/MonitoringMetricsService.java` のクラスコメント中に `{@link MockChatService}` という Javadoc 参照が残っており、`MockChatService` クラスは本ステップで削除済みのため解決不能な参照になっている（`./mvnw compile` では javadoc 生成を行わないためビルドエラーにはならない）。design.md の対応表では `MonitoringMetricsService` はステップ6で `RandomWalkMetricsGenerationAdapter` に移動する予定であり、その際にこの参照も追随修正されるべき（本タスクはステップ3〜4のみが対象のため修正しなかった）。

### スコープ外（今回未実装）

design.md の Implementation steps 5〜9（suggest/monitoring のヘキサゴナル化、ArchUnit ルールテスト本体、ドキュメント追随、全体検証）は本タスクの指示によりステップ3〜4のみに限定したため未実装。
