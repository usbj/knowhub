<!--
  资源详情 /resource/:id
  ------------------------------------------------------------------
  真实接口驱动：getResourceDetailApi（含 hasLiked/hasCollected/myScore 登录态回填 + 登录态计浏览量）
  + relatedResourcesApi（同分类相关推荐）。
  资源无 level 等级、无越级锁态（与博客详情差异点）：非 PUBLISHED 后端 404，前端跳回列表 + 提示。
  互动按钮（点赞/收藏/评分）登录态可用，未登录点击跳登录；FILE 下载按钮调 downloadResourceApi（
  /authoring/resource/{id}/download，登录态兜底 + 下载量 +1）拿链接后 window.open，不在详情预取下载链接，
  避免前台 permitAll 区触发 fileService checkOwnerOrAdmin 强转 principal CCE；LINK 访问直接 window.open(linkUrl)。
-->
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Download, Link } from '@element-plus/icons-vue'
import KhCard from '@/components/common/KhCard.vue'
import KhTag from '@/components/common/KhTag.vue'
import KhAvatar from '@/components/common/KhAvatar.vue'
import KhStatPill from '@/components/common/KhStatPill.vue'
import KhIcon from '@/components/common/KhIcon.vue'
import KhSectionTitle from '@/components/common/KhSectionTitle.vue'
import { getResourceDetailApi, relatedResourcesApi } from '@/api/knowhub/resource-portal'
import {
  downloadResourceApi,
  toggleResourceLikeApi,
  toggleResourceCollectApi,
  rateResourceApi,
} from '@/api/knowhub/resource-authoring'
import type { ResourcePortalDetailRecord, ResourcePortalRecord } from '@/types/api/knowhub/resource'
import { useUserStore } from '@/stores/user'
import { formatDateTime } from '@/utils/format'
import toast from '@/utils/toast'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const resourceId = computed(() => Number(route.params.id))
const resource = ref<ResourcePortalDetailRecord | null>(null)
const related = ref<ResourcePortalRecord[]>([])
const loading = ref(true)
const ratingValue = ref(0)
const interacting = ref(false)
const downloading = ref(false)

const isLinkType = computed(() => resource.value?.resourceType === 'LINK')
const isLoggedIn = computed(() => userStore.isAuthenticated)
const statusMeta: Record<string, { text: string; type: 'success' | 'warning' | 'info' | 'neutral' }> = {
  PUBLISHED: { text: '已发布', type: 'success' },
}
const categoryLabel = computed(() => resource.value?.categoryName || '其他')

/** 加载详情：未登录也可见（前台 permitAll），hasLiked/hasCollected/myScore 未登录为 null */
const fetchDetail = async () => {
  loading.value = true
  try {
    const res = await getResourceDetailApi(resourceId.value)
    resource.value = res.data ?? null
    if (!resource.value) {
      // 后端 404 语义（业务码 404 但 axios 走 success/code 分支时 data 可能为 null）
      ElMessage.error('资源不存在或已下架')
      router.replace('/resources')
      return
    }
    ratingValue.value = resource.value.myScore ?? 0
    void fetchRelated()
  } catch {
    resource.value = null
    router.replace('/resources')
  } finally {
    loading.value = false
  }
}

const fetchRelated = async () => {
  try {
    const res = await relatedResourcesApi(resourceId.value, 5)
    related.value = res.data ?? []
  } catch {
    related.value = []
  }
}

const requireAuth = (): boolean => {
  if (!isLoggedIn.value) {
    toast('请先登录后再操作')
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return false
  }
  return true
}

const handleLike = async () => {
  if (!resource.value || !requireAuth()) return
  interacting.value = true
  try {
    const liked = !resource.value.hasLiked
    await toggleResourceLikeApi(resourceId.value, liked)
    resource.value.hasLiked = liked
  } finally {
    interacting.value = false
  }
}

const handleCollect = async () => {
  if (!resource.value || !requireAuth()) return
  interacting.value = true
  try {
    const collected = !resource.value.hasCollected
    await toggleResourceCollectApi(resourceId.value, collected)
    resource.value.hasCollected = collected
  } finally {
    interacting.value = false
  }
}

