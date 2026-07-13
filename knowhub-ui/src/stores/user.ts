/**
 * 当前登录用户 store —— knowhub 前台鉴权态
 * ------------------------------------------------------------------
 * 参考后台 rookie-ui 的 stores/user.ts，前台版从简：
 * - 仅维护 token + userInfo（GET /person 返回的个人资料）
 * - 不耦合字典 / 系统配置缓存（前台暂未引入这些全局缓存）
 * - localStorage key 统一 knowhub- 前缀，与后台 rookie- 区分，端口虽不同但语义更清晰
 */
import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getPersonalProfileApi } from '@/api/system/user'
import { useNoticeStore } from '@/stores/notice'
import { useDictStore } from '@/stores/dict'
import { useSysConfigStore } from '@/stores/system-config'
import type { LoginResponseData } from '@/types/api/login'
import type { SysUserProfile } from '@/types/api/user'

export const USER_TOKEN_STORAGE_KEY = 'knowhub-user-token'
export const USER_INFO_STORAGE_KEY = 'knowhub-user-info'

/**
 * 读取本地缓存的用户信息。
 * 单独拆方法让初始化逻辑更清楚，便于后续替换更完整的鉴权恢复逻辑。
 */
const readStoredUserInfo = (): SysUserProfile | null => {
  const rawUserInfo = localStorage.getItem(USER_INFO_STORAGE_KEY)

  if (!rawUserInfo) {
    return null
  }

  try {
    return JSON.parse(rawUserInfo) as SysUserProfile
  } catch {
    localStorage.removeItem(USER_INFO_STORAGE_KEY)
    return null
  }
}

export const useUserStore = defineStore('user', () => {
  /**
   * 当前登录 token。
   * 后端 /login 返回的 data 目前就是这个字符串。
   */
  const token = ref<string>(localStorage.getItem(USER_TOKEN_STORAGE_KEY) || '')

  /**
   * 当前登录用户信息。
   * 这里存储 /person 返回的真实个人资料，用于顶导航与个人中心复用。
   */
  const userInfo = ref<SysUserProfile | null>(readStoredUserInfo())

  /**
   * 是否处于已登录状态。
   * 目前以 token 是否存在作为最基础的登录判断条件。
   */
  const isAuthenticated = computed(() => Boolean(token.value))

  /**
   * 顶部导航展示名。
   * 优先使用昵称，没有昵称时退回账号名，再退回兜底占位。
   * 与后台一致，便于顶栏和个人中心复用。
   */
  const displayName = computed(() => userInfo.value?.nickName || userInfo.value?.username || '')

  /**
   * 顶栏头像首字。
   * 优先取昵称首字，再取账号首字；空则落到占位字。
   */
  const avatarText = computed(() => {
    const name = userInfo.value?.nickName || userInfo.value?.username || ''
    return name.charAt(0) || '客'
  })

  /**
   * 登录成功后先写入 token。
   * 个人资料随后由调用方（登录页或路由守卫）触发 fetchUserProfile 拉取。
   */
  const setLoginSession = (loginToken: LoginResponseData) => {
    token.value = loginToken
    localStorage.setItem(USER_TOKEN_STORAGE_KEY, loginToken)
  }

  /**
   * 使用接口返回的个人资料更新 store 和本地缓存。
   */
  const setUserProfile = (profile: SysUserProfile) => {
    userInfo.value = profile
    localStorage.setItem(USER_INFO_STORAGE_KEY, JSON.stringify(profile))
  }

  /**
   * 拉取当前登录用户个人资料（GET /person）。
   * 登录后或刷新页面恢复登录态时调用。
   */
  const fetchUserProfile = async () => {
    const result = await getPersonalProfileApi()
    setUserProfile(result.data)
    return result.data
  }

  /**
   * 清空当前登录态。
   * 退出登录时同时移除 token 与用户信息，避免界面残留旧数据。
   * 同时复位公告 store，避免下个账号看到上游账号的通知。
   * 前台无后端登出接口，纯前端清理。
   */
  const logout = () => {
    const noticeStore = useNoticeStore()
    const dictStore = useDictStore()
    const sysConfigStore = useSysConfigStore()

    token.value = ''
    userInfo.value = null
    localStorage.removeItem(USER_TOKEN_STORAGE_KEY)
    localStorage.removeItem(USER_INFO_STORAGE_KEY)
    noticeStore.resetNoticeState()
    dictStore.clearDictCache()
    sysConfigStore.clearSysConfigCache()
  }

  return {
    token,
    userInfo,
    isAuthenticated,
    displayName,
    avatarText,
    setLoginSession,
    setUserProfile,
    fetchUserProfile,
    logout,
  }
})