/**
 * 文件作用：
 * 承接日志管理模块下的错误日志页面，
 * 负责错误日志列表查询、分页展示、只读详情查看（含完整堆栈）、批量删除和清空。
 * 关键状态：
 * - `queryForm`：当前筛选条件对象。
 * - `pageState`：列表分页状态。
 * - `selectedRows`：表格多选结果，驱动批量删除。
 * - `detailVisible` / `detailRecord`：只读详情弹窗显隐与详情数据。
 * 关键依赖：
 * - 复用公共筛选面板、公共表格组件，sourceType 走字典系统渲染标签与下拉。
 * - 支持从操作日志页通过 query 携带 errorId 跳入并自动打开错误日志详情，
 *   请求来源的错误日志（存在 operLogId）详情弹窗内提供「查看操作日志」反向跳转。
 */
<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElButton, ElMessage, ElMessageBox } from 'element-plus'
import {
  cleanSysErrorLogApi,
  deleteSysErrorLogApi,
  getSysErrorLogDetailApi,
  getSysErrorLogPageApi,
} from '@/api/system/log'
import BaseCard from '@/components/BaseCard.vue'
import DictTag from '@/components/DictTag.vue'
import SearchFilterPanel from '@/components/SearchFilterPanel.vue'
import SharedTablePanel from '@/components/SharedTablePanel.vue'
import { useDict } from '@/composables/useDict'
import { usePermission } from '@/composables/usePermission'
import { SYSTEM_PERMISSION_KEYS } from '@/constants/systemPermissions'
import type { NormalizedPageResult } from '@/types/api/system/common'
import type { SysErrorLogListQuery, SysErrorLogPageResult, SysErrorLogRecord } from '@/types/api/system/log'
import type { SharedActionConfig, SharedFieldSchemaMap } from '@/types/components/data-display'
import { formatDateTime } from '@/utils/format'
import {
  createDefaultErrorLogQuery,
  createErrorLogQuerySchema,
  createErrorLogSchema,
  type ErrorLogQueryFormState,
} from './config'
import LogCodeBlock from '../components/LogCodeBlock.vue'

const route = useRoute()
const router = useRouter()
const { ensureDictLoaded } = useDict()
const { hasPermission } = usePermission()

const queryForm = reactive<ErrorLogQueryFormState>(createDefaultErrorLogQuery())
const listLoading = ref(false)
const detailLoading = ref(false)
const detailVisible = ref(false)
const detailRecord = ref<SysErrorLogRecord | null>(null)
const selectedRows = ref<Record<string, unknown>[]>([])
const pageState = ref<SysErrorLogPageResult>({
  records: [],
  pageNum: 1,
  pageSize: 10,
  pages: 0,
  total: 0,
})

const querySchema = computed<SharedFieldSchemaMap<ErrorLogQueryFormState>>(() => createErrorLogQuerySchema())
const tableSchema = computed<SharedFieldSchemaMap<SysErrorLogRecord>>(() => createErrorLogSchema())
const tablePagination = computed<NormalizedPageResult<Record<string, unknown>>>(() => ({
  records: pageState.value.records,
  pageNum: pageState.value.pageNum,
  pageSize: pageState.value.pageSize,
  pages: pageState.value.pages,
  total: pageState.value.total,
}))
// 错误日志为只读业务，公共表格的内置弹窗表单不使用，关闭其 form 弹窗能力
const formVisible = ref(false)
const formModel = ref<Record<string, unknown>>({})
const canBatchDelete = computed(
  () => selectedRows.value.length > 0 && hasPermission(SYSTEM_PERMISSION_KEYS.errorLog.delete),
)

