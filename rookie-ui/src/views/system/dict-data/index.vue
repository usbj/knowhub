/**
 * 文件作用：
 * 承接系统模块下的字典数据管理页面，
 * 负责字典数据列表查询、分页展示、新增、编辑和删除操作。
 * 关键状态：
 * - `dictOptions` / `dictMap`：字典选项及字典主键映射，供筛选和编辑表单复用。
 * - `queryForm` / `formModel`：当前查询条件与弹窗表单模型。
 * - `pageState` / `dialogVisible`：列表分页状态与弹窗显隐状态。
 * 关键依赖：
 * - 支持从字典管理页携带 `dictId` 跳入并自动回填筛选。
 */
<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createSysDictDataApi,
  deleteSysDictDataApi,
  getSysDictDataDetailApi,
  getSysDictDataPageApi,
  getSysDictPageApi,
  updateSysDictDataApi,
} from '@/api/system/dict'
import BaseCard from '@/components/BaseCard.vue'
import SearchFilterPanel from '@/components/SearchFilterPanel.vue'
import SharedTablePanel from '@/components/SharedTablePanel.vue'
import { SYSTEM_PERMISSION_KEYS } from '@/constants/systemPermissions'
import type { NormalizedPageResult } from '@/types/api/system/common'
import type {
  SysDictDataListQuery,
  SysDictDataPageResult,
  SysDictDataRecord,
} from '@/types/api/system/dict'
import type { SharedActionConfig, SharedFieldSchemaMap } from '@/types/components/data-display'
import {
  createDefaultDictDataForm,
  createDefaultDictDataQuery,
  createDictDataQuerySchema,
  createDictDataSchema,
  dictDataFormRules,
  type DictDataQueryFormState,
} from './config'

type DictDataDialogMode = 'create' | 'edit'

const route = useRoute()
const dictMap = ref(new Map<number, { dictId: number; dictKey: string; dictName: string }>())
const dictOptions = ref<Array<{ label: string; value: number }>>([])
const queryForm = reactive<DictDataQueryFormState>(createDefaultDictDataQuery())
const listLoading = ref(false)
const submitLoading = ref(false)
const dialogVisible = ref(false)
const dialogMode = ref<DictDataDialogMode>('create')
const formModel = ref<SysDictDataRecord>(createDefaultDictDataForm())
const pageState = ref<SysDictDataPageResult>({
  records: [],
  pageNum: 1,
  pageSize: 10,
  pages: 0,
  total: 0,
})

const querySchema = computed<SharedFieldSchemaMap<DictDataQueryFormState>>(() =>
  createDictDataQuerySchema(dictOptions.value),
)
const tableSchema = computed<SharedFieldSchemaMap<SysDictDataRecord>>(() =>
  createDictDataSchema(dictOptions.value),
)
const dialogTitle = computed(() => (dialogMode.value === 'create' ? '新增字典数据' : '编辑字典数据'))
const dialogSubmitText = computed(() => (dialogMode.value === 'create' ? '创建数据' : '保存修改'))
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
    permKey: SYSTEM_PERMISSION_KEYS.dict.edit,
    buttonType: 'primary',
    onClick: async (row) => {
      await openEditDialog(Number(row.dictDataId))
    },
  },
  {
    key: 'delete',
    label: '删除',
    permKey: SYSTEM_PERMISSION_KEYS.dict.delete,
    buttonType: 'danger',
    onClick: async (row) => {
      await handleDeleteDictData(Number(row.dictDataId))
    },
  },
])

/**
 * 方法效果：
 * 根据当前路由参数回填字典数据页的默认筛选条件，
 * 让从字典管理页跳转过来时能直接落到对应字典的数据列表。
 * 参数：
 * - 无，直接读取当前路由参数。
 * 返回值：
 * - 无返回值；副作用是更新查询表单状态。
 */
const applyRouteQuery = () => {
  const routeDictId = Number(route.query.dictId)

  if (!Number.isNaN(routeDictId) && routeDictId > 0) {
    queryForm.dictId = routeDictId
  }

}

/**
 * 方法效果：
 * 拉取字典选项，并同步构建字典主键到字典信息的映射表。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是更新字典下拉数据源和映射表。
 */
