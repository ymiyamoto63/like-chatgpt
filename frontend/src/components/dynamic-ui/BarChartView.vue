<script setup lang="ts">
import { computed } from 'vue'
import type { BarChartComponent } from '../../types/chat'

const props = defineProps<{
  spec: BarChartComponent
}>()

const maxValue = computed(() => Math.max(0, ...props.spec.values))

const bars = computed(() =>
  props.spec.labels.map((label, index) => {
    const value = props.spec.values[index] ?? 0
    const widthPercent = maxValue.value > 0 ? (value / maxValue.value) * 100 : 0
    return { label, value, widthPercent }
  }),
)
</script>

<template>
  <div class="flex max-w-full flex-col gap-2">
    <p class="text-sm font-semibold text-zinc-900 dark:text-zinc-100">
      {{ spec.title }}
    </p>
    <div class="flex flex-col gap-1.5">
      <div
        v-for="(bar, barIndex) in bars"
        :key="barIndex"
        class="flex items-center gap-2"
      >
        <span
          class="min-w-20 shrink-0 text-xs text-zinc-700 dark:text-zinc-300"
        >
          {{ bar.label }}
        </span>
        <div
          class="h-2.5 min-w-16 flex-1 overflow-hidden rounded-full bg-zinc-200 dark:bg-zinc-700"
        >
          <div
            class="h-full rounded-full bg-violet-500"
            :style="{ width: bar.widthPercent + '%' }"
          ></div>
        </div>
        <span
          class="min-w-6 shrink-0 text-right text-xs tabular-nums text-zinc-700 dark:text-zinc-300"
        >
          {{ bar.value }}
        </span>
      </div>
    </div>
  </div>
</template>
