# バックエンドへの port & adapter（ヘキサゴナルアーキテクチャ）適用リファクタリング（Issue #4）

## 概要

バックエンド（Spring Boot 3、Java 21）の全機能（chat / suggest / monitoring）を、ヘキサゴナルアーキテクチャ構成（domain / application/port / application/service / adapter）にリファクタリングしました。外部から観測できる振る舞い（APIのエンドポイント・JSON形式・応答内容）は**一切変更していません**。

このリファクタリングの目的は、キーワードマッチ応答・FAQ取得・候補生成・メトリクス生成といった現在のモック実装を出力ポート背後のアダプタに隔離することで、将来の実LLM連携・DB/CMS連携時に「アダプタの追加・交換」だけで差し替え可能な設計を実現することです。

---

## 主要変更点

### 1. パッケージ構成の再編

```
com.example.chatbackend
├── domain/                    # ドメインモデル層
│   ├── chat/                 # ChatRequest, ChatResponse, UiComponent 類
│   ├── faq/                  # FaqEntry
│   ├── suggest/              # SuggestRequest, SuggestResponse
│   └── monitoring/           # MonitoringSnapshot, MonitoringNode, MonitoringEdge
├── application/              # ユースケース・ポート定義層（Spring非依存）
│   ├── port/
│   │   ├── in/              # GenerateChatReplyUseCase, GenerateSuggestionUseCase, GetMonitoringSnapshotUseCase
│   │   └── out/             # ReplyGenerationPort, FaqQueryPort, SuggestionGenerationPort, MetricsGenerationPort
│   └── service/             # ChatService, SuggestService, MonitoringService（薄い委譲実装）
├── adapter/                  # ポート実装・入出力アダプタ層
│   ├── in/web/              # ChatController, SuggestController, MonitoringController
│   └── out/                 # 実装アダプタ（キーワードマッチ、インメモリFAQ等）
│       ├── reply/           # KeywordMatchReplyGenerationAdapter
│       ├── faq/             # InMemoryFaqQueryAdapter
│       ├── suggest/         # CannedPhraseSuggestionAdapter
│       └── monitoring/      # RandomWalkMetricsGenerationAdapter
└── config/                   # ApplicationConfiguration（アプリケーション層Bean配置）
```

旧パッケージ（`controller/`, `service/`, `repository/`, `dto/`）は完全に削除されました。

### 2. ポート・アダプタ設計

| ポート | メソッド | 実装アダプタ | 移動元 |
|---|---|---|---|
| **入力ポート（3つ）** | — | — | — |
| `GenerateChatReplyUseCase` | `generateReply(ChatRequest): ChatResponse` | `ChatService` | `MockChatService` |
| `GenerateSuggestionUseCase` | `generateSuggestion(SuggestRequest): SuggestResponse` | `SuggestService` | `MockSuggestService` |
| `GetMonitoringSnapshotUseCase` | `getSnapshot(): MonitoringSnapshot` | `MonitoringService` | `MonitoringMetricsService` |
| **出力ポート（4つ）** | — | — | — |
| `ReplyGenerationPort` | `generateReply(String): ChatResponse` | `KeywordMatchReplyGenerationAdapter` | `MockChatService` の全ロジック |
| `FaqQueryPort` | `findByTitle(String), findTitlesByCategory(String)` | `InMemoryFaqQueryAdapter` | `FaqRepository` |
| `SuggestionGenerationPort` | `generateSuggestion(String): SuggestResponse` | `CannedPhraseSuggestionAdapter` | `MockSuggestService` |
| `MetricsGenerationPort` | `generateSnapshot(): MonitoringSnapshot` | `RandomWalkMetricsGenerationAdapter` | `MonitoringMetricsService` |

- Controller は入力ポートIF のみに依存（具象実装に依存せず）
- ユースケース実装（`application.service`）は Spring アノテーションなしの POJO で、Bean 配置は `config/ApplicationConfiguration` に集約
- 出力ポート実装アダプタが実際のロジック（キーワードマッチ、FAQ参照等）を保持

### 3. ArchUnit による依存方向ルールの自動検証

新規ファイル `backend/src/test/java/com/example/chatbackend/architecture/ArchitectureTest.java` で以下4ルールを実装・検証：

1. **domain は application / adapter に依存しない**
2. **application は adapter に依存しない**
3. **application.service は adapter を import しない**（AC-5 明示的対応）
4. **Spring 依存は adapter と構成クラス（config）に限る**

---

## テスト結果（AC-1〜AC-5）

| AC | 内容 | 結果 | 備考 |
|---|---|---|---|
| AC-1 | `./mvnw test` が全件成功する | ✅ PASS | **Tests run: 53, Failures: 0, Errors: 0** |
| AC-2 | ArchUnit テストが存在し、依存ルール違反を検出できる | ✅ PASS | 4ルール GREEN。一時的違反混入による RED も確認済み |
| AC-3 | 既存コントローラテスト3件が同じリクエスト/レスポンスで成功 | ✅ PASS | 3クラス計16件 GREEN。ファイル内容（パッケージ以外）完全一致 |
| AC-4 | フロントエンドからの疎通確認（代表シナリオ3種） | ⚠️ **未実施** | **マージ前に手動確認要** (下記参照) |
| AC-5 | `application/service` が adapter を import していない | ✅ PASS | ArchUnit + grep 二重確認。import ゼロ件 |

### API 互換性（FR-7）

curl で以下を実測検証済み、すべて `docs/api/*.md` のスキーマと完全一致：
- `POST /api/chat`「担当」「日別」「新規問い合わせ」「FAQ提示フロー」各シナリオ
- `POST /api/suggest`（候補生成）
- `GET /api/monitoring/snapshot`（構成図・メトリクス）

---

## ⚠️ AC-4 手動確認の実施について

AC-4（フロントエンドからの疎通確認）はユーザー側での手動実施事項です。**マージ前に以下の3シナリオを実施してください**：

1. **担当者別レポート**: チャット画面で「今月の問い合わせ件数を担当者別にまとめて」を送信 → 表＋棒グラフが従来どおり表示される
2. **新規問い合わせフロー**: 「新規問い合わせ」→ カテゴリ選択 → 緊急度選択 → 内容入力 → FAQ提示 → 「解決しないので問い合わせる」→ 確認画面→「登録する」まで一連の流れが崩れていない
3. **モニタリングアラート応答**: モニタリング画面でアラート発火 → 「原因を調べる」で原因分析表示 → 「閉じる」で応答が閉じる

バックエンド側の curl 検証（FR-7 API互換性）で JSON 形式は既に裏付けられているため、これらの手動確認は主に「フロントエンド側の描画・遷移が壊れていないこと」の最終確認です。

詳細は `/home/miyam/like-chatgpt/docs/pipeline/4/test-report.md` の「AC-4 手動確認手順」を参照してください。

---

## 対象ファイル

- **新規作成**: domain / application/port / application/service / adapter 配下の全クラス、ArchUnit テスト（計21ファイル）
- **削除**: 旧 controller / service / repository / dto パッケージ全ファイル
- **移動＋内容無変更**: テストクラス6つ
- **ドキュメント更新**: `docs/api/*.md` のクラス名・テストパス参照のみ更新（API 仕様は不変）
- **ビルド設定**: `backend/pom.xml` に archunit-junit5 (test scope) 依存追加

---

## Closes #4

Generated with [Claude Code](https://claude.com/claude-code)
