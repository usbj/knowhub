import './assets/main.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import '@kangc/v-md-editor/lib/style/base-editor.css'
import '@kangc/v-md-editor/lib/style/preview.css'
import '@kangc/v-md-editor/lib/theme/style/github.css'
import 'highlight.js/styles/github.css'

import App from './App.vue'
import router from './router'
import { useThemePreferenceStore } from '@/stores/themePreference'
import { setupVmdEditor } from '@/utils/markdown'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)
app.use(ElementPlus)

/**
 * markdown 编辑器/预览的主题、语言统一在应用启动时注册一次：
 * - github 主题（含 highlight.js 代码高亮）在 utils/markdown 注入 Hljs 实例；
 * - 编辑器、预览组件经 app.use 全局注册，页面用 <v-md-editor> / <v-md-preview>；
 * - 样式按需引入 base-editor / preview / github 主题 + highlight.js github 配色。
 */
setupVmdEditor(app)

/**
 * 主题 store 需要在应用挂载前先把 CSS 变量打到页面上，
 * 这样初次渲染就能拿到正确的明暗主题和尺寸参数。
 */
useThemePreferenceStore(pinia).applyThemeSettings()

app.mount('#app')
