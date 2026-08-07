<!--
  ProjectMemberPanel —— 前台项目成员管理面板
  ------------------------------------------------------------------
  承接项目创作页内"团队成员"标签页，成员展示与增删改。
  与后台 ProjectMemberPanel 的差异：
  - API 源从 /knowhub/project/*（后台 admin 接口，需 knowhub:project:member 按钮权限）
    换为 /authoring/project/*（前台创作者薄封装，登录 + LEADER/作者 校验，不依赖后台按钮权限）。
  - 角色标签用内联 roleLabel 映射替代后台 DictTag（前台不引字典缓存渲染角色，与项目详情页
    memberRoleLabel 同口径），保持前台一致。
  关键参数：
  - `projectId`：项目主键，组件据此拉取成员列表。
  关键交互：
  - 成员列表展示昵称/账号/角色/权限标志位；
  - 「添加成员」打开搜用户子弹窗（前台轻量选人接口），批量加（默认 MEMBER，已存在跳过）；
  - 每行「编辑/删除」：编辑改角色/权限标志位（单点编辑走 editProjectMemberApi）；
  - 删除成员：LEADER 不可直接删（后端拦截，前端按钮也禁用）。
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
import {
  addProjectMembersBatchApi,
  deleteProjectMemberApi,
  editProjectMemberApi,
  listProjectMembersApi,
} from '@/api/knowhub/project-authoring'
import type { ProjectMemberRecord } from '@/types/api/knowhub/project-authoring'
import type { AuthoringUserRecord } from '@/api/knowhub/authoring-user'
import ProjectMemberAddDialog from './ProjectMemberAddDialog.vue'

const emit = defineEmits<{
  /**
   * 添加成员弹窗显隐变化：true=已打开（父层据此隐藏外层成员管理弹窗，避免两个弹窗同屏），
   * false=已关闭（父层据此恢复外层成员管理弹窗）。
   */
  'add-dialog-change': [visible: boolean]
  /**
   * 编辑成员弹窗显隐变化：true=已打开（父层隐藏外层成员管理弹窗），false=已关闭（父层恢复）。
   * 与 add-dialog-change 同口径，覆盖"编辑成员"子弹窗与成员管理外层弹窗互斥。
   */
  'edit-dialog-change': [visible: boolean]
}>()

const props = defineProps<{
  projectId: number | undefined
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

/** 角色字典内联映射（前台不引 DictTag，与项目详情页 memberRoleLabel 同口径） */
const roleLabel: Record<string, string> = {
  LEADER: '负责人',
  MENTOR: '导师',
  MEMBER: '参与者',
}
const roleTagType = (role: string): 'primary' | 'warning' | 'info' =>
  role === 'LEADER' ? 'primary' : role === 'MENTOR' ? 'warning' : 'info'

/** 已选成员 userId 集合，传给搜用户子弹窗控制"已加入"禁用态 */
const excludeUserIds = computed(() => new Set(members.value.map((m) => Number(m.userId))))

/**
 * 方法效果：
 * 拉取项目成员列表（前台 authoring 接口，service 内 canOp(view) 校验防越权）。
 */
const fetchMembers = async () => {
  if (!props.projectId) {
    members.value = []
    return
  }
  loading.value = true
  try {
    const result = await listProjectMembersApi(props.projectId)
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

/** 打开搜用户子弹窗 */
const openAddDialog = () => {
  addDialogVisible.value = true
}

// 添加成员弹窗显隐变化通知父层：开启时父层隐藏外层成员管理弹窗，关闭时恢复
// （避免两个 ElDialog 同屏叠加，保持页面一次只一个弹窗）
watch(addDialogVisible, (v) => {
  emit('add-dialog-change', v)
})

/**
 * 方法效果：
 * 搜用户子弹窗「加入」回调：批量调接口加成员（默认 MEMBER），已存在的后端跳过；
 * 加完刷新列表。
 */
const handleAddUser = async (user: AuthoringUserRecord) => {
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

// 编辑成员弹窗显隐变化通知父层：与添加成员同口径，开启时父层隐藏成员管理外层弹窗，关闭时恢复
watch(editDialogVisible, (v) => {
  emit('edit-dialog-change', v)
})

const submitEdit = async () => {
  if (!editForm.value.memberId) return
  try {
    await editProjectMemberApi(editForm.value)
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
  <div class="kh-member-panel">
    <div class="kh-member-panel__toolbar">
      <ElButton size="small" type="primary" :loading="batchLoading" @click="openAddDialog">添加成员</ElButton>
    </div>

    <ElTable v-loading="loading" :data="members" size="small" border stripe>
      <ElTableColumn label="成员" min-width="140">
        <template #default="{ row }">
          <span>{{ row.nickname || '--' }}</span>
          <span class="kh-member-panel__username">（{{ row.username || row.userId }}）</span>
        </template>
      </ElTableColumn>
      <ElTableColumn label="角色" width="110">
        <template #default="{ row }">
          <ElTag :type="roleTagType(row.memberRole)" size="small">
            {{ roleLabel[row.memberRole] ?? row.memberRole }}
          </ElTag>
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
      <ElTableColumn label="操作" width="140" fixed="right">
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

    <!-- 搜用户子弹窗（前台轻量选人接口） -->
    <ProjectMemberAddDialog
      v-if="addDialogVisible"
      :exclude-user-ids="excludeUserIds"
      @add="handleAddUser"
      @close="addDialogVisible = false"
    />

    <!-- 编辑成员弹窗（单点改角色/权限标志位） -->
    <ElDialog
      v-model="editDialogVisible"
      title="编辑成员"
      width="460px"
      destroy-on-close
      append-to-body
    >
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
.kh-member-panel {
  display: grid;
  gap: 10px;
}

.kh-member-panel__toolbar {
  display: flex;
  justify-content: flex-end;
}

.kh-member-panel__username {
  color: var(--kh-text-tertiary);
  font-size: var(--kh-font-size-sm);
}
</style>
