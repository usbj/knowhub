<!--
  文件作用：
  项目文件树组件，GitHub 式侧边栏布局，承接项目详情弹窗内/前台项目页的文件展示与操作。
  关键参数：
  - `projectId`：项目主键，组件据此拉取文件树。
  - `canEdit`：当前用户对该项目是否有编辑权限（控制新建文件夹/上传/删除按钮显隐）。
  - `canDownload`：当前用户对该项目是否有下载权限（控制文件叶子下载按钮显隐）。
  关键交互：
  - 目录可展开/折叠（递归渲染 children），文件叶子点击下载；
  - 有编辑权限时展示「新建文件夹 / 上传文件」按钮，节点悬浮展示「重命名 / 删除」；
  - 上传文件走 presignedUploadFlow（businessType 按 PROJECT_SRC/PKG/DOC 三类，可选），
    传完调 addProjectFileNodeApi 挂到当前目录；
  - 新建文件夹调 createProjectFolderApi；重命名/删除调 update/deleteProjectFileNodeApi；
  - 删除目录级联删子节点（后端处理），前端仅 confirm 确认。
  设计约定：
  - 文件树数据由后端 service 层按 parentId 组装为树形（getProjectFileTreeApi），前端直接递归渲染；
  - 文件大小格式化（B/KB/MB）；businessType 用 DictTag 渲染中文（file_business_type 字典）；
  - 主题适配：树节点、操作按钮、空状态均用 base.css 的 --rookie-* 变量，深浅模式自动跟随。
  - 通过 defineOptions({ name: 'ProjectFileTree' }) + 模板内 <ProjectFileTree> 自引用实现递归。
-->
<script setup lang="ts">
import { ref, watch } from 'vue'
import {
  ElButton,
  ElDialog,
  ElEmpty,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
} from 'element-plus'
import DictTag from '@/components/DictTag.vue'
import { presignedUploadFlow } from '@/utils/upload'
import {
  addProjectFileNodeApi,
  createProjectFolderApi,
  deleteProjectFileNodeApi,
  downloadProjectFileApi,
  getProjectFileTreeApi,
  updateProjectFileNodeApi,
} from '@/api/knowhub/project'
import {
  PROJECT_BUSINESS_TYPE,
  PROJECT_FILE_IS_DIR,
  type ProjectFileTreeNode,
} from '@/types/api/knowhub/project'

// 显式命名以支持模板内自引用递归（<ProjectFileTree> 渲染子节点）
defineOptions({ name: 'ProjectFileTree' })

const props = withDefaults(
  defineProps<{
    projectId?: number
    canEdit?: boolean
    canDownload?: boolean
    /** 当前渲染的节点（递归时由父层传入；顶层不传则按 projectId 拉全树） */
    node?: ProjectFileTreeNode
    /** 是否递归子层（true=作为递归节点渲染单个 node；false=作为容器拉树渲染根列表） */
    recursive?: boolean
  }>(),
  { canEdit: false, canDownload: false, recursive: false },
)

/** 文件树根节点列表（仅容器模式用） */
const tree = ref<ProjectFileTreeNode[]>([])
const loading = ref(false)
const uploading = ref(false)
const uploadPercent = ref(0)
const fileInputRef = ref<HTMLInputElement | null>(null)
const uploadTargetParentId = ref<number | undefined>(undefined)

const folderDialogVisible = ref(false)
const folderForm = ref<{ name: string; parentId?: number }>({ name: '', parentId: undefined })
const renameDialogVisible = ref(false)
const renameForm = ref<{ fileId: number; name: string }>({ fileId: 0, name: '' })
/** 当前节点展开态（递归节点用，目录默认展开） */
const expanded = ref(true)

/**
 * 方法效果：
 * 文件大小格式化（字节 → KB/MB/GB）。
 */
const formatSize = (len?: number) => {
  if (len == null) return '--'
  if (len < 1024) return `${len} B`
  if (len < 1024 * 1024) return `${(len / 1024).toFixed(1)} KB`
  if (len < 1024 * 1024 * 1024) return `${(len / 1024 / 1024).toFixed(1)} MB`
  return `${(len / 1024 / 1024 / 1024).toFixed(2)} GB`
}

/** 容器模式：拉取项目文件树 */
const fetchTree = async () => {
  if (!props.projectId) {
    tree.value = []
    return
  }
  loading.value = true
  try {
    const result = await getProjectFileTreeApi(props.projectId)
    tree.value = result.data ?? []
  } catch {
    tree.value = []
  } finally {
    loading.value = false
  }
}

// 仅容器模式监听 projectId 拉树
watch(
  () => props.projectId,
  async (id) => {
    if (!props.recursive) {
      if (id) await fetchTree()
      else tree.value = []
    }
  },
  { immediate: true },
)

const toggleExpand = () => {
  expanded.value = !expanded.value
}

/** 新建文件夹：打开弹窗（parentId 为目标目录，根级传 undefined） */
const openFolderDialog = (parentId?: number) => {
  folderForm.value = { name: '', parentId }
  folderDialogVisible.value = true
}

