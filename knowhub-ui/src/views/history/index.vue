<!--
  浏览历史 /history（登录）
  ------------------------------------------------------------------
  当前用户浏览历史：按 bizType 过滤 + 列表 + 删单条 + 清空 + 分页。
  数据来自后端 /history/*（authenticated 兜底）。
-->
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, DeleteFilled } from '@element-plus/icons-vue'
import KhCard from '@/components/common/KhCard.vue'
import KhIcon from '@/components/common/KhIcon.vue'
import KhPagination from '@/components/common/KhPagination.vue'
import { listHistoryApi, deleteHistoryApi, clearHistoryApi } from '@/api/knowhub/history'
import type { ViewHistoryRecord } from '@/types/api/knowhub/history'
import { formatDateTime } from '@/utils/format'

const router = useRouter()

/** 业务类型过滤 */
type BizFilter = '' | 'BLOG' | 'ARTICLE' | 'CHAPTER' | 'RESOURCE'
const bizFilter = ref<BizFilter>('')
const filterOptions: { value: BizFilter; label: string }[] = [
  { value: '', label: '全部' },
  { value: 'BLOG', label: '博客' },
  { value: 'ARTICLE', label: '文章' },
  { value: 'CHAPTER', label: '章节' },
  { value: 'RESOURCE', label: '资源' },
]

const bizLabel = (t: string) => filterOptions.find((o) => o.value === t)?.label ?? t

/** 点击历史项跳转对应详情（章节跳所属文章） */
const goDetail = (r: ViewHistoryRecord) => {
  switch (r.bizType) {
    case 'BLOG':
      router.push(`/blog/${r.bizId}`)
      break
    case 'ARTICLE':
    case 'CHAPTER':
      // 前台文章/章节详情页尚未落地，暂跳笔记导航（后续补文章详情路由）
      router.push('/blogs')
      break
    case 'RESOURCE':
      router.push(`/resource/${r.bizId}`)
      break
  }
}

