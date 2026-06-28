/**
 * 文件作用：
 * 承接日志管理模块下的操作日志页面，
 * 负责操作日志列表查询、分页展示、只读详情查看、批量删除和清空。
 * 关键状态：
 * - `queryForm`：当前筛选条件对象。
 * - `pageState`：列表分页状态。
 * - `selectedRows`：表格多选结果，驱动批量删除。
 * - `detailVisible` / `detailRecord`：只读详情弹窗显隐与详情数据。
 * 关键依赖：
 * - 复用公共筛选面板、公共表格组件，businessType / deviceType / status 走字典系统渲染标签与下拉。
 * - 失败行（status=1 且存在 errorLogId）展示「错误日志」行操作，点击跳转错误日志详情。
 */
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElButton, ElMessage, ElMessageBox } from 'element-plus'
import {
  cleanSysOperLogApi,
  deleteSysOperLogApi,
  getSysOperLogDetailApi,
  getSysOperLogPageApi,
} from '@/api/system/log'
import BaseCard from '@/components/BaseCard.vue'
import DictTag from '@/components/DictTag.vue'
import SearchFilterPanel from '@/components/SearchFilterPanel.vue'
import SharedTablePanel from '@/components/SharedTablePanel.vue'
import { useDict } from '@/composables/useDict'
import { usePermission } from '@/composables/usePermission'
import { SYSTEM_PERMISSION_KEYS } from '@/constants/systemPermissions'
import type { NormalizedPageResult } from '@/types/api/system/common'
import type { SysOperLogListQuery, SysOperLogPageResult, SysOperLogRecord } from '@/types/api/system/log'
import type { SharedActionConfig, SharedFieldSchemaMap } from '@/types/components/data-display'
import { formatDateTime } from '@/utils/format'
import {
  createDefaultOperLogQuery,
  createOperLogQuerySchema,
  createOperLogSchema,
  type OperLogQueryFormState,
} from './config'
import LogCodeBlock from '../components/LogCodeBlock.vue'

const router = useRouter()
const { ensureDictLoaded } = useDict()
const { hasPermission } = usePermission()

const queryForm = reactive<OperLogQueryFormState>(createDefaultOperLogQuery())
const listLoading = ref(false)
const detailLoading = ref(false)
const detailVisible = ref(false)
const detailRecord = ref<SysOperLogRecord | null>(null)
const selectedRows = ref<Record<string, unknown>[]>([])
const pageState = ref<SysOperLogPageResult>({
  records: [],
  pageNum: 1,
  pageSize: 10,
  pages: 0,
  total: 0,
})

const querySchema = computed<SharedFieldSchemaMap<OperLogQueryFormState>>(() => createOperLogQuerySchema())
const tableSchema = computed<SharedFieldSchemaMap<SysOperLogRecord>>(() => createOperLogSchema())
const tablePagination = computed<NormalizedPageResult<Record<string, unknown>>>(() => ({
  records: pageState.value.records,
  pageNum: pageState.value.pageNum,
  pageSize: pageState.value.pageSize,
  pages: pageState.value.pages,
  total: pageState.value.total,
}))
// 操作日志为只读业务，公共表格的内置弹窗表单不使用，关闭其 form 弹窗能力
const formVisible = ref(false)
const formModel = ref<Record<string, unknown>>({})
const canBatchDelete = computed(() => selectedRows.value.length > 0 && hasPermission(SYSTEM_PERMISSION_KEYS.operLog.delete))

