import { createApp } from 'vue'
import { createPinia } from 'pinia'

// Element Plus 全量注册（demo 阶段省事，正式制作再切按需）
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

// 全局样式：令牌在前、基础在后、EP 覆写最后
import './assets/design-tokens.css'
import './assets/base.css'

// @kangc/v-md-editor 主题样式（编辑器外壳 / 预览 / github 主题 + highlight.js github 配色）
// 与后台 rookie-ui 引入顺序一致，页面可直接用 <v-md-editor> / <v-md-preview>
import '@kangc/v-md-editor/lib/style/base-editor.css'
import '@kangc/v-md-editor/lib/style/preview.css'
import '@kangc/v-md-editor/lib/theme/style/github.css'
import 'highlight.js/styles/github.css'

import App from './App.vue'
import router from './router'
import { setupVmdEditor } from '@/utils/markdown'

const app = createApp(App)

// 注册 Element Plus 图标为全局组件（UI 操作图标用）
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component as never)
}

app.use(createPinia())
app.use(router)
app.use(ElementPlus)

/**
 * markdown 编辑器/预览的主题、语言统一在应用启动时注册一次：
 * - github 主题（含 highlight.js 代码高亮）在 utils/markdown 注入 Hljs 实例；
 * - 编辑器、预览组件经 app.use 全局注册，页面用 <v-md-editor> / <v-md-preview>；
 * 深色模式覆盖待前台主题体系建立后补齐（见根 README.dev §12）。
 */
setupVmdEditor(app)

app.mount('#app')
