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
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
      // 文件模块所有接口走 /file 前缀：PUBLIC 回显 /file/public/{id}、中转下载 /file/proxy/{id}、
      // 中转上传 /file/proxy-upload/{id}、列表/详情/下载/删除等。dev 经此代理转发到后端；
      // 生产由 nginx 同名转发。双模式（中转/直链）下直链模式用绝对 URL 不经此代理，中转模式全部走此代理。
      '/file': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
  },
})
