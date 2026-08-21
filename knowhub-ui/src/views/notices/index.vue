<!--
  公告列表页 /notices
  ------------------------------------------------------------------
  顶栏公告下拉"查看全部"的落脚页。顶部分类筛选 + 公告卡片列表 + 分页器。
  数据源按登录态分流：
  - 登录用户走 /sys/notice/my（含审核/评论/邀请/协作等私发通知 + 已读态 + routePath，
    与顶栏铃铛同口径），点卡片开全局详情弹窗 + 标记已读，「前往查看」按钮跳对应作品。
  - 未登录游客走 /portal/notice/list（仅群发已发布公告，无读写态）。
  两者字段结构接近，统一映射成展示模型渲染；noticeType 字典 code（NOTICE/NOTIFY/REMIND）内联映射。
-->
<script setup lang="ts">
import { computed, ref } from 'vue'
import KhCard from '@/components/common/KhCard.vue'
import KhTag from '@/components/common/KhTag.vue'
import KhIcon from '@/components/common/KhIcon.vue'
import { getPublicNoticesApi } from '@/api/system/notice-portal'
import { getMyNoticesPageApi } from '@/api/system/notice'
import type { NoticePortalRecord } from '@/types/api/notice-portal'
import type { SysNoticeRecord } from '@/types/api/notice'
import { useUserStore } from '@/stores/user'
import { useNoticeStore, type NoticeDetailRecord } from '@/stores/notice'
import { formatDateTime } from '@/utils/format'

const userStore = useUserStore()
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

/**
 * 统一展示模型：两种数据源（登录 SysNoticeRecord / 游客 NoticePortalRecord）映射到同一结构渲染。
 * - raw：保留原始记录引用，点详情时透传给 noticeStore.openDetail（联合类型兼容）；
 * - unread：仅登录源有意义（hasRead），游客源恒 false（无读写态）；
 * - routePath：登录源的私发通知带作品详情路由（评论/审核/协作），游客源群发公告通常无。
 */
interface NoticeItem {
  id: number
  title: string
  content: string
  noticeType: string
  isTop: number
  publishTime?: string
  createBy?: string
  unread: boolean
  raw: NoticeDetailRecord
}

