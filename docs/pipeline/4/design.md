# バックエンドへの port & adapter（ヘキサゴナルアーキテクチャ）適用 — 実装設計書

対象要件: `docs/pipeline/4/requirements.md`（= `docs/requirements/ports-and-adapters-refactor.md`）

## Approach

`com.example.chatbackend` を FR-1 の標準ヘキサゴナル構成（`domain` / `application/port/in` / `application/port/out` / `application/service` / `adapter/in/web` / `adapter/out/*`）に再編する。既存の `dto` 配下 record 群はパッケージ移動のみで `domain/` に、`FaqRepository` / `MockChatService` / `MockSuggestService` / `MonitoringMetricsService` の4クラスはそれぞれ「入力ポート＋出力ポート＋薄いアプリケーションサービス＋出力アダプタ」に分割する。既存ロジック（キーワードマッチ・FAQハードコード・メトリクス生成アルゴリズム・ミュータブル状態）は一切変更せず、クラスの置き場所とインターフェース境界だけを変える。Controller 3つは入力ポート（ユースケースIF）にのみ依存させ、`adapter/in/web` に移動する。FR-6 の依存ルールは ArchUnit で機械検証し、`application` 層（port・service とも）を Spring 非依存にする（Bean 配線は新設の `config/ApplicationConfiguration` に集約）。API のURL・JSON形式・応答内容は FR-7 により一切変更しない。

## Alternatives considered

- **application 層の Spring 非依存化**: 採用する（`application.service.*` から `@Service` 等の Spring アノテーションを外し、Bean 化は `config/ApplicationConfiguration` の `@Bean` メソッドで行う）。代替案は `application.service` に引き続き `@Service` を残し「Spring 依存は adapter 中心だが application も許容」とする案だが、これだと FR-6 が候補に挙げる「Spring 依存は adapter と構成クラスに限る」ルールを ArchUnit で機械検証できず（application 層の一部が Spring に依存するため）、Q7 の「受け入れ基準の機械化」という目的を弱める。コストは `config` パッケージに `@Bean` 3つを追加するだけなので、採用する。
- **ArchUnit ルールの表現方法**: `noClasses().that()...should().dependOnClassesThat()...` の単純な宣言的ルールを複数用意する方式を採用する。代替案は ArchUnit の `Architectures.onionArchitecture()` / `layeredArchitecture()` DSL だが、`onionArchitecture()` の `domainService` / `applicationService` という分類は本プロジェクトの `port.in` / `port.out` という分割と1:1対応せず、FR-6 の文言（「domain は application/adapter に依存しない」等）と直接対応させにくい。単純ルールのほうが FR-6 の各文言・AC-5 と1:1でトレースでき、実装者・レビュアーにとって読みやすいため採用する。
- **応答生成アダプタから FaqQueryPort を呼ぶ経路**: `KeywordMatchReplyGenerationAdapter`（応答生成の出力アダプタ）が直接 `FaqQueryPort` を利用する設計を採用する。代替案は `ChatService`（application.service）が `ReplyGenerationPort` と `FaqQueryPort` の両方をオーケストレーションする案だが、これだと問い合わせフローの内部状態（カテゴリ/緊急度/内容のパース結果等）を application 層に持ち出す必要が生じ、FR-3「MockChatService のキーワードマッチ応答生成ロジックは出力ポート背後のアダプタに移す」という決定（Q4）に反する。よって前者を採用し、`ChatService` は `replyGenerationPort.generateReply(message)` を呼ぶだけの薄い実装とする。

## 新パッケージ構成

