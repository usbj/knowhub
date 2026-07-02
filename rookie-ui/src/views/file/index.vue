/**
 * 文件作用：
 * 承接文件存储管理页面，
 * 负责文件对象列表查询、分页展示、上传（测试入口）、下载、详情查看和删除。
 * 关键状态：
 * - `queryForm`：当前查询条件。
 * - `pageState` / `detailVisible` / `detailRecord`：列表分页、详情弹窗状态与数据。
 * 关键依赖：
 * - 复用公共表格、筛选面板，对齐字典管理页结构。
 * - 顶部工具栏放 FileUploadButton（临时测试入口，后期可整块移除）。
 * - 文件无编辑表单，SharedTablePanel 不传 form-visible；详情走独立只读弹窗。
 */
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteFileObjectsApi,
  getFileDetailApi,
  getFilePageApi,
  getDownloadUrlApi,
  buildFilePublicUrl,
} from '@/api/knowhub/file'
import BaseCard from '@/components/BaseCard.vue'
import FileUploadButton from '@/components/FileUploadButton.vue'
import SearchFilterPanel from '@/components/SearchFilterPanel.vue'
import SharedTablePanel from '@/components/SharedTablePanel.vue'
import { SYSTEM_PERMISSION_KEYS } from '@/constants/systemPermissions'
import type { NormalizedPageResult } from '@/types/api/system/common'
import type { FileListQuery, FileObjectRecord } from '@/types/api/knowhub/file'
import type { SharedActionConfig, SharedFieldSchemaMap } from '@/types/components/data-display'
import {
  createDefaultFileQuery,
  createFileQuerySchema,
  createFileSchema,
  type FileQueryFormState,
} from './config'
import FileDetailDialog from './components/FileDetailDialog.vue'

const queryForm = reactive<FileQueryFormState>(createDefaultFileQuery())
const listLoading = ref(false)
const detailVisible = ref(false)
const detailRecord = ref<FileObjectRecord | null>(null)
const pageState = ref<{ records: FileObjectRecord[]; pageNum: number; pageSize: number; pages: number; total: number }>({
  records: [],
  pageNum: 1,
  pageSize: 10,
  pages: 0,
  total: 0,
})

const querySchema = computed<SharedFieldSchemaMap<FileQueryFormState>>(() => createFileQuerySchema())
const tableSchema = computed<SharedFieldSchemaMap<FileObjectRecord>>(() => createFileSchema())
const tablePagination = computed<NormalizedPageResult<Record<string, unknown>>>(() => ({
  records: pageState.value.records,
  pageNum: pageState.value.pageNum,
  pageSize: pageState.value.pageSize,
  pages: pageState.value.pages,
  total: pageState.value.total,
}))

const tableActions = computed<SharedActionConfig<Record<string, unknown>>[]>(() => [
  {
    key: 'download',
    label: '下载',
    permKey: SYSTEM_PERMISSION_KEYS.file.download,
    buttonType: 'primary',
    visible: (row) => String(row.uploadStatus) === 'CONFIRMED',
    onClick: async (row) => {
      await handleDownloadFile(Number(row.objectId), String(row.access ?? ''))
    },
  },
  {
    key: 'detail',
    label: '详情',
    permKey: SYSTEM_PERMISSION_KEYS.file.info,
    buttonType: 'primary',
    onClick: async (row) => {
      await openDetailDialog(Number(row.objectId))
    },
  },
  {
    key: 'delete',
    label: '删除',
    permKey: SYSTEM_PERMISSION_KEYS.file.delete,
    buttonType: 'danger',
    onClick: async (row) => {
      await handleDeleteFile(Number(row.objectId))
    },
  },
])

/**
 * 方法效果：
 * 接收筛选组件回传的新条件对象，并逐项同步到当前页面的查询表单。
 * 参数：
 * - `nextValue`：筛选组件回传的最新查询条件。
 * 返回值：
 * - 无返回值；副作用是更新当前页的查询表单状态。
 */
const handleQueryFormUpdate = (nextValue: Record<string, unknown>) => {
  queryForm.businessType = nextValue.businessType ? String(nextValue.businessType) : undefined
  queryForm.uploadStatus = nextValue.uploadStatus ? String(nextValue.uploadStatus) : undefined
  queryForm.access = nextValue.access ? String(nextValue.access) : undefined
  queryForm.createBy = String(nextValue.createBy ?? '')
  queryForm.dateRange = Array.isArray(nextValue.dateRange)
    ? nextValue.dateRange.map((item) => String(item))
    : []
}

const buildListParams = (): FileListQuery => {
  const [beginTime, endTime] = queryForm.dateRange

  return {
    pageNum: pageState.value.pageNum,
    pageSize: pageState.value.pageSize,
    businessType: queryForm.businessType,
    uploadStatus: queryForm.uploadStatus,
    access: queryForm.access,
    createBy: queryForm.createBy.trim() || undefined,
    beginTime,
    endTime,
  }
}

