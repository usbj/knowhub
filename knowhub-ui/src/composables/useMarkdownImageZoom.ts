/**
 * 文件作用：
 * markdown 正文配图点击放大 composable。v-md-preview 渲染出的 `.github-markdown-body img`
 * 默认只静态铺图不可交互；本 composable 把「点配图 → el-image-viewer 全屏画廊放大（缩放/旋转/切同容器内其它配图）」
 * 全流程封装，供 6 处正文/简介 v-md-preview 站点复用（KhComment、blog/detail、article/detail、article/chapter/read、
 * project/detail、resource/detail、layout/KhNoticeDetailDialog），避免 7 处复制同一段事件委托 + viewer refs 逻辑。
 *
 * 用法（接入站 4 处编辑之一）：
 *   const contentRef = ref<HTMLElement | null>(null)
 *   const { viewerVisible, viewerUrls, viewerIndex, onContentClick, closeViewer } = useMarkdownImageZoom(contentRef)
 *   模板容器 div 加 `ref="contentRef" @click="onContentClick"`，
 *   模板尾部贴 `<el-image-viewer v-if="viewerVisible" :url-list="viewerUrls" :initial-index="viewerIndex" :z-index="3000" hide-on-click-modal teleported @close="closeViewer" />`。
 *
 * 设计要点：
 * - contentRef 由接入站持有（评论/博客/章节阅读等其它逻辑如折叠量高度、TOC scroll-spy 已复用该 ref），本 composable 只读用它查 <img>，
 *   与同 ref 上其它逻辑（h2/h3/h4 查询、scrollHeight 量高）正交不冲突——onContentClick 只 querySelectorAll('img') 并在 IMG 点击源触发，非 IMG 早退。
 * - 画廊图集为同容器内全部 <img> 的 currentSrc||src（正文出现序），让画廊切看本页其它配图；空 src（data: 损坏等）跳过避免空白页。
 * - 漏赋坑修：viewerUrls.value 必须在开 viewerVisible 前同步回填——el-image-viewer 是 v-if 挂载即读 url-list，
 *   漏赋会让 url-list 恒 [] → currentImg=urlList[active]=undefined → 画布 <img> src 空 → 放大后一片空白（KhComment 早期 bug，擗出时一并守住）。
 *   先同步赋 viewerUrls + viewerIndex，再 await nextTick 让 reactive diff 生效，最后开 viewerVisible。
 *
 * 不含：本 composable 只管「点击放大」，不管上传（上传走 useImageInsert）、不管折叠限高（评论折叠是 KhComment 专属，不搬）。
 */
import { nextTick, ref } from 'vue'
import type { Ref } from 'vue'

export function useMarkdownImageZoom(contentRef: Ref<HTMLElement | null>) {
  /** el-image-viewer 可见性 */
  const viewerVisible = ref(false)
  /** el-image-viewer 画廊图集（同容器内全部 <img> src，正文出现序） */
  const viewerUrls = ref<string[]>([])
  /** el-image-viewer 初始展示索引（被点击图在图集中的位置） */
  const viewerIndex = ref(0)

  /**
   * 点击容器委托判定：源是 <img>（v-md-preview 渲染出的配图）才放大。
   * 收集容器内所有 img 的 currentSrc||src（正文出现序）组图集，记被点图索引，
   * 让用户在画廊里切看本容器其它配图。空 src 跳过避免画廊空白页。
   * 用 currentSrc 取最终解析地址（响应式 <picture> 也稳），兜底回退 src attribute。
   */
  const onContentClick = async (e: MouseEvent) => {
    const target = e.target as HTMLElement | null
    if (!target || target.tagName !== 'IMG') return
    const root = contentRef.value
    if (!root) return
    const imgs = Array.from(root.querySelectorAll<HTMLImageElement>('img'))
    if (imgs.length === 0) return
    const urls: string[] = []
    let clickedIdx = -1
    const clickedSrc = (target as HTMLImageElement).currentSrc || (target as HTMLImageElement).src || ''
    for (const img of imgs) {
      const src = img.currentSrc || img.src || ''
      if (!src) continue
      if (src === clickedSrc && clickedIdx === -1) {
        clickedIdx = urls.length
      }
      urls.push(src)
    }
    if (urls.length === 0) return
    // 先回填画廊图集与初始索引（同步赋值确保 el-image-viewer v-if 挂载即拿到正确 url-list），
    // 再等 nextTick 让响应式 diff 生效，最后开 viewerVisible：漏赋 viewerUrls 致画廊 url-list 恒 []
    // → currentImg=urlList[active]=undefined → 画布 <img> src 空 → 放大后一片空白（已修，擗出时守住该坑）。
    viewerUrls.value = urls
    viewerIndex.value = clickedIdx === -1 ? 0 : clickedIdx
    await nextTick()
    viewerVisible.value = true
  }

  /** 关闭画廊：el-image-viewer @close 回调。teleport 的 viewer 卸载由 v-if 收。 */
  const closeViewer = () => {
    viewerVisible.value = false
  }

  return { viewerVisible, viewerUrls, viewerIndex, onContentClick, closeViewer }
}