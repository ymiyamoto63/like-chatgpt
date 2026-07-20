# バックエンドへの port & adapter（ヘキサゴナルアーキテクチャ）適用

## 概要
バックエンド（Spring Boot、`com.example.chatbackend`）の全機能（chat / suggest / monitoring）を、現行の controller / service / repository / dto レイヤ構成から、標準的なヘキサゴナルアーキテクチャ構成（domain / application / adapter）へリファクタリングする。外部から観測できる振る舞い（APIのエンドポイント・JSON形式・応答内容）は一切変更しない。

## 背景・目的
本プロジェクトのバックエンドはモック実装（キーワードマッチ応答、ハードコードFAQ、ランダム生成メトリクス）で構成されており、README・コード内コメントに「将来、実LLM連携やDB/CMSへの差し替えを行う」方針が明記されている。差し替え対象のロジックを出力ポート背後のアダプタに隔離することで、将来の差し替えを「アダプタの追加・交換」だけで完結させられるようにする。これが本リファクタリングの主目的である（決定記録 Q2）。

## スコープ
- chat 機能（ChatController / MockChatService / FaqRepository / FaqEntry）のヘキサゴナル構成への移行
- suggest 機能（SuggestController / MockSuggestService）の同移行
- monitoring 機能（MonitoringController / MonitoringMetricsService）の同移行
- `dto` パッケージの record 群（ChatResponse、UiComponent 系、MonitoringSnapshot 系、SuggestResponse 等）の domain パッケージへの移動
- 入力ポート（ユースケースIF）・出力ポートの新設
- 既存ユニットテスト・コントローラテストの新構成への追随（パッケージ・クラス名参照の更新）
- ArchUnit の導入と依存方向ルールテストの追加

## 対象外（Non-goals）
- 実LLM連携・DB/CMS 連携の実装（差し替え「準備」のみが対象。README のスコープ外方針を維持）
- API の振る舞い・JSONスキーマ・エンドポイントの変更（`docs/api/*.md` 記載のスキーマは不変）
- フロントエンド（`frontend/`）の変更
- 認証機能の追加
- 応答シナリオ・FAQデータ・メトリクス生成ロジックの内容自体の変更

## 影響範囲
- バックエンド Java パッケージ構成全体（`backend/src/main/java/com/example/chatbackend/` 配下の全クラスの移動・分割・IF抽出）
- バックエンドテスト（`backend/src/test/` 配下の既存テストの参照更新、ArchUnit テストの追加）
- `backend/pom.xml`（ArchUnit の test スコープ依存追加）
- ドキュメント（README・`docs/api/*.md` はAPI不変のため原則変更不要。クラス名を参照している箇所があれば追随）
- フロントエンド・DBスキーマ: 影響なし

## 機能要件
- FR-1: パッケージ構成を標準ヘキサゴナル構成に再編する: `domain/`（ドメインモデル）、`application/port/in/`（ユースケースIF）、`application/port/out/`（出力ポートIF）、`application/service/`（ユースケース実装）、`adapter/in/web/`（Controller）、`adapter/out/`（出力ポート実装）。
- FR-2: chat / suggest / monitoring の各機能に入力ポート（ユースケースIF）を定義し、Controller は具象サービスではなく入力ポートIFに依存する。
- FR-3: MockChatService のキーワードマッチ応答生成ロジックは、応答生成の出力ポート（例: 応答生成ポート）の背後のアダプタ（`adapter/out/` 配下）に移す。ユースケース実装はポートIFのみに依存し、将来の実LLMアダプタはアダプタ追加のみで差し替え可能であること。
- FR-4: FaqRepository は FAQ 取得の出力ポートIF＋インメモリ実装アダプタに分離する（既存コメントの「実装のみ置き換えれば差し替え可能」という設計意図をポートIFとして明示化する）。
- FR-5: 既存の `dto` パッケージの record 群はドメインモデルとして `domain/` 配下に移動し、Web アダプタはそれを直接 JSON 化して返す（Web 専用 DTO・マッピング層は設けない）。リクエスト DTO（ChatRequest / SuggestRequest）のバリデーション挙動も現状維持。
- FR-6: 依存方向ルール（domain は application / adapter に依存しない、application は adapter に依存しない、Spring 依存は adapter と構成クラスに限る、等の設計フェーズで確定するルール）を ArchUnit テストとして追加する。
- FR-7: リファクタリング前後で全 API（`POST /api/chat`、`POST /api/suggest`、`GET /api/monitoring/snapshot`）のレスポンス JSON 形式・内容が変わらないこと。
- FR-8: suggest の候補生成ロジック（MockSuggestService の定型文マッチ）および monitoring のメトリクス生成ロジック（MonitoringMetricsService）も、chat の応答生成ロジック（FR-3）と同様に、それぞれの出力ポートの背後のアダプタ（`adapter/out/` 配下）に置く。ユースケース実装はポートIFのみに依存する。MonitoringMetricsService が保持するミュータブル状態（既存方針の明示的例外。制約セクション参照）は、リファクタリング後も出力アダプタ側が保持し続ける。

