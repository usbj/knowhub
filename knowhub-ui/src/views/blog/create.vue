<!--
  博客创作 /blog/create（CSDN/掘进 风格 editorial 全屏创作页）
  ------------------------------------------------------------------
  设计：content-first，整体居中限宽（max 860px）+ 顶部工具条 + 正文编辑区为主、
  元信息（封面/标签 chip/等级 chip/摘要）折叠面板置于正文下方。
  - 正文编辑区不占满整宽，左右留白；v-md-editor 工具栏/拖拽/粘贴插图走预签名直传 BLOG_BODY+PUBLIC。
  - 标签不用显式输入框：全量启用标签平铺成可点 chip，点选高亮/取消，已选置前；轻量搜索框过滤 chip。
  - 等级用按钮组（L1/L2/L3），按 getMyBlogLevelApi 禁用不可选，后端 assertCanCreateLevel 兜底。
  状态机：
  - 新建：存草稿调 draftBlogApi 成功后跳回 /profile?tab=blog&t=<ts> 触发"我的博客"重拉，从列表进编辑拿到 blogId。
  - 编辑：路由 ?id=xxx，onMounted 回填 getBlogForEditApi。已发布态先撤回才能编辑。
  -->
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElSelect, ElOption } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import KhIcon from '@/components/common/KhIcon.vue'
import KhMarkdownEditor from '@/components/common/KhMarkdownEditor.vue'
import BlogCoverUploader from '@/components/blog/BlogCoverUploader.vue'
import {
  draftBlogApi,
  editBlogApi,
  publishBlogApi,
  revokeBlogApi,
  getBlogForEditApi,
  getMyBlogLevelApi,
} from '@/api/knowhub/authoring'
import { listEnabledTagsApi } from '@/api/knowhub/blog'
import { parseMdFile } from '@/utils/md-import'
import type { BlogAuthoringPayload } from '@/types/api/knowhub/authoring'
import type { TagRecord } from '@/types/api/knowhub/tag'

const route = useRoute()
const router = useRouter()

/** 表单模型（与 BlogAuthoringPayload 对齐） */
const form = ref<{
  blogId?: number
  title: string
  content: string
  summary: string
  coverUrl: string
  level: number
  tagIds: number[]
}>({
  title: '',
  content: '',
  summary: '',
  coverUrl: '',
  level: 1,
  tagIds: [],
})

/** 当前博客状态（编辑模式回填，驱动按钮态） */
const blogStatus = ref<string>('')
const isEdit = computed(() => route.query.id !== undefined)
const editId = computed(() => (route.query.id ? Number(route.query.id) : undefined))

/** 当前用户 view 等级（0/1/2/3），等级选项据此禁用 */
const myLevel = ref<number>(0)

/** 全部启用标签（el-select 多选数据源，filterable 本地过滤） */
const tagOptions = ref<TagRecord[]>([])

/** 元信息折叠态（默认折叠，写完正文再展开补元信息——content-first 创作流） */
const metaExpanded = ref(false)

const saving = ref(false)
const publishing = ref(false)

/** 等级选项：禁用态按用户 view 等级算 */
const levelOptions = computed(() => [
  { value: 1, label: '公开', sub: '所有人可见', disabled: false },
  { value: 2, label: '内部', sub: '登录成员可见', disabled: myLevel.value < 2 },
  { value: 3, label: '机密', sub: '高权限可见', disabled: myLevel.value < 3 },
])

const isPublished = computed(() => blogStatus.value === 'PUBLISHED')
const canEditNow = computed(() =>
  ['DRAFT', 'REJECTED', 'REVOKED', ''].includes(blogStatus.value),
)

const buildPayload = (): BlogAuthoringPayload => ({
  blogId: form.value.blogId,
  title: form.value.title.trim(),
  content: form.value.content,
  summary: form.value.summary.trim() || undefined,
  coverUrl: form.value.coverUrl.trim() || undefined,
  level: form.value.level,
  tagIds: form.value.tagIds.length ? form.value.tagIds : undefined,
})

