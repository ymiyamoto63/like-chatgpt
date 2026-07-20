<script setup lang="ts">
import { computed } from 'vue'
import type { MonitoringHistorySample, MonitoringSnapshot } from '../../types/monitoring'
import { getMonitoringLevel, type MonitoringLevel } from '../../constants/monitoring'
import {
  NODE_WIDTH,
  NODE_HEIGHT,
  VIEWBOX_WIDTH,
  VIEWBOX_HEIGHT,
  NODE_POSITIONS,
} from '../../constants/monitoringLayout'

const props = defineProps<{
  snapshot: MonitoringSnapshot
  historyByNodeId: Map<string, MonitoringHistorySample[]>
}>()

const emit = defineEmits<{ (e: 'node-click', nodeId: string): void }>()

const LEVEL_STROKE_CLASS: Record<MonitoringLevel, string> = {
  normal: 'stroke-emerald-500 dark:stroke-emerald-400',
  warning: 'stroke-orange-500 dark:stroke-orange-400',
  danger: 'stroke-rose-500 dark:stroke-rose-400',
}

const LEVEL_TEXT_CLASS: Record<MonitoringLevel, string> = {
  normal: 'fill-emerald-600 dark:fill-emerald-400',
  warning: 'fill-orange-600 dark:fill-orange-400',
  danger: 'fill-rose-600 dark:fill-rose-400',
}

const GAUGE_MARGIN = 10
const GAUGE_HEIGHT = 6
const GAUGE_WIDTH = NODE_WIDTH - GAUGE_MARGIN * 2

const nodes = computed(() =>
  props.snapshot.nodes.flatMap((node) => {
    const position = NODE_POSITIONS[node.id]
    if (!position) {
      return []
    }
    const worstLevel = getMonitoringLevel(Math.max(node.cpuPercent, node.memoryPercent))
    const history = props.historyByNodeId.get(node.id)
    return [
      {
        id: node.id,
        label: node.label,
        cpuPercent: node.cpuPercent,
        memoryPercent: node.memoryPercent,
        cpuLevel: getMonitoringLevel(node.cpuPercent),
        memoryLevel: getMonitoringLevel(node.memoryPercent),
        cpuSparklinePoints: sparklinePoints(history, 'cpuPercent'),
        memorySparklinePoints: sparklinePoints(history, 'memoryPercent'),
        x: position.x,
        y: position.y,
        centerX: position.x + NODE_WIDTH / 2,
        centerY: position.y + NODE_HEIGHT / 2,
        strokeClass: LEVEL_STROKE_CLASS[worstLevel],
      },
    ]
  }),
)

const nodeCenterById = computed(() => {
  const map = new Map<string, { x: number; y: number }>()
  for (const node of nodes.value) {
    map.set(node.id, { x: node.centerX, y: node.centerY })
  }
  return map
})

const edges = computed(() =>
  props.snapshot.edges.flatMap((edge) => {
    const source = nodeCenterById.value.get(edge.sourceId)
    const target = nodeCenterById.value.get(edge.targetId)
    if (!source || !target) {
      return []
    }
    const level = getMonitoringLevel(edge.bandwidthPercent)
    return [
      {
        id: edge.id,
        bandwidthPercent: edge.bandwidthPercent,
        x1: source.x,
        y1: source.y,
        x2: target.x,
        y2: target.y,
        midX: (source.x + target.x) / 2,
        midY: (source.y + target.y) / 2,
        strokeClass: LEVEL_STROKE_CLASS[level],
        textClass: LEVEL_TEXT_CLASS[level],
      },
    ]
  }),
)

function valueToY(value: number): number {
  const clamped = Math.min(100, Math.max(0, value))
  return GAUGE_HEIGHT - (clamped / 100) * GAUGE_HEIGHT
}

function sparklinePoints(
  history: MonitoringHistorySample[] | undefined,
  metric: 'cpuPercent' | 'memoryPercent',
): string {
  const samples = history ?? []
  if (samples.length === 0) {
    return ''
  }
  if (samples.length === 1) {
    const y = valueToY(samples[0][metric])
    return `0,${y} ${GAUGE_WIDTH},${y}`
  }
  return samples
    .map((sample, index) => {
      const x = (index / (samples.length - 1)) * GAUGE_WIDTH
      const y = valueToY(sample[metric])
      return `${x},${y}`
    })
    .join(' ')
}
</script>

