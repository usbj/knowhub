<!--
  章节管理页 /article/:id/chapters
  ------------------------------------------------------------------
  文章的章节子页面：列出该文章下所有章节（序号 + 章节名 + 状态徽标 + 作者 + 浏览量 + 编辑/发布/撤回/删除 按钮）+
  顶部信息条（文章标题 + 状态徽标 + 可见性提示 + 文章级操作「编辑文章信息/发布文章/撤回文章」+「新增章节」）。
  - 文章级操作：从"我的文章"点进来不只是管章节，也能就地编辑文章元信息/发布/撤回（与文章创作页 [article/create.vue] 按钮态一致）。
  - 章节状态机：PUBLISHED 已发布/ PENDING_AUTHOR_REVIEW 待作者审(SEMIPUBLIC 非作者提交)/ DRAFT 草稿/
    REJECTED 被驳回/ REVOKED 已撤回。
  - PUBLISHED 章节只能「撤回」后才能编辑（防绕审改已发布）；DRAFT/REJECTED/REVOKED 可「编辑/发布」。
  - 可见性提示：SEMIPUBLIC 半公开文章下他人提交的章节需当前文章作者审（本页暂不展开审核 UI，未来补）。
  入口：文章创作页"章节管理"按钮 / 我的文章列表。
-->
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Plus, Edit, Upload, Delete, RefreshLeft, Rank } from '@element-plus/icons-vue'
import KhIcon from '@/components/common/KhIcon.vue'
import KhTag from '@/components/common/KhTag.vue'
import {
  getArticleForEditApi,
  publishArticleAuthoringApi,
  revokeArticleAuthoringApi,
  getMyChaptersApi,
  publishChapterAuthoringApi,
  revokeChapterAuthoringApi,
  deleteChapterApi,
  reorderChaptersApi,
} from '@/api/knowhub/article-authoring'
import type { ChapterAuthoringDetail, ArticleAuthoringDetail } from '@/types/api/knowhub/article-authoring'

const route = useRoute()
const router = useRouter()

const articleId = computed(() => Number(route.params.id))
const article = ref<ArticleAuthoringDetail | null>(null)
const chapters = ref<ChapterAuthoringDetail[]>([])
const loading = ref(false)

/** 分页：章节多了太长，分页展示（拖拽只在当前页内重排，不跨页）。复用后台 quarryChapter 的 PageHelper 分页 */
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

/** 章节状态 → 徽标样式/文案 */
const statusMeta: Record<string, { text: string; type: 'success' | 'warning' | 'danger' | 'neutral' | 'info' }> = {
  PUBLISHED: { text: '已发布', type: 'success' },
  PENDING_AUTHOR_REVIEW: { text: '待作者审', type: 'warning' },
  DRAFT: { text: '草稿', type: 'neutral' },
  REJECTED: { text: '已驳回', type: 'danger' },
  REVOKED: { text: '已撤回', type: 'info' },
}

/** 可编辑：非 PUBLISHED/非 PENDING_AUTHOR_REVIEW，且 canEdit */
const canEditChapter = (c: ChapterAuthoringDetail) =>
  Boolean(c.canEdit) && !['PUBLISHED', 'PENDING_AUTHOR_REVIEW'].includes(c.status ?? '')

/** 可发布/再次提交：DRAFT/REJECTED/REVOKED，非 PUBLISHED/PENDING */
const canPublishChapter = (c: ChapterAuthoringDetail) =>
  Boolean(c.canEdit) && ['DRAFT', 'REJECTED', 'REVOKED'].includes(c.status ?? '')

const canRevokeChapter = (c: ChapterAuthoringDetail) =>
  Boolean(c.canEdit) && c.status === 'PUBLISHED'

/** —— 文章级状态（与文章创作页一致的按钮态）—— */
const articleStatus = computed(() => article.value?.status ?? '')
const isArticlePublished = computed(() => articleStatus.value === 'PUBLISHED')
/** 文章可编辑：草稿/驳回/撤回/空态；PUBLISHED/待审 不可直接改（须先撤回） */
const canEditArticle = computed(() =>
  Boolean(article.value?.canEdit) &&
  ['DRAFT', 'REJECTED', 'REVOKED', ''].includes(articleStatus.value),
)
/** 文章可发布：已有 articleId 且草稿/驳回/撤回态 */
const canPublishArticle = computed(() =>
  Boolean(article.value?.articleId) && canEditArticle.value,
)
/** 文章可撤回：已发布态 */
const canRevokeArticle = computed(() => isArticlePublished.value)

