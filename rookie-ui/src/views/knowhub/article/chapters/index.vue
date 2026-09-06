<!--
  文件作用：
  章节管理二级路由页（无菜单，从文章管理列表"章节"按钮跳转 /knowhub/article/chapters?articleId=）。
  负责该文章下章节的列表查询、分页展示、新增/编辑、提交发布、撤回、作者审核、删除、详情查看。
  关键状态：
  - `articleId`：从路由 query 取，章节按文章维度列表。
  - `articleInfo`：所属文章信息（标题/可见性，顶部面包屑展示）。
  - `queryForm`：当前查询条件。
  - `pageState`：分页状态。
  - `editVisible`/`editMode`/`editChapterId`：章节编辑弹窗。
  - `detailVisible`/`detailRecord`：章节详情弹窗（含正文 content）。
  - `reviewVisible`/`reviewChapterId`/`reviewChapter`/`reviewForm`：章节作者审核弹窗。
  关键依赖：
  - 列表用 SharedTablePanel 仅展示表格（不放内置弹窗表单），编辑用独立 ChapterEditDialog；
  - 章节提交状态机由后端按文章 visibility + 提交者是否作者决定，前端按 status 显隐按钮；
  - 章节作者审核（仅 PENDING_AUTHOR_REVIEW 态）走 ChapterReviewDialog，文章作者审非作者提交的章节。
-->
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteChaptersApi,
  getChapterDetailApi,
  getChapterPageApi,
  publishChapterApi,
  reviewChapterApi,
  revokeChapterApi,
} from '@/api/knowhub/chapter'
import { getArticleDetailApi } from '@/api/knowhub/article'
import BaseCard from '@/components/BaseCard.vue'
import SearchFilterPanel from '@/components/SearchFilterPanel.vue'
import SharedTablePanel from '@/components/SharedTablePanel.vue'
import ChapterEditDialog from '../components/ChapterEditDialog.vue'
import ChapterReviewDialog from '../components/ChapterReviewDialog.vue'
import { SYSTEM_PERMISSION_KEYS } from '@/constants/systemPermissions'
import { useUserStore } from '@/stores/user'
import type { NormalizedPageResult } from '@/types/api/system/common'
import type {
  ChapterListQuery,
  ChapterPageResult,
  ChapterRecord,
  ChapterReviewPayload,
} from '@/types/api/knowhub/chapter'
import type { ArticleRecord } from '@/types/api/knowhub/article'
import type { SharedActionConfig, SharedFieldSchemaMap } from '@/types/components/data-display'
import {
  createDefaultChapterQuery,
  createChapterQuerySchema,
  createChapterSchema,
  type ChapterQueryFormState,
} from './config'

const route = useRoute()
const router = useRouter()

type ChapterDialogMode = 'create' | 'edit'

// 章节作者审核回避前端对齐：章节提交者不能审自己提交的章节（后端 reviewChapter 强判
// chapter.authorId==userId 拒，前端按钮显隐先挡避免点了报错；admin 亦回避）
const userStore = useUserStore()
const currentUserId = computed(() => userStore.userInfo?.userId ?? -1)

// 从路由 query 取文章 ID（章节按文章维度列表）
const articleId = computed(() => Number(route.query.articleId) || 0)

// 所属文章信息（顶部展示标题/可见性）
const articleInfo = ref<ArticleRecord | null>(null)

const queryForm = reactive<ChapterQueryFormState>(createDefaultChapterQuery())
const listLoading = ref(false)
const pageState = ref<ChapterPageResult>({
  records: [],
  pageNum: 1,
  pageSize: 10,
  pages: 0,
  total: 0,
})

// 编辑弹窗
const editVisible = ref(false)
const editMode = ref<ChapterDialogMode>('create')
const editChapterId = ref<number | undefined>(undefined)

// 详情弹窗（含正文 content）
const detailVisible = ref(false)
const detailRecord = ref<ChapterRecord | null>(null)

// 章节作者审核弹窗
const reviewVisible = ref(false)
const reviewChapterId = ref<number | null>(null)
const reviewChapter = ref<ChapterRecord | null>(null)
const reviewLoading = ref(false)
const reviewForm = reactive<{ pass: boolean; advice: string }>({ pass: true, advice: '' })

const querySchema = computed<SharedFieldSchemaMap<ChapterQueryFormState>>(() => createChapterQuerySchema())
const tableSchema = computed<SharedFieldSchemaMap<ChapterRecord>>(() => createChapterSchema())
const tablePagination = computed<NormalizedPageResult<Record<string, unknown>>>(() => ({
  records: pageState.value.records,
  pageNum: pageState.value.pageNum,
  pageSize: pageState.value.pageSize,
  pages: pageState.value.pages,
  total: pageState.value.total,
}))

