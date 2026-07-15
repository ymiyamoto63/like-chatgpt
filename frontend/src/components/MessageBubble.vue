<script setup lang="ts">
import type {
  BarChartComponent,
  ChoicesComponent,
  Message,
  TableComponent,
  UiComponentSpec,
} from '../types/chat'
import TableView from './TableView.vue'
import BarChartView from './BarChartView.vue'
import ChoicesView from './ChoicesView.vue'

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
</script>

<template>
  <div class="message-row" :class="message.role">
    <div class="bubble">
      {{ message.content }}
      <div
        v-for="(component, index) in message.components"
        :key="index"
        class="component-wrapper"
      >
        <TableView v-if="isTableComponent(component)" :spec="component" />
        <BarChartView v-else-if="isBarChartComponent(component)" :spec="component" />
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

<style scoped>
.message-row {
  display: flex;
  width: 100%;
}

.message-row.user {
  justify-content: flex-end;
}

.message-row.assistant {
  justify-content: flex-start;
}

.bubble {
  max-width: 70%;
  padding: 10px 14px;
  border-radius: 12px;
  white-space: pre-wrap;
  word-break: break-word;
  text-align: left;
}

.message-row.user .bubble {
  background: var(--accent);
  color: #fff;
  border-bottom-right-radius: 2px;
}

.message-row.assistant .bubble {
  background: var(--code-bg);
  color: var(--text-h);
  border-bottom-left-radius: 2px;
}

.component-wrapper {
  margin-top: 10px;
  white-space: normal;
}
</style>
