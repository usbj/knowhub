<!--
  资源上传 /resource/upload（editorial 创作页，与博客/文章 create.vue 顶栏范式对齐）
  ------------------------------------------------------------------
  支持两种资源类型：FILE 文件（presignedUploadFlow RESOURCE_FILE/PRIVATE 直传后端，两模式自适应）/
  LINK 链接（仅传 linkUrl；图标 URL 字段暂移除，"是否做封面"待讨论再定）。分类用 resource_category_id
  分类树（el-cascader 树选择），-1=其他前端硬编码。底部双按钮：存草稿 / 发布。
  状态机：新建存草稿后跳 /profile?tab=resource&t=<ts> 触发重拉；编辑 ?id=xxx 走 getMyResourceForEditApi 回填。
  已发布态先撤回才能编辑/换源（后端 editResourceInfo 已挡 PUBLISHED/PENDING_REVIEW）——前端据此隐藏换文件入口。
  顶栏：满宽 header + 内部 max-width 860px 居中限宽 + 半透明毛玻璃；标题输入无边框大字（与博客/文章一致），
  返回/标题/状态在左紧贴、按钮组在右（space-between），状态用中性 mono 小标签不再定制 is-pending 色。
-->
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, UploadFilled } from '@element-plus/icons-vue'
import KhIcon from '@/components/common/KhIcon.vue'
import KhMarkdownEditor from '@/components/common/KhMarkdownEditor.vue'
import {
  addMyResourceApi,
  editMyResourceApi,
  publishMyResourceApi,
  revokeMyResourceApi,
  getMyResourceForEditApi,
} from '@/api/knowhub/resource-authoring'
import { getResourceCategoryTreeApi } from '@/api/knowhub/resource-portal'
import type { ResourceAuthoringPayload } from '@/types/api/knowhub/resource-authoring'
import { RESOURCE_BUSINESS_TYPE } from '@/types/api/knowhub/resource-authoring'
import type { ResourceCategoryTreeNode } from '@/types/api/knowhub/resource'
import { presignedUploadFlow } from '@/utils/upload'
import { checkFileAllowed, formatAllowedHint } from '@/utils/upload-whitelist'

const route = useRoute()
const router = useRouter()

/** 资源类型枚举（与后端 ResourceType 对齐：FILE 文件 / LINK 链接） */
type ResType = 'FILE' | 'LINK'

const form = ref<{
  resourceId?: number
  resourceType: ResType
  title: string
  summary: string
  description: string
  fileObjectId: number | null
  originalName: string
  contentLength: number | null
  /** FILE 访问语义：PUBLIC 公开（任何人下）/ PRIVATE 私有（鉴权后可下）；缺省 PRIVATE。
   *  落 file_object.access（presignedUploadFlow 传 access），资源主表不存此字段。
   *  已发布资源先撤回才能改（与换源同口径，canEditNow 守卫）。 */
  fileAccess: 'PUBLIC' | 'PRIVATE'
  linkUrl: string
  resourceCategoryId: number | null
}>({
  resourceType: 'FILE',
  title: '',
  summary: '',
  description: '',
  fileObjectId: null,
  originalName: '',
  contentLength: null,
  fileAccess: 'PRIVATE',
  linkUrl: '',
  resourceCategoryId: null,
})

const resourceStatus = ref<string>('')
const isEdit = computed(() => route.query.id !== undefined)
const editId = computed(() => (route.query.id ? Number(route.query.id) : undefined))
const isPublished = computed(() => resourceStatus.value === 'PUBLISHED')
const canEditNow = computed(() => ['DRAFT', 'REJECTED', 'REVOKED', ''].includes(resourceStatus.value))

/** 分类树（el-cascader 用），-1=其他前端硬编码叶子节点追加到根列表 */
const categoryTree = ref<ResourceCategoryTreeNode[]>([])
const cascaderOptions = computed<ResourceCategoryTreeNode[]>(() => {
  const other: ResourceCategoryTreeNode = {
    categoryId: -1,
    parentId: 0,
    categoryName: '其他',
    children: undefined,
  }
  return [...categoryTree.value, other]
})
const cascaderProps = {
  value: 'categoryId',
  label: 'categoryName',
  children: 'children',
  checkStrictly: true,
  emitPath: false,
}

/** 文件上传状态 */
const uploading = ref(false)
const uploadProgress = ref(0)
const allowedHint = ref('不限类型')

