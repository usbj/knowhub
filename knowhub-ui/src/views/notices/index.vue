<!--
  公告列表页 /notices
  ------------------------------------------------------------------
  顶栏公告下拉"查看全部"的落脚页。顶部分类筛选 + 公告卡片列表 + 分页器。
  接 /portal/notice/list 公开公告接口（群发+已发布，未登录访客可读），点"查看详情"开固定大小弹窗，
  正文用 v-md-preview 渲染 markdown（与博客详情同款 github 主题）。
  noticeType 字典 code（NOTICE/NOTIFY/REMIND）内联映射，不引字典预加载（公开页可能未登录）。
-->
<script setup lang="ts">
import { computed, ref } from 'vue'
import KhCard from '@/components/common/KhCard.vue'
import KhTag from '@/components/common/KhTag.vue'
import KhIcon from '@/components/common/KhIcon.vue'
import { getPublicNoticesApi } from '@/api/system/notice-portal'
import type { NoticePortalRecord } from '@/types/api/notice-portal'
import { useNoticeStore } from '@/stores/notice'
import { formatDateTime } from '@/utils/format'

const noticeStore = useNoticeStore()

type FilterKey = 'ALL' | 'NOTICE' | 'NOTIFY' | 'REMIND'
const filterKey = ref<FilterKey>('ALL')
const filters: { key: FilterKey; label: string }[] = [
  { key: 'ALL', label: '全部' },
  { key: 'NOTICE', label: '公告' },
  { key: 'NOTIFY', label: '通知' },
  { key: 'REMIND', label: '提醒' },
]

const noticeTypeMap: Record<string, string> = { NOTICE: '公告', NOTIFY: '通知', REMIND: '提醒' }
const noticeTagType: Record<string, 'warm' | 'success' | 'primary' | 'info'> = {
  NOTICE: 'warm',
  NOTIFY: 'success',
  REMIND: 'primary',
}
const resolveNoticeType = (code: string) => noticeTypeMap[code] ?? code
const resolveTagType = (code: string) => noticeTagType[code] ?? 'info'

