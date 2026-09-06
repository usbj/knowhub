<!--
  章节创作/编辑页 /article/:id/chapter/edit
  ------------------------------------------------------------------
  章节是文章子页面，正文走章节主表。表单精简（用户拍板"不要太多杂项"）：章节名 + 排序 + 正文 markdown。
  - 不含封面/标签/等级（章节不分等级、可见性随文章、无标签）。
  - 顶部工具条：返回 / 章节名输入 / 状态徽标 / 导入.md / 存草稿 / 发布(提交) / 撤回。
  「导入 .md」复用公共 createMdImporter（与博客创作页同源）——本地 md 纯前端解析，本地图片路径
  改写为 `[图片：alt]` 占位需作者手动重插图；章节名为空时自动填文件名，正文填 content。
  - 主体：正文编辑器（content-first，复用通用 KhMarkdownEditor，编辑/预览浮层切换，拖拽/粘贴插图
    走预签名直传 BLOG_BODY 暂用——后端 FileBusinessType 暂无 CHAPTER_BODY，将来加后切）占满 main 区，
    超出由内部滚动条兜底不再往下蔓延；**排序杂项置正文下方折叠面板**（仿博客元信息区，默认折叠，
    写完正文再展开补排序），不再挤在正文上方。
  - 状态机：新建 schema — submitChapterApi（按文章 visibility 决定状态机：作者免审直 PUBLISHED；
    非作者 PRIVATE 拒、SEMIPUBLIC 进 PENDING_AUTHOR_REVIEW、PUBLIC 免审 PUBLISHED）。
    编辑 ?cid=xxx — getChapterForEditApi 回填；PUBLISHED 须先撤回才能改（编辑接口挡），REVOKE 后可再 submit/publish。
  - PUBLISHED 章节不能直接编辑：编辑按钮在管理页已挡（canEditChapter），本页编辑态若取到 PUBLISHED
    也会提示"先撤回"；保存调用 editChapterApi（后端状态机拦 PUBLISHED）。
-->
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import KhIcon from '@/components/common/KhIcon.vue'
import KhMarkdownEditor from '@/components/common/KhMarkdownEditor.vue'
import {
  submitChapterApi,
  editChapterApi,
  publishChapterAuthoringApi,
  revokeChapterAuthoringApi,
  getChapterForEditApi,
} from '@/api/knowhub/article-authoring'
import { createMdImporter } from '@/utils/md-import'
import type { ChapterAuthoringPayload } from '@/types/api/knowhub/article-authoring'

const route = useRoute()
const router = useRouter()

const articleId = computed(() => Number(route.params.id))
const editCid = computed(() => (route.query.cid ? Number(route.query.cid) : undefined))
const isEdit = computed(() => editCid.value !== undefined)

const form = ref<{
  chapterId?: number
  articleId: number
  chapterName: string
  sortOrder: number
  content: string
}>({
  articleId: 0,
  chapterName: '',
  sortOrder: 0,
  content: '',
})

const chapterStatus = ref<string>('')
const saving = ref(false)
const publishing = ref(false)

/** 章节元信息折叠态（默认折叠，content-first 仿博客；写完正文再展开补排序） */
const metaExpanded = ref(false)

const isPublished = computed(() => chapterStatus.value === 'PUBLISHED')
const canEditNow = computed(() => ['DRAFT', 'REJECTED', 'REVOKED', ''].includes(chapterStatus.value))
/** 待作者审「审核中」章节锁定预览：后端已禁改（editChapterInfo PENDING 前置挡），前端也要禁止输入/切换，
 *  不让作者/贡献者误以为能改也只是被拦——编辑器强制预览态、章节名只读、保存/发布按钮置灰并提示原因。 */
const isUnderReview = computed(() => chapterStatus.value === 'PENDING_AUTHOR_REVIEW')
/** 鼠标悬停锁定按钮的 hint 文案，告诉用户为何禁用（区分审核中 vs 已发布须先撤回） */
const lockHint = computed(() => {
  if (isUnderReview.value) return '审核中章节不可改动，请等审核结果后再编辑'
  if (isPublished.value) return '已发布章节请先撤回再编辑'
  return ''
})

