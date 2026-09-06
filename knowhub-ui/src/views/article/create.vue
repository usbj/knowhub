<!--
  文章创作 /article/create（editorial 创作页，参考博客 create.vue）
  ------------------------------------------------------------------
  文章 = 章节集合（文档站结构）：主表不存正文，正文在 chapter。故文章创作页只有"前言 summary（文档导言）"
  + 文章元信息（等级/可见性/封面/标签），**没有正文编辑器**——正文走章节创作页 /article/:id/chapter/edit。
  - 顶部工具条：返回 / 标题输入 / 状态徽标 / 存草稿 / 发布 / 撤回 / 章节管理（编辑态显示，跳 /article/:id/chapters）。
  - 主体：前言区（v-md-editor 编辑 summary 前言，可选；列表页与详情页都能预览此前言）+ 元信息折叠面板（封面/标签/等级按钮组/可见性按钮组）。
  - 状态机：新建存草稿后跳 /profile?tab=article&t=<ts> 触发"我的文章"重拉，从列表进编辑/章节管理拿到 articleId。
    编辑对应 ?id=xxx，onMounted 回填 getArticleForEditApi。已发布态先撤回才能改元信息。
  - 等级按钮组按 getMyArticleLevelApi 禁用不可选；可见性三档硬编码（PRIVATE/SEMIPUBLIC/PUBLIC，决定章节提交审不审）。
-->
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElSelect, ElOption, ElSwitch } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import KhIcon from '@/components/common/KhIcon.vue'
import KhMarkdownEditor from '@/components/common/KhMarkdownEditor.vue'
import ArticleCoverUploader from '@/components/article/ArticleCoverUploader.vue'
import {
  draftArticleApi,
  editArticleApi,
  publishArticleAuthoringApi,
  revokeArticleAuthoringApi,
  getArticleForEditApi,
  getMyArticleLevelApi,
} from '@/api/knowhub/article-authoring'
import { listEnabledTagsApi } from '@/api/knowhub/blog'
import type { ArticleAuthoringPayload } from '@/types/api/knowhub/article-authoring'
import type { TagRecord } from '@/types/api/knowhub/tag'

const route = useRoute()
const router = useRouter()

/** 文章表单模型（与 ArticleAuthoringPayload 对齐） */
const form = ref<{
  articleId?: number
  title: string
  summary: string
  coverObjectKey: string
  level: number
  visibility: string
  tagIds: number[]
  /** 评论区开关 1开/0关，作者在创作页勾选 */
  commentEnabled: number
  /** 评论精选开关 0=新评论直接可见 / 1=新评论仅发表人+作者可见，作者同意展示后他人可见 */
  commentCurated: number
}>({
  title: '',
  summary: '',
  coverObjectKey: '',
  level: 1,
  visibility: 'PRIVATE',
  tagIds: [],
  commentEnabled: 1,
  commentCurated: 0,
})

const articleStatus = ref<string>('')
const isEdit = computed(() => route.query.id !== undefined)
const editId = computed(() => (route.query.id ? Number(route.query.id) : undefined))

/** 当前用户 view 等级（0/1/2/3），等级选项据此禁用 */
const myLevel = ref<number>(0)

/** 全部启用标签 */
const tagOptions = ref<TagRecord[]>([])

const metaExpanded = ref(true)

const saving = ref(false)
const publishing = ref(false)

/** 等级选项：禁用态按用户 view 等级算 */
const levelOptions = computed(() => [
  { value: 1, label: '公开', sub: '所有人可见', disabled: false },
  { value: 2, label: '内部', sub: '登录成员可见', disabled: myLevel.value < 2 },
  { value: 3, label: '机密', sub: '高权限可见', disabled: myLevel.value < 3 },
])

/** 可见性三档硬编码（与后端 ArticleVisibility 枚举对齐；决定章节提交审不审） */
const visibilityOptions: { value: string; label: string; sub: string }[] = [
  { value: 'PRIVATE', label: '未公开', sub: '仅作者可写章节，作者提交免审' },
  { value: 'SEMIPUBLIC', label: '半公开', sub: '他人可提交章节，需作者审核' },
  { value: 'PUBLIC', label: '全公开', sub: '他人可提交章节并免审发布' },
]