const handleRate = async (score: number) => {
  if (!resource.value || !requireAuth()) return
  interacting.value = true
  try {
    await rateResourceApi(resourceId.value, score)
    resource.value.myScore = score
    ratingValue.value = score
    ElMessage.success('评分已提交')
  } finally {
    interacting.value = false
  }
}

/** 访问：新窗口打开 linkUrl */
const handleVisit = () => {
  if (resource.value?.linkUrl) {
    window.open(resource.value.linkUrl, '_blank', 'noopener')
  } else {
    toast('该资源未配置访问链接')
  }
}

/** 下载：登录态调 downloadResourceApi 拿链接后 window.open */
const handleDownload = async () => {
  if (!resource.value || !requireAuth()) return
  downloading.value = true
  try {
    const res = await downloadResourceApi(resourceId.value)
    const url = res.data
    if (url) window.open(url, '_blank', 'noopener')
    else toast('文件暂不可下载')
  } finally {
    downloading.value = false
  }
}

const goBack = () => router.back()
const goResource = (id: number) => router.push(`/resource/${id}`)

/** 字节大小 → B/KB/MB/GB，与 ResourceCard / 资源列表 formatSize 口径一致 */
const formatSize = (len?: number | null) => {
  if (len == null) return '--'
  if (len < 1024) return `${len} B`
  if (len < 1024 * 1024) return `${(len / 1024).toFixed(1)} KB`
  if (len < 1024 * 1024 * 1024) return `${(len / 1024 / 1024).toFixed(1)} MB`
  return `${(len / 1024 / 1024 / 1024).toFixed(2)} GB`
}

const coverGradient = computed(() =>
  isLinkType.value ? 'linear-gradient(135deg,#2563eb,#0ea5e9)' : 'linear-gradient(135deg,#6366f1,#a5b4fc)',
)
const coverIcon = computed(() => (isLinkType.value ? 'link' : 'file'))

onMounted(fetchDetail)
</script>

