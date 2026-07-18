import { defineStore } from 'pinia'
import { POLL_INTERVAL_MS } from '../constants/monitoring'
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
      } catch {
        if (requestGeneration !== this.fetchGeneration) {
          return
        }
        this.hasError = true
      }
    },
  },
})