const validate = (): boolean => {
  if (!form.value.chapterName.trim()) {
    ElMessage.warning('请输入章节名')
    return false
  }
  if (!form.value.content.trim()) {
    ElMessage.warning('请输入章节正文')
    return false
  }
  return true
}

const buildPayload = (): ChapterAuthoringPayload => ({
  chapterId: form.value.chapterId,
  articleId: form.value.articleId,
  chapterName: form.value.chapterName.trim(),
  sortOrder: form.value.sortOrder,
  content: form.value.content,
})

/** 保存：编辑态调 editChapterApi；新建态调 submitChapterApi（提交即按 visibility 决定状态机） */
const handleSaveDraft = async () => {
  if (!validate()) return
  saving.value = true
  try {
    if (form.value.chapterId) {
      await editChapterApi(buildPayload())
      ElMessage.success('章节已保存')
    } else {
      await submitChapterApi(buildPayload())
      ElMessage.success('章节已提交，返回章节管理')
      router.push(`/article/${articleId.value}/chapters`)
    }
  } finally {
    saving.value = false
  }
}

/** 发布/再提交：用于 DRAFT/REJECTED/REVOKED 章节再次发布（编辑态） */
const handlePublish = async () => {
  if (!validate()) return
  if (!form.value.chapterId) {
    // 新建态：直接 submit 即视为发布/提交
    publishing.value = true
    try {
      await submitChapterApi(buildPayload())
      ElMessage.success('章节已提交，返回章节管理')
      router.push(`/article/${articleId.value}/chapters`)
    } finally {
      publishing.value = false
    }
    return
  }
  publishing.value = true
  try {
    if (canEditNow.value && !isPublished.value) {
      await editChapterApi(buildPayload())
    }
    await publishChapterAuthoringApi(form.value.chapterId)
    ElMessage.success('章节已提交/发布')
    router.push(`/article/${articleId.value}/chapters`)
  } finally {
    publishing.value = false
  }
}

/** 撤回：仅 PUBLISHED 可撤回（编辑态） */
const handleRevoke = async () => {
  const cid = form.value.chapterId
  if (!cid) return
  try {
    await ElMessageBox.confirm('撤回后章节转为已撤回态，可继续编辑正文后重新发布', '确认撤回', { type: 'warning' })
  } catch { return }
  await revokeChapterAuthoringApi(cid)
  ElMessage.success('已撤回，可继续编辑')
  chapterStatus.value = 'REVOKED'
}

/**
 * 章节正文插图上传已下沉到通用组件 KhMarkdownEditor：businessType 复用 BLOG_BODY（PUBLIC 公开读，
 * Markdown 正文内插图语义；后端 FileBusinessType 暂无 CHAPTER_BODY，将来加后给本标签传
 * businessType="CHAPTER_BODY" 即可切换，无需改章节页）。预签名直传回填 /file/resolve/{id}。
 */

const statusText = computed(() => {
  const m: Record<string, string> = {
    DRAFT: '草稿',
    PUBLISHED: '已发布',
    REVOKED: '已撤回',
    PENDING_AUTHOR_REVIEW: '待作者审',
    REJECTED: '已驳回',
  }
  return chapterStatus.value ? (m[chapterStatus.value] ?? chapterStatus.value) : ''
})

const fetchForEdit = async (chapterId: number) => {
  const res = await getChapterForEditApi(chapterId)
  const c = res.data
  if (!c) {
    ElMessage.error('章节不存在或无权查看')
    router.back()
    return
  }
  form.value.chapterId = c.chapterId
  form.value.articleId = c.articleId
  form.value.chapterName = c.chapterName
  form.value.sortOrder = c.sortOrder ?? 0
  form.value.content = c.content ?? ''
  chapterStatus.value = c.status ?? ''
  if (isPublished.value) {
    ElMessage.info('该章节已发布，编辑需先撤回')
  }
}