```
com.example.chatbackend
├── ChatBackendApplication.java                          # 変更なし（ルート、@SpringBootApplication）
├── config/
│   ├── WebConfig.java                                    # 変更なし（CORS設定）
│   └── ApplicationConfiguration.java                     # 新規: 3つの application.service を @Bean 化
├── domain/
│   ├── chat/
│   │   ├── ChatRequest.java
│   │   ├── ChatResponse.java
│   │   └── component/
│   │       ├── UiComponent.java
│   │       ├── TableComponent.java
│   │       ├── BarChartComponent.java
│   │       ├── ChoicesComponent.java
│   │       ├── TrendChartComponent.java
│   │       └── FaqListComponent.java
│   ├── faq/
│   │   └── FaqEntry.java
│   ├── suggest/
│   │   ├── SuggestRequest.java
│   │   └── SuggestResponse.java
│   └── monitoring/
│       ├── MonitoringSnapshot.java
│       ├── MonitoringNode.java
│       └── MonitoringEdge.java
├── application/
│   ├── port/
│   │   ├── in/
│   │   │   ├── GenerateChatReplyUseCase.java
│   │   │   ├── GenerateSuggestionUseCase.java
│   │   │   └── GetMonitoringSnapshotUseCase.java
│   │   └── out/
│   │       ├── ReplyGenerationPort.java
│   │       ├── FaqQueryPort.java
│   │       ├── SuggestionGenerationPort.java
│   │       └── MetricsGenerationPort.java
│   └── service/
│       ├── ChatService.java
│       ├── SuggestService.java
│       └── MonitoringService.java
└── adapter/
    ├── in/
    │   └── web/
    │       ├── ChatController.java
    │       ├── SuggestController.java
    │       └── MonitoringController.java
    └── out/
        ├── reply/
        │   └── KeywordMatchReplyGenerationAdapter.java   # 旧 MockChatService の全ロジック
        ├── faq/
        │   └── InMemoryFaqQueryAdapter.java               # 旧 FaqRepository の全ロジック
        ├── suggest/
        │   └── CannedPhraseSuggestionAdapter.java         # 旧 MockSuggestService の全ロジック
        └── monitoring/
            └── RandomWalkMetricsGenerationAdapter.java    # 旧 MonitoringMetricsService の全ロジック（ミュータブル状態含む）
```

旧 `controller/` `service/` `repository/` `dto/` の4パッケージは、全ファイル移動後は空になるため削除する。

## 既存クラス → 新配置 対応表

| 既存 | 新配置 | 区分 |
|---|---|---|
| `dto/chat/ChatRequest.java` | `domain/chat/ChatRequest.java` | 移動のみ（`@NotBlank` はそのまま維持） |
| `dto/chat/ChatResponse.java` | `domain/chat/ChatResponse.java` | 移動のみ |
| `dto/component/UiComponent.java` | `domain/chat/component/UiComponent.java` | 移動のみ（`@JsonTypeInfo`/`@JsonSubTypes` の `name` 値は不変） |
| `dto/component/BarChartComponent.java` 他4種 | `domain/chat/component/*.java` | 移動のみ |
| `dto/monitoring/MonitoringEdge.java` 他2種 | `domain/monitoring/*.java` | 移動のみ |
| `dto/suggest/SuggestRequest.java` / `SuggestResponse.java` | `domain/suggest/*.java` | 移動のみ |
| `repository/FaqEntry.java` | `domain/faq/FaqEntry.java` | 移動のみ |
| `repository/FaqRepository.java` | `application/port/out/FaqQueryPort.java`（新規IF）＋ `adapter/out/faq/InMemoryFaqQueryAdapter.java`（実装。ハードコードFAQデータ・検索ロジックはそのまま移動、`@Repository` 維持） | 分割 |
| `service/MockChatService.java` | `application/port/in/GenerateChatReplyUseCase.java`（新規IF）＋ `application/service/ChatService.java`（新規・薄いユースケース実装）＋ `application/port/out/ReplyGenerationPort.java`（新規IF）＋ `adapter/out/reply/KeywordMatchReplyGenerationAdapter.java`（既存キーワードマッチ全ロジック・定数・コメントをそのまま移動） | 分割 |
| `service/MockSuggestService.java` | `application/port/in/GenerateSuggestionUseCase.java` ＋ `application/service/SuggestService.java` ＋ `application/port/out/SuggestionGenerationPort.java` ＋ `adapter/out/suggest/CannedPhraseSuggestionAdapter.java`（定型文リスト・コメントをそのまま移動） | 分割 |
| `service/MonitoringMetricsService.java` | `application/port/in/GetMonitoringSnapshotUseCase.java` ＋ `application/service/MonitoringService.java` ＋ `application/port/out/MetricsGenerationPort.java` ＋ `adapter/out/monitoring/RandomWalkMetricsGenerationAdapter.java`（ミュータブル状態・アルゴリズム・クラスコメントをそのまま移動、FR-8） | 分割 |
| `controller/ChatController.java` | `adapter/in/web/ChatController.java` | 移動＋依存差し替え（`MockChatService` → `GenerateChatReplyUseCase`） |
| `controller/SuggestController.java` | `adapter/in/web/SuggestController.java` | 同上（→ `GenerateSuggestionUseCase`） |
| `controller/MonitoringController.java` | `adapter/in/web/MonitoringController.java` | 同上（→ `GetMonitoringSnapshotUseCase`） |
| `config/WebConfig.java` | `config/WebConfig.java` | 変更なし |
| `ChatBackendApplication.java` | 変更なし | 変更なし |
| （なし） | `config/ApplicationConfiguration.java` | 新規（3 application.service の `@Bean` 定義） |
| （なし） | `application/port/in/*.java`（3ファイル）、`application/port/out/*.java`（4ファイル） | 新規IF |

