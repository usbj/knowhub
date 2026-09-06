/**
 * 文件作用：
 * 计算 sticky 侧栏的"底部预留量"——给固定在视口顶部的 sticky 卡做底部边界收缩用，
 * 防止 sticky 容器末端（grid row 末尾/main 底部）滚入视口时把 sticky 元素上推、钻入 header 被遮。
 *
 * 核心思路：sticky aside 的 sticky 父容器一旦末端进入视口底部（容器 bottom < vh），sticky 元素会被
 * 浏览器推贴容器末端向上滚，此时 aside 顶部不再钉在 top=header 线，反而向上推到 viewport 顶（钻 header）。
 * 解决：当容器 bottom 进入视口多少 px，就让 aside max-height 同等缩短（即 sticky_bottom = vh - container.bottom，
 * 仅当为正时），aside 底始终贴容器末端，容器末端没到视口底时 sticky_bottom=0 不收缩，正常钉 top=header。
 *
 * 用法：
 *   const stickyBottom = useStickyBottom('.dr__layout')   // ref<number>
 *   <aside :style="{ '--kh-sticky-bottom': stickyBottom + 'px' }" />
 *   .aside { max-height: calc(100vh - var(--kh-header-height) - Xpx - var(--kh-sticky-bottom, 0px)); }
 *
 * 实现：IntersectionObserver + scroll/resize 兜底，回调里对 sticky 容器 getBoundingClientRect，
 *   sticky_bottom = max(0, vh - rect.bottom)（rect.bottom 即容器末端 y，小于 vh 时表示末端已进入视口）。
 *   同时取 footer visibleHeight 作 max（footer 在容器之后，footer 进视口也要收缩避免与其叠盖——以
 *   footer self rect 计算更准）。两者取大。
 */
import { onBeforeUnmount, onMounted, ref } from 'vue'

const FOOTER_SELECTOR = '.kh-footer'

export const useStickyBottom = (containerSelector: string) => {
  const stickyBottom = ref(0)
  let containerEl: HTMLElement | null = null
  let footerEl: HTMLElement | null = null

  const recompute = () => {
    const vh = window.innerHeight
    let value = 0
    // 惰性补绑：sticky 父容器带 v-else（docLoading 期未挂载），onMounted 时 querySelector 拿到 null，
    // layout 渲染后必须在首个 scroll/resize 回调里重查补绑，否则只靠 footer 兜底、layout 末端上推不收缩 → aside 钻 header。
    if (!containerEl) containerEl = document.querySelector<HTMLElement>(containerSelector)
    if (!footerEl) footerEl = document.querySelector<HTMLElement>(FOOTER_SELECTOR)
    // 主：sticky 父容器末端进视口时收缩（sticky 上推触发点）
    if (containerEl) {
      const rect = containerEl.getBoundingClientRect()
      const inset = vh - rect.bottom
      if (inset > 0) value = Math.max(value, inset)
    }
    // 兜底：footer 进视口也要收缩避免叠盖（取大）
    if (footerEl) {
      const rect = footerEl.getBoundingClientRect()
      const fvis = Math.max(0, Math.min(rect.height, vh - rect.top))
      if (fvis > value) value = fvis
    }
    stickyBottom.value = value
  }

  onMounted(() => {
    containerEl = document.querySelector<HTMLElement>(containerSelector)
    footerEl = document.querySelector<HTMLElement>(FOOTER_SELECTOR)
    window.addEventListener('scroll', recompute, { passive: true })
    window.addEventListener('resize', recompute)
    recompute()
  })

  onBeforeUnmount(() => {
    window.removeEventListener('scroll', recompute)
    window.removeEventListener('resize', recompute)
  })

  return stickyBottom
}