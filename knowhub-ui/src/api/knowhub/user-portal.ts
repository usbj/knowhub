/**
 * 文件作用：
 * 前台用户公开主页接口（/portal/user/{userId}，公开免登录，后端 /portal/** permitAll）。
 * <p>
 * 供用户主页横幅展示用户公开信息（点击评论区用户名 / 作者名跳转目标）。
 * 仅输出 userId/nickName/avatar/username/sex/createTime（不含 phoneNumber 等敏感字段，
 * 后端 UserPortalVo 已做隐私裁剪 + SQL select 列限定双重锁）。
 * 用户不存在或已停用/删除：后端抛 ServiceException(404) → http util 统一 ElMessage 提示。
 */
import { get } from '@/utils/http'
import type { ApiResult } from '@/types/api/common'
import type { UserPublicRecord } from '@/types/api/knowhub/user-portal'

/** 按 userId 查用户公开主页信息（游客可读，隐藏手机号等敏感字段） */
export const getUserPublicApi = (userId: number) =>
  get<ApiResult<UserPublicRecord>>(`/portal/user/${userId}`)

/** 类型再导出，供页面直接用 */
export type { UserPublicRecord }
