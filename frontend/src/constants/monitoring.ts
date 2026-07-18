export const WARNING_THRESHOLD = 70
export const DANGER_THRESHOLD = 90
export const POLL_INTERVAL_MS = 5000

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
