/**
 * 字典 store —— knowhub 前台
 * ------------------------------------------------------------------
 * 照搬后台 rookie-ui/src/stores/dict.ts 的实现，仅改 localStorage key 前缀为 knowhub-。
 * 提供登录后全量预加载、按 dictKey 拉取、getDictLabel/getDictOptions 等展示工具，
 * 供前台任何需要把字典 code 翻成中文标签 / 渲染下拉的页面复用。
 */
import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getSysDictAllApi, getSysDictDataByTypeApi } from '@/api/system/dict'
import type { SysDictDataRecord } from '@/types/api/dict'

export const DICT_CACHE_STORAGE_KEY = 'knowhub-dict-cache'

type DictDataMap = Record<string, SysDictDataRecord[]>
type DictTagType = 'primary' | 'success' | 'warning' | 'danger' | 'info'
type DictTagEffect = 'dark' | 'light' | 'plain'

/**
 * 读取本地缓存的字典数据。
 * 优先恢复一份最近一次可用的字典内容，避免刷新后所有页面先空一拍。
 */
const readStoredDictCache = (): DictDataMap => {
  const rawCache = localStorage.getItem(DICT_CACHE_STORAGE_KEY)

  if (!rawCache) {
    return {}
  }

  try {
    return JSON.parse(rawCache) as DictDataMap
  } catch {
    localStorage.removeItem(DICT_CACHE_STORAGE_KEY)
    return {}
  }
}

const sortDictRecords = (records: SysDictDataRecord[]) =>
  [...records].sort((previous, next) => Number(previous.dictDataSort) - Number(next.dictDataSort))

export const normalizeTagType = (tagType?: string): DictTagType => {
  const normalizedTagType = String(tagType ?? '').trim()

  if (
    normalizedTagType === 'primary' ||
    normalizedTagType === 'success' ||
    normalizedTagType === 'warning' ||
    normalizedTagType === 'danger' ||
    normalizedTagType === 'info'
  ) {
    return normalizedTagType
  }

  return 'info'
}

export const normalizeTagEffect = (tagEffect?: string): DictTagEffect => {
  const normalizedTagEffect = String(tagEffect ?? '').trim()

  if (
    normalizedTagEffect === 'dark' ||
    normalizedTagEffect === 'light' ||
    normalizedTagEffect === 'plain'
  ) {
    return normalizedTagEffect
  }

  return 'plain'
}