## ポート・アダプタの命名確定

### 入力ポート（3つ、`application/port/in/`）

| 入力ポート | メソッド | 実装（application/service） | 呼び出し元（adapter/in/web） |
|---|---|---|---|
| `GenerateChatReplyUseCase` | `ChatResponse generateReply(ChatRequest request)` | `ChatService` | `ChatController` |
| `GenerateSuggestionUseCase` | `SuggestResponse generateSuggestion(SuggestRequest request)` | `SuggestService` | `SuggestController` |
| `GetMonitoringSnapshotUseCase` | `MonitoringSnapshot getSnapshot()` | `MonitoringService` | `MonitoringController` |

### 出力ポート（4つ、`application/port/out/`）と実装アダプタ

| 出力ポート | メソッド | 実装アダプタ（`adapter/out/`） | 移動元 |
|---|---|---|---|
| `ReplyGenerationPort`（応答生成） | `ChatResponse generateReply(String message)` | `adapter/out/reply/KeywordMatchReplyGenerationAdapter`（`@Component`） | `MockChatService` |
| `FaqQueryPort`（FAQ取得） | `Optional<FaqEntry> findByTitle(String title)` / `List<String> findTitlesByCategory(String category)` | `adapter/out/faq/InMemoryFaqQueryAdapter`（`@Repository`） | `FaqRepository` |
| `SuggestionGenerationPort`（候補生成） | `SuggestResponse generateSuggestion(String text)` | `adapter/out/suggest/CannedPhraseSuggestionAdapter`（`@Component`） | `MockSuggestService` |
| `MetricsGenerationPort`（メトリクス生成） | `MonitoringSnapshot generateSnapshot()`（`synchronized`） | `adapter/out/monitoring/RandomWalkMetricsGenerationAdapter`（`@Component`、シングルトンスコープ既定値のままミュータブル状態を保持） | `MonitoringMetricsService` |

`KeywordMatchReplyGenerationAdapter` はコンストラクタで `FaqQueryPort` を受け取り、問い合わせフロー中の FAQ 参照（`findTitlesByCategory` / `findByTitle`）をそのまま呼び出す（前掲「Alternatives considered」参照）。

### application.service の Bean 配線（Spring 非依存化のため）

`application.service.*`（`ChatService` / `SuggestService` / `MonitoringService`）は Spring アノテーションを一切持たない POJO とし、`config/ApplicationConfiguration.java` に `@Configuration` ＋ `@Bean` メソッドを3つ用意して入力ポートIFとしてBean登録する。

```java
@Configuration
public class ApplicationConfiguration {

    @Bean
    GenerateChatReplyUseCase generateChatReplyUseCase(ReplyGenerationPort replyGenerationPort) {
        return new ChatService(replyGenerationPort);
    }

    @Bean
    GenerateSuggestionUseCase generateSuggestionUseCase(SuggestionGenerationPort suggestionGenerationPort) {
        return new SuggestService(suggestionGenerationPort);
    }

    @Bean
    GetMonitoringSnapshotUseCase getMonitoringSnapshotUseCase(MetricsGenerationPort metricsGenerationPort) {
        return new MonitoringService(metricsGenerationPort);
    }
}
```

`ReplyGenerationPort` / `SuggestionGenerationPort` / `MetricsGenerationPort` / `FaqQueryPort` の実 Bean は各出力アダプタの `@Component` / `@Repository` によって供給され、Spring がインターフェース型でコンストラクタ注入する（Q8 で確認済みの既存のコンストラクタインジェクション方針を維持）。

## ArchUnit 依存ルール（FR-6・AC-2・AC-5）

新規ファイル: `backend/src/test/java/com/example/chatbackend/architecture/ArchitectureTest.java`

