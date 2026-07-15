<!--
  资源详情 /resource/:id
  ------------------------------------------------------------------
  资源头（封面色 + 分类/状态标签 + 标题 + 简介 + 作者/大小/下载量/时间）+ 行动区（网站类访问/文件类下载）
  + 简介卡（description）+ 右栏（资源信息 + 同作者其它资源）。
  仅消费 ResourceVo 已对齐字段；大小用 formatSize 格式化 contentLength。
-->
<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Download, Link } from '@element-plus/icons-vue'
import KhCard from '@/components/common/KhCard.vue'
import KhTag from '@/components/common/KhTag.vue'
import KhAvatar from '@/components/common/KhAvatar.vue'
import KhStatPill from '@/components/common/KhStatPill.vue'
import KhIcon from '@/components/common/KhIcon.vue'
import KhSectionTitle from '@/components/common/KhSectionTitle.vue'
import { getResourceById, resources } from '@/mock/resource'
import { formatDateTime } from '@/utils/format'
import toast from '@/utils/toast'

const route = useRoute()
const router = useRouter()

const resourceId = computed(() => Number(route.params.id))
const resource = computed(() => getResourceById(resourceId.value) ?? resources[0]!)

const categoryLabel: Record<string, string> = {
  WEBSITE: '网站资源',
  SOFTWARE: '软件',
  SCRIPT: '脚本',
  DOCUMENT: '文档',
  TOOL: '工具',
}
const statusMeta: Record<string, { text: string; type: 'success' | 'warning' | 'info' | 'neutral' }> = {
  PUBLISHED: { text: '已发布', type: 'success' },
  PENDING_REVIEW: { text: '待审核', type: 'warning' },
  ARCHIVED: { text: '已归档', type: 'info' },
}

/** 字节大小 → B/KB/MB/GB，与项目详情 / 资源卡 formatSize 口径一致 */
const formatSize = (len?: number) => {
  if (len == null) return '--'
  if (len < 1024) return `${len} B`
  if (len < 1024 * 1024) return `${(len / 1024).toFixed(1)} KB`
  if (len < 1024 * 1024 * 1024) return `${(len / 1024 / 1024).toFixed(1)} MB`
  return `${(len / 1024 / 1024 / 1024).toFixed(2)} GB`
}

/** 是否网站/工具类（有 linkUrl，展示"访问"） */
const isLinkType = computed(() => resource.value.category === 'WEBSITE' || resource.value.category === 'TOOL')

/** 同作者其它资源（右栏推荐） */
const moreByAuthor = computed(() =>
  resources
    .filter((r) => r.resourceId !== resource.value.resourceId && r.authorNickname === resource.value.authorNickname && r.status !== 'ARCHIVED')
    .slice(0, 4),
)

/** 访问：新窗口打开 linkUrl（无 linkUrl 则提示） */
const handleVisit = () => {
  if (resource.value.linkUrl) {
    window.open(resource.value.linkUrl, '_blank', 'noopener')
  } else {
    toast('该资源未配置访问链接')
  }
}

/** 下载（demo 占位，文件下载接口落地后替换） */
const handleDownload = () => {
  toast(`下载「${resource.value.title}」（demo 占位，文件下载接口落地后接入）`)
}

const goBack = () => router.back()
const goResource = (id: number) => router.push(`/resource/${id}`)
</script>

