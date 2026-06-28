/**
 * 文件作用：
 * 根据传入的字典键值和实际值，渲染普通标签形式的展示内容。
 * 关键参数：
 * - `dictKey`：字典类型键值。
 * - `value`：待展示的实际值，支持单值或数组。
 * - `placeholder`：未命中字典或值为空时的占位文本。
 * 关键行为：
 * - 优先使用字典项自带的 `tagType`、`tagEffect`、`cssClass` 渲染标签样式。
 */
<script setup lang="ts">
import { computed } from 'vue'
import { ElTag } from 'element-plus'
import { useDict } from '@/composables/useDict'
import { useDictStore } from '@/stores/dict'

const props = withDefaults(
  defineProps<{
    dictKey: string
    value: unknown
    placeholder?: string
  }>(),
  {
    placeholder: '--',
  },
)

const { resolveDictRecord } = useDict()
const dictStore = useDictStore()

const tagItems = computed(() => {
  if (props.value === null || props.value === undefined || props.value === '') {
    return []
  }

  const values = Array.isArray(props.value) ? props.value : [props.value]

  return values
    .map((item) => {
      const matchedRecord = resolveDictRecord(props.dictKey, item)

      return {
        key: `${props.dictKey}-${String(item)}`,
        label: matchedRecord?.dictDataLabel ?? String(item ?? ''),
        type: dictStore.normalizeTagType(matchedRecord?.tagType),
        effect: dictStore.normalizeTagEffect(matchedRecord?.tagEffect),
        className: matchedRecord?.cssClass?.trim() || '',
      }
    })
    .filter((item) => Boolean(item.label))
})
</script>

<template>
  <div v-if="tagItems.length > 0" class="dict-tag">
    <ElTag
      v-for="tagItem in tagItems"
      :key="tagItem.key"
      size="small"
      :effect="tagItem.effect"
      :type="tagItem.type"
      :class="tagItem.className"
    >
      {{ tagItem.label }}
    </ElTag>
  </div>
  <span v-else class="dict-tag__placeholder">{{ placeholder }}</span>
</template>

<style scoped>
.dict-tag {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.dict-tag__placeholder {
  color: var(--rookie-text-tertiary);
}
</style>
