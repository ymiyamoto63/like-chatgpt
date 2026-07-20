<script setup lang="ts">
import type {
  BarChartComponent,
  ChoicesComponent,
  DonutChartComponent,
  FaqListComponent,
  Message,
  StatCardsComponent,
  TableComponent,
  TrendChartComponent,
  UiComponentSpec,
} from '../../types/chat'
import TableView from '../dynamic-ui/TableView.vue'
import BarChartView from '../dynamic-ui/BarChartView.vue'
import ChoicesView from '../dynamic-ui/ChoicesView.vue'
import TrendChartView from '../dynamic-ui/TrendChartView.vue'
import FaqListView from '../dynamic-ui/FaqListView.vue'
import StatCardsView from '../dynamic-ui/StatCardsView.vue'
import DonutChartView from '../dynamic-ui/DonutChartView.vue'

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

function isFaqListComponent(component: UiComponentSpec): component is FaqListComponent {
  return component.type === 'faq_list'
}

function isStatCardsComponent(component: UiComponentSpec): component is StatCardsComponent {
  return component.type === 'stat_cards'
}

function isDonutChartComponent(component: UiComponentSpec): component is DonutChartComponent {
  return component.type === 'donut_chart'
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
        <StatCardsView v-else-if="isStatCardsComponent(component)" :spec="component" />
        <DonutChartView v-else-if="isDonutChartComponent(component)" :spec="component" />
        <ChoicesView
          v-else-if="isChoicesComponent(component)"
          :spec="component"
          :disabled="!choicesEnabled"
          @select="emit('select', $event)"
        />
        <FaqListView
          v-else-if="isFaqListComponent(component)"
          :spec="component"
          :disabled="!choicesEnabled"
          @select="emit('select', $event)"
        />
      </div>
    </div>
  </div>
</template>
