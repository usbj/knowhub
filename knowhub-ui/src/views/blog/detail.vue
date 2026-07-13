<!--
  博客详情 /blog/:id
  ------------------------------------------------------------------
  标题 + 作者/时间/标签/观看数/评分 + 点赞/收藏/分享 + 左侧目录锚点 + 正文（静态预渲染）+ 相关推荐 + 评论区占位。
-->
<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowLeft,
  ChatDotRound,
  Share,
  Collection,
} from '@element-plus/icons-vue'
import KhCard from '@/components/common/KhCard.vue'
import KhTag from '@/components/common/KhTag.vue'
import KhAvatar from '@/components/common/KhAvatar.vue'
import KhRating from '@/components/common/KhRating.vue'
import KhStatPill from '@/components/common/KhStatPill.vue'
import KhSectionTitle from '@/components/common/KhSectionTitle.vue'
import KhIcon from '@/components/common/KhIcon.vue'
import BlogCard from '@/components/blog/BlogCard.vue'
import { getBlogById, blogs } from '@/mock/blog'

const route = useRoute()
const router = useRouter()

const blogId = computed(() => Number(route.params.id))
const blog = computed(() => getBlogById(blogId.value) ?? blogs[0]!)

/** 从正文提取标题作为目录锚点 */
const toc = computed(() => {
  const lines = (blog.value.content ?? '').split('\n')
  return lines
    .filter((l) => l.startsWith('## '))
    .map((l) => l.replace(/^##\s+/, '').trim())
    .slice(0, 8)
})

/** 相关推荐：同标签的其它博客 */
const related = computed(() =>
  blogs
    .filter((b) => b.id !== blog.value.id && b.status === 'PUBLISHED' && b.tags.some((t) => blog.value.tags.includes(t)))
    .slice(0, 3),
)

const goBack = () => router.back()

/** 点赞/收藏状态（demo 交互占位） */
const liked = computed(() => ({ value: false }))
const collected = computed(() => ({ value: false }))
</script>

<template>
  <div class="bd">
    <!-- 面包屑 -->
    <div class="kh-container kh-container--wide bd__crumb">
      <button class="bd__back" type="button" @click="goBack">
        <el-icon><ArrowLeft /></el-icon> 返回
      </button>
      <RouterLink to="/">首页</RouterLink>
      <el-icon class="bd__crumb-sep"><KhIcon name="chevron-right" :size="12" /></el-icon>
      <RouterLink to="/notes">笔记导航</RouterLink>
      <el-icon class="bd__crumb-sep"><KhIcon name="chevron-right" :size="12" /></el-icon>
      <span class="bd__crumb-current">{{ blog.title }}</span>
    </div>

    <div class="kh-container kh-container--wide bd__layout">
      <!-- 主体 -->
      <article class="bd__main">
        <!-- 头部信息 -->
        <header class="bd__head">
          <div class="bd__tags">
            <KhTag v-for="t in blog.tags" :key="t" type="primary">{{ t }}</KhTag>
          </div>
          <h1 class="bd__title">{{ blog.title }}</h1>
          <div class="bd__meta">
            <div class="bd__author">
              <KhAvatar :item="{ label: blog.author }" :size="36" />
              <div>
                <div class="bd__author-name">{{ blog.author }}</div>
                <div class="bd__author-time">发布于 {{ blog.publishTime }}</div>
              </div>
            </div>
            <div class="bd__meta-stats">
              <KhRating :value="blog.rating" :size="14" show-value />
              <KhStatPill icon="eye" :value="blog.views" label="阅读" />
              <KhStatPill icon="heart" :value="blog.likes" label="赞" />
              <KhStatPill icon="bookmark" :value="blog.collects" label="收藏" />
            </div>
          </div>
        </header>

        <!-- 正文（静态预渲染） -->
        <div class="bd__content markdown">
          <pre class="markdown__pre">{{ blog.content }}</pre>
        </div>

        <!-- 底部操作 -->
        <div class="bd__actions">
          <button class="bd__action" :class="{ 'is-active': liked.value }" type="button">
            <KhIcon name="heart" :size="14" /> {{ blog.likes }}
          </button>
          <button class="bd__action" :class="{ 'is-active': collected.value }" type="button">
            <el-icon><Collection /></el-icon> 收藏
          </button>
          <button class="bd__action" type="button">
            <el-icon><Share /></el-icon> 分享
          </button>
          <button class="bd__action" type="button">
            <KhIcon name="star" :size="14" /> 评分
          </button>
          <button class="bd__action bd__action--warn" type="button">
            <KhIcon name="megaphone" :size="14" /> 问题反馈
          </button>
        </div>

        <!-- 评论区占位 -->
        <KhCard padding="lg" class="bd__comments">
          <KhSectionTitle title="评论" subtitle="登录后参与讨论" />
          <div class="bd__comment-input">
            <KhAvatar :item="{ label: '林溪' }" :size="36" />
            <input class="bd__comment-field" placeholder="写下你的评论…" disabled />
            <button class="bd__comment-send" type="button">发送</button>
          </div>
          <div class="bd__comment-empty">
            <el-icon><ChatDotRound /></el-icon>
            <span>暂无评论，来抢沙发吧</span>
          </div>
        </KhCard>
      </article>

      <!-- 侧栏：目录 + 作者卡 + 相关推荐 -->
      <aside class="bd__aside">
        <KhCard padding="md" class="bd__toc">
          <div class="bd__toc-head">
            <KhIcon name="doc" :size="16" /> 目录
          </div>
          <ul class="bd__toc-list">
            <li v-for="(t, i) in toc" :key="i" class="bd__toc-item">
              <span class="bd__toc-no">{{ String(i + 1).padStart(2, '0') }}</span>
              <span class="bd__toc-text">{{ t }}</span>
            </li>
          </ul>
        </KhCard>

        <KhCard padding="md" class="bd__author-card">
          <div class="bd__author-row">
            <KhAvatar :item="{ label: blog.author }" :size="48" />
            <div>
              <div class="bd__author-card-name">{{ blog.author }}</div>
              <div class="bd__author-card-bio">实验室成员 · 知识贡献者</div>
            </div>
          </div>
          <button class="bd__follow" type="button">关注作者</button>
        </KhCard>

        <KhCard padding="md" class="bd__related">
          <KhSectionTitle title="相关推荐" />
          <ul class="bd__related-list">
            <li v-for="r in related" :key="r.id" class="bd__related-item" @click="router.push(`/blog/${r.id}`)">
              <div class="bd__related-cover" :style="{ background: r.cover }">
                <KhIcon name="blog" :size="16" />
              </div>
              <div class="bd__related-text">
                <div class="bd__related-title kh-line-clamp-2">{{ r.title }}</div>
                <div class="bd__related-meta">{{ r.views }} 阅读 · {{ r.rating.toFixed(1) }} 评分</div>
              </div>
            </li>
            <li v-if="!related.length" class="bd__related-empty">暂无相关推荐</li>
          </ul>
        </KhCard>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.bd__crumb {
  display: flex;
  align-items: center;
  gap: var(--kh-space-2);
  padding-top: var(--kh-space-5);
  padding-bottom: var(--kh-space-4);
  font-size: var(--kh-font-size-sm);
  color: var(--kh-text-tertiary);
}
.bd__back {
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
.bd__back:hover {
  border-color: var(--kh-primary-border);
  color: var(--kh-primary);
}
.bd__crumb a {
  color: var(--kh-text-secondary);
}
.bd__crumb a:hover {
  color: var(--kh-primary);
}
.bd__crumb-sep {
  color: var(--kh-text-tertiary);
}
.bd__crumb-current {
  color: var(--kh-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 320px;
}

.bd__layout {
  display: grid;
  grid-template-columns: 1fr 280px;
  gap: var(--kh-space-8);
  align-items: start;
  padding-bottom: var(--kh-space-12);
}
.bd__main {
  min-width: 0;
}
.bd__head {
  padding-bottom: var(--kh-space-6);
  border-bottom: 1px solid var(--kh-border-soft);
}
.bd__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: var(--kh-space-3);
}
.bd__title {
  font-size: var(--kh-font-size-4xl);
  font-weight: 700;
  line-height: 1.3;
  letter-spacing: -0.01em;
}
.bd__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kh-space-4);
  margin-top: var(--kh-space-5);
  flex-wrap: wrap;
}
.bd__author {
  display: flex;
  align-items: center;
  gap: 10px;
}
.bd__author-name {
  font-size: var(--kh-font-size-sm);
  font-weight: 600;
  color: var(--kh-text);
}
.bd__author-time {
  font-size: 12px;
  color: var(--kh-text-tertiary);
}
.bd__meta-stats {
  display: flex;
  align-items: center;
  gap: var(--kh-space-4);
}

/* 正文 —— 模拟 markdown 排版 */
.bd__content {
  padding: var(--kh-space-8) 0;
}
.markdown__pre {
  margin: 0;
  white-space: pre-wrap;
  word-wrap: break-word;
  font-family: var(--kh-font-body);
  font-size: var(--kh-font-size-md);
  line-height: 1.9;
  color: var(--kh-text);
  background: none;
}
:deep(.markdown__pre) {
  line-height: 1.9;
}

.bd__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--kh-space-3);
  padding: var(--kh-space-5) 0;
  border-top: 1px solid var(--kh-border-soft);
  border-bottom: 1px solid var(--kh-border-soft);
}
.bd__action {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 38px;
  padding: 0 var(--kh-space-4);
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius-pill);
  background: var(--kh-surface);
  color: var(--kh-text-secondary);
  font-size: var(--kh-font-size-sm);
  font-weight: 500;
  cursor: pointer;
  transition: all var(--kh-transition-fast);
}
.bd__action:hover {
  border-color: var(--kh-primary-border);
  color: var(--kh-primary);
  background: var(--kh-primary-soft);
}
.bd__action.is-active {
  background: var(--kh-primary);
  border-color: var(--kh-primary);
  color: #fff;
}
.bd__action--warn {
  margin-left: auto;
}
.bd__action--warn:hover {
  border-color: var(--kh-warm);
  color: var(--kh-warm);
  background: var(--kh-warm-soft);
}

