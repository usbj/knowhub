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
import type { NoticePortalRecord } from '@/types/api/notice-portal'

/**
 * 通知详情弹窗可承载的记录类型。
 * - SysNoticeRecord：通知下拉来源（/sys/notice/my，含 hasRead，无 needConfirm/hasConfirmed）。
 * - NoticePortalRecord：公开公告列表来源（/portal/notice/list，含 needConfirm/hasConfirmed，无 hasRead）。
 * 两者展示字段 title/content/noticeType/isTop/createBy/publishTime 兼容；确认按钮仅后者有数据支撑。
 */
export type NoticeDetailRecord = SysNoticeRecord | NoticePortalRecord

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

  // ---- 全局通知详情弹窗态（顶栏下拉 + 公告列表页共用 <KhNoticeDetailDialog>）----
  /** 弹窗可见性：v-model:visible 双向绑定挂载在 AppLayout 的全局组件 */
  const detailVisible = ref(false)
  /** 当前展开详情的通知（下拉源 SysNoticeRecord 或列表源 NoticePortalRecord） */
  const currentNotice = ref<NoticeDetailRecord | null>(null)

  /**
   * 打开通知详情弹窗。任意来源（顶栏下拉 / 公告列表卡）统一入口。
   * 调用方先自行 markAsRead（若需要），再 openDetail(notice)。
   */
  const openDetail = (notice: NoticeDetailRecord) => {
    currentNotice.value = notice
    detailVisible.value = true
  }

  /** 关闭弹窗：不动 currentNotice（避免关闭瞬态闪空），下次 openDetail 自然覆盖。 */
  const closeDetail = () => {
    detailVisible.value = false
  }

  /**
   * 退出登录或登录态失效时清空通知状态，避免残留旧账号数据。
   */
  const resetNoticeState = () => {
    myNotices.value = []
    loaded.value = false
    detailVisible.value = false
    currentNotice.value = null
  }

  return {
    myNotices,
    loaded,
    unreadCount,
    fetchMyNotices,
    getNoticeById,
    markAsRead,
    resetNoticeState,
    detailVisible,
    currentNotice,
    openDetail,
    closeDetail,
  }
})