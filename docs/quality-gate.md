# quality-gate による品質計測

like-chatgpt は [quality-gate](https://github.com/ymiyamoto63/quality-gate) の計測対象リポジトリです。
計測は GitHub Actions（`.github/workflows/quality-gate.yml`）で行い、成果物を quality-gate の Ingest API に送ります。
合否は quality-gate がリポジトリ直下の `.quality-gate.yml` を使って判定します（現在は `report-only`）。

## 計測する指標と成果物

| 指標 | ツール | 出力先 | `type` / `component` |
| --- | --- | --- | --- |
| M-01 ブランチカバレッジ | JaCoCo | `backend/target/site/jacoco/jacoco.xml` | `jacoco-xml` / `backend` |
| M-01 ブランチカバレッジ | Vitest（v8） | `reports/frontend-coverage/lcov.info` | `lcov` / `frontend` |
| M-02 ミューテーションスコア | PIT | `backend/target/pit-reports/mutations.xml` | `pit-xml` / `backend`（`mutationScope=all`） |
| M-03〜05 性能 | — | — | 未整備のため `.quality-gate.yml` で無効化 |
| M-06 脆弱性 | Trivy | `reports/trivy.sarif` | `sarif` |
| M-07 循環的複雑度 | PMD（`backend/pmd-ruleset.xml`） | `backend/target/pmd.xml` | `pmd-xml` / `backend` |
| M-08 API 契約テスト | JUnit（`*ControllerTest`） | `backend/target/surefire-reports/` | `junit-xml` / `backend` |
| M-09 API 破壊的変更 | oasdiff（`api/openapi.yml`） | `reports/oasdiff.json` | `oasdiff-json` / `backend` |
| M-10 アクセシビリティ | Playwright + axe-core | `reports/axe-results.json` | `axe-json` / `frontend` |

`api/openapi.yml` は結合テスト `OpenApiExportIT` が起動中のアプリから書き出す生成物です。
API を変更したら `mvn verify` で再生成してコミットしてください（CI は差分が無いことを検証します）。

## ローカルでの計測

```bash
# バックエンド: テスト + JaCoCo + PMD + OpenAPI 生成（api/openapi.yml）
cd backend
./mvnw verify
./mvnw -P mutation test          # PIT（target/pit-reports/）

# フロントエンド
cd ../frontend
npm run test                     # Vitest
npm run test:coverage            # カバレッジ（../reports/frontend-coverage/）
npm run test:a11y                # Playwright + axe-core（../reports/axe-results.json）
```

`test:a11y` は dev server を自動で起動し、API はテスト内でモックします（バックエンドの起動は不要）。
Playwright の同梱ブラウザを取得できない環境では、`E2E_CHROMIUM` に Chromium の実行ファイルを指定します。

## quality-gate への送信

1. quality-gate の **管理 › リポジトリ管理** で `ymiyamoto63/like-chatgpt` を登録し、Ingest Token を発行する
2. このリポジトリの **Settings → Secrets and variables → Actions** に次を設定する

   | 種別 | 名前 | 値 |
   | --- | --- | --- |
   | Variables | `QG_BASE_URL` | 取り込み先の quality-gate の URL |
   | Secrets | `QG_INGEST_TOKEN` | 発行した Ingest Token（`qg_<prefix>_<secret>`） |

未設定の間は、計測と成果物（`quality-gate-reports`）の保存だけを行い、送信はスキップします。
ローカルから送る場合は、上の手順で `reports/` に成果物を揃えたうえで次を実行します
（`reports/backend/` と `reports/contract/` にはバックエンドの成果物をコピーしておきます。手順はワークフローの「バックエンドの成果物の収集」を参照）。

```bash
QG_BASE_URL=http://localhost:8080 QG_INGEST_TOKEN=qg_... \
QG_REPOSITORY=ymiyamoto63/like-chatgpt QG_COMMIT_SHA=$(git rev-parse HEAD) QG_BRANCH=main \
  ./scripts/quality-gate-submit.sh
```
