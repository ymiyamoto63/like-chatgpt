# Lessons Learned

## 2026-07-11 — implementation — bare `vue-tsc --noEmit` is a silent no-op in this repo
- **Failure**: A type-predicate error (TS2677) introduced in `frontend/src/api/chatApi.ts` passed "typecheck" in one implementation step, then blocked `npm run build` in a later step.
- **Root cause**: The repo's root `frontend/tsconfig.json` has `"files": []` and only resolves project references in `-b` mode, so bare `npx vue-tsc --noEmit` type-checks 0 files and always exits 0.
- **Fix**: The later implementer changed the type-guard parameter types to `unknown` and verified with `npx vue-tsc -b` / `npm run build` (both green).
- **Prevention**: For frontend verification in this repo, always use `npx vue-tsc -b` or `vue-tsc --noEmit -p tsconfig.app.json` (or `npm run build`) — never bare `vue-tsc --noEmit`.
