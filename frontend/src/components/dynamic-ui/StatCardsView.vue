<script setup lang="ts">
import { computed } from 'vue'
import type { StatCardsComponent } from '../../types/chat'

const props = defineProps<{
  spec: StatCardsComponent
}>()

function deltaTrend(delta: string | undefined | null): 'up' | 'down' | 'flat' | null {
  if (delta === undefined || delta === null) {
    return null
  }
  if (delta.startsWith('+')) {
    return 'up'
  }
  if (delta.startsWith('-')) {
    return 'down'
  }
  return 'flat'
}

const cards = computed(() =>
  props.spec.cards.map((card) => ({
    ...card,
    trend: deltaTrend(card.delta),
  })),
)
</script>

<template>
  <div class="grid grid-cols-2 gap-2 sm:grid-cols-4">
    <div
      v-for="(card, cardIndex) in cards"
      :key="cardIndex"
      class="flex flex-col gap-1 overflow-hidden rounded-lg border border-zinc-200 bg-white px-3 py-2 dark:border-zinc-700 dark:bg-zinc-900"
    >
      <span class="truncate text-xs text-zinc-500 dark:text-zinc-400">
        {{ card.label }}
      </span>
      <span class="truncate text-base font-semibold text-zinc-900 dark:text-zinc-100">
        {{ card.value }}
      </span>
      <span
        v-if="card.trend"
        class="truncate text-xs"
        :class="{
          'text-emerald-600 dark:text-emerald-400': card.trend === 'up',
          'text-rose-600 dark:text-rose-400': card.trend === 'down',
          'text-zinc-500 dark:text-zinc-400': card.trend === 'flat',
        }"
      >
        <template v-if="card.trend === 'up'">▲ </template>
        <template v-else-if="card.trend === 'down'">▼ </template>
        {{ card.delta }}
      </span>
    </div>
  </div>
</template>
