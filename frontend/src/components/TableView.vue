<script setup lang="ts">
import type { TableComponent } from '../types/chat'

defineProps<{
  spec: TableComponent
}>()
</script>

<template>
  <div class="table-view">
    <table>
      <thead>
        <tr>
          <th v-for="(column, columnIndex) in spec.columns" :key="columnIndex">{{ column }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(row, rowIndex) in spec.rows" :key="rowIndex">
          <td v-for="(cell, cellIndex) in row" :key="cellIndex">{{ cell }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.table-view {
  overflow-x: auto;
  max-width: 100%;
}

table {
  border-collapse: collapse;
  font-size: 0.95em;
}

th,
td {
  padding: 6px 10px;
  border: 1px solid var(--border);
  text-align: left;
  white-space: nowrap;
}

th {
  background: var(--accent-bg);
  color: var(--text-h);
  font-weight: 600;
}

td {
  color: var(--text-h);
}
</style>
