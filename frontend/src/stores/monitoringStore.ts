import { defineStore } from 'pinia'
import { POLL_INTERVAL_MS } from '../constants/monitoring'
import {
  ALERT_DANGER_THRESHOLD,
  ALERT_WARNING_THRESHOLD,
  type AlertSeverity,
  type MonitoringAlert,
} from '../constants/monitoringAlert'
import { getMonitoringSnapshot } from '../api/monitoringApi'
import type { MonitoringSnapshot } from '../types/monitoring'

export const useMonitoringStore = defineStore('monitoring', {
  state: () => ({
    activeScreen: 'chat' as 'chat' | 'monitoring',
    snapshot: null as MonitoringSnapshot | null,
    lastUpdatedAt: null as number | null,
    hasError: false,
    pollTimerId: null as number | null,
    fetchGeneration: 0,
    // 対象（ノードまたはエッジ）ごとに直近で通知した重大度（warning/danger）を記録し、
    // 同一水準への再通知を抑制する。danger→warningへ下がった場合は通知済みのまま「warning」に
    // 格下げして記録し、warningの再通知はしないが90%への再上昇時はdangerとして再通知する
    alertedTierByTargetId: new Map<string, AlertSeverity>(),
    pendingAlerts: [] as MonitoringAlert[],
  }),
  actions: {
    showMonitoring() {
      this.activeScreen = 'monitoring'
    },
    showChat() {
      this.activeScreen = 'chat'
    },
    startPolling() {
      if (this.pollTimerId !== null) {
        return
      }
      this.fetchGeneration += 1
      this.fetchSnapshot()
      this.pollTimerId = window.setInterval(() => this.fetchSnapshot(), POLL_INTERVAL_MS)
    },
    stopPolling() {
      if (this.pollTimerId !== null) {
        window.clearInterval(this.pollTimerId)
        this.pollTimerId = null
      }
    },
    async fetchSnapshot() {
      const requestGeneration = this.fetchGeneration
      try {
        const snapshot = await getMonitoringSnapshot()
        if (requestGeneration !== this.fetchGeneration) {
          return
        }
        this.snapshot = snapshot
        this.lastUpdatedAt = Date.now()
        this.hasError = false
        this.updateAlertState(snapshot)
      } catch {
        if (requestGeneration !== this.fetchGeneration) {
          return
        }
        this.hasError = true
      }
    },
    // ノード（CPU・メモリ）とエッジ（トラフィック）の使用率をwarning(80%)/danger(90%)の
    // 2段階のしきい値と比較し、通知待ちアラート（複数件可）を管理する。
    // 80%未満に戻った対象は完全にクールダウン解除、90%未満（80%以上）に戻った対象は
    // dangerの再通知のみ解除（warningの再通知はしない）。
    // 同一対象のバナーが未クリックのまま残っている間にエスカレーションした場合は、
    // バナーを増やさず既存のものを新しい重大度で置き換える
    updateAlertState(snapshot: MonitoringSnapshot) {
      const nodeLabelById = new Map(snapshot.nodes.map((node) => [node.id, node.label]))
      for (const node of snapshot.nodes) {
        const metric: 'cpu' | 'memory' =
          node.memoryPercent > node.cpuPercent ? 'memory' : 'cpu'
        this.evaluateAlertTarget({
          targetId: node.id,
          targetLabel: node.label,
          metric,
          metricLabel: metric === 'cpu' ? 'CPU' : 'メモリ',
          value: Math.max(node.cpuPercent, node.memoryPercent),
        })
      }
      for (const edge of snapshot.edges) {
        const sourceLabel = nodeLabelById.get(edge.sourceId) ?? edge.sourceId
        const targetLabel = nodeLabelById.get(edge.targetId) ?? edge.targetId
        this.evaluateAlertTarget({
          targetId: edge.id,
          targetLabel: `${sourceLabel}〜${targetLabel}間`,
          metric: 'traffic',
          metricLabel: 'トラフィック',
          value: edge.bandwidthPercent,
        })
      }
    },
    evaluateAlertTarget(candidate: Omit<MonitoringAlert, 'severity'>) {
      const currentTier: AlertSeverity | null =
        candidate.value >= ALERT_DANGER_THRESHOLD
          ? 'danger'
          : candidate.value >= ALERT_WARNING_THRESHOLD
            ? 'warning'
            : null
      const previousTier = this.alertedTierByTargetId.get(candidate.targetId) ?? null

      if (currentTier === null) {
        this.alertedTierByTargetId.delete(candidate.targetId)
        return
      }
      if (currentTier === 'warning' && previousTier === 'danger') {
        // dangerから80%台まで降格：通知済み扱いはwarningとして引き継ぎ、再通知はしない
        this.alertedTierByTargetId.set(candidate.targetId, 'warning')
        return
      }
      if (currentTier === previousTier) {
        return
      }
      // ここに来るのは (previousTier=null → warning/danger) または (warning → danger) のみ
      this.alertedTierByTargetId.set(candidate.targetId, currentTier)
      const alert: MonitoringAlert = { ...candidate, severity: currentTier }
      const existingIndex = this.pendingAlerts.findIndex(
        (pending) => pending.targetId === candidate.targetId,
      )
      if (existingIndex >= 0) {
        this.pendingAlerts[existingIndex] = alert
      } else {
        this.pendingAlerts.push(alert)
      }
    },
    consumePendingAlert(alert: MonitoringAlert) {
      this.pendingAlerts = this.pendingAlerts.filter(
        (pending) => pending.targetId !== alert.targetId,
      )
    },
  },
})