`@AnalyzeClasses(packages = "com.example.chatbackend", importOptions = ImportOption.DoNotIncludeTests.class)` を使い、テストクラス自体をスキャン対象から除外した上で、次の `@ArchTest` ルールを定義する。

1. **domain は application / adapter に依存しない**
   ```java
   noClasses().that().resideInAPackage("..domain..")
       .should().dependOnClassesThat().resideInAnyPackage("..application..", "..adapter..");
   ```
2. **application は adapter に依存しない**（`port.in` / `port.out` / `service` すべてを含む）
   ```java
   noClasses().that().resideInAPackage("..application..")
       .should().dependOnClassesThat().resideInAPackage("..adapter..");
   ```
3. **application.service は adapter を import しない（AC-5 の明示的トレーサビリティ用。ルール2の部分集合だが要件文言と1:1対応させるため独立したルールとして残す）**
   ```java
   noClasses().that().resideInAPackage("..application.service..")
       .should().dependOnClassesThat().resideInAPackage("..adapter..");
   ```
4. **Spring 依存は adapter と構成クラス（config）に限る**（採用する。理由は「Alternatives considered」参照）
   ```java
   noClasses().that().resideInAnyPackage("..domain..", "..application..")
       .should().dependOnClassesThat().resideInAPackage("org.springframework..");
   ```

ルール4の「Spring依存はadapterと構成クラスに限る」は、`domain`/`application` からの Spring 依存を禁止するブラックリスト形式で表現する（ホワイトリスト形式で `adapter`/`config` 以外を洗い出すよりも、ルート直下の `ChatBackendApplication` を特別扱いする必要がなく、FR-6 の意図と等価かつ単純）。

`jakarta.validation.constraints.NotBlank`（`ChatRequest`）や `com.fasterxml.jackson.annotation.*`（`UiComponent` 系）は `org.springframework..` ではないため、これらのルールでは違反にならない。これは Q6（Web DTO 分離なし、domain が JSON 表現を兼ねる）の帰結として設計上許容する。

AC-2 が求める「違反を検出できる状態でパスする」ことの確認は、実装ステップ7で一時的に違反コード（例: `application.service` から `adapter.out` を import する）を混入させてテストが RED になることを確認した後、元に戻して GREEN であることを確認する手順で行う（恒久的な失敗テストはリポジトリに残さない）。

## pom.xml 変更

`<dependencies>` に ArchUnit の test スコープ依存を追加する（Spring Boot Parent BOM に archunit のバージョン管理は無いため、バージョンを明示指定する）。

```xml
<dependency>
    <groupId>com.tngtech.archunit</groupId>
    <artifactId>archunit-junit5</artifactId>
    <version>1.3.0</version>
    <scope>test</scope>
</dependency>
```

実装時に Maven Central で `archunit-junit5` の 1.3.x 系最新安定版を確認し、Java 21 / JUnit 5 と互換であることを確認した上でバージョンを確定すること。

## Files affected

### main（新規作成）
- `backend/src/main/java/com/example/chatbackend/domain/chat/ChatRequest.java`
- `backend/src/main/java/com/example/chatbackend/domain/chat/ChatResponse.java`
- `backend/src/main/java/com/example/chatbackend/domain/chat/component/{UiComponent,TableComponent,BarChartComponent,ChoicesComponent,TrendChartComponent,FaqListComponent}.java`
- `backend/src/main/java/com/example/chatbackend/domain/faq/FaqEntry.java`
- `backend/src/main/java/com/example/chatbackend/domain/suggest/{SuggestRequest,SuggestResponse}.java`
- `backend/src/main/java/com/example/chatbackend/domain/monitoring/{MonitoringSnapshot,MonitoringNode,MonitoringEdge}.java`
- `backend/src/main/java/com/example/chatbackend/application/port/in/{GenerateChatReplyUseCase,GenerateSuggestionUseCase,GetMonitoringSnapshotUseCase}.java`
- `backend/src/main/java/com/example/chatbackend/application/port/out/{ReplyGenerationPort,FaqQueryPort,SuggestionGenerationPort,MetricsGenerationPort}.java`
- `backend/src/main/java/com/example/chatbackend/application/service/{ChatService,SuggestService,MonitoringService}.java`
- `backend/src/main/java/com/example/chatbackend/adapter/in/web/{ChatController,SuggestController,MonitoringController}.java`
- `backend/src/main/java/com/example/chatbackend/adapter/out/reply/KeywordMatchReplyGenerationAdapter.java`
- `backend/src/main/java/com/example/chatbackend/adapter/out/faq/InMemoryFaqQueryAdapter.java`
- `backend/src/main/java/com/example/chatbackend/adapter/out/suggest/CannedPhraseSuggestionAdapter.java`
- `backend/src/main/java/com/example/chatbackend/adapter/out/monitoring/RandomWalkMetricsGenerationAdapter.java`
- `backend/src/main/java/com/example/chatbackend/config/ApplicationConfiguration.java`