/**
 * 导入 .md 文件为章节正文：复用公共工具 createMdImporter（utils/md-import.ts）——纯前端解析，
 * 本地图片路径就地改写为 `[图片：alt]` 占位，作者后续对占位手动重插图。章节名为空时填文件名
 * （200 字截断对齐 maxlength）；正文填 form.content。不自动提交，保留作者检查机会。
 */
const { openPicker: openMdPicker } = createMdImporter(
  ({ title, content }) => {
    if (!form.value.chapterName.trim()) form.value.chapterName = title
    form.value.content = content
  },
  { maxTitleLen: 200, fallbackTitle: '未命名章节' },
)

onMounted(() => {
  form.value.articleId = articleId.value
  if (editCid.value) {
    void fetchForEdit(editCid.value)
  }
})
</script>

<template>
  <div class="ace">
    <!-- 顶部工具条 -->
    <header class="ace__bar">
      <div class="ace__bar-inner">
        <div class="ace__bar-left">
          <button class="ace__back" type="button" @click="router.push(`/article/${articleId}/chapters`)">
            <el-icon><ArrowLeft /></el-icon> 返回章节
          </button>
          <input
            v-model="form.chapterName"
            class="ace__name-input"
            :class="{ 'ace__name-input--locked': isUnderReview }"
            placeholder="输入章节名…"
            maxlength="200"
            :readonly="isUnderReview"
          />
          <span v-if="statusText" class="ace__status" :class="{ 'ace__status--locked': isUnderReview }">{{ statusText }}</span>
        </div>
        <div class="ace__bar-right">
          <!-- 审核中/已发布 禁用所有改稿按钮，title 提示原因（lockHint） -->
          <button
            class="ace__btn ace__btn--ghost"
            type="button"
            title="从 .md 文件导入章节正文（本地图片路径会标为占位，需手动重插图；章节名为空时自动填文件名）"
            :disabled="saving || publishing || isUnderReview"
            @click="openMdPicker"
          >
            <KhIcon name="file" :size="14" /> 导入 .md
          </button>
          <button
            class="ace__btn ace__btn--ghost"
            type="button"
            :title="lockHint || undefined"
            :disabled="saving || publishing || isUnderReview"
            @click="handleSaveDraft"
          >
            {{ saving ? '保存中…' : (isEdit ? '保存修改' : '存草稿') }}
          </button>
          <button
            v-if="isPublished"
            class="ace__btn ace__btn--warn"
            type="button"
            :disabled="saving || publishing"
            @click="handleRevoke"
          >撤回</button>
          <button
            v-else
            class="ace__btn ace__btn--primary"
            type="button"
            :title="lockHint || undefined"
            :disabled="saving || publishing || isPublished || isUnderReview"
            @click="handlePublish"
          >{{ publishing ? '提交中…' : (isEdit ? '发布/再提交' : '提交章节') }}</button>
        </div>
      </div>
    </header>

    <div class="ace__wrap">
      <!-- 审核中锁定横幅：明确告知本页已锁定预览、不可改，等审核结果 -->
      <div v-if="isUnderReview" class="ace__lock-banner">
        <KhIcon name="lock" :size="14" />
        <span>该章节正在作者审核中，已锁定为预览态，请等待审核结果后再编辑。</span>
      </div>
      <!-- 正文编辑器（content-first）：审核中强制预览态（mode=preview + 隐藏切换），作者可就地审阅不改稿 -->
      <section class="ace__editor">
        <KhMarkdownEditor
          v-model="form.content"
          :mode="isUnderReview ? 'preview' : undefined"
          :show-mode-switch="!isUnderReview"
          height="100%"
          placeholder="写章节正文…"
          business-type="BLOG_BODY"
          access="PUBLIC"
        />
      </section>

      <!-- 章节元信息折叠面板（正文下方，仿博客 meta）：排序杂项置此，默认折叠，content-first。
           审核中时整面板禁交互（排序不可改） -->
      <section class="ace__meta" :class="{ 'ace__meta--locked': isUnderReview }">
        <button class="ace__meta-head" type="button" :disabled="isUnderReview" @click="metaExpanded = !metaExpanded">
          <KhIcon name="tag" :size="14" />
          <span>章节信息</span>
          <span class="ace__meta-summary">排序 {{ form.sortOrder ?? 0 }}</span>
          <span class="ace__meta-caret" :class="{ 'is-open': metaExpanded }">▾</span>
        </button>
        <div v-show="metaExpanded" class="ace__meta-body">
          <!-- 排序 -->
          <div class="ace__field">
            <label class="ace__label">
              <KhIcon name="order" :size="14" /> 排序
              <span class="ace__label-hint">数字小的在前（缺省 0，同级按创建顺序）</span>
            </label>
            <input
              v-model.number="form.sortOrder"
              type="number"
              class="ace__sort-input"
              min="0"
              max="9999"
              placeholder="0"
              :readonly="isUnderReview"
            />
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
/* 整页固定视口高度（min-height → height）：让正文编辑区有明确高度约束，
   不会被内容往下撑蔓延；超出部分由编辑器内部 .v-md-editor__main 自带 overflow:auto 滚动，
   不会再出现"写一点往下延长一点、不知超出去哪"。 */
