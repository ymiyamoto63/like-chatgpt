export const NODE_WIDTH = 140
export const NODE_HEIGHT = 76
export const VIEWBOX_WIDTH = 960
export const VIEWBOX_HEIGHT = 480

// バックエンドのトポロジー定義（MonitoringMetricsService）とは独立したハードコード。
// トポロジーを変更する際は両ファイルを必ず同時に更新すること（docs/design.md Risks節）。
export const NODE_POSITIONS: Record<string, { x: number; y: number }> = {
  internet: { x: 20, y: 202 },
  lb: { x: 180, y: 202 },
  'web-1': { x: 340, y: 60 },
  'web-2': { x: 340, y: 344 },
  'app-1': { x: 500, y: 60 },
  'app-2': { x: 500, y: 344 },
  cache: { x: 660, y: 60 },
  'db-primary': { x: 660, y: 344 },
  'db-replica': { x: 820, y: 202 },
}
