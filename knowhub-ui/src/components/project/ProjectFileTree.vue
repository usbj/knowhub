<!--
  ProjectFileTree —— 前台项目文件树组件
  ------------------------------------------------------------------
  GitHub 式文件树，承接项目创作页"项目文件"标签页的文件展示与操作。
  与后台 ProjectFileTree 的差异：
  - API 源从 /knowhub/project/*（后台 admin 接口，需 knowhub:project:add 按钮权限）
    换为 /authoring/project/file/*（前台创作者薄封装，登录 + LEADER/作者 校验）。
  - 文件/文件夹图标用 KhIcon（file/folder）替代后台 emoji，避免用 emoji 当 UI 图标。
  - businessType 标签用内联映射替代后台 DictTag（前台不引字典缓存渲染 file_business_type）。
  关键参数：
  - `projectId`：项目主键，组件据此拉取文件树。
  - `canEdit`：当前用户对该项目是否有编辑权限（控制新建文件夹/上传/删除按钮显隐）。
  - `canDownload`：当前用户对该项目是否有下载权限（控制文件叶子下载按钮显隐）。
  关键交互：
  - 目录可展开/折叠（递归渲染 children），文件叶子点击下载；
  - 有编辑权限时展示「新建文件夹 / 上传文件」按钮，节点悬浮展示「重命名 / 删除」；
  - 上传文件走 presignedUploadFlow（businessType=PROJECT_DOC，access PRIVATE），
    传完调 addProjectFileNodeApi 挂到当前目录；
  - 新建文件夹调 addProjectFolderApi；重命名调 editProjectFileNodeApi；
  - 删除目录级联删子节点（后端处理），前端仅 confirm 确认。
  通过 defineOptions({ name: 'ProjectFileTree' }) + 模板内 <ProjectFileTree> 自引用实现递归。
-->
<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import {
  ElButton,
  ElDialog,
  ElEmpty,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElTag,
} from 'element-plus'
import KhIcon from '@/components/common/KhIcon.vue'
import { presignedUploadFlow } from '@/utils/upload'
import { checkFileAllowed, formatAllowedHint } from '@/utils/upload-whitelist'
import {
  addProjectFileNodeApi,
  addProjectFolderApi,
  deleteProjectFileNodeApi,
  downloadProjectFileAuthoringApi,
  editProjectFileNodeApi,
  getProjectFileTreeAuthoringApi,
} from '@/api/knowhub/project-authoring'
import {
  PROJECT_BUSINESS_TYPE,
  PROJECT_FILE_IS_DIR,
  type ProjectFileTreeNode,
} from '@/types/api/knowhub/project-authoring'

// 显式命名以支持模板内自引用递归（<ProjectFileTree> 渲染子节点）
defineOptions({ name: 'ProjectFileTree' })

const props = withDefaults(
  defineProps<{
    projectId?: number
    canEdit?: boolean
    canDownload?: boolean
    /** 项目等级（1/2/3）：决定上传文件 access 语义，L1→PUBLIC（公开）/L2/L3→PRIVATE（私有） */
    level?: number
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
 * 当前业务类型（PROJECT_DOC）允许的文件类型文案，用于上传按钮旁灰字提示。
 * 白名单空时显示"不限类型"。容器模式 onMounted 时拉取一次。
 */
const allowedHint = ref('')

onMounted(async () => {
  if (!props.recursive) {
    try {
      allowedHint.value = await formatAllowedHint(PROJECT_BUSINESS_TYPE.DOC)
    } catch {
      allowedHint.value = ''
    }
  }
})

/** 文件业务类型中文映射（前台不引 DictTag，内联 file_business_type 字典口径） */
const bizLabel: Record<string, string> = {
  PROJECT_SRC: '源码',
  PROJECT_PKG: '打包',
  PROJECT_DOC: '文档',
}
const bizTagType = (biz: string): 'primary' | 'accent' | 'info' =>
  biz === PROJECT_BUSINESS_TYPE.SRC ? 'primary' : biz === PROJECT_BUSINESS_TYPE.PKG ? 'accent' : 'info'

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

