# Pipeline Config

dev-pipeline 実行時の本プロジェクト固有設定。各フェーズのサブエージェントには `## stack` と自フェーズのセクションを渡すこと。

## stack

- **backend**: Spring Boot 3 / Java 21 / Maven Wrapper（`backend/mvnw`、Maven のローカルインストール不要）。`com.example.chatbackend` はヘキサゴナルアーキテクチャ構成（`domain` / `application/port/in,out` / `application/service` / `adapter/in/web` / `adapter/out/*` / `config`）。application 層は Spring 非依存の POJO で、Bean 配線は `config/ApplicationConfiguration` の `@Bean` に集約。依存方向ルールは `backend/src/test/java/com/example/chatbackend/architecture/ArchitectureTest.java`（ArchUnit 4ルール）で機械検証される。
- **frontend**: Vue 3 + TypeScript + Vite + Pinia + Tailwind CSS 4。会話は localStorage 保存。テストランナー（vitest 等）・E2E スイートは未導入。
- バックエンドは全機能モック実装（キーワードマッチ応答・ハードコードFAQ・ランダム生成メトリクス）。実LLM・DB連携はスコープ外（README 参照）。
- **フロント⇔バック同期定数**: `adapter/out/reply/KeywordMatchReplyGenerationAdapter` のアラートキーワード ⇔ `frontend/src/constants/monitoringAlert.ts`、`adapter/out/suggest/CannedPhraseSuggestionAdapter` の定型文 ⇔ `frontend/src/constants/reportButtons.ts`。言語をまたぐため自動同期不可。片側を変更する際は必ず両側を同時に変更する。
- リポジトリ直下の `.vscode/settings.json` はユーザーの個人設定。コミット・変更禁止。

## commands

- backend ビルド確認: `cd backend && ./mvnw -q compile`
- backend テスト: `cd backend && ./mvnw test`
- backend 起動: `cd backend && ./mvnw spring-boot:run`（port 8080。**IDE デバッガ等の既存プロセスが 8080 を掴んでいることがある**ため、起動失敗時はまず既存プロセスを確認）
- frontend 型検査: `cd frontend && npx vue-tsc -b`（**bare の `vue-tsc --noEmit` は本リポジトリでは 0 ファイル検査の no-op なので使用禁止** — lessons-learned 2026-07-11 参照）
- frontend ビルド: `cd frontend && npm run build`
- frontend 起動: `cd frontend && npm run dev`（port 5173）
- frontend 自動テスト: なし（未導入。手動確認は README の疎通確認手順で行う）

## requirements

- 事前精査済みの要件は `docs/requirements/<feature-slug>.md`（refine-requirements スキルの形式）。FR/AC の ID は下流フェーズが参照する安定IDなので、更新時も既存番号を変えない。
- 過去の要件ドキュメントとの整合を確認する（例: FAQ データのハードコード維持は inquiry-faq-suggestion.md FR-5 の決定）。

## design

- ヘキサゴナル構成の依存方向ルール（ArchitectureTest の4ルール）を破る設計は不可。新しい外部依存・差し替え候補ロジックは出力ポート＋アダプタとして設計する。
- AC マッピング（全 AC ↔ 設計要素・テストの対応表）を必須セクションとする。
- API 変更を伴う場合は `docs/api/*.md` の更新方針を設計に含める。フロント⇔バック同期定数（stack 参照）に触れる場合は両側の変更を明記。

## implementation

- 各ステップ完了時に `./mvnw -q compile`（可能なら `./mvnw test`）が通る状態を保つ。
- application 層（`application/service`・ポートIF）に Spring・Jackson 以外のフレームワーク依存を持ち込まない。新規 Bean は `ApplicationConfiguration` への配線を忘れない（忘れると `ChatBackendApplicationTests` が NoSuchBeanDefinitionException で落ちる）。
- クラス移動時はロジック・定数値・コメント（特にフロント同期コメント）を保全し、`git mv` で履歴を残す。
- フロントの型検査は必ず `npx vue-tsc -b` か `npm run build`（commands 参照）。

## testing

- 変更したサイドのスイートを必ず実行する。backend は `./mvnw test` 全件。frontend は自動テスト未導入のため `npx vue-tsc -b` + `npm run build` を green の基準とする。
- API 互換性の検証で curl を使う場合、port 8080 の既存プロセスが検証対象のコードを実行しているとは限らない（IDE デバッガの旧プロセスが残っていることがある）。プロセスの起動元を確認するか、自分で起動し直してから検証する。
- フロント疎通の手動確認シナリオは README の疎通確認手順＋代表3シナリオ（担当者別レポート / 新規問い合わせフロー / モニタリングアラート応答）。

## review

- ポーリング・画面切替を伴う非同期取得では in-flight リクエストの stale レスポンス上書きレースを必ず確認する（lessons-learned 2026-07-18: monitoringStore の世代カウンタ導入の経緯）。
- リファクタリング diff は移動元/移動先の1対1突き合わせで挙動非互換（定数・文字列・アノテーションの欠落）を確認する。
- ArchitectureTest のルールが空集合マッチで素通しになっていないかに注意。

## publish

- push 前に `gh pr list --head <branch>` で既存 PR を確認し、あれば `gh pr create` ではなく `gh pr edit` で更新する（lessons-learned 2026-07-18: 過去にブランチ既存 PR で create が拒否された）。
- `git add -A` 禁止。ファイル名指定で stage する（`.vscode/settings.json` 等の巻き込み防止）。
- `docs/pipeline/<issue-number>/pr-description.md` はリポジトリの記録として残す（PR 作成後に削除しない）。
