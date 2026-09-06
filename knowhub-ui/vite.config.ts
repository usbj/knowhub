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
      // 文件回显走相对路径 /file/resolve/{id}、/file/public/{id}（<img src> 浏览器直发，不走 axios/baseURL）。
      // 不配此代理时 /file/resolve 会被 dev server 当成前端路由 → 后端 302 根本收不到请求，图片裂图。
      // 生产由 nginx 同名转发；与后台 rookie-ui 同口径。
      '/file': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
})