/** 登录源原始记录（含 hasRead/routePath），游客源为 null */
const myRaw = ref<SysNoticeRecord[]>([])
/** 游客源原始记录，登录态为 null */
const portalRaw = ref<NoticePortalRecord[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const loading = ref(false)

/** 统一展示列表：按登录态取对应原始记录映射成 NoticeItem */
const noticeItems = computed<NoticeItem[]>(() => {
  if (userStore.isAuthenticated) {
    return myRaw.value.map((n) => ({
      id: Number(n.noticeId),
      title: n.title,
      content: n.content,
      noticeType: n.noticeType,
      isTop: Number(n.isTop),
      publishTime: n.publishTime,
      createBy: n.createBy,
      unread: !n.hasRead,
      raw: n,
    }))
  }
  return portalRaw.value.map((n) => ({
    id: Number(n.noticeId),
    title: n.title,
    content: n.content,
    noticeType: n.noticeType,
    isTop: Number(n.isTop ?? 0),
    publishTime: n.publishTime,
    createBy: n.createBy,
    unread: false,
    raw: n,
  }))
})

/**
 * 按当前筛选 + 分页拉取列表（按登录态分流数据源）。失败兜底空，公开页不弹错。
 * 登录源传 noticeType 过滤（后端 /sys/notice/my 已支持），游客源同样传 noticeType（/portal/notice/list 已支持）。
 * 切 tab/翻页时**不归零 total**：只清数据，total 保留旧值直到新数据回来再替换，避免角标请求中闪 0。
 */
const fetchNotices = async () => {
  loading.value = true
  try {
    const typeParam = filterKey.value === 'ALL' ? undefined : filterKey.value
    if (userStore.isAuthenticated) {
      const page = await getMyNoticesPageApi({
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        noticeType: typeParam,
      })
      myRaw.value = page.records ?? []
      total.value = page.total ?? 0
    } else {
      const page = await getPublicNoticesApi({
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        noticeType: typeParam,
      })
      portalRaw.value = page.records ?? []
      total.value = page.total ?? 0
    }
  } catch {
    myRaw.value = []
    portalRaw.value = []
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

/**
 * 各分类计数（tab 角标）：
 * - 登录用户：走 noticeStore.typeCounts（后端 /sys/notice/my-counts 聚合的真实总数），
 *   ALL/NOTICE/NOTIFY/REMIND 四角标都真实，不随切 tab/翻页变（角标用全局聚合，与列表分页解耦）。
 * - 未登录游客：/sys/notice/my-counts 需登录不可用，ALL 用分页 total（群发公告真实总数），
 *   子类型无聚合接口只能用当前页内 filter 近似（游客态公告仅群发、类型少，可接受；登录态为主场景）。
 */
const countFor = (key: FilterKey) => {
  if (userStore.isAuthenticated) {
    return noticeStore.typeCounts[key] ?? 0
  }
  return key === 'ALL' ? total.value : noticeItems.value.filter((n) => n.noticeType === key).length
}

/**
 * 点卡片"查看详情"：开全局详情弹窗（store.openDetail 注入 currentNotice，由 AppLayout 的 <KhNoticeDetailDialog> 渲染）。
 * 登录源顺手标记已读（乐观，未读才请求），与顶栏下拉点击口径一致；游客源无读写态，仅开弹窗。
 */
const openDetail = (item: NoticeItem) => {
  if (userStore.isAuthenticated && item.unread && item.raw.noticeId) {
    void noticeStore.markAsRead(item.raw.noticeId)
    // 乐观同步本地列表项已读态，避免下次进页面仍显未读
    const target = myRaw.value.find((n) => Number(n.noticeId) === item.id)
    if (target) target.hasRead = true
  }
  noticeStore.openDetail(item.raw)
}

/** 列表已按置顶优先+时间倒序返回，前端不再二次排序 */
const sortedNotices = computed(() => noticeItems.value)

/**
 * 进页拉取：登录态先刷各类型真实总数（四个分类角标）再拉首页列表；
 * 游客态只拉群发公告列表（无聚合计数接口，角标走 countFor 内 total/页内近似）。
 * 角标用全局聚合 typeCounts，切 tab/翻页不重拉（角标稳定不随分页变）；
 * 新通知到达时顶栏轮询的 fetchMyNotices(true) 会顺带刷新 typeCounts，本页再次进入自然拿到最新。
 */
if (userStore.isAuthenticated) {
  void noticeStore.fetchTypeCounts()
}
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
          <p class="notices__subtitle">
            {{ userStore.isAuthenticated ? '平台动态、功能更新与你的站内通知' : 'knowhub 平台动态、功能更新与活动通知' }}
          </p>
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
          v-for="item in sortedNotices"
          :key="item.id"
          padding="lg"
          class="notice-card"
          :class="{ 'is-pinned': Number(item.isTop) === 1 }"
        >
          <div class="notice-card__head">
            <KhTag size="sm" :type="resolveTagType(item.noticeType)">{{ resolveNoticeType(item.noticeType) }}</KhTag>
            <span v-if="Number(item.isTop) === 1" class="notice-card__pin"><KhIcon name="star" :size="12" /> 置顶</span>
            <span v-if="item.unread" class="notice-card__unread-dot" title="未读"></span>
            <span class="notice-card__time">
              <KhIcon name="clock" :size="12" /> {{ formatDateTime(item.publishTime) }}
            </span>
          </div>
          <h2 class="notice-card__title">{{ item.title }}</h2>
          <p class="notice-card__content">{{ item.content }}</p>
          <div class="notice-card__foot">
            <span class="notice-card__publisher">
              <KhIcon name="user" :size="12" /> {{ item.createBy ?? '系统' }}
            </span>
            <button class="notice-card__more" type="button" @click="openDetail(item)">
              查看详情 <KhIcon name="arrow-right" :size="13" />
            </button>
          </div>
        </KhCard>
      </div>
      <KhCard v-else-if="!loading" padding="lg" class="notices__empty">
        <KhIcon name="megaphone" :size="40" :stroke="1.4" />
        <p>该分类下暂无{{ userStore.isAuthenticated ? '通知' : '公告' }}</p>
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
/* 未读小圆点：登录源 hasRead=false 时露出，与顶栏下拉未读圆点同口径 */
.notice-card__unread-dot {
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: var(--kh-primary);
  flex: none;
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