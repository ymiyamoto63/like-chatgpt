import { API_BASE_URL } from '../constants/api'
import type { MonitoringEdge, MonitoringNode, MonitoringSnapshot } from '../types/monitoring'

export class MonitoringResponseFormatError extends Error {}

function isValidMonitoringNode(value: unknown): value is MonitoringNode {
  if (typeof value !== 'object' || value === null) {
    return false
  }
  const { id, label, cpuPercent, memoryPercent } = value as Record<string, unknown>
  if (typeof id !== 'string' || typeof label !== 'string') {
    return false
  }
  return (
    typeof cpuPercent === 'number' &&
    Number.isFinite(cpuPercent) &&
    typeof memoryPercent === 'number' &&
    Number.isFinite(memoryPercent)
  )
}

function isValidMonitoringEdge(value: unknown): value is MonitoringEdge {
  if (typeof value !== 'object' || value === null) {
    return false
  }
  const { id, sourceId, targetId, bandwidthPercent } = value as Record<string, unknown>
  if (typeof id !== 'string' || typeof sourceId !== 'string' || typeof targetId !== 'string') {
    return false
  }
  return typeof bandwidthPercent === 'number' && Number.isFinite(bandwidthPercent)
}

function validateMonitoringSnapshot(value: unknown): MonitoringSnapshot {
  if (typeof value !== 'object' || value === null) {
    throw new MonitoringResponseFormatError('Monitoring API response is not an object')
  }

  const record = value as Record<string, unknown>
  if (!Array.isArray(record.nodes) || !record.nodes.every(isValidMonitoringNode)) {
    throw new MonitoringResponseFormatError('Monitoring API response "nodes" is invalid')
  }
  if (!Array.isArray(record.edges) || !record.edges.every(isValidMonitoringEdge)) {
    throw new MonitoringResponseFormatError('Monitoring API response "edges" is invalid')
  }

  return { nodes: record.nodes, edges: record.edges }
}

export async function getMonitoringSnapshot(): Promise<MonitoringSnapshot> {
  const response = await fetch(`${API_BASE_URL}/api/monitoring/snapshot`)

  if (!response.ok) {
    throw new Error(`Monitoring API request failed with status ${response.status}`)
  }

  return validateMonitoringSnapshot(await response.json())
}
