/**
 * 文件作用：
 * 承接博客模块下的文章管理页面，
 * 负责博客列表查询、分页展示、新增、编辑、发布、撤回、审核、删除和只读详情查看。
 * 关键状态：
 * - `tagOptions`：启用标签下拉选项，供筛选区与弹窗内 tagIds 多选复用。
 * - `queryForm` / `formModel`：当前查询条件与弹窗表单模型。
 * - `pageState` / `dialogVisible` / `detailVisible` / `reviewVisible`：分页、编辑弹窗、详情弹窗、审核弹窗状态。
 * 关键依赖：
 * - 复用公共表格、公共表单、筛选面板，对齐通知内容管理页结构。
 * - tagIds / coverUrl 字段用 #field-* 插槽接管为标签多选与封面上传组件。
 */
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElButton, ElMessage, ElMessageBox, ElOption, ElSelect } from 'element-plus'
import {
  createBlogApi,
  deleteBlogsApi,
  getBlogDetailApi,
  getBlogPageApi,
  publishBlogApi,
  reviewBlogApi,
  revokeBlogApi,
  updateBlogApi,
} from '@/api/knowhub/blog'
import { getTagListApi } from '@/api/knowhub/tag'
import BaseCard from '@/components/BaseCard.vue'
import SearchFilterPanel from '@/components/SearchFilterPanel.vue'
import SharedTablePanel from '@/components/SharedTablePanel.vue'
import BlogDetailDialog from './components/BlogDetailDialog.vue'
import BlogReviewDialog from './components/BlogReviewDialog.vue'
import BlogCoverUploader from './components/BlogCoverUploader.vue'
import BlogContentEditor from './components/BlogContentEditor.vue'
import { SYSTEM_PERMISSION_KEYS } from '@/constants/systemPermissions'
import { useUserStore } from '@/stores/user'
import type { NormalizedPageResult } from '@/types/api/system/common'
import type { BlogListQuery, BlogPageResult, BlogRecord, ReviewPayload } from '@/types/api/knowhub/blog'
import type { SharedActionConfig, SharedFieldSchemaMap } from '@/types/components/data-display'
import {
  createDefaultBlogForm,
  createDefaultBlogQuery,
  createBlogQuerySchema,
  createBlogSchema,
  blogFormRules,
  type BlogQueryFormState,
  type TagOption,
} from './config'

type BlogDialogMode = 'create' | 'edit'

const queryForm = reactive<BlogQueryFormState>(createDefaultBlogQuery())
const tagOptions = ref<TagOption[]>([])
const listLoading = ref(false)
const submitLoading = ref(false)
const dialogVisible = ref(false)
const dialogMode = ref<BlogDialogMode>('create')
const formModel = ref<BlogRecord>(createDefaultBlogForm())
const detailVisible = ref(false)
const detailRecord = ref<BlogRecord | null>(null)
const reviewVisible = ref(false)
const reviewBlogId = ref<number | null>(null)
/** 待审核博客详情（审核弹窗展示标题/封面/正文用） */
const reviewBlog = ref<BlogRecord | null>(null)
const reviewLoading = ref(false)
/** 正文全屏编辑器显隐 */
const contentEditorVisible = ref(false)

// 编辑/发布/撤回仅作者 OR 超级管理员可操作（后端 canEditBlog 强判，前端按钮显隐对齐避免点了报错）
const userStore = useUserStore()
const currentUserId = computed(() => userStore.userInfo?.userId ?? -1)
const isAdmin = computed(() => userStore.userInfo?.userRole?.some((r) => r.roleKey === 'admin') ?? false)
/** 当前用户是否可改某博客（作者本人 OR 超级管理员） */
const canEditRow = (row: Record<string, unknown>) =>
  Number(row.authorId) === currentUserId.value || isAdmin.value
const pageState = ref<BlogPageResult>({
  records: [],
  pageNum: 1,
  pageSize: 10,
  pages: 0,
  total: 0,
})