const tableActions = computed<SharedActionConfig<Record<string, unknown>>[]>(() => [
  {
    key: 'edit',
    label: '编辑',
    permKey: SYSTEM_PERMISSION_KEYS.chapter.create,
    buttonType: 'primary',
    // 仅 DRAFT/REJECTED/REVOKED 可编辑；PUBLISHED 须先撤回、PENDING_AUTHOR_REVIEW 审核中不能改
    visible: (row) => !['PUBLISHED', 'PENDING_AUTHOR_REVIEW'].includes(String(row.status)),
    onClick: async (row) => {
      openEditDialog(Number(row.chapterId))
    },
  },
  {
    key: 'publish',
    label: '提交',
    permKey: SYSTEM_PERMISSION_KEYS.chapter.publish,
    buttonType: 'success',
    // DRAFT/REJECTED/REVOKED 可提交发布；按文章 visibility 决定走不走作者审（后端判定）
    visible: (row) => !['PUBLISHED', 'PENDING_AUTHOR_REVIEW'].includes(String(row.status)),
    onClick: async (row) => {
      await handlePublishChapter(Number(row.chapterId))
    },
  },
  {
    key: 'revoke',
    label: '撤回',
    permKey: SYSTEM_PERMISSION_KEYS.chapter.revoke,
    buttonType: 'warning',
    visible: (row) => String(row.status) === 'PUBLISHED',
    onClick: async (row) => {
      await handleRevokeChapter(Number(row.chapterId))
    },
  },
  {
    key: 'review',
    label: '审核',
    permKey: SYSTEM_PERMISSION_KEYS.chapter.review,
    buttonType: 'primary',
    // 仅 PENDING_AUTHOR_REVIEW 态可审（半公开文章非作者提交后待文章作者审）；
    // 且不能审自己提交的（authorId==当前用户则隐藏，admin 亦回避）
    visible: (row) => String(row.status) === 'PENDING_AUTHOR_REVIEW' && Number(row.authorId) !== currentUserId.value,
    onClick: async (row) => {
      await openReviewDialog(Number(row.chapterId))
    },
  },
  {
    key: 'detail',
    label: '详情',
    buttonType: 'primary',
    onClick: async (row) => {
      await openDetailDialog(Number(row.chapterId))
    },
  },
  {
    key: 'delete',
    label: '删除',
    permKey: SYSTEM_PERMISSION_KEYS.chapter.delete,
    buttonType: 'danger',
    onClick: async (row) => {
      await handleDeleteChapter(Number(row.chapterId))
    },
  },
])

const handleQueryFormUpdate = (nextValue: Record<string, unknown>) => {
  queryForm.chapterName = String(nextValue.chapterName ?? '')
  queryForm.status = nextValue.status ? String(nextValue.status) : undefined
  queryForm.reviewStatus = nextValue.reviewStatus ? String(nextValue.reviewStatus) : undefined
  queryForm.dateRange = Array.isArray(nextValue.dateRange)
    ? nextValue.dateRange.map((item) => String(item))
    : []
}

const buildListParams = (): ChapterListQuery => {
  const [beginTime, endTime] = queryForm.dateRange
  return {
    pageNum: pageState.value.pageNum,
    pageSize: pageState.value.pageSize,
    articleId: articleId.value,
    chapterName: queryForm.chapterName.trim() || undefined,
    status: queryForm.status,
    reviewStatus: queryForm.reviewStatus,
    beginTime,
    endTime,
  }
}

const fetchPage = async () => {
  if (!articleId.value) {
    ElMessage.warning('缺少文章 ID，无法加载章节')
    return
  }
  listLoading.value = true
  try {
    pageState.value = await getChapterPageApi(buildListParams())
  } finally {
    listLoading.value = false
  }
}

const fetchArticleInfo = async () => {
  if (!articleId.value) return
  try {
    const result = await getArticleDetailApi(articleId.value)
    articleInfo.value = result.data
  } catch {
    articleInfo.value = null
  }
}

const handleSearch = async () => {
  pageState.value.pageNum = 1
  await fetchPage()
}

const resetQueryForm = async () => {
  Object.assign(queryForm, createDefaultChapterQuery())
  pageState.value.pageNum = 1
  pageState.value.pageSize = 10
  await fetchPage()
}

const openCreateDialog = () => {
  editMode.value = 'create'
  editChapterId.value = undefined
  editVisible.value = true
}

const openEditDialog = (chapterId: number) => {
  editMode.value = 'edit'
  editChapterId.value = chapterId
  editVisible.value = true
}

const handleEditSaved = () => {
  void fetchPage()
}

const openDetailDialog = async (chapterId: number) => {
  const result = await getChapterDetailApi(chapterId)
  detailRecord.value = result.data
  detailVisible.value = true
}

const openReviewDialog = async (chapterId: number) => {
  reviewLoading.value = true
  try {
    const result = await getChapterDetailApi(chapterId)
    reviewChapter.value = result.data
  } finally {
    reviewLoading.value = false
  }
  reviewChapterId.value = chapterId
  reviewForm.pass = true
  reviewForm.advice = ''
  reviewVisible.value = true
}