const list = ref<ViewHistoryRecord[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const loading = ref(false)

const fetchList = async () => {
  loading.value = true
  try {
    const res = await listHistoryApi(bizFilter.value || undefined, {
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    list.value = (res.records ?? []).map((r) => ({
      ...r,
      viewTime: r.viewTime ? (formatDateTime(r.viewTime) as string) : r.viewTime,
      lastViewTime: r.lastViewTime ? (formatDateTime(r.lastViewTime) as string) : r.lastViewTime,
    }))
    total.value = res.total ?? 0
  } finally {
    loading.value = false
  }
}

const onPageChange = (p: number, sz: number) => {
  pageNum.value = p
  pageSize.value = sz
  void fetchList()
}

const onDelete = async (r: ViewHistoryRecord) => {
  await ElMessageBox.confirm(`确定删除「${r.title ?? '该记录'}」的浏览历史？`, '提示', {
    type: 'warning',
  }).catch(() => null)
  await deleteHistoryApi(r.viewId)
  ElMessage.success('已删除')
  void fetchList()
}

const onClear = async () => {
  await ElMessageBox.confirm('确定清空全部浏览历史？此操作不可恢复。', '清空确认', {
    type: 'warning',
  }).catch(() => null)
  await clearHistoryApi()
  ElMessage.success('已清空')
  pageNum.value = 1
  void fetchList()
}

const isEmpty = computed(() => !loading.value && list.value.length === 0)

watch(bizFilter, () => {
  pageNum.value = 1
  void fetchList()
})

onMounted(() => void fetchList())
</script>

<template>
  <div class="history">
    <section class="kh-container kh-container--wide history__head">
      <h1 class="history__title">浏览历史</h1>
      <div class="history__head-actions">
        <div class="history__filters">
          <button
            v-for="o in filterOptions"
            :key="o.value"
            class="history__filter-btn"
            :class="{ 'is-active': bizFilter === o.value }"
            type="button"
            @click="bizFilter = o.value"
          >
            {{ o.label }}
          </button>
        </div>
        <button class="history__clear" type="button" @click="onClear">
          <el-icon><DeleteFilled /></el-icon> 清空
        </button>
      </div>
    </section>

    <section class="kh-container kh-container--wide history__body">
      <div v-if="list.length" class="history__list">
        <KhCard v-for="r in list" :key="r.viewId" clickable padding="md" class="history__item" @click="goDetail(r)">
          <div class="history__item-main">
            <div class="history__item-top">
              <span class="history__biz" :class="`history__biz--${r.bizType}`">{{ bizLabel(r.bizType) }}</span>
              <h3 class="history__item-title kh-line-clamp-1">{{ r.title ?? '（内容已不可见）' }}</h3>
            </div>
            <div class="history__item-meta">
              <span v-if="r.authorName">{{ r.authorName }}</span>
              <span class="history__sep">·</span>
              <span>最近浏览 {{ r.lastViewTime }}</span>
              <span class="history__sep">·</span>
              <span>累计 {{ r.viewCount ?? 1 }} 次</span>
            </div>
          </div>
          <button class="history__del" type="button" @click.stop="onDelete(r)">
            <el-icon><Delete /></el-icon>
          </button>
        </KhCard>
      </div>
      <KhCard v-else-if="isEmpty" padding="lg" class="history__empty">
        <KhIcon name="clock" :size="40" :stroke="1.4" />
        <p>暂无浏览历史，去看看博客和资源吧</p>
      </KhCard>

      <div v-if="list.length" class="history__pager">
        <KhPagination v-model:current="pageNum" v-model:page-size="pageSize" :total="total" @change="onPageChange" />
      </div>
    </section>
  </div>
</template>

<style scoped>
.history__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kh-space-4);
  padding-top: var(--kh-space-10);
  padding-bottom: var(--kh-space-6);
  flex-wrap: wrap;
}
.history__title {
  font-size: var(--kh-font-size-4xl);
  font-weight: 700;
  letter-spacing: -0.01em;
}
.history__head-actions {
  display: flex;
  align-items: center;
  gap: var(--kh-space-4);
}
.history__filters {
  display: flex;
  gap: 4px;
  padding: 3px;
  background: var(--kh-surface-muted);
  border-radius: var(--kh-radius-pill);
}
.history__filter-btn {
  padding: 6px 14px;
  border: none;
  background: transparent;
  border-radius: var(--kh-radius-pill);
  color: var(--kh-text-secondary);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--kh-transition-fast);
}
.history__filter-btn.is-active {
  background: var(--kh-surface);
  color: var(--kh-primary);
  box-shadow: var(--kh-shadow-xs);
}
.history__clear {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 32px;
  padding: 0 var(--kh-space-3);
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius-pill);
  background: var(--kh-surface);
  color: var(--kh-text-secondary);
  font-size: 12px;
  cursor: pointer;
}
.history__clear:hover {
  border-color: var(--kh-danger-border, var(--kh-warm));
  color: var(--kh-warm);
}

.history__body {
  padding-bottom: var(--kh-space-12);
}
.history__list {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-3);
}
.history__item {
  display: flex;
  align-items: center;
  gap: var(--kh-space-4);
}
.history__item-main {
  flex: 1;
  min-width: 0;
}
.history__item-top {
  display: flex;
  align-items: center;
  gap: var(--kh-space-2);
}
.history__biz {
  flex: none;
  padding: 2px 8px;
  border-radius: var(--kh-radius-pill);
  font-size: 11px;
  font-weight: 600;
  background: var(--kh-primary-soft);
  color: var(--kh-primary-strong);
}
.history__biz--ARTICLE { background: rgba(16, 185, 129, 0.12); color: #047857; }
.history__biz--CHAPTER { background: rgba(139, 92, 246, 0.12); color: #6d28d9; }
.history__biz--RESOURCE { background: rgba(245, 158, 11, 0.12); color: #b45309; }
.history__item-title {
  font-size: var(--kh-font-size-md);
  font-weight: 600;
  color: var(--kh-text);
}
.history__item-meta {
  margin-top: 6px;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--kh-text-tertiary);
}
.history__sep { color: var(--kh-border); }
.history__del {
  flex: none;
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius-sm);
  background: var(--kh-surface);
  color: var(--kh-text-tertiary);
  cursor: pointer;
}
.history__del:hover {
  border-color: var(--kh-warm);
  color: var(--kh-warm);
}
.history__empty {
  text-align: center;
  color: var(--kh-text-tertiary);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--kh-space-3);
}
.history__pager {
  display: flex;
  justify-content: center;
  margin-top: var(--kh-space-8);
}
</style>
