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
import { ElButton, ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteFileObjectsApi,
  getFileDetailApi,
  getFilePageApi,
  getDownloadUrlApi,
  packSizeApi,
  packDownloadServerApi,
} from '@/api/knowhub/file'
import BaseCard from '@/components/BaseCard.vue'
import FileUploadButton from '@/components/FileUploadButton.vue'
import SearchFilterPanel from '@/components/SearchFilterPanel.vue'
import SharedTablePanel from '@/components/SharedTablePanel.vue'
import { SYSTEM_PERMISSION_KEYS } from '@/constants/systemPermissions'
import { usePermission } from '@/composables/usePermission'
import { USER_TOKEN_STORAGE_KEY } from '@/stores/user'
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
import MigrationDialog from './components/MigrationDialog.vue'

const { hasPermission } = usePermission()

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
 * 下载文件。PUBLIC 与 PRIVATE 统一走 getDownloadUrlApi 拿下载链接（后端按访问模式发，
 * 中转模式→/file/proxy/{id} 带 attachment;filename；直链模式→预签名绝对 URL 带 attachment），
 * 再按链接形态触发下载：
 * - 相对路径（/file/proxy/{id}，中转模式）：同源鉴权接口，window.open 不带 Token 会 401，
 *   改用 fetch 带 Token 头取 blob 再 a.click() 触发下载；
 * - 绝对 URL（直链模式，预签名或 nginx 代理）：直接 window.open 跳转拉取（预签名自带 attachment）。
 * 注意：不走 /file/public/{id}（那是回显接口，内联显示图片，不触发下载）。
 * 参数：
 * - `objectId`：文件对象主键。
 * - `access`：访问语义 PUBLIC/PRIVATE（仅用于判断，链接形态由后端决定）。
 * 返回值：
 * - 无返回值；副作用是触发下载。
 */
