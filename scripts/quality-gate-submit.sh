#!/usr/bin/env bash
# CI の計測結果（reports/ と各ツールの出力）を quality-gate の Ingest API に送る。
#
# 流れ: Run 作成 → 成果物のアップロード（あるものだけ） → finalize
# 仕様: https://github.com/ymiyamoto63/quality-gate/blob/main/docs/operations/ingest.md
#
# 必須の環境変数:
#   QG_BASE_URL       取り込み先の quality-gate の URL（例: https://quality-gate.example.com）
#   QG_INGEST_TOKEN   リポジトリ単位の Ingest Token（qg_<prefix>_<secret>）
#   QG_REPOSITORY     owner/name（GitHub Actions では GITHUB_REPOSITORY）
#   QG_COMMIT_SHA     計測したコミット（40 桁）
#   QG_BRANCH         ブランチ名
# 任意の環境変数:
#   QG_PR_NUMBER      Pull Request 番号
#   QG_RUNNER_TYPE    self-hosted | github-hosted（既定: github-hosted）
#   QG_TRIGGERED_BY   push / pull_request / local など（既定: local）
#   QG_CI_RUN_URL     CI の実行 URL
#   QG_SKIPPED        スキップを申告する指標 ID（空白区切り。例: "M-02"）
#   QG_REPORTS_DIR    計測結果の置き場（既定: reports）
set -euo pipefail

: "${QG_BASE_URL:?QG_BASE_URL が未設定です}"
: "${QG_INGEST_TOKEN:?QG_INGEST_TOKEN が未設定です}"
: "${QG_REPOSITORY:?QG_REPOSITORY が未設定です}"
: "${QG_COMMIT_SHA:?QG_COMMIT_SHA が未設定です}"
: "${QG_BRANCH:?QG_BRANCH が未設定です}"

REPORTS="${QG_REPORTS_DIR:-reports}"
API="${QG_BASE_URL%/}/api/v1/runs"
AUTH=(-H "Authorization: Bearer ${QG_INGEST_TOKEN}")

skipped_json() {
  local id reason
  for id in ${QG_SKIPPED:-}; do
    case "$id" in
      M-02) reason='ミューテーションテスト（PIT）の成果物が無いため' ;;
      *) reason='CI で計測しなかったため' ;;
    esac
    jq -n --arg id "$id" --arg reason "$reason" '{metricId: $id, reason: $reason}'
  done | jq -s '.'
}

REQUEST=$(jq -n \
  --arg repository "$QG_REPOSITORY" \
  --arg commitSha "$QG_COMMIT_SHA" \
  --arg branch "$QG_BRANCH" \
  --arg pr "${QG_PR_NUMBER:-}" \
  --arg runnerType "${QG_RUNNER_TYPE:-github-hosted}" \
  --arg triggeredBy "${QG_TRIGGERED_BY:-local}" \
  --arg ciRunUrl "${QG_CI_RUN_URL:-}" \
  --arg measuredAt "$(date -u +%FT%TZ)" \
  --argjson skippedMetrics "$(skipped_json)" \
  '{repository: $repository, commitSha: $commitSha, branch: $branch,
    runnerType: $runnerType, triggeredBy: $triggeredBy, measuredAt: $measuredAt,
    skippedMetrics: $skippedMetrics}
   + (if $pr != "" then {pullRequestNumber: ($pr | tonumber)} else {} end)
   + (if $ciRunUrl != "" then {ciRunUrl: $ciRunUrl} else {} end)')

RUN_ID=$(curl -sS --fail-with-body -X POST "$API" "${AUTH[@]}" \
  -H 'Content-Type: application/json' -d "$REQUEST" | jq -r '.runId')
echo "Run を作成しました: $RUN_ID"

# upload <type> <file> [component] [metadata]
# ファイルが無ければ送らない。未提出の指標は quality-gate が ERROR（未計測）として扱う
upload() {
  local type=$1 file=$2 component=${3:-} metadata=${4:-}
  if [ ! -s "$file" ]; then
    echo "::warning::成果物がありません（type=$type）: $file"
    return 0
  fi
  local args=(-F "file=@${file}")
  [ -n "$metadata" ] && args+=(-F "metadata=${metadata}")
  local query="type=${type}"
  [ -n "$component" ] && query="${query}&component=${component}"
  curl -sS --fail-with-body -X POST "${API}/${RUN_ID}/artifacts?${query}" "${AUTH[@]}" "${args[@]}" >/dev/null
  echo "送信しました: type=$type ${component:+component=$component }$file"
}

upload quality-gate-config .quality-gate.yml
upload jacoco-xml "$REPORTS/backend/jacoco.xml" backend
upload lcov "$REPORTS/frontend-coverage/lcov.info" frontend
# mutation プロファイルは全クラスを対象にするため mutationScope は all
upload pit-xml "$REPORTS/backend/mutations.xml" backend '{"mutationScope":"all"}'
upload pmd-xml "$REPORTS/backend/pmd.xml" backend
upload sarif "$REPORTS/trivy.sarif"
for junit in "$REPORTS"/contract/TEST-*.xml; do
  [ -e "$junit" ] || continue
  upload junit-xml "$junit" backend
done
if [ -e "$REPORTS/oasdiff-base-spec-missing" ]; then
  upload oasdiff-json "$REPORTS/oasdiff.json" backend '{"baseSpecMissing":true}'
else
  upload oasdiff-json "$REPORTS/oasdiff.json" backend
fi
upload axe-json "$REPORTS/axe-results.json" frontend

curl -sS --fail-with-body -X POST "${API}/${RUN_ID}/finalize" "${AUTH[@]}" | jq .
