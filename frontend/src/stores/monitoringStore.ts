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
    // ノードごとに直近で通知した重大度（warning/danger）を記録し、同一水準への
    // 再通知を抑制する。danger→warningへ下がった場合は通知済みのまま「warning」に
    // 格下げして記録し、warningの再通知はしないが90%への再上昇時はdangerとして再通知する
    alertedTierByNodeId: new Map<string, AlertSeverity>(),
    pendingAlert: null as MonitoringAlert | null,
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
    // ノードごとにCPU・メモリ使用率をwarning(80%)/danger(90%)の2段階のしきい値と比較し、
    // 通知待ちアラート（最大1件）を管理する。80%未満に戻ったノードは完全にクールダウン解除、
    // 90%未満（80%以上）に戻ったノードはdangerの再通知のみ解除（warningの再通知はしない）。
    // 既に通知待ちがある間は新規検知しない（＝1回のスナップショットで複数ノードが同時に
    // しきい値超過していても最初の1件のみ通知する）
    updateAlertState(snapshot: MonitoringSnapshot) {
      for (const node of snapshot.nodes) {
        const worst = Math.max(node.cpuPercent, node.memoryPercent)
        const currentTier: AlertSeverity | null =
          worst >= ALERT_DANGER_THRESHOLD
            ? 'danger'
            : worst >= ALERT_WARNING_THRESHOLD
              ? 'warning'
              : null
        const previousTier = this.alertedTierByNodeId.get(node.id) ?? null

        if (currentTier === null) {
          this.alertedTierByNodeId.delete(node.id)
          continue
        }
        if (currentTier === 'warning' && previousTier === 'danger') {
          // dangerから80%台まで降格：通知済み扱いはwarningとして引き継ぎ、再通知はしない
          this.alertedTierByNodeId.set(node.id, 'warning')
          continue
        }
        if (currentTier === previousTier) {
          continue
        }
        // ここに来るのは (previousTier=null → warning/danger) または (warning → danger) のみ。
        // 通知待ちが埋まっている間はtierを更新せず保留し、次回以降のポーリングで再判定させる
        if (this.pendingAlert !== null) {
          continue
        }

        this.alertedTierByNodeId.set(node.id, currentTier)
        const metric: 'cpu' | 'memory' =
          node.memoryPercent > node.cpuPercent ? 'memory' : 'cpu'
        this.pendingAlert = {
          nodeId: node.id,
          nodeLabel: node.label,
          metric,
          metricLabel: metric === 'cpu' ? 'CPU' : 'メモリ',
          value: metric === 'cpu' ? node.cpuPercent : node.memoryPercent,
          severity: currentTier,
        }
      }
    },
    consumePendingAlert() {
      this.pendingAlert = null
    },
  },
})