const submitFolder = async () => {
  if (!props.projectId || !folderForm.value.name.trim()) {
    ElMessage.warning('请输入文件夹名称')
    return
  }
  try {
    await createProjectFolderApi({
      projectId: props.projectId,
      parentId: folderForm.value.parentId,
      name: folderForm.value.name.trim(),
      isDir: PROJECT_FILE_IS_DIR,
      sort: 0,
    })
    ElMessage.success('文件夹创建成功')
    folderDialogVisible.value = false
    await fetchTree()
  } catch {
    // http.ts 已统一弹错
  }
}

const openRenameDialog = (node: ProjectFileTreeNode) => {
  renameForm.value = { fileId: node.fileId ?? 0, name: node.name }
  renameDialogVisible.value = true
}

const submitRename = async () => {
  if (!renameForm.value.name.trim()) {
    ElMessage.warning('请输入名称')
    return
  }
  try {
    await updateProjectFileNodeApi({ fileId: renameForm.value.fileId, name: renameForm.value.name.trim() })
    ElMessage.success('重命名成功')
    renameDialogVisible.value = false
    await fetchTree()
  } catch {
    // http.ts 已统一弹错
  }
}

const handleDelete = async (node: ProjectFileTreeNode) => {
  if (!node.fileId) return
  try {
    await ElMessageBox.confirm(
      `确认删除「${node.name}」吗？${node.isDir === PROJECT_FILE_IS_DIR ? '文件夹下所有内容将被一并删除。' : ''}`,
      '删除文件',
      { type: 'warning' },
    )
    await deleteProjectFileNodeApi(node.fileId)
    ElMessage.success('删除成功')
    await fetchTree()
  } catch {
    // 用户取消或 http.ts 已弹错
  }
}

const triggerFilePicker = (parentId?: number) => {
  if (!props.canEdit) return
  uploadTargetParentId.value = parentId
  fileInputRef.value?.click()
}

const handleFileChange = async (event: Event) => {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file || !props.projectId) return
  uploading.value = true
  uploadPercent.value = 0
  try {
    const result = await presignedUploadFlow({
      file,
      businessType: PROJECT_BUSINESS_TYPE.DOC,
      access: 'PRIVATE',
      onProgress: (percent) => {
        uploadPercent.value = percent
      },
    })
    await addProjectFileNodeApi({
      projectId: props.projectId,
      parentId: uploadTargetParentId.value,
      name: file.name,
      isDir: 0,
      objectId: result.objectId,
      sort: 0,
    })
    ElMessage.success('文件上传成功')
    await fetchTree()
  } catch {
    // http.ts 已统一弹错
  } finally {
    uploading.value = false
    uploadPercent.value = 0
    if (fileInputRef.value) fileInputRef.value.value = ''
  }
}

const handleDownload = async (node: ProjectFileTreeNode) => {
  if (!node.fileId) return
  try {
    const result = await downloadProjectFileApi(node.fileId)
    const url = result.data
    if (url) {
      window.open(url, '_blank')
    } else {
      ElMessage.warning('获取下载链接失败')
    }
  } catch {
    // http.ts 已统一弹错
  }
}
</script>

