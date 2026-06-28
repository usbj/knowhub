import { computed } from 'vue'
import { useDictStore } from '@/stores/dict'

/**
 * 文件作用：
 * 提供组件级字典读取能力，
 * 让页面可以按 `dictKey` 直接获取字典数组、下拉选项和展示文本。
 * 关键参数：
 * - `dictKey`：字典类型键值，对应后端字典表中的 `dictKey`。
 * - `valueType`：控制选项值按字符串还是数字返回。
 * 关键能力：
 * - `getDictData`：返回响应式字典数据数组。
 * - `getDictOptions`：返回响应式下拉选项数组。
 * - `resolveDictRecord`：按值读取完整字典项，供标签样式等场景复用。
 * - `resolveDictLabel`：按值解析展示文本。
 */
export const useDict = () => {
  const dictStore = useDictStore()

  const getDictData = (dictKey: string) =>
    computed(() => dictStore.getDictData(dictKey))

  const getDictOptions = (dictKey: string, valueType: 'string' | 'number' = 'string') =>
    computed(() => dictStore.getDictOptions(dictKey, valueType))

  const resolveDictOptions = (dictKey: string, valueType: 'string' | 'number' = 'string') =>
    dictStore.getDictOptions(dictKey, valueType)

  const resolveDictRecord = (dictKey: string, value: unknown) =>
    dictStore.getDictRecord(dictKey, value)

  const resolveDictLabel = (dictKey: string, value: unknown) =>
    dictStore.getDictLabel(dictKey, value)

  const ensureDictLoaded = async (dictKey: string, force = false) =>
    dictStore.fetchDictDataByKey(dictKey, force)

  return {
    dictStore,
    getDictData,
    getDictOptions,
    resolveDictOptions,
    resolveDictRecord,
    resolveDictLabel,
    ensureDictLoaded,
  }
}