/** 拉分类树 + 允许类型提示 */
const fetchInit = async () => {
  try {
    const res = await getResourceCategoryTreeApi()
    categoryTree.value = res.data ?? []
  } catch {
    categoryTree.value = []
  }
  try {
    allowedHint.value = await formatAllowedHint(RESOURCE_BUSINESS_TYPE.FILE)
  } catch {
    allowedHint.value = '不限类型'
  }
}

const switchType = (t: ResType) => {
  if (!canEditNow.value) {
    ElMessage.warning('已发布资源请先撤回再切换类型')
    return
  }
  form.value.resourceType = t
}

/** 切换公开/私有访问语义：仅可编辑态可切（与换源同口径）；切后已上传文件不变，新选择仅影响后续签发口。
 *  注：已上传的旧文件 access 落在 file_object 行不随此切换改写——切 PUBLIC 后旧 PRIVATE 文件仍 PRIVATE，
 *  若要生效需换源重传（与"已发布禁换源"状态机一致，避免半切状态）。 */
const switchAccess = (a: 'PUBLIC' | 'PRIVATE') => {
  if (!canEditNow.value) {
    ElMessage.warning('已发布资源请先撤回再切换访问语义')
    return
  }
  form.value.fileAccess = a
}

const handleFileSelect = async (file: File) => {
  if (!canEditNow.value) {
    ElMessage.warning('已发布资源请先撤回再换源')
    return
  }
  // 白名单预检（与后端 applyUploadToken 口径一致）
  const chk = await checkFileAllowed(file, RESOURCE_BUSINESS_TYPE.FILE)
  if (!chk.ok) {
    ElMessage.error(chk.reason ?? '该文件类型不在允许范围')
    return
  }
  uploading.value = true
  uploadProgress.value = 0
  try {
    const result = await presignedUploadFlow({
      file,
      businessType: RESOURCE_BUSINESS_TYPE.FILE,
      access: form.value.fileAccess,
      onProgress: (p) => {
        uploadProgress.value = p
      },
    })
    form.value.fileObjectId = result.objectId
    form.value.originalName = file.name
    form.value.contentLength = file.size
    ElMessage.success('文件上传成功')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '文件上传失败')
  } finally {
    uploading.value = false
    uploadProgress.value = 0
  }
}

const onFileInputChange = (e: Event) => {
  const target = e.target as HTMLInputElement
  const file = target.files?.[0]
  if (file) void handleFileSelect(file)
  target.value = ''
}

const removeFile = () => {
  if (!canEditNow.value) {
    ElMessage.warning('已发布资源请先撤回再换源')
    return
  }
  form.value.fileObjectId = null
  form.value.originalName = ''
  form.value.contentLength = null
}

/** 字节大小 → B/KB/MB/GB，与 ResourceCard formatSize 口径一致 */
const formatSize = (len?: number | null) => {
  if (len == null) return '--'
  if (len < 1024) return `${len} B`
  if (len < 1024 * 1024) return `${(len / 1024).toFixed(1)} KB`
  if (len < 1024 * 1024 * 1024) return `${(len / 1024 / 1024).toFixed(1)} MB`
  return `${(len / 1024 / 1024 / 1024).toFixed(2)} GB`
}

const validate = (): boolean => {
  if (!form.value.title.trim()) {
    ElMessage.warning('请输入资源标题')
    return false
  }
  if (form.value.resourceType === 'FILE' && !form.value.fileObjectId) {
    ElMessage.warning('文件类资源须上传文件')
    return false
  }
  if (form.value.resourceType === 'LINK' && !form.value.linkUrl.trim()) {
    ElMessage.warning('链接类资源须填写链接 URL')
    return false
  }
  return true
}

const buildPayload = (): ResourceAuthoringPayload => {
  const payload: ResourceAuthoringPayload = {
    resourceId: form.value.resourceId,
    resourceType: form.value.resourceType,
    title: form.value.title.trim(),
    summary: form.value.summary.trim() || undefined,
    description: form.value.description.trim() || undefined,
    resourceCategoryId: form.value.resourceCategoryId ?? undefined,
  }
  if (form.value.resourceType === 'FILE') {
    payload.fileObjectId = form.value.fileObjectId ?? undefined
  } else {
    payload.linkUrl = form.value.linkUrl.trim() || undefined
  }
  return payload
}

