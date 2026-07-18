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

export interface ChoicesComponent {
  type: 'choices'
  options: string[]
}

export interface TrendChartComponent {
  type: 'trend_chart'
  title: string
  labels: string[]
  values: number[]
  average: number
}

export interface FaqListComponent {
  type: 'faq_list'
  titles: string[]
}

export type UiComponentSpec =
  | TableComponent
  | BarChartComponent
  | ChoicesComponent
  | TrendChartComponent
  | FaqListComponent

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
