/**
 * 文件作用：
 * 统一注册 @kangc/v-md-editor@next（Vue 3 版）的编辑器、预览组件及所依赖的
 * github 主题（基于 highlight.js 代码高亮）与中文语言。
 * 关键约定：
 * - @next 版主入口默认导出是编辑器插件（带 install，组件名 v-md-editor），
 *   预览组件在 lib/preview.js（带 install，组件名 v-md-preview），
 *   两者都通过 app.use() 全局注册，页面直接用 <v-md-editor> / <v-md-preview>；
 * - 编辑器与预览是两个独立的 vMdParser 实例，github 主题必须分别 use 到两个插件上，
 *   否则预览组件 created 时读 themeConfig.markdownParser 为 undefined 会报错；
 * - github 主题编译产物是带 install(app, config) 的插件对象，config.Hljs 经
 *   use(plugin, config) 第二参透传，注入 highlight.js 实例启用代码块高亮；
 * - 中文语言只对编辑器工具栏生效（预览无工具栏），只在编辑器上设。
 */
import type { App } from 'vue'
import hljs from 'highlight.js'
import VueMarkdownEditor from '@kangc/v-md-editor'
import VueMarkdownPreview from '@kangc/v-md-editor/lib/preview.js'
import githubTheme from '@kangc/v-md-editor/lib/theme/github.js'
import zhCN from '@kangc/v-md-editor/lib/lang/zh-CN'

let registered = false

/**
 * 方法效果：
 * 幂等注册 v-md-editor 的 github 主题（含 highlight.js 代码高亮）与中文语言，
 * 并把编辑器、预览组件挂到应用实例上，保证只在应用启动时执行一次。
 * 参数：
 * - `app`：Vue 应用实例，用于 app.use 全局注册两个组件插件。
 * 返回值：
 * - 无返回值；副作用是完成主题、语言、组件注册。
 */
export const setupVmdEditor = (app: App) => {
  if (registered) {
    return
  }

  // 编辑器：语言 + 主题（主题内含 highlight.js 代码高亮）
  VueMarkdownEditor.lang.use('zh-CN', zhCN)
  VueMarkdownEditor.use(githubTheme, { Hljs: hljs })

  // 预览：与编辑器是独立 parser 实例，必须单独 use 同一主题，
  // 否则预览组件 created 读 themeConfig.markdownParser 为 undefined 报错
  VueMarkdownPreview.use(githubTheme, { Hljs: hljs })

  app.use(VueMarkdownEditor)
  app.use(VueMarkdownPreview)

  registered = true
}
