import type {
  BarChartComponent,
  ChoicesComponent,
  TableComponent,
  TrendChartComponent,
  UiComponentSpec,
} from '../types/chat'

export interface ChatApiResponse {
  reply: string
  components: UiComponentSpec[]
}

export class ChatResponseFormatError extends Error {}

function isStringArray(value: unknown): value is string[] {
  return Array.isArray(value) && value.every((item) => typeof item === 'string')
}

function isValidTableComponent(value: unknown): value is TableComponent {
  const { columns, rows } = value as Record<string, unknown>
  if (!isStringArray(columns) || columns.length === 0) {
    return false
  }
  if (!Array.isArray(rows)) {
    return false
  }
  return rows.every((row) => isStringArray(row) && row.length === columns.length)
}

function isValidBarChartComponent(value: unknown): value is BarChartComponent {
  const { title, labels, values } = value as Record<string, unknown>
  if (typeof title !== 'string') {
    return false
  }
  if (!isStringArray(labels) || labels.length === 0) {
    return false
  }
  if (!Array.isArray(values) || values.length !== labels.length) {
    return false
  }
  return values.every((v) => typeof v === 'number' && Number.isFinite(v))
}

function isValidChoicesComponent(value: unknown): value is ChoicesComponent {
  const { options } = value as Record<string, unknown>
  return isStringArray(options) && options.length > 0
}

function isValidTrendChartComponent(value: unknown): value is TrendChartComponent {
  const { title, labels, values, average } = value as Record<string, unknown>
  if (typeof title !== 'string') {
    return false
  }
  if (!isStringArray(labels) || labels.length === 0) {
    return false
  }
  if (!Array.isArray(values) || values.length !== labels.length) {
    return false
  }
  if (!values.every((v) => typeof v === 'number' && Number.isFinite(v))) {
    return false
  }
  return typeof average === 'number' && Number.isFinite(average)
}

function isValidUiComponentSpec(value: unknown): value is UiComponentSpec {
  if (typeof value !== 'object' || value === null) {
    return false
  }
  const record = value as Record<string, unknown>
  if (record.type === 'table') {
    return isValidTableComponent(record)
  }
  if (record.type === 'bar_chart') {
    return isValidBarChartComponent(record)
  }
  if (record.type === 'choices') {
    return isValidChoicesComponent(record)
  }
  if (record.type === 'trend_chart') {
    return isValidTrendChartComponent(record)
  }
  return false
}

function validateChatApiResponse(value: unknown): ChatApiResponse {
  if (typeof value !== 'object' || value === null) {
    throw new ChatResponseFormatError('Chat API response is not an object')
  }

  const record = value as Record<string, unknown>
  if (typeof record.reply !== 'string') {
    throw new ChatResponseFormatError('Chat API response "reply" is not a string')
  }
  if (!Array.isArray(record.components)) {
    throw new ChatResponseFormatError('Chat API response "components" is not an array')
  }
  if (!record.components.every(isValidUiComponentSpec)) {
    throw new ChatResponseFormatError('Chat API response contains an invalid component')
  }

  return { reply: record.reply, components: record.components as UiComponentSpec[] }
}

export async function postChat(message: string): Promise<ChatApiResponse> {
  const response = await fetch('/api/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ message }),
  })

  if (!response.ok) {
    throw new Error(`Chat API request failed with status ${response.status}`)
  }

  return validateChatApiResponse(await response.json())
}
