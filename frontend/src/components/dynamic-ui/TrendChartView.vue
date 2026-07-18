<script setup lang="ts">
import { computed } from 'vue'
import type { TrendChartComponent } from '../../types/chat'

const props = defineProps<{
  spec: TrendChartComponent
}>()

const scaleMax = computed(() => Math.max(0, props.spec.average, ...props.spec.values))

const bars = computed(() =>
  props.spec.labels.map((label, index) => {
    const value = props.spec.values[index] ?? 0
    const heightPercent = scaleMax.value > 0 ? (value / scaleMax.value) * 100 : 0
    return { label, value, heightPercent }
  }),
)

const averagePercent = computed(() =>
  scaleMax.value > 0 ? (props.spec.average / scaleMax.value) * 100 : 0,
)
</script>

<template>
  <div class="flex max-w-full flex-col gap-1">
    <p class="mb-1 text-sm font-semibold text-zinc-900 dark:text-zinc-100">
      {{ spec.title }}
    </p>
    <div class="relative h-40">
      <div
        class="absolute right-0 left-0 border-t-2 border-dashed border-rose-500"
        :style="{ bottom: averagePercent + '%' }"
      >
        <span
          class="absolute right-0 bottom-0.5 rounded bg-white/80 px-1 text-[11px] text-rose-500 dark:bg-zinc-900/80"
        >
          平均 {{ spec.average }}
        </span>
      </div>
      <div class="flex h-full items-end gap-1">
        <div
          v-for="(bar, barIndex) in bars"
          :key="barIndex"
          class="flex h-full min-w-3 flex-1 flex-col items-center justify-end"
        >
          <span
            class="mb-0.5 text-[10px] tabular-nums text-zinc-500 dark:text-zinc-400"
          >
            {{ bar.value }}
          </span>
          <div
            class="w-3/5 min-h-px rounded-t bg-violet-500"
            :style="{ height: bar.heightPercent + '%' }"
          ></div>
        </div>
      </div>
    </div>
    <div class="flex gap-1">
      <span
        v-for="(bar, barIndex) in bars"
        :key="barIndex"
        class="min-w-3 flex-1 text-center text-[10px] text-zinc-500 dark:text-zinc-400"
      >
        {{ bar.label }}
      </span>
    </div>
  </div>
</template>
