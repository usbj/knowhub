<!--
  文件作用：
  项目新增/编辑弹窗（创建与编辑共用），分标签页：项目信息 / 团队成员 / 项目文件。
  创建态只显示"项目信息"页，提交创建成功（拿到 projectId）后自动切到编辑态并解锁"团队成员/项目文件"页，
  后续编辑态直接三页都可操作。
  关键参数：
  - `visible`：弹窗显隐，父层双向绑定。
  - `mode`：'create' | 'edit'，决定标题与初始可操作页。
  - `projectId`：编辑态/创建后回填的项目主键（create 态初始 undefined，提交成功后有值）。
  关键依赖：
  - 项目信息页用 ElForm（title/type/level/summary/description/markdown）；
  - 团队成员页用 ProjectMemberPanel（搜用户批量加 + 单点编辑/删除）；
  - 项目文件页用 ProjectFileTree（GitHub 式树，新建文件夹/上传/重命名/删除/下载）；
  - 权限态来自详情接口回填的 canEdit/canDownload，控制成员/文件操作按钮显隐。
-->
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
  ElTabPane,
  ElTabs,
} from 'element-plus'
import MarkdownEditor from '@/components/MarkdownEditor.vue'
import { createProjectApi, getProjectDetailApi, updateProjectApi } from '@/api/knowhub/project'
import type { ProjectRecord } from '@/types/api/knowhub/project'
import { createDefaultProjectForm } from '../config'
import ProjectMemberPanel from './ProjectMemberPanel.vue'
import ProjectFileTree from './ProjectFileTree.vue'

const props = defineProps<{
  visible: boolean
  mode: 'create' | 'edit'
  /** 编辑态项目主键；创建态传 undefined，提交成功后父层回填再解锁成员/文件页 */
  projectId?: number
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  /** 创建/编辑成功后通知父层刷新列表 */
  saved: [projectId: number]
}>()

const formRef = ref()
const formModel = ref<ProjectRecord>(createDefaultProjectForm())
const activeTab = ref<'info' | 'members' | 'files'>('info')
const submitting = ref(false)
const detailLoading = ref(false)
/** 权限态：编辑态从详情接口回填，控制成员/文件操作按钮显隐 */
const canEdit = ref(false)
const canDownload = ref(false)

/** 创建态仅"项目信息"页可操作；编辑态全部可操作 */
const memberLock = computed(() => !props.projectId)
const fileLock = computed(() => !props.projectId)

const dialogTitle = computed(() => (props.mode === 'create' && !props.projectId ? '新增项目' : '编辑项目'))

const rules = {
  title: [
    { required: true, message: '请输入项目名称', trigger: 'blur' },
    { min: 2, max: 128, message: '名称长度需在 2 到 128 位之间', trigger: 'blur' },
  ],
  type: [{ required: true, message: '请选择项目类型', trigger: 'change' }],
  level: [{ required: true, message: '请选择项目等级', trigger: 'change' }],
}

watch(
  () => [props.visible, props.projectId, props.mode] as const,
  async ([visible, projectId, mode]) => {
    if (!visible) {
      activeTab.value = 'info'
      canEdit.value = false
      canDownload.value = false
      return
    }
    // 编辑态：拉详情回显 + 回填权限态
    if (mode === 'edit' && projectId) {
      detailLoading.value = true
      try {
        const result = await getProjectDetailApi(projectId)
        formModel.value = { ...createDefaultProjectForm(), ...result.data }
        canEdit.value = Boolean(result.data?.canEdit) || result.data?.myMemberRole === 'LEADER'
        canDownload.value = Boolean(result.data?.canDownload) || result.data?.myMemberRole === 'LEADER'
      } finally {
        detailLoading.value = false
      }
    } else if (mode === 'create') {
      formModel.value = createDefaultProjectForm()
      canEdit.value = true // 创建者创建后默认 LEADER，能加成员/文件
      canDownload.value = true
    }
  },
  { immediate: true },
)

