<!--
  文件作用：
  承接文章管理页面（后台菜单名"文章管理"），负责文章列表查询、分页展示、新增、编辑、
  发布、撤回、审核、删除、详情查看，以及跳转到章节管理二级页。
  关键状态：
  - `queryForm`：当前查询条件。
  - `pageState`：分页状态。
  - `editVisible`/`editMode`/`editArticleId`：编辑弹窗显隐、模式、文章主键。
  - `detailVisible`/`detailRecord`：详情弹窗（只读，不改数据）。
  - `reviewVisible`/`reviewArticleId`/`reviewArticle`/`reviewForm`：审核弹窗（只给通过/驳回+意见）。
  关键依赖：
  - 列表用 SharedTablePanel 仅展示表格（不放内置弹窗表单），编辑用独立 ArticleEditDialog；
  - 等级权限(view/edit:lN)由后端 ArticlePermissionResolver 取最高等级判定，
    前端列表可见性由后端 SQL 过滤、详情按钮显隐由后端回填的 canView/canEdit/isAuthor 控制。
  - 章节是文章子模块（无独立菜单），列表"章节"按钮跳二级路由页 /knowhub/article/chapters?articleId=。
-->
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteArticlesApi,
  getArticleDetailApi,
  getArticlePageApi,
  publishArticleApi,
  reviewArticleApi,
  revokeArticleApi,
} from '@/api/knowhub/article'
import BaseCard from '@/components/BaseCard.vue'
import SearchFilterPanel from '@/components/SearchFilterPanel.vue'
import SharedTablePanel from '@/components/SharedTablePanel.vue'
import ArticleEditDialog from './components/ArticleEditDialog.vue'
import ArticleDetailDialog from './components/ArticleDetailDialog.vue'
import { SYSTEM_PERMISSION_KEYS } from '@/constants/systemPermissions'
import { useUserStore } from '@/stores/user'
import type { NormalizedPageResult } from '@/types/api/system/common'
import type {
  ArticleListQuery,
  ArticlePageResult,
  ArticleRecord,
  ArticleReviewPayload,
} from '@/types/api/knowhub/article'
import type { SharedActionConfig, SharedFieldSchemaMap } from '@/types/components/data-display'
import {
  createDefaultArticleQuery,
  createArticleQuerySchema,
  createArticleSchema,
  type ArticleQueryFormState,
} from './config'

const router = useRouter()

type ArticleDialogMode = 'create' | 'edit'

// 审核员回避前端对齐：自己不能审自己提交的文章（后端 reviewArticle 强判 authorId==userId 拒，
// 前端按钮显隐先挡避免点了报错；admin 也不例外——admin 自审自同样隐藏审核按钮）
const userStore = useUserStore()
const currentUserId = computed(() => userStore.userInfo?.userId ?? -1)

const queryForm = reactive<ArticleQueryFormState>(createDefaultArticleQuery())
const listLoading = ref(false)
const pageState = ref<ArticlePageResult>({
  records: [],
  pageNum: 1,
  pageSize: 10,
  pages: 0,
  total: 0,
})

// 编辑弹窗（新增+编辑共用，含文章信息/章节两标签页）
const editVisible = ref(false)
const editMode = ref<ArticleDialogMode>('create')
const editArticleId = ref<number | undefined>(undefined)

// 详情弹窗（只读）
const detailVisible = ref(false)
const detailRecord = ref<ArticleRecord | null>(null)

// 审核弹窗（只给通过/驳回+意见，不展示其它数据）
const reviewVisible = ref(false)
const reviewArticleId = ref<number | null>(null)
const reviewArticle = ref<ArticleRecord | null>(null)
const reviewLoading = ref(false)
const reviewForm = reactive<{ pass: boolean; advice: string }>({ pass: true, advice: '' })

const querySchema = computed<SharedFieldSchemaMap<ArticleQueryFormState>>(() => createArticleQuerySchema())
const tableSchema = computed<SharedFieldSchemaMap<ArticleRecord>>(() => createArticleSchema())
const tablePagination = computed<NormalizedPageResult<Record<string, unknown>>>(() => ({
  records: pageState.value.records,
  pageNum: pageState.value.pageNum,
  pageSize: pageState.value.pageSize,
  pages: pageState.value.pages,
  total: pageState.value.total,
}))