<template>
  <div v-if="resource" class="rd">
    <!-- 面包屑 -->
    <div class="kh-container kh-container--wide rd__crumb">
      <button class="rd__back" type="button" @click="goBack">
        <el-icon><ArrowLeft /></el-icon> 返回
      </button>
      <RouterLink to="/">首页</RouterLink>
      <el-icon class="rd__crumb-sep"><KhIcon name="chevron-right" :size="12" /></el-icon>
      <RouterLink to="/resources">资源推荐</RouterLink>
      <el-icon class="rd__crumb-sep"><KhIcon name="chevron-right" :size="12" /></el-icon>
      <span class="rd__crumb-current">{{ resource.title }}</span>
    </div>

    <!-- 资源头：左（封面 + 标题信息）右（行动按钮） -->
    <div class="kh-container kh-container--wide">
      <KhCard padding="lg" class="rd__head">
        <div class="rd__head-body">
          <div class="rd__head-cover" :style="{ background: coverGradient }">
            <KhIcon :name="coverIcon" :size="40" class="rd__head-cover-icon" />
          </div>
          <div class="rd__head-main">
            <div class="rd__head-tags">
              <KhTag type="primary" size="sm">{{ categoryLabel }}</KhTag>
              <KhTag type="info" size="sm">{{ isLinkType ? '链接' : '文件' }}</KhTag>
            </div>
            <h1 class="rd__title">{{ resource.title }}</h1>
            <p class="rd__summary">{{ resource.summary }}</p>
            <div class="rd__head-meta">
              <div class="rd__head-author">
                <KhAvatar :item="{ label: resource.authorNickname || '匿名' }" :size="28" />
                <span>{{ resource.authorNickname || '匿名' }}</span>
              </div>
              <KhStatPill v-if="!isLinkType && resource.contentLength" icon="file" :value="formatSize(resource.contentLength)" label="大小" />
              <KhStatPill v-else-if="isLinkType" icon="link" :value="resource.linkUrl ? '外部链接' : '无链接'" />
              <KhStatPill v-if="!isLinkType" icon="download" :value="resource.downloadCount ?? 0" label="下载" />
              <KhStatPill icon="eye" :value="resource.viewCount ?? 0" label="浏览" />
              <span class="rd__head-time"><KhIcon name="clock" :size="12" /> {{ formatDateTime(resource.publishTime) }}</span>
            </div>
          </div>

          <!-- 右：行动按钮区（链接类访问 / 文件类下载） -->
          <div class="rd__head-action">
            <button v-if="isLinkType" class="rd__read-btn" type="button" @click="handleVisit">
              <el-icon><Link /></el-icon> 访问资源
            </button>
            <button v-else class="rd__read-btn" type="button" :disabled="downloading" @click="handleDownload">
              <el-icon><Download /></el-icon> {{ downloading ? '准备中…' : '下载资源' }}
            </button>
            <div class="rd__interact">
              <button class="rd__interact-btn" :class="{ 'is-on': resource.hasLiked }" type="button" :disabled="interacting" @click="handleLike">
                <KhIcon name="heart" :size="14" /> {{ resource.likeCount ?? 0 }}
              </button>
              <button class="rd__interact-btn" :class="{ 'is-on': resource.hasCollected }" type="button" :disabled="interacting" @click="handleCollect">
                <KhIcon name="bookmark" :size="14" /> {{ resource.collectCount ?? 0 }}
              </button>
            </div>
            <div v-if="isLoggedIn" class="rd__rating">
              <el-rate :model-value="ratingValue" :max="5" @change="handleRate" />
              <span class="rd__rating-text">均分 {{ resource.ratingAvg ?? 0 }}（{{ resource.ratingCount ?? 0 }} 人评）</span>
            </div>
            <span v-else class="rd__read-hint">登录后可评分</span>
          </div>
        </div>
      </KhCard>
    </div>

    <!-- 主体：左简介 / 右资源信息 + 相关推荐 -->
    <div class="kh-container kh-container--wide rd__layout">
      <div class="rd__main">
        <KhCard padding="lg" class="rd__section">
          <KhSectionTitle title="资源简介" />
          <div v-if="resource.description" class="rd__intro">
            <v-md-preview :text="resource.description" />
          </div>
          <p v-else class="rd__intro rd__intro--empty">该资源暂无简介</p>
          <div v-if="resource.linkUrl" class="rd__linkrow">
            <span class="rd__linklabel">链接</span>
            <a class="rd__link" :href="resource.linkUrl" target="_blank" rel="noopener">{{ resource.linkUrl }}</a>
          </div>
        </KhCard>
      </div>

      <aside class="rd__aside">
        <KhCard padding="md" class="rd__info">
          <h3 class="rd__info-title">资源信息</h3>
          <div class="rd__info-row"><span>分类</span><b>{{ categoryLabel }}</b></div>
          <div class="rd__info-row"><span>类型</span><b>{{ isLinkType ? '链接' : '文件' }}</b></div>
          <div class="rd__info-row"><span>作者</span><b>{{ resource.authorNickname || '匿名' }}</b></div>
          <div v-if="!isLinkType" class="rd__info-row"><span>大小</span><b>{{ formatSize(resource.contentLength) }}</b></div>
          <div v-if="!isLinkType" class="rd__info-row"><span>下载量</span><b>{{ resource.downloadCount ?? 0 }}</b></div>
          <div class="rd__info-row"><span>浏览量</span><b>{{ resource.viewCount ?? 0 }}</b></div>
          <div class="rd__info-row"><span>评分</span><b>{{ resource.ratingAvg ?? 0 }}（{{ resource.ratingCount ?? 0 }} 人）</b></div>
          <div class="rd__info-row"><span>发布</span><b>{{ formatDateTime(resource.publishTime) }}</b></div>
        </KhCard>

        <KhCard v-if="related.length" padding="md" class="rd__more">
          <KhSectionTitle title="相关推荐" />
          <ul class="rd__more-list">
            <li v-for="r in related" :key="r.resourceId" class="rd__more-item" @click="goResource(r.resourceId)">
              <div class="rd__more-icon" :style="{ background: r.resourceType === 'LINK' ? 'linear-gradient(135deg,#2563eb,#0ea5e9)' : 'linear-gradient(135deg,#6366f1,#a5b4fc)' }">
                <KhIcon :name="r.resourceType === 'LINK' ? 'link' : 'file'" :size="14" />
              </div>
              <div class="rd__more-text">
                <div class="rd__more-title kh-line-clamp-1">{{ r.title }}</div>
                <div class="rd__more-meta">{{ r.categoryName || '其他' }} · {{ r.downloadCount ?? 0 }} 下载</div>
              </div>
            </li>
          </ul>
        </KhCard>
      </aside>
    </div>
  </div>
  <div v-else-if="loading" class="kh-container rd__loading">加载中…</div>