/** 文章状态徽标文案/样式 */
const articleStatusMeta = computed(() => {
  const m: Record<string, { text: string; type: 'success' | 'warning' | 'danger' | 'neutral' | 'info' }> = {
    PUBLISHED: { text: '已发布', type: 'success' },
    PENDING_REVIEW: { text: '待审核', type: 'warning' },
    DRAFT: { text: '草稿', type: 'neutral' },
    REJECTED: { text: '已驳回', type: 'danger' },
    REVOKED: { text: '已撤回', type: 'info' },
  }
  return m[articleStatus.value] ?? { text: '', type: 'neutral' as const }
})

const fetchArticle = async () => {
  const res = await getArticleForEditApi(articleId.value)
  article.value = res.data ?? null
}

const fetchChapters = async () => {
  loading.value = true
  try {
    const res = await getMyChaptersApi({ articleId: articleId.value, pageNum: pageNum.value, pageSize: pageSize.value })
    chapters.value = res.records ?? []
    total.value = res.total ?? 0
  } finally {
    loading.value = false
  }
}

/** 翻页：重拉当前页章节；拖拽重排在分页内排序后基于全局起始位置持久化 */
const onPageChange = (p: number) => {
  pageNum.value = p
  void fetchChapters()
}

const goAddChapter = () => {
  router.push(`/article/${articleId.value}/chapter/edit`)
}

const goEditChapter = (chapterId: number) => {
  router.push(`/article/${articleId.value}/chapter/edit?cid=${chapterId}`)
}

const handlePublish = async (c: ChapterAuthoringDetail) => {
  try {
    await ElMessageBox.confirm(`确认提交/发布章节「${c.chapterName}」？`, '发布章节', { type: 'info' })
  } catch { return }
  await publishChapterAuthoringApi(c.chapterId)
  ElMessage.success('已提交/发布')
  void fetchChapters()
}

const handleRevoke = async (c: ChapterAuthoringDetail) => {
  try {
    await ElMessageBox.confirm(`撤回章节「${c.chapterName}」后可继续编辑再发布`, '确认撤回', { type: 'warning' })
  } catch { return }
  await revokeChapterAuthoringApi(c.chapterId)
  ElMessage.success('已撤回')
  void fetchChapters()
}

const handleDelete = async (c: ChapterAuthoringDetail) => {
  try {
    await ElMessageBox.confirm(`删除章节「${c.chapterName}」无法恢复，确认删除？`, '删除章节', { type: 'warning' })
  } catch { return }
  await deleteChapterApi(c.chapterId)
  ElMessage.success('已删除')
  void fetchChapters()
}

const goUp = () => router.push('/profile?tab=article')

/** —— 文章级操作 —— */
/** 跳文章创作页编辑文章信息（带 id 回填） */
const goEditArticle = () => {
  if (article.value?.articleId) {
    router.push({ path: '/article/create', query: { id: String(article.value.articleId) } })
  }
}

/** 发布文章：先 edit 当前元信息（canEditNow 时）再 publish（照文章创作页逻辑） */
const handlePublishArticle = async () => {
  if (!article.value?.articleId) return
  try {
    await ElMessageBox.confirm('确认发布文章？若开启审核将进入待审核态', '发布文章', { type: 'info' })
  } catch { return }
  await publishArticleAuthoringApi(article.value.articleId)
  ElMessage.success('发布已提交')
  void fetchArticle()
}

/** 撤回文章：仅 PUBLISHED 可撤回，撤回后可继续改文章信息与章节 */
const handleRevokeArticle = async () => {
  if (!article.value?.articleId) return
  try {
    await ElMessageBox.confirm('撤回后文章转为已撤回态，可继续编辑后重新发布', '确认撤回文章', { type: 'warning' })
  } catch { return }
  await revokeArticleAuthoringApi(article.value.articleId)
  ElMessage.success('已撤回，可继续编辑')
  void fetchArticle()
}

const visibilityHint = computed(() => {
  switch (article.value?.visibility) {
    case 'PRIVATE': return '未公开 · 仅作者可写章节，作者提交免审'
    case 'SEMIPUBLIC': return '半公开 · 他人可提交章节，非作者提交需作者审核'
    case 'PUBLIC': return '全公开 · 他人可提交章节并免审发布'
    default: return ''
  }
})

/** 拖拽重排：作者/系统编辑权限即可调（排序是组织权，不涉内容审核，PUBLISHED 也允许）。
 *  后端 reorderChapters 逐章 canEditChapter 鉴权 + 同 articleId 一致校验 + 事务。
 *  前端用 article.canEdit 放开拖拽（已发布文章作者仍可重排章节顺序）。 */
