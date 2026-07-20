export interface MonitoringNode {
  id: string
  label: string
  cpuPercent: number
  memoryPercent: number
}

export interface MonitoringEdge {
  id: string
  sourceId: string
  targetId: string
  bandwidthPercent: number
}

export interface MonitoringSnapshot {
  nodes: MonitoringNode[]
  edges: MonitoringEdge[]
}

export interface MonitoringHistorySample {
  timestamp: number
  cpuPercent: number
  memoryPercent: number
}
