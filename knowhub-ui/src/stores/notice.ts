/**
 * 公告 store —— knowhub 前台当前登录用户的通知状态
 * ------------------------------------------------------------------
 * 参考后台 rookie-ui 的 stores/notice.ts，前台版从简：
 * - 拉取 /sys/notice/my 列表 + 未读计数 + 标记已读
 * - 不接确认通知（needConfirm）弹窗交互（前台公告偏弱交互，按需再加）
 * - 未登录不主动拉取（公开页不触发，守卫拦截需求登录态后再拉）
 */
import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getMyNoticesApi, markAsReadApi } from '@/api/system/notice'
import type { SysNoticeRecord } from '@/types/api/notice'

export const useNoticeStore = defineStore('notice', () => {
  const myNotices = ref<SysNoticeRecord[]>([])
  /** 是否已完成首次拉取，避免重复请求（与后台一致用 loaded 而非 initialized） */
  const loaded = ref(false)

  /**
   * 未读通知数量，驱动顶栏铃铛徽标。
   * 后端 hasRead=true 表示已读，这里取反统计未读。
   */
  const unreadCount = computed(() => myNotices.value.filter((item) => !item.hasRead).length)

  /**
   * 拉取当前用户的通知列表并写入 store。
   * - `force`：是否强制重新拉取，忽略已加载标记。
   * 失败时不抛错给上层，前台公告属锦上添花，不应阻塞页面正常使用。
   */
  const fetchMyNotices = async (force = false) => {
    if (loaded.value && !force) {
      return
    }

    try {
      const result = await getMyNoticesApi()
      myNotices.value = result.data
      loaded.value = true
    } catch (error) {
      // http 工具已弹错误提示；前台公告拉取失败不应阻断页面流程
      console.error('fetch my notices failed', error)
    }
  }

  /**
   * 按通知主键从已加载列表中取单条，供详情展示复用。
   */
  const getNoticeById = (noticeId: number): SysNoticeRecord | null =>
    myNotices.value.find((item) => Number(item.noticeId) === noticeId) ?? null

  /**
   * 标记指定通知为已读。乐观更新本地 hasRead=true 避免等待刷新；
   * 已读则跳过请求。接口失败时由下次拉取纠正。
   */
  const markAsRead = async (noticeId: number) => {
    const target = getNoticeById(noticeId)

    if (!target || target.hasRead) {
      return
    }

    target.hasRead = true
    await markAsReadApi(noticeId)
  }

  /**
   * 退出登录或登录态失效时清空通知状态，避免残留旧账号数据。
   */
  const resetNoticeState = () => {
    myNotices.value = []
    loaded.value = false
  }

  return {
    myNotices,
    loaded,
    unreadCount,
    fetchMyNotices,
    getNoticeById,
    markAsRead,
    resetNoticeState,
  }
})