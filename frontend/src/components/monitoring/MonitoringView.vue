<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import { useMonitoringStore } from '../stores/monitoringStore'
import { useConversationStore } from '../stores/conversationStore'
import {
  buildAlertReplyText,
  buildCauseAnalysisChoiceLabel,
  CLOSE_ALERT_LABEL,
  type AlertSeverity,
  type MonitoringAlert,
} from '../constants/monitoringAlert'
import TopologyDiagram from './TopologyDiagram.vue'

const store = useMonitoringStore()
const conversationStore = useConversationStore()

const lastUpdatedLabel = computed(() => {
  if (store.lastUpdatedAt === null) {
    return '--:--:--'
  }
  return new Date(store.lastUpdatedAt).toLocaleTimeString('ja-JP', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  })
})

const ALERT_BANNER_SEVERITY_CLASS: Record<AlertSeverity, string> = {
  warning:
    'border-orange-300 bg-orange-50 text-orange-700 hover:bg-orange-100 dark:border-orange-500/40 dark:bg-orange-500/10 dark:text-orange-300 dark:hover:bg-orange-500/20',
  danger:
    'border-rose-300 bg-rose-50 text-rose-700 hover:bg-rose-100 dark:border-rose-500/40 dark:bg-rose-500/10 dark:text-rose-300 dark:hover:bg-rose-500/20',
}

function handleAlertBannerClick(alert: MonitoringAlert) {
  conversationStore.createAlertConversation(buildAlertReplyText(alert), [
    buildCauseAnalysisChoiceLabel(alert),
    CLOSE_ALERT_LABEL,
  ])
  store.consumePendingAlert(alert)
  store.showChat()
}

onMounted(() => {
  store.startPolling()
})

onUnmounted(() => {
  store.stopPolling()
})
</script>

<template>
  <div class="flex h-full flex-col gap-3 overflow-y-auto p-5">
    <div class="flex shrink-0 items-baseline justify-between">
      <h2 class="text-sm font-semibold text-zinc-900 dark:text-zinc-100">
        システムモニタリング
      </h2>
      <span class="text-xs tabular-nums text-zinc-500 dark:text-zinc-400">
        最終更新: {{ lastUpdatedLabel }}
      </span>
    </div>

    <button
      v-for="alert in store.pendingAlerts"
      :key="alert.targetId"
      type="button"
      class="shrink-0 rounded-lg border px-3 py-2 text-left text-xs transition-colors"
      :class="ALERT_BANNER_SEVERITY_CLASS[alert.severity]"
      @click="handleAlertBannerClick(alert)"
    >
      <span class="font-semibold">⚠ {{ buildAlertReplyText(alert) }}</span>
      <span class="ml-1 underline">クリックして原因を確認</span>
    </button>

    <div
      v-if="store.hasError && store.snapshot"
      class="shrink-0 rounded-lg border border-rose-300 bg-rose-50 px-3 py-2 text-xs text-rose-700 dark:border-rose-500/40 dark:bg-rose-500/10 dark:text-rose-300"
    >
      接続エラー: 最新データを取得できませんでした。直前のデータを表示しています。
    </div>

    <div
      v-if="store.hasError && !store.snapshot"
      class="flex flex-1 items-center justify-center rounded-lg border border-rose-300 bg-rose-50 px-3 py-8 text-sm text-rose-700 dark:border-rose-500/40 dark:bg-rose-500/10 dark:text-rose-300"
    >
      データを取得できませんでした
    </div>

    <TopologyDiagram v-if="store.snapshot" :snapshot="store.snapshot" />
  </div>
</template>