### main（削除、内容は上記へ移動済み）
- `backend/src/main/java/com/example/chatbackend/dto/**`（全ファイル）
- `backend/src/main/java/com/example/chatbackend/repository/{FaqEntry,FaqRepository}.java`
- `backend/src/main/java/com/example/chatbackend/service/{MockChatService,MockSuggestService,MonitoringMetricsService}.java`
- `backend/src/main/java/com/example/chatbackend/controller/{ChatController,SuggestController,MonitoringController}.java`

### main（変更なし）
- `backend/src/main/java/com/example/chatbackend/ChatBackendApplication.java`
- `backend/src/main/java/com/example/chatbackend/config/WebConfig.java`

### test（移動・改名）
- `service/MockChatServiceTest.java` → `adapter/out/reply/KeywordMatchReplyGenerationAdapterTest.java`（`generateResponse(...)` → `generateReply(...)`、コンストラクタ引数 `FaqRepository` → `InMemoryFaqQueryAdapter`、import を domain 配下に更新。アサーション内容は不変）
- `service/MockSuggestServiceTest.java` → `adapter/out/suggest/CannedPhraseSuggestionAdapterTest.java`（`suggest(...)` → `generateSuggestion(...)`。アサーション内容は不変）
- `service/MonitoringMetricsServiceTest.java` → `adapter/out/monitoring/RandomWalkMetricsGenerationAdapterTest.java`（`getSnapshot()` → `generateSnapshot()`。アサーション内容は不変）
- `controller/ChatControllerTest.java` → `adapter/in/web/ChatControllerTest.java`（パッケージ移動のみ、内容不変）
- `controller/SuggestControllerTest.java` → `adapter/in/web/SuggestControllerTest.java`（同上）
- `controller/MonitoringControllerTest.java` → `adapter/in/web/MonitoringControllerTest.java`（同上）

### test（変更なし）
- `backend/src/test/java/com/example/chatbackend/ChatBackendApplicationTests.java`

### test（新規）
- `backend/src/test/java/com/example/chatbackend/architecture/ArchitectureTest.java`

### ビルド設定
- `backend/pom.xml`: archunit-junit5 (test scope) 依存追加

### ドキュメント（クラス名・テストパス参照の追随のみ、API仕様の記載内容は変更しない）
- `docs/api/chat-response-schema.md`: `MockChatService` → `KeywordMatchReplyGenerationAdapter`、テストファイルパス `backend/src/test/java/com/example/chatbackend/ChatControllerTest.java` → `backend/src/test/java/com/example/chatbackend/adapter/in/web/ChatControllerTest.java`
- `docs/api/suggest-api.md`: `MockSuggestService` → `CannedPhraseSuggestionAdapter`
- `docs/api/monitoring-response-schema.md`: `MonitoringMetricsService` → `RandomWalkMetricsGenerationAdapter`、`@Service` シングルトンBean → `@Component` シングルトンBean、テストファイルパスを `adapter/in/web/MonitoringControllerTest.java` に更新

`docs/requirements/*.md`（`inquiry-faq-suggestion.md` 等の過去機能要件）は本リファクタリングのスコープ外であり、制約「既存の requirements ドキュメントの FR/AC 参照を無効化しないこと」に従い変更しない（クラス名の記載が古くなるが、これは実装当時の設計記録として保持する）。

## Implementation steps

各ステップ完了時点で `./mvnw -q compile` が通り、可能な範囲で `./mvnw test` も green であることを確認しながら進める。

1. **pom.xml に ArchUnit 依存を追加**
   - `backend/pom.xml` に `archunit-junit5`（test scope）を追加。
   - 確認: `./mvnw -q compile`（既存コードは無変更なので影響なし）。

