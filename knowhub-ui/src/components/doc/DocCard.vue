<!--
  DocCard —— 文档（文章）卡片
  ------------------------------------------------------------------
  封面色块 + 难度徽标 + 标题 + 简介 + 章节数 + 标签 + 作者 + 阅读量 + 发布时间。
  数据源切真实后端 ArticlePortalRecord（/portal/article/* 出参），不再耦合 mock。
  搜索命中章节时 matchedChapters 非空，在卡上标"命中章节：x章 / y章"可点击跳章节阅读页。
-->
<script setup lang="ts">
import { useRouter } from 'vue-router'
import KhCard from '@/components/common/KhCard.vue'
import KhTag from '@/components/common/KhTag.vue'
import KhAvatar from '@/components/common/KhAvatar.vue'
import KhStatPill from '@/components/common/KhStatPill.vue'
import KhIcon from '@/components/common/KhIcon.vue'
import type { ArticlePortalRecord } from '@/types/api/knowhub/article'
import { viewLevelTagType, getViewLevelLabel } from '@/utils/viewLevel'

const props = defineProps<{ doc: ArticlePortalRecord }>()
const router = useRouter()
const goDetail = () => router.push(`/docs/${props.doc.articleId}`)

/** 跳章节阅读页（命中章节点击直接进对应章） */
const goChapter = (chapterId: number) => {
  router.push(`/docs/${props.doc.articleId}/read/${chapterId}`)
}
</script>

<template>
  <KhCard clickable padding="none" class="doc-card" @click="goDetail">
    <div class="doc-card__cover" :style="{ background: props.doc.coverUrl ? `url(${props.doc.coverUrl}) center/cover` : 'linear-gradient(135deg,#2563eb,#0ea5e9)' }">
      <KhIcon name="book" :size="28" class="doc-card__cover-icon" />
      <div v-if="props.doc.level" class="doc-card__level">
        <KhTag size="sm" :type="viewLevelTagType[props.doc.level as 1 | 2 | 3] ?? 'neutral'">{{ getViewLevelLabel(props.doc.level) }}</KhTag>
      </div>
    </div>

    <div class="doc-card__body">
      <h3 class="doc-card__title kh-line-clamp-2">{{ props.doc.title }}</h3>
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
          <KhAvatar :item="{ label: props.doc.authorNickname ?? '' }" :size="22" />
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
}
.doc-card__cover-icon {
  color: rgba(255, 255, 255, 0.92);
}
.doc-card__level {
  position: absolute;
  top: var(--kh-space-3);
  right: var(--kh-space-3);
}
.doc-card__body {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-2);
  padding: var(--kh-space-4) var(--kh-space-5);
  flex: 1;
}
.doc-card__title {
  font-size: var(--kh-font-size-md);
  font-weight: 600;
  line-height: 1.45;
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