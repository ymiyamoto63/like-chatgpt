# Lessons Learned

## 2026-07-18 — publish — 同一ブランチに既存PRがあり `gh pr create` が拒否
- **Failure**: `feature/sidebar-report-buttons` には過去機能のオープンPR（#1）が既に存在し、`gh pr create` が重複作成を拒否した。
- **Root cause**: 本リポジトリでは複数機能を同一featureブランチに積み続けており、ブランチ:PRが1:1で既存PRが残っている。
- **Fix**: `gh pr edit` で既存PR #1 のタイトル・本文を全体スコープに合わせて更新し、push済みコミットをそのPRに載せた。
- **Prevention**: publishフェーズでは push 前に `gh pr list --head <branch>` で既存PRの有無を確認し、あれば create ではなく edit で本文を更新する方針を最初から取ること。

## 2026-07-18 — review — ポーリングストアの stale レスポンス上書きレース
- **Failure**: `monitoringStore.ts` の `fetchSnapshot` が、ポーリング停止後に解決した古い fetch の結果で状態を上書きするレースをレビューで指摘された（画面を素早く往復すると最終更新時刻が巻き戻りうる）。
- **Root cause**: `stopPolling` はタイマー（`clearInterval`）だけを止め、進行中の非同期リクエストの結果を無効化する仕組みがなかった。
- **Fix**: `startPolling` ごとにインクリメントする世代カウンタ `fetchGeneration` を導入し、応答到着時に世代が変わっていたら成功・失敗とも結果を破棄。再テスト・再レビューでグリーン確認。
- **Prevention**: ポーリングや画面切替を伴う非同期取得を実装するときは、タイマー停止だけでなく in-flight リクエストの無効化（世代カウンタか AbortController）を最初から設計・実装に含めること。

## 2026-07-11 — implementation — bare `vue-tsc --noEmit` is a silent no-op in this repo
- **Failure**: A type-predicate error (TS2677) introduced in `frontend/src/api/chatApi.ts` passed "typecheck" in one implementation step, then blocked `npm run build` in a later step.
- **Root cause**: The repo's root `frontend/tsconfig.json` has `"files": []` and only resolves project references in `-b` mode, so bare `npx vue-tsc --noEmit` type-checks 0 files and always exits 0.
- **Fix**: The later implementer changed the type-guard parameter types to `unknown` and verified with `npx vue-tsc -b` / `npm run build` (both green).
- **Prevention**: For frontend verification in this repo, always use `npx vue-tsc -b` or `vue-tsc --noEmit -p tsconfig.app.json` (or `npm run build`) — never bare `vue-tsc --noEmit`.