const isPublished = computed(() => articleStatus.value === 'PUBLISHED')
const canEditNow = computed(() => ['DRAFT', 'REJECTED', 'REVOKED', ''].includes(articleStatus.value))

const buildPayload = (): ArticleAuthoringPayload => ({
  articleId: form.value.articleId,
  title: form.value.title.trim(),
  // 前言/摘要强制必填（validate 已挡空），传 trim 后的 markdown 原文，后端落库非空
  summary: form.value.summary.trim(),
  coverObjectKey: form.value.coverObjectKey.trim() || undefined,
  level: form.value.level,
  visibility: form.value.visibility,
  tagIds: form.value.tagIds.length ? form.value.tagIds : undefined,
  commentEnabled: form.value.commentEnabled,
  commentCurated: form.value.commentCurated,
})

const validate = (): boolean => {
  if (!form.value.title.trim()) {
    ElMessage.warning('请输入文章标题')
    return false
  }
  // 前言/摘要强制必填（ article.summary 数据库列已改非空）：文档导言，列表/卡片/搜索摘要均依赖此字段
  if (!form.value.summary.trim()) {
    ElMessage.warning('请写文章前言（文档导言），用作摘要展示')
    return false
  }
  if (form.value.tagIds.length > 8) {
    ElMessage.warning('标签最多选择 8 个')
    return false
  }
  return true
}

/** 存草稿：新建跳 profile"我的文章"列表（带 tab=article+t 触发重拉）；编辑态留本页提示 */
const handleSaveDraft = async () => {
  if (!validate()) return
  saving.value = true
  try {
    const payload = buildPayload()
    if (form.value.articleId) {
      await editArticleApi(payload)
      ElMessage.success('草稿已保存')
    } else {
      await draftArticleApi(payload)
      ElMessage.success('文章草稿已保存，即将返回我的文章；从列表进入可管理章节')
      router.push({ path: '/profile', query: { tab: 'article', t: String(Date.now()) } })
    }
  } finally {
    saving.value = false
  }
}

/** 发布：仅编辑模式（已有 articleId）可用，先 edit 再 publish */
const handlePublish = async () => {
  if (!validate()) return
  if (!form.value.articleId) {
    ElMessage.warning('请先保存草稿，再从我的文章列表进入发布')
    return
  }
  publishing.value = true
  try {
    if (canEditNow.value) {
      await editArticleApi(buildPayload())
    }
    await publishArticleAuthoringApi(form.value.articleId)
    ElMessage.success('发布已提交' + (articleStatus.value !== 'PUBLISHED' ? '（若开启审核将进入待审核）' : ''))
    articleStatus.value = 'PENDING_REVIEW'
  } finally {
    publishing.value = false
  }
}

/** 撤回：仅 PUBLISHED 可撤回 */
const handleRevoke = async () => {
  const id = form.value.articleId
  if (!id) return
  try {
    await ElMessageBox.confirm('撤回后将转为已撤回态，可继续编辑元信息后重新发布', '确认撤回', { type: 'warning' })
  } catch {
    return
  }
  await revokeArticleAuthoringApi(id)
  ElMessage.success('已撤回，可继续编辑')
  articleStatus.value = 'REVOKED'
}

/** 跳章节管理页（编辑态显示此按钮） */
const goChapters = () => {
  if (form.value.articleId) {
    router.push(`/article/${form.value.articleId}/chapters`)
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
  return articleStatus.value ? (m[articleStatus.value] ?? articleStatus.value) : ''
})

const fetchForEdit = async (articleId: number) => {
  const res = await getArticleForEditApi(articleId)
  const b = res.data
  if (!b) {
    ElMessage.error('文章不存在或无权查看')
    router.back()
    return
  }
  if (b.canEdit === false) {
    ElMessage.error('无权编辑该文章')
    router.back()
    return
  }
  form.value.articleId = b.articleId
  form.value.title = b.title
  form.value.summary = b.summary ?? ''
  form.value.coverObjectKey = b.coverObjectKey ?? ''
  form.value.level = b.level ?? 1
  form.value.visibility = b.visibility ?? 'PRIVATE'
  form.value.tagIds = b.tagIds ?? []
  form.value.commentEnabled = b.commentEnabled ?? 1
  form.value.commentCurated = b.commentCurated ?? 0
  articleStatus.value = b.status ?? ''
  if (isPublished.value) {
    ElMessage.info('该文章已发布，编辑需先撤回')
  }
}

