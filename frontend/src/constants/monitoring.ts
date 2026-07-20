export const WARNING_THRESHOLD = 70
export const DANGER_THRESHOLD = 90
export const POLL_INTERVAL_MS = 5000
// ノードごとのCPU/メモリ履歴リングバッファの最大保持件数（5秒間隔ポーリングで3分相当）
export const MAX_HISTORY_SAMPLES = 36

export type MonitoringLevel = 'normal' | 'warning' | 'danger'

export function getMonitoringLevel(value: number): MonitoringLevel {
  if (value >= DANGER_THRESHOLD) {
    return 'danger'
  }
  if (value >= WARNING_THRESHOLD) {
    return 'warning'
  }
  return 'normal'
}
