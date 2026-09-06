/**
 * 文件作用：
 * 当前登录用户侧的通知接口，前台版接"我的通知"分页懒加载 + 未读计数 + 标记已读。
 * （后台另有通知主体/分组/发布撤回/确认等管理接口，前台暂不需要，按需再补。）
 */
import { get, getPage, post } from '@/utils/http'
import type { ApiResult, PageQueryParams, RawPageInfoResult } from '@/types/api/common'
import type { SysNoticeRecord } from '@/types/api/notice'

/** /sys/notice/my 分页入参：标准分页 + 可选通知类型过滤（供 /notices 页类型 tab 复用） */
export interface MyNoticePageQuery extends PageQueryParams {
  /** 通知类型字典 code（NOTICE/NOTIFY/REMIND），空表示全部 */
  noticeType?: string
}

/**
 * 分页获取当前登录用户可见的通知列表（含已读/已确认状态与完整正文），
 * 供顶栏铃铛下拉滚动懒加载使用，请求层完成分页结果归一化（records/total）。
 * 后端从 SecurityContextHolder 取当前用户，无需传 userId。
 * 与后台 rookie-ui 的 getMyNoticesPageApi 同口径（/sys/notice/my，PageHelper 归一化）。
 * noticeType 可选：不传返回全部可见通知（含审核/评论/邀请/协作等私发），传则按类型过滤。
 */
export const getMyNoticesPageApi = (params: MyNoticePageQuery) =>
  getPage<SysNoticeRecord>('/sys/notice/my', { params })

/**
 * 获取当前登录用户可见的通知列表（含已读/已确认状态与完整正文）—— 旧版不分页。
 * 保留兼容：少数非下拉场景（如个人中心 message tab 历史调用）仍走首页全量口径。
 * 后端 /sys/notice/my 返回 PageInfo<SysNoticeVo>（分页包装，list 字段才是数组），
 * 故泛型填 ApiResult<RawPageInfoResult<SysNoticeRecord>>，调用方取 result.data.list 作为通知数组。
 */
export const getMyNoticesApi = () =>
  get<ApiResult<RawPageInfoResult<SysNoticeRecord>>>('/sys/notice/my')

/**
 * 获取当前用户未读通知数（铃铛徽标专用，独立于分页列表——分页列表只含首页，本地 filter 不准）。
 * 后端返回 Result<Long>，store 取 result.data 作为未读计数。
 */
export const getUnreadCountApi = () => get<ApiResult<number>>('/sys/notice/unread-count')

/**
 * 标记指定通知为当前用户已读。
 * - `noticeId`：通知主键。
 */
export const markAsReadApi = (noticeId: number) =>
  post<ApiResult<boolean>>(`/sys/notice/read/${noticeId}`)

/**
 * 全部已读：批量标记当前用户所有可见且未读的通知为已读。
 * 后端 INSERT...SELECT 一次性覆盖所有未读（含懒加载下拉未加载页），徽标随之清零。
 * 无未读时后端插入 0 行仍返回成功。
 */
export const markAllAsReadApi = () => post<ApiResult<boolean>>('/sys/notice/read-all')

/**
 * 按通知类型聚合统计当前用户可见通知数（/sys/notice/my-counts）。
 * 返回 { ALL, NOTICE, NOTIFY, REMIND } 各真实总数（ALL=各类型求和）。
 * 供前台 /notices 分类 tab 与 profile 消息子 tab 显示真实总数角标，
 * 不随分页当前页数据量变（分页列表只含已加载页，本地 filter 角标不准）。
 * 后端从 SecurityContextHolder 取当前用户，需登录态。
 */
export const getMyNoticeCountsByTypeApi = () =>
  get<ApiResult<Record<string, number>>>('/sys/notice/my-counts')