.ace { display: flex; flex-direction: column; height: calc(100vh - var(--kh-header-height)); background: var(--kh-bg); }

.ace__bar { position: sticky; top: var(--kh-header-height); z-index: 10; background: color-mix(in srgb, var(--kh-surface) 92%, transparent); backdrop-filter: blur(10px); border-bottom: 1px solid var(--kh-border-soft); flex: none; }
.ace__bar-inner { max-width: 880px; margin: 0 auto; display: flex; align-items: center; justify-content: space-between; gap: var(--kh-space-4); padding: var(--kh-space-3) var(--kh-space-5); }
.ace__bar-left { display: flex; align-items: center; gap: var(--kh-space-3); flex: 1; min-width: 0; }
.ace__back { display: inline-flex; align-items: center; gap: 4px; padding: 6px 12px; border: 1px solid var(--kh-border); border-radius: var(--kh-radius-sm); background: var(--kh-surface); color: var(--kh-text-secondary); font-size: var(--kh-font-size-sm); cursor: pointer; flex: none; transition: all var(--kh-transition-fast); }
.ace__back:hover { border-color: var(--kh-primary-border); color: var(--kh-primary); }
.ace__name-input { flex: 1; min-width: 0; border: none; outline: none; background: transparent; font-size: var(--kh-font-size-xl); font-weight: 700; color: var(--kh-text); }
.ace__name-input::placeholder { color: var(--kh-text-tertiary); font-weight: 600; }
.ace__name-input--locked { cursor: not-allowed; }
.ace__status { font-size: 12px; color: var(--kh-text-tertiary); padding: 2px 8px; border: 1px solid var(--kh-border-soft); border-radius: var(--kh-radius-pill); flex: none; }
.ace__status--locked { color: var(--kh-warm); border-color: var(--kh-warm); background: var(--kh-warm-soft); }