/** 容器模式：拉取项目文件树（前台 authoring 接口） */
const fetchTree = async () => {
  if (!props.projectId) {
    tree.value = []
    return
  }
  loading.value = true
  try {
    const result = await getProjectFileTreeAuthoringApi(props.projectId)
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
    await addProjectFolderApi({
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
    await editProjectFileNodeApi({ fileId: renameForm.value.fileId, name: renameForm.value.name.trim() })
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
  // 上传前类型白名单预检：后端 applyUploadToken 会校验 contentType 落白名单，前端先按文件名
  // 扩展名预检，不通过直接提示允许类型清单，避免一次失败往返（白名单空=不限制=放行）
  const check = await checkFileAllowed(file, PROJECT_BUSINESS_TYPE.DOC)
  if (!check.ok) {
    ElMessage.warning(check.reason ?? '该文件类型不在允许范围')
    if (fileInputRef.value) fileInputRef.value.value = ''
    return
  }
  uploading.value = true
  uploadPercent.value = 0
  try {
    const result = await presignedUploadFlow({
      file,
      businessType: PROJECT_BUSINESS_TYPE.DOC,
      /** access 按项目等级派生：L1（及未知等级）→PUBLIC（公开）/L2/L3→PRIVATE（私有）。项目层已 canDownload 鉴权，
       *  文件层走 bizAuthorized=true 跳过 owner 闸；access 仅决定 PUBLIC 直 /file/public vs PRIVATE 走 /file/proxy。
       *  level 缺省按最低等级 L1=公开处理。 */
      access: props.level != null && props.level >= 2 ? 'PRIVATE' : 'PUBLIC',
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
    const result = await downloadProjectFileAuthoringApi(node.fileId)
    const url = result.data
    if (url) {
      window.open(url, '_blank', 'noopener')
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
  <div v-if="recursive && node" class="kh-file-tree__node">
    <div class="kh-file-tree__row">
      <button
        class="kh-file-tree__caret"
        :class="{ 'is-leaf': node.isDir !== PROJECT_FILE_IS_DIR }"
        type="button"
        @click="node.isDir === PROJECT_FILE_IS_DIR ? toggleExpand() : undefined"
      >
        <KhIcon v-if="node.isDir === PROJECT_FILE_IS_DIR" :name="expanded ? 'chevron-right' : 'chevron-right'" :size="12" :class="{ 'is-expanded': expanded }" />
      </button>
      <span class="kh-file-tree__icon">
        <KhIcon :name="node.isDir === PROJECT_FILE_IS_DIR ? 'project' : 'file'" :size="14" />
      </span>
      <span
        class="kh-file-tree__name"
        :class="{ 'is-downloadable': node.isDir !== PROJECT_FILE_IS_DIR && canDownload }"
        :title="node.name"
        @click="node.isDir !== PROJECT_FILE_IS_DIR && canDownload ? handleDownload(node) : undefined"
      >
        {{ node.name }}
      </span>
      <span v-if="node.isDir !== PROJECT_FILE_IS_DIR && node.contentLength != null" class="kh-file-tree__size">
        {{ formatSize(node.contentLength) }}
      </span>
      <span v-if="node.isDir !== PROJECT_FILE_IS_DIR && node.businessType" class="kh-file-tree__biz">
        <ElTag size="small" :type="bizTagType(node.businessType)">{{ bizLabel[node.businessType] ?? node.businessType }}</ElTag>
      </span>
      <span class="kh-file-tree__actions">
        <button
          v-if="node.isDir === PROJECT_FILE_IS_DIR && canEdit"
          class="kh-file-tree__btn"
          title="新建文件夹"
          @click.stop="openFolderDialog(node.fileId)"
        >
          <KhIcon name="project" :size="12" />
        </button>
        <button
          v-if="node.isDir === PROJECT_FILE_IS_DIR && canEdit"
          class="kh-file-tree__btn"
          title="上传文件"
          @click.stop="triggerFilePicker(node.fileId)"
        >
          <KhIcon name="download" :size="12" style="transform: rotate(180deg)" />
        </button>
        <button v-if="canEdit" class="kh-file-tree__btn" title="重命名" @click.stop="openRenameDialog(node)">
          <KhIcon name="more" :size="12" />
        </button>
        <button
          v-if="canEdit"
          class="kh-file-tree__btn kh-file-tree__btn--danger"
          title="删除"
          @click.stop="handleDelete(node)"
        >
          ✕
        </button>
      </span>
    </div>
    <div v-if="node.isDir === PROJECT_FILE_IS_DIR && expanded && node.children?.length" class="kh-file-tree__children">
      <ProjectFileTree
        v-for="child in node.children"
        :key="child.fileId"
        :node="child"
        :recursive="true"
        :can-edit="canEdit"
        :can-download="canDownload"
        :level="level"
      />
    </div>
  </div>

  <!-- 容器模式：拉全树 + 顶部操作栏 + 根节点列表 -->
  <div v-else class="kh-file-tree">
    <div v-if="canEdit" class="kh-file-tree__toolbar">
      <ElButton size="small" @click="openFolderDialog(undefined)">新建文件夹</ElButton>
      <ElButton size="small" type="primary" @click="triggerFilePicker(undefined)">上传文件</ElButton>
      <span v-if="uploading" class="kh-file-tree__uploading">上传中 {{ uploadPercent }}%</span>
      <span v-else-if="allowedHint" class="kh-file-tree__hint" title="当前业务类型允许的文件类型">支持 {{ allowedHint }}</span>
    </div>

    <input ref="fileInputRef" type="file" class="kh-file-tree__input" @change="handleFileChange" />

    <div v-loading="loading" class="kh-file-tree__body">
      <template v-if="tree.length > 0">
        <ProjectFileTree
          v-for="node in tree"
          :key="node.fileId"
          :node="node"
          :recursive="true"
          :can-edit="canEdit"
          :can-download="canDownload"
          :level="level"
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
.kh-file-tree {
  display: grid;
  gap: 10px;
}

.kh-file-tree__toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.kh-file-tree__input {
  display: none;
}

.kh-file-tree__uploading {
  color: var(--kh-text-secondary);
  font-size: var(--kh-font-size-sm);
}

.kh-file-tree__hint {
  color: var(--kh-text-tertiary);
  font-size: 12px;
  font-family: var(--kh-font-mono);
}

.kh-file-tree__body {
  min-height: 80px;
  padding: 6px 0;
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius-md);
  background: var(--kh-surface-muted);
}

/* 递归节点样式 */
.kh-file-tree__node {
  display: grid;
}

.kh-file-tree__row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  font-size: var(--kh-font-size-sm);
  color: var(--kh-text);
}

.kh-file-tree__row:hover {
  background: var(--kh-surface-muted);
}

.kh-file-tree__caret {
  width: 16px;
  height: 16px;
  display: grid;
  place-items: center;
  border: none;
  background: transparent;
  cursor: pointer;
  user-select: none;
  color: var(--kh-text-tertiary);
  transition: transform var(--kh-transition-fast);
}
.kh-file-tree__caret.is-leaf {
  cursor: default;
}
.kh-file-tree__caret :deep(.is-expanded) {
  transform: rotate(90deg);
}

.kh-file-tree__icon {
  flex-shrink: 0;
  display: inline-flex;
  color: var(--kh-text-secondary);
}

.kh-file-tree__name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}

.kh-file-tree__name.is-downloadable {
  cursor: pointer;
  color: var(--kh-primary);
}

.kh-file-tree__name.is-downloadable:hover {
  text-decoration: underline;
}

.kh-file-tree__size {
  color: var(--kh-text-tertiary);
  font-size: 12px;
  flex-shrink: 0;
  font-family: var(--kh-font-mono);
}

.kh-file-tree__biz {
  flex-shrink: 0;
}

.kh-file-tree__actions {
  display: flex;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.15s;
}

.kh-file-tree__row:hover .kh-file-tree__actions {
  opacity: 1;
}

.kh-file-tree__btn {
  border: none;
  background: transparent;
  color: var(--kh-text-secondary);
  cursor: pointer;
  padding: 0 4px;
  font-size: 13px;
  border-radius: var(--kh-radius-sm);
  display: inline-flex;
  align-items: center;
}

.kh-file-tree__btn:hover {
  color: var(--kh-primary);
  background: var(--kh-primary-soft);
}

.kh-file-tree__btn--danger:hover {
  color: var(--kh-danger);
  background: var(--kh-danger-soft);
}

.kh-file-tree__children {
  padding-left: 18px;
}
</style>
