<script setup lang="ts">
import type {
  BarChartComponent,
  ChoicesComponent,
  Message,
  TableComponent,
  TrendChartComponent,
  UiComponentSpec,
} from '../types/chat'
import TableView from './TableView.vue'
import BarChartView from './BarChartView.vue'
import ChoicesView from './ChoicesView.vue'
import TrendChartView from './TrendChartView.vue'

defineProps<{
  message: Message
  choicesEnabled?: boolean
}>()

const emit = defineEmits<{
  select: [option: string]
}>()

function isTableComponent(component: UiComponentSpec): component is TableComponent {
  return component.type === 'table'
}

function isBarChartComponent(component: UiComponentSpec): component is BarChartComponent {
  return component.type === 'bar_chart'
}

function isChoicesComponent(component: UiComponentSpec): component is ChoicesComponent {
  return component.type === 'choices'
}

function isTrendChartComponent(component: UiComponentSpec): component is TrendChartComponent {
  return component.type === 'trend_chart'
}
</script>

<template>
  <div
    class="flex w-full"
    :class="message.role === 'user' ? 'justify-end' : 'justify-start'"
  >
    <div
      class="max-w-[75%] rounded-2xl px-4 py-2.5 text-left text-[15px] leading-relaxed break-words whitespace-pre-wrap"
      :class="
        message.role === 'user'
          ? 'rounded-br-md bg-violet-600 text-white'
          : 'rounded-bl-md bg-zinc-100 text-zinc-900 dark:bg-zinc-800 dark:text-zinc-100'
      "
    >
      {{ message.content }}
      <div
        v-for="(component, index) in message.components"
        :key="index"
        class="mt-3 whitespace-normal"
      >
        <TableView v-if="isTableComponent(component)" :spec="component" />
        <BarChartView v-else-if="isBarChartComponent(component)" :spec="component" />
        <TrendChartView v-else-if="isTrendChartComponent(component)" :spec="component" />
        <ChoicesView
          v-else-if="isChoicesComponent(component)"
          :spec="component"
          :disabled="!choicesEnabled"
          @select="emit('select', $event)"
        />
      </div>
    </div>
  </div>
</template>