2. **domain モデルの移動（`dto` → `domain`）**
   - `dto/chat/*` → `domain/chat/*`、`dto/component/*` → `domain/chat/component/*`、`dto/monitoring/*` → `domain/monitoring/*`、`dto/suggest/*` → `domain/suggest/*`、`repository/FaqEntry.java` → `domain/faq/FaqEntry.java` をパッケージ宣言のみ変更して移動。
   - `dto/` パッケージを削除。
   - 参照元（`controller/*`、`service/*`、`repository/FaqRepository.java`、既存テスト6クラス）の import をすべて新パッケージに更新。
   - この時点ではクラス名・API・振る舞いは一切変わらない（純粋なパッケージ移動）。
   - 確認: `./mvnw -q compile` と `./mvnw test`（全件既存のまま green）。

3. **FAQ 出力ポート＋アダプタの抽出（FR-4）**
   - `application/port/out/FaqQueryPort.java` を新規作成（`findByTitle` / `findTitlesByCategory`）。
   - `adapter/out/faq/InMemoryFaqQueryAdapter.java` を新規作成し、`repository/FaqRepository.java` の実装（ハードコードFAQデータ・クラスコメント含む）をそのまま移動、`FaqQueryPort` を実装、`@Repository` を維持。
   - `repository/FaqRepository.java` を削除（`repository/` パッケージは空になる）。
   - `service/MockChatService.java`（この時点ではまだ旧パッケージ）のコンストラクタ引数を `FaqRepository` → `FaqQueryPort` に変更。
   - 確認: `./mvnw -q compile` と `./mvnw test`（`MockChatServiceTest` は `new MockChatService(new InMemoryFaqQueryAdapter())` に更新して green）。

4. **chat 機能のヘキサゴナル化（FR-2, FR-3）**
   - `application/port/in/GenerateChatReplyUseCase.java`、`application/port/out/ReplyGenerationPort.java` を新規作成。
   - `application/service/ChatService.java` を新規作成（`GenerateChatReplyUseCase` 実装、`ReplyGenerationPort` に委譲するのみ）。
   - `adapter/out/reply/KeywordMatchReplyGenerationAdapter.java` を新規作成し、`service/MockChatService.java` の全ロジック（定数・コメント含む）を移動、`ReplyGenerationPort` を実装、コンストラクタで `FaqQueryPort` を受け取る。
   - `service/MockChatService.java` を削除。
   - `controller/ChatController.java` を `adapter/in/web/ChatController.java` に移動し、依存を `GenerateChatReplyUseCase` に変更。
   - `config/ApplicationConfiguration.java` を新規作成し、`GenerateChatReplyUseCase` の `@Bean` メソッドを追加。
   - `service/MockChatServiceTest.java` を `adapter/out/reply/KeywordMatchReplyGenerationAdapterTest.java` に移動し、呼び出しを `generateReply(...)` に変更（アサーションは不変）。
   - `controller/ChatControllerTest.java` を `adapter/in/web/ChatControllerTest.java` に移動（内容不変）。
   - 確認: `./mvnw -q compile` と `./mvnw test`。

5. **suggest 機能のヘキサゴナル化（FR-2, FR-8）**
   - ステップ4と同じパターンで `GenerateSuggestionUseCase` / `SuggestionGenerationPort` / `SuggestService` / `CannedPhraseSuggestionAdapter` を作成し、`service/MockSuggestService.java` を削除。
   - `controller/SuggestController.java` を `adapter/in/web/SuggestController.java` に移動し依存を `GenerateSuggestionUseCase` に変更。
   - `config/ApplicationConfiguration.java` に `GenerateSuggestionUseCase` の `@Bean` を追加。
   - `service/MockSuggestServiceTest.java` → `adapter/out/suggest/CannedPhraseSuggestionAdapterTest.java`（`generateSuggestion(...)` に変更、アサーション不変）。
   - `controller/SuggestControllerTest.java` → `adapter/in/web/SuggestControllerTest.java`（内容不変）。
   - 確認: `./mvnw -q compile` と `./mvnw test`。