const fetchDictOptions = async () => {
  const result = await getSysDictPageApi({
    pageNum: 1,
    pageSize: 500,
  })

  dictOptions.value = result.records.map((item) => ({
    label: item.dictName,
    value: Number(item.dictId),
  }))
  dictMap.value = new Map(
    result.records.map((item) => [
      Number(item.dictId),
      {
        dictId: Number(item.dictId),
        dictKey: item.dictKey,
        dictName: item.dictName,
      },
    ]),
  )
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
  queryForm.dictId =
    nextValue.dictId === undefined || nextValue.dictId === null || nextValue.dictId === ''
      ? undefined
      : Number(nextValue.dictId)
  queryForm.dictDataLabel = String(nextValue.dictDataLabel ?? '')
}

const buildListParams = (): SysDictDataListQuery => ({
  pageNum: pageState.value.pageNum,
  pageSize: pageState.value.pageSize,
  dictId: queryForm.dictId,
  dictKey: queryForm.dictId ? dictMap.value.get(queryForm.dictId)?.dictKey : undefined,
  dictDataLabel: queryForm.dictDataLabel.trim() || undefined,
})

/**
 * 方法效果：
 * 拉取字典数据分页列表，并更新当前表格与分页状态。
 * 参数：
 * - 无，直接使用当前页的查询条件和分页参数。
 * 返回值：
 * - 无返回值；副作用是刷新表格数据和分页信息。
 */
const fetchPage = async () => {
  listLoading.value = true

  try {
    pageState.value = await getSysDictDataPageApi(buildListParams())
  } finally {
    listLoading.value = false
  }
}

/**
 * 方法效果：
 * 执行字典数据查询，并从第一页重新拉取列表。
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
 * 重置字典数据查询条件，并恢复初始分页后重新查询。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是清空筛选条件并刷新列表。
 */
const resetQueryForm = async () => {
  Object.assign(queryForm, createDefaultDictDataQuery())
  pageState.value.pageNum = 1
  pageState.value.pageSize = 10
  await fetchPage()
}

/**
 * 方法效果：
 * 打开新增字典数据弹窗，并尽量继承当前筛选字典作为默认值。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是切换弹窗状态并重置表单。
 */
const openCreateDialog = () => {
  dialogMode.value = 'create'
  formModel.value = {
    ...createDefaultDictDataForm(),
    dictId: queryForm.dictId,
    dictKey: queryForm.dictId ? dictMap.value.get(queryForm.dictId)?.dictKey : '',
  }
  dialogVisible.value = true
}

/**
 * 方法效果：
 * 打开编辑字典数据弹窗，并先拉取详情用于完整回显。
 * 参数：
 * - `dictDataId`：待编辑字典数据主键。
 * 返回值：
 * - 无返回值；副作用是更新表单模型并展示弹窗。
 */
const openEditDialog = async (dictDataId: number) => {
  const result = await getSysDictDataDetailApi(dictDataId)

  dialogMode.value = 'edit'
  formModel.value = {
    ...createDefaultDictDataForm(),
    ...result.data,
  }
  dialogVisible.value = true
}

/**
 * 方法效果：
 * 接收公共表单回传的新模型，并同步为当前弹窗表单状态。
 * 参数：
 * - `nextValue`：公共表单组件回传的新表单对象。
 * 返回值：
 * - 无返回值；副作用是覆盖当前弹窗表单状态。
 */
const handleFormModelUpdate = (nextValue: Record<string, unknown>) => {
  const nextDictId =
    nextValue.dictId === undefined || nextValue.dictId === null || nextValue.dictId === ''
      ? undefined
      : Number(nextValue.dictId)

  formModel.value = {
    ...formModel.value,
    ...nextValue,
    dictId: nextDictId,
    dictKey: nextDictId ? dictMap.value.get(nextDictId)?.dictKey ?? '' : '',
  }
}

/**
 * 方法效果：
 * 处理表格分页切换，并根据新的页码和每页条数重新拉取数据。
 * 参数：
 * - `payload`：分页组件回传的页码和每页条数。
 * 返回值：
 * - 无返回值；副作用是刷新字典数据列表。
 */
const handlePaginationChange = async (payload: { pageNum: number; pageSize: number }) => {
  pageState.value.pageNum = payload.pageNum
  pageState.value.pageSize = payload.pageSize
  await fetchPage()
}

