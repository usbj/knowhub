import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
  ],
  server: {
    // 前台端口固定 5174，避开后台管理项目 rookie-ui 的默认端口 5173，防止本地同时启动时端口冲突。
    port: 5174,
    // 与后台 rookie-ui 对齐：dev 下经 /api 代理转发到后端 localhost:8080，
    // 请求头 Token 由前端 http 工具直接注入，后端 TokenVerifyFilter 读取。
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
    },
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
})