const handleDownloadFile = async (objectId: number, _access: string) => {
  // PUBLIC/PRIVATE 统一走下载接口拿带 attachment;filename 的链接（不走 /file/public/{id} 回显接口）
  const result = await getDownloadUrlApi(objectId)
  const downloadUrl = result.data?.downloadUrl
  if (!downloadUrl) {
    return
  }

  // 相对路径 = 中转模式同源鉴权接口，需带 Token；绝对 URL = 直链模式直接跳转
  if (downloadUrl.startsWith('http://') || downloadUrl.startsWith('https://')) {
    window.open(downloadUrl, '_blank')
    return
  }

  // 中转模式：fetch 带 Token 头取 blob，再触发下载（文件名优先取接口返回的 originalName，其次响应头 Content-Disposition）
  const fallbackName = result.data?.originalName ?? `${objectId}`
  try {
    const token = localStorage.getItem(USER_TOKEN_STORAGE_KEY)
    const resp = await fetch(downloadUrl, {
      headers: token ? { Token: token } : {},
    })
    if (!resp.ok) {
      ElMessage.error(`下载失败：HTTP ${resp.status}`)
      return
    }
    const blob = await resp.blob()
    const objUrl = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = objUrl
    // 文件名取响应头 Content-Disposition 的 filename，取不到用接口返回的 originalName 兜底
    const disposition = resp.headers.get('Content-Disposition') ?? ''
    const nameMatch = disposition.match(/filename="?([^";]+)"?/)
    a.download = nameMatch?.[1] ?? fallbackName
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(objUrl)
  } catch {
    ElMessage.error('下载失败：网络异常')
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

// 50G 阈值（50 × 1024³ 字节），打包下载预估总大小超此值时弹警告确认。
const PACK_SIZE_WARN_THRESHOLD = 50 * 1024 * 1024 * 1024
// 打包下载按钮权限守卫（按 knowhub:file:pack-download 进页面门槛对齐 systemPermissions.ts）。
const canPackDownload = computed(() => hasPermission(SYSTEM_PERMISSION_KEYS.file.packDownload))
const packLoading = ref(false)

// 数据迁移按钮权限守卫（按 knowhub:file:transfer 进页面门槛对齐 systemPermissions.ts）。
const canTransfer = computed(() => hasPermission(SYSTEM_PERMISSION_KEYS.file.transfer))
const migrationVisible = ref(false)

const handleOpenMigration = () => {
  migrationVisible.value = true
}

/**
 * 方法效果：
 * 一键打包下载 OSS 中所有文件成一个 zip（扩展点1）。流程：
 * 1. 调 packSizeApi 取预估总字节数；超 50G 弹 ElMessageBox.confirm 警告，管理员确认后继续。
 * 2. 弹「下载到客户端本地 / 服务器本地」二选一（中转模式强制服务器本地，跳过此选择）。
 * 3. 客户端：fetch 带 Token 头取 /file/pack-download 的 zip blob 再 a.click() 触发下载（同中转下载口径）。
 * 4. 服务器：调 packDownloadServerApi 返落盘绝对路径，ElMessage.success 提示。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是触发打包下载或提示落盘路径。
 */
const handlePackDownload = async () => {
  packLoading.value = true
  let totalSize = 0
  try {
    const sizeResult = await packSizeApi()
    totalSize = Number(sizeResult.data ?? 0)
  } catch {
    packLoading.value = false
    ElMessage.error('预估打包大小失败')
    return
  }

  // 超 50G 弹警告确认（管理员确认后方可继续）
  if (totalSize > PACK_SIZE_WARN_THRESHOLD) {
    const sizeGb = (totalSize / 1024 / 1024 / 1024).toFixed(2)
    try {
      await ElMessageBox.confirm(
        `当前 OSS 文件总大小约 ${sizeGb} GB，超过 50G 阈值，打包下载可能耗时较长且占用带宽/磁盘，确认继续吗？`,
        '打包下载警告',
        { type: 'warning' },
      )
    } catch {
      packLoading.value = false
      return
    }
  }

  // 下载目标选择：直链模式可选客户端/服务器本地，中转模式强制服务器本地（用户浏览器不可达 OSS）
  // 默认按直链模式弹选择；中转模式无客户端下载意义，直接走服务器本地。
  // 注：前端不感知当前后端 access_mode，统一弹二选一让管理员决定，中转模式下选客户端会失败回退提示。
  let toServer = false
  try {
    const action = await ElMessageBox.confirm(
      '请选择打包下载目标：客户端本地（浏览器直接下载 zip）或服务器本地（落服务器磁盘后人工取）。',
      '打包下载目标',
      {
        distinguishCancelAndClose: true,
        confirmButtonText: '服务器本地',
        cancelButtonText: '客户端本地',
        type: 'info',
      },
    )
    // confirmButtonText 命中 → 服务器本地
    toServer = true
    void action
  } catch (action) {
    // 取消键（cancelButtonText）→ 客户端本地；关闭弹窗 → 中止
    if (action === 'close') {
      packLoading.value = false
      return
    }
    toServer = false
  }

  if (toServer) {
    // 服务器本地：调 pack-download-server 返落盘绝对路径
    try {
      const result = await packDownloadServerApi()
      const path = result.data
      if (path) {
        ElMessage.success(`已打包下载到服务器本地：${path}`)
      } else {
        ElMessage.warning('打包下载到服务器本地完成，但未返回路径')
      }
    } catch {
      ElMessage.error('打包下载到服务器失败')
    } finally {
      packLoading.value = false
    }
    return
  }

  // 客户端本地：fetch 带 Token 头取 /file/pack-download 的 zip blob 再触发下载（同中转下载口径）
  try {
    const token = localStorage.getItem(USER_TOKEN_STORAGE_KEY)
    const resp = await fetch('/file/pack-download', {
      headers: token ? { Token: token } : {},
    })
    if (!resp.ok) {
      ElMessage.error(`打包下载失败：HTTP ${resp.status}`)
      return
    }
    const blob = await resp.blob()
    const objUrl = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = objUrl
    // zip 名取响应头 Content-Disposition 的 filename，取不到用时间戳兜底
    const disposition = resp.headers.get('Content-Disposition') ?? ''
    const nameMatch = disposition.match(/filename\*=UTF-8''([^;]+)/i) ?? disposition.match(/filename="?([^";]+)"?/)
    a.download = nameMatch?.[1] ? decodeURIComponent(nameMatch[1]) : `knowhub-oss-backup-${Date.now()}.zip`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(objUrl)
  } catch {
    ElMessage.error('打包下载失败：网络异常')
  } finally {
    packLoading.value = false
  }
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
      <!-- 顶部工具栏：文件上传测试入口（临时，后期可整块移除）+ 扩展点1 打包下载 OSS + 扩展点2 数据迁移 -->
      <div class="file-view__toolbar">
        <FileUploadButton @uploaded="handleUploaded" />
        <ElButton
          v-if="canPackDownload"
          type="warning"
          :loading="packLoading"
          @click="handlePackDownload"
        >
          打包下载 OSS
        </ElButton>
        <ElButton
          v-if="canTransfer"
          type="primary"
          @click="handleOpenMigration"
        >
          数据迁移
        </ElButton>
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

    <!-- 扩展点2：OSS 数据迁移弹窗 -->
    <MigrationDialog
      :visible="migrationVisible"
      @update:visible="migrationVisible = $event"
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
  gap: 12px;
  margin-bottom: 14px;
}
</style>