## 画面・UIフロー
なし（バックエンド内部構造のリファクタリングのみ。既存画面の挙動は不変）。

## 受け入れ基準
- AC-1: `./mvnw test` が全件成功する（既存テストの参照更新後）。（検証方法: 自動テスト）
- AC-2: ArchUnit テストが存在し、FR-6 の依存方向ルール違反を検出できる状態でパスする。（検証方法: 自動テスト）
- AC-3: 既存のコントローラテスト（ChatControllerTest / SuggestControllerTest / MonitoringControllerTest）がリファクタリング後も同じリクエスト／レスポンスの組で成功し、API 互換性を裏付ける。（検証方法: 自動テスト）
- AC-4: フロントエンドから疎通確認（README 記載の手順）を行い、代表シナリオ（担当者別レポート、新規問い合わせフロー、モニタリングアラート応答）が従来どおり動作する。（検証方法: 手動確認）
- AC-5: ユースケース実装（application/service）のクラスが adapter パッケージのクラスを import していない（AC-2 の ArchUnit で機械検証）。（検証方法: 自動テスト）

## 制約
- ビルドは Maven Wrapper（`./mvnw`）、JDK 21 以上（README 記載）。
- フロントエンドと文字列定数を一致させている箇所がある（MockChatService のアラートキーワード ⇔ `frontend/src/constants/monitoringAlert.ts`、MockSuggestService の定型文 ⇔ `frontend/src/constants/reportButtons.ts`）。移動後もコメントごと維持し、値を変えないこと。
- MonitoringMetricsService はミュータブル状態を持つシングルトンという既存方針の明示的例外（クラスコメント参照）。この状態保持の性質はリファクタリング後も維持する（FR-8 により、当該状態は出力アダプタ側が保持する）。
- FaqRepository のデータはハードコード維持（`docs/requirements/inquiry-faq-suggestion.md` FR-5）。
- 既存の requirements ドキュメントの FR/AC 参照（例: inquiry-faq-suggestion.md）を無効化しないこと。

## 重大な非機能要件
なし（外部挙動・性能特性を変えない内部リファクタリング）。

## 未決定事項
- ポート・アダプタの具体的な命名（例: ReplyGenerationPort / GenerateReplyUseCase 等） — 選択肢: 設計フェーズで決定 / 推奨仮置き: 設計フェーズに委任（要件レベルでは FR-1〜FR-4、FR-8 の構造が満たされればよい）/ 外れた場合の影響: なし（リネームのみ）。

## 決定記録
- Q1: 適用範囲 → バックエンド全体（chat / suggest / monitoring）（理由: コードベースが小さく追加コストが小さい。2つのアーキテクチャの混在を避ける）
- Q2: 主目的 → 将来の差し替え準備（実LLM・DB/CMS等）（理由: FaqRepository コメント等、既存の設計方針と一致）
- Q3: パッケージ構成 → 標準ヘキサゴナル構成（domain / application/port / application/service / adapter/in / adapter/out）（理由: 定番レイアウトでパッケージ名から意図が読める）
- Q4: キーワードマッチ応答ロジックの位置づけ → 出力ポート背後のアダプタ（理由: 将来の実LLM連携時にアダプタ追加だけで差し替え可能にする）
- Q5: 入力ポート → 定義する（Controller はユースケースIFに依存）（理由: 標準構成と整合し追加コスト小）
- Q6: dto パッケージの扱い → ドメインモデルとして再利用（Web DTO 分離・マッピング層なし）（理由: 本アプリは「UIコンポーネント付き応答」自体がドメインの関心事であり、モデル重複を避ける。API JSON 不変）
- Q7: ArchUnit → 導入する（理由: 依存方向ルールを自動検証でき、受け入れ基準を機械化できる）
- Q8: 「DIも導入」の意図確認 → 取り下げ（DI未導入との勘違い。Spring のコンストラクタインジェクションが全クラスで導入済みであることを確認。application 層の Spring 非依存化は要件に含めず、扱いは FR-6 の依存ルール確定時に設計フェーズで判断）
- Q9: suggest/monitoring のロジック位置づけ → chat と同様に出力ポート背後のアダプタ化（理由: Q1「バックエンド全体」・Q4「アダプタ化」の決定と対称的で、monitoring も将来実メトリクス連携への差し替え候補のため）

## 用語集
- ポート（port）: application 層が定義するインターフェース。入力ポート＝ユースケースIF（Controller から呼ばれる側）、出力ポート＝外部機能への要求IF（応答生成・FAQ取得など、application 層から呼ぶ側）。
- アダプタ（adapter）: ポートの実装または利用側の具象。入力アダプタ＝Controller、出力アダプタ＝ポートIFの実装（現状はすべてモック実装）。
- モック（Mock〜）: 本プロジェクトでは「実サービスの代わりにハードコード・キーワードマッチで応答する実装」を指す。リファクタリング後は出力アダプタとして位置づけられる。