</template>

<style scoped>
.rd__crumb {
  display: flex;
  align-items: center;
  gap: var(--kh-space-2);
  padding-top: var(--kh-space-5);
  padding-bottom: var(--kh-space-4);
  font-size: var(--kh-font-size-sm);
  color: var(--kh-text-tertiary);
}
.rd__back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius-sm);
  background: var(--kh-surface);
  color: var(--kh-text-secondary);
  cursor: pointer;
  margin-right: var(--kh-space-3);
  font-size: 12px;
}
.rd__back:hover {
  border-color: var(--kh-primary-border);
  color: var(--kh-primary);
}
.rd__crumb a {
  color: var(--kh-text-secondary);
}
.rd__crumb a:hover {
  color: var(--kh-primary);
}
.rd__crumb-sep {
  color: var(--kh-text-tertiary);
}
.rd__crumb-current {
  color: var(--kh-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 320px;
}

/* 资源头 */
.rd__head {
  overflow: hidden;
}
.rd__head-body {
  display: flex;
  align-items: center;
  gap: var(--kh-space-5);
}
.rd__head-cover {
  width: 104px;
  height: 104px;
  border-radius: var(--kh-radius);
  display: grid;
  place-items: center;
  flex: none;
}
.rd__head-cover-icon {
  color: rgba(255, 255, 255, 0.92);
}
.rd__head-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-3);
}
.rd__head-tags {
  display: flex;
  gap: 6px;
}
.rd__title {
  font-size: var(--kh-font-size-3xl);
  font-weight: 700;
  letter-spacing: -0.01em;
}
.rd__summary {
  color: var(--kh-text-secondary);
  font-size: var(--kh-font-size-md);
  line-height: 1.7;
}
.rd__head-meta {
  display: flex;
  align-items: center;
  gap: var(--kh-space-5);
  flex-wrap: wrap;
  font-size: 12px;
  color: var(--kh-text-tertiary);
}
.rd__head-author {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--kh-text-secondary);
  font-weight: 500;
}
.rd__head-time {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

/* 右：行动按钮区 */
.rd__head-action {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--kh-space-3);
  flex: none;
}
.rd__read-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 44px;
  padding: 0 var(--kh-space-6);
  border: none;
  border-radius: var(--kh-radius-pill);
  background: linear-gradient(120deg, var(--kh-primary), var(--kh-primary-strong));
  color: #fff;
  font-weight: 600;
  font-size: var(--kh-font-size-md);
  cursor: pointer;
  box-shadow: var(--kh-shadow-primary);
  transition: transform var(--kh-transition-fast);
  white-space: nowrap;
}
.rd__read-btn:hover:not(:disabled) {
  transform: translateY(-1px);
}
.rd__read-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.rd__interact {
  display: flex;
  gap: 8px;
}
.rd__interact-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 6px 14px;
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius-pill);
  background: var(--kh-surface);
  color: var(--kh-text-secondary);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--kh-transition-fast);
}
.rd__interact-btn:hover:not(:disabled) {
  border-color: var(--kh-primary-border);
  color: var(--kh-primary);
}
.rd__interact-btn.is-on {
  background: var(--kh-primary-soft);
  border-color: var(--kh-primary);
  color: var(--kh-primary-strong);
}
.rd__interact-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.rd__rating {
  display: flex;
  align-items: center;
  gap: 8px;
}
.rd__rating-text {
  font-size: 11px;
  color: var(--kh-text-tertiary);
}
.rd__read-hint {
  font-size: 11px;
  color: var(--kh-text-tertiary);
  font-family: var(--kh-font-mono);
}

