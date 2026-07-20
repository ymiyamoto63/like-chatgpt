<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import type { MonitoringHistorySample } from '../../types/monitoring'

const props = defineProps<{
  node: { id: string; label: string; cpuPercent: number; memoryPercent: number }
  history: MonitoringHistorySample[]
}>()

const emit = defineEmits<{ (e: 'close'): void }>()

const METRIC_STROKE_CLASS = {
  cpuPercent: 'stroke-sky-500 dark:stroke-sky-400',
  memoryPercent: 'stroke-violet-500 dark:stroke-violet-400',
} as const

const VIEWBOX_WIDTH = 480
const VIEWBOX_HEIGHT = 220
const PLOT_MARGIN_LEFT = 32
const PLOT_MARGIN_RIGHT = 8
const PLOT_MARGIN_TOP = 12
const PLOT_MARGIN_BOTTOM = 28
const PLOT_WIDTH = VIEWBOX_WIDTH - PLOT_MARGIN_LEFT - PLOT_MARGIN_RIGHT
const PLOT_HEIGHT = VIEWBOX_HEIGHT - PLOT_MARGIN_TOP - PLOT_MARGIN_BOTTOM

function formatTime(timestamp: number): string {
  return new Date(timestamp).toLocaleTimeString('ja-JP', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  })
}

function valueToY(value: number): number {
  const clamped = Math.min(100, Math.max(0, value))
  return PLOT_HEIGHT - (clamped / 100) * PLOT_HEIGHT
}

function chartPoints(metric: 'cpuPercent' | 'memoryPercent'): string {
  const samples = props.history
  if (samples.length === 0) {
    return ''
  }
  if (samples.length === 1) {
    const y = valueToY(samples[0][metric])
    return `0,${y} ${PLOT_WIDTH},${y}`
  }
  return samples
    .map((sample, index) => {
      const x = (index / (samples.length - 1)) * PLOT_WIDTH
      const y = valueToY(sample[metric])
      return `${x},${y}`
    })
    .join(' ')
}

const hasHistory = computed(() => props.history.length > 0)
const cpuPoints = computed(() => chartPoints('cpuPercent'))
const memoryPoints = computed(() => chartPoints('memoryPercent'))
const oldestTimeLabel = computed(() =>
  hasHistory.value ? formatTime(props.history[0].timestamp) : '',
)
const newestTimeLabel = computed(() =>
  hasHistory.value ? formatTime(props.history[props.history.length - 1].timestamp) : '',
)

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    emit('close')
  }
}

onMounted(() => {
  window.addEventListener('keydown', onKeydown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <div
    class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
    @click.self="emit('close')"
  >
    <div class="w-full max-w-lg rounded-lg bg-white p-5 shadow-xl dark:bg-zinc-900">
      <div class="mb-3 flex items-center justify-between">
        <h3 class="text-sm font-semibold text-zinc-900 dark:text-zinc-100">{{ node.label }}</h3>
        <button
          type="button"
          class="text-zinc-500 hover:text-zinc-800 dark:hover:text-zinc-200"
          @click="emit('close')"
        >
          ✕
        </button>
      </div>

      <p
        v-if="!hasHistory"
        class="py-8 text-center text-xs text-zinc-500 dark:text-zinc-400"
      >
        データがありません
      </p>

      <template v-else>
        <svg
          :viewBox="`0 0 ${VIEWBOX_WIDTH} ${VIEWBOX_HEIGHT}`"
          width="100%"
          class="text-zinc-500 dark:text-zinc-400"
        >
          <g :transform="`translate(${PLOT_MARGIN_LEFT}, ${PLOT_MARGIN_TOP})`">
            <line
              x1="0"
              :y1="valueToY(0)"
              :x2="PLOT_WIDTH"
              :y2="valueToY(0)"
              class="stroke-zinc-200 dark:stroke-zinc-700"
              stroke-width="1"
            />
            <line
              x1="0"
              :y1="valueToY(50)"
              :x2="PLOT_WIDTH"
              :y2="valueToY(50)"
              class="stroke-zinc-200 dark:stroke-zinc-700"
              stroke-width="1"
            />
            <line
              x1="0"
              :y1="valueToY(100)"
              :x2="PLOT_WIDTH"
              :y2="valueToY(100)"
              class="stroke-zinc-200 dark:stroke-zinc-700"
              stroke-width="1"
            />

            <text
              :x="-6"
              :y="valueToY(0) + 3"
              text-anchor="end"
              class="text-[10px] fill-zinc-500 dark:fill-zinc-400"
            >
              0%
            </text>
            <text
              :x="-6"
              :y="valueToY(50) + 3"
              text-anchor="end"
              class="text-[10px] fill-zinc-500 dark:fill-zinc-400"
            >
              50%
            </text>
            <text
              :x="-6"
              :y="valueToY(100) + 3"
              text-anchor="end"
              class="text-[10px] fill-zinc-500 dark:fill-zinc-400"
            >
              100%
            </text>

            <polyline
              :points="cpuPoints"
              fill="none"
              stroke-width="1.5"
              stroke-linecap="round"
              stroke-linejoin="round"
              :class="METRIC_STROKE_CLASS.cpuPercent"
            />
            <polyline
              :points="memoryPoints"
              fill="none"
              stroke-width="1.5"
              stroke-linecap="round"
              stroke-linejoin="round"
              :class="METRIC_STROKE_CLASS.memoryPercent"
            />

            <text
              x="0"
              :y="PLOT_HEIGHT + 18"
              text-anchor="start"
              class="text-[10px] tabular-nums fill-zinc-500 dark:fill-zinc-400"
            >
              {{ oldestTimeLabel }}
            </text>
            <text
              :x="PLOT_WIDTH"
              :y="PLOT_HEIGHT + 18"
              text-anchor="end"
              class="text-[10px] tabular-nums fill-zinc-500 dark:fill-zinc-400"
            >
              {{ newestTimeLabel }}
            </text>
          </g>
        </svg>

        <div class="mt-2 flex items-center gap-4 text-xs text-zinc-600 dark:text-zinc-300">
          <span class="flex items-center gap-1">
            <span class="inline-block h-2 w-2 rounded-full bg-sky-500 dark:bg-sky-400"></span>
            CPU
          </span>
          <span class="flex items-center gap-1">
            <span class="inline-block h-2 w-2 rounded-full bg-violet-500 dark:bg-violet-400"></span>
            メモリ
          </span>
        </div>
      </template>
    </div>
  </div>
</template>
