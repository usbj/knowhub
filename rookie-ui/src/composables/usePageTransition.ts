/**
 * 文件作用：
 * 统一管理整页切换时的顶部进度条状态与推进节奏，
 * 供路由守卫、根页面和主要内容区域共用。
 */
import { ref } from 'vue'

const isPageTransitioning = ref(false)
const progress = ref(0)
const activeTransitionId = ref(0)

let progressTimer: number | null = null
let finishTimer: number | null = null
let hideTimer: number | null = null

/**
 * 清理当前运行中的定时器。
 * 页面切换过程中会重复触发开始与完成逻辑，因此需要统一回收旧定时器，避免状态串线。
 */
const clearTransitionTimers = () => {
  if (progressTimer !== null) {
    window.clearInterval(progressTimer)
    progressTimer = null
  }

  if (finishTimer !== null) {
    window.clearTimeout(finishTimer)
    finishTimer = null
  }

  if (hideTimer !== null) {
    window.clearTimeout(hideTimer)
    hideTimer = null
  }
}

/**
 * 启动整页切换进度条。
 * 进度条会先平滑推进到接近完成的位置，真正结束要等页面内容准备好后再补到 100%。
 */
export const startPageTransition = () => {
  activeTransitionId.value += 1
  const currentTransitionId = activeTransitionId.value

  clearTransitionTimers()
  isPageTransitioning.value = true
  progress.value = 0

  progressTimer = window.setInterval(() => {
    if (currentTransitionId !== activeTransitionId.value) {
      return
    }

    /**
     * 进度条最多先推进到 92%，
     * 如果页面还没准备好，就停在末段等待，避免假装“已经完成”。
     */
    if (progress.value < 70) {
      progress.value += 8
      return
    }

    if (progress.value < 86) {
      progress.value += 2.5
      return
    }

    if (progress.value < 92) {
      progress.value += 0.8
    }
  }, 40)
}

/**
 * 在页面内容准备好后结束进度条。
 * extraDuration 用来和内容动画对齐，让顶部进度与视图过渡保持同一节奏。
 */
export const finishPageTransition = (extraDuration = 0) => {
  const currentTransitionId = activeTransitionId.value

  if (!isPageTransitioning.value) {
    return
  }

  if (finishTimer !== null) {
    window.clearTimeout(finishTimer)
  }

  finishTimer = window.setTimeout(() => {
    if (currentTransitionId !== activeTransitionId.value) {
      return
    }

    if (progressTimer !== null) {
      window.clearInterval(progressTimer)
      progressTimer = null
    }

    progress.value = 100

    hideTimer = window.setTimeout(() => {
      if (currentTransitionId !== activeTransitionId.value) {
        return
      }

      isPageTransitioning.value = false
      progress.value = 0
    }, 180)
  }, extraDuration)
}

/**
 * 统一暴露页面切换状态。
 * 这里使用模块级单例，方便根组件、路由和布局组件共用同一份进度状态。
 */
export const usePageTransition = () => ({
  isPageTransitioning,
  progress,
  startPageTransition,
  finishPageTransition,
})
