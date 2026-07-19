# コードレビュー: バックエンドへの port & adapter 適用（Issue #4）

対象 diff: `git diff 11dce1fbf70dc8d1b78230aeecc2a8827e40af6e..HEAD`（base SHA `11dce1f`、ブランチ `feature/4-ports-and-adapters`）
参照: `docs/pipeline/4/requirements.md`、`docs/pipeline/4/design.md`、`docs/pipeline/4/implementation-notes.md`、`docs/pipeline/4/test-report.md`

## レビュー方法

- `git diff` で全53ファイルの変更内容を確認。
- 移動元（base SHA 時点の `service/`・`repository/`・`controller/`・`dto/` 配下）と移動先の新規ファイルを `diff` で突き合わせ、パッケージ宣言・クラス名・import 以外の差分（ロジック・定数・コメント・アノテーション）がないことを機械的に確認。
- 全テストファイル（3ユニットテスト＋3コントローラテスト）についても同様に、アサーション内容の非変更を確認。
- `./mvnw -o test` を実測実行し、`Tests run: 53, Failures: 0, Errors: 0` を再現確認（test-report.md の記載と一致）。
- 旧パッケージ（`controller/` `service/` `repository/` `dto/`）・旧クラス名への参照が `backend/` 配下と `README.md` に残っていないことを `grep` で確認（`docs/requirements/*.md` は制約により意図的に未更新であることも design.md 記載どおり確認）。
- diff 全体のファイル一覧から `frontend/` および `docs/pipeline/4`・`docs/api/`・`docs/requirements/ports-and-adapters-refactor.md`・`backend/` 以外への変更がないことを確認。

## 結論

**Blocking な指摘なし。** 正確性・要件充足・設計整合のいずれにおいても実害のある defect は見つからなかった。全クラス移動は「パッケージ宣言・クラス名・呼び出しメソッド名・import・インターフェース実装宣言」のみの機械的差分であり、ロジック・定数値・コメント（フロントエンド同期コメント含む）は一字一句保持されている。ArchUnit ルールも実際に RED/GREEN 双方の挙動を手元で確認でき、空集合マッチ等の見せかけの緑ではない。

## 指摘（stylistic、修正不要と判断した観察事項）

いずれも blocking ではないが、参考として記録する。

1. **`InMemoryFaqQueryAdapter` のインターフェース実装メソッドに `@Override` が付与されていない**（`backend/src/main/java/com/example/chatbackend/adapter/out/faq/InMemoryFaqQueryAdapter.java` の `findByTitle` / `findTitlesByCategory`）。同じ移動パターンの他3アダプタ（`KeywordMatchReplyGenerationAdapter#generateReply`、`CannedPhraseSuggestionAdapter#generateSuggestion`、`RandomWalkMetricsGenerationAdapter#generateSnapshot`）はいずれも `@Override` を付与しているのに対し、`FaqQueryPort` の実装だけ付いていない。コンパイル・実行には影響しないが、一貫性の観点でのみ気になる程度（stylistic、修正不要）。

2. **`docs/api/monitoring-response-schema.md` のスパイクピーク値・発生間隔の記載（「78〜98の範囲」「平均12ティック±6ティック」）が実装値（`SPIKE_PEAK_MIN=90.0`〜`98.0`、`SPIKE_MEAN_INTERVAL_TICKS=1`・`SPIKE_INTERVAL_JITTER_TICKS=0`）と一致していない**。ただしこれは今回の diff で変更された行ではなく（クラス名参照のみ変更）、base SHA 時点の `MonitoringMetricsService.java` から既に同じ定数値だったことを確認済み。本リファクタリング（Issue #4）のスコープ外の既存不整合であり、今回の変更で悪化・新規発生したものではないため指摘対象外とする。

## 確認済みで問題なしと判断した主要ポイント

- **FR-7（挙動の非互換なし）**: `MockChatService`→`KeywordMatchReplyGenerationAdapter`、`MockSuggestService`→`CannedPhraseSuggestionAdapter`、`MonitoringMetricsService`→`RandomWalkMetricsGenerationAdapter`、`FaqRepository`→`InMemoryFaqQueryAdapter` の4クラスすべて、`diff` による突き合わせでロジック・定数・コメントの差分ゼロを確認。フロントエンド同期コメント（`CAUSE_ANALYSIS_KEYWORD` 等・`CANNED_PHRASES` のコメント）も一字一句保持。`UiComponent` の `@JsonSubTypes` の `name` 属性値・クラス参照も変更なし。`ChatRequest` の `@NotBlank`、`SuggestRequest` の意図的な非対称バリデーション（`@NotBlank` なし）も保持。
- **FR-8（ミュータブル状態の所在）**: `tickCount`・スパイク管理フィールド・`MetricState` マップ群はすべて `RandomWalkMetricsGenerationAdapter`（adapter 層）に保持され、`application/service/MonitoringService` は完全にステートレスな薄い委譲のみ。`synchronized` 修飾子も維持。
- **設計との一致**: パッケージ構成・クラス名・メソッド名（`generateReply`/`generateSuggestion`/`getSnapshot`/`generateSnapshot` 等）は design.md の対応表・命名確定表と完全一致。`application.service.*`（`ChatService`/`SuggestService`/`MonitoringService`）は Spring アノテーションなしの POJO で、Bean 配線は `config/ApplicationConfiguration` の `@Bean` 3つに集約。旧 `controller/`・`service/`・`repository/`・`dto/` パッケージにファイル・ディレクトリの残骸なし。
- **ArchUnit ルールの実効性**: `ArchitectureTest` の4ルールは `..domain..`／`..application..`／`..application.service..`／`org.springframework..` を正しく参照しており、パッケージ指定の typo による空集合マッチは見られない。`@AnalyzeClasses(... importOptions = ImportOption.DoNotIncludeTests.class)` も指定済み。実装記録・テストレポートに記載の一時的違反混入による RED 確認（`Tests run: 4, Failures: 2`）の手順・結果も整合しており、素通しルールではないことが実証されている。
- **Bean 配線**: `ApplicationConfiguration` に3ユースケースの `@Bean` が過不足なく定義されており、`ChatBackendApplicationTests`（`contextLoads`）を含む全53テストが実際に green であることを手元でも再現確認した。
- **フロントエンド・無関係ファイル**: diff のファイル一覧に `frontend/` 配下の変更は一切なし。`backend/`・`docs/pipeline/4/`・`docs/api/*.md`・`docs/requirements/ports-and-adapters-refactor.md` のみが対象。
- **コントローラテスト・ユニットテストの内容不変性**: 6テストクラスすべて、移動元とのプレーンテキスト diff でパッケージ宣言・クラス名・呼び出しメソッド名以外の差分ゼロを確認（アサーション文言・期待値は完全一致）。

## テストで検証済みのため再実行しなかった事項

- `./mvnw test` 全件 green（AC-1）
- ArchUnit RED/GREEN 検出確認（AC-2）
- curl による FR-7 API 互換性の実測比較（test-report.md 記載）

以上は test-report.md に実施記録があり、本レビューでは `./mvnw -o test` の再実行による再現確認のみ行った（53件 green を再現）。AC-4（フロントエンド疎通の手動確認）は本レビューのスコープ外（バックエンドのみのレビュー対象）。
