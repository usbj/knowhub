<!--
  KhPagination —— 前台统一分页器
  ------------------------------------------------------------------
  包装 ElPagination，固定布局 total(+页大小下拉) + 页码 + 跳页框，
  统一各列表页分页外观（共 N 条 · 每页 X 条 ▾ · 页码 · 跳页）。
  用法：
    <KhPagination v-model:current="pageNum" v-model:page-size="pageSize"
                  :total="total" @change="onPageChange" />
    const onPageChange = (p, sz) => { pageNum.value = p; pageSize.value = sz; void fetchList() }
    —— 切每页条数时组件已把页码置 1 抛回（@change 第二参数为最新 pageSize），
       父层只需把 pageNum=1 赋值（或直接用回调的 p=1）后重拉。
  v-model:current / v-model:page-size 双向绑定，隐藏的 update 事件由组件内部抛。
-->
<script setup lang="ts">
import { ElPagination } from 'element-plus'

const props = withDefaults(
  defineProps<{
    /** 总条数（后端 PageInfo.total） */
    total: number
    /** 当前页（v-model:current） */
    current: number
    /** 每页条数（v-model:page-size） */
    pageSize: number
    /** 每页条数候选，默认 [9,12,20,50]；各页可传覆盖（如资源页 [12,24,36]） */
    pageSizes?: number[]
  }>(),
  {
    pageSizes: () => [9, 12, 20, 50],
  },
)

const emit = defineEmits<{
  /** v-model:current 同步 */
  'update:current': [v: number]
  /** v-model:page-size 同步 */
  'update:page-size': [v: number]
  /**
   * 翻页或切每页条数时统一抛给父层重拉：
   * - current-change：pageNum=新页, pageSize=当前
   * - size-change：pageNum=1（已回首页）, pageSize=新尺寸
   */
  change: [pageNum: number, pageSize: number]
}>()

const onCurrentChange = (p: number) => {
  emit('update:current', p)
  emit('change', p, props.pageSize)
}

const onSizeChange = (s: number) => {
  emit('update:page-size', s)
  emit('change', 1, s)
}
</script>

<template>
  <div class="kh-pager">
    <ElPagination
      :current-page="current"
      :page-size="pageSize"
      :total="total"
      :page-sizes="pageSizes"
      layout="total, sizes, prev, pager, next, jumper"
      background
      @current-change="onCurrentChange"
      @size-change="onSizeChange"
    />
  </div>
</template>

<style scoped>
.kh-pager {
  display: flex;
  justify-content: center;
}
</style>