.bd__comments {
  margin-top: var(--kh-space-6);
}
.bd__comment-input {
  display: flex;
  align-items: center;
  gap: var(--kh-space-3);
  margin-top: var(--kh-space-4);
}
.bd__comment-field {
  flex: 1;
  height: 40px;
  padding: 0 var(--kh-space-4);
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius-pill);
  background: var(--kh-surface-muted);
  outline: none;
  font-size: var(--kh-font-size-sm);
}
.bd__comment-send {
  height: 40px;
  padding: 0 var(--kh-space-5);
  border: none;
  border-radius: var(--kh-radius-pill);
  background: var(--kh-primary);
  color: #fff;
  font-weight: 600;
  font-size: var(--kh-font-size-sm);
  cursor: pointer;
}
.bd__comment-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: var(--kh-space-10);
  color: var(--kh-text-tertiary);
  font-size: var(--kh-font-size-sm);
}

/* 侧栏 */
.bd__aside {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-5);
  position: sticky;
  top: calc(var(--kh-header-height) + var(--kh-space-4));
}
.bd__toc-head {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--kh-text-tertiary);
  font-size: var(--kh-font-size-sm);
  font-weight: 600;
  margin-bottom: var(--kh-space-4);
}
.bd__toc-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.bd__toc-item {
  display: flex;
  gap: 8px;
  padding: 8px 10px;
  border-radius: var(--kh-radius-sm);
  cursor: pointer;
  transition: background var(--kh-transition-fast);
}
.bd__toc-item:hover {
  background: var(--kh-surface-muted);
}
.bd__toc-no {
  font-family: var(--kh-font-mono);
  font-size: 11px;
  color: var(--kh-text-tertiary);
  flex: none;
}
.bd__toc-text {
  font-size: 13px;
  color: var(--kh-text-secondary);
  line-height: 1.4;
}
.bd__author-card {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-3);
}
.bd__author-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.bd__author-card-name {
  font-size: var(--kh-font-size-md);
  font-weight: 600;
  color: var(--kh-text);
}
.bd__author-card-bio {
  font-size: 12px;
  color: var(--kh-text-tertiary);
  margin-top: 2px;
}
.bd__follow {
  height: 34px;
  border: 1px solid var(--kh-primary-border);
  border-radius: var(--kh-radius-pill);
  background: var(--kh-primary-soft);
  color: var(--kh-primary-strong);
  font-weight: 600;
  font-size: var(--kh-font-size-sm);
  cursor: pointer;
}
.bd__follow:hover {
  background: var(--kh-primary);
  color: #fff;
}
.bd__related-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-3);
}
.bd__related-item {
  display: flex;
  gap: 10px;
  cursor: pointer;
  padding: 6px;
  border-radius: var(--kh-radius-sm);
  transition: background var(--kh-transition-fast);
}
.bd__related-item:hover {
  background: var(--kh-surface-muted);
}
.bd__related-cover {
  width: 44px;
  height: 44px;
  border-radius: var(--kh-radius-sm);
  display: grid;
  place-items: center;
  color: rgba(255, 255, 255, 0.92);
  flex: none;
}
.bd__related-text {
  min-width: 0;
  flex: 1;
}
.bd__related-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--kh-text);
  line-height: 1.4;
}
.bd__related-meta {
  font-size: 11px;
  color: var(--kh-text-tertiary);
  margin-top: 3px;
  font-family: var(--kh-font-mono);
}
.bd__related-empty {
  text-align: center;
  padding: var(--kh-space-5);
  color: var(--kh-text-tertiary);
  font-size: var(--kh-font-size-sm);
}

@media (max-width: 1024px) {
  .bd__layout {
    grid-template-columns: 1fr;
  }
  .bd__aside {
    position: static;
  }
}
</style>