const tableActions = computed<SharedActionConfig<Record<string, unknown>>[]>(() => [
  {
    key: 'chapter',
    label: '章节',
    permKey: SYSTEM_PERMISSION_KEYS.chapter.quarry,
    buttonType: 'primary',
    // 任意状态都可进章节管理页（章节可见性由后端按文章可见性判定）
    onClick: async (row) => {
      openChapterPage(Number(row.articleId))
    },
  },
  {
    key: 'edit',
    label: '编辑',
    permKey: SYSTEM_PERMISSION_KEYS.article.create,
    buttonType: 'primary',
    // 仅 DRAFT/REJECTED/REVOKED 可编辑；PUBLISHED 须先撤回、PENDING_REVIEW 审核中不能改
    visible: (row) => !['PUBLISHED', 'PENDING_REVIEW'].includes(String(row.status)),
    onClick: async (row) => {
      openEditDialog(Number(row.articleId))
    },
  },
  {
    key: 'publish',
    label: '发布',
    permKey: SYSTEM_PERMISSION_KEYS.article.publish,
    buttonType: 'success',
    visible: (row) => !['PUBLISHED', 'PENDING_REVIEW'].includes(String(row.status)),
    onClick: async (row) => {
      await handlePublishArticle(Number(row.articleId))
    },
  },
  {
    key: 'revoke',
    label: '撤回',
    permKey: SYSTEM_PERMISSION_KEYS.article.revoke,
    buttonType: 'warning',
    visible: (row) => String(row.status) === 'PUBLISHED',
    onClick: async (row) => {
      await handleRevokeArticle(Number(row.articleId))
    },
  },
  {
    key: 'review',
    label: '审核',
    permKey: SYSTEM_PERMISSION_KEYS.article.review,
    buttonType: 'primary',
    // 仅 PENDING_REVIEW 可审；且不能审自己提交的（authorId==当前用户则隐藏，admin 亦回避）
    visible: (row) => String(row.status) === 'PENDING_REVIEW' && Number(row.authorId) !== currentUserId.value,
    onClick: async (row) => {
      await openReviewDialog(Number(row.articleId))
    },
  },
  {
    key: 'detail',
    label: '详情',
    buttonType: 'primary',
    onClick: async (row) => {
      await openDetailDialog(Number(row.articleId))
    },
  },
  {
    key: 'delete',
    label: '删除',
    permKey: SYSTEM_PERMISSION_KEYS.article.delete,
    buttonType: 'danger',
    onClick: async (row) => {
      await handleDeleteArticle(Number(row.articleId))
    },
  },
])

const handleQueryFormUpdate = (nextValue: Record<string, unknown>) => {
  queryForm.title = String(nextValue.title ?? '')
  queryForm.level =
    nextValue.level != null && nextValue.level !== '' ? Number(nextValue.level) : undefined
  queryForm.visibility = nextValue.visibility ? String(nextValue.visibility) : undefined
  queryForm.status = nextValue.status ? String(nextValue.status) : undefined
  queryForm.reviewStatus = nextValue.reviewStatus ? String(nextValue.reviewStatus) : undefined
  queryForm.createBy = String(nextValue.createBy ?? '')
  queryForm.dateRange = Array.isArray(nextValue.dateRange)
    ? nextValue.dateRange.map((item) => String(item))
    : []
}

const buildListParams = (): ArticleListQuery => {
  const [beginTime, endTime] = queryForm.dateRange
  return {
    pageNum: pageState.value.pageNum,
    pageSize: pageState.value.pageSize,
    title: queryForm.title.trim() || undefined,
    level: queryForm.level,
    visibility: queryForm.visibility,
    status: queryForm.status,
    reviewStatus: queryForm.reviewStatus,
    createBy: queryForm.createBy.trim() || undefined,
    beginTime,
    endTime,
  }
}

const fetchPage = async () => {
  listLoading.value = true
  try {
    pageState.value = await getArticlePageApi(buildListParams())
  } finally {
    listLoading.value = false
  }
}

const handleSearch = async () => {
  pageState.value.pageNum = 1
  await fetchPage()
}

const resetQueryForm = async () => {
  Object.assign(queryForm, createDefaultArticleQuery())
  pageState.value.pageNum = 1
  pageState.value.pageSize = 10
  await fetchPage()
}

/** 打开新增文章弹窗（只显示文章信息页，提交创建成功后解锁章节页） */
const openCreateDialog = () => {
  editMode.value = 'create'
  editArticleId.value = undefined
  editVisible.value = true
}

/** 打开编辑文章弹窗（文章信息+章节两标签页全部可操作） */
const openEditDialog = (articleId: number) => {
  editMode.value = 'edit'
  editArticleId.value = articleId
  editVisible.value = true
}

/** 编辑弹窗创建成功后回填 articleId，解锁章节页并刷新列表 */
const handleEditSaved = (articleId: number) => {
  editArticleId.value = articleId
  editMode.value = 'edit'
  void fetchPage()
}

/** 跳转到章节管理二级路由页（按 articleId 维度展示该文章的章节列表） */
const openChapterPage = (articleId: number) => {
  void router.push({
    path: '/knowhub/article/chapters',
    query: { articleId: String(articleId) },
  })
}

const openDetailDialog = async (articleId: number) => {
  const result = await getArticleDetailApi(articleId)
  detailRecord.value = result.data
  detailVisible.value = true
}

const openReviewDialog = async (articleId: number) => {
  reviewLoading.value = true
  try {
    const result = await getArticleDetailApi(articleId)
    reviewArticle.value = result.data
  } finally {
    reviewLoading.value = false
  }
  reviewArticleId.value = articleId
  reviewForm.pass = true
  reviewForm.advice = ''
  reviewVisible.value = true
}