/* 审核中锁定横幅：通栏暖色提示，告知已锁预览、等审核 */
.ace__lock-banner { display: flex; align-items: center; gap: 6px; padding: var(--kh-space-3) var(--kh-space-4); border-radius: var(--kh-radius-sm); background: var(--kh-warm-soft); color: var(--kh-warm); font-size: var(--kh-font-size-sm); font-weight: 500; }
.ace__bar-right { display: flex; align-items: center; gap: var(--kh-space-2); flex: none; }
.ace__btn { display: inline-flex; align-items: center; gap: 5px; height: 36px; padding: 0 var(--kh-space-4); border-radius: var(--kh-radius-sm); font-size: var(--kh-font-size-sm); font-weight: 600; cursor: pointer; border: 1px solid transparent; transition: all var(--kh-transition-fast); }
.ace__btn:disabled { opacity: 0.5; cursor: not-allowed; }
.ace__btn--ghost { border-color: var(--kh-border); background: var(--kh-surface); color: var(--kh-text-secondary); }
.ace__btn--ghost:hover:not(:disabled) { border-color: var(--kh-primary-border); color: var(--kh-primary); }
.ace__btn--primary { background: linear-gradient(120deg, var(--kh-primary), var(--kh-primary-strong)); color: #fff; }
.ace__btn--warn { border-color: var(--kh-warm); color: var(--kh-warm); background: var(--kh-surface); }
.ace__btn--warn:hover:not(:disabled) { background: var(--kh-primary-soft); }

/* 主容器：flex:1 + min-height:0 链，把高度传给正文编辑器，让编辑器占满而非随内容撑高。
   content-first：正文编辑器 flex:1 占主体，元信息折叠面板 flex:none 置于正文下方展开/折叠。 */
.ace__wrap { max-width: 880px; margin: 0 auto; width: 100%; padding: var(--kh-space-4) var(--kh-space-5) var(--kh-space-6); display: flex; flex-direction: column; gap: var(--kh-space-4); flex: 1; min-height: 0; }

/* 正文编辑器容器：flex:1 + min-height:0 占满 main 区剩余高度，最小高度兜底防小屏过窄。
   外框/mode 浮层/圆角 /tooltip 溢出已封装进 KhMarkdownEditor 根，本类只管"占满"。 */
.ace__editor { position: relative; flex: 1; min-height: 320px; display: flex; flex-direction: column; }

/* 章节元信息折叠面板（仿博客 meta）：正文下方，默认折叠 content-first */
.ace__meta { background: var(--kh-surface); border: 1px solid var(--kh-border-soft); border-radius: var(--kh-radius-lg); overflow: hidden; flex: none; }
.ace__meta-head {
  width: 100%; display: flex; align-items: center; gap: var(--kh-space-2);
  padding: var(--kh-space-3) var(--kh-space-4); background: transparent; border: none; cursor: pointer;
  font-size: var(--kh-font-size-sm); color: var(--kh-text-secondary); transition: background var(--kh-transition-fast);
}
.ace__meta-head:hover { background: var(--kh-surface-muted); }
.ace__meta-head:disabled { cursor: not-allowed; opacity: 0.6; }
.ace__meta-head:disabled:hover { background: transparent; }
.ace__meta-head > :first-child { color: var(--kh-primary); }
.ace__meta-summary { margin-left: auto; font-size: 12px; color: var(--kh-text-tertiary); font-family: var(--kh-font-mono); }
.ace__meta-caret { transition: transform var(--kh-transition-fast); color: var(--kh-text-tertiary); font-size: 12px; }
.ace__meta-caret.is-open { transform: rotate(180deg); }
.ace__meta-body { padding: var(--kh-space-4) var(--kh-space-5); border-top: 1px solid var(--kh-border-soft); display: flex; flex-direction: column; gap: var(--kh-space-4); }
.ace__field { display: flex; flex-direction: column; gap: var(--kh-space-2); }
.ace__label { display: flex; align-items: center; gap: 6px; font-size: var(--kh-font-size-sm); font-weight: 600; color: var(--kh-text); }
.ace__label-hint { font-size: 12px; font-weight: 400; color: var(--kh-text-tertiary); }
.ace__sort-input { width: 120px; height: 34px; padding: 0 10px; border: 1px solid var(--kh-border); border-radius: var(--kh-radius-sm); background: var(--kh-surface); font-size: var(--kh-font-size-sm); color: var(--kh-text); }
.ace__sort-input:focus { outline: none; border-color: var(--kh-primary-border); }
.ace__sort-input[readonly] { cursor: not-allowed; color: var(--kh-text-tertiary); background: var(--kh-bg-soft); }
</style>