onMounted(async () => {
  const [levelRes, tagRes] = await Promise.all([
    getMyArticleLevelApi(),
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
  <div class="ac">
    <!-- 顶部工具条 -->
    <header class="ac__bar">
      <div class="ac__bar-inner">
        <div class="ac__bar-left">
          <button class="ac__back" type="button" @click="router.back()">
            <el-icon><ArrowLeft /></el-icon> 返回
          </button>
          <input
            v-model="form.title"
            class="ac__title-input"
            placeholder="输入文章标题…"
            maxlength="200"
          />
          <span v-if="statusText" class="ac__status">{{ statusText }}</span>
        </div>
        <div class="ac__bar-right">
          <button
            v-if="form.articleId"
            class="ac__btn ac__btn--ghost"
            type="button"
            title="管理该文章的章节（提交/编辑/发布/撤回/删除章节）"
            @click="goChapters"
          >
            <KhIcon name="doc" :size="14" /> 章节管理
          </button>
          <button class="ac__btn ac__btn--ghost" type="button" :disabled="saving || publishing" @click="handleSaveDraft">
            {{ saving ? '保存中…' : '存草稿' }}
          </button>
          <button
            v-if="isPublished"
            class="ac__btn ac__btn--warn"
            type="button"
            :disabled="saving || publishing"
            @click="handleRevoke"
          >撤回</button>
          <button
            v-else
            class="ac__btn ac__btn--primary"
            type="button"
            :disabled="saving || publishing || isPublished"
            @click="handlePublish"
          >{{ publishing ? '发布中…' : '发布' }}</button>
        </div>
      </div>
    </header>

    <div class="ac__wrap">
      <!-- 前言区（文章导言，可选；正文在各章节里写）。编辑器复用通用 KhMarkdownEditor，固定高度 + 右下角模式浮层。 -->
      <section class="ac__summary">
        <div class="ac__section-head">
          <h2>文章前言</h2>
          <span class="ac__section-hint">文档导言/编者按，可选；正文写在各章节里</span>
        </div>
        <div class="ac__summary-editor">
          <KhMarkdownEditor
            v-model="form.summary"
            height="100%"
            placeholder="写一段文章导言/编者按（可选）…"
          />
        </div>
      </section>

      <!-- 元信息折叠面板 -->
      <section class="ac__meta">
        <button class="ac__meta-head" type="button" @click="metaExpanded = !metaExpanded">
          <KhIcon name="tag" :size="14" />
          <span>文章信息</span>
          <span class="ac__meta-summary">
            L{{ form.level }} · {{ visibilityOptions.find((v) => v.value === form.visibility)?.label ?? form.visibility }}
            · {{ form.tagIds.length }} 标签 · {{ form.coverObjectKey ? '有封面' : '无封面' }}
          </span>
          <span class="ac__meta-caret" :class="{ 'is-open': metaExpanded }">▾</span>
        </button>

        <div v-show="metaExpanded" class="ac__meta-body">
          <!-- 封面 -->
          <div class="ac__field">
            <label class="ac__label">封面图</label>
            <ArticleCoverUploader v-model="form.coverObjectKey" />
          </div>

          <!-- 标签 -->
          <div class="ac__field">
            <label class="ac__label">标签<span class="ac__label-hint">可多选，输入关键词筛选</span></label>
            <ElSelect
              v-model="form.tagIds"
              multiple
              filterable
              :max="8"
              collapse-tags
              collapse-tags-tooltip
              placeholder="选择文章标签…"
              class="ac__tag-select"
            >
              <ElOption v-for="t in tagOptions" :key="t.tagId" :label="t.tagName" :value="t.tagId" />
            </ElSelect>
          </div>

          <!-- 等级 -->
          <div class="ac__field">
            <label class="ac__label">查看等级</label>
            <div class="ac__level-group">
              <button
                v-for="opt in levelOptions"
                :key="opt.value"
                class="ac__level-btn"
                :class="{ 'is-active': form.level === opt.value, 'is-disabled': opt.disabled }"
                type="button"
                :disabled="opt.disabled"
                :title="opt.disabled ? '当前等级不可创建此级别' : opt.sub"
                @click="form.level = opt.value"
              >
                <span class="ac__level-name">L{{ opt.value }} {{ opt.label }}</span>
                <span class="ac__level-sub">{{ opt.sub }}</span>
              </button>
            </div>
          </div>

          <!-- 可见性 -->
          <div class="ac__field">
            <label class="ac__label">内部可见性<span class="ac__label-hint">决定他人能否提交章节及是否需审核</span></label>
            <div class="ac__vis-group">
              <button
                v-for="opt in visibilityOptions"
                :key="opt.value"
                class="ac__vis-btn"
                :class="{ 'is-active': form.visibility === opt.value }"
                type="button"
                :title="opt.sub"
                @click="form.visibility = opt.value"
              >
                <span class="ac__vis-name">{{ opt.label }}</span>
                <span class="ac__vis-sub">{{ opt.sub }}</span>
              </button>
            </div>
          </div>

          <!-- 评论设置：开启评论区 + 评论精选（详见 comment 模块） -->
          <div class="ac__field">
            <label class="ac__label">评论设置</label>
            <div class="ac__switch-row">
              <ElSwitch v-model="form.commentEnabled" :active-value="1" :inactive-value="0" />
              <span class="ac__switch-text">开启评论区</span>
            </div>
            <div class="ac__switch-row">
              <ElSwitch v-model="form.commentCurated" :active-value="1" :inactive-value="0" />
              <span class="ac__switch-text">评论精选</span>
              <span class="ac__switch-hint">开启后新评论仅你与发表人可见，需你同意后才对他人展示</span>
            </div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.ac {
  display: flex;
  flex-direction: column;
  min-height: calc(100vh - var(--kh-header-height));
  background: var(--kh-bg);
}

/* 顶部工具条 */
.ac__bar {
  position: sticky;
  top: var(--kh-header-height);
  z-index: 10;
  background: color-mix(in srgb, var(--kh-surface) 92%, transparent);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid var(--kh-border-soft);
  flex: none;
}
.ac__bar-inner {
  max-width: 880px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kh-space-4);
  padding: var(--kh-space-3) var(--kh-space-5);
}
.ac__bar-left { display: flex; align-items: center; gap: var(--kh-space-3); flex: 1; min-width: 0; }
.ac__back {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 6px 12px; border: 1px solid var(--kh-border); border-radius: var(--kh-radius-sm);
  background: var(--kh-surface); color: var(--kh-text-secondary); font-size: var(--kh-font-size-sm);
  cursor: pointer; flex: none; transition: all var(--kh-transition-fast);
}
.ac__back:hover { border-color: var(--kh-primary-border); color: var(--kh-primary); }
.ac__title-input {
  flex: 1; min-width: 0; border: none; outline: none; background: transparent;
  font-size: var(--kh-font-size-xl); font-weight: 700; color: var(--kh-text);
}
.ac__title-input::placeholder { color: var(--kh-text-tertiary); font-weight: 600; }
.ac__status {
  font-size: 12px; color: var(--kh-text-tertiary); padding: 2px 8px;
  border: 1px solid var(--kh-border-soft); border-radius: var(--kh-radius-pill); flex: none;
}
.ac__bar-right { display: flex; align-items: center; gap: var(--kh-space-2); flex: none; }
.ac__btn {
  display: inline-flex; align-items: center; gap: 5px;
  height: 36px; padding: 0 var(--kh-space-4); border-radius: var(--kh-radius-sm);
  font-size: var(--kh-font-size-sm); font-weight: 600; cursor: pointer;
  border: 1px solid transparent; transition: all var(--kh-transition-fast);
}
.ac__btn:disabled { opacity: 0.5; cursor: not-allowed; }
.ac__btn--ghost { border-color: var(--kh-border); background: var(--kh-surface); color: var(--kh-text-secondary); }
.ac__btn--ghost:hover:not(:disabled) { border-color: var(--kh-primary-border); color: var(--kh-primary); }
.ac__btn--primary { background: linear-gradient(120deg, var(--kh-primary), var(--kh-primary-strong)); color: #fff; }
.ac__btn--warn { border-color: var(--kh-warm); color: var(--kh-warm); background: var(--kh-surface); }
.ac__btn--warn:hover:not(:disabled) { background: var(--kh-primary-soft); }

/* 主体 */
.ac__wrap { max-width: 880px; margin: 0 auto; width: 100%; padding: var(--kh-space-6) var(--kh-space-5) var(--kh-space-12); display: flex; flex-direction: column; gap: var(--kh-space-6); }

.ac__section-head { display: flex; align-items: center; gap: var(--kh-space-3); margin-bottom: var(--kh-space-3); }
.ac__section-head h2 { font-size: var(--kh-font-size-md); font-weight: 600; color: var(--kh-text); margin: 0; }
.ac__section-hint { font-size: 12px; color: var(--kh-text-tertiary); }
/* 前言编辑器容器：给 KhMarkdownEditor 固定高度（外框/模式浮层/圆角/tooltip 溢出已封装进组件）。 */
.ac__summary-editor { height: 320px; min-height: 240px; }

/* 元信息折叠 */
.ac__meta { background: var(--kh-surface); border: 1px solid var(--kh-border-soft); border-radius: var(--kh-radius-lg); overflow: hidden; }
.ac__meta-head {
  width: 100%; display: flex; align-items: center; gap: var(--kh-space-2);
  padding: var(--kh-space-3) var(--kh-space-4); background: transparent; border: none; cursor: pointer;
  font-size: var(--kh-font-size-sm); color: var(--kh-text-secondary);
}
.ac__meta-head:hover { background: var(--kh-surface-muted); }
.ac__meta-summary { margin-left: auto; font-size: 12px; color: var(--kh-text-tertiary); }
.ac__meta-caret { transition: transform var(--kh-transition-fast); color: var(--kh-text-tertiary); }
.ac__meta-caret.is-open { transform: rotate(180deg); }
.ac__meta-body { padding: 0 var(--kh-space-4) var(--kh-space-4); display: flex; flex-direction: column; gap: var(--kh-space-5); border-top: 1px solid var(--kh-border-soft); }
.ac__field { display: flex; flex-direction: column; gap: var(--kh-space-2); padding-top: var(--kh-space-3); }
.ac__label { font-size: var(--kh-font-size-sm); font-weight: 600; color: var(--kh-text); display: flex; align-items: center; gap: 6px; }
.ac__label-hint { font-size: 12px; font-weight: 400; color: var(--kh-text-tertiary); }
.ac__tag-select { width: 100%; }

/* 等级/可见性按钮组 */
.ac__level-group, .ac__vis-group { display: flex; gap: var(--kh-space-2); flex-wrap: wrap; }
.ac__level-btn, .ac__vis-btn {
  display: flex; flex-direction: column; gap: 2px;
  padding: 10px 14px; border: 1px solid var(--kh-border); border-radius: var(--kh-radius);
  background: var(--kh-surface); color: var(--kh-text-secondary); cursor: pointer;
  transition: all var(--kh-transition-fast); text-align: left; min-width: 120px;
}
.ac__level-btn:hover:not(.is-disabled):not(.is-active), .ac__vis-btn:hover:not(.is-active) {
  border-color: var(--kh-primary-border); color: var(--kh-primary);
}
.ac__level-btn.is-active, .ac__vis-btn.is-active {
  border-color: var(--kh-primary); background: var(--kh-primary-soft); color: var(--kh-primary-strong);
}
.ac__level-btn.is-disabled { opacity: 0.4; cursor: not-allowed; }
.ac__level-name, .ac__vis-name { font-size: var(--kh-font-size-sm); font-weight: 600; }
.ac__level-sub, .ac__vis-sub { font-size: 11px; color: var(--kh-text-tertiary); }

/* 评论设置：开启评论区 + 评论精选两个 switch */
.ac__switch-row { display: flex; align-items: center; gap: var(--kh-space-3); flex-wrap: wrap; }
.ac__switch-text { font-size: var(--kh-font-size-sm); color: var(--kh-text); font-weight: 500; }
.ac__switch-hint { font-size: 12px; color: var(--kh-text-tertiary); }
</style>