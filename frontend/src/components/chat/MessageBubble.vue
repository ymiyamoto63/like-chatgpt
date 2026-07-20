<script setup lang="ts">
import { ref } from 'vue'
import type { Ref } from 'vue'
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
import { useConversationStore } from '../../stores/conversationStore'
import { useTypewriterReveal } from '../../composables/useTypewriterReveal'
import TableView from '../dynamic-ui/TableView.vue'
import BarChartView from '../dynamic-ui/BarChartView.vue'
import ChoicesView from '../dynamic-ui/ChoicesView.vue'
import TrendChartView from '../dynamic-ui/TrendChartView.vue'
import FaqListView from '../dynamic-ui/FaqListView.vue'
import StatCardsView from '../dynamic-ui/StatCardsView.vue'
import DonutChartView from '../dynamic-ui/DonutChartView.vue'

const props = defineProps<{
  message: Message
  choicesEnabled?: boolean
}>()

const emit = defineEmits<{
  select: [option: string]
}>()

const store = useConversationStore()

// マウント時に一度だけ判定し固定する（新規到着メッセージか、復元メッセージか）。
// 復元メッセージは animatingMessageIds に登録されないため常に false となる。
const shouldAnimate =
  props.message.role === 'assistant' && store.isMessageAnimating(props.message.id)

let displayedText: Ref<string>
let showComponents: Ref<boolean>
let componentsVisible: Ref<boolean>

if (shouldAnimate) {
  const reveal = useTypewriterReveal({
    text: props.message.content,
    hasComponents: (props.message.components?.length ?? 0) > 0,
    onSettled: () => store.settleMessageAnimation(props.message.id),
  })
  displayedText = reveal.displayedText
  showComponents = reveal.showComponents
  componentsVisible = reveal.componentsVisible
} else {
  displayedText = ref(props.message.content)
  showComponents = ref(true)
  componentsVisible = ref(true)
}

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
      {{ displayedText }}
      <div
        v-if="showComponents"
        class="mt-3 transition-opacity duration-300"
        :class="componentsVisible ? 'opacity-100' : 'opacity-0'"
      >
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
  </div>
</template>
