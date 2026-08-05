import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    rollupOptions: {
      output: {
        // 把体积大、变动少的第三方库拆成独立 chunk：
        // 与频繁变动的业务代码分离，发版后这些 vendor chunk 仍命中缓存，
        // 也让首屏并行下载而不是挤在一个 1MB+ 的大包里。
        manualChunks: {
          'element-plus': ['element-plus', '@element-plus/icons-vue'],
          vue: ['vue', 'vue-router', 'pinia'],
        },
      },
    },
  },
  server: {
    port: 5173,
    host: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8888',
        changeOrigin: true,
      },
      '/drama-covers': {
        target: 'http://localhost:8888',
        changeOrigin: true,
      },
    },
  },
})