const querySchema = computed<SharedFieldSchemaMap<BlogQueryFormState>>(() =>
  createBlogQuerySchema(tagOptions.value),
)
const tableSchema = computed<SharedFieldSchemaMap<BlogRecord>>(() =>
  createBlogSchema(tagOptions.value),
)
const dialogTitle = computed(() => (dialogMode.value === 'create' ? '新增博客' : '编辑博客'))
const dialogSubmitText = computed(() => (dialogMode.value === 'create' ? '创建博客' : '保存修改'))
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
    permKey: SYSTEM_PERMISSION_KEYS.blog.edit,
    buttonType: 'primary',
    // 仅 DRAFT/REJECTED/REVOKED 可编辑；PUBLISHED 须先撤回、PENDING_REVIEW 审核中不能改；
    // 且仅作者本人 OR 超级管理员可见（编辑不分等级，对齐后端 canEditBlog）
    visible: (row) =>
      !['PUBLISHED', 'PENDING_REVIEW'].includes(String(row.status)) && canEditRow(row),
    onClick: async (row) => {
      await openEditDialog(Number(row.blogId))
    },
  },
  {
    key: 'publish',
    label: '发布',
    permKey: SYSTEM_PERMISSION_KEYS.blog.publish,
    buttonType: 'success',
    // 仅非发布且非待审状态可发布；PUBLISHED 无需重复发布、PENDING_REVIEW 已在审不可重复提交；
    // 且仅作者本人 OR 超级管理员可见（发布属编辑范畴）
    visible: (row) =>
      !['PUBLISHED', 'PENDING_REVIEW'].includes(String(row.status)) && canEditRow(row),
    onClick: async (row) => {
      await handlePublishBlog(Number(row.blogId))
    },
  },
  {
    key: 'revoke',
    label: '撤回',
    permKey: SYSTEM_PERMISSION_KEYS.blog.revoke,
    buttonType: 'warning',
    // 仅已发布可撤回；且仅作者本人 OR 超级管理员可见（撤回属编辑范畴）
    visible: (row) => String(row.status) === 'PUBLISHED' && canEditRow(row),
    onClick: async (row) => {
      await handleRevokeBlog(Number(row.blogId))
    },
  },
  {
    key: 'review',
    label: '审核',
    permKey: SYSTEM_PERMISSION_KEYS.blog.review,
    buttonType: 'primary',
    // 仅 PENDING_REVIEW 可审；且不能审自己提交的（authorId==当前用户则隐藏，admin 亦回避——
    // 后端 reviewBlog 强判 authorId==userId 拒，前端按钮显隐先挡避免点了报错）
    visible: (row) => String(row.status) === 'PENDING_REVIEW' && Number(row.authorId) !== currentUserId.value,
    onClick: async (row) => {
      await openReviewDialog(Number(row.blogId))
    },
  },
  {
    key: 'detail',
    label: '详情',
    buttonType: 'primary',
    onClick: async (row) => {
      await openDetailDialog(Number(row.blogId))
    },
  },
  {
    key: 'delete',
    label: '删除',
    permKey: SYSTEM_PERMISSION_KEYS.blog.delete,
    buttonType: 'danger',
    onClick: async (row) => {
      await handleDeleteBlog(Number(row.blogId))
    },
  },
])

/**
 * 方法效果：
 * 拉取启用标签下拉选项，供筛选区与弹窗内 tagIds 多选复用。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是更新标签选项数据源。
 */
const fetchTagOptions = async () => {
  const result = await getTagListApi({ status: 1 })

  tagOptions.value = (result.data ?? []).map((item) => ({
    label: item.tagName,
    value: Number(item.tagId),
  }))
}

/**
 * 方法效果：
 * 接收筛选组件回传的新条件对象，并逐项同步到当前页面的查询表单。
 * 参数：
 * - `nextValue`：筛选组件回传的最新查询条件。
 * 返回值：
 * - 无返回值；副作用是更新当前页的查询表单状态。
 */
const handleQueryFormUpdate = (nextValue: Record<string, unknown>) => {
  queryForm.title = String(nextValue.title ?? '')
  queryForm.keyword = String(nextValue.keyword ?? '')
  queryForm.tagIds = Array.isArray(nextValue.tagIds) ? nextValue.tagIds.map((item) => Number(item)) : []
  queryForm.level = nextValue.level != null && nextValue.level !== '' ? Number(nextValue.level) : undefined
  queryForm.status = nextValue.status ? String(nextValue.status) : undefined
  queryForm.reviewStatus = nextValue.reviewStatus ? String(nextValue.reviewStatus) : undefined
  queryForm.createBy = String(nextValue.createBy ?? '')
  queryForm.dateRange = Array.isArray(nextValue.dateRange)
    ? nextValue.dateRange.map((item) => String(item))
    : []
}

const buildListParams = (): BlogListQuery => {
  const [beginTime, endTime] = queryForm.dateRange

  return {
    pageNum: pageState.value.pageNum,
    pageSize: pageState.value.pageSize,
    title: queryForm.title.trim() || undefined,
    keyword: queryForm.keyword.trim() || undefined,
    level: queryForm.level,
    tagIds: queryForm.tagIds.length ? queryForm.tagIds : undefined,
    status: queryForm.status,
    reviewStatus: queryForm.reviewStatus,
    createBy: queryForm.createBy.trim() || undefined,
    beginTime,
    endTime,
  }
}