6. **monitoring 機能のヘキサゴナル化（FR-2, FR-8）**
   - ステップ4と同じパターンで `GetMonitoringSnapshotUseCase` / `MetricsGenerationPort` / `MonitoringService` / `RandomWalkMetricsGenerationAdapter` を作成し、`service/MonitoringMetricsService.java` を削除。ミュータブル状態・アルゴリズム・クラスコメントはそのまま `RandomWalkMetricsGenerationAdapter` に移す（FR-8）。
   - `controller/MonitoringController.java` を `adapter/in/web/MonitoringController.java` に移動し依存を `GetMonitoringSnapshotUseCase` に変更。
   - `config/ApplicationConfiguration.java` に `GetMonitoringSnapshotUseCase` の `@Bean` を追加。
   - `service/MonitoringMetricsServiceTest.java` → `adapter/out/monitoring/RandomWalkMetricsGenerationAdapterTest.java`（`generateSnapshot()` に変更、アサーション不変）。
   - `controller/MonitoringControllerTest.java` → `adapter/in/web/MonitoringControllerTest.java`（内容不変）。
   - この時点で旧 `controller/` `service/` `repository/` `dto/` の4パッケージにファイルが残っていないことを確認する。
   - 確認: `./mvnw -q compile` と `./mvnw test`。

7. **ArchUnit ルールテストの追加（FR-6, AC-2, AC-5）**
   - `backend/src/test/java/com/example/chatbackend/architecture/ArchitectureTest.java` を新規作成し、「ArchUnit 依存ルール」節の4ルールを実装。
   - 一時的に違反コード（例: `ChatService` に `adapter.out.reply.KeywordMatchReplyGenerationAdapter` を直接 import させる）を混入させ、当該ルールが RED になることを確認した後、元に戻す（コミットには含めない）。
   - 確認: `./mvnw test`（`ArchitectureTest` を含め全件 green）。

8. **ドキュメント参照の追随**
   - `docs/api/chat-response-schema.md` / `docs/api/suggest-api.md` / `docs/api/monitoring-response-schema.md` のクラス名・テストファイルパス記載を更新（「Files affected」節参照）。API仕様（エンドポイント・JSONスキーマ・サンプル値）自体は変更しない。

9. **全体検証**
   - `backend/` で `./mvnw -q compile` と `./mvnw test` を実行し全件成功を確認（AC-1）。
   - README 記載の手順でバックエンド・フロントエンドを起動し、代表シナリオ（担当者別レポート、新規問い合わせフロー、モニタリングアラート応答）を手動確認（AC-4）。

## Risks / edge cases

- **import 更新漏れ**: `dto` → `domain` の一括移動（ステップ2）で main/test 双方の参照を漏れなく更新する必要がある。IDE のリファクタリング機能または `grep -rl 'chatbackend\.dto\.'` で更新漏れを機械的に確認すること。
- **Jackson の型識別子**: `UiComponent` の `@JsonSubTypes.Type(value = ..., name = "table")` 等の `name` 属性値（JSON の `type` フィールドに出力される）はクラスの完全修飾名ではなく固定文字列のため、パッケージ移動そのものでは変化しない。ただし `value = BarChartComponent.class` 等のクラス参照は import 更新に追随させること。
- **フロントエンド同期コメント**: `KeywordMatchReplyGenerationAdapter`（旧 `MockChatService`）のアラートキーワード定数、`CannedPhraseSuggestionAdapter`（旧 `MockSuggestService`）の定型文定数は、値とコメント（フロントエンド定数ファイルへの参照）を一字一句変えずに移動すること。
- **domain 層への jakarta.validation / Jackson 依存**: `ChatRequest` の `@NotBlank`、`UiComponent` 系の `@JsonTypeInfo`/`@JsonSubTypes` は Spring ではないため ArchUnit ルール4には抵触しないが、Q6（Web DTO 分離なし）の意図的な帰結であり、レイヤ純粋主義的な違和感は許容する前提であることをレビュー時に明示する。
- **ミュータブル状態の共有範囲**: `RandomWalkMetricsGenerationAdapter` は Spring 既定の singleton スコープを維持する（明示的な `@Scope` 指定は不要）。ユニットテストでは現行同様 `new RandomWalkMetricsGenerationAdapter()` でテストごとに独立インスタンスを使うため、状態がテスト間で漏れないことを確認する。
- **ArchUnit のテストクラス誤検知**: `@AnalyzeClasses` のスキャン対象にテストソース自体を含めると、`ArchitectureTest` 自身や移動後のテストクラスがルール違反として誤検知される可能性がある。`importOptions = ImportOption.DoNotIncludeTests.class` を必ず指定すること。
- **archunit のバージョン管理**: Spring Boot Parent BOM は archunit の依存管理を持たないため、`pom.xml` にバージョンを明示する必要がある。Java 21 との互換性を実装時に確認すること。
- **Bean 配線の見落とし**: `application.service` を Spring 非依存にする設計上、`ApplicationConfiguration` に `@Bean` 定義を書き忘れると `ChatBackendApplicationTests`（`contextLoads`）が `NoSuchBeanDefinitionException` で失敗する。ステップ4〜6でそれぞれ追加を忘れないこと。
- **API 互換性（FR-7）**: リクエスト/レスポンスの JSON は一切変更しない。特に `SuggestRequest` に `@NotBlank` が無い（空文字列も200で「候補なし」を返す）という既存の非対称なバリデーション仕様は、ドメインモデル移動後も維持すること。