const dragIndex = ref<number | null>(null)
const dropIndex = ref<number | null>(null)
const reordering = ref(false)

const canReorder = computed(() => Boolean(article.value?.articleId) && Boolean(article.value?.canEdit))

const onDragStart = (i: number) => {
  if (!canReorder.value) return
  dragIndex.value = i
}
const onDragOver = (e: DragEvent, i: number) => {
  if (!canReorder.value || dragIndex.value === null) return
  e.preventDefault()
  if (dropIndex.value !== i) dropIndex.value = i
}
const onDragLeave = () => {
  dropIndex.value = null
}
const onDrop = async (i: number) => {
  if (!canReorder.value || dragIndex.value === null || dragIndex.value === i) {
    dragIndex.value = null
    dropIndex.value = null
    return
  }
  const from = dragIndex.value
  dragIndex.value = null
  dropIndex.value = null
  // 本地先交换顺序，UI 即时响应；失败回滚（catch 里重拉）
  const list = chapters.value.slice()
  const [moved] = list.splice(from, 1)
  if (!moved) return
  list.splice(i, 0, moved)
  chapters.value = list
  await persistReorder()
}

/** 持久化当前页章节顺序：拖拽只在当前页内交换，sortOrder = (pageNum-1)*pageSize + 页内index
 *  （全局连续，不影响其他页章节相对顺序）；顶层即数组，每项 {chapterId, sortOrder, articleId} 调 reorderChaptersApi */
const persistReorder = async () => {
  if (!article.value?.articleId) return
  const base = (pageNum.value - 1) * pageSize.value
  reordering.value = true
  try {
    await reorderChaptersApi(
      chapters.value.map((c, idx) => ({
        chapterId: c.chapterId,
        sortOrder: base + idx,
        articleId: article.value!.articleId,
      })),
    )
    ElMessage.success('章节顺序已更新')
  } catch {
    // 失败重拉，恢复后端真实顺序
    void fetchChapters()
  } finally {
    reordering.value = false
  }
}

onMounted(() => {
  void fetchArticle()
  void fetchChapters()
})
</script>

