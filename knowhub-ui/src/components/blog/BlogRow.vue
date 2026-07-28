<!--
  BlogRow —— 横条状博客项（CSDN/掘金风）
  ------------------------------------------------------------------
  中间标题+摘要+标签+元信息 / 右侧可选封面缩略图（有则显示 144x96，无则不占位）。
  封面统一在右，有图无图混排时左侧文字对齐，视觉不突兀。clickable 跳详情。
-->
<script setup lang="ts">
import { useRouter } from 'vue-router'
import { computed } from 'vue'
import KhCard from '@/components/common/KhCard.vue'
import KhTag from '@/components/common/KhTag.vue'
import KhAvatar from '@/components/common/KhAvatar.vue'
import KhStatPill from '@/components/common/KhStatPill.vue'
import KhIcon from '@/components/common/KhIcon.vue'
import type { MockBlog } from '@/mock/blog'
import type { BlogPortalRecord } from '@/types/api/knowhub/blog'

/** 兼容 mock(MockBlog) 与真实接口(BlogPortalRecord)两种数据源：
 *  mock 有 tags:string[] + rating；真实接口有 tagIds/tagNames(可能空) + 无 rating。
 *  首页仍用 mock，notes/搜索用真实接口，统一在此归一化展示字段。 */
const props = defineProps<{ blog: MockBlog | BlogPortalRecord }>()
const router = useRouter()
const goDetail = () => router.push(`/blog/${props.blog.blogId}`)

/** 展示用标签名列表：优先 tagNames，其次 mock 的 tags，空则 [] */
const displayTags = computed<string[]>(() => {
  const b = props.blog as MockBlog & BlogPortalRecord
  if (b.tagNames && b.tagNames.length) return b.tagNames
  if (b.tags && b.tags.length) return b.tags
  return []
})
/** 作者昵称：真实接口已 join 带 authorNickname，mock 也有；兜底空串 */
const authorNickname = computed(() => (props.blog as MockBlog).authorNickname ?? '')
</script>

<template>
  <KhCard clickable padding="md" class="blog-row" @click="goDetail">
    <div class="blog-row__body">
      <h3 class="blog-row__title kh-line-clamp-1">{{ blog.title }}</h3>
      <p class="blog-row__summary kh-line-clamp-2">{{ blog.summary }}</p>

      <div class="blog-row__tags">
        <KhTag v-for="t in displayTags.slice(0, 4)" :key="t" size="sm" type="primary">{{ t }}</KhTag>
      </div>

      <div class="blog-row__meta">
        <div class="blog-row__author">
          <KhAvatar :item="{ label: authorNickname }" :size="20" />
          <span>{{ authorNickname }}</span>
        </div>
        <span class="blog-row__sep" />
        <!-- rating 仅 mock 有（后端缺口#12），真实接口无则不渲染评分条 -->
        <KhRating v-if="(blog as MockBlog).rating" :value="(blog as MockBlog).rating" :size="11" show-value />
        <span v-if="(blog as MockBlog).rating" class="blog-row__sep" />
        <div class="blog-row__stats">
          <KhStatPill icon="eye" :value="blog.viewCount ?? 0" />
          <KhStatPill icon="heart" :value="blog.likeCount ?? 0" />
          <KhStatPill icon="bookmark" :value="blog.collectCount ?? 0" />
        </div>
        <span class="blog-row__time">
          <KhIcon name="clock" :size="12" />
          {{ blog.publishTime }}
        </span>
      </div>
    </div>

    <!-- 封面缩略图：右侧。有则显示，无则不占位（左侧内容自动占满） -->
    <div v-if="blog.coverUrl" class="blog-row__cover" :style="{ background: blog.coverUrl }">
      <KhIcon name="blog" :size="22" class="blog-row__cover-icon" />
    </div>
  </KhCard>
</template>

<style scoped>
.blog-row {
  display: flex;
  gap: var(--kh-space-5);
  align-items: stretch;
}
.blog-row__body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-2);
}
.blog-row__cover {
  position: relative;
  width: 160px;
  height: 104px;
  border-radius: var(--kh-radius);
  display: grid;
  place-items: center;
  flex: none;
  overflow: hidden;
}
.blog-row__cover-icon {
  color: rgba(255, 255, 255, 0.85);
}
.blog-row__title {
  font-size: var(--kh-font-size-lg);
  font-weight: 600;
  color: var(--kh-text);
  line-height: 1.4;
  transition: color var(--kh-transition-fast);
}
.blog-row:hover .blog-row__title {
  color: var(--kh-primary);
}
.blog-row__summary {
  font-size: var(--kh-font-size-sm);
  color: var(--kh-text-secondary);
  line-height: 1.6;
}
.blog-row__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.blog-row__meta {
  display: flex;
  align-items: center;
  gap: var(--kh-space-3);
  margin-top: auto;
  padding-top: var(--kh-space-2);
  font-size: 12px;
  color: var(--kh-text-tertiary);
  flex-wrap: wrap;
}
.blog-row__author {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 500;
  color: var(--kh-text-secondary);
}
.blog-row__sep {
  width: 1px;
  height: 12px;
  background: var(--kh-border);
}
.blog-row__stats {
  display: flex;
  align-items: center;
  gap: var(--kh-space-3);
}
.blog-row__time {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-left: auto;
}

@media (max-width: 640px) {
  .blog-row {
    flex-direction: column;
  }
  /* 移动端竖排：封面提到上方 */
  .blog-row__cover {
    order: -1;
    width: 100%;
    height: 130px;
  }
  .blog-row__time {
    margin-left: 0;
  }
}
</style>

<style scoped>
.blog-row {
  display: flex;
  gap: var(--kh-space-5);
  align-items: stretch;
}
.blog-row__cover {
  position: relative;
  width: 160px;
  height: 104px;
  border-radius: var(--kh-radius);
  display: grid;
  place-items: center;
  flex: none;
  overflow: hidden;
}
.blog-row__cover-icon {
  color: rgba(255, 255, 255, 0.85);
}
.blog-row__body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-2);
}
.blog-row__title {
  font-size: var(--kh-font-size-lg);
  font-weight: 600;
  color: var(--kh-text);
  line-height: 1.4;
  transition: color var(--kh-transition-fast);
}
.blog-row:hover .blog-row__title {
  color: var(--kh-primary);
}
.blog-row__summary {
  font-size: var(--kh-font-size-sm);
  color: var(--kh-text-secondary);
  line-height: 1.6;
}
.blog-row__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.blog-row__meta {
  display: flex;
  align-items: center;
  gap: var(--kh-space-3);
  margin-top: auto;
  padding-top: var(--kh-space-2);
  font-size: 12px;
  color: var(--kh-text-tertiary);
  flex-wrap: wrap;
}
.blog-row__author {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 500;
  color: var(--kh-text-secondary);
}
.blog-row__sep {
  width: 1px;
  height: 12px;
  background: var(--kh-border);
}
.blog-row__stats {
  display: flex;
  align-items: center;
  gap: var(--kh-space-3);
}
.blog-row__time {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-left: auto;
}

@media (max-width: 640px) {
  .blog-row {
    flex-direction: column;
  }
  .blog-row__cover {
    width: 100%;
    height: 120px;
  }
  .blog-row__time {
    margin-left: 0;
  }
}
</style>
