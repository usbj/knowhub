<!--
  文件作用：
  项目成员管理面板，承接编辑弹窗内成员展示与增删改。
  关键参数：
  - `projectId`：项目主键，组件据此拉取成员列表。
  - `canEdit`：当前用户对该项目是否有编辑权限（控制增删改按钮显隐）。
  关键交互：
  - 成员列表按 LEADER→MENTOR→MEMBER 排序展示（昵称/账号/角色/权限标志位）；
  - 有编辑权限时展示「添加成员」按钮，点击打开搜用户子弹窗（参考通知分组，支持昵称/用户名/手机号搜索），
    选择后批量加（默认 MEMBER 角色，已存在的跳过）；
  - 每行展示「编辑/删除」：编辑改角色/权限标志位（单点编辑走 updateProjectMemberApi）；
  - 删除成员：LEADER 不可直接删（后端拦截，前端按钮也禁用）。
  设计约定：
  - memberRole 走字典 project_member_role；权限标志位用 ElTag（是/否）；
  - 添加成员走「搜索用户批量加」范式（与通知分组一致），单点编辑改角色/权限走独立编辑弹窗；
  - 主题适配：表格、弹窗、标签均用 base.css 的 --rookie-* 变量，深浅模式自动跟随。
-->
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  ElButton,
  ElDialog,
  ElFormItem,
  ElMessage,
  ElMessageBox,
  ElSelect,
  ElOption,
  ElSwitch,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus'
import DictTag from '@/components/DictTag.vue'
import {
  addProjectMembersBatchApi,
  deleteProjectMemberApi,
  getProjectMembersApi,
  updateProjectMemberApi,
} from '@/api/knowhub/project'
import type { ProjectMemberRecord } from '@/types/api/knowhub/project'
import type { SysUserFormData } from '@/types/api/system/user'
import ProjectMemberAddDialog from './ProjectMemberAddDialog.vue'

const props = defineProps<{
  projectId: number | undefined
  canEdit: boolean
}>()

const members = ref<ProjectMemberRecord[]>([])
const loading = ref(false)
const addDialogVisible = ref(false)
const editDialogVisible = ref(false)
const editForm = ref<ProjectMemberRecord>({
  userId: 0,
  memberRole: 'MEMBER',
  canView: 1,
  canDownload: 0,
  canEdit: 0,
})
const batchLoading = ref(false)

/** 已选成员 userId 集合，传给搜用户子弹窗控制"已加入"禁用态 */
const excludeUserIds = computed(() => new Set(members.value.map((m) => Number(m.userId))))

/**
 * 方法效果：
 * 拉取项目成员列表。
 */
const fetchMembers = async () => {
  if (!props.projectId) {
    members.value = []
    return
  }
  loading.value = true
  try {
    const result = await getProjectMembersApi(props.projectId)
    members.value = result.data ?? []
  } catch {
    members.value = []
  } finally {
    loading.value = false
  }
}

watch(
  () => props.projectId,
  async (id) => {
    if (id) await fetchMembers()
    else members.value = []
  },
  { immediate: true },
)

/** 打开搜用户子弹窗（参考通知分组） */
const openAddDialog = () => {
  addDialogVisible.value = true
}

/**
 * 方法效果：
 * 搜用户子弹窗「加入」回调：批量调接口加成员（默认 MEMBER），已存在的后端跳过；
 * 加完刷新列表。
 */
const handleAddUser = async (user: SysUserFormData) => {
  if (!props.projectId) return
  batchLoading.value = true
  try {
    await addProjectMembersBatchApi(props.projectId, [Number(user.userId)])
    ElMessage.success(`已加入：${user.nickName || user.username}`)
    await fetchMembers()
  } finally {
    batchLoading.value = false
  }
}

const openEditDialog = (row: ProjectMemberRecord) => {
  editForm.value = { ...row }
  editDialogVisible.value = true
}

const submitEdit = async () => {
  if (!editForm.value.memberId) return
  try {
    await updateProjectMemberApi(editForm.value)
    ElMessage.success('成员更新成功')
    editDialogVisible.value = false
    await fetchMembers()
  } catch {
    // http.ts 已统一弹错
  }
}