<template>
  <div class="ac-ch">
    <!-- 面包屑 -->
    <div class="kh-container kh-container--wide ac-ch__crumb">
      <button class="ac-ch__back" type="button" @click="goUp">
        <el-icon><ArrowLeft /></el-icon> 返回我的文章
      </button>
      <RouterLink to="/docs">文档学习</RouterLink>
      <el-icon class="ac-ch__sep"><KhIcon name="chevron-right" :size="12" /></el-icon>
      <span class="ac-ch__current">章节管理 · {{ article?.title }}</span>
    </div>

    <!-- 文章信息条 -->
    <div class="kh-container kh-container--wide">
      <div class="ac-ch__head">
        <div class="ac-ch__head-main">
          <div class="ac-ch__title-row">
            <h1 class="ac-ch__title">{{ article?.title ?? '加载中…' }}</h1>
            <KhTag
              v-if="articleStatusMeta.text"
              size="sm"
              :type="articleStatusMeta.type"
            >{{ articleStatusMeta.text }}</KhTag>
          </div>
          <p v-if="article" class="ac-ch__vis">{{ visibilityHint }}</p>
        </div>
        <div class="ac-ch__head-actions">
          <button
            v-if="article?.articleId"
            class="ac-ch__act ac-ch__act--ghost"
            type="button"
            title="编辑文章元信息（标题/前言/等级/可见性/封面/标签）"
            @click="goEditArticle"
          >
            <el-icon><Edit /></el-icon> 编辑文章信息
          </button>
          <button
            v-if="canRevokeArticle"
            class="ac-ch__act ac-ch__act--warn"
            type="button"
            :disabled="false"
            @click="handleRevokeArticle"
          >撤回文章</button>
          <button
            v-else-if="canPublishArticle"
            class="ac-ch__act ac-ch__act--primary"
            type="button"
            @click="handlePublishArticle"
          >发布文章</button>
          <button class="ac-ch__add" type="button" @click="goAddChapter">
            <el-icon><Plus /></el-icon> 新增章节
          </button>
        </div>
      </div>
    </div>

    <!-- 章节列表 -->
    <div class="kh-container kh-container--wide ac-ch__list-wrap">
      <div v-if="loading" class="ac-ch__placeholder"><p>加载中…</p></div>
      <div v-else-if="!chapters.length" class="ac-ch__placeholder">
        <KhIcon name="doc" :size="40" :stroke="1.4" />
        <p>暂无章节，点上方「新增章节」开始写正文</p>
      </div>
      <ol v-else class="ac-ch__list">
        <li
          v-for="(c, i) in chapters"
          :key="c.chapterId"
          class="ac-ch__item"
          :class="{
            'is-dragging': dragIndex === i,
            'is-drag-over': dropIndex === i && dragIndex !== null && dragIndex !== i,
            'is-reordering': reordering,
          }"
          :draggable="canReorder"
          @dragstart="onDragStart(i)"
          @dragover="onDragOver($event, i)"
          @dragleave="onDragLeave"
          @drop="onDrop(i)"
        >
          <span
            v-if="canReorder"
            class="ac-ch__drag-handle"
            :title="reordering ? '保存中…' : '长按拖拽调整顺序'"
          >
            <el-icon><Rank /></el-icon>
          </span>
          <span class="ac-ch__no">{{ String((pageNum - 1) * pageSize + i + 1).padStart(2, '0') }}</span>
          <div class="ac-ch__item-main">
            <div class="ac-ch__item-title" @click="goEditChapter(c.chapterId)">{{ c.chapterName }}</div>
            <div class="ac-ch__item-meta">
              <KhTag size="sm" :type="statusMeta[c.status ?? '']?.type ?? 'neutral'">{{ statusMeta[c.status ?? '']?.text ?? '未知' }}</KhTag>
              <span v-if="c.authorNickname">· {{ c.authorNickname }}</span>
            </div>
          </div>
          <div class="ac-ch__item-actions">
            <button v-if="canEditChapter(c)" class="ac-ch__btn" type="button" title="编辑" @click="goEditChapter(c.chapterId)">
              <el-icon><Edit /></el-icon>
            </button>
            <button v-if="canPublishChapter(c)" class="ac-ch__btn ac-ch__btn--primary" type="button" title="发布/提交" @click="handlePublish(c)">
              <el-icon><Upload /></el-icon>
            </button>
            <button v-if="canRevokeChapter(c)" class="ac-ch__btn ac-ch__btn--warn" type="button" title="撤回" @click="handleRevoke(c)">
              <el-icon><RefreshLeft /></el-icon>
            </button>
            <button class="ac-ch__btn ac-ch__btn--danger" type="button" title="删除" @click="handleDelete(c)">
              <el-icon><Delete /></el-icon>
            </button>
          </div>
        </li>
      </ol>

      <!-- 分页：章节数多时分页展示，拖拽只在当前页内重排（不跨页）。复用后台 el-pagination 范式 -->
      <div v-if="total > pageSize" class="ac-ch__pager">
        <el-pagination
          layout="prev, pager, next"
          :total="total"
          :page-size="pageSize"
          :current-page="pageNum"
          background
          @current-change="onPageChange"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.ac-ch__crumb { display: flex; align-items: center; gap: var(--kh-space-2); padding-top: var(--kh-space-5); padding-bottom: var(--kh-space-4); font-size: var(--kh-font-size-sm); color: var(--kh-text-tertiary); }
.ac-ch__back { display: inline-flex; align-items: center; gap: 4px; padding: 4px 10px; border: 1px solid var(--kh-border); border-radius: var(--kh-radius-sm); background: var(--kh-surface); color: var(--kh-text-secondary); cursor: pointer; margin-right: var(--kh-space-3); font-size: 12px; }
.ac-ch__back:hover { border-color: var(--kh-primary-border); color: var(--kh-primary); }
.ac-ch__crumb a { color: var(--kh-text-secondary); }
.ac-ch__sep { color: var(--kh-text-tertiary); }
.ac-ch__current { color: var(--kh-text); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 480px; }

