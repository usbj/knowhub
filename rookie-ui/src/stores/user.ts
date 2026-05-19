import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getPersonalProfileApi } from '@/api/system/user'
import { useDictStore } from '@/stores/dict'
import type { LoginResponseData } from '@/types/api/system/login'
import type { SysUserProfile } from '@/types/api/system/user'

export const USER_TOKEN_STORAGE_KEY = 'rookie-user-token'
export const USER_INFO_STORAGE_KEY = 'rookie-user-info'

/**
 * 读取本地缓存的用户信息。
 * 这里单独拆方法，是为了让初始化逻辑更清楚，也便于后续替换成更完整的鉴权恢复逻辑。
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
   * 这里存储 /person 返回的真实个人资料，用于头导航与个人中心复用。
   */
  const userInfo = ref<SysUserProfile | null>(readStoredUserInfo())

  /**
   * 是否处于已登录状态。
   * 目前以 token 是否存在作为最基础的登录判断条件。
   */
  const isAuthenticated = computed(() => Boolean(token.value))

  /**
   * 顶部导航展示名。
   * 优先使用昵称，没有昵称时退回账号名。
   */
  const displayName = computed(() => userInfo.value?.nickName || userInfo.value?.username || '未登录')

  /**
   * 给头导航和个人中心提供一份更轻的展示数据。
   * 这样页面层不需要反复处理原始接口结构。
   */
  const profileSummary = computed(() => {
    if (!userInfo.value) {
      return null
    }

    return {
      userId: userInfo.value.userId,
      username: userInfo.value.username,
      nickName: userInfo.value.nickName,
      phoneNumber: userInfo.value.phoneNumber,
      sex: userInfo.value.sex,
      status: userInfo.value.status,
      createTime: userInfo.value.createTime,
      roleNames: userInfo.value.userRole?.map((role) => role.roleName).filter(Boolean) ?? [],
    }
  })

  /**
   * 登录成功后先写入 token。
   * 个人资料改为通过 /person 拉取，避免继续依赖本地 mock 数据。
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

  const fetchUserProfile = async () => {
    const result = await getPersonalProfileApi()
    setUserProfile(result.data)
    return result.data
  }

  /**
   * 清空当前登录态。
   * 退出登录时需要同时移除 token 与用户信息，避免界面残留旧数据。
   */
  const logout = () => {
    const dictStore = useDictStore()

    token.value = ''
    userInfo.value = null

    localStorage.removeItem(USER_TOKEN_STORAGE_KEY)
    localStorage.removeItem(USER_INFO_STORAGE_KEY)
    dictStore.clearDictCache()
  }

  return {
    token,
    userInfo,
    isAuthenticated,
    displayName,
    profileSummary,
    setLoginSession,
    setUserProfile,
    fetchUserProfile,
    logout,
  }
})