/**
 * 方法效果：
 * 拉取文件对象分页列表，并更新当前表格与分页状态。
 * 参数：
 * - 无，直接使用当前页的查询条件和分页参数。
 * 返回值：
 * - 无返回值；副作用是刷新表格数据和分页信息。
 */
const fetchPage = async () => {
  listLoading.value = true

  try {
    pageState.value = await getFilePageApi(buildListParams())
  } finally {
    listLoading.value = false
  }
}

/**
 * 方法效果：
 * 执行文件查询，并从第一页重新拉取列表。
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
 * 重置文件查询条件，并恢复初始分页后重新查询。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是清空筛选条件并刷新列表。
 */
const resetQueryForm = async () => {
  Object.assign(queryForm, createDefaultFileQuery())
  pageState.value.pageNum = 1
  pageState.value.pageSize = 10
  await fetchPage()
}

/**
 * 方法效果：
 * 处理表格分页切换，并根据新的页码和每页条数重新拉取数据。
 * 参数：
 * - `payload`：分页组件回传的页码和每页条数。
 * 返回值：
 * - 无返回值；副作用是刷新文件列表。
 */
const handlePaginationChange = async (payload: { pageNum: number; pageSize: number }) => {
  pageState.value.pageNum = payload.pageNum
  pageState.value.pageSize = payload.pageSize
  await fetchPage()
}

/**
 * 方法效果：
 * 下载文件。PUBLIC 对象直接打开 /file/public/{id}（302 到 RustFS）；
 * PRIVATE 对象调 getDownloadUrlApi 拿短期 GET 预签名 URL 后跳转。
 * 参数：
 * - `objectId`：文件对象主键。
 * - `access`：访问语义 PUBLIC/PRIVATE。
 * 返回值：
 * - 无返回值；副作用是打开下载。
 */
const handleDownloadFile = async (objectId: number, access: string) => {
  if (access === 'PUBLIC') {
    // PUBLIC 对象无鉴权，直接打开 /file/public/{id} 走 302 重定向
    window.open(buildFilePublicUrl(objectId), '_blank')
    return
  }

  // PRIVATE 对象走鉴权预签名下载接口
  const result = await getDownloadUrlApi(objectId)
  if (result.data?.downloadUrl) {
    window.open(result.data.downloadUrl, '_blank')
  }
}

/**
 * 方法效果：
 * 打开文件详情弹窗，拉取详情后展示元数据。
 * 参数：
 * - `objectId`：文件对象主键。
 * 返回值：
 * - 无返回值；副作用是更新详情数据并展示弹窗。
 */
const openDetailDialog = async (objectId: number) => {
  const result = await getFileDetailApi(objectId)

  detailRecord.value = result.data
  detailVisible.value = true
}

/**
 * 方法效果：
 * 删除指定文件对象，并在删除成功后自动处理当前分页是否需要回退。
 * 参数：
 * - `objectId`：待删除文件对象主键。
 * 返回值：
 * - 无返回值；副作用是调用删除接口并刷新列表。
 */
const handleDeleteFile = async (objectId: number) => {
  await ElMessageBox.confirm('删除后对象由定时任务物理清理，确认继续吗？', '删除文件', {
    type: 'warning',
  })

  await deleteFileObjectsApi([objectId])
  ElMessage.success('文件删除成功')

  if (pageState.value.records.length === 1 && pageState.value.pageNum > 1) {
    pageState.value.pageNum -= 1
  }

  await fetchPage()
}

/**
 * 方法效果：
 * FileUploadButton 上传成功后的回调，刷新列表以展示新上传的文件。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是刷新文件列表。
 */
const handleUploaded = async () => {
  pageState.value.pageNum = 1
  await fetchPage()
}

onMounted(async () => {
  await fetchPage()
})
</script>

<template>
  <section class="file-view">
    <BaseCard>
      <SearchFilterPanel
        :schema="querySchema"
        :model-value="queryForm as unknown as Record<string, unknown>"
        :columns="3"
        label-width="84px"
        :show-create-button="false"
        @update:model-value="handleQueryFormUpdate"
        @search="handleSearch"
        @reset="resetQueryForm"
      />
    </BaseCard>

    <BaseCard title="文件列表">
      <!-- 顶部工具栏：文件上传测试入口（临时，后期可整块移除） -->
      <div class="file-view__toolbar">
        <FileUploadButton @uploaded="handleUploaded" />
      </div>

      <SharedTablePanel
        :rows="pageState.records as Record<string, unknown>[]"
        :schema="tableSchema"
        :actions="tableActions"
        :loading="listLoading"
        :show-selection="false"
        :pagination="tablePagination"
        :table-max-height="520"
        row-key="objectId"
        @pagination-change="handlePaginationChange"
      />
    </BaseCard>

    <!-- 文件详情只读弹窗 -->
    <FileDetailDialog
      :visible="detailVisible"
      :file-object="detailRecord"
      @update:visible="detailVisible = $event"
    />
  </section>
</template>

<style scoped>
.file-view {
  display: grid;
  gap: 18px;
}

.file-view__toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 14px;
}
</style>