/**
 * 方法效果：
 * 拉取博客分页列表，并更新当前表格与分页状态。
 * 参数：
 * - 无，直接使用当前页的查询条件和分页参数。
 * 返回值：
 * - 无返回值；副作用是刷新表格数据和分页信息。
 */
const fetchPage = async () => {
  listLoading.value = true

  try {
    pageState.value = await getBlogPageApi(buildListParams())
  } finally {
    listLoading.value = false
  }
}

/**
 * 方法效果：
 * 执行博客查询，并从第一页重新拉取列表。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是刷新列表。
 */
const handleSearch = async () => {
  pageState.value.pageNum = 1
  await fetchPage()
}

/**
 * 方法效果：
 * 重置博客查询条件，并恢复初始分页后重新查询。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是清空筛选条件并刷新列表。
 */
const resetQueryForm = async () => {
  Object.assign(queryForm, createDefaultBlogQuery())
  pageState.value.pageNum = 1
  pageState.value.pageSize = 10
  await fetchPage()
}

/**
 * 方法效果：
 * 打开新增博客弹窗，并准备一份干净的表单模型。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是切换弹窗状态并重置表单。
 */
const openCreateDialog = () => {
  dialogMode.value = 'create'
  formModel.value = createDefaultBlogForm()
  dialogVisible.value = true
}

/**
 * 方法效果：
 * 打开编辑博客弹窗，并先拉取详情用于完整回显（含正文、标签、封面）。
 * 参数：
 * - `blogId`：待编辑博客主键。
 * 返回值：
 * - 无返回值；副作用是更新表单模型并展示弹窗。
 */
const openEditDialog = async (blogId: number) => {
  const result = await getBlogDetailApi(blogId)

  dialogMode.value = 'edit'
  formModel.value = {
    ...createDefaultBlogForm(),
    ...result.data,
    tagIds: Array.isArray(result.data.tagIds) ? result.data.tagIds.map((item) => Number(item)) : [],
  }
  dialogVisible.value = true
}

/**
 * 方法效果：
 * 打开只读详情弹窗，拉取博客详情后展示正文、封面、审核信息。
 * 参数：
 * - `blogId`：待查看博客主键。
 * 返回值：
 * - 无返回值；副作用是更新详情数据并展示弹窗。
 */
const openDetailDialog = async (blogId: number) => {
  const result = await getBlogDetailApi(blogId)

  detailRecord.value = result.data
  detailVisible.value = true
}

/**
 * 方法效果：
 * 打开审核弹窗，先拉取博客详情（标题/封面/正文）供审核员参考决策。
 * 参数：
 * - `blogId`：待审核博客主键。
 * 返回值：
 * - 无返回值；副作用是更新审核博客详情并展示弹窗。
 */
const openReviewDialog = async (blogId: number) => {
  reviewLoading.value = true
  try {
    const result = await getBlogDetailApi(blogId)
    reviewBlog.value = result.data
  } finally {
    reviewLoading.value = false
  }
  reviewBlogId.value = blogId
  reviewVisible.value = true
}

/**
 * 方法效果：
 * 打开正文全屏编辑器。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是展示正文编辑弹窗。
 */
const openContentEditor = () => {
  contentEditorVisible.value = true
}

/**
 * 方法效果：
 * 接收正文编辑器回传的正文，同步到当前弹窗表单模型。
 * 参数：
 * - `value`：编辑器回写的新正文 markdown。
 * 返回值：
 * - 无返回值；副作用是更新表单 content 字段。
 */
const handleContentUpdate = (value: string) => {
  formModel.value = {
    ...formModel.value,
    content: value,
  }
}

