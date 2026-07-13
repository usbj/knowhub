<!--
  BlogCard —— 博客卡片
  ------------------------------------------------------------------
  封面色块 + 标题 + 摘要 + 标签条 + 作者 + 评分 + 观看/点赞/收藏。clickable 跳详情。
-->
<script setup lang="ts">
import { useRouter } from 'vue-router'
import KhCard from '@/components/common/KhCard.vue'
import KhTag from '@/components/common/KhTag.vue'
import KhAvatar from '@/components/common/KhAvatar.vue'
import KhRating from '@/components/common/KhRating.vue'
import KhStatPill from '@/components/common/KhStatPill.vue'
import KhIcon from '@/components/common/KhIcon.vue'
import type { MockBlog } from '@/mock/blog'

const props = defineProps<{ blog: MockBlog }>()
const router = useRouter()

/** 点击卡片跳博客详情 */
const goDetail = () => router.push(`/blog/${props.blog.id}`)
</script>

<template>
  <KhCard clickable padding="none" class="blog-card" @click="goDetail">
    <!-- 封面 -->
    <div class="blog-card__cover" :style="{ background: blog.cover ?? 'linear-gradient(135deg,#2563eb,#0ea5e9)' }">
      <KhIcon name="blog" :size="28" class="blog-card__cover-icon" />
      <div class="blog-card__cover-tags">
        <KhTag v-if="blog.status !== 'PUBLISHED'" size="sm" :type="blog.status === 'PENDING_REVIEW' ? 'warning' : blog.status === 'REJECTED' ? 'danger' : 'neutral'">
          {{ blog.status === 'PENDING_REVIEW' ? '待审核' : blog.status === 'REJECTED' ? '已驳回' : blog.status === 'DRAFT' ? '草稿' : '已撤回' }}
        </KhTag>
      </div>
    </div>

    <!-- 正文 -->
    <div class="blog-card__body">
      <h3 class="blog-card__title kh-line-clamp-2">{{ blog.title }}</h3>
      <p class="blog-card__summary kh-line-clamp-2">{{ blog.summary }}</p>

      <div class="blog-card__tags">
        <KhTag v-for="t in blog.tags" :key="t" size="sm" type="primary">{{ t }}</KhTag>
      </div>

      <div class="blog-card__meta">
        <div class="blog-card__author">
          <KhAvatar :item="{ label: blog.author }" :size="24" />
          <span class="blog-card__author-name">{{ blog.author }}</span>
        </div>
        <KhRating :value="blog.rating" :size="12" />
      </div>

      <div class="blog-card__stats">
        <KhStatPill icon="eye" :value="blog.views" />
        <KhStatPill icon="heart" :value="blog.likes" />
        <KhStatPill icon="bookmark" :value="blog.collects" />
        <span class="blog-card__time">
          <KhIcon name="clock" :size="12" />
          {{ blog.publishTime }}
        </span>
      </div>
    </div>
  </KhCard>
</template>

<style scoped>
.blog-card {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.blog-card__cover {
  position: relative;
  height: 140px;
  display: flex;
  align-items: flex-end;
  padding: var(--kh-space-4);
}
.blog-card__cover-icon {
  color: rgba(255, 255, 255, 0.85);
}
.blog-card__cover-tags {
  position: absolute;
  top: var(--kh-space-3);
  right: var(--kh-space-3);
}
.blog-card__body {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-3);
  padding: var(--kh-space-5);
  flex: 1;
}
.blog-card__title {
  font-size: var(--kh-font-size-lg);
  font-weight: 600;
  color: var(--kh-text);
  line-height: 1.45;
}
.blog-card__summary {
  font-size: var(--kh-font-size-sm);
  color: var(--kh-text-secondary);
  line-height: 1.6;
}
.blog-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.blog-card__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: auto;
  padding-top: var(--kh-space-2);
}
.blog-card__author {
  display: flex;
  align-items: center;
  gap: 8px;
}
.blog-card__author-name {
  font-size: var(--kh-font-size-sm);
  color: var(--kh-text-secondary);
}
.blog-card__stats {
  display: flex;
  align-items: center;
  gap: var(--kh-space-4);
  padding-top: var(--kh-space-3);
  border-top: 1px solid var(--kh-border-soft);
}
.blog-card__time {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-left: auto;
  font-size: 11px;
  color: var(--kh-text-tertiary);
}
</style>
