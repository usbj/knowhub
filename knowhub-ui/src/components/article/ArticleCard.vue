<!--
  ArticleCard —— 文章（文档）卡片
  ------------------------------------------------------------------
  封面色块 + 难度徽标 + 标题 + 简介 + 章节数 + 标签 + 作者 + 阅读量 + 发布时间。
  数据源切真实后端 ArticlePortalRecord（/portal/article/* 出参），不再耦合 mock。
  搜索命中章节时 matchedChapters 非空，在卡上标"命中章节：x章 / y章"可点击跳章节阅读页。
-->
<script setup lang="ts">
import { useRouter } from 'vue-router'
import { computed } from 'vue'
import KhCard from '@/components/common/KhCard.vue'
import KhTag from '@/components/common/KhTag.vue'
import KhAvatar from '@/components/common/KhAvatar.vue'
import KhStatPill from '@/components/common/KhStatPill.vue'
import KhIcon from '@/components/common/KhIcon.vue'
import type { ArticlePortalRecord } from '@/types/api/knowhub/article'
import { viewLevelTagType, getViewLevelLabel } from '@/utils/viewLevel'

const props = defineProps<{ doc: ArticlePortalRecord }>()
const router = useRouter()
const goDetail = () => router.push(`/article/${props.doc.articleId}`)

/** 跳章节阅读页（命中章节点击直接进对应章） */
const goChapter = (chapterId: number) => {
  router.push(`/article/${props.doc.articleId}/read/${chapterId}`)
}

/**
 * 封面 coverUrl 形态辨识：真实接口为 /file/resolve/{id} 或绝对 URL（含 / 或 http）→ <img> 渲染；
 * 兜底渐变色块由 CSS background 默认值提供（coverUrl 空时 .doc-card__cover 的渐变背景）。
 * 与 BlogRow 同口径，避免裸 background: coverUrl 的非法 CSS 值致封面加载不出。
 */
const hasImageCover = computed(() => {
  const url = props.doc.coverUrl
  if (!url) return false
  return /^https?:\/\//i.test(url) || url.startsWith('/')
})
</script>

<template>
  <KhCard clickable padding="none" class="doc-card" @click="goDetail">
    <div class="doc-card__cover" :class="{ 'doc-card__cover--img': hasImageCover }">
      <img v-if="hasImageCover" :src="props.doc.coverUrl ?? undefined" alt="封面" class="doc-card__cover-img" />
      <KhIcon v-else name="book" :size="28" class="doc-card__cover-icon" />
    </div>

    <div class="doc-card__body">
      <div class="doc-card__title-row">
        <h3 class="doc-card__title kh-line-clamp-2">{{ props.doc.title }}</h3>
        <KhTag v-if="props.doc.level" size="sm" class="doc-card__level" :type="viewLevelTagType[props.doc.level as 1 | 2 | 3] ?? 'neutral'">{{ getViewLevelLabel(props.doc.level) }}</KhTag>
      </div>
      <p class="doc-card__summary kh-line-clamp-2">{{ props.doc.summary }}</p>

      <div v-if="props.doc.tagNames?.length" class="doc-card__tags">
        <KhTag v-for="t in props.doc.tagNames" :key="t" size="sm" type="accent">{{ t }}</KhTag>
      </div>

      <!-- 搜索命中章节标出（仅 search 接口填充 matchedChapters） -->
      <div v-if="props.doc.matchedChapters?.length" class="doc-card__matched">
        <KhIcon name="search" :size="12" />
        <span>命中章节：</span>
        <button
          v-for="m in props.doc.matchedChapters"
          :key="m.chapterId"
          type="button"
          class="doc-card__matched-chip"
          @click.stop="goChapter(m.chapterId)"
        >{{ m.chapterName }}</button>
      </div>

      <div class="doc-card__chapters">
        <KhIcon name="doc" :size="13" />
        <span>共 {{ props.doc.chapterCount ?? 0 }} 章</span>
      </div>

      <div class="doc-card__meta">
        <div class="doc-card__author">
          <KhAvatar :item="{ label: props.doc.authorNickname ?? '', src: props.doc.authorAvatar ?? undefined }" :size="22" />
          <span>{{ props.doc.authorNickname ?? '匿名' }}</span>
        </div>
        <span v-if="props.doc.publishTime" class="doc-card__time">
          <KhIcon name="clock" :size="12" />
          {{ props.doc.publishTime }}
        </span>
      </div>

      <div class="doc-card__stats">
        <KhStatPill icon="eye" :value="props.doc.viewCount ?? 0" label="阅读" />
      </div>
    </div>
  </KhCard>
</template>

<style scoped>
.doc-card {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.doc-card__cover {
  position: relative;
  height: 96px;
  display: grid;
  place-items: center;
  /* 放大溢出由 overflow:hidden 裁掉，与资源卡封面悬浮放大同口径 */
  overflow: hidden;
  /* 无封面时的渐变兜底（hasImageCover=false 走图标占位，背景渐变打底） */
  background: linear-gradient(135deg, #2563eb, #0ea5e9);
}
/* 有图片封面：display 切回 block，让 <img> object-fit 铺满 */
.doc-card__cover--img {
  display: block;
}
.doc-card__cover-img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center;
  transition: transform 280ms ease;
}
/* 悬浮卡片时封面图放大 1.08（cover 填满裁切，溢出由 .doc-card__cover overflow:hidden 裁掉） */
.doc-card:hover .doc-card__cover-img {
  transform: scale(1.08);
}
.doc-card__cover-icon {
  color: rgba(255, 255, 255, 0.92);
}
.doc-card__body {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-2);
  padding: var(--kh-space-4) var(--kh-space-5);
  flex: 1;
}
/* 标题行：标题与等级标签持平——标题占满剩余宽、标签靠右不缩 */
.doc-card__title-row {
  display: flex;
  align-items: flex-start;
  gap: var(--kh-space-2);
}
.doc-card__title {
  flex: 1 1 auto;
  min-width: 0;
  font-size: var(--kh-font-size-md);
  font-weight: 600;
  line-height: 1.45;
}
/* 等级标签贴标题右侧顶部对齐（align-items:flex-start 时与标题首行持平），不缩不折行 */
.doc-card__level {
  flex: none;
  margin-top: 2px;
}
.doc-card__summary {
  font-size: var(--kh-font-size-sm);
  color: var(--kh-text-secondary);
  line-height: 1.55;
}
.doc-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
/* 命中章节标示：仅搜索结果带出，可点击跳章节阅读页 */
.doc-card__matched {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--kh-primary-strong);
}
.doc-card__matched span {
  color: var(--kh-text-tertiary);
}
.doc-card__matched-chip {
  border: 1px dashed var(--kh-primary-border);
  border-radius: var(--kh-radius-sm);
  background: var(--kh-primary-soft);
  color: var(--kh-primary-strong);
  font-size: 11px;
  padding: 1px 6px;
  cursor: pointer;
  transition: all var(--kh-transition-fast);
}
.doc-card__matched-chip:hover {
  background: var(--kh-primary);
  color: #fff;
  border-style: solid;
}
.doc-card__chapters {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: var(--kh-text-secondary);
  font-weight: 500;
}
.doc-card__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: auto;
  padding-top: var(--kh-space-2);
  font-size: 11px;
  color: var(--kh-text-tertiary);
}
.doc-card__author {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.doc-card__time {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}
.doc-card__stats {
  padding-top: var(--kh-space-3);
  border-top: 1px solid var(--kh-border-soft);
}
</style>