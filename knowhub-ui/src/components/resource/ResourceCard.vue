<!--
  ResourceCard —— 资源卡片
  ------------------------------------------------------------------
  消费前台 ResourcePortalRecord（后端 VO），资源无 mock 的 category 枚举与 cover 色块：
  - 类型由 resourceType 区分：FILE 文件 / LINK 链接；展示口径与图标色按类型派生（不再依赖 mock category）。
  - 分类标签展示 categoryName（join 带出，-1=其他时为 null → 前端硬编码"其他"）。
  - LINK 类卡内行动按钮直接访问 linkUrl；FILE 类按钮去详情页走下载接口（详情计下载量 +1，列表不计）。
-->
<script setup lang="ts">
import { useRouter } from 'vue-router'
import KhCard from '@/components/common/KhCard.vue'
import KhTag from '@/components/common/KhTag.vue'
import KhStatPill from '@/components/common/KhStatPill.vue'
import KhIcon from '@/components/common/KhIcon.vue'
import type { ResourcePortalRecord } from '@/types/api/knowhub/resource'

const props = defineProps<{ resource: ResourcePortalRecord }>()
const router = useRouter()
const goDetail = () => router.push(`/resource/${props.resource.resourceId}`)

/** 是否链接类（有 linkUrl，直接访问）；后端 resourceType=LINK */
const isLinkType = () => props.resource.resourceType === 'LINK'

/** 卡片封面色与图标按资源类型派生（资源主表无 cover 列，前端占位色代替 mock 的 cover） */
const coverGradient = () =>
  isLinkType()
    ? 'linear-gradient(135deg,#2563eb,#0ea5e9)'
    : 'linear-gradient(135deg,#6366f1,#a5b4fc)'
const coverIcon = () => (isLinkType() ? 'link' : 'file')

const categoryLabel = () => props.resource.categoryName || '其他'

/** 卡片内行动按钮：链接类直接开链接，文件类去详情页下载（不在此 +1 下载量）。点击不冒泡到卡片跳转 */
const handleAction = () => {
  if (isLinkType()) {
    if (props.resource.linkUrl) window.open(props.resource.linkUrl, '_blank', 'noopener')
    else goDetail()
  } else {
    goDetail()
  }
}

/** 字节大小 → B/KB/MB/GB，与项目详情 / 资源详情 formatSize 口径一致 */
const formatSize = (len?: number | null) => {
  if (len == null) return '--'
  if (len < 1024) return `${len} B`
  if (len < 1024 * 1024) return `${(len / 1024).toFixed(1)} KB`
  if (len < 1024 * 1024 * 1024) return `${(len / 1024 / 1024).toFixed(1)} MB`
  return `${(len / 1024 / 1024 / 1024).toFixed(2)} GB`
}
</script>

<template>
  <KhCard clickable padding="none" class="res-card" @click="goDetail">
    <div class="res-card__cover" :style="{ background: coverGradient() }">
      <KhIcon :name="coverIcon()" :size="28" class="res-card__cover-icon" />
      <KhTag size="sm" type="neutral" class="res-card__cat">{{ categoryLabel() }}</KhTag>
    </div>

    <div class="res-card__body">
      <h3 class="res-card__title kh-line-clamp-2">{{ resource.title }}</h3>
      <p class="res-card__desc kh-line-clamp-2">{{ resource.summary }}</p>

      <div class="res-card__info">
        <span class="res-card__author">{{ resource.authorNickname || '匿名' }}</span>
        <span v-if="resource.contentLength" class="res-card__size">
          <KhIcon name="file" :size="12" /> {{ formatSize(resource.contentLength) }}
        </span>
      </div>

      <div class="res-card__footer">
        <div class="res-card__stats">
          <KhStatPill v-if="isLinkType()" icon="eye" :value="resource.viewCount ?? 0" label="浏览" />
          <template v-else>
            <KhStatPill icon="download" :value="resource.downloadCount ?? 0" label="下载" />
            <KhStatPill icon="eye" :value="resource.viewCount ?? 0" label="浏览" />
          </template>
        </div>
        <button class="res-card__action" type="button" @click.stop="handleAction">
          <template v-if="isLinkType()">
            <KhIcon name="arrow-up-right" :size="14" /> 访问
          </template>
          <template v-else>
            <KhIcon name="download" :size="14" /> 查看
          </template>
        </button>
      </div>
    </div>
  </KhCard>
</template>

<style scoped>
.res-card {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.res-card__cover {
  position: relative;
  height: 90px;
  display: grid;
  place-items: center;
}
.res-card__cover-icon {
  color: rgba(255, 255, 255, 0.92);
}
.res-card__cat {
  position: absolute;
  top: var(--kh-space-3);
  right: var(--kh-space-3);
  background: rgba(255, 255, 255, 0.92) !important;
  color: var(--kh-text) !important;
  border-color: transparent !important;
}
.res-card__body {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-2);
  padding: var(--kh-space-4) var(--kh-space-5) var(--kh-space-4);
  flex: 1;
}
.res-card__title {
  font-size: var(--kh-font-size-md);
  font-weight: 600;
  line-height: 1.45;
}
.res-card__desc {
  font-size: var(--kh-font-size-sm);
  color: var(--kh-text-secondary);
  line-height: 1.55;
  flex: 1;
}
.res-card__info {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 11px;
  color: var(--kh-text-tertiary);
}
.res-card__size {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-family: var(--kh-font-mono);
}
.res-card__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: var(--kh-space-3);
  border-top: 1px solid var(--kh-border-soft);
}
.res-card__stats {
  display: inline-flex;
  gap: var(--kh-space-3);
}
.res-card__action {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 12px;
  border-radius: var(--kh-radius);
  background: var(--kh-primary-soft);
  border: 1px solid var(--kh-primary-border);
  color: var(--kh-primary-strong);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition:
    background var(--kh-transition-fast),
    transform var(--kh-transition-fast);
}
.res-card__action:hover {
  background: var(--kh-primary);
  color: #fff;
}
</style>