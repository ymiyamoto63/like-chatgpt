<script setup lang="ts">
import type { TableComponent } from '../../types/chat'

defineProps<{
  spec: TableComponent
}>()
</script>

<template>
  <div
    class="max-w-full overflow-x-auto rounded-lg border border-zinc-200 dark:border-zinc-700"
  >
    <table class="w-full border-collapse text-sm">
      <thead>
        <tr class="border-b border-zinc-200 dark:border-zinc-700">
          <th
            v-for="(column, columnIndex) in spec.columns"
            :key="columnIndex"
            class="bg-zinc-50 px-3 py-2 text-left font-semibold whitespace-nowrap text-zinc-900 dark:bg-zinc-900 dark:text-zinc-100"
          >
            {{ column }}
          </th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="(row, rowIndex) in spec.rows"
          :key="rowIndex"
          class="border-b border-zinc-100 last:border-b-0 dark:border-zinc-800"
        >
          <td
            v-for="(cell, cellIndex) in row"
            :key="cellIndex"
            class="px-3 py-2 whitespace-nowrap text-zinc-700 dark:text-zinc-300"
          >
            {{ cell }}
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
