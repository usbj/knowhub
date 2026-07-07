/**
 * 文件作用：
 * 系统设置（system_config）Pinia store，对标字典 store（stores/dict.ts）。
 * 登录后启动时一次性全量加载所有启用设置项到内存，供页面/组件按 `configKey` 读取。
 * 关键能力：
 * - `configMap`：按 `configKey` 存放 `SysConfigRecord` 的内存缓存。
 * - `initializeSysConfigs(force)`：登录后调 `getSysConfigAllApi` 拉全部启用项写入 configMap。
 * - `getSysConfig(key)`：按 key 读取单条设置记录，未命中返回 null。
 * - `clearSysConfigCache()`：退出登录时清空内存与本地缓存。
 * 与字典的区别：系统设置体量小、启动加载一次即可，不需要按 key 懒加载。
 */
import { ref } from 'vue'
import { defineStore } from 'pinia'
import { getSysConfigAllApi } from '@/api/system/system-config'
import type { SysConfigRecord } from '@/types/api/system/system-config'

export const SYS_CONFIG_CACHE_STORAGE_KEY = 'rookie-system-config-cache'

type SysConfigMap = Record<string, SysConfigRecord>

/**
 * 读取本地缓存的系统设置数据。
 * 优先恢复最近一次可用的设置内容，避免刷新后页面先空一拍。
 */
const readStoredSysConfigCache = (): SysConfigMap => {
  const rawCache = localStorage.getItem(SYS_CONFIG_CACHE_STORAGE_KEY)

  if (!rawCache) {
    return {}
  }

  try {
    return JSON.parse(rawCache) as SysConfigMap
  } catch {
    localStorage.removeItem(SYS_CONFIG_CACHE_STORAGE_KEY)
    return {}
  }
}

export const useSysConfigStore = defineStore('system-config', () => {
  /**
   * 当前前端已缓存的所有系统设置，按 `configKey` 索引存储。
   */
  const configMap = ref<SysConfigMap>(readStoredSysConfigCache())

  /**
   * 标记当前会话是否已经完成过一次系统设置预加载。
   * 即使本地有缓存，也会在登录后的首轮导航中再刷新一次。
   */
  const initialized = ref(false)
  const loading = ref(false)

  const persistSysConfigCache = () => {
    localStorage.setItem(SYS_CONFIG_CACHE_STORAGE_KEY, JSON.stringify(configMap.value))
  }

  /**
   * 方法效果：
   * 登录后预加载全部启用系统设置项，供全局页面直接按 key 消费。
   * 参数：
   * - `force`：是否强制重新拉取全部设置项。
   * 返回值：
   * - Promise<void>，在全部拉取完成后结束。
   */
  const initializeSysConfigs = async (force = false) => {
    if (initialized.value && !force) {
      return
    }

    loading.value = true

    try {
      const result = await getSysConfigAllApi()
      const records = result.data ?? []

      const nextMap: SysConfigMap = {}
      for (const record of records) {
        const normalizedKey = record.configKey?.trim()
        if (!normalizedKey) {
          continue
        }
        nextMap[normalizedKey] = record
      }

      configMap.value = nextMap
      persistSysConfigCache()
      initialized.value = true
    } finally {
      loading.value = false
    }
  }

  /**
   * 方法效果：
   * 按 `configKey` 读取当前已缓存的系统设置记录。
   * 参数：
   * - `configKey`：设置项键值。
   * 返回值：
   * - 命中时返回完整 `SysConfigRecord`，未命中返回 `null`。
   */
  const getSysConfig = (configKey: string) => {
    const normalizedKey = configKey.trim()
    return configMap.value[normalizedKey] ?? null
  }

  /**
   * 方法效果：
   * 清空当前会话和本地持久化的全部系统设置缓存。
   * 参数：
   * - 无。
   * 返回值：
   * - 无返回值；副作用是重置系统设置缓存状态。
   */
  const clearSysConfigCache = () => {
    configMap.value = {}
    initialized.value = false
    loading.value = false
    localStorage.removeItem(SYS_CONFIG_CACHE_STORAGE_KEY)
  }

  return {
    configMap,
    initialized,
    loading,
    initializeSysConfigs,
    getSysConfig,
    clearSysConfigCache,
  }
})
