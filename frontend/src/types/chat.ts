export interface TableComponent {
  type: 'table'
  columns: string[]
  rows: string[][]
}

export interface BarChartComponent {
  type: 'bar_chart'
  title: string
  labels: string[]
  values: number[]
}

export type UiComponentSpec = TableComponent | BarChartComponent

export interface Message {
  id: string
  role: 'user' | 'assistant'
  content: string
  createdAt: number
  components?: UiComponentSpec[]
}

export interface Conversation {
  id: string
  title: string
  messages: Message[]
}