const tableActions = computed<SharedActionConfig<Record<string, unknown>>[]>(() => [
  {
    key: 'detail',
    label: '详情',
    buttonType: 'primary',
    onClick: async (row) => {
      await openDetailDialog(Number(row.errorId))
    },
  },
  {
    key: 'delete',
    label: '删除',
    permKey: SYSTEM_PERMISSION_KEYS.errorLog.delete,
    buttonType: 'danger',
    onClick: async (row) => {
      await handleBatchDelete([Number(row.errorId)], '删除错误日志')
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
  queryForm.sourceType = nextValue.sourceType ? String(nextValue.sourceType) : undefined
  queryForm.title = String(nextValue.title ?? '')
  queryForm.operName = String(nextValue.operName ?? '')
  queryForm.exceptionType = String(nextValue.exceptionType ?? '')
  queryForm.dateRange = Array.isArray(nextValue.dateRange)
    ? nextValue.dateRange.map((item) => String(item))
    : []
}

const buildListParams = (): SysErrorLogListQuery => {
  const [beginTime, endTime] = queryForm.dateRange

  return {
    pageNum: pageState.value.pageNum,
    pageSize: pageState.value.pageSize,
    sourceType: queryForm.sourceType,
    title: queryForm.title.trim() || undefined,
    operName: queryForm.operName.trim() || undefined,
    exceptionType: queryForm.exceptionType.trim() || undefined,
    beginTime,
    endTime,
  }
}

/**
 * 方法效果：
 * 拉取错误日志分页列表，并更新当前表格与分页状态。
 * 参数：
 * - 无，直接使用当前页的查询条件和分页参数。
 * 返回值：
 * - 无返回值；副作用是刷新表格数据和分页信息。
 */
const fetchPage = async () => {
  listLoading.value = true

  try {
    pageState.value = await getSysErrorLogPageApi(buildListParams())
  } finally {
    listLoading.value = false
  }
}

/**
 * 方法效果：
 * 执行错误日志查询，并从第一页重新拉取列表。
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
 * 重置错误日志查询条件，并恢复初始分页后重新查询。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是清空筛选条件并刷新列表。
 */
const handleResetQuery = async () => {
  Object.assign(queryForm, createDefaultErrorLogQuery())
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
 * - 无返回值；副作用是刷新错误日志列表。
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
 * 打开只读详情弹窗，拉取错误日志详情后展示异常消息与完整堆栈。
 * 参数：
 * - `errorId`：待查看错误日志主键。
 * 返回值：
 * - 无返回值；副作用是更新详情数据并展示弹窗。
 */
const openDetailDialog = async (errorId: number) => {
  detailLoading.value = true
  try {
    const result = await getSysErrorLogDetailApi(errorId)
    detailRecord.value = result.data
    detailVisible.value = true
  } finally {
    detailLoading.value = false
  }
}

/**
 * 方法效果：
 * 跳转回操作日志页面，并通过 query 携带 operId 让目标页自动打开操作日志详情。
 * 参数：
 * - `operLogId`：关联的操作日志主键。
 * 返回值：
 * - 无返回值；副作用是触发路由跳转。
 */
const jumpToOperLog = async (operLogId: number) => {
  await router.push({
    path: '/system/log/oper-log',
    query: { operId: String(operLogId) },
  })
}

/**
 * 方法效果：
 * 批量删除错误日志（物理删除，不可恢复），并在删除成功后回退分页与刷新列表。
 * 参数：
 * - `errorIds`：待删除错误日志主键数组。
 * - `action`：操作场景文案，用于确认弹窗标题。
 * 返回值：
 * - 无返回值；副作用是调用删除接口并刷新列表。
 */
const handleBatchDelete = async (errorIds: number[], action: string) => {
  await ElMessageBox.confirm(
    `当前将删除 ${errorIds.length} 条错误日志，删除后不可恢复，确认继续吗？`,
    action,
    { type: 'warning' },
  )

  await deleteSysErrorLogApi(errorIds)
  ElMessage.success('错误日志删除成功')

  // 删除当前页最后一批数据时回退到上一页，避免停留在空页
  if (errorIds.length >= pageState.value.records.length && pageState.value.pageNum > 1) {
    pageState.value.pageNum -= 1
  }

  selectedRows.value = []
  await fetchPage()
}

/**
 * 方法效果：
 * 处理工具栏批量删除按钮点击，删除当前选中的错误日志。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是调用批量删除并刷新列表。
 */
const handleToolbarBatchDelete = async () => {
  const errorIds = selectedRows.value.map((row) => Number(row.errorId))
  if (errorIds.length === 0) {
    return
  }
  await handleBatchDelete(errorIds, '批量删除错误日志')
}

/**
 * 方法效果：
 * 清空全部错误日志（TRUNCATE，不可恢复），清空后回到第一页刷新。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是清空全部数据并刷新列表。
 */
const handleClean = async () => {
  await ElMessageBox.confirm(
    '清空将删除全部错误日志且不可恢复，确认继续吗？',
    '清空错误日志',
    { type: 'warning' },
  )

  await cleanSysErrorLogApi()
  ElMessage.success('错误日志已清空')
  selectedRows.value = []
  pageState.value.pageNum = 1
  await fetchPage()
}

/**
 * 方法效果：
 * 处理从操作日志页通过 query 携带 errorId 跳入的场景，
 * 列表拉取完成后自动打开对应错误日志详情。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是解析路由 query 并打开详情弹窗。
 */
const applyRouteErrorId = async () => {
  const errorId = Number(route.query.errorId)

  if (!Number.isNaN(errorId) && errorId > 0) {
    await openDetailDialog(errorId)
  }
}

onMounted(async () => {
  // 确保错误来源字典已加载，避免刷新或首次进入时标签/下拉闪空
  await ensureDictLoaded('sys_error_source_type')
  await fetchPage()
  await applyRouteErrorId()
})

watch(
  () => route.query.errorId,
  async (nextErrorId) => {
    const errorId = Number(nextErrorId)
    // query 变化但列表仍在当前页时，自动打开对应详情；清空 query 时不重复触发
    if (!Number.isNaN(errorId) && errorId > 0 && !detailVisible.value) {
      await openDetailDialog(errorId)
    }
  },
)
</script>

<template>
  <section class="error-log-view">
    <BaseCard>
      <SearchFilterPanel
        :schema="querySchema"
        :model-value="queryForm as unknown as Record<string, unknown>"
        :columns="5"
        label-width="84px"
        :show-create-button="false"
        @update:model-value="handleQueryFormUpdate"
        @search="handleSearch"
        @reset="handleResetQuery"
      />
    </BaseCard>

    <BaseCard title="错误日志列表">
      <!-- 工具栏：批量删除 / 清空，按权限点控制可用性 -->
      <div class="error-log-view__toolbar">
        <ElButton
          type="danger"
          plain
          :disabled="!canBatchDelete"
          @click="handleToolbarBatchDelete"
        >
          批量删除
        </ElButton>
        <ElButton
          v-if="hasPermission(SYSTEM_PERMISSION_KEYS.errorLog.clean)"
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
        row-key="errorId"
        @pagination-change="handlePaginationChange"
        @selection-change="handleSelectionChange"
      >
        <template #field-sourceType="{ modelValue }">
          <DictTag dict-key="sys_error_source_type" :value="modelValue" />
        </template>
      </SharedTablePanel>
    </BaseCard>

    <!-- 错误日志只读详情弹窗（含完整堆栈） -->
    <ElDialog
      v-model="detailVisible"
      width="860px"
      top="40px"
      destroy-on-close
      class="log-detail-dialog error-log-view__detail-dialog"
    >
      <template #header>
        <!-- 标题栏：标题 + 错误来源徽标聚合，强调第一眼识别 -->
        <div class="log-detail-dialog__header">
          <span class="log-detail-dialog__title">错误日志详情</span>
          <DictTag
            v-if="detailRecord"
            dict-key="sys_error_source_type"
            :value="detailRecord.sourceType"
          />
        </div>
      </template>

      <div v-loading="detailLoading" class="log-detail-dialog__body">
        <template v-if="detailRecord">
          <!-- 基础信息：连贯定义表，标签列固定宽度、单元格共享细边线，避免碎片化 -->
          <dl class="log-detail-dialog__kv">
            <div class="log-detail-dialog__kv-row">
              <dt>日志编号</dt>
              <dd>{{ detailRecord.errorId }}</dd>
              <dt>错误时间</dt>
              <dd>{{ formatDateTime(detailRecord.errorTime) }}</dd>
            </div>
            <div class="log-detail-dialog__kv-row">
              <dt>错误来源</dt>
              <dd>
                <DictTag dict-key="sys_error_source_type" :value="detailRecord.sourceType" />
              </dd>
              <dt>操作人员</dt>
              <dd>{{ detailRecord.operName || '--' }}</dd>
            </div>
            <div class="log-detail-dialog__kv-row log-detail-dialog__kv-row--single">
              <dt>错误简述</dt>
              <dd>{{ detailRecord.title || '--' }}</dd>
            </div>
            <div class="log-detail-dialog__kv-row log-detail-dialog__kv-row--single">
              <dt>异常类型</dt>
              <dd class="is-mono">{{ detailRecord.exceptionType || '--' }}</dd>
            </div>
            <div class="log-detail-dialog__kv-row log-detail-dialog__kv-row--single">
              <dt>异常消息</dt>
              <dd>{{ detailRecord.exceptionMsg || '--' }}</dd>
            </div>
          </dl>

          <!-- 请求来源错误提供关联操作日志的快捷入口 -->
          <div v-if="detailRecord.operLogId" class="log-detail-dialog__related">
            <div class="log-detail-dialog__related-box">
              <div class="log-detail-dialog__related-info">
                <span class="log-detail-dialog__related-label">该错误来源于一次请求操作</span>
                <span class="log-detail-dialog__related-desc">可查看关联操作日志，比对请求参数与执行上下文</span>
              </div>
              <ElButton plain @click="jumpToOperLog(Number(detailRecord.operLogId))">
                查看操作日志
              </ElButton>
            </div>
          </div>

          <!-- 完整堆栈：限高滚动 + 一键复制 -->
          <LogCodeBlock title="完整堆栈" :content="detailRecord.exceptionStack" max-height="360px" />
        </template>
      </div>
    </ElDialog>
  </section>
</template>

<style scoped>
.error-log-view {
  display: grid;
  gap: 18px;
}

.error-log-view__toolbar {
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
  与操作日志详情弹窗共用 .log-detail-dialog 体系，避免重复定义。
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
</style>