export const useDictStore = defineStore('dict', () => {
  /**
   * 当前前端已缓存的所有字典数据，按 `dictKey` 分组存储。
   */
  const dictDataMap = ref<DictDataMap>(readStoredDictCache())

  /**
   * 标记当前会话是否已经完成过一次字典预加载。
   * 即使本地有缓存，也会在登录后的首轮导航中再刷新一次（见 initializeDictionaries 的 force=true）。
   */
  const initialized = ref(false)
  const loading = ref(false)
  const loadedKeys = ref<string[]>(Object.keys(dictDataMap.value))
  const pendingRequests = new Map<string, Promise<SysDictDataRecord[]>>()

  const loadedKeySet = computed(() => new Set(loadedKeys.value))

  const persistDictCache = () => {
    localStorage.setItem(DICT_CACHE_STORAGE_KEY, JSON.stringify(dictDataMap.value))
  }

  /**
   * 用最新字典数组覆盖指定 `dictKey` 的缓存，并同步到本地存储。
   */
  const setDictData = (dictKey: string, records: SysDictDataRecord[]) => {
    const normalizedKey = dictKey.trim()

    if (!normalizedKey) {
      return
    }

    dictDataMap.value = {
      ...dictDataMap.value,
      [normalizedKey]: sortDictRecords(records),
    }

    if (!loadedKeySet.value.has(normalizedKey)) {
      loadedKeys.value = [...loadedKeys.value, normalizedKey]
    }

    persistDictCache()
  }

  /**
   * 读取指定字典键值当前已经缓存的数据项数组。
   */
  const getDictData = (dictKey: string) => {
    const normalizedKey = dictKey.trim()
    return dictDataMap.value[normalizedKey] ?? []
  }

  /**
   * 根据字典键值和实际值读取完整字典项，供标签样式等展示逻辑复用。
   */
  const getDictRecord = (dictKey: string, value: unknown) => {
    const records = getDictData(dictKey)
    return records.find((item) => String(item.dictDataValue) === String(value)) ?? null
  }

  /**
   * 把字典数据转换成通用下拉选项数组，供 ElSelect 或公共表单使用。
   * - `valueType`：控制返回值是字符串还是数字。
   */
  const getDictOptions = (dictKey: string, valueType: 'string' | 'number' = 'string') =>
    getDictData(dictKey).map((item) => ({
      label: item.dictDataLabel,
      value: valueType === 'number' ? Number(item.dictDataValue) : item.dictDataValue,
    }))

  /**
   * 根据字典键值和实际值，解析出对应展示文本。
   * - 单值返回文本；数组返回文本数组；未命中回退原始值字符串。
   */
  const getDictLabel = (dictKey: string, value: unknown): string | string[] => {
    const records = getDictData(dictKey)

    const resolveSingleLabel = (currentValue: unknown) => {
      const matchedRecord = records.find((item) => String(item.dictDataValue) === String(currentValue))
      return matchedRecord?.dictDataLabel ?? String(currentValue ?? '')
    }

    if (Array.isArray(value)) {
      return value.map((item) => resolveSingleLabel(item))
    }

    return resolveSingleLabel(value)
  }

  /**
   * 按单个 `dictKey` 拉取远端字典数据，并写入缓存。
   * - `force`：是否忽略当前内存缓存强制刷新。
   */
  const fetchDictDataByKey = async (dictKey: string, force = false) => {
    const normalizedKey = dictKey.trim()

    if (!normalizedKey) {
      return []
    }

    if (!force && loadedKeySet.value.has(normalizedKey)) {
      return getDictData(normalizedKey)
    }

    const existingRequest = pendingRequests.get(normalizedKey)
    if (existingRequest) {
      return existingRequest
    }

    const request = getSysDictDataByTypeApi(normalizedKey)
      .then((result) => {
        const records = sortDictRecords(result.data ?? [])
        setDictData(normalizedKey, records)
        return records
      })
      .finally(() => {
        pendingRequests.delete(normalizedKey)
      })

    pendingRequests.set(normalizedKey, request)
    return request
  }

  /**
   * 登录后预加载全部启用字典类型对应的数据项，供全局页面直接消费。
   * 走无权限的 GET /sys/dict/all 拿字典类型列表（让没有字典管理权限的普通用户也能用字典功能），
   * 再逐个按 dictKey 调 GET /sys/dist/data/type/{dictKey} 拉数据项。
   * - `force`：是否强制重新拉取所有字典。
   */
  const initializeDictionaries = async (force = false) => {
    if (initialized.value && !force) {
      return
    }

    loading.value = true

    try {
      const result = await getSysDictAllApi()

      const dictKeys = Array.from(
        new Set(
          (result.data ?? [])
            .map((item) => item.dictKey?.trim())
            .filter((item): item is string => Boolean(item)),
        ),
      )

      // 初始化阶段总是从后端拉取最新字典数据，
      // 避免 localStorage 缓存导致新增字典项无法进入前端。
      await Promise.allSettled(dictKeys.map((dictKey) => fetchDictDataByKey(dictKey, true)))
      initialized.value = true
    } finally {
      loading.value = false
    }
  }

  /**
   * 清空当前会话和本地持久化的全部字典缓存。
   * 退出登录时由 user store 调用。
   */
  const clearDictCache = () => {
    dictDataMap.value = {}
    loadedKeys.value = []
    initialized.value = false
    loading.value = false
    pendingRequests.clear()
    localStorage.removeItem(DICT_CACHE_STORAGE_KEY)
  }

  return {
    dictDataMap,
    initialized,
    loading,
    loadedKeys,
    setDictData,
    getDictData,
    getDictRecord,
    getDictOptions,
    getDictLabel,
    normalizeTagType,
    normalizeTagEffect,
    fetchDictDataByKey,
    initializeDictionaries,
    clearDictCache,
  }
})