const validate = (): boolean => {
  if (!form.value.title.trim()) {
    ElMessage.warning('请输入博客标题')
    return false
  }
  if (!form.value.content.trim()) {
    ElMessage.warning('请输入博客正文')
    return false
  }
  return true
}

/**
 * 存草稿：新建调 draftApi 成功后跳回 profile"我的博客"列表（带 tab=blog + 时间戳触发重拉），
 * 从列表点进编辑即拿到 blogId——不需要 draft 返回 id。
 * 编辑态调 editApi，保存后留在当前页提示。
 */
const handleSaveDraft = async () => {
  if (!validate()) return
  saving.value = true
  try {
    const payload = buildPayload()
    if (form.value.blogId) {
      await editBlogApi(payload)
      ElMessage.success('草稿已保存')
    } else {
      await draftBlogApi(payload)
      ElMessage.success('草稿已保存，即将返回我的博客')
      // 跳 profile 带 tab=blog + 时间戳，触发"我的博客"列表重拉取（新建草稿自然出现在列表里）
      router.push({ path: '/profile', query: { tab: 'blog', t: String(Date.now()) } })
    }
  } finally {
    saving.value = false
  }
}

/** 发布：仅在编辑模式（已有 blogId）可用，先 edit 再 publish */
const handlePublish = async () => {
  if (!validate()) return
  if (!form.value.blogId) {
    ElMessage.warning('请先保存草稿，再从我的博客列表进入发布')
    return
  }
  publishing.value = true
  try {
    if (canEditNow.value) {
      await editBlogApi(buildPayload())
    }
    await publishBlogApi(form.value.blogId)
    ElMessage.success('发布成功')
    blogStatus.value = 'PENDING_REVIEW'
  } finally {
    publishing.value = false
  }
}

/** 撤回：仅 PUBLISHED 可撤回 */
const handleRevoke = async () => {
  const blogId = form.value.blogId
  if (!blogId) return
  try {
    await ElMessageBox.confirm('撤回后将转为已撤回态，可继续编辑后重新发布', '确认撤回', {
      type: 'warning',
    })
  } catch {
    return
  }
  await revokeBlogApi(blogId)
  ElMessage.success('已撤回，可继续编辑')
  blogStatus.value = 'REVOKED'
}

/**
 * v-md-editor 正文插图上传逻辑已下沉到通用组件 KhMarkdownEditor（businessType=BLOG_BODY + PUBLIC，
 * 预签名直传 /file/resolve/{id}）。本页不再自写 handleUploadImage。
 */

/** 隐藏 file input 的 ref，点击导入按钮触发其 click */
const mdFileInput = ref<HTMLInputElement | null>(null)

/**
 * 导入 .md 文件为草稿内容：纯前端解析（FileReader 读文本），本地图片路径就地改写成
 * `[图片：alt]` 单行占位。填 title+content 后展开元信息面板，让作者补等级/标签/封面、
 * 对占位图手动重插图，再点"存草稿"。不自动提交——保留作者检查机会。
 * 复用既有 draftBlogApi，零新后端接口。
 */
const handleImportMd = async (e: Event) => {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  // 清空 input.value 否则同文件二次选不触发 change
  input.value = ''
  if (!file) return
  try {
    const { title, content } = await parseMdFile(file)
    form.value.title = title
    form.value.content = content
    metaExpanded.value = true
    ElMessage.success(`已导入「${title}」，请检查正文并对本地图片占位手动重插图`)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '导入失败')
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
  return blogStatus.value ? (m[blogStatus.value] ?? blogStatus.value) : ''
})

