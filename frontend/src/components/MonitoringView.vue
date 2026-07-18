<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import { useMonitoringStore } from '../stores/monitoringStore'
import TopologyDiagram from './TopologyDiagram.vue'

const store = useMonitoringStore()

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
