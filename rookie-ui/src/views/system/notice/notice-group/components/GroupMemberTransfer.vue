/**
 * 文件作用：
 * 封装通知分组的成员管理穿梭框，
 * 左侧展示全部可选用户，右侧展示当前分组已选成员，
 * 保存时按"新增/移除"差集分别调用后端成员增删接口。
 * 关键参数：
 * - `groupId`：当前分组主键。
 * - `members`：当前分组已有成员列表（含成员记录 id 与 userId）。
 * - `userOptions`：全部用户选项，供穿梭框左侧渲染。
 * 关键行为：
 * - 打开时以已有成员 userId 作为右侧初始勾选；
 * - 保存时把目标集合与原始集合做差集，分别调用添加与移除接口。
 */
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ElTransfer } from 'element-plus'
import { addNoticeGroupMembersApi, removeNoticeGroupMembersApi } from '@/api/system/notice'
import type { SysNoticeGroupMemberRecord } from '@/types/api/system/notice'

const props = defineProps<{
  groupId: number
  members: SysNoticeGroupMemberRecord[]
  userOptions: Array<{ key: number; label: string; disabled?: boolean }>
}>()

const emit = defineEmits<{
  success: []
}>()

const visible = ref(false)
const saving = ref(false)
// 穿梭框右侧勾选的 userId 集合
const targetUserIds = ref<number[]>([])

/**
 * userId 到成员记录 id 的映射，用于移除成员时把 userId 还原成后端需要的成员记录主键。
 * 后端 removeMembers 接收的是成员记录 id（SysNoticeGroupMember.id），而非 userId。
 */
const memberIdByUserId = computed(() => {
  const map = new Map<number, number>()

  props.members.forEach((member) => {
    map.set(Number(member.userId), Number(member.id))
  })

  return map
})

/**
 * 方法效果：
 * 打开穿梭框，并以当前已有成员的 userId 作为右侧初始勾选项。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是重置勾选并展示弹层。
 */
const open = () => {
  targetUserIds.value = props.members.map((member) => Number(member.userId))
  visible.value = true
}

/**
 * 方法效果：
 * 提交成员变更，按目标集合与原始集合的差集分别调用新增与移除接口。
 * 数据流转：
 * - 新增 = 目标 userId 中原始没有的，调用 addMembers 传 userIds；
 * - 移除 = 原始 userId 中目标没有的，按 userId 还原成成员记录 id 后调用 removeMembers。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是调用接口、提示并关闭弹层、抛出 success 事件。
 */
const handleSave = async () => {
  const targetSet = new Set(targetUserIds.value.map((item) => Number(item)))
  const originalUserIds = props.members.map((member) => Number(member.userId))

  const toAdd = targetUserIds.value
    .map((item) => Number(item))
    .filter((userId) => !originalUserIds.includes(userId))
  const toRemoveMemberIds = originalUserIds
    .filter((userId) => !targetSet.has(userId))
    .map((userId) => memberIdByUserId.value.get(userId))
    .filter((id): id is number => typeof id === 'number')

  // 无任何变更时直接关闭，避免无意义请求
  if (toAdd.length === 0 && toRemoveMemberIds.length === 0) {
    visible.value = false
    return
  }

  saving.value = true

  try {
    if (toAdd.length > 0) {
      await addNoticeGroupMembersApi(props.groupId, toAdd)
    }

    if (toRemoveMemberIds.length > 0) {
      await removeNoticeGroupMembersApi(props.groupId, toRemoveMemberIds)
    }

    ElMessage.success('分组成员更新成功')
    visible.value = false
    emit('success')
  } finally {
    saving.value = false
  }
}

watch(visible, (next) => {
  // 每次重新打开时基于最新成员重置勾选，避免残留上次差集状态
  if (next) {
    targetUserIds.value = props.members.map((member) => Number(member.userId))
  }
})

defineExpose({ open })
</script>

<template>
  <el-dialog
    v-model="visible"
    title="管理分组成员"
    width="720px"
    destroy-on-close
  >
    <ElTransfer
      v-model="targetUserIds"
      :data="userOptions"
      filterable
      filter-placeholder="搜索用户昵称"
      :titles="['可选用户', '分组成员']"
      :button-texts="['移除', '加入']"
    />

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
    </template>
  </el-dialog>
</template>