const fetchForEdit = async (blogId: number) => {
  const res = await getBlogForEditApi(blogId)
  const b = res.data
  if (!b) {
    ElMessage.error('博客不存在或无权查看')
    router.back()
    return
  }
  if (b.canEdit === false) {
    ElMessage.error('无权编辑该博客')
    router.back()
    return
  }
  form.value.blogId = b.blogId
  form.value.title = b.title
  form.value.content = b.content
  form.value.summary = b.summary ?? ''
  form.value.coverUrl = b.coverUrl ?? ''
  form.value.level = b.level ?? 1
  form.value.tagIds = b.tagIds ?? []
  blogStatus.value = b.status ?? ''
  if (isPublished.value) {
    ElMessage.info('该博客已发布，编辑需先撤回')
  }
}

onMounted(async () => {
  const [levelRes, tagRes] = await Promise.all([
    getMyBlogLevelApi(),
    listEnabledTagsApi(),
  ])
  myLevel.value = levelRes.data ?? 0
  tagOptions.value = tagRes.data ?? []
  const id = editId.value
  if (id) {
    await fetchForEdit(id)
  }
})
</script>

<template>
  <div class="create">
    <!-- 顶部工具条（顶栏高度对齐 AppHeader，居中限宽） -->
    <header class="create__bar">
      <div class="create__bar-inner">
        <div class="create__bar-left">
          <button class="create__back" type="button" @click="router.back()">
            <el-icon><ArrowLeft /></el-icon> 返回
          </button>
          <input
            v-model="form.title"
            class="create__title-input"
            placeholder="输入博客标题…"
            maxlength="100"
          />
          <span v-if="statusText" class="create__status">{{ statusText }}</span>
        </div>
        <div class="create__bar-right">
          <!-- 隐藏 file input：导入 .md 文件纯前端解析为草稿内容，本地图片路径改写占位 -->
          <input
            ref="mdFileInput"
            type="file"
            accept=".md,.markdown,text/markdown"
            class="create__md-input"
            @change="handleImportMd"
          />
          <button
            class="create__btn create__btn--ghost"
            type="button"
            title="从 .md 文件导入正文（本地图片路径会标为占位，需手动重插图）"
            :disabled="saving || publishing"
            @click="mdFileInput?.click()"
          >
            <KhIcon name="file" :size="14" /> 导入 .md
          </button>
          <button
            class="create__btn create__btn--ghost"
            type="button"
            :disabled="saving || publishing"
            @click="handleSaveDraft"
          >
            {{ saving ? '保存中…' : '存草稿' }}
          </button>
          <button
            v-if="isPublished"
            class="create__btn create__btn--warn"
            type="button"
            :disabled="saving || publishing"
            @click="handleRevoke"
          >
            撤回
          </button>
          <button
            v-else
            class="create__btn create__btn--primary"
            type="button"
            :disabled="saving || publishing || isPublished"
            @click="handlePublish"
          >
            {{ publishing ? '发布中…' : '发布' }}
          </button>
        </div>
      </div>
    </header>

    <!-- 主体：居中限宽容器 -->
    <div class="create__wrap">
      <!-- 正文编辑区为主（v-md-editor 封装到通用组件 KhMarkdownEditor，正文插图走预签名直传 BLOG_BODY+PUBLIC） -->
      <section class="create__editor">
        <KhMarkdownEditor
          v-model="form.content"
          height="100%"
        />
      </section>

      <!-- 元信息折叠面板（正文下方） -->
      <section class="create__meta">
        <button
          class="create__meta-head"
          type="button"
          @click="metaExpanded = !metaExpanded"
        >
          <KhIcon name="tag" :size="14" />
          <span>博客信息</span>
          <span class="create__meta-summary">
            {{ form.level }}级 · {{ form.tagIds.length }} 标签 · {{ form.coverUrl ? '有封面' : '无封面' }}
          </span>
          <span class="create__meta-caret" :class="{ 'is-open': metaExpanded }">▾</span>
        </button>

        <div v-show="metaExpanded" class="create__meta-body">
          <!-- 封面 -->
          <div class="create__field">
            <label class="create__label">封面图</label>
            <BlogCoverUploader v-model="form.coverUrl" />
          </div>

          <!-- 标签：el-select 多选（选中显示为带 × 小方块 tag，filterable 本地过滤） -->
          <div class="create__field">
            <label class="create__label">
              标签
              <span class="create__label-hint">可多选，输入关键词筛选</span>
            </label>
            <ElSelect
              v-model="form.tagIds"
              multiple
              filterable
              collapse-tags
              collapse-tags-tooltip
              placeholder="选择博客标签…"
              class="create__tag-select"
            >
              <ElOption
                v-for="t in tagOptions"
                :key="t.tagId"
                :label="t.tagName"
                :value="t.tagId"
              />
            </ElSelect>
          </div>

          <!-- 等级：按钮组 -->
          <div class="create__field">
            <label class="create__label">可见等级</label>
            <div class="create__level-group">
              <button
                v-for="opt in levelOptions"
                :key="opt.value"
                class="create__level-btn"
                :class="{ 'is-active': form.level === opt.value, 'is-disabled': opt.disabled }"
                type="button"
                :disabled="opt.disabled"
                :title="opt.disabled ? '当前等级不可创建此级别' : opt.sub"
                @click="form.level = opt.value"
              >
                <span class="create__level-name">L{{ opt.value }} {{ opt.label }}</span>
                <span class="create__level-sub">{{ opt.sub }}</span>
              </button>
            </div>
          </div>

          <!-- 摘要 -->
          <div class="create__field">
            <label class="create__label">摘要<span class="create__label-hint">留空则自动截取正文</span></label>
            <textarea
              v-model="form.summary"
              class="create__summary"
              placeholder="一句话概括博客内容（选填）"
              rows="3"
              maxlength="200"
            />
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.create {
  display: flex;
  flex-direction: column;
  min-height: calc(100vh - var(--kh-header-height));
  background: var(--kh-bg);
}

