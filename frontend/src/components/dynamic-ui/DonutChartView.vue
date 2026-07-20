<script setup lang="ts">
import { computed } from 'vue'
import type { DonutChartComponent } from '../../types/chat'

const props = defineProps<{
  spec: DonutChartComponent
}>()

const RADIUS = 40
const STROKE_WIDTH = 16
const CIRCUMFERENCE = 2 * Math.PI * RADIUS

// パレットが labels 数（6色）を超える場合は index % length で循環使用する
// （設計書「Risks / edge cases」節の「ドーナツチャートの色パレット枯渇」を参照）
const PALETTE = [
  { stroke: 'stroke-violet-500 dark:stroke-violet-400', swatch: 'bg-violet-500 dark:bg-violet-400' },
  { stroke: 'stroke-emerald-500 dark:stroke-emerald-400', swatch: 'bg-emerald-500 dark:bg-emerald-400' },
  { stroke: 'stroke-orange-500 dark:stroke-orange-400', swatch: 'bg-orange-500 dark:bg-orange-400' },
  { stroke: 'stroke-sky-500 dark:stroke-sky-400', swatch: 'bg-sky-500 dark:bg-sky-400' },
  { stroke: 'stroke-rose-500 dark:stroke-rose-400', swatch: 'bg-rose-500 dark:bg-rose-400' },
  { stroke: 'stroke-amber-500 dark:stroke-amber-400', swatch: 'bg-amber-500 dark:bg-amber-400' },
]

const total = computed(() => props.spec.values.reduce((sum, value) => sum + value, 0))

const segments = computed(() => {
  let cumulativeLength = 0
  return props.spec.labels.map((label, index) => {
    const value = props.spec.values[index] ?? 0
    const ratio = total.value > 0 ? value / total.value : 0
    const segmentLength = ratio * CIRCUMFERENCE
    const segment = {
      label,
      value,
      ratio,
      dasharray: `${segmentLength} ${CIRCUMFERENCE - segmentLength}`,
      dashoffset: -cumulativeLength,
      color: PALETTE[index % PALETTE.length],
    }
    cumulativeLength += segmentLength
    return segment
  })
})
</script>

<template>
  <div class="flex max-w-full flex-col gap-3 sm:flex-row sm:items-center">
    <p class="text-sm font-semibold text-zinc-900 dark:text-zinc-100 sm:hidden">
      {{ spec.title }}
    </p>
    <svg viewBox="0 0 100 100" class="h-32 w-32 shrink-0">
      <g transform="rotate(-90 50 50)">
        <circle
          cx="50"
          cy="50"
          :r="RADIUS"
          fill="none"
          :stroke-width="STROKE_WIDTH"
          class="stroke-zinc-200 dark:stroke-zinc-700"
        />
        <circle
          v-for="(segment, segmentIndex) in segments"
          :key="segmentIndex"
          cx="50"
          cy="50"
          :r="RADIUS"
          fill="none"
          :stroke-width="STROKE_WIDTH"
          :stroke-dasharray="segment.dasharray"
          :stroke-dashoffset="segment.dashoffset"
          :class="segment.color.stroke"
        />
      </g>
    </svg>
    <div class="flex min-w-0 flex-1 flex-col gap-1">
      <p class="hidden text-sm font-semibold text-zinc-900 dark:text-zinc-100 sm:block">
        {{ spec.title }}
      </p>
      <div
        v-for="(segment, segmentIndex) in segments"
        :key="segmentIndex"
        class="flex items-center gap-1.5 text-xs text-zinc-700 dark:text-zinc-300"
      >
        <span class="h-2 w-2 shrink-0 rounded-full" :class="segment.color.swatch"></span>
        <span class="min-w-0 flex-1 truncate">{{ segment.label }}</span>
        <span class="shrink-0 tabular-nums text-zinc-500 dark:text-zinc-400">
          {{ Math.round(segment.ratio * 100) }}%
        </span>
      </div>
    </div>
  </div>
</template>
