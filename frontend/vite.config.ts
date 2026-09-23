import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue(), tailwindcss()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    include: ['src/**/*.spec.ts'],
    coverage: {
      // quality-gate M-01 ブランチカバレッジの計測元（reports/frontend-coverage/lcov.info）
      provider: 'v8',
      // テストが触れていないファイルも分母に含める。include を指定しないと
      // テストを書いた範囲だけの数字になり、実態より良く見える
      include: ['src/**/*.{ts,vue}'],
      // .quality-gate.yml の exclusions と揃える
      exclude: ['src/**/*.spec.ts', 'src/main.ts', 'src/**/*.d.ts'],
      reporter: ['text', 'lcov'],
      reportsDirectory: '../reports/frontend-coverage',
    },
  },
})