/* 主体 */
.rd__layout {
  display: grid;
  grid-template-columns: 1fr 300px;
  gap: var(--kh-space-6);
  align-items: start;
  margin-top: var(--kh-space-6);
  padding-bottom: var(--kh-space-12);
}
.rd__main {
  min-width: 0;
}
.rd__section {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-3);
}
.rd__intro {
  font-size: var(--kh-font-size-md);
  line-height: 1.9;
  color: var(--kh-text);
  margin: 0;
}
/* v-md-preview github 主题根类 github-markdown-body：清自带左右内边距让简介与上方标题左对齐、
   去一二级标题下横线，与博客/项目详情正文渲染口径一致 */
.rd__intro :deep(.github-markdown-body) {
  background: transparent;
  padding: 0;
  font-family: var(--kh-font-body);
  font-size: var(--kh-font-size-md);
  line-height: 1.9;
  color: var(--kh-text);
}
.rd__intro :deep(.github-markdown-body h1),
.rd__intro :deep(.github-markdown-body h2) {
  border-bottom: none;
}
.rd__intro--empty {
  color: var(--kh-text-tertiary);
  white-space: pre-wrap;
}
.rd__linkrow {
  display: flex;
  align-items: center;
  gap: var(--kh-space-3);
  padding: var(--kh-space-3) var(--kh-space-4);
  background: var(--kh-surface-muted);
  border-radius: var(--kh-radius);
  margin-top: var(--kh-space-2);
}
.rd__linklabel {
  font-size: 12px;
  color: var(--kh-text-tertiary);
  flex: none;
}
.rd__link {
  font-family: var(--kh-font-mono);
  font-size: 13px;
  color: var(--kh-primary);
  word-break: break-all;
}
.rd__link:hover {
  text-decoration: underline;
}

/* 右栏 */
.rd__aside {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-5);
  position: sticky;
  top: calc(var(--kh-header-height) + var(--kh-space-4));
}
.rd__info-title {
  font-size: var(--kh-font-size-md);
  font-weight: 600;
  margin-bottom: var(--kh-space-3);
}
.rd__info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  font-size: 12px;
  border-bottom: 1px dashed var(--kh-border-soft);
}
.rd__info-row:last-child {
  border-bottom: none;
}
.rd__info-row span {
  color: var(--kh-text-tertiary);
}
.rd__info-row b {
  color: var(--kh-text);
  font-weight: 500;
  max-width: 180px;
  text-align: right;
}

/* 相关推荐 */
.rd__more-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-3);
}
.rd__more-item {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  padding: 4px;
  border-radius: var(--kh-radius-sm);
  transition: background var(--kh-transition-fast);
}
.rd__more-item:hover {
  background: var(--kh-surface-muted);
}
.rd__more-icon {
  width: 30px;
  height: 30px;
  border-radius: var(--kh-radius-sm);
  display: grid;
  place-items: center;
  color: #fff;
  flex: none;
}
.rd__more-text {
  flex: 1;
  min-width: 0;
}
.rd__more-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--kh-text);
}
.rd__more-meta {
  font-size: 11px;
  color: var(--kh-text-tertiary);
  margin-top: 2px;
}

.rd__loading {
  text-align: center;
  padding: var(--kh-space-12);
  color: var(--kh-text-tertiary);
}

@media (max-width: 1024px) {
  .rd__layout {
    grid-template-columns: 1fr;
  }
  .rd__aside {
    position: static;
  }
}
@media (max-width: 640px) {
  .rd__head-body {
    flex-direction: column;
    align-items: stretch;
  }
  .rd__head-cover {
    width: 100%;
    height: 72px;
  }
  .rd__head-action {
    flex-direction: row;
    justify-content: stretch;
    flex-wrap: wrap;
  }
}
</style>