<template>
  <div class="rd">
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

    <!-- 资源头：左（封面可选 + 标题信息）右（行动按钮，像文章介绍页的"开始阅读"） -->
    <div class="kh-container kh-container--wide">
      <KhCard padding="lg" class="rd__head">
        <div class="rd__head-body">
          <!-- 封面：部分资源可能无封面占位，无则不渲染这一块（标题信息自动占满） -->
          <div v-if="resource.cover" class="rd__head-cover" :style="{ background: resource.cover }">
            <KhIcon :name="resource.linkIcon" :size="40" class="rd__head-cover-icon" />
          </div>
          <div class="rd__head-main">
            <div class="rd__head-tags">
              <KhTag type="primary" size="sm">{{ categoryLabel[resource.category] }}</KhTag>
              <KhTag :type="statusMeta[resource.status]?.type ?? 'neutral'" size="sm" dot>{{ statusMeta[resource.status]?.text ?? '未知' }}</KhTag>
            </div>
            <h1 class="rd__title">{{ resource.title }}</h1>
            <p class="rd__summary">{{ resource.description }}</p>
            <div class="rd__head-meta">
              <div class="rd__head-author">
                <KhAvatar :item="{ label: resource.authorNickname }" :size="28" />
                <span>{{ resource.authorNickname }}</span>
              </div>
              <KhStatPill v-if="!isLinkType && resource.contentLength" icon="file" :value="formatSize(resource.contentLength)" label="大小" />
              <KhStatPill v-else-if="isLinkType" icon="link" :value="resource.linkUrl ? '外部链接' : '无链接'" />
              <KhStatPill v-if="!isLinkType" icon="download" :value="resource.downloadCount" label="下载" />
              <span class="rd__head-time"><KhIcon name="clock" :size="12" /> {{ formatDateTime(resource.createTime) }}</span>
            </div>
          </div>

          <!-- 右：行动按钮区（网站类访问 / 文件类下载；同文章介绍页右侧布局） -->
          <div class="rd__head-action">
            <button v-if="isLinkType" class="rd__read-btn" type="button" @click="handleVisit">
              <el-icon><Link /></el-icon> 访问资源
            </button>
            <button v-else class="rd__read-btn" type="button" @click="handleDownload">
              <el-icon><Download /></el-icon> 下载资源
            </button>
            <button v-if="resource.linkUrl && !isLinkType" class="rd__read-btn rd__read-btn--ghost" type="button" @click="handleVisit">
              <el-icon><Link /></el-icon> 查看链接
            </button>
            <span v-if="!isLinkType" class="rd__read-hint">{{ formatSize(resource.contentLength) }} · {{ resource.downloadCount }} 下载</span>
          </div>
        </div>
      </KhCard>
    </div>

    <!-- 主体：左简介 / 右资源信息 + 同作者 -->
    <div class="kh-container kh-container--wide rd__layout">
      <div class="rd__main">
        <KhCard padding="lg" class="rd__section">
          <KhSectionTitle title="资源简介" />
          <p class="rd__intro">{{ resource.description }}</p>
          <div v-if="resource.linkUrl" class="rd__linkrow">
            <span class="rd__linklabel">链接</span>
            <a class="rd__link" :href="resource.linkUrl" target="_blank" rel="noopener">{{ resource.linkUrl }}</a>
          </div>
        </KhCard>
      </div>

      <aside class="rd__aside">
        <KhCard padding="md" class="rd__info">
          <h3 class="rd__info-title">资源信息</h3>
          <div class="rd__info-row"><span>分类</span><b>{{ categoryLabel[resource.category] }}</b></div>
          <div class="rd__info-row"><span>状态</span><b>{{ statusMeta[resource.status]?.text ?? '未知' }}</b></div>
          <div class="rd__info-row"><span>作者</span><b>{{ resource.authorNickname }}</b></div>
          <div v-if="!isLinkType" class="rd__info-row"><span>大小</span><b>{{ formatSize(resource.contentLength) }}</b></div>
          <div v-if="!isLinkType" class="rd__info-row"><span>下载量</span><b>{{ resource.downloadCount }}</b></div>
          <div class="rd__info-row"><span>上传</span><b>{{ formatDateTime(resource.createTime) }}</b></div>
        </KhCard>

        <KhCard v-if="moreByAuthor.length" padding="md" class="rd__more">
          <KhSectionTitle title="该作者的其它资源" />
          <ul class="rd__more-list">
            <li v-for="r in moreByAuthor" :key="r.resourceId" class="rd__more-item" @click="goResource(r.resourceId)">
              <div class="rd__more-icon" :style="{ background: r.cover }">
                <KhIcon :name="r.linkIcon" :size="14" />
              </div>
              <div class="rd__more-text">
                <div class="rd__more-title kh-line-clamp-1">{{ r.title }}</div>
                <div class="rd__more-meta">{{ categoryLabel[r.category] }}</div>
              </div>
            </li>
          </ul>
        </KhCard>
      </aside>
    </div>
  </div>
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

/* 资源头：封面(可选) + 标题信息 + 右行动按钮 */
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

/* 右：行动按钮区（与文章介绍页右侧"开始阅读"对称） */
.rd__head-action {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--kh-space-2);
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
.rd__read-btn:hover {
  transform: translateY(-1px);
}
.rd__read-btn--ghost {
  background: var(--kh-surface);
  border: 1px solid var(--kh-border);
  color: var(--kh-text-secondary);
  box-shadow: none;
  height: 38px;
  padding: 0 var(--kh-space-5);
  font-size: var(--kh-font-size-sm);
  font-weight: 500;
}
.rd__read-btn--ghost:hover {
  border-color: var(--kh-primary-border);
  color: var(--kh-primary);
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

/* 同作者 */
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
  }
  .rd__head-action .rd__read-btn {
    flex: 1;
  }
}
</style>