/** 存草稿：新建跳 profile"我的资源"列表；编辑态留本页提示 */
const handleSaveDraft = async () => {
  if (!validate()) return
  try {
    if (form.value.resourceId && isEdit.value) {
      await editMyResourceApi(buildPayload())
      ElMessage.success('草稿已保存')
    } else {
      await addMyResourceApi(buildPayload())
      ElMessage.success('资源草稿已保存，即将返回我的资源')
      router.push({ path: '/profile', query: { tab: 'resource', t: String(Date.now()) } })
    }
  } catch {
    /* http 拦截器已提示 */
  }
}

/** 发布：需先保存草稿拿到 resourceId */
const handlePublish = async () => {
  if (!validate()) return
  if (!form.value.resourceId) {
    ElMessage.warning('请先保存草稿，再从我的资源列表进入发布')
    return
  }
  try {
    if (canEditNow.value) {
      await editMyResourceApi(buildPayload())
    }
    await publishMyResourceApi(form.value.resourceId)
    ElMessage.success('发布已提交（若开启审核将进入待审核）')
    resourceStatus.value = 'PENDING_REVIEW'
  } catch {
    /* http 拦截器已提示 */
  }
}

/** 撤回：仅 PUBLISHED 可撤回 */
const handleRevoke = async () => {
  if (!form.value.resourceId) return
  try {
    await ElMessageBox.confirm('撤回后将转为已撤回态，可继续编辑/换源后重新发布', '确认撤回', {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await revokeMyResourceApi(form.value.resourceId)
    ElMessage.success('已撤回，可继续编辑/换源')
    resourceStatus.value = 'REVOKED'
  } catch {
    /* http 拦截器已提示 */
  }
}

const statusText = computed(() => {
  const m: Record<string, string> = {
    DRAFT: '草稿',
    PUBLISHED: '已发布',
    REVOKED: '已撤回',
    PENDING_REVIEW: '待审核',
    REJECTED: '已驳回',
  }
  return resourceStatus.value ? (m[resourceStatus.value] ?? resourceStatus.value) : ''
})

const saving = computed(() => uploading.value)
const fetchForEdit = async (id: number) => {
  try {
    const res = await getMyResourceForEditApi(id)
    const b = res.data
    if (!b) {
      ElMessage.error('资源不存在或无权查看')
      router.back()
      return
    }
    form.value.resourceId = b.resourceId
    form.value.resourceType = (b.resourceType as ResType) || 'FILE'
    form.value.title = b.title ?? ''
    form.value.summary = b.summary ?? ''
    form.value.description = b.description ?? ''
    form.value.fileObjectId = b.fileObjectId ?? null
    form.value.originalName = b.originalName ?? ''
    form.value.contentLength = b.contentLength ?? null
    form.value.fileAccess = (b.fileAccess as 'PUBLIC' | 'PRIVATE') === 'PUBLIC' ? 'PUBLIC' : 'PRIVATE'
    form.value.linkUrl = b.linkUrl ?? ''
    form.value.resourceCategoryId = b.resourceCategoryId ?? null
    resourceStatus.value = b.status ?? ''
  } catch {
    router.back()
  }
}

onMounted(async () => {
  await fetchInit()
  if (isEdit.value && editId.value) {
    void fetchForEdit(editId.value)
  }
})
</script>

<template>
  <div class="ru">
    <!-- 顶部工具条（满宽 + 内部居中限宽，与博客/文章创作页 create__bar 范式一致） -->
    <header class="ru__bar">
      <div class="ru__bar-inner">
        <div class="ru__bar-left">
          <button class="ru__back" type="button" @click="router.back()">
            <el-icon><ArrowLeft /></el-icon> 返回
          </button>
          <input v-model="form.title" class="ru__title-input" placeholder="输入资源标题…" maxlength="100" />
          <span v-if="statusText" class="ru__status">{{ statusText }}</span>
        </div>
        <div class="ru__bar-right">
          <button v-if="isPublished" class="ru__btn ru__btn--warn" type="button" @click="handleRevoke">撤回</button>
          <button class="ru__btn ru__btn--ghost" type="button" :disabled="saving" @click="handleSaveDraft">
            {{ saving ? '保存中…' : '存草稿' }}
          </button>
          <button
            class="ru__btn ru__btn--primary"
            type="button"
            :disabled="saving || isPublished"
            @click="handlePublish"
          >发布</button>
        </div>
      </div>
    </header>

    <div class="ru__wrap">
      <!-- 资源类型选择 -->
      <section class="ru__section">
        <div class="ru__section-title">资源类型</div>
        <div class="ru__type-group">
          <button
            class="ru__type-btn"
            :class="{ 'is-active': form.resourceType === 'FILE' }"
            type="button"
            :disabled="!canEditNow"
            @click="switchType('FILE')"
          >
            <KhIcon name="file" :size="18" /> 文件资源
            <span class="ru__type-sub">上传一个文件供下载</span>
          </button>
          <button
            class="ru__type-btn"
            :class="{ 'is-active': form.resourceType === 'LINK' }"
            type="button"
            :disabled="!canEditNow"
            @click="switchType('LINK')"
          >
            <KhIcon name="link" :size="18" /> 链接资源
            <span class="ru__type-sub">引用一个外部网站/工具链接</span>
          </button>
        </div>
      </section>

      <!-- 文件上传（FILE 类型） -->
      <section v-if="form.resourceType === 'FILE'" class="ru__section">
        <div class="ru__section-title">
          文件
          <span class="ru__section-hint">允许：{{ allowedHint }}</span>
        </div>
        <div v-if="!canEditNow" class="ru__notice">
          已发布资源请先撤回再换源（PUBLISHED 禁止原地编辑，撤回后可换文件再发布）。
        </div>
        <div v-if="form.fileObjectId && form.originalName" class="ru__file-card">
          <div class="ru__file-icon"><KhIcon name="file" :size="24" /></div>
          <div class="ru__file-info">
            <div class="ru__file-name">{{ form.originalName }}</div>
            <div class="ru__file-meta">{{ formatSize(form.contentLength) }}</div>
          </div>
          <button v-if="canEditNow" class="ru__file-remove" type="button" @click="removeFile">更换</button>
        </div>
        <div v-else-if="!uploading" class="ru__upload">
          <label class="ru__upload-label">
            <input class="ru__upload-input" type="file" :disabled="!canEditNow" @change="onFileInputChange" />
            <el-icon class="ru__upload-icon"><UploadFilled :size="32" /></el-icon>
            <span>点击选择文件上传</span>
            <span class="ru__upload-sub">{{ allowedHint }}</span>
          </label>
        </div>
        <div v-if="uploading" class="ru__progress">
          <el-progress :percentage="uploadProgress" :stroke-width="6" />
          <span>上传中 {{ uploadProgress }}%</span>
        </div>
      </section>

      <!-- 访问语义（仅 FILE 类型，PUBLIC 任何人可下 / PRIVATE 鉴权可下） -->
      <section v-if="form.resourceType === 'FILE'" class="ru__section">
        <div class="ru__section-title">
          访问语义
          <span class="ru__section-hint">控制谁能下载该文件</span>
        </div>
        <div class="ru__access-group">
          <button
            class="ru__access-btn"
            :class="{ 'is-active': form.fileAccess === 'PRIVATE' }"
            type="button"
            :disabled="!canEditNow"
            @click="switchAccess('PRIVATE')"
          >
            <KhIcon name="lock" :size="18" /> 私有
            <span class="ru__type-sub">登录并经业务鉴权可下</span>
          </button>
          <button
            class="ru__access-btn"
            :class="{ 'is-active': form.fileAccess === 'PUBLIC' }"
            type="button"
            :disabled="!canEditNow"
            @click="switchAccess('PUBLIC')"
          >
            <KhIcon name="eye" :size="18" /> 公开
            <span class="ru__type-sub">登录用户可下（不走匿名直链）</span>
          </button>
        </div>
      </section>

      <!-- 链接信息（LINK 类型）：仅链接 URL，图标 URL 暂移除（讨论是否做封面再说） -->
      <section v-else class="ru__section">
        <div class="ru__section-title">链接信息</div>
        <div class="ru__field">
          <label class="ru__label">链接 URL <span class="ru__req">*</span></label>
          <input v-model="form.linkUrl" class="ru__input" placeholder="https://example.com" :disabled="!canEditNow" />
        </div>
      </section>

      <!-- 元信息：分类 + 摘要 -->
      <section class="ru__section">
        <div class="ru__section-title">元信息</div>
        <div class="ru__field">
          <label class="ru__label">分类</label>
          <el-cascader
            v-model="form.resourceCategoryId"
            :options="cascaderOptions"
            :props="cascaderProps"
            placeholder="不选默认其他"
            clearable
            :disabled="!canEditNow"
          />
        </div>
        <div class="ru__field">
          <label class="ru__label">摘要</label>
          <textarea v-model="form.summary" class="ru__textarea" rows="2" placeholder="资源一句话简介（列表卡片展示用）" :disabled="!canEditNow" />
        </div>
      </section>

      <!-- 正文（Markdown） -->
      <section class="ru__section">
        <div class="ru__section-title">详细说明（可选，支持 Markdown）</div>
        <div class="ru__editor">
          <KhMarkdownEditor
            v-if="canEditNow"
            v-model="form.description"
            height="380px"
          />
          <div v-else class="ru__desc-readonly" :class="{ 'is-empty': !form.description.trim() }">
            {{ form.description.trim() || '已发布资源正文展示用预览，撤回后可编辑' }}
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.ru {
  display: flex;
  flex-direction: column;
  min-height: calc(100vh - var(--kh-header-height));
  background: var(--kh-bg);
}

/* —— 顶部工具条（满宽，内部居中限宽，与博客/文章创作页 create__bar 范式对齐） —— */
.ru__bar {
  position: sticky;
  top: var(--kh-header-height);
  z-index: 10;
  background: color-mix(in srgb, var(--kh-surface) 92%, transparent);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid var(--kh-border-soft);
  flex: none;
}
.ru__bar-inner {
  max-width: 860px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kh-space-4);
  padding: var(--kh-space-3) var(--kh-space-5);
}
.ru__bar-left {
  display: flex;
  align-items: center;
  gap: var(--kh-space-3);
  flex: 1;
  min-width: 0;
}
.ru__back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius-sm);
  background: var(--kh-surface);
  color: var(--kh-text-secondary);
  font-size: var(--kh-font-size-sm);
  cursor: pointer;
  flex: none;
  transition: all var(--kh-transition-fast);
}
.ru__back:hover {
  border-color: var(--kh-primary-border);
  color: var(--kh-primary);
}
.ru__title-input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  font-size: var(--kh-font-size-xl);
  font-weight: 700;
  color: var(--kh-text);
}
.ru__title-input::placeholder {
  color: var(--kh-text-tertiary);
  font-weight: 600;
}
.ru__status {
  font-size: 12px;
  color: var(--kh-text-tertiary);
  padding: 2px 10px;
  border-radius: var(--kh-radius-pill);
  background: var(--kh-bg-soft);
  flex: none;
  font-family: var(--kh-font-mono);
}
.ru__bar-right {
  display: flex;
  align-items: center;
  gap: var(--kh-space-2);
  flex: none;
}
.ru__btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 38px;
  padding: 0 var(--kh-space-5);
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius-pill);
  background: var(--kh-surface);
  color: var(--kh-text-secondary);
  font-weight: 600;
  font-size: var(--kh-font-size-sm);
  cursor: pointer;
  transition: all var(--kh-transition-fast);
}
.ru__btn:hover:not(:disabled) {
  border-color: var(--kh-primary-border);
  color: var(--kh-primary);
}
.ru__btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.ru__btn--primary {
  background: var(--kh-primary);
  border-color: var(--kh-primary);
  color: #fff;
}
.ru__btn--primary:hover:not(:disabled) {
  background: var(--kh-primary-strong);
  color: #fff;
}
.ru__btn--warn {
  border-color: var(--kh-warm);
  color: var(--kh-warm);
}
.ru__btn--warn:hover:not(:disabled) {
  background: var(--kh-warm-soft, rgba(245, 158, 11, 0.12));
}

