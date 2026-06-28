/**
 * 文件作用：
 * 提供角色管理页专用的菜单权限树字段，
 * 把权限树的展开、全选和父子联动逻辑从公共表单中拆出来。
 * 关键参数：
 * - `modelValue`：当前已选菜单主键数组。
 * - `options`：权限树节点数据，节点格式约定为 `{ label, value, children }`。
 * 关键事件：
 * - `update:modelValue`：在勾选变化后向角色页同步最新权限主键数组。
 */
<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { ElCheckbox, ElTree } from 'element-plus'
import { collectPermissionIdsWithAncestors } from '../config'

const props = withDefaults(
  defineProps<{
    modelValue?: number[]
    options?: Array<Record<string, unknown>>
  }>(),
  {
    modelValue: () => [],
    options: () => [],
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: number[]]
}>()

const treeRef = ref<InstanceType<typeof ElTree>>()
const expanded = ref(false)
const checkStrictly = ref(false)

const checkedKeys = computed(() => props.modelValue.map((item) => Number(item)))

const collectAllKeys = (nodes: Array<Record<string, unknown>>): number[] =>
  nodes.flatMap((node) => {
    const currentKey = Number(node.value ?? 0)
    const children = Array.isArray(node.children) ? (node.children as Array<Record<string, unknown>>) : []
    return [currentKey, ...collectAllKeys(children)]
  })

const allKeys = computed(() => collectAllKeys(props.options).filter((item) => item > 0))
const isAllChecked = computed(() => allKeys.value.length > 0 && checkedKeys.value.length === allKeys.value.length)

const syncCheckedKeys = async () => {
  await nextTick()
  treeRef.value?.setCheckedKeys([])
  checkedKeys.value.forEach((currentKey) => {
    treeRef.value?.setChecked(currentKey, true, false)
  })
}

const handleTreeCheck = () => {
  const rawCheckedKeys = (treeRef.value?.getCheckedKeys(false) ?? []).map((item) => Number(item))
  const nextValue = checkStrictly.value
    ? collectPermissionIdsWithAncestors(props.options, rawCheckedKeys)
    : rawCheckedKeys

  emit('update:modelValue', nextValue)
}

const handleExpandToggle = async () => {
  expanded.value = !expanded.value
  await nextTick()

  const store = (treeRef.value as InstanceType<typeof ElTree> & {
    store?: { _getAllNodes?: () => Array<{ expanded: boolean }> }
  } | undefined)?.store

  const nodes = store?._getAllNodes?.() ?? []
  nodes.forEach((node) => {
    node.expanded = expanded.value
  })
}

const handleCheckAllToggle = () => {
  treeRef.value?.setCheckedKeys(isAllChecked.value ? [] : allKeys.value)
  handleTreeCheck()
}

watch(
  () => props.modelValue,
  async () => {
    await syncCheckedKeys()
  },
  { immediate: true, deep: true },
)

watch(checkStrictly, async () => {
  await syncCheckedKeys()
})
</script>

<template>
  <div class="role-permission-tree-field">
    <div class="role-permission-tree-field__toolbar">
      <ElCheckbox :model-value="expanded" @change="handleExpandToggle">展开/折叠</ElCheckbox>
      <ElCheckbox :model-value="isAllChecked" @change="handleCheckAllToggle">全选/全不选</ElCheckbox>
      <ElCheckbox v-model="checkStrictly">取消父子联动</ElCheckbox>
    </div>

    <ElTree
      ref="treeRef"
      class="role-permission-tree-field__tree"
      show-checkbox
      node-key="value"
      :data="options"
      :props="{ label: 'label', children: 'children' }"
      :check-strictly="checkStrictly"
      @check="handleTreeCheck"
    />
  </div>
</template>

<style scoped>
.role-permission-tree-field {
  display: grid;
  gap: 10px;
  width: 100%;
}

.role-permission-tree-field__toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  color: var(--rookie-text-secondary);
  font-size: var(--rookie-font-size-sm);
}

.role-permission-tree-field__tree {
  min-height: 240px;
  max-height: 320px;
  padding: 12px;
  overflow: auto;
  border: 1px solid var(--rookie-border);
  border-radius: var(--rookie-radius-md);
  background: var(--rookie-card-bg);
}
</style>
