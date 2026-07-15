<!--
  DocCard —— 文档（文章）卡片
  ------------------------------------------------------------------
  封面色块 + 难度徽标 + 标题 + 简介 + 章节数 + 标签 + 作者 + 阅读量 + 更新时间。
-->
<script setup lang="ts">
import { useRouter } from 'vue-router'
import KhCard from '@/components/common/KhCard.vue'
import KhTag from '@/components/common/KhTag.vue'
import KhAvatar from '@/components/common/KhAvatar.vue'
import KhStatPill from '@/components/common/KhStatPill.vue'
import KhIcon from '@/components/common/KhIcon.vue'
import type { MockDoc } from '@/mock/doc'
import { viewLevelTagType, getViewLevelLabel } from '@/utils/viewLevel'

const props = defineProps<{ doc: MockDoc }>()
const router = useRouter()
const goDetail = () => router.push(`/docs/${props.doc.id}`)
</script>

<template>
  <KhCard clickable padding="none" class="doc-card" @click="goDetail">
    <div class="doc-card__cover" :style="{ background: doc.cover }">
      <KhIcon name="book" :size="28" class="doc-card__cover-icon" />
      <div class="doc-card__level">
        <KhTag size="sm" :type="viewLevelTagType[doc.level] ?? 'neutral'">{{ getViewLevelLabel(doc.level) }}</KhTag>
      </div>
    </div>

    <div class="doc-card__body">
      <h3 class="doc-card__title kh-line-clamp-2">{{ doc.title }}</h3>
      <p class="doc-card__summary kh-line-clamp-2">{{ doc.summary }}</p>

      <div class="doc-card__tags">
        <KhTag v-for="t in doc.tags" :key="t" size="sm" type="accent">{{ t }}</KhTag>
      </div>

      <div class="doc-card__chapters">
        <KhIcon name="doc" :size="13" />
        <span>共 {{ doc.chapterCount }} 章</span>
      </div>

      <div class="doc-card__meta">
        <div class="doc-card__author">
          <KhAvatar :item="{ label: doc.author }" :size="22" />
          <span>{{ doc.author }}</span>
        </div>
        <span class="doc-card__time">
          <KhIcon name="clock" :size="12" />
          {{ doc.updateTime }}
        </span>
      </div>

      <div class="doc-card__stats">
        <KhStatPill icon="eye" :value="doc.readCount" label="阅读" />
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
