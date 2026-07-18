<script setup lang="ts">
import { computed } from 'vue'
import type { MonitoringSnapshot } from '../types/monitoring'
import { getMonitoringLevel, type MonitoringLevel } from '../constants/monitoring'
import {
  NODE_WIDTH,
  NODE_HEIGHT,
  VIEWBOX_WIDTH,
  VIEWBOX_HEIGHT,
  NODE_POSITIONS,
} from '../constants/monitoringLayout'

const props = defineProps<{
  snapshot: MonitoringSnapshot
}>()

const LEVEL_STROKE_CLASS: Record<MonitoringLevel, string> = {
  normal: 'stroke-emerald-500 dark:stroke-emerald-400',
  warning: 'stroke-amber-500 dark:stroke-amber-400',
  danger: 'stroke-rose-500 dark:stroke-rose-400',
}

const LEVEL_FILL_CLASS: Record<MonitoringLevel, string> = {
  normal: 'fill-emerald-500 dark:fill-emerald-400',
  warning: 'fill-amber-500 dark:fill-amber-400',
  danger: 'fill-rose-500 dark:fill-rose-400',
}

const LEVEL_TEXT_CLASS: Record<MonitoringLevel, string> = {
  normal: 'fill-emerald-600 dark:fill-emerald-400',
  warning: 'fill-amber-600 dark:fill-amber-400',
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
    return [
      {
        id: node.id,
        label: node.label,
        cpuPercent: node.cpuPercent,
        memoryPercent: node.memoryPercent,
        cpuLevel: getMonitoringLevel(node.cpuPercent),
        memoryLevel: getMonitoringLevel(node.memoryPercent),
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

function gaugeFillClass(level: MonitoringLevel): string {
  return LEVEL_FILL_CLASS[level]
}

function gaugeWidth(percent: number): number {
  return (Math.min(100, Math.max(0, percent)) / 100) * GAUGE_WIDTH
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
      <g v-for="node in nodes" :key="node.id">
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
        <rect
          :x="node.x + GAUGE_MARGIN"
          :y="node.y + 38"
          :width="gaugeWidth(node.cpuPercent)"
          :height="GAUGE_HEIGHT"
          rx="3"
          :class="gaugeFillClass(node.cpuLevel)"
        />
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
        <rect
          :x="node.x + GAUGE_MARGIN"
          :y="node.y + 60"
          :width="gaugeWidth(node.memoryPercent)"
          :height="GAUGE_HEIGHT"
          rx="3"
          :class="gaugeFillClass(node.memoryLevel)"
        />
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
