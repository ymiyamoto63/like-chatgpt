export type AlertSeverity = 'warning' | 'danger'

export interface MonitoringAlert {
  nodeId: string
  nodeLabel: string
  metric: 'cpu' | 'memory'
  metricLabel: 'CPU' | 'メモリ'
  value: number
  severity: AlertSeverity
}

// このアラート機能専用のしきい値。トポロジー図の枠色・ゲージ色に使う
// constants/monitoring.ts の WARNING_THRESHOLD(70)/DANGER_THRESHOLD(90) とは別軸の値
export const ALERT_WARNING_THRESHOLD = 80
export const ALERT_DANGER_THRESHOLD = 90

// バックエンド（MockChatService.java）の同名キーワード定数と値を一致させること
// （言語をまたぐため自動同期はできない）
export const CAUSE_ANALYSIS_KEYWORD = '使用率が高い原因を教えて'
export const CLOSE_ALERT_LABEL = '閉じる'

export function buildAlertReplyText(alert: MonitoringAlert): string {
  const value = Math.round(alert.value)
  if (alert.severity === 'danger') {
    return `${alert.nodeLabel} の${alert.metricLabel}使用率が${value}%（危険水準）に達しました。原因を確認しますか？`
  }
  return `${alert.nodeLabel} の${alert.metricLabel}使用率が${value}%（警告水準）です。念のため原因を確認しますか？`
}

export function buildCauseAnalysisChoiceLabel(alert: MonitoringAlert): string {
  return `${alert.nodeLabel}の${alert.metricLabel}${CAUSE_ANALYSIS_KEYWORD}`
}
