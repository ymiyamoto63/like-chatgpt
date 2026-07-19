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

## step 5-6

対象: `docs/pipeline/4/design.md` の Implementation steps 5〜6（ステップ1〜4は完了済み、ステップ7以降は未実装）。

### ステップ5: suggest 機能のヘキサゴナル化（FR-2, FR-8）

- 新規: `application/port/in/GenerateSuggestionUseCase.java`（`SuggestResponse generateSuggestion(SuggestRequest request)`）。
- 新規: `application/port/out/SuggestionGenerationPort.java`（`SuggestResponse generateSuggestion(String text)`）。
- 新規: `application/service/SuggestService.java`（Spring アノテーションなしのPOJO。`GenerateSuggestionUseCase` を実装し `suggestionGenerationPort.generateSuggestion(request.text())` に委譲するのみ）。
- `service/MockSuggestService.java` を `git mv` で `adapter/out/suggest/CannedPhraseSuggestionAdapter.java` に移動。クラス名を変更し `SuggestionGenerationPort` を実装、`suggest(String)` → `generateSuggestion(String)`（`@Override` 付与）にリネーム。定型文定数（`CANNED_PHRASES`）・フロントエンド同期コメント・全ロジックは値・コメントとも一字一句変更なし。`@Service` → `@Component` に変更（設計の出力ポート・アダプタ命名対応表どおり）。
- `controller/SuggestController.java` を `adapter/in/web/SuggestController.java` に `git mv` し、依存を `GenerateSuggestionUseCase` に変更（フィールド名 `generateSuggestionUseCase`、呼び出しは `generateSuggestionUseCase.generateSuggestion(request)`）。
- `config/ApplicationConfiguration.java` に `GenerateSuggestionUseCase` の `@Bean` メソッドを追加。
- テスト移動: `service/MockSuggestServiceTest.java` → `adapter/out/suggest/CannedPhraseSuggestionAdapterTest.java`（`git mv`）。パッケージ宣言・クラス名・インスタンス生成の型を `CannedPhraseSuggestionAdapter` に変更し、メソッド呼び出しを `generateSuggestion(...)` に変更。アサーション内容は一切変更なし（フィールド名 `service` は既存のまま据え置き）。
- テスト移動: `controller/SuggestControllerTest.java` → `adapter/in/web/SuggestControllerTest.java`（`git mv`）。パッケージ宣言のみ変更、内容（アサーション・リクエスト）は不変。

### ステップ6: monitoring 機能のヘキサゴナル化（FR-2, FR-8）

- 新規: `application/port/in/GetMonitoringSnapshotUseCase.java`（`MonitoringSnapshot getSnapshot()`）。
- 新規: `application/port/out/MetricsGenerationPort.java`（`MonitoringSnapshot generateSnapshot()`）。
- 新規: `application/service/MonitoringService.java`（Spring アノテーションなしのPOJO。`GetMonitoringSnapshotUseCase` を実装し `metricsGenerationPort.generateSnapshot()` に委譲するのみ）。
- `service/MonitoringMetricsService.java` を `git mv` で `adapter/out/monitoring/RandomWalkMetricsGenerationAdapter.java` に移動。クラス名・コンストラクタ名を変更し `MetricsGenerationPort` を実装、`getSnapshot()` → `generateSnapshot()`（`@Override` 付与、`synchronized` 維持）にリネーム。ミュータブル状態（`tickCount`・スパイク管理フィールド・`MetricState` マップ群）・ランダムウォーク／スパイクアルゴリズム・トポロジー定義データは値・ロジックとも一字一句変更なし。`@Service` → `@Component` に変更（設計の出力ポート・アダプタ命名対応表どおり。Spring 既定のシングルトンスコープを維持）。
  - クラスコメント中の dangling Javadoc 参照 `{@link MockChatService}`（step 3-4 で既知の副作用として記録済み）を `{@link KeywordMatchReplyGenerationAdapter}` に修正し、`import com.example.chatbackend.adapter.out.reply.KeywordMatchReplyGenerationAdapter;` を追加してリンクが解決可能な状態にした。`{@link #getSnapshot()}` も `{@link #generateSnapshot()}` に修正。
- `controller/MonitoringController.java` を `adapter/in/web/MonitoringController.java` に `git mv` し、依存を `GetMonitoringSnapshotUseCase` に変更（フィールド名 `getMonitoringSnapshotUseCase`、呼び出しは `getMonitoringSnapshotUseCase.getSnapshot()`）。
- `config/ApplicationConfiguration.java` に `GetMonitoringSnapshotUseCase` の `@Bean` メソッドを追加（これで3ユースケースすべての Bean 配線が完了）。
- テスト移動: `service/MonitoringMetricsServiceTest.java` → `adapter/out/monitoring/RandomWalkMetricsGenerationAdapterTest.java`（`git mv`）。パッケージ宣言・クラス名・インスタンス生成の型を `RandomWalkMetricsGenerationAdapter` に変更し、メソッド呼び出しを `generateSnapshot()` に変更。アサーション内容は一切変更なし（フィールド名 `monitoringMetricsService` は既存のまま据え置き）。
- テスト移動: `controller/MonitoringControllerTest.java` → `adapter/in/web/MonitoringControllerTest.java`（`git mv`）。パッケージ宣言のみ変更、内容（アサーション・リクエスト）は不変。

### 旧パッケージの削除確認

`find backend/src -path '*/chatbackend/controller*' -o -path '*/chatbackend/service*' -o -path '*/chatbackend/repository*' -o -path '*/chatbackend/dto*'` の結果はゼロ件。旧 `controller/` `service/` `repository/` `dto/` の4パッケージはファイルが残っていないことを確認し、空になったディレクトリ（`main/controller`・`main/repository`・`main/service`・`test/controller`・`test/service`）を `rmdir` で削除した。