/* —— 主体限宽容器 —— */
.ru__wrap {
  max-width: 860px;
  width: 100%;
  margin: 0 auto;
  padding: var(--kh-space-6) var(--kh-space-5) var(--kh-space-12);
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-6);
}

.ru__section {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-3);
}
.ru__section-title {
  font-size: var(--kh-font-size-md);
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
}
.ru__section-hint {
  font-size: 12px;
  color: var(--kh-text-tertiary);
  font-weight: 400;
}
.ru__type-group {
  display: flex;
  gap: var(--kh-space-3);
}
.ru__type-btn {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: var(--kh-space-4);
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius);
  background: var(--kh-surface);
  cursor: pointer;
  color: var(--kh-text-secondary);
  transition: all var(--kh-transition-fast);
}
.ru__type-btn:hover:not(:disabled) {
  border-color: var(--kh-primary-border);
  color: var(--kh-primary);
}
.ru__type-btn.is-active {
  border-color: var(--kh-primary);
  background: var(--kh-primary-soft);
  color: var(--kh-primary-strong);
}
.ru__type-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.ru__type-sub {
  font-size: 11px;
  color: var(--kh-text-tertiary);
  font-weight: 400;
}

.ru__access-group {
  display: flex;
  gap: var(--kh-space-3);
}
.ru__access-btn {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: var(--kh-space-4);
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius);
  background: var(--kh-surface);
  cursor: pointer;
  color: var(--kh-text-secondary);
  transition: all var(--kh-transition-fast);
}
.ru__access-btn:hover:not(:disabled) {
  border-color: var(--kh-primary-border);
  color: var(--kh-primary);
}
.ru__access-btn.is-active {
  border-color: var(--kh-primary);
  background: var(--kh-primary-soft);
  color: var(--kh-primary-strong);
}
.ru__access-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.ru__notice {
  padding: 8px 12px;
  background: var(--kh-warn-soft, rgba(245, 158, 11, 0.1));
  border-radius: var(--kh-radius-sm);
  color: var(--kh-warm);
  font-size: 12px;
}
.ru__file-card {
  display: flex;
  align-items: center;
  gap: var(--kh-space-3);
  padding: var(--kh-space-3) var(--kh-space-4);
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius);
  background: var(--kh-surface);
}
.ru__file-icon {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  background: var(--kh-bg-soft);
  border-radius: var(--kh-radius-sm);
  color: var(--kh-primary);
}
.ru__file-info {
  flex: 1;
  min-width: 0;
}
.ru__file-name {
  font-size: 13px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ru__file-meta {
  font-size: 11px;
  color: var(--kh-text-tertiary);
  font-family: var(--kh-font-mono);
  margin-top: 2px;
}
.ru__file-remove {
  padding: 4px 10px;
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius-sm);
  background: var(--kh-surface);
  color: var(--kh-text-secondary);
  font-size: 12px;
  cursor: pointer;
}
.ru__file-remove:hover {
  border-color: var(--kh-primary-border);
  color: var(--kh-primary);
}
.ru__upload {
  border: 1.5px dashed var(--kh-border);
  border-radius: var(--kh-radius);
  padding: var(--kh-space-8);
  text-align: center;
}
.ru__upload-label {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  color: var(--kh-text-secondary);
}
.ru__upload-label:hover {
  color: var(--kh-primary);
}
.ru__upload-input {
  display: none;
}
.ru__upload-icon {
  color: var(--kh-text-tertiary);
}
.ru__upload-sub {
  font-size: 11px;
  color: var(--kh-text-tertiary);
}
.ru__progress {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: var(--kh-space-4);
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius);
  font-size: 12px;
  color: var(--kh-text-secondary);
}
.ru__progress :deep(.el-progress) {
  width: 100%;
}

