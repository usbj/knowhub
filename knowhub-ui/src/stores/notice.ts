/**
 * 公告 store —— knowhub 前台当前登录用户的通知状态
 * ------------------------------------------------------------------
 * 参考后台 rookie-ui 的 stores/notice.ts，前台版对齐下拉滚动懒加载：
 * - 拉取 /sys/notice/my 首页列表 + 未读计数 + 标记已读 + 滚动到底加载下一页
 * - 未读计数走独立接口 /sys/notice/unread-count（分页列表只含已加载页，本地 filter 不准）
 * - 不接确认通知（needConfirm）弹窗交互（前台公告偏弱交互，按需再加）
 * - 未登录不主动拉取（公开页不触发，守卫拦截需求登录态后再拉）
 */
import { ref } from 'vue'
import { defineStore } from 'pinia'
import {
  getMyNoticesPageApi,
  getUnreadCountApi,
  markAsReadApi,
  markAllAsReadApi,
  getMyNoticeCountsByTypeApi,
} from '@/api/system/notice'
import type { SysNoticeRecord } from '@/types/api/notice'
import type { NoticePortalRecord } from '@/types/api/notice-portal'

/**
 * 通知详情弹窗可承载的记录类型。
 * - SysNoticeRecord：通知下拉来源（/sys/notice/my，含 hasRead，无 needConfirm/hasConfirmed）。
 * - NoticePortalRecord：公开公告列表来源（/portal/notice/list，含 needConfirm/hasConfirmed，无 hasRead）。
 * 两者展示字段 title/content/noticeType/isTop/createBy/publishTime 兼容；确认按钮仅后者有数据支撑。
 */
export type NoticeDetailRecord = SysNoticeRecord | NoticePortalRecord

/** 每次懒加载拉取的条数，与后端默认分页大小一致（与后台 rookie-ui 同口径） */
const PAGE_SIZE = 10