const handleDelete = async (row: ProjectMemberRecord) => {
  if (!row.memberId) return
  try {
    await ElMessageBox.confirm(`确认移除成员「${row.nickname || row.username}」吗？`, '删除成员', {
      type: 'warning',
    })
    await deleteProjectMemberApi(row.memberId)
    ElMessage.success('成员已移除')
    await fetchMembers()
  } catch {
    // 用户取消或 http.ts 已弹错
  }
}

defineExpose({ fetchMembers })
</script>

<template>
  <div class="project-member-panel">
    <div v-if="canEdit" class="project-member-panel__toolbar">
      <ElButton size="small" type="primary" :loading="batchLoading" @click="openAddDialog">添加成员</ElButton>
    </div>

    <ElTable v-loading="loading" :data="members" size="small" border stripe>
      <ElTableColumn label="成员" min-width="140">
        <template #default="{ row }">
          <span>{{ row.nickname || '--' }}</span>
          <span class="project-member-panel__username">（{{ row.username || row.userId }}）</span>
        </template>
      </ElTableColumn>
      <ElTableColumn label="角色" width="110">
        <template #default="{ row }">
          <DictTag dict-key="project_member_role" :value="row.memberRole" />
        </template>
      </ElTableColumn>
      <ElTableColumn label="查看" width="70" align="center">
        <template #default="{ row }">
          <ElTag :type="row.canView === 1 ? 'success' : 'info'" size="small">
            {{ row.canView === 1 ? '是' : '否' }}
          </ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn label="下载" width="70" align="center">
        <template #default="{ row }">
          <ElTag :type="row.canDownload === 1 ? 'success' : 'info'" size="small">
            {{ row.canDownload === 1 ? '是' : '否' }}
          </ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn label="编辑" width="70" align="center">
        <template #default="{ row }">
          <ElTag :type="row.canEdit === 1 ? 'success' : 'info'" size="small">
            {{ row.canEdit === 1 ? '是' : '否' }}
          </ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn v-if="canEdit" label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <ElButton link type="primary" size="small" @click="openEditDialog(row)">编辑</ElButton>
          <ElButton
            link
            type="danger"
            size="small"
            :disabled="row.memberRole === 'LEADER'"
            @click="handleDelete(row)"
          >
            删除
          </ElButton>
        </template>
      </ElTableColumn>
    </ElTable>

    <!-- 搜用户子弹窗（参考通知分组） -->
    <ProjectMemberAddDialog
      v-if="addDialogVisible"
      :exclude-user-ids="excludeUserIds"
      @add="handleAddUser"
      @update:visible="addDialogVisible = $event"
    />

    <!-- 编辑成员弹窗（单点改角色/权限标志位） -->
    <ElDialog v-model="editDialogVisible" title="编辑成员" width="460px" destroy-on-close append-to-body>
      <ElFormItem label="成员">
        <span>{{ editForm.nickname || editForm.username || editForm.userId }}</span>
      </ElFormItem>
      <ElFormItem label="角色" required>
        <ElSelect v-model="editForm.memberRole" placeholder="请选择角色" style="width: 100%">
          <ElOption label="负责人" value="LEADER" />
          <ElOption label="导师" value="MENTOR" />
          <ElOption label="参与者" value="MEMBER" />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="查看权限">
        <ElSwitch v-model="editForm.canView" :active-value="1" :inactive-value="0" />
      </ElFormItem>
      <ElFormItem label="下载权限">
        <ElSwitch v-model="editForm.canDownload" :active-value="1" :inactive-value="0" />
      </ElFormItem>
      <ElFormItem label="编辑权限">
        <ElSwitch v-model="editForm.canEdit" :active-value="1" :inactive-value="0" />
      </ElFormItem>
      <template #footer>
        <ElButton @click="editDialogVisible = false">取消</ElButton>
        <ElButton type="primary" @click="submitEdit">确定</ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped>
.project-member-panel {
  display: grid;
  gap: 10px;
}

.project-member-panel__toolbar {
  display: flex;
  justify-content: flex-end;
}

.project-member-panel__username {
  color: var(--rookie-text-tertiary);
  font-size: var(--rookie-font-size-sm);
}
</style>