.ru__field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.ru__label {
  font-size: 12px;
  color: var(--kh-text-tertiary);
  font-weight: 500;
}
.ru__req {
  color: var(--kh-danger);
}
.ru__input {
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius-sm);
  padding: 8px 12px;
  font-size: var(--kh-font-size-md);
  background: var(--kh-surface);
}
.ru__input:focus {
  outline: none;
  border-color: var(--kh-primary);
}
.ru__input:disabled,
.ru__textarea:disabled {
  background: var(--kh-surface-muted);
  cursor: not-allowed;
}
.ru__textarea {
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius-sm);
  padding: 8px 12px;
  font-size: var(--kh-font-size-md);
  background: var(--kh-surface);
  resize: vertical;
  font-family: inherit;
}
.ru__textarea:focus {
  outline: none;
  border-color: var(--kh-primary);
}
.ru__editor {
  border-radius: var(--kh-radius);
  overflow: hidden;
}
.ru__desc-readonly {
  min-height: 120px;
  padding: var(--kh-space-4);
  background: var(--kh-surface-muted);
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius);
  color: var(--kh-text-secondary);
  font-size: var(--kh-font-size-md);
  line-height: 1.8;
  white-space: pre-wrap;
}
.ru__desc-readonly.is-empty {
  color: var(--kh-text-tertiary);
  font-style: italic;
}

@media (max-width: 768px) {
  .ru__bar-left {
    flex-wrap: wrap;
  }
  .ru__title-input {
    order: 3;
    flex-basis: 100%;
  }
  .ru__type-group {
    flex-direction: column;
  }
  .ru__access-group {
    flex-direction: column;
  }
}
</style>