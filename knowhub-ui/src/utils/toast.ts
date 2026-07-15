/**
 * 文件作用：
 * 轻量自定义提示 toast —— 不用 ElMessage。
 * 原因：ElMessage 鼠标悬浮会暂停关闭计时（mouseenter clearTimer / mouseleave startTimer，
 * 见 element-plus 源码 message.vue），且无法配置关闭。本 toast 固定时长、不因悬浮暂停，
 * 适合「下载占位 / 未配置链接」这类一次性提示。
 * —— 后端真实提示（鉴权失效、请求失败等）仍走 http.ts 的 ElMessage.error，不复用本工具。
 */
import { createApp, h } from 'vue'

let seed = 0

/** 弹一个一次性提示。duration 到点自动销毁；不响应鼠标悬浮（不暂停计时） */
const toast = (text: string, duration = 1500) => {
  const id = ++seed
  const host = document.createElement('div')
  host.className = 'kh-toast-portal'
  host.dataset.id = String(id)
  document.body.appendChild(host)

  const app = createApp({
    render() {
      return h('div', { class: 'kh-toast' }, [h('span', { class: 'kh-toast__text' }, text)])
    },
  })
  app.mount(host)

  // 触发淡入：下一帧加 is-show
  requestAnimationFrame(() => host.classList.add('is-show'))

  // 到点淡出并卸载
  window.setTimeout(() => {
    host.classList.remove('is-show')
    window.setTimeout(() => {
      app.unmount()
      host.remove()
    }, 200)
  }, duration)
}

export default toast