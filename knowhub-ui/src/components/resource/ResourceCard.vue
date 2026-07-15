<!--
  ResourceCard —— 资源卡片
  ------------------------------------------------------------------
  网站资源：缩略 + 访问按钮；文件资源：文件图标 + 大小 + 下载按钮 + 下载数。
-->
<script setup lang="ts">
import { useRouter } from 'vue-router'
import KhCard from '@/components/common/KhCard.vue'
import KhTag from '@/components/common/KhTag.vue'
import KhStatPill from '@/components/common/KhStatPill.vue'
import KhIcon from '@/components/common/KhIcon.vue'
import type { MockResource } from '@/mock/resource'
import toast from '@/utils/toast'

const props = defineProps<{ resource: MockResource }>()
const router = useRouter()
const goDetail = () => router.push(`/resource/${props.resource.resourceId}`)

/** 是否网站/工具类（有 linkUrl，直接访问） */
const isLinkType = () => props.resource.category === 'WEBSITE' || props.resource.category === 'TOOL'

/** 卡片内行动按钮：网站类直接开链接，文件类下载占位。点击不冒泡到卡片跳转 */
const handleAction = () => {
  if (isLinkType()) {
    if (props.resource.linkUrl) window.open(props.resource.linkUrl, '_blank', 'noopener')
    else toast('该资源未配置访问链接')
  } else {
    toast(`下载「${props.resource.title}」（demo 占位，文件下载接口落地后接入）`)
  }
}

const categoryLabel: Record<string, string> = {
  WEBSITE: '网站资源',
  SOFTWARE: '软件',
  SCRIPT: '脚本',
  DOCUMENT: '文档',
  TOOL: '工具',
}

/** 字节大小 → B/KB/MB/GB，与项目详情 formatSize 口径一致 */
const formatSize = (len?: number) => {
  if (len == null) return '--'
  if (len < 1024) return `${len} B`
  if (len < 1024 * 1024) return `${(len / 1024).toFixed(1)} KB`
  if (len < 1024 * 1024 * 1024) return `${(len / 1024 / 1024).toFixed(1)} MB`
  return `${(len / 1024 / 1024 / 1024).toFixed(2)} GB`
}
</script>

<template>
  <KhCard clickable padding="none" class="res-card" @click="goDetail">
    <div class="res-card__cover" :style="{ background: resource.cover }">
      <KhIcon :name="resource.linkIcon" :size="28" class="res-card__cover-icon" />
      <KhTag size="sm" type="neutral" class="res-card__cat">{{ categoryLabel[resource.category] }}</KhTag>
    </div>

    <div class="res-card__body">
      <h3 class="res-card__title kh-line-clamp-2">{{ resource.title }}</h3>
      <p class="res-card__desc kh-line-clamp-2">{{ resource.description }}</p>

      <div class="res-card__info">
        <span class="res-card__author">{{ resource.authorNickname }}</span>
        <span v-if="resource.contentLength" class="res-card__size">
          <KhIcon name="file" :size="12" /> {{ formatSize(resource.contentLength) }}
        </span>
      </div>

      <div class="res-card__footer">
        <div class="res-card__stats">
          <KhStatPill v-if="resource.category === 'WEBSITE' || resource.category === 'TOOL'" icon="eye" :value="resource.views" label="访问" />
          <KhStatPill v-else icon="download" :value="resource.downloadCount" label="下载" />
        </div>
        <button class="res-card__action" type="button" @click.stop="handleAction">
          <template v-if="resource.category === 'WEBSITE' || resource.category === 'TOOL'">
            <KhIcon name="arrow-up-right" :size="14" /> 访问
          </template>
          <template v-else>
            <KhIcon name="download" :size="14" /> 下载
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