<template>
  <!-- 递归节点模式：渲染单个 node + 其 children（递归引用自身） -->
  <div v-if="recursive && node" class="project-file-tree__node">
    <div class="project-file-tree__row">
      <span
        class="project-file-tree__caret"
        :class="{ 'is-leaf': node.isDir !== PROJECT_FILE_IS_DIR }"
        @click="node.isDir === PROJECT_FILE_IS_DIR ? toggleExpand() : undefined"
      >
        {{ node.isDir === PROJECT_FILE_IS_DIR ? (expanded ? '▾' : '▸') : '' }}
      </span>
      <span class="project-file-tree__icon">{{ node.isDir === PROJECT_FILE_IS_DIR ? '📁' : '📄' }}</span>
      <span
        class="project-file-tree__name"
        :class="{ 'is-downloadable': node.isDir !== PROJECT_FILE_IS_DIR && canDownload }"
        :title="node.name"
        @click="node.isDir !== PROJECT_FILE_IS_DIR && canDownload ? handleDownload(node) : undefined"
      >
        {{ node.name }}
      </span>
      <span v-if="node.isDir !== PROJECT_FILE_IS_DIR && node.contentLength != null" class="project-file-tree__size">
        {{ formatSize(node.contentLength) }}
      </span>
      <span v-if="node.isDir !== PROJECT_FILE_IS_DIR && node.businessType" class="project-file-tree__biz">
        <DictTag dict-key="file_business_type" :value="node.businessType" />
      </span>
      <span class="project-file-tree__actions">
        <button
          v-if="node.isDir === PROJECT_FILE_IS_DIR && canEdit"
          class="project-file-tree__btn"
          title="新建文件夹"
          @click.stop="openFolderDialog(node.fileId)"
        >
          ＋📁
        </button>
        <button
          v-if="node.isDir === PROJECT_FILE_IS_DIR && canEdit"
          class="project-file-tree__btn"
          title="上传文件"
          @click.stop="triggerFilePicker(node.fileId)"
        >
          ⬆
        </button>
        <button v-if="canEdit" class="project-file-tree__btn" title="重命名" @click.stop="openRenameDialog(node)">
          ✎
        </button>
        <button
          v-if="canEdit"
          class="project-file-tree__btn project-file-tree__btn--danger"
          title="删除"
          @click.stop="handleDelete(node)"
        >
          ✕
        </button>
      </span>
    </div>
    <div v-if="node.isDir === PROJECT_FILE_IS_DIR && expanded && node.children?.length" class="project-file-tree__children">
      <ProjectFileTree
        v-for="child in node.children"
        :key="child.fileId"
        :node="child"
        :recursive="true"
        :can-edit="canEdit"
        :can-download="canDownload"
      />
    </div>
  </div>

  <!-- 容器模式：拉全树 + 顶部操作栏 + 根节点列表 -->
  <div v-else class="project-file-tree">
    <div v-if="canEdit" class="project-file-tree__toolbar">
      <ElButton size="small" @click="openFolderDialog(undefined)">新建文件夹</ElButton>
      <ElButton size="small" type="primary" @click="triggerFilePicker(undefined)">上传文件</ElButton>
      <span v-if="uploading" class="project-file-tree__uploading">上传中 {{ uploadPercent }}%</span>
    </div>

    <input ref="fileInputRef" type="file" class="project-file-tree__input" @change="handleFileChange" />

    <div v-loading="loading" class="project-file-tree__body">
      <template v-if="tree.length > 0">
        <ProjectFileTree
          v-for="node in tree"
          :key="node.fileId"
          :node="node"
          :recursive="true"
          :can-edit="canEdit"
          :can-download="canDownload"
        />
      </template>
      <ElEmpty v-else-if="!loading" description="暂无文件" :image-size="48" />
    </div>

    <!-- 新建文件夹弹窗 -->
    <ElDialog v-model="folderDialogVisible" title="新建文件夹" width="420px" destroy-on-close>
      <ElFormItem label="文件夹名称" required>
        <ElInput v-model="folderForm.name" placeholder="请输入文件夹名称" maxlength="255" />
      </ElFormItem>
      <template #footer>
        <ElButton @click="folderDialogVisible = false">取消</ElButton>
        <ElButton type="primary" @click="submitFolder">确定</ElButton>
      </template>
    </ElDialog>

    <!-- 重命名弹窗 -->
    <ElDialog v-model="renameDialogVisible" title="重命名" width="420px" destroy-on-close>
      <ElFormItem label="名称" required>
        <ElInput v-model="renameForm.name" placeholder="请输入名称" maxlength="255" />
      </ElFormItem>
      <template #footer>
        <ElButton @click="renameDialogVisible = false">取消</ElButton>
        <ElButton type="primary" @click="submitRename">确定</ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped>
.project-file-tree {
  display: grid;
  gap: 10px;
}

.project-file-tree__toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.project-file-tree__input {
  display: none;
}

.project-file-tree__uploading {
  color: var(--rookie-text-secondary);
  font-size: var(--rookie-font-size-sm);
}

.project-file-tree__body {
  min-height: 80px;
  padding: 6px 0;
  border: 1px solid var(--rookie-border);
  border-radius: var(--rookie-radius-md);
  background: var(--rookie-surface-weak);
}

/* 递归节点样式（scoped 对自引用子组件生效需 :deep，但自引用同组件 scoped 仍作用于自身 class） */
.project-file-tree__node {
  display: grid;
}

.project-file-tree__row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  font-size: var(--rookie-font-size-sm);
  color: var(--rookie-text);
}

.project-file-tree__row:hover {
  background: var(--rookie-surface-muted);
}

.project-file-tree__caret {
  width: 14px;
  text-align: center;
  cursor: pointer;
  user-select: none;
  color: var(--rookie-text-tertiary);
}

.project-file-tree__caret.is-leaf {
  cursor: default;
}

.project-file-tree__icon {
  flex-shrink: 0;
}

.project-file-tree__name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}

.project-file-tree__name.is-downloadable {
  cursor: pointer;
  color: var(--rookie-primary);
}

.project-file-tree__name.is-downloadable:hover {
  text-decoration: underline;
}

.project-file-tree__size {
  color: var(--rookie-text-tertiary);
  font-size: 12px;
  flex-shrink: 0;
}

.project-file-tree__biz {
  flex-shrink: 0;
}

.project-file-tree__actions {
  display: flex;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.15s;
}

.project-file-tree__row:hover .project-file-tree__actions {
  opacity: 1;
}

.project-file-tree__btn {
  border: none;
  background: transparent;
  color: var(--rookie-text-secondary);
  cursor: pointer;
  padding: 0 4px;
  font-size: 13px;
  border-radius: var(--rookie-radius-sm);
}

.project-file-tree__btn:hover {
  color: var(--rookie-primary);
  background: var(--rookie-primary-soft);
}

.project-file-tree__btn--danger:hover {
  color: var(--rookie-danger);
  background: var(--rookie-danger-soft);
}

.project-file-tree__children {
  padding-left: 18px;
}
</style>