/** 正文预览摘要：取正文前 60 字符（去 markdown 符号粗略清洗）作为表单内「编辑正文」按钮旁的提示 */
const contentPreview = computed(() => {
  const raw = formModel.value.content ?? ''
  const plain = raw.replace(/[#*`>\-\[\]()!]/g, '').replace(/\s+/g, ' ').trim()
  if (!plain) {
    return ''
  }
  return plain.length > 60 ? `${plain.slice(0, 60)}…` : plain
})


/**
 * 方法效果：
 * 接收公共表单回传的新模型，并同步为当前弹窗表单状态。
 * 参数：
 * - `nextValue`：公共表单组件回传的新表单对象。
 * 返回值：
 * - 无返回值；副作用是覆盖当前弹窗表单状态。
 */
const handleFormModelUpdate = (nextValue: Record<string, unknown>) => {
  formModel.value = {
    ...formModel.value,
    ...nextValue,
    tagIds: Array.isArray(nextValue.tagIds) ? (nextValue.tagIds as number[]) : formModel.value.tagIds ?? [],
    coverUrl:
      nextValue.coverUrl === undefined || nextValue.coverUrl === null
        ? formModel.value.coverUrl
        : String(nextValue.coverUrl),
    level:
      nextValue.level === undefined || nextValue.level === null || nextValue.level === ''
        ? formModel.value.level
        : Number(nextValue.level),
  }
}

/**
 * 方法效果：
 * 处理表格分页切换，并根据新的页码和每页条数重新拉取数据。
 * 参数：
 * - `payload`：分页组件回传的页码和每页条数。
 * 返回值：
 * - 无返回值；副作用是刷新博客列表。
 */
const handlePaginationChange = async (payload: { pageNum: number; pageSize: number }) => {
  pageState.value.pageNum = payload.pageNum
  pageState.value.pageSize = payload.pageSize
  await fetchPage()
}

/**
 * 方法效果：
 * 提交新增或编辑博客表单。
 * 参数：
 * - 无，直接读取当前弹窗表单模型和弹窗模式。
 * 返回值：
 * - 无返回值；副作用是调用保存接口、关闭弹窗并刷新列表。
 */
const handleSubmitForm = async () => {
  submitLoading.value = true

  try {
    const payload: BlogRecord = {
      ...formModel.value,
      title: formModel.value.title.trim(),
      content: formModel.value.content.trim(),
      summary: formModel.value.summary?.trim() || '',
      coverUrl: formModel.value.coverUrl?.trim() || '',
      tagIds: formModel.value.tagIds ?? [],
      level: formModel.value.level ?? 1,
    }

    if (dialogMode.value === 'create') {
      await createBlogApi(payload)
      ElMessage.success('博客创建成功')
    } else {
      await updateBlogApi(payload)
      ElMessage.success('博客更新成功')
    }

    dialogVisible.value = false
    await fetchPage()
  } finally {
    submitLoading.value = false
  }
}

/**
 * 方法效果：
 * 发布指定博客。受全局审核开关控制：开关关→直接发布；开关开→进入待审核。
 * 参数：
 * - `blogId`：待发布博客主键。
 * 返回值：
 * - 无返回值；副作用是调用发布接口并刷新列表。
 */
const handlePublishBlog = async (blogId: number) => {
  await ElMessageBox.confirm('确认发布该博客吗？发布后对读者可见。', '发布博客', {
    type: 'warning',
  })

  await publishBlogApi(blogId)
  ElMessage.success('博客发布成功')
  await fetchPage()
}

/**
 * 方法效果：
 * 撤回已发布的博客，使其回到已撤回状态。
 * 参数：
 * - `blogId`：待撤回博客主键。
 * 返回值：
 * - 无返回值；副作用是调用撤回接口并刷新列表。
 */
const handleRevokeBlog = async (blogId: number) => {
  await ElMessageBox.confirm('确认撤回该博客吗？撤回后不再对读者可见。', '撤回博客', {
    type: 'warning',
  })

  await revokeBlogApi(blogId)
  ElMessage.success('博客撤回成功')
  await fetchPage()
}

/**
 * 方法效果：
 * 接收审核弹窗的提交结果，调审核接口（通过/驳回）并刷新列表。
 * 参数：
 * - `payload`：审核入参（blogId / pass / advice）。
 * 返回值：
 * - 无返回值；副作用是调用审核接口、关闭弹窗并刷新列表。
 */
const handleReviewSubmit = async (payload: ReviewPayload) => {
  await reviewBlogApi(payload)
  ElMessage.success(payload.pass ? '审核通过，博客已发布' : '已驳回博客')
  reviewVisible.value = false
  await fetchPage()
}

/**
 * 方法效果：
 * 删除指定博客，并在删除成功后自动处理当前分页是否需要回退。
 * 参数：
 * - `blogId`：待删除博客主键。
 * 返回值：
 * - 无返回值；副作用是调用删除接口并刷新列表。
 */
const handleDeleteBlog = async (blogId: number) => {
  await ElMessageBox.confirm('删除后不可恢复，确认继续吗？', '删除博客', {
    type: 'warning',
  })

  await deleteBlogsApi([blogId])
  ElMessage.success('博客删除成功')

  if (pageState.value.records.length === 1 && pageState.value.pageNum > 1) {
    pageState.value.pageNum -= 1
  }

  await fetchPage()
}

onMounted(async () => {
  await Promise.all([fetchTagOptions(), fetchPage()])
})
</script>

<template>
  <section class="blog-view">
    <BaseCard>
      <SearchFilterPanel
        :schema="querySchema"
        :model-value="queryForm as unknown as Record<string, unknown>"
        :columns="3"
        label-width="84px"
        create-button-text="新增博客"
        :create-permission-key="SYSTEM_PERMISSION_KEYS.blog.create"
        @update:model-value="handleQueryFormUpdate"
        @search="handleSearch"
        @reset="resetQueryForm"
        @create="openCreateDialog"
      >
        <!-- 筛选区标签多选：custom 字段插槽接管为 ElSelect multiple -->
        <template #field-tagIds="{ modelValue, updateFieldValue }">
          <ElSelect
            :model-value="Array.isArray(modelValue) ? (modelValue as number[]) : []"
            multiple
            filterable
            clearable
            collapse-tags
            collapse-tags-tooltip
            placeholder="请选择标签"
            style="width: 100%"
            @update:model-value="updateFieldValue"
          >
            <ElOption
              v-for="item in tagOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </template>
      </SearchFilterPanel>
    </BaseCard>

    <BaseCard title="博客列表">
      <!-- 公共表格区域 -->
      <SharedTablePanel
        :rows="pageState.records as Record<string, unknown>[]"
        :schema="tableSchema"
        :actions="tableActions"
        :loading="listLoading"
        :form-loading="submitLoading"
        :show-selection="false"
        :pagination="tablePagination"
        :table-max-height="520"
        :form-visible="dialogVisible"
        :form-model-value="formModel as unknown as Record<string, unknown>"
        :form-title="dialogTitle"
        :form-submit-text="dialogSubmitText"
        :form-columns="1"
        :form-rules="blogFormRules"
        row-key="blogId"
        @pagination-change="handlePaginationChange"
        @update:form-visible="dialogVisible = $event"
        @update:form-model-value="handleFormModelUpdate"
        @form-submit="handleSubmitForm"
        @form-cancel="dialogVisible = false"
      >
        <!-- 表单内标签多选：custom 字段插槽接管为 ElSelect multiple -->
        <template #field-tagIds="{ modelValue, updateFieldValue }">
          <ElSelect
            :model-value="Array.isArray(modelValue) ? (modelValue as number[]) : []"
            multiple
            filterable
            clearable
            collapse-tags
            collapse-tags-tooltip
            placeholder="请选择标签"
            style="width: 100%"
            @update:model-value="updateFieldValue"
          >
            <ElOption
              v-for="item in tagOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </template>

        <!-- 表单内封面上传：custom 字段插槽接管为 BlogCoverUploader -->
        <template #field-coverUrl="{ modelValue, updateFieldValue }">
          <BlogCoverUploader
            :model-value="String(modelValue ?? '')"
            @update:model-value="updateFieldValue"
          />
        </template>

        <!-- 表单内正文：custom 字段插槽接管为「编辑正文」按钮 + 摘要预览 -->
        <template #field-content>
          <div class="blog-view__content-entry">
            <ElButton type="primary" plain @click="openContentEditor">编辑正文</ElButton>
            <span v-if="contentPreview" class="blog-view__content-preview">{{ contentPreview }}</span>
            <span v-else class="blog-view__content-empty">未填写正文</span>
          </div>
        </template>
      </SharedTablePanel>
    </BaseCard>

    <!-- 博客详情只读弹窗 -->
    <BlogDetailDialog
      :visible="detailVisible"
      :blog="detailRecord"
      @update:visible="detailVisible = $event"
    />

    <!-- 博客审核弹窗（展示标题/封面/正文供审核员参考） -->
    <BlogReviewDialog
      :visible="reviewVisible"
      :blog-id="reviewBlogId"
      :blog="reviewBlog"
      :loading="reviewLoading"
      @update:visible="reviewVisible = $event"
      @submit="handleReviewSubmit"
    />

    <!-- 正文全屏编辑器（CSDN 风格双栏 + 图片预签名直传） -->
    <BlogContentEditor
      :visible="contentEditorVisible"
      :model-value="formModel.content ?? ''"
      @update:visible="contentEditorVisible = $event"
      @update:model-value="handleContentUpdate"
    />
  </section>
</template>

<style scoped>
.blog-view {
  display: grid;
  gap: 18px;
}

.blog-view__content-entry {
  display: flex;
  align-items: center;
  gap: 12px;
}

.blog-view__content-preview {
  color: var(--rookie-text-secondary);
  font-size: var(--rookie-font-size-sm);
}

.blog-view__content-empty {
  color: var(--rookie-text-tertiary);
  font-size: var(--rookie-font-size-sm);
}
</style>
