/**
 * 文件作用：
 * 统一注册 @kangc/v-md-editor（Vue 3 版）的编辑器、预览组件及所依赖的
 * github 主题（基于 highlight.js 代码高亮）与中文语言。
 * 与后台 rookie-ui/src/utils/markdown.ts 同款实现，前后台一致。
 * 关键约定：
 * - 主入口默认导出是编辑器插件（带 install，组件名 v-md-editor），
 *   预览组件在 lib/preview.js（带 install，组件名 v-md-preview），
 *   两者都通过 app.use() 全局注册，页面直接用 <v-md-editor> / <v-md-preview>；
 * - 编辑器与预览是两个独立的 vMdParser 实例，github 主题必须分别 use 到两个插件上，
 *   否则预览组件 created 读 themeConfig.markdownParser 为 undefined 报错；
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
 * 幂等注册 v-md-editor 的 github 主题（含 highlight.js 代码高亮）与中文语言，
 * 并把编辑器、预览组件挂到应用实例上，保证只在应用启动时执行一次。
 */
export const setupVmdEditor = (app: App) => {
  if (registered) {
    return
  }

  // 覆盖自带 preview 工具条项的 title 为"对比"：原 tooltip"开启预览/关闭预览"语义不准，
  // 该按钮实际行为是 toggle edit↔editable（编辑↔左右对比），属创作页"对比"功能。
  // 其余 icon(v-md-icon-preview)/active/action(切 currentMode) 照库原样保留，零行为改动。
  // 库的默认导出类型未暴露 toolbar()，用最小结构类型强型调用（运行时存在）。
  type VmdEditorLike = {
    lang: { use: (lang: string, config: unknown) => void }
    use: (plugin: unknown, config?: unknown) => void
    toolbar: (name: string, config: Record<string, unknown>) => void
  }
  const Editor = VueMarkdownEditor as unknown as VmdEditorLike

  // 编辑器：语言 + 主题（主题内含 highlight.js 代码高亮）
  Editor.lang.use('zh-CN', zhCN)
  Editor.use(githubTheme, { Hljs: hljs })

  Editor.toolbar('preview', {
    name: 'preview',
    icon: 'v-md-icon-preview',
    title: '对比',
    active: function active(editor: { currentMode: string }) {
      return editor.currentMode === 'editable'
    },
    action: function action(editor: { currentMode: string }) {
      editor.currentMode = editor.currentMode === 'editable' ? 'edit' : 'editable'
    },
  })

  // 预览：与编辑器是独立 parser 实例，必须单独 use 同一主题，
  // 否则预览组件 created 读 themeConfig.markdownParser 为 undefined 报错
  VueMarkdownPreview.use(githubTheme, { Hljs: hljs })

  app.use(VueMarkdownEditor)
  app.use(VueMarkdownPreview)

  registered = true
}