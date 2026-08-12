<!--
  BlogRow —— 横条状博客项（CSDN/掘金风）
  ------------------------------------------------------------------
  中间标题+摘要+标签（最多3个，超出折叠成 +N 徽标可点展开）+元信息
  （作者·浏览·点赞·收藏·时间，时间跟在收藏后面）/ 右侧可选封面缩略图。
  数据源为真实接口 BlogPortalRecord（/portal/blog/* 出参）。clickable 跳详情。
-->
<script setup lang="ts">
import { useRouter } from 'vue-router'
import { computed, ref } from 'vue'
import KhCard from '@/components/common/KhCard.vue'
import KhTag from '@/components/common/KhTag.vue'
import KhAvatar from '@/components/common/KhAvatar.vue'
import KhStatPill from '@/components/common/KhStatPill.vue'
import KhIcon from '@/components/common/KhIcon.vue'
import type { BlogPortalRecord } from '@/types/api/knowhub/blog'

const props = defineProps<{ blog: BlogPortalRecord }>()
const router = useRouter()
const goDetailCard = () => router.push(`/blog/${props.blog.blogId}`)

/** 展示用标签名列表：取真实接口 tagNames（可能为空），空则 [] */
const displayTags = computed<string[]>(() => props.blog.tagNames ?? [])
/** 作者昵称：真实接口已 join 带 authorNickname，兜底空串 */
const authorNickname = computed(() => props.blog.authorNickname ?? '')

/** 标签默认最多3个可见，超出折叠成 +N 徽标；点徽标展开全部（再点不动）。 */
const TAG_VISIBLE_MAX = 3
const tagsExpanded = ref(false)
const visibleTags = computed(() =>
  tagsExpanded.value ? displayTags.value : displayTags.value.slice(0, TAG_VISIBLE_MAX),
)
const hiddenTagCount = computed(() =>
  Math.max(0, displayTags.value.length - TAG_VISIBLE_MAX),
)
const toggleTags = (e: Event) => {
  e.stopPropagation()
  tagsExpanded.value = !tagsExpanded.value
}

/**
 * 封面 coverUrl 形态辨识：真实接口为 /file/resolve/{id} 或绝对 URL（含 / 或 http），
 * mock 为 linear-gradient(...) CSS 渐变。只有"图片 URL 形态"才用 <img> 渲染，否则保持 background 渐变块。
 */
const hasImageCover = computed(() => {
  const url = props.blog.coverUrl
  if (!url) return false
  // http(s) 绝对地址 / 相对路径 /file/resolve/{id} 都视为图片 URL
  return /^https?:\/\//i.test(url) || url.startsWith('/')
})
</script>

<template>
  <KhCard clickable padding="md" class="blog-row" @click="goDetailCard">
    <div class="blog-row__body">
      <h3 class="blog-row__title kh-line-clamp-1">{{ blog.title }}</h3>
      <p class="blog-row__summary kh-line-clamp-2">{{ blog.summary }}</p>

      <!-- 标签：默认最多 3 个可见，超出折叠为 +N 徽标，点徽标展开全部 -->
      <div v-if="displayTags.length" class="blog-row__tags">
        <KhTag v-for="t in visibleTags" :key="t" size="sm" type="primary">{{ t }}</KhTag>
        <button
          v-if="hiddenTagCount > 0 && !tagsExpanded"
          type="button"
          class="blog-row__tag-more"
          :title="`展开剩余 ${hiddenTagCount} 个标签`"
          @click="toggleTags"
        >+{{ hiddenTagCount }}</button>
      </div>

      <div class="blog-row__meta">
        <div class="blog-row__author">
          <KhAvatar :item="{ label: authorNickname }" :size="20" />
          <span>{{ authorNickname }}</span>
        </div>
        <span class="blog-row__sep" />
        <div class="blog-row__stats">
          <KhStatPill icon="eye" :value="blog.viewCount ?? 0" />
          <KhStatPill icon="heart" :value="blog.likeCount ?? 0" />
          <KhStatPill icon="bookmark" :value="blog.collectCount ?? 0" />
          <!-- 创建时间跟在收藏后面（用户要求，不再 margin-left:auto 独立靠右） -->
          <span class="blog-row__time">
            <KhIcon name="clock" :size="12" />
            {{ blog.publishTime }}
          </span>
        </div>
      </div>
    </div>

    <!-- 封面缩略图：右侧。真实接口 coverUrl 为图片 URL（/file/resolve/{id} / 绝对地址）→ <img>；
         非图片形态（渐变等）→ background 兜底（hasImageCover=false 时走 background 分支）。 -->
    <div
      v-if="blog.coverUrl"
      class="blog-row__cover"
      :class="{ 'blog-row__cover--img': hasImageCover }"
      :style="hasImageCover ? null : { background: blog.coverUrl }"
    >
      <img v-if="hasImageCover" :src="blog.coverUrl" alt="封面" class="blog-row__cover-img" />
      <KhIcon v-else name="blog" :size="22" class="blog-row__cover-icon" />
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
  background: var(--kh-surface-muted);
}
.blog-row__cover--img {
  display: block;
}
.blog-row__cover-img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
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
  align-items: center;
  gap: 6px;
}
/* 超过 3 个标签时折叠出的 +N 徽标按钮 */
.blog-row__tag-more {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 22px;
  min-width: 28px;
  padding: 0 8px;
  border: 1px dashed var(--kh-border);
  border-radius: var(--kh-radius-sm);
  background: var(--kh-surface-muted);
  color: var(--kh-text-tertiary);
  font-size: 11px;
  font-weight: 600;
  font-family: var(--kh-font-mono);
  cursor: pointer;
  transition: all var(--kh-transition-fast);
}
.blog-row__tag-more:hover {
  border-color: var(--kh-primary-border);
  color: var(--kh-primary);
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
  /* 时间跟在收藏后面（用户要求），不再独立靠右；与 stats 内其它项同间距 */
  margin-left: var(--kh-space-1);
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
