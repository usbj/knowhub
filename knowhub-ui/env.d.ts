/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<Record<string, never>, Record<string, never>, unknown>
  export default component
}

declare module '*.svg' {
  const src: string
  export default src
}

/**
 * @kangc/v-md-editor (2.x, Vue 3) 未随包发布类型声明（package.json 的 types 字段指向不存在的目录），
 * 这里按实际使用形态给出 ambient 声明，避免 vue-tsc 报 "无法找到模块声明"。
 * 主入口默认导出是编辑器插件对象（带 install/use/lang，组件名 v-md-editor），
 * 预览组件在 lib/preview.js（带 install，组件名 v-md-preview），均经 app.use 全局注册。
 */
declare module '@kangc/v-md-editor' {
  const VueMarkdownEditor: Record<string, unknown> & {
    install: (app: unknown) => void
    use: (plugin: unknown, options?: unknown) => void
    lang: { use: (lang: string, pack: Record<string, unknown>) => void }
  }
  export default VueMarkdownEditor
}

declare module '@kangc/v-md-editor/lib/preview.js' {
  const VueMarkdownPreview: Record<string, unknown> & {
    install: (app: unknown) => void
    use: (plugin: unknown, options?: unknown) => void
  }
  export default VueMarkdownPreview
}

declare module '@kangc/v-md-editor/lib/theme/github.js' {
  interface GithubThemeConfig {
    Hljs: unknown
    baseConfig?: Record<string, unknown>
    codeBlockClass?: (lang: string) => string
    codeHighlightExtensionMap?: Record<string, unknown>
  }
  /** 编译产物是带 install 的 Vue 插件对象，config 经 use(plugin, config) 透传。 */
  const githubTheme: { install: (app: unknown, config?: GithubThemeConfig) => void }
  export default githubTheme
}

declare module '@kangc/v-md-editor/lib/lang/zh-CN' {
  const lang: Record<string, unknown>
  export default lang
}