### 検証結果

- `cd backend && ./mvnw -q compile` → 成功（警告・エラーなし）
- `cd backend && ./mvnw test` → `Tests run: 49, Failures: 0, Errors: 0, Skipped: 0` で BUILD SUCCESS（既存テスト含め全件 green。`CannedPhraseSuggestionAdapterTest` 7件、`SuggestControllerTest` 3件、`RandomWalkMetricsGenerationAdapterTest` 3件、`MonitoringControllerTest` 1件を含む）

### 設計からの逸脱

なし。API・JSON 形式は一切変更していない。

### スコープ外（今回未実装）

design.md の Implementation steps 7〜9（ArchUnit ルールテスト本体の追加、ドキュメント参照の追随、全体検証の README 手動確認）は本タスクの指示によりステップ5〜6のみに限定したため未実装。

## step 7-8

対象: `docs/pipeline/4/design.md` の Implementation steps 7〜8（ステップ1〜6は完了済み、ステップ9の全体検証・README手動確認は未実施）。

### ステップ7: ArchUnit ルールテストの追加（FR-6, AC-2, AC-5）

- 新規: `backend/src/test/java/com/example/chatbackend/architecture/ArchitectureTest.java`。`@AnalyzeClasses(packages = "com.example.chatbackend", importOptions = ImportOption.DoNotIncludeTests.class)` を指定し、design.md「ArchUnit 依存ルール」節の4ルールをそのまま実装。
  1. `domainMustNotDependOnApplicationOrAdapter`（domain は application/adapter に依存しない）
  2. `applicationMustNotDependOnAdapter`（application は adapter に依存しない）
  3. `applicationServiceMustNotDependOnAdapter`（AC-5 のトレーサビリティ用の独立ルール）
  4. `onlyAdapterAndConfigMayDependOnSpring`（domain/application から `org.springframework..` への依存禁止）

**AC-2 の RED 確認（作業記録）**: `application/service/ChatService.java` に一時的に `import com.example.chatbackend.adapter.out.reply.KeywordMatchReplyGenerationAdapter;` を追加し、未使用フィールド `private final KeywordMatchReplyGenerationAdapter tempViolation = null;`（`@SuppressWarnings("unused")` 付与）を混入させた状態で `./mvnw test -Dtest=ArchitectureTest` を実行。結果:
- `applicationMustNotDependOnAdapter` → **FAILURE**（`ChatService.tempViolation` が `adapter.out.reply.KeywordMatchReplyGenerationAdapter` 型を持つことを検出）
- `applicationServiceMustNotDependOnAdapter` → **FAILURE**（同上。`application.service` サブパッケージ限定ルールでも同一違反を検出）
- `domainMustNotDependOnApplicationOrAdapter` → 影響なし（`domain` 側の変更ではないため対象外、PASS のまま）
- `onlyAdapterAndConfigMayDependOnSpring` → 影響なし（Spring への依存ではないため対象外、PASS のまま）

（`Tests run: 4, Failures: 2` で BUILD FAILURE を確認）。ルール2・3が期待どおり違反を検出できることを確認した後、`ChatService.java` を `git diff` で差分ゼロ（コミット済み状態と完全一致）になるまで元に戻した。違反コードは最終状態に一切残していない。

その後 `./mvnw test`（全件）を実行し、`ArchitectureTest` の4ルールすべて GREEN、既存49件と合わせて `Tests run: 53, Failures: 0, Errors: 0, Skipped: 0` で BUILD SUCCESS を確認。

### ステップ8: ドキュメント参照の追随

API仕様・JSONスキーマ・サンプル値は一切変更せず、クラス名・テストファイルパスの記載のみを新構成に更新した。

- `docs/api/chat-response-schema.md`: `MockChatService` → `KeywordMatchReplyGenerationAdapter`、テストパス `backend/src/test/java/com/example/chatbackend/ChatControllerTest.java` → `backend/src/test/java/com/example/chatbackend/adapter/in/web/ChatControllerTest.java`
- `docs/api/suggest-api.md`: `MockSuggestService` → `CannedPhraseSuggestionAdapter`
- `docs/api/monitoring-response-schema.md`: `MonitoringMetricsService` → `RandomWalkMetricsGenerationAdapter`（2箇所）、`@Service` シングルトンBean → `@Component` シングルトンBean、テストパス `backend/src/test/java/com/example/chatbackend/MonitoringControllerTest.java` → `backend/src/test/java/com/example/chatbackend/adapter/in/web/MonitoringControllerTest.java`

**確認**: `grep -rn "MockChatService\|MockSuggestService\|MonitoringMetricsService\|FaqRepository\b"` および旧パッケージパス（`controller/`・`service/`・`repository/`・`dto/`）参照を `docs/api/*.md`・`README.md` に対して実行し、いずれもゼロ件であることを確認した。README.md には元々これらのクラス名・旧パスへの言及が無かったため変更なし。

### 検証結果

- `cd backend && ./mvnw -q compile` → 成功（警告・エラーなし）
- `cd backend && ./mvnw test` → `Tests run: 53, Failures: 0, Errors: 0, Skipped: 0` で BUILD SUCCESS（既存49件 + `ArchitectureTest` 4件）

### 設計からの逸脱

なし。API・JSONスキーマ・サンプル値は一切変更していない。

### スコープ外（今回未実装）

design.md の Implementation step 9（`./mvnw` による全体検証は本タスクでも実施済みだが、README記載手順でのフロントエンド疎通確認・代表シナリオ3種の手動確認（AC-4）は本タスクの指示によりステップ7〜8のみに限定したため未実施）。
