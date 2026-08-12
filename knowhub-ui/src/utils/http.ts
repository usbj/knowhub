/**
 * 文件作用：
 * 统一封装前台 HTTP 请求实例、基础地址、请求头 Token 注入和通用错误处理，
 * 供所有与后端对接的接口方法复用。
 *
 * 与后台 rookie-ui 的 http.ts 对齐：请求头用 `Token` 直传 JWT（无 Bearer 前缀），
 * 后端 TokenVerifyFilter 从 `Token` 请求头取值；dev 下经 vite /api 代理转发到 localhost:8080。
 * 但前台版从简：不携带字典/系统配置缓存清理逻辑，401 失效统一跳 /login 并带 redirect。
 */
import axios, { AxiosError } from 'axios'
import type { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import { USER_INFO_STORAGE_KEY, USER_TOKEN_STORAGE_KEY } from '@/stores/user'
import type { ApiResult, NormalizedPageResult, RawPageInfoResult } from '@/types/api/common'

const SUCCESS_CODE = 200
const AUTH_EXPIRED_CODE = 401

/**
 * 扩展 AxiosRequestConfig，增加本仓库自定义的请求级标记。
 * - skipAuthRedirect：登录接口本身返回 401（账号密码错误）时，不应触发"登录态失效跳转"，
 *   否则用户在登录页会遭遇无意义的整页刷新；该标记让拦截器只弹错误提示、不跳转。
 */
declare module 'axios' {
  export interface AxiosRequestConfig {
    skipAuthRedirect?: boolean
    /** 静默失败：首页等信息流请求失败时不弹 ElMessage 红条刷屏，由页面以空态兜底 */
    silentError?: boolean
  }
}

/** 防止 401 时多请求并发触发多次登录跳转 */
let isRedirectingToLogin = false

/**
 * 清空本地登录态。401 或主动登出时调用。
 * 这里只负责移除 token 与 userInfo，不依赖任何其他 store 的缓存（前台无字典/系统配置缓存）。
 */
const clearLocalAuthState = () => {
  localStorage.removeItem(USER_TOKEN_STORAGE_KEY)
  localStorage.removeItem(USER_INFO_STORAGE_KEY)
}

/**
 * 计算登录跳转时要回填的 redirect 参数，用于登录后回到原页面。
 * 直接基于当前 pathname+search+hash，前台 BASE_URL 默认 '/'，无需像后台那样抠 BASE_URL 前缀。
 */
const getCurrentRedirectPath = () => {
  const currentLocation = `${window.location.pathname}${window.location.search}${window.location.hash}`
  return currentLocation || '/'
}

/**
 * 统一跳转登录页并带上 redirect 查询参数。
 * 与后台一致：用 window.location.replace 触发整页跳转，避免 SPA 路由残留鉴权态。
 */
const redirectToLogin = () => {
  if (typeof window === 'undefined' || isRedirectingToLogin) {
    return
  }

  isRedirectingToLogin = true
  clearLocalAuthState()

  const loginUrl = new URL('/login', window.location.origin)
  const redirectPath = getCurrentRedirectPath()

  if (redirectPath && redirectPath !== '/login') {
    loginUrl.searchParams.set('redirect', redirectPath)
  }

  window.location.replace(loginUrl.toString())
}

/**
 * 当前开发环境默认通过 /api 代理转发到后端服务，
 * 生产环境可通过 VITE_API_BASE_URL 指定真实接口地址。
 */
const http: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
})

/**
 * 请求发出前自动补充 token。
 * 后端过滤器从 Token 请求头里读取登录态，因此统一在这里注入。
 */
http.interceptors.request.use((config) => {
  const token = localStorage.getItem(USER_TOKEN_STORAGE_KEY)

  if (token) {
    config.headers.Token = token
  }

  return config
})

/**
 * 统一处理后端 Result 结构中的业务错误和网络错误。
 * 成功时仍返回完整响应体，交给各 API 方法按自身类型消费。
 */
http.interceptors.response.use(
  (response: AxiosResponse<ApiResult<unknown>>) => {
    const payload = response.data

    if (typeof payload?.code === 'number' && payload.code !== SUCCESS_CODE) {
      if (payload.code === AUTH_EXPIRED_CODE) {
        ElMessage.error(payload.msg || '登录状态已失效，请重新登录')
        // 登录接口自身的 401（账号密码错误）不应触发跳转，否则登录页被无意义刷新
        if (!response.config?.skipAuthRedirect) {
          redirectToLogin()
        }
        return Promise.reject(new Error(payload.msg || '登录状态已失效'))
      }

      if (!response.config?.silentError) {
        ElMessage.error(payload.msg || '请求失败')
      }
      return Promise.reject(new Error(payload.msg || '请求失败'))
    }

    return response
  },
  (error: AxiosError) => {
    if (error.response?.status === AUTH_EXPIRED_CODE) {
      const msg =
        error.response?.data && typeof error.response.data === 'object' && 'msg' in error.response.data
          ? String((error.response.data as { msg: unknown }).msg)
          : '登录状态已失效，请重新登录'
      ElMessage.error(msg)
      if (!error.config?.skipAuthRedirect) {
        redirectToLogin()
      }
      return Promise.reject(error)
    }

    if (!error.config?.silentError) {
      const message =
        error.response?.data && typeof error.response.data === 'object' && 'msg' in error.response.data
          ? String((error.response.data as { msg: unknown }).msg)
          : error.message || '网络请求异常'

      ElMessage.error(message)
    }
    return Promise.reject(error)
  },
)

/**
 * 统一暴露简洁的请求方法。
 * 直接返回后端响应体 data，避免业务层重复取 response.data。
 */
export const request = async <T>(config: AxiosRequestConfig) => {
  const response = await http.request<T>(config)
  return response.data
}

export const get = <T>(url: string, config?: AxiosRequestConfig) =>
  request<T>({
    ...config,
    method: 'get',
    url,
  })

export const post = <T, D = unknown>(url: string, data?: D, config?: AxiosRequestConfig<D>) =>
  request<T>({
    ...config,
    method: 'post',
    url,
    data,
  })

export const put = <T, D = unknown>(url: string, data?: D, config?: AxiosRequestConfig<D>) =>
  request<T>({
    ...config,
    method: 'put',
    url,
    data,
  })

export const del = <T>(url: string, config?: AxiosRequestConfig) =>
  request<T>({
    ...config,
    method: 'delete',
    url,
  })

/**
 * 把后端 PageInfo 结构转换为前端更易消费的分页结果。
 * 与后台 rookie-ui http.requestPage 行为一致，供列表页统一抄后台写法。
 */
export const requestPage = async <T>(config: AxiosRequestConfig) => {
  const response = await request<ApiResult<RawPageInfoResult<T>>>(config)
  const pageInfo = response.data

  return {
    records: pageInfo.list,
    pageNum: pageInfo.pageNum,
    pageSize: pageInfo.pageSize,
    pages: pageInfo.pages,
    total: pageInfo.total,
  } satisfies NormalizedPageResult<T>
}

/**
 * 发起分页查询请求，直接返回归一化后的分页结果。
 */
export const getPage = <T>(url: string, config?: AxiosRequestConfig) =>
  requestPage<T>({
    ...config,
    method: 'get',
    url,
  })

export default http