/* —— 顶部工具条（满宽，内部限宽） —— */
.create__bar {
  position: sticky;
  top: var(--kh-header-height);
  z-index: 10;
  background: color-mix(in srgb, var(--kh-surface) 92%, transparent);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid var(--kh-border-soft);
  flex: none;
}
.create__bar-inner {
  max-width: 880px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kh-space-4);
  padding: var(--kh-space-3) var(--kh-space-5);
}
.create__bar-left {
  display: flex;
  align-items: center;
  gap: var(--kh-space-3);
  flex: 1;
  min-width: 0;
}
.create__back {
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
.create__back:hover {
  border-color: var(--kh-primary-border);
  color: var(--kh-primary);
}
.create__title-input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  font-size: var(--kh-font-size-xl);
  font-weight: 700;
  color: var(--kh-text);
}
.create__title-input::placeholder {
  color: var(--kh-text-tertiary);
  font-weight: 600;
}
.create__status {
  font-size: 12px;
  color: var(--kh-text-tertiary);
  padding: 2px 10px;
  border-radius: var(--kh-radius-pill);
  background: var(--kh-bg-soft);
  flex: none;
  font-family: var(--kh-font-mono);
}
.create__bar-right {
  display: flex;
  align-items: center;
  gap: var(--kh-space-2);
  flex: none;
}
.create__btn {
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
.create__btn:hover:not(:disabled) {
  border-color: var(--kh-primary-border);
  color: var(--kh-primary);
}
.create__btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.create__btn--primary {
  background: var(--kh-primary);
  border-color: var(--kh-primary);
  color: #fff;
}
.create__btn--primary:hover:not(:disabled) {
  background: var(--kh-primary-strong);
  color: #fff;
}
.create__btn--warn {
  border-color: var(--kh-warm);
  color: var(--kh-warm);
}
.create__btn--warn:hover:not(:disabled) {
  background: var(--kh-warm-soft);
}
/* 隐藏的 md 文件 input：用按钮触发其 click，自身不占位不显示 */
.create__md-input {
  display: none;
}

/* —— 主体限宽容器 —— */
.create__wrap {
  max-width: 880px;
  width: 100%;
  margin: 0 auto;
  padding: var(--kh-space-6) var(--kh-space-5) var(--kh-space-12);
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-6);
}