export const useNoticeStore = defineStore('notice', () => {
  /** 已加载的通知数组（分页累积，下拉滚动到底追加下一页） */
  const myNotices = ref<SysNoticeRecord[]>([])
  /** 是否已完成首次拉取，避免重复请求（与后台一致用 loaded 而非 initialized） */
  const loaded = ref(false)
  /** 是否还有下一页（已加载条数 < total）；驱动底部状态行与 loadMore 守卫 */
  const hasMore = ref(false)
  /** 是否正在加载下一页；防滚动并发重复请求 */
  const loadingMore = ref(false)

  /**
   * 未读通知数量，驱动顶栏铃铛徽标。
   * 走独立接口 /sys/notice/unread-count 而非本地 filter：分页列表只含已加载页，
   * 本地 filter 未读会漏掉未加载页的未读，徽标长期不准。
   * 已读标记后本地乐观 -1 保持即时反馈，下次拉取再以服务端为准纠正。
   */
  const unreadCount = ref(0)
  /**
   * 上一次轮询的未读计数快照，用于检测徽标增长。
   * 新通知到达时 unreadCount 会变大，此时须 force 重拉 myNotices 首页，
   * 否则下拉用 loaded 守卫命中的旧列表不含新通知——徽标亮但下拉空（bug 1 根因）。
   */
  const prevUnreadCount = ref(0)

  /**
   * 拉取未读计数（独立于列表，列表只含已加载页，filter 不准）。失败静默，徽标失败不应阻断页面。
   * 检测到计数较上次增长时回调通知调用方，由调用方决定是否 force 重拉列表
   * （下拉正打开时不重置分页，关闭时才重拉首页，避免把用户滚到第 3 页的状态打回首页）。
   */
  const fetchUnreadCount = async () => {
    let increased = false
    try {
      const result = await getUnreadCountApi()
      const next = result.data ?? 0
      increased = next > prevUnreadCount.value
      prevUnreadCount.value = next
      unreadCount.value = next
    } catch (error) {
      console.error('fetch unread count failed', error)
    }
    return increased
  }

  /**
   * 各通知类型的真实总数（{ ALL, NOTICE, NOTIFY, REMIND }），独立于分页列表的 total。
   * 解决分页列表只含已加载页、本地 filter 算角标不准的 bug：/notices 分类 tab 与 profile
   * 消息子 tab 的四个角标统一从这里取真实总数，不随翻页/切 tab 当前页数据量变。
   * ALL 由后端聚合接口求和给出（各类型相加），其余键为该类型可见通知真实总数。
   */
  const typeCounts = ref<Record<string, number>>({ ALL: 0, NOTICE: 0, NOTIFY: 0, REMIND: 0 })

  /** 拉取各类型真实总数（/sys/notice/my-counts 聚合接口，需登录态）。失败静默，角标失败不阻断页面。 */
  const fetchTypeCounts = async () => {
    try {
      const result = await getMyNoticeCountsByTypeApi()
      // 合并而非整体替换：后端只返实际存在的类型键，缺省键回退 0，保证四个角标恒有值
      typeCounts.value = { ALL: 0, NOTICE: 0, NOTIFY: 0, REMIND: 0, ...result.data }
    } catch (error) {
      console.error('fetch notice type counts failed', error)
    }
  }

  /**
   * 拉取当前用户通知的第一页并重置累积列表，同时刷新未读数与各类型真实总数。
   * - `force`：是否强制重新拉取，忽略已加载标记（下拉隐藏后再次打开可 force 刷新）。
   * 后端 /sys/notice/my 走 PageHelper 归一化，getPage 直接返回 records/total。
   * 失败时不抛错给上层，前台公告属锦上添花，不应阻塞页面正常使用。
   */
  const fetchMyNotices = async (force = false) => {
    if (loaded.value && !force) {
      return
    }

    try {
      const result = await getMyNoticesPageApi({ pageNum: 1, pageSize: PAGE_SIZE })
      myNotices.value = result.records ?? []
      hasMore.value = (result.records?.length ?? 0) < (result.total ?? 0)
      loaded.value = true
      // 同步刷新未读计数 + 各类型真实总数（列表已拉到首页，顺带把准确徽标与分类角标也拉回来，
      // 保证 /notices、profile 消息子 tab 角标与列表同源同步）
      await fetchUnreadCount()
      await fetchTypeCounts()
    } catch (error) {
      // http 工具已弹错误提示；前台公告拉取失败不应阻断页面流程
      console.error('fetch my notices failed', error)
    }
  }

  /**
   * 滚动到底部时拉取下一页并追加到列表尾部（懒加载）。
   * 已到最后或正在加载时直接返回，避免并发重复请求。
   * 下一页页码 = 已加载条数 / PAGE_SIZE + 1（首页已占 1~PAGE_SIZE，第 2 页从 PAGE_SIZE+1 起）。
   */
  const loadMoreNotices = async () => {
    if (!hasMore.value || loadingMore.value) {
      return
    }

    loadingMore.value = true
    try {
      const pageNum = Math.floor(myNotices.value.length / PAGE_SIZE) + 1
      const result = await getMyNoticesPageApi({ pageNum, pageSize: PAGE_SIZE })
      myNotices.value.push(...(result.records ?? []))
      hasMore.value = myNotices.value.length < (result.total ?? 0)
    } catch (error) {
      // 补页失败静默，下次滚动/重新打开下拉会重试
      console.error('load more notices failed', error)
    } finally {
      loadingMore.value = false
    }
  }

  /**
   * 按通知主键从已加载列表中取单条，供详情展示复用。
   */
  const getNoticeById = (noticeId: number): SysNoticeRecord | null =>
    myNotices.value.find((item) => Number(item.noticeId) === noticeId) ?? null

  /**
   * 标记指定通知为已读。乐观更新本地 hasRead=true 并徽标 -1 避免等待刷新；
   * 已读则跳过请求。接口失败时由下次拉取纠正。
   */
  const markAsRead = async (noticeId: number) => {
    const target = getNoticeById(noticeId)

    if (!target || target.hasRead) {
      return
    }

    target.hasRead = true
    if (unreadCount.value > 0) {
      unreadCount.value -= 1
    }
    await markAsReadApi(noticeId)
  }

  /**
   * 全部已读：乐观把已加载列表全部 hasRead=true + 徽标清零，再调后端批量接口。
   * 后端 INSERT...SELECT 覆盖所有未读（含下拉未加载页），故徽标直接置 0 而非本地 filter 计数
   * （本地只含已加载页，未加载页未读本地看不见，但后端已一并标读，徽标清 0 准确）。
   * 接口失败时由下次拉取纠正。无未读时后端插 0 行仍成功，重复点击无副作用。
   */
  const markAllAsRead = async () => {
    myNotices.value.forEach((n) => {
      n.hasRead = true
    })
    unreadCount.value = 0
    await markAllAsReadApi()
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
    hasMore.value = false
    loadingMore.value = false
    unreadCount.value = 0
    prevUnreadCount.value = 0
    typeCounts.value = { ALL: 0, NOTICE: 0, NOTIFY: 0, REMIND: 0 }
    detailVisible.value = false
    currentNotice.value = null
  }

  return {
    myNotices,
    loaded,
    hasMore,
    loadingMore,
    unreadCount,
    typeCounts,
    fetchMyNotices,
    loadMoreNotices,
    fetchUnreadCount,
    fetchTypeCounts,
    getNoticeById,
    markAsRead,
    markAllAsRead,
    resetNoticeState,
    detailVisible,
    currentNotice,
    openDetail,
    closeDetail,
  }
})