const handlePaginationChange = async (payload: { pageNum: number; pageSize: number }) => {
  pageState.value.pageNum = payload.pageNum
  pageState.value.pageSize = payload.pageSize
  await fetchPage()
}

const handlePublishChapter = async (chapterId: number) => {
  await ElMessageBox.confirm('确认提交该章节吗？按文章可见性决定是否需作者审核。', '提交章节', { type: 'warning' })
  await publishChapterApi(chapterId)
  ElMessage.success('章节已提交')
  await fetchPage()
}

const handleRevokeChapter = async (chapterId: number) => {
  await ElMessageBox.confirm('确认撤回该章节吗？撤回后不再可见。', '撤回章节', { type: 'warning' })
  await revokeChapterApi(chapterId)
  ElMessage.success('章节撤回成功')
  await fetchPage()
}

const handleReviewSubmit = async () => {
  if (!reviewChapterId.value) return
  if (!reviewForm.pass && !reviewForm.advice.trim()) {
    ElMessage.warning('驳回需填写审核意见')
    return
  }
  const payload: ChapterReviewPayload = {
    chapterId: reviewChapterId.value,
    pass: reviewForm.pass,
    advice: reviewForm.advice.trim() || undefined,
  }
  await reviewChapterApi(payload)
  ElMessage.success(reviewForm.pass ? '审核通过，章节已发布' : '已驳回章节')
  reviewVisible.value = false
  await fetchPage()
}

const handleDeleteChapter = async (chapterId: number) => {
  await ElMessageBox.confirm('删除后不可恢复，确认继续吗？', '删除章节', { type: 'warning' })
  await deleteChaptersApi([chapterId])
  ElMessage.success('章节删除成功')
  if (pageState.value.records.length === 1 && pageState.value.pageNum > 1) {
    pageState.value.pageNum -= 1
  }
  await fetchPage()
}

/** 返回文章管理列表（文章菜单 route=article，父级 knowhub 目录，实际路径 /knowhub/article） */
const goBack = () => {
  void router.push('/knowhub/article')
}

onMounted(async () => {
  await Promise.all([fetchArticleInfo(), fetchPage()])
})
</script>

<template>
  <section class="chapter-view">
    <BaseCard>
      <div class="chapter-view__header">
        <el-button @click="goBack">← 返回文章列表</el-button>
        <div class="chapter-view__title">
          <span class="chapter-view__title-label">所属文章：</span>
          <span class="chapter-view__title-text">{{ articleInfo?.title || '加载中…' }}</span>
          <span v-if="articleInfo?.visibility" class="chapter-view__title-visibility">
            （可见性：{{ articleInfo.visibility }}）
          </span>
        </div>
      </div>
    </BaseCard>

    <BaseCard>
      <SearchFilterPanel
        :schema="querySchema"
        :model-value="queryForm as unknown as Record<string, unknown>"
        :columns="3"
        label-width="84px"
        create-button-text="新增章节"
        :create-permission-key="SYSTEM_PERMISSION_KEYS.chapter.create"
        @update:model-value="handleQueryFormUpdate"
        @search="handleSearch"
        @reset="resetQueryForm"
        @create="openCreateDialog"
      />
    </BaseCard>

    <BaseCard title="章节列表">
      <SharedTablePanel
        :rows="pageState.records as Record<string, unknown>[]"
        :schema="tableSchema"
        :actions="tableActions"
        :loading="listLoading"
        :show-selection="false"
        :pagination="tablePagination"
        :table-max-height="520"
        :form-visible="false"
        row-key="chapterId"
        @pagination-change="handlePaginationChange"
      />
    </BaseCard>

    <!-- 章节新增/编辑弹窗（含正文 markdown 编辑） -->
    <ChapterEditDialog
      :visible="editVisible"
      :mode="editMode"
      :article-id="articleId"
      :chapter-id="editChapterId"
      @update:visible="editVisible = $event"
      @saved="handleEditSaved"
    />

    <!-- 章节详情弹窗（只读，含正文 content） -->
    <ChapterReviewDialog
      :visible="detailVisible"
      :chapter="detailRecord"
      :review-mode="false"
      @update:visible="detailVisible = $event"
    />

    <!-- 章节作者审核弹窗（仅审核结果+意见） -->
    <ChapterReviewDialog
      :visible="reviewVisible"
      :chapter="reviewChapter"
      :review-mode="true"
      :review-form="reviewForm"
      :review-loading="reviewLoading"
      @update:visible="reviewVisible = $event"
      @submit="handleReviewSubmit"
    />
  </section>
</template>

<style scoped>
.chapter-view {
  display: grid;
  gap: 18px;
}

.chapter-view__header {
  display: flex;
  align-items: center;
  gap: 16px;
}

.chapter-view__title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--rookie-font-size-base);
}

.chapter-view__title-label {
  color: var(--rookie-text-tertiary);
}

.chapter-view__title-text {
  color: var(--rookie-text);
  font-weight: 600;
}

.chapter-view__title-visibility {
  color: var(--rookie-text-secondary);
  font-size: var(--rookie-font-size-sm);
}
</style>