/**
 * 文件作用：
 * 提供组件级系统设置读取能力，对标 useDict。
 * 让页面可以按 `configKey` 直接获取设置值，并按 `valueType` 自动转换类型：
 * - STRING：原样返回字符串。
 * - BOOLEAN：`"true"` / `"1"` 视为 true，其余为 false。
 * - NUMBER：`Number(value)`。
 * - JSON：`JSON.parse`，失败回落默认值。
 * 关键参数：
 * - `configKey`：设置项键值，对应后端 `system_config.config_key`。
 * - `defaultValue`：未命中、停用或转换失败时的回退值。
 * 关键能力：
 * - `getString` / `getBoolean` / `getNumber` / `getObject` / `getList`：按类型读取设置值。
 * - `resolveSysConfig`：读取完整设置记录，供高级场景复用。
 * 返回值采用非响应式直接返回，对标 `useDict` 的 `resolveDictLabel`：
 * 系统设置启动加载后一般不变，页面使用时直接调用即可。
 */
import { useSysConfigStore } from '@/stores/system-config'

export const useSysConfig = () => {
  const sysConfigStore = useSysConfigStore()

  const resolveSysConfig = (configKey: string) => sysConfigStore.getSysConfig(configKey)

  const getString = (configKey: string, defaultValue = '') => {
    const record = sysConfigStore.getSysConfig(configKey)
    if (!record || record.status === 0) {
      return defaultValue
    }
    return record.configValue ?? defaultValue
  }

  const getBoolean = (configKey: string, defaultValue = false) => {
    const record = sysConfigStore.getSysConfig(configKey)
    if (!record || record.status === 0) {
      return defaultValue
    }
    const raw = String(record.configValue ?? '').trim().toLowerCase()
    if (raw === 'true' || raw === '1') {
      return true
    }
    if (raw === 'false' || raw === '0') {
      return false
    }
    return defaultValue
  }

  const getNumber = (configKey: string, defaultValue = 0) => {
    const record = sysConfigStore.getSysConfig(configKey)
    if (!record || record.status === 0) {
      return defaultValue
    }
    const parsed = Number(record.configValue)
    return Number.isNaN(parsed) ? defaultValue : parsed
  }

  const getObject = <T>(configKey: string, defaultValue: T): T => {
    const record = sysConfigStore.getSysConfig(configKey)
    if (!record || record.status === 0) {
      return defaultValue
    }
    try {
      return JSON.parse(record.configValue) as T
    } catch {
      return defaultValue
    }
  }

  const getList = <T>(configKey: string, defaultValue: T[] = []): T[] => {
    const parsed = getObject<T[] | null>(configKey, null)
    return Array.isArray(parsed) ? parsed : defaultValue
  }

  return {
    sysConfigStore,
    resolveSysConfig,
    getString,
    getBoolean,
    getNumber,
    getObject,
    getList,
  }
}