const tableActions = computed<SharedActionConfig<Record<string, unknown>>[]>(() => [
  {
    key: 'detail',
    label: '详情',
    buttonType: 'primary',
    onClick: async (row) => {
      await openDetailDialog(Number(row.operId))
    },
  },
  {
    key: 'error-log',
    label: '错误日志',
    buttonType: 'danger',
    // 仅失败操作且后端已关联错误日志时才展示「错误日志」按钮，供用户跳转查看
    visible: (row) => Number(row.status) === 1 && Boolean(row.errorLogId),
    onClick: async (row) => {
      await jumpToErrorLog(Number(row.errorLogId))
    },
  },
  {
    key: 'delete',
    label: '删除',
    permKey: SYSTEM_PERMISSION_KEYS.operLog.delete,
    buttonType: 'danger',
    onClick: async (row) => {
      await handleBatchDelete([Number(row.operId)], '删除操作日志')
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
  queryForm.title = String(nextValue.title ?? '')
  queryForm.businessType = nextValue.businessType ? String(nextValue.businessType) : undefined
  queryForm.operName = String(nextValue.operName ?? '')
  queryForm.status =
    nextValue.status === undefined || nextValue.status === null || nextValue.status === ''
      ? undefined
      : Number(nextValue.status)
  queryForm.requestMethod = nextValue.requestMethod ? String(nextValue.requestMethod) : undefined
  queryForm.dateRange = Array.isArray(nextValue.dateRange)
    ? nextValue.dateRange.map((item) => String(item))
    : []
}

const buildListParams = (): SysOperLogListQuery => {
  const [beginTime, endTime] = queryForm.dateRange

  return {
    pageNum: pageState.value.pageNum,
    pageSize: pageState.value.pageSize,
    title: queryForm.title.trim() || undefined,
    businessType: queryForm.businessType,
    operName: queryForm.operName.trim() || undefined,
    status: queryForm.status,
    requestMethod: queryForm.requestMethod,
    beginTime,
    endTime,
  }
}

/**
 * 方法效果：
 * 拉取操作日志分页列表，并更新当前表格与分页状态。
 * 参数：
 * - 无，直接使用当前页的查询条件和分页参数。
 * 返回值：
 * - 无返回值；副作用是刷新表格数据和分页信息。
 */
const fetchPage = async () => {
  listLoading.value = true

  try {
    pageState.value = await getSysOperLogPageApi(buildListParams())
  } finally {
    listLoading.value = false
  }
}

/**
 * 方法效果：
 * 执行操作日志查询，并从第一页重新拉取列表。
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
 * 重置操作日志查询条件，并恢复初始分页后重新查询。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是清空筛选条件并刷新列表。
 */
const handleResetQuery = async () => {
  Object.assign(queryForm, createDefaultOperLogQuery())
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
 * - 无返回值；副作用是刷新操作日志列表。
 */
const handlePaginationChange = async (payload: { pageNum: number; pageSize: number }) => {
  pageState.value.pageNum = payload.pageNum
  pageState.value.pageSize = payload.pageSize
  await fetchPage()
}

/**
 * 方法效果：
 * 接收表格多选结果并同步到本地状态，驱动批量删除按钮可用性。
 * 参数：
 * - `rows`：当前选中的数据行数组。
 * 返回值：
 * - 无返回值；副作用是更新选中行状态。
 */
const handleSelectionChange = (rows: Record<string, unknown>[]) => {
  selectedRows.value = rows
}

/**
 * 方法效果：
 * 打开只读详情弹窗，拉取操作日志详情后展示请求参数、响应结果等长内容。
 * 参数：
 * - `operId`：待查看操作日志主键。
 * 返回值：
 * - 无返回值；副作用是更新详情数据并展示弹窗。
 */
const openDetailDialog = async (operId: number) => {
  detailLoading.value = true
  try {
    const result = await getSysOperLogDetailApi(operId)
    detailRecord.value = result.data
    detailVisible.value = true
  } finally {
    detailLoading.value = false
  }
}

/**
 * 方法效果：
 * 跳转到错误日志页面，并通过 query 携带 errorId 让目标页自动打开错误日志详情。
 * 参数：
 * - `errorLogId`：关联的错误日志主键。
 * 返回值：
 * - 无返回值；副作用是触发路由跳转。
 */
const jumpToErrorLog = async (errorLogId: number) => {
  await router.push({
    path: '/system/log/error-log',
    query: { errorId: String(errorLogId) },
  })
}

/**
 * 方法效果：
 * 批量删除操作日志（物理删除，不可恢复），并在删除成功后回退分页与刷新列表。
 * 参数：
 * - `operIds`：待删除操作日志主键数组。
 * - `action`：操作场景文案，用于确认弹窗标题。
 * 返回值：
 * - 无返回值；副作用是调用删除接口并刷新列表。
 */
const handleBatchDelete = async (operIds: number[], action: string) => {
  await ElMessageBox.confirm(
    `当前将删除 ${operIds.length} 条操作日志，删除后不可恢复，确认继续吗？`,
    action,
    { type: 'warning' },
  )

  await deleteSysOperLogApi(operIds)
  ElMessage.success('操作日志删除成功')

  // 删除当前页最后一批数据时回退到上一页，避免停留在空页
  if (operIds.length >= pageState.value.records.length && pageState.value.pageNum > 1) {
    pageState.value.pageNum -= 1
  }

  selectedRows.value = []
  await fetchPage()
}

/**
 * 方法效果：
 * 处理工具栏批量删除按钮点击，删除当前选中的操作日志。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是调用批量删除并刷新列表。
 */
const handleToolbarBatchDelete = async () => {
  const operIds = selectedRows.value.map((row) => Number(row.operId))
  if (operIds.length === 0) {
    return
  }
  await handleBatchDelete(operIds, '批量删除操作日志')
}

/**
 * 方法效果：
 * 清空全部操作日志（TRUNCATE，不可恢复），清空后回到第一页刷新。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是清空全部数据并刷新列表。
 */
const handleClean = async () => {
  await ElMessageBox.confirm(
    '清空将删除全部操作日志且不可恢复，确认继续吗？',
    '清空操作日志',
    { type: 'warning' },
  )

  await cleanSysOperLogApi()
  ElMessage.success('操作日志已清空')
  selectedRows.value = []
  pageState.value.pageNum = 1
  await fetchPage()
}

onMounted(async () => {
  // 确保日志相关字典已加载，避免刷新或首次进入时标签/下拉闪空
  await Promise.all([
    ensureDictLoaded('sys_oper_business_type'),
    ensureDictLoaded('sys_oper_device_type'),
    ensureDictLoaded('sys_oper_status'),
  ])
  await fetchPage()
})
</script>

<template>
  <section class="oper-log-view">
    <BaseCard>
      <SearchFilterPanel
        :schema="querySchema"
        :model-value="queryForm as unknown as Record<string, unknown>"
        :columns="6"
        label-width="84px"
        :show-create-button="false"
        @update:model-value="handleQueryFormUpdate"
        @search="handleSearch"
        @reset="handleResetQuery"
      />
    </BaseCard>

    <BaseCard title="操作日志列表">
      <!-- 工具栏：批量删除 / 清空，按权限点控制可用性 -->
      <div class="oper-log-view__toolbar">
        <ElButton
          type="danger"
          plain
          :disabled="!canBatchDelete"
          @click="handleToolbarBatchDelete"
        >
          批量删除
        </ElButton>
        <ElButton
          v-if="hasPermission(SYSTEM_PERMISSION_KEYS.operLog.clean)"
          type="danger"
          @click="handleClean"
        >
          清空日志
        </ElButton>
      </div>

      <SharedTablePanel
        :rows="pageState.records as Record<string, unknown>[]"
        :schema="tableSchema"
        :actions="tableActions"
        :loading="listLoading"
        :show-selection="true"
        :pagination="tablePagination"
        :table-max-height="560"
        :form-visible="formVisible"
        :form-model-value="formModel"
        row-key="operId"
        @pagination-change="handlePaginationChange"
        @selection-change="handleSelectionChange"
      >
        <template #field-status="{ modelValue }">
          <DictTag dict-key="sys_oper_status" :value="modelValue" />
        </template>
      </SharedTablePanel>
    </BaseCard>

    <!-- 操作日志只读详情弹窗 -->
    <ElDialog
      v-model="detailVisible"
      width="760px"
      top="40px"
      destroy-on-close
      class="log-detail-dialog oper-log-view__detail-dialog"
    >
      <template #header>
        <!-- 标题栏：模块标题 + 业务类型 / 状态徽标聚合，强调第一眼识别 -->
        <div class="log-detail-dialog__header">
          <span class="log-detail-dialog__title">操作日志详情</span>
          <DictTag
            v-if="detailRecord"
            dict-key="sys_oper_business_type"
            :value="detailRecord.businessType"
          />
          <DictTag
            v-if="detailRecord"
            dict-key="sys_oper_status"
            :value="detailRecord.status"
          />
        </div>
      </template>

      <div v-loading="detailLoading" class="log-detail-dialog__body">
        <template v-if="detailRecord">
          <!-- 基础信息：连贯定义表，标签列固定宽度、单元格共享细边线，避免碎片化 -->
          <dl class="log-detail-dialog__kv">
            <div class="log-detail-dialog__kv-row">
              <dt>模块标题</dt>
              <dd>{{ detailRecord.title || '--' }}</dd>
              <dt>业务类型</dt>
              <dd>
                <DictTag dict-key="sys_oper_business_type" :value="detailRecord.businessType" />
              </dd>
            </div>
            <div class="log-detail-dialog__kv-row">
              <dt>日志编号</dt>
              <dd>{{ detailRecord.operId }}</dd>
              <dt>状态</dt>
              <dd>
                <DictTag dict-key="sys_oper_status" :value="detailRecord.status" />
              </dd>
            </div>
            <div class="log-detail-dialog__kv-row">
              <dt>请求方式</dt>
              <dd>{{ detailRecord.requestMethod || '--' }}</dd>
              <dt>耗时</dt>
              <dd>{{ detailRecord.costTime }} ms</dd>
            </div>
            <div class="log-detail-dialog__kv-row">
              <dt>操作人员</dt>
              <dd>{{ detailRecord.operName || '--' }}</dd>
              <dt>操作 IP</dt>
              <dd>{{ detailRecord.operIp || '--' }}</dd>
            </div>
            <div class="log-detail-dialog__kv-row">
              <dt>设备类型</dt>
              <dd>
                <DictTag dict-key="sys_oper_device_type" :value="detailRecord.deviceType" />
              </dd>
              <dt>操作系统 / 浏览器</dt>
              <dd>{{ detailRecord.operOs || '--' }} / {{ detailRecord.operBrowser || '--' }}</dd>
            </div>
            <div class="log-detail-dialog__kv-row log-detail-dialog__kv-row--single">
              <dt>操作时间</dt>
              <dd>{{ formatDateTime(detailRecord.operTime) }}</dd>
            </div>
            <div class="log-detail-dialog__kv-row log-detail-dialog__kv-row--single">
              <dt>请求地址</dt>
              <dd class="is-mono">{{ detailRecord.operUrl || '--' }}</dd>
            </div>
            <div class="log-detail-dialog__kv-row log-detail-dialog__kv-row--single">
              <dt>执行方法</dt>
              <dd class="is-mono">{{ detailRecord.method || '--' }}</dd>
            </div>
          </dl>

          <!-- 失败操作给出关联错误日志的快捷入口 -->
          <div v-if="Number(detailRecord.status) === 1 && detailRecord.errorLogId" class="log-detail-dialog__related">
            <div class="log-detail-dialog__related-box log-detail-dialog__related-box--error">
              <div class="log-detail-dialog__related-info">
                <span class="log-detail-dialog__related-label">该操作执行失败</span>
                <span class="log-detail-dialog__related-desc">已记录关联错误日志，可查看对应堆栈定位问题</span>
              </div>
              <ElButton plain @click="jumpToErrorLog(Number(detailRecord.errorLogId))">
                查看错误日志
              </ElButton>
            </div>
          </div>

          <!-- 长文本区块：请求参数 / 返回结果 -->
          <div class="log-detail-dialog__code-section">
            <LogCodeBlock title="请求参数" :content="detailRecord.operParam" max-height="220px" />
            <LogCodeBlock title="返回结果" :content="detailRecord.jsonResult" max-height="220px" />
          </div>
        </template>
      </div>
    </ElDialog>
  </section>
</template>

<style scoped>
.oper-log-view {
  display: grid;
  gap: 18px;
}

.oper-log-view__toolbar {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-bottom: 14px;
}
</style>

<!--
  详情弹窗滚动 + 头部聚合样式用非 scoped 全局块：
  ElDialog teleport 到 body，scoped 的 data-v 锚点不在 teleported 子树内，
  scoped 选择器匹配不到 .el-dialog；改用全局样式稳定命中。
-->
<style>
.log-detail-dialog.el-dialog {
  display: flex;
  flex-direction: column;
  max-height: calc(100vh - 80px);
}

.log-detail-dialog .el-dialog__header {
  flex: none;
  margin: 0;
  padding: 16px 20px;
  border-bottom: 1px solid var(--rookie-border);
}

.log-detail-dialog .el-dialog__body {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
}

.log-detail-dialog__header {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.log-detail-dialog__title {
  font-size: var(--rookie-font-size-lg);
  font-weight: 600;
  color: var(--rookie-text);
}

.log-detail-dialog__body {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 4px 4px 8px;
}

/* 连贯定义表：dt/dd 共享边线，外层一个圆角容器统一收边，避免每格自背盒子的碎片感 */
.log-detail-dialog__kv {
  margin: 0;
  border: 1px solid var(--rookie-border);
  border-radius: var(--rookie-radius-sm);
  overflow: hidden;
  background: var(--rookie-surface);
}

/* 默认两列对：标签 140px + 值弹性，左右各一对 */
.log-detail-dialog__kv-row {
  display: grid;
  grid-template-columns: 140px 1fr 140px 1fr;
  align-items: stretch;
}

/* 单列行：标签 140px + 值跨满剩余三栏 */
.log-detail-dialog__kv-row--single {
  grid-template-columns: 140px 1fr;
}

.log-detail-dialog__kv-row--single > dd {
  grid-column: 2 / span 3;
}

/* 每行之间一条细分割线 */
.log-detail-dialog__kv-row + .log-detail-dialog__kv-row {
  border-top: 1px solid var(--rookie-border);
}

.log-detail-dialog__kv dt {
  padding: 10px 14px;
  background: var(--rookie-surface-muted);
  color: var(--rookie-text-tertiary);
  font-size: var(--rookie-font-size-sm);
  font-weight: 500;
  border-right: 1px solid var(--rookie-border);
  display: flex;
  align-items: center;
}

.log-detail-dialog__kv dd {
  margin: 0;
  padding: 10px 16px;
  color: var(--rookie-text);
  font-size: var(--rookie-font-size-md);
  word-break: break-all;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

/* 一行只有一对 dt/dd 的右栏要跨满；这里用 grid-column 显式跨列 */
.log-detail-dialog__kv-row:has(> dd:only-child) > dd {
  grid-column: 2 / span 3;
}

.log-detail-dialog__kv dd.is-mono {
  font-family: 'Fira Code', 'Courier New', Courier, monospace;
  font-size: var(--rookie-font-size-sm);
  white-space: nowrap;
  overflow-x: auto;
}

@media (max-width: 640px) {
  /* 窄屏退化为单列：标签上、值下，避免横向拥挤 */
  .log-detail-dialog__kv-row,
  .log-detail-dialog__kv-row--single {
    grid-template-columns: 1fr;
  }

  .log-detail-dialog__kv dt {
    border-right: none;
    border-bottom: 1px solid var(--rookie-border);
  }

  .log-detail-dialog__kv-row > dd,
  .log-detail-dialog__kv-row--single > dd {
    grid-column: auto;
  }
}

.log-detail-dialog__related {
  display: flex;
}

.log-detail-dialog__related-box {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  padding: 12px 16px;
  border: 1px solid var(--rookie-border);
  border-left: 3px solid var(--rookie-primary);
  border-radius: var(--rookie-radius-sm);
  background: var(--rookie-surface-muted);
}

.log-detail-dialog__related-box--error {
  border-left-color: var(--rookie-danger);
}

.log-detail-dialog__related-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.log-detail-dialog__related-label {
  font-size: var(--rookie-font-size-sm);
  font-weight: 600;
  color: var(--rookie-text);
}

.log-detail-dialog__related-desc {
  font-size: var(--rookie-font-size-xs);
  color: var(--rookie-text-tertiary);
}

.log-detail-dialog__code-section {
  display: grid;
  gap: 14px;
}
</style>