## Test strategy

- **既存ユニットテスト3件**: パッケージ移動・呼び出しメソッド名変更のみ行い、アサーション内容（reply文言・コンポーネント構造・値）は一切変更しない。これにより移動後もモックロジックの純粋な振る舞いが変わっていないことを保証する。
- **既存コントローラテスト3件（AC-3）**: パッケージ移動のみで内容は無変更。`@SpringBootTest` + `@AutoConfigureMockMvc` によるフルコンテキスト起動で、Bean配線（`ApplicationConfiguration` の `@Bean` 経由での DI）も含めてエンドツーエンドに検証される。これが green であること自体が API 互換性（FR-7）の自動テストによる裏付けとなる。
- **`ChatBackendApplicationTests`（無変更）**: Spring コンテキストが全 Bean（`application.service` の3 Bean を含む）を解決できることを保証する統合テスト。ここが落ちる場合は `ApplicationConfiguration` の配線ミスを疑う。
- **新規 `ArchitectureTest`（AC-2, AC-5）**: 「ArchUnit 依存ルール」節の4ルールを自動テストとして追加する。実装ステップ7で違反コードを一時的に混入させて RED を確認する手順により、「違反を検出できる状態でパスする」という AC-2 の要求を満たす。
- **API互換性の手動裏付け**: 実装者は移行前後で同一リクエストに対する `curl` レスポンスを比較する（例: `curl -s -X POST localhost:8080/api/chat -d '{"message":"担当"}' -H 'Content-Type: application/json'` を before/after で実行し diff が無いことを確認）。`docs/api/*.md` に記載のサンプル JSON との一致も確認する。
- **手動確認（AC-4）**: README 記載の手順でバックエンド・フロントエンドを起動し、以下3シナリオを実施する。
  1. 担当者別レポート（「今月の問い合わせ件数を担当者別にまとめて」等）
  2. 新規問い合わせフロー（トリガー→カテゴリ→緊急度→内容→FAQ提示→解決/未解決→確認→登録）
  3. モニタリングアラート応答（モニタリング画面でのアラート発火→「原因を調べる」選択→原因分析表示、「閉じる」）
  フロントエンドは今回変更禁止のため E2E（Playwright 等）テストの新規追加は不要。既存に E2E スイートが無ければ実施しない。

## AC mapping

| AC | 内容 | 実装ステップ | 検証（テスト戦略） |
|---|---|---|---|
| AC-1 | `./mvnw test` が全件成功する | ステップ2〜9（特にステップ9の全体検証） | 既存6テストクラス＋新規 `ArchitectureTest` が green |
| AC-2 | ArchUnit テストが存在し、FR-6 の依存方向ルール違反を検出できる状態でパスする | ステップ7（`ArchitectureTest` 追加、違反混入によるRED確認） | `ArchitectureTest` の4ルール＋一時的違反混入による手動RED確認 |
| AC-3 | 既存コントローラテスト3件がリファクタリング後も同じリクエスト/レスポンスの組で成功する | ステップ4, 5, 6（Controller を `adapter/in/web` へ移動、依存をユースケースIFに変更） | `ChatControllerTest` / `SuggestControllerTest` / `MonitoringControllerTest`（アサーション不変） |
| AC-4 | フロントエンドからの疎通確認（代表シナリオ3種が従来どおり動作） | ステップ9（全体検証） | 手動確認（担当者別レポート／新規問い合わせフロー／モニタリングアラート応答） |
| AC-5 | `application/service` のクラスが `adapter` パッケージのクラスを import していない | ステップ4, 5, 6（`ChatService`/`SuggestService`/`MonitoringService` を出力ポートIFのみに依存する薄い実装として設計） | `ArchitectureTest` の `applicationServiceMustNotDependOnAdapter` ルール |