<template>
  <svg
    :viewBox="`0 0 ${VIEWBOX_WIDTH} ${VIEWBOX_HEIGHT}`"
    width="100%"
    class="text-zinc-500 dark:text-zinc-400"
  >
    <g>
      <g v-for="edge in edges" :key="edge.id">
        <line
          :x1="edge.x1"
          :y1="edge.y1"
          :x2="edge.x2"
          :y2="edge.y2"
          stroke-width="2"
          :class="edge.strokeClass"
        />
        <rect
          :x="edge.midX - 18"
          :y="edge.midY - 9"
          width="36"
          height="16"
          rx="3"
          class="fill-white dark:fill-zinc-900"
        />
        <text
          :x="edge.midX"
          :y="edge.midY + 3"
          text-anchor="middle"
          class="text-[10px] tabular-nums"
          :class="edge.textClass"
        >
          {{ Math.round(edge.bandwidthPercent) }}%
        </text>
      </g>
    </g>
    <g>
      <g v-for="node in nodes" :key="node.id" class="cursor-pointer" @click="emit('node-click', node.id)">
        <rect
          :x="node.x"
          :y="node.y"
          :width="NODE_WIDTH"
          :height="NODE_HEIGHT"
          rx="8"
          stroke-width="2"
          class="fill-white dark:fill-zinc-900"
          :class="node.strokeClass"
        />
        <text
          :x="node.x + NODE_WIDTH / 2"
          :y="node.y + 18"
          text-anchor="middle"
          class="text-xs font-semibold fill-zinc-900 dark:fill-zinc-100"
        >
          {{ node.label }}
        </text>

        <text :x="node.x + GAUGE_MARGIN" :y="node.y + 34" class="text-[10px] fill-zinc-500 dark:fill-zinc-400">
          CPU
        </text>
        <rect
          :x="node.x + GAUGE_MARGIN"
          :y="node.y + 38"
          :width="GAUGE_WIDTH"
          :height="GAUGE_HEIGHT"
          rx="3"
          class="fill-zinc-200 dark:fill-zinc-700"
        />
        <g :transform="`translate(${node.x + GAUGE_MARGIN}, ${node.y + 38})`">
          <polyline
            :points="node.cpuSparklinePoints"
            fill="none"
            stroke-width="1.5"
            stroke-linecap="round"
            stroke-linejoin="round"
            :class="LEVEL_STROKE_CLASS[node.cpuLevel]"
          />
        </g>
        <text
          :x="node.x + NODE_WIDTH - GAUGE_MARGIN"
          :y="node.y + 34"
          text-anchor="end"
          class="text-[10px] tabular-nums fill-zinc-700 dark:fill-zinc-300"
        >
          {{ Math.round(node.cpuPercent) }}%
        </text>

        <text :x="node.x + GAUGE_MARGIN" :y="node.y + 56" class="text-[10px] fill-zinc-500 dark:fill-zinc-400">
          MEM
        </text>
        <rect
          :x="node.x + GAUGE_MARGIN"
          :y="node.y + 60"
          :width="GAUGE_WIDTH"
          :height="GAUGE_HEIGHT"
          rx="3"
          class="fill-zinc-200 dark:fill-zinc-700"
        />
        <g :transform="`translate(${node.x + GAUGE_MARGIN}, ${node.y + 60})`">
          <polyline
            :points="node.memorySparklinePoints"
            fill="none"
            stroke-width="1.5"
            stroke-linecap="round"
            stroke-linejoin="round"
            :class="LEVEL_STROKE_CLASS[node.memoryLevel]"
          />
        </g>
        <text
          :x="node.x + NODE_WIDTH - GAUGE_MARGIN"
          :y="node.y + 56"
          text-anchor="end"
          class="text-[10px] tabular-nums fill-zinc-700 dark:fill-zinc-300"
        >
          {{ Math.round(node.memoryPercent) }}%
        </text>
      </g>
    </g>
  </svg>
</template>