/** 公告列表 + 分页态 */
const notices = ref<NoticePortalRecord[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const loading = ref(false)

/** 按当前筛选 + 分页拉取公告列表（失败兜底空，公开页不弹错） */
const fetchNotices = async () => {
  loading.value = true
  try {
    const page = await getPublicNoticesApi({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      noticeType: filterKey.value === 'ALL' ? undefined : filterKey.value,
    })
    notices.value = page.records ?? []
    total.value = page.total ?? 0
  } catch {
    notices.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

/** 切类型筛选：重置回首页再拉 */
const changeFilter = (key: FilterKey) => {
  filterKey.value = key
  pageNum.value = 1
  void fetchNotices()
}

/** 切页码 */
const changePage = (p: number) => {
  pageNum.value = p
  void fetchNotices()
}

/** 各分类计数（仅当前已加载页的近似计数，用于 tab 角标；总数以分页 total 为准） */
const countFor = (key: FilterKey) =>
  key === 'ALL' ? total.value : notices.value.filter((n) => n.noticeType === key).length

/** 点卡片"查看详情"：开全局详情弹窗（store.openDetail 注入 currentNotice，由 AppLayout 的 <KhNoticeDetailDialog> 渲染） */
const openDetail = (n: NoticePortalRecord) => {
  noticeStore.openDetail(n)
}

/** 公告列表已按置顶优先+时间倒序返回，前端不再二次排序 */
const sortedNotices = computed(() => notices.value)

void fetchNotices()
</script>

<template>
  <div class="notices">
    <!-- 顶部 Hero（紧凑） -->
    <section class="notices__hero">
      <div class="kh-container kh-container--wide">
        <div class="notices__hero-head">
          <h1 class="notices__title">
            <span class="notices__title-icon"><KhIcon name="megaphone" :size="20" /></span>
            系统公告
          </h1>
          <p class="notices__subtitle">knowhub 平台动态、功能更新与活动通知</p>
        </div>

        <!-- 分类筛选 -->
        <div class="notices__filters">
          <button
            v-for="f in filters"
            :key="f.key"
            class="notices__filter"
            :class="{ 'is-active': filterKey === f.key }"
            type="button"
            @click="changeFilter(f.key)"
          >
            {{ f.label }}
            <span class="notices__filter-count">{{ countFor(f.key) }}</span>
          </button>
        </div>
      </div>
    </section>

    <!-- 公告列表 -->
    <section class="kh-container kh-container--wide notices__body">
      <div v-if="sortedNotices.length" class="notices__list">
        <KhCard
          v-for="n in sortedNotices"
          :key="n.noticeId"
          padding="lg"
          class="notice-card"
          :class="{ 'is-pinned': Number(n.isTop) === 1 }"
        >
          <div class="notice-card__head">
            <KhTag size="sm" :type="resolveTagType(n.noticeType)">{{ resolveNoticeType(n.noticeType) }}</KhTag>
            <span v-if="Number(n.isTop) === 1" class="notice-card__pin"><KhIcon name="star" :size="12" /> 置顶</span>
            <span class="notice-card__time">
              <KhIcon name="clock" :size="12" /> {{ formatDateTime(n.publishTime) }}
            </span>
          </div>
          <h2 class="notice-card__title">{{ n.title }}</h2>
          <p class="notice-card__content">{{ n.content }}</p>
          <div class="notice-card__foot">
            <span class="notice-card__publisher">
              <KhIcon name="user" :size="12" /> {{ n.createBy ?? '系统' }}
            </span>
            <button class="notice-card__more" type="button" @click="openDetail(n)">
              查看详情 <KhIcon name="arrow-right" :size="13" />
            </button>
          </div>
        </KhCard>
      </div>
      <KhCard v-else-if="!loading" padding="lg" class="notices__empty">
        <KhIcon name="megaphone" :size="40" :stroke="1.4" />
        <p>该分类下暂无公告</p>
      </KhCard>

      <!-- 分页器 -->
      <div v-if="total > pageSize" class="notices__pagination">
        <el-pagination
          background
          layout="prev, pager, next"
          :current-page="pageNum"
          :page-size="pageSize"
          :total="total"
          @current-change="changePage"
        />
      </div>
    </section>

    <!-- 详情弹窗已提取为全局组件 <KhNoticeDetailDialog> 挂在 AppLayout，点"查看详情"经 noticeStore.openDetail 触发 -->
  </div>
</template>

<style scoped>
.notices__hero {
  padding: var(--kh-space-10) 0 var(--kh-space-6);
  background: var(--kh-gradient-hero);
}
.notices__hero-head {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-3);
}
.notices__title {
  display: flex;
  align-items: center;
  gap: var(--kh-space-3);
  font-family: var(--kh-font-display);
  font-size: var(--kh-font-size-3xl);
  font-weight: 700;
  color: var(--kh-text);
}
.notices__title-icon {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: var(--kh-radius);
  background: var(--kh-warm-soft);
  color: var(--kh-warm);
}
.notices__subtitle {
  color: var(--kh-text-secondary);
  font-size: var(--kh-font-size-md);
}

.notices__filters {
  display: flex;
  flex-wrap: wrap;
  gap: var(--kh-space-2);
  margin-top: var(--kh-space-6);
}
.notices__filter {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius-pill);
  background: var(--kh-surface);
  color: var(--kh-text-secondary);
  font-size: var(--kh-font-size-sm);
  font-weight: 500;
  cursor: pointer;
  transition: all var(--kh-transition-fast);
}
.notices__filter:hover {
  border-color: var(--kh-border-strong);
  color: var(--kh-text);
}
.notices__filter.is-active {
  background: var(--kh-primary);
  border-color: var(--kh-primary);
  color: #fff;
}
.notices__filter-count {
  font-family: var(--kh-font-mono);
  font-size: 11px;
  padding: 1px 6px;
  border-radius: var(--kh-radius-pill);
  background: var(--kh-bg-soft);
  color: var(--kh-text-tertiary);
}
.notices__filter.is-active .notices__filter-count {
  background: rgba(255, 255, 255, 0.22);
  color: #fff;
}

.notices__body {
  margin-top: var(--kh-space-8);
  padding-bottom: var(--kh-space-12);
}
.notices__list {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-4);
}
.notice-card {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-3);
}
.notice-card.is-pinned {
  border-color: var(--kh-warm-soft);
  box-shadow: 0 2px 12px rgba(245, 158, 11, 0.1);
}
.notice-card.is-pinned::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 4px;
  background: linear-gradient(180deg, var(--kh-warm), var(--kh-accent));
  border-radius: var(--kh-radius-lg) 0 0 var(--kh-radius-lg);
}
.notice-card__head {
  display: flex;
  align-items: center;
  gap: var(--kh-space-3);
}
.notice-card__pin {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 11px;
  font-weight: 600;
  color: var(--kh-warm);
  padding: 2px 8px;
  border-radius: var(--kh-radius-pill);
  background: var(--kh-warm-soft);
}
.notice-card__time {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--kh-text-tertiary);
  font-family: var(--kh-font-mono);
}
.notice-card__title {
  font-size: var(--kh-font-size-lg);
  font-weight: 600;
  color: var(--kh-text);
  line-height: 1.5;
}
.notice-card__content {
  font-size: var(--kh-font-size-sm);
  color: var(--kh-text-secondary);
  line-height: 1.7;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.notice-card__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: var(--kh-space-3);
  border-top: 1px solid var(--kh-border-soft);
}
.notice-card__publisher {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--kh-text-tertiary);
}
.notice-card__more {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: none;
  background: transparent;
  color: var(--kh-primary);
  font-size: var(--kh-font-size-sm);
  font-weight: 500;
  cursor: pointer;
  transition: gap var(--kh-transition-fast);
}
.notice-card__more:hover {
  gap: 8px;
}
.notices__empty {
  text-align: center;
  color: var(--kh-text-tertiary);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--kh-space-3);
}

/* —— 分页器 —— */
.notices__pagination {
  display: flex;
  justify-content: center;
  margin-top: var(--kh-space-8);
}
</style>