<!--
  公告列表页 /notices
  ------------------------------------------------------------------
  顶栏公告下拉"查看全部"的落脚页。顶部分类筛选 + 公告卡片列表。
  demo 阶段用本地 mock，真实接口：缺面向访客的公开公告接口（见计划）。
-->
<script setup lang="ts">
import { computed, ref } from 'vue'
import KhCard from '@/components/common/KhCard.vue'
import KhTag from '@/components/common/KhTag.vue'
import KhIcon from '@/components/common/KhIcon.vue'
import { notices } from '@/mock/notice'

type FilterKey = 'ALL' | '系统' | '活动' | '维护' | '更新'
const filterKey = ref<FilterKey>('ALL')
const filters: { key: FilterKey; label: string; tone?: string }[] = [
  { key: 'ALL', label: '全部' },
  { key: '更新', label: '更新' },
  { key: '活动', label: '活动' },
  { key: '维护', label: '维护' },
  { key: '系统', label: '系统' },
]

const tagType: Record<string, 'primary' | 'warm' | 'warning' | 'info'> = {
  更新: 'primary',
  活动: 'warm',
  维护: 'warning',
  系统: 'info',
}

/** 置顶优先，再按时间倒序 */
const sortedNotices = computed(() => {
  const list = [...notices].sort((a, b) => b.publishTime.localeCompare(a.publishTime))
  return list.sort((a, b) => Number(!!b.pinned) - Number(!!a.pinned))
})

const filtered = computed(() =>
  filterKey.value === 'ALL'
    ? sortedNotices.value
    : sortedNotices.value.filter((n) => n.type === filterKey.value),
)
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
            @click="filterKey = f.key"
          >
            {{ f.label }}
            <span class="notices__filter-count">
              {{ f.key === 'ALL' ? notices.length : notices.filter((n) => n.type === f.key).length }}
            </span>
          </button>
        </div>
      </div>
    </section>

    <!-- 公告列表 -->
    <section class="kh-container kh-container--wide notices__body">
      <div v-if="filtered.length" class="notices__list">
        <KhCard
          v-for="n in filtered"
          :key="n.id"
          padding="lg"
          class="notice-card"
          :class="{ 'is-pinned': n.pinned }"
        >
          <div class="notice-card__head">
            <KhTag size="sm" :type="tagType[n.type] ?? 'info'">{{ n.type }}</KhTag>
            <span v-if="n.pinned" class="notice-card__pin"><KhIcon name="star" :size="12" /> 置顶</span>
            <span class="notice-card__time">
              <KhIcon name="clock" :size="12" /> {{ n.publishTime }}
            </span>
          </div>
          <h2 class="notice-card__title">{{ n.title }}</h2>
          <p class="notice-card__content">{{ n.content }}</p>
          <div class="notice-card__foot">
            <span class="notice-card__publisher">
              <KhIcon name="user" :size="12" /> {{ n.publisher }}
            </span>
            <button class="notice-card__more" type="button">
              查看详情 <KhIcon name="arrow-right" :size="13" />
            </button>
          </div>
        </KhCard>
      </div>
      <KhCard v-else padding="lg" class="notices__empty">
        <KhIcon name="megaphone" :size="40" :stroke="1.4" />
        <p>该分类下暂无公告</p>
      </KhCard>
    </section>
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
</style>