/**
 * 方法效果：
 * 提交新增或编辑字典数据表单。
 * 参数：
 * - 无，直接读取当前弹窗表单模型和弹窗模式。
 * 返回值：
 * - 无返回值；副作用是调用保存接口、关闭弹窗并刷新列表。
 */
const handleSubmitForm = async () => {
  submitLoading.value = true

  try {
    const selectedDict = formModel.value.dictId ? dictMap.value.get(Number(formModel.value.dictId)) : undefined
    const payload: SysDictDataRecord = {
      ...formModel.value,
      dictId: selectedDict?.dictId ?? formModel.value.dictId,
      dictKey: selectedDict?.dictKey ?? formModel.value.dictKey,
      dictDataLabel: formModel.value.dictDataLabel.trim(),
      dictDataValue: formModel.value.dictDataValue.trim(),
      dictDataSort: formModel.value.dictDataSort.trim(),
      tagType: formModel.value.tagType?.trim() || 'info',
      tagEffect: formModel.value.tagEffect?.trim() || 'plain',
      cssClass: formModel.value.cssClass?.trim() || '',
      extJson: formModel.value.extJson?.trim() || undefined,
      remark: formModel.value.remark.trim(),
    }

    if (dialogMode.value === 'create') {
      await createSysDictDataApi(payload)
      ElMessage.success('字典数据创建成功')
    } else {
      await updateSysDictDataApi(payload)
      ElMessage.success('字典数据更新成功')
    }

    dialogVisible.value = false
    await fetchPage()
  } finally {
    submitLoading.value = false
  }
}

/**
 * 方法效果：
 * 删除指定字典数据，并在删除成功后自动处理当前分页是否需要回退。
 * 参数：
 * - `dictDataId`：待删除字典数据主键。
 * 返回值：
 * - 无返回值；副作用是调用删除接口并刷新列表。
 */
const handleDeleteDictData = async (dictDataId: number) => {
  await ElMessageBox.confirm('删除后不可恢复，确认继续吗？', '删除字典数据', {
    type: 'warning',
  })

  await deleteSysDictDataApi(dictDataId)
  ElMessage.success('字典数据删除成功')

  if (pageState.value.records.length === 1 && pageState.value.pageNum > 1) {
    pageState.value.pageNum -= 1
  }

  await fetchPage()
}

onMounted(async () => {
  await fetchDictOptions()
  applyRouteQuery()
  await fetchPage()
})

watch(
  () => route.query.dictId,
  async () => {
    applyRouteQuery()
    pageState.value.pageNum = 1
    await fetchPage()
  },
)
</script>

<template>
  <section class="system-dict-data-view">
    <BaseCard>
      <SearchFilterPanel
        :schema="querySchema"
        :model-value="queryForm as unknown as Record<string, unknown>"
        :columns="3"
        label-width="72px"
        create-button-text="新增字典数据"
        :create-permission-key="SYSTEM_PERMISSION_KEYS.dict.create"
        @update:model-value="handleQueryFormUpdate"
        @search="handleSearch"
        @reset="resetQueryForm"
        @create="openCreateDialog"
      />
    </BaseCard>

    <BaseCard title="字典数据列表">
      <SharedTablePanel
        :rows="pageState.records as Record<string, unknown>[]"
        :schema="tableSchema"
        :actions="tableActions"
        :loading="listLoading"
        :form-loading="submitLoading"
        :show-selection="true"
        :pagination="tablePagination"
        :table-max-height="520"
        :form-visible="dialogVisible"
        :form-model-value="formModel as unknown as Record<string, unknown>"
        :form-title="dialogTitle"
        :form-submit-text="dialogSubmitText"
        :form-columns="2"
        :form-rules="dictDataFormRules"
        row-key="dictDataId"
        @pagination-change="handlePaginationChange"
        @update:form-visible="dialogVisible = $event"
        @update:form-model-value="handleFormModelUpdate"
        @form-submit="handleSubmitForm"
        @form-cancel="dialogVisible = false"
      />
    </BaseCard>
  </section>
</template>

<style scoped>
.system-dict-data-view {
  display: grid;
  gap: 18px;
}
</style>