.ac-ch__head { display: flex; align-items: flex-end; justify-content: space-between; gap: var(--kh-space-4); flex-wrap: wrap; padding-bottom: var(--kh-space-5); border-bottom: 1px solid var(--kh-border-soft); }
.ac-ch__head-main { min-width: 0; flex: 1; }
.ac-ch__title-row { display: flex; align-items: center; gap: var(--kh-space-3); flex-wrap: wrap; }
.ac-ch__title { font-size: var(--kh-font-size-3xl); font-weight: 700; margin: 0; }
.ac-ch__vis { font-size: var(--kh-font-size-sm); color: var(--kh-text-tertiary); margin: var(--kh-space-2) 0 0; }
.ac-ch__head-actions { display: flex; align-items: center; gap: var(--kh-space-2); flex-wrap: wrap; }
/* 文章级操作按钮：编辑文章信息(ghost) / 发布(primary) / 撤回(warn)，与新增章节并排 */
.ac-ch__act {
  display: inline-flex; align-items: center; gap: 6px; height: 38px; padding: 0 var(--kh-space-4);
  border: 1px solid var(--kh-border); border-radius: var(--kh-radius-pill);
  background: var(--kh-surface); color: var(--kh-text-secondary);
  font-size: var(--kh-font-size-sm); font-weight: 600; cursor: pointer;
  transition: all var(--kh-transition-fast);
}
.ac-ch__act:disabled { opacity: 0.5; cursor: not-allowed; }
.ac-ch__act--ghost:hover:not(:disabled) { border-color: var(--kh-primary-border); color: var(--kh-primary); }
.ac-ch__act--primary { background: var(--kh-primary); border-color: var(--kh-primary); color: #fff; }
.ac-ch__act--primary:hover:not(:disabled) { background: var(--kh-primary-strong); }
.ac-ch__act--warn { border-color: var(--kh-warm); color: var(--kh-warm); }
.ac-ch__act--warn:hover:not(:disabled) { background: var(--kh-warm-soft); }
.ac-ch__add { display: inline-flex; align-items: center; gap: 6px; height: 40px; padding: 0 var(--kh-space-5); border: none; border-radius: var(--kh-radius-pill); background: linear-gradient(120deg, var(--kh-primary), var(--kh-primary-strong)); color: #fff; font-weight: 600; font-size: var(--kh-font-size-sm); cursor: pointer; box-shadow: var(--kh-shadow-primary); }
.ac-ch__add:hover { transform: translateY(-1px); }

.ac-ch__list-wrap { padding-bottom: var(--kh-space-12); }
.ac-ch__pager { display: flex; justify-content: center; margin-top: var(--kh-space-6); }
.ac-ch__placeholder { display: flex; flex-direction: column; align-items: center; gap: var(--kh-space-3); padding: var(--kh-space-12); color: var(--kh-text-tertiary); text-align: center; }
.ac-ch__list { list-style: none; margin: var(--kh-space-6) 0 0; padding: 0; display: flex; flex-direction: column; gap: var(--kh-space-3); }
.ac-ch__item { display: flex; align-items: center; gap: var(--kh-space-4); padding: var(--kh-space-4) var(--kh-space-5); background: var(--kh-surface); border: 1px solid var(--kh-border-soft); border-radius: var(--kh-radius-lg); transition: border-color var(--kh-transition-fast); }
.ac-ch__item:hover { border-color: var(--kh-primary-border); }
.ac-ch__item.is-dragging { opacity: 0.4; }
.ac-ch__item.is-drag-over { border-top: 2px solid var(--kh-primary); padding-top: calc(var(--kh-space-4) - 1px); }
.ac-ch__item.is-reordering { pointer-events: none; opacity: 0.6; }
.ac-ch__drag-handle { display: inline-flex; align-items: center; justify-content: center; width: 24px; flex: none; color: var(--kh-text-tertiary); cursor: grab; }
.ac-ch__drag-handle:active { cursor: grabbing; }
.ac-ch__drag-handle:hover { color: var(--kh-primary); }
.ac-ch__no { font-family: var(--kh-font-mono); font-size: var(--kh-font-size-lg); font-weight: 700; color: var(--kh-primary); width: 32px; flex: none; }
.ac-ch__item-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 6px; }
.ac-ch__item-title { font-size: var(--kh-font-size-md); font-weight: 600; color: var(--kh-text); cursor: pointer; }
.ac-ch__item-title:hover { color: var(--kh-primary); }
.ac-ch__item-meta { display: flex; align-items: center; gap: 6px; font-size: 12px; color: var(--kh-text-tertiary); }
.ac-ch__item-actions { display: flex; align-items: center; gap: 6px; flex: none; }
.ac-ch__btn { display: inline-flex; align-items: center; justify-content: center; width: 34px; height: 34px; border: 1px solid var(--kh-border); border-radius: var(--kh-radius-sm); background: var(--kh-surface); color: var(--kh-text-secondary); cursor: pointer; transition: all var(--kh-transition-fast); }
.ac-ch__btn:hover { border-color: var(--kh-primary-border); color: var(--kh-primary); }
.ac-ch__btn--primary { color: var(--kh-primary); border-color: var(--kh-primary-border); }
.ac-ch__btn--primary:hover { background: var(--kh-primary-soft); }
.ac-ch__btn--warn { color: var(--kh-warm); border-color: var(--kh-warm); }
.ac-ch__btn--warn:hover { background: var(--kh-primary-soft); }
.ac-ch__btn--danger { color: var(--kh-danger); border-color: var(--kh-border); }
.ac-ch__btn--danger:hover { border-color: var(--kh-danger); background: var(--kh-primary-soft); }
</style>