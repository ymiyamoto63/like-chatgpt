export type AlertSeverity = 'warning' | 'danger'

export type AlertMetric = 'cpu' | 'memory' | 'traffic'

export type AlertMetricLabel = 'CPU' | 'メモリ' | 'トラフィック'

export interface MonitoringAlert {
  // ノードのidまたはエッジのid（両者は衝突しない前提）
  targetId: string
  // ノードのlabel、またはエッジの場合は「Web-1〜App-1間」のような区間表記
  targetLabel: string
  metric: AlertMetric
  metricLabel: AlertMetricLabel
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
    return `${alert.targetLabel} の${alert.metricLabel}使用率が${value}%（危険水準）に達しました。原因を確認しますか？`
  }
  return `${alert.targetLabel} の${alert.metricLabel}使用率が${value}%（警告水準）です。念のため原因を確認しますか？`
}

export function buildCauseAnalysisChoiceLabel(alert: MonitoringAlert): string {
  return `${alert.targetLabel}の${alert.metricLabel}${CAUSE_ANALYSIS_KEYWORD}`
}
