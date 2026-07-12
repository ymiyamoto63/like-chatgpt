<script setup lang="ts">
import { computed } from 'vue'
import type { BarChartComponent } from '../types/chat'

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
  <div class="bar-chart-view">
    <p class="chart-title">{{ spec.title }}</p>
    <div class="chart-rows">
      <div v-for="(bar, barIndex) in bars" :key="barIndex" class="chart-row">
        <span class="chart-label">{{ bar.label }}</span>
        <div class="chart-track">
          <div class="chart-bar" :style="{ width: bar.widthPercent + '%' }"></div>
        </div>
        <span class="chart-value">{{ bar.value }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.bar-chart-view {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-width: 100%;
}

.chart-title {
  font-weight: 600;
  color: var(--text-h);
}

.chart-rows {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.chart-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.chart-label {
  flex: 0 0 auto;
  min-width: 80px;
  color: var(--text-h);
  font-size: 0.9em;
}

.chart-track {
  flex: 1 1 auto;
  min-width: 60px;
  height: 12px;
  border-radius: 6px;
  background: var(--code-bg);
  overflow: hidden;
}

.chart-bar {
  height: 100%;
  background: var(--accent);
  border-radius: 6px;
}

.chart-value {
  flex: 0 0 auto;
  min-width: 24px;
  text-align: right;
  color: var(--text-h);
  font-size: 0.9em;
}
</style>