/* —— 正文编辑器 —— */
/* 外框（border + 圆角 + 背景）已下沉到通用组件 KhMarkdownEditor 根；本页只负责让编辑器容器
   占满正文区高度（固定视口余量 + 最小高度兜底）。 */
.create__editor {
  position: relative;
  height: calc(100vh - var(--kh-header-height) - 220px);
  min-height: 420px;
}

/* —— 元信息折叠面板 —— */
.create__meta {
  background: var(--kh-surface);
  border: 1px solid var(--kh-border-soft);
  border-radius: var(--kh-radius-lg);
  overflow: hidden;
}
.create__meta-head {
  width: 100%;
  display: flex;
  align-items: center;
  gap: var(--kh-space-2);
  padding: var(--kh-space-4) var(--kh-space-5);
  border: none;
  background: transparent;
  color: var(--kh-text);
  font-size: var(--kh-font-size-md);
  font-weight: 600;
  cursor: pointer;
  transition: background var(--kh-transition-fast);
}
.create__meta-head:hover {
  background: var(--kh-surface-muted);
}
.create__meta-head > :first-child {
  color: var(--kh-primary);
}
.create__meta-summary {
  margin-left: auto;
  font-size: 12px;
  font-weight: 500;
  color: var(--kh-text-tertiary);
  font-family: var(--kh-font-mono);
}
.create__meta-caret {
  color: var(--kh-text-tertiary);
  font-size: 12px;
  transition: transform var(--kh-transition-fast);
}
.create__meta-caret.is-open {
  transform: rotate(180deg);
}
.create__meta-body {
  padding: var(--kh-space-4) var(--kh-space-5) var(--kh-space-6);
  border-top: 1px solid var(--kh-border-soft);
  display: grid;
  gap: var(--kh-space-5);
}
.create__field {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-2);
}
.create__label {
  font-size: var(--kh-font-size-sm);
  font-weight: 600;
  color: var(--kh-text);
  display: flex;
  align-items: center;
  gap: 6px;
}
.create__label-hint {
  font-size: 12px;
  font-weight: 400;
  color: var(--kh-text-tertiary);
}

/* —— 标签选择器（el-select 多选，选中显示带 × 小方块 tag） —— */
.create__tag-select {
  width: 100%;
  max-width: 520px;
}

/* —— 等级按钮组 —— */
.create__level-group {
  display: flex;
  gap: 10px;
}
.create__level-btn {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  padding: 10px 14px;
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius);
  background: var(--kh-surface);
  cursor: pointer;
  transition: all var(--kh-transition-fast);
  text-align: left;
}
.create__level-btn:hover:not(.is-disabled) {
  border-color: var(--kh-primary-border);
  background: var(--kh-primary-soft);
}
.create__level-btn.is-active {
  border-color: var(--kh-primary);
  background: var(--kh-primary-soft);
  box-shadow: var(--kh-shadow-xs);
}
.create__level-btn.is-active .create__level-name {
  color: var(--kh-primary-strong);
}
.create__level-btn.is-disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.create__level-name {
  font-size: var(--kh-font-size-sm);
  font-weight: 600;
  color: var(--kh-text);
}
.create__level-sub {
  font-size: 11px;
  color: var(--kh-text-tertiary);
}

/* —— 摘要 —— */
.create__summary {
  width: 100%;
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius);
  background: var(--kh-surface-muted);
  padding: 10px 14px;
  font-size: var(--kh-font-size-sm);
  color: var(--kh-text);
  resize: vertical;
  outline: none;
  font-family: var(--kh-font-body);
  line-height: 1.6;
  transition: border-color var(--kh-transition-fast);
}
.create__summary:focus {
  border-color: var(--kh-primary-border);
  background: var(--kh-surface);
}

@media (max-width: 768px) {
  .create__wrap {
    padding: var(--kh-space-4) var(--kh-space-3) var(--kh-space-10);
  }
  .create__level-group {
    flex-direction: column;
  }
}
</style>