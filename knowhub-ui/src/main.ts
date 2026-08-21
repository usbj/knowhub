import { createApp } from 'vue'
import { createPinia } from 'pinia'

// Element Plus 全量注册（demo 阶段省事，正式制作再切按需）
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

// 全局样式：令牌在前、基础在后、EP 覆写最后
import './assets/design-tokens.css'
import './assets/base.css'

// 代码块等宽字体：JetBrains Mono 自托管（@fontsource 包内含 woff2，Vite 构建会自动 hash 纳入产物，无需手动放字体文件）。
// 只引 latin 子集 + 400/500/700 三个字重（正文/强调/关键字加粗），避免引全子集全字重致打包膨胀。
// fontsource 默认 font-display: swap（FOUT，字体加载期用系统等宽回退，加载完替换，无 FOIT 闪烁）。
// 国内禁用 Google Fonts 远程加载，走 npm 包自托管保证稳定。
import '@fontsource/jetbrains-mono/latin-400.css'
import '@fontsource/jetbrains-mono/latin-500.css'
import '@fontsource/jetbrains-mono/latin-700.css'

// @kangc/v-md-editor 主题样式（编辑器外壳 / 预览 / github 主题 + highlight.js atom-one-light 浅色 IDE 配色）
// 与后台 rookie-ui 引入顺序一致，页面可直接用 <v-md-editor> / <v-md-preview>
import '@kangc/v-md-editor/lib/style/base-editor.css'
import '@kangc/v-md-editor/lib/style/preview.css'
import '@kangc/v-md-editor/lib/theme/style/github.css'
// 代码块高亮配色：用 atom-one-light 浅色 IDE 主题（接近 VS Code Light+，配合 markdown.css 代码块浅色 #F8F8F2 容器，浅底深字 token 协调）
import 'highlight.js/styles/atom-one-light.css'
// 全局 Markdown 渲染样式：收口 .github-markdown-body 代码块/表格/引用/标题梯度等覆盖，
// 引入在 v-md-editor github.css + highlight.js github.css 之后，确保覆盖优先
import './assets/markdown.css'

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