const submitInfo = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  submitting.value = true
  try {
    const payload: ProjectRecord = {
      ...formModel.value,
      title: formModel.value.title.trim(),
      summary: formModel.value.summary?.trim() || '',
      description: formModel.value.description?.trim() || '',
    }
    if (props.mode === 'create' && !props.projectId) {
      const result = await createProjectApi(payload)
      Object.assign(formModel.value, result.data ?? {})
      ElMessage.success('项目创建成功，可继续添加成员与文件')
      emit('saved', Number(formModel.value.projectId ?? 0))
    } else {
      await updateProjectApi({ ...payload, projectId: props.projectId })
      ElMessage.success('项目信息已保存')
    }
  } finally {
    submitting.value = false
  }
}

/** 创建态点击成员/文件页时，若未保存项目信息则提示先保存 */
const handleTabChange = (name: string | number) => {
  if ((name === 'members' || name === 'files') && !props.projectId && props.mode === 'create') {
    ElMessage.warning('请先填写并保存项目信息，再添加成员/文件')
    activeTab.value = 'info'
  }
}

const close = () => {
  emit('update:visible', false)
}
</script>

<template>
  <ElDialog
    :model-value="visible"
    :title="dialogTitle"
    width="920px"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
  >
    <ElTabs v-model="activeTab" v-loading="detailLoading" @tab-change="handleTabChange">
      <!-- 项目信息 -->
      <ElTabPane label="项目信息" name="info">
        <ElForm ref="formRef" :model="formModel" :rules="rules" label-width="90px">
          <ElFormItem label="项目名称" prop="title">
            <ElInput v-model="formModel.title" placeholder="请输入项目名称" maxlength="128" show-word-limit />
          </ElFormItem>
          <ElFormItem label="项目类型" prop="type">
            <ElSelect v-model="formModel.type" placeholder="请选择类型" style="width: 100%">
              <ElOption label="比赛项目" value="COMPETITION" />
              <ElOption label="练习项目" value="PRACTICE" />
              <ElOption label="运维项目" value="OPS" />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="项目等级" prop="level">
            <ElSelect v-model="formModel.level" placeholder="请选择等级" style="width: 100%">
              <ElOption label="公开 (L1)" :value="1" />
              <ElOption label="内部 (L2)" :value="2" />
              <ElOption label="机密 (L3)" :value="3" />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="简介" prop="summary">
            <ElInput v-model="formModel.summary" type="textarea" :rows="2" placeholder="项目简介（可选）" maxlength="500" show-word-limit />
          </ElFormItem>
          <ElFormItem label="详细介绍" prop="description">
            <MarkdownEditor
              class="project-edit__markdown"
              :model-value="formModel.description ?? ''"
              placeholder="项目详细介绍（支持 Markdown，可选）"
              height="220px"
              @update:model-value="formModel.description = $event"
            />
          </ElFormItem>
        </ElForm>
        <div class="project-edit__info-actions">
          <ElButton type="primary" :loading="submitting" @click="submitInfo">
            {{ mode === 'create' && !projectId ? '创建项目' : '保存信息' }}
          </ElButton>
          <ElButton @click="close">关闭</ElButton>
        </div>
      </ElTabPane>

      <!-- 团队成员（创建前提示先保存信息） -->
      <ElTabPane label="团队成员" name="members" :disabled="memberLock">
        <ProjectMemberPanel :project-id="projectId" :can-edit="canEdit" />
      </ElTabPane>

      <!-- 项目文件（创建前提示先保存信息） -->
      <ElTabPane label="项目文件" name="files" :disabled="fileLock">
        <ProjectFileTree :project-id="projectId" :can-edit="canEdit" :can-download="canDownload" />
      </ElTabPane>
    </ElTabs>
  </ElDialog>
</template>

<style scoped>
.project-edit__info-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 8px;
}

/* v-md-editor 在 ElFormItem 内默认按内容宽度撑开会溢出弹窗，显式约束 100% */
.project-edit__markdown :deep(.v-md-editor) {
  width: 100%;
}
</style>