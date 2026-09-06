/**
 * 文件作用：
 * markdown 代码块增强 composable。v-md-preview（github 主题）渲染出的 fence 代码块结构为
 *   <div class="v-md-pre-wrapper v-md-pre-wrapper-{lang} extra-class" extra-attr>
 *     <pre class="language-{lang}"><code class="hljs language-{lang}">…</code></pre>
 *   </div>
 * 本 composable 把每个 wrapper 重构为「工具栏 + 代码区」的 flex 兄弟结构（提示词方案 A）：
 *   <div class="v-md-pre-wrapper …">            ← 外层，flex column，圆角裁剪
 *     <div class="kh-code-header">              ← 工具栏，flex-shrink:0，永远固定顶部不被滚走
 *       <span class="kh-code-lang">JavaScript</span>   ← 左，无语言时不挂
 *       <button class="kh-code-copy" aria-label="复制代码">…</button>  ← 右，margin-left:auto 推到右
 *     </div>
 *     <pre class="language-{lang}"><code>…</code></pre>  ← 代码区，flex:1，横向滚动只发生在此
 *   </div>
 * ⚠️ 关键：工具栏与 pre 是兄弟节点，工具栏不是 pre/code 的子元素，故横向滚动代码时工具栏不被带走、
 * 也不靠 padding-top 浮动占位（旧实现用 absolute + padding-top hack，脆弱）。
 *
 * 行号：给 code 内每行包 <span class="kh-code-line">，CSS counter 在 .kh-code-line::before 自动生成行号。
 * pre 软换行（pre-wrap）时无横向滚动条（用户此前反馈不想要滚轮）；若日后切硬换行（pre + overflow-x:auto），
 * 行号 ::before 用 sticky left:0 可在横向滚动时固定左侧（本版 pre-wrap 下不涉及）。
 *
 * 与 {@link useMarkdownImageZoom} 共用同一 contentRef（7 站正文/简介/评论/公告 v-md-preview 站点）：
 * - useMarkdownImageZoom 的 onContentClick 仅在 <img> 点击源触发，与本 composable 的复制按钮点击正交不冲突；
 * - 复制按钮挂 @click.stop 防冒泡到 onContentClick（IMG 才触发，本就不冲突，stop 保险防 future）。
 *
 * 渲染时序：v-md-preview 用 requestAnimationFrame 异步渲染 markdown，contentRef 挂载时代码块可能尚未出现；
 * 且切章节/切内容时 v-md-preview 会重渲染。故用 MutationObserver 监听 contentRef 子树 childList/subtree，
 * 子树变化即触发 enhance（幂等：已注入 `[data-kh-code-enhanced]` 的跳过），覆盖首屏异步 + 重渲染两场景。
 * observer 在 onBeforeUnmount disconnect 防泄漏。
 *
 * 用法（接入站 7 处，与 useMarkdownImageZoom 并列）：
 *   const contentRef = ref<HTMLElement | null>(null)
 *   const { … } = useMarkdownImageZoom(contentRef)
 *   useMarkdownCodeBlock(contentRef)   // 副作用型，无返回值需接
 *   模板容器 div 已是 ref="contentRef"，零模板改动。
 *
 * 样式由全局 assets/markdown.css 的 .kh-code-header / .kh-code-lang / .kh-code-copy 持，
 * 本 composable 只注 DOM + 绑事件。
 */
import { nextTick, onBeforeUnmount, onMounted, watch } from 'vue'
import type { Ref } from 'vue'
import { ElMessage } from 'element-plus'

/** 代码 class 名里的语言短码 → 友好显示名映射；未列出的回退到「大写首字母」或原样。 */
const LANG_DISPLAY: Record<string, string> = {
  js: 'JavaScript',
  javascript: 'JavaScript',
  ts: 'TypeScript',
  typescript: 'TypeScript',
  vue: 'Vue',
  java: 'Java',
  py: 'Python',
  python: 'Python',
  sql: 'SQL',
  bash: 'Shell',
  sh: 'Shell',
  shell: 'Shell',
  html: 'HTML',
  xml: 'XML',
  css: 'CSS',
  scss: 'SCSS',
  json: 'JSON',
  yml: 'YAML',
  yaml: 'YAML',
  md: 'Markdown',
  markdown: 'Markdown',
  go: 'Go',
  rs: 'Rust',
  rust: 'Rust',
  c: 'C',
  cpp: 'C++',
  'c++': 'C++',
  cs: 'C#',
  'c#': 'C#',
  php: 'PHP',
  rb: 'Ruby',
  ruby: 'Ruby',
  kt: 'Kotlin',
  kotlin: 'Kotlin',
  dart: 'Dart',
  swift: 'Swift',
  dockerfile: 'Dockerfile',
  makefile: 'Makefile',
  ini: 'INI',
  toml: 'TOML',
  text: 'Text',
  plaintext: 'Text',
}