const handlePaginationChange = async (payload: { pageNum: number; pageSize: number }) => {
  pageState.value.pageNum = payload.pageNum
  pageState.value.pageSize = payload.pageSize
  await fetchPage()
}

const handlePublishArticle = async (articleId: number) => {
  await ElMessageBox.confirm('确认发布该文章吗？发布后对外可见。', '发布文章', { type: 'warning' })
  await publishArticleApi(articleId)
  ElMessage.success('文章发布成功')
  await fetchPage()
}

const handleRevokeArticle = async (articleId: number) => {
  await ElMessageBox.confirm('确认撤回该文章吗？撤回后不再可见。', '撤回文章', { type: 'warning' })
  await revokeArticleApi(articleId)
  ElMessage.success('文章撤回成功')
  await fetchPage()
}

const handleReviewSubmit = async () => {
  if (!reviewArticleId.value) return
  if (!reviewForm.pass && !reviewForm.advice.trim()) {
    ElMessage.warning('驳回需填写审核意见')
    return
  }
  const payload: ArticleReviewPayload = {
    articleId: reviewArticleId.value,
    pass: reviewForm.pass,
    advice: reviewForm.advice.trim() || undefined,
  }
  await reviewArticleApi(payload)
  ElMessage.success(reviewForm.pass ? '审核通过，文章已发布' : '已驳回文章')
  reviewVisible.value = false
  await fetchPage()
}

const handleDeleteArticle = async (articleId: number) => {
  await ElMessageBox.confirm('删除后不可恢复，确认继续吗？', '删除文章', { type: 'warning' })
  await deleteArticlesApi([articleId])
  ElMessage.success('文章删除成功')
  if (pageState.value.records.length === 1 && pageState.value.pageNum > 1) {
    pageState.value.pageNum -= 1
  }
  await fetchPage()
}

onMounted(async () => {
  await fetchPage()
})
</script>

<template>
  <section class="article-view">
    <BaseCard>
      <SearchFilterPanel
        :schema="querySchema"
        :model-value="queryForm as unknown as Record<string, unknown>"
        :columns="3"
        label-width="84px"
        create-button-text="新增文章"
        :create-permission-key="SYSTEM_PERMISSION_KEYS.article.create"
        @update:model-value="handleQueryFormUpdate"
        @search="handleSearch"
        @reset="resetQueryForm"
        @create="openCreateDialog"
      />
    </BaseCard>

    <BaseCard title="文章列表">
      <!-- 列表仅展示表格，编辑用独立 ArticleEditDialog（含文章信息/章节标签页） -->
      <SharedTablePanel
        :rows="pageState.records as Record<string, unknown>[]"
        :schema="tableSchema"
        :actions="tableActions"
        :loading="listLoading"
        :show-selection="false"
        :pagination="tablePagination"
        :table-max-height="520"
        :form-visible="false"
        row-key="articleId"
        @pagination-change="handlePaginationChange"
      />
    </BaseCard>

    <!-- 新增/编辑文章弹窗（文章信息 / 章节 两标签页） -->
    <ArticleEditDialog
      :visible="editVisible"
      :mode="editMode"
      :article-id="editArticleId"
      @update:visible="editVisible = $event"
      @saved="handleEditSaved"
    />

    <!-- 文章详情弹窗（只读，不改数据） -->
    <ArticleDetailDialog
      :visible="detailVisible"
      :article="detailRecord"
      @update:visible="detailVisible = $event"
    />

    <!-- 文章审核弹窗（仅审核结果+意见，不展示其它数据，不承担改数据职责） -->
    <el-dialog v-model="reviewVisible" title="审核文章" width="520px" destroy-on-close>
      <div v-if="reviewArticle" class="article-review__meta">
        <p><strong>文章标题：</strong>{{ reviewArticle.title }}</p>
        <p v-if="reviewArticle.summary"><strong>摘要：</strong>{{ reviewArticle.summary }}</p>
      </div>
      <el-form label-width="80px">
        <el-form-item label="审核结果" required>
          <el-radio-group v-model="reviewForm.pass">
            <el-radio :value="true">通过</el-radio>
            <el-radio :value="false">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审核意见" :required="!reviewForm.pass">
          <el-input
            v-model="reviewForm.advice"
            type="textarea"
            :rows="3"
            :placeholder="reviewForm.pass ? '通过意见（可选）' : '驳回意见（必填）'"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button type="primary" :loading="reviewLoading" @click="handleReviewSubmit">提交审核</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.article-view {
  display: grid;
  gap: 18px;
}

.article-review__meta {
  margin-bottom: 12px;
  padding: 10px 14px;
  border: 1px solid var(--rookie-border);
  border-radius: var(--rookie-radius-md);
  background: var(--rookie-surface-weak);
  font-size: var(--rookie-font-size-sm);
  color: var(--rookie-text-secondary);
}

.article-review__meta p {
  margin: 4px 0;
}
</style>