/** 把语言短码转友好显示名：先查映射表，未命中则首字母大写，再未命中原样返回。 */
const displayLang = (lang: string): string => {
  const lower = lang.toLowerCase()
  if (LANG_DISPLAY[lower]) return LANG_DISPLAY[lower]
  if (LANG_DISPLAY[lang]) return LANG_DISPLAY[lang]
  // 形如 csharp→Csharp 这类无映射的，首字母大写兜底
  return lang.charAt(0).toUpperCase() + lang.slice(1)
}

/** Lucide copy 图标 inline SVG（composable 不引 Vue 组件，保持纯逻辑）。 */
const COPY_ICON_SVG =
  '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>'
/** 复制成功态图标（Lucide check）。 */
const CHECK_ICON_SVG =
  '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path d="M20 6 9 17l-5-5"/></svg>'
/** 复制失败态图标（Lucide x）。 */
const FAIL_ICON_SVG =
  '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path d="M18 6 6 18M6 6l12 12"/></svg>'

/** 从 wrapper class 列表里提取语言短码（v-md-pre-wrapper-{lang} 或 language-{lang}）。无语言返 ''。 */
const extractLang = (wrapper: HTMLElement): string => {
  const cls = wrapper.className
  const m = cls.match(/(?:v-md-pre-wrapper|language)-([a-zA-Z0-9+#-]+)/)
  return m ? (m[1] ?? '') : ''
}

/** 复制按钮点击：写 pre.textContent 到剪贴板，成功换「已复制」✓（绿）1.5s 还原，失败换 ✕（红）1.5s 还原。
 *  两态都用 setTimeout 还原图标 + 清 class，避免状态残留；写剪贴板异常（非安全上下文/无权限）走失败态。 */
const handleCopy = async (btn: HTMLButtonElement, pre: HTMLElement) => {
  const text = pre.textContent ?? ''
  let ok = false
  try {
    await navigator.clipboard.writeText(text)
    ok = true
  } catch {
    ok = false
  }
  if (ok) {
    btn.classList.add('is-copied')
    btn.classList.remove('is-failed')
    btn.innerHTML = CHECK_ICON_SVG
    btn.title = '已复制'
    btn.setAttribute('aria-label', '已复制')
  } else {
    btn.classList.add('is-failed')
    btn.classList.remove('is-copied')
    btn.innerHTML = FAIL_ICON_SVG
    btn.title = '复制失败，请手动选择代码复制'
    btn.setAttribute('aria-label', '复制失败')
    ElMessage.warning('复制失败，请手动选择代码复制')
  }
  window.setTimeout(() => {
    btn.classList.remove('is-copied', 'is-failed')
    btn.innerHTML = COPY_ICON_SVG
    btn.title = '复制'
    btn.setAttribute('aria-label', '复制代码')
  }, 1500)
}

export function useMarkdownCodeBlock(contentRef: Ref<HTMLElement | null>) {
  let observer: MutationObserver | null = null

  /**
   * 遍历 contentRef 内所有 `div[class*=v-md-pre-wrapper]`，幂等注入语言标签 + 复制按钮。
   * 幂等：wrapper 有 [data-kh-code-enhanced] 标记则跳过（切章节重渲染时新 wrapper 无标记会被注入，
   * 旧 wrapper 若被 v-md-preview 复用仍带标记不重复注入；若被替换则新元素自然无标记）。
   */
  /**
   * 把 code 内部按行包成 <span class="kh-code-line">，供 CSS counter 自动生成行号。
   * 保留 hljs token span：遍历 code 顶层 childNodes，文本节点按 \n 拆段，元素节点整体归入当前行；
   * 行间插 <span class="kh-code-line">…</span> 包裹每行内容。跨行 token（多行字符串/注释）归入起始行，
   * 其内部 \n 自然换行（行号对该行少计 1，可接受——单行 token 是绝大多数，跨行极少且代码仍可读）。
   */
  const wrapLines = (code: HTMLElement) => {
    if (code.querySelector('.kh-code-line')) return // 已包过则跳过（幂等）
    const fragment = document.createDocumentFragment()
    let line = document.createElement('span')
    line.className = 'kh-code-line'
    code.childNodes.forEach((node) => {
      if (node.nodeType === Node.TEXT_NODE) {
        const text = node.textContent ?? ''
        const parts = text.split('\n')
        parts.forEach((part, i) => {
          if (i > 0) {
            // 遇到换行：当前行收尾，进 fragment，开新行
            fragment.appendChild(line)
            line = document.createElement('span')
            line.className = 'kh-code-line'
          }
          // 空行不添加子节点（保持 span 为空）；空 span 高度塌陷由 CSS .kh-code-line min-height 兜底，
          // 不在此塞零宽空白——否则会污染 pre.textContent 致复制带不可见字符。
          if (part) line.appendChild(document.createTextNode(part))
        })
      } else {
        // 元素节点（hljs token span 等）整体归入当前行
        line.appendChild(node.cloneNode(true))
      }
    })
    fragment.appendChild(line) // 末行
    code.replaceChildren(fragment)
  }

  const enhance = () => {
    const root = contentRef.value
    if (!root) return
    const wrappers = root.querySelectorAll<HTMLElement>('div[class*="v-md-pre-wrapper"]')
    wrappers.forEach((wrapper) => {
      if (wrapper.dataset.khCodeEnhanced) return
      const pre = wrapper.querySelector('pre')
      const code = wrapper.querySelector('code')
      if (!pre || !code) return

      // 行号：给 code 内部每行包 .kh-code-line，CSS counter 自动编号
      wrapLines(code)

      // 工具栏（flex 兄弟，插在 pre 之前）：永远固定顶部，不随代码滚动移动、不靠 padding-top 浮动占位
      const header = document.createElement('div')
      header.className = 'kh-code-header'

      // 语言标签（左）：无语言代码块（纯 ```）挂一个「Text」灰底占位，避免工具栏左空右挤失衡（提示词：无语言显 text）
      const lang = extractLang(wrapper)
      const langLabel = document.createElement('span')
      langLabel.className = 'kh-code-lang'
      if (!lang) langLabel.classList.add('is-plain')
      langLabel.textContent = lang ? displayLang(lang) : 'Text'
      header.appendChild(langLabel)

      // 复制按钮（右，margin-left:auto 推到工具栏右端）：JS 动态注入，不破坏 pre>code 原生结构
      const copyBtn = document.createElement('button')
      copyBtn.className = 'kh-code-copy'
      copyBtn.type = 'button'
      copyBtn.title = '复制'
      copyBtn.setAttribute('aria-label', '复制代码')
      copyBtn.innerHTML = COPY_ICON_SVG
      copyBtn.addEventListener('click', (e) => {
        e.stopPropagation()
        void handleCopy(copyBtn, pre)
      })
      header.appendChild(copyBtn)

      // pre 是 wrapper 现有首子，header 插其前 → 工具栏与代码区成 flex 兄弟
      wrapper.insertBefore(header, pre)
      wrapper.dataset.khCodeEnhanced = '1'
    })
  }

  onMounted(() => {
    // 首屏：v-md-preview 异步渲染，等下一帧再 enhance 兜底（observer 也会捕获，双保险）
    nextTick(enhance)
    observer = new MutationObserver(() => enhance())
    // 监听 contentRef 子树：v-md-preview 挂载/重渲染时代码块 wrapper 进子树即触发
    const root = contentRef.value
    if (root) {
      observer.observe(root, { childList: true, subtree: true })
    }
  })

  // contentRef 可能延迟赋值（v-if 条件容器，如 KhNoticeDetailDialog 的 bodyReady 延迟挂载 v-md-preview）：
  // watch contentRef 变化，从 null→有值时（重新）挂 observer + enhance
  const stopWatch = watch(
    contentRef,
    (root) => {
      if (!observer) return
      if (root) {
        observer.observe(root, { childList: true, subtree: true })
        nextTick(enhance)
      } else {
        observer.disconnect()
      }
    },
  )

  onBeforeUnmount(() => {
    observer?.disconnect()
    observer = null
    stopWatch()
  })

  return { enhance }
}
