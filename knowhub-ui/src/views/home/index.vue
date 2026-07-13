<!--
  首页 / —— knowhub 前台定调页
  ------------------------------------------------------------------
  内容优先的博客论坛首页（非产品介绍页）：
    1. Hero（润色标语 + 加高搜索框 + 四类内容徽章 + 柔和渐变 blob 浮动 + 内容依次淡入）
    2. 公告轮播条
    3. 主体两栏：
       左主列内容 feed —— 最新笔记横条 / 活跃项目网格 / 资源推荐网格
       右侧栏 —— AI 日报 / 热门资源榜 / 热门标签
  无横向滚动区域。无活跃成员墙、无大标签云、无统计卡（按反馈移除）。
  动画均带 prefers-reduced-motion 降级。
-->
<script setup lang="ts">
import { Search, ArrowRight, Promotion } from '@element-plus/icons-vue'
import KhCard from '@/components/common/KhCard.vue'
import KhTag from '@/components/common/KhTag.vue'
import KhIcon from '@/components/common/KhIcon.vue'
import BlogRow from '@/components/blog/BlogRow.vue'
import ProjectCard from '@/components/project/ProjectCard.vue'
import ResourceCard from '@/components/resource/ResourceCard.vue'

import { blogs } from '@/mock/blog'
import { projects } from '@/mock/project'
import { resources } from '@/mock/resource'
import { notices } from '@/mock/notice'
import { tags } from '@/mock/tag'
import { latestAiDaily } from '@/mock/aiDaily'
import { useRouter } from 'vue-router'

const router = useRouter()

/** 各分区取数（demo 静态切片，仅展示已发布内容） */
const latestBlogs = blogs.filter((b) => b.status === 'PUBLISHED').slice(0, 5)
const hotProjects = projects
  .filter((p) => p.status === 'PUBLISHED')
  .sort((a, b) => b.activity - a.activity)
  .slice(0, 4)
const featuredResources = resources.filter((r) => r.status === 'PUBLISHED').slice(0, 8)
const pinnedNotices = notices

/** 热门资源榜（按下载量，文件类优先） */
const hotResourceRank = [...resources]
  .filter((r) => r.status === 'PUBLISHED' && r.category !== 'WEBSITE' && r.category !== 'TOOL')
  .sort((a, b) => b.downloadCount - a.downloadCount)
  .slice(0, 6)

/** 热门标签（侧栏紧凑榜，非大标签云） */
const hotTags = [...tags].sort((a, b) => b.count - a.count).slice(0, 10)

/** 分区定义（带序号与图标点缀，增强层次） */
const sections = [
  { key: 'notes', no: '01', icon: 'blog', title: '最新笔记', tone: 'var(--kh-primary)' },
  { key: 'projects', no: '02', icon: 'project', title: '活跃项目', tone: 'var(--kh-accent)' },
  { key: 'resources', no: '03', icon: 'resource', title: '资源推荐', tone: 'var(--kh-warm)' },
] as const

/** Hero 内容类别徽章：标明系统里有什么资源 */
const contentKinds = [
  { key: 'blog', label: '博客笔记', desc: '沉淀与分享', icon: 'blog', tone: 'var(--kh-primary)', to: '/notes' },
  { key: 'project', label: '项目展示', desc: '看见成果', icon: 'project', tone: 'var(--kh-accent)', to: '/projects' },
  { key: 'resource', label: '资源推荐', desc: '精选好物', icon: 'resource', tone: 'var(--kh-warm)', to: '/resources' },
  { key: 'doc', label: '文档学习', desc: '系统进阶', icon: 'doc', tone: 'var(--kh-success)', to: '/docs' },
] as const

const goNotes = () => router.push('/notes')
</script>

<template>
  <div class="home">
    <!-- —— Hero —— -->
    <section class="hero">
      <div class="hero__bg" aria-hidden="true">
        <div class="hero__blob hero__blob--1" />
        <div class="hero__blob hero__blob--2" />
      </div>
      <div class="kh-container kh-container--wide hero__inner">
        <div class="hero__text">
          <span class="hero__badge">
            <el-icon><Promotion /></el-icon> 实验室知识沉淀与项目展示
          </span>
          <h1 class="hero__title">
            学以<span class="hero__title-grad">记之</span>，研以<span class="hero__title-grad">展之</span>
          </h1>
          <p class="hero__subtitle">
            知枢 knowhub —— 高校实验室与中小组织的综合知识库。写博客、攒项目、传资源、学文档，让实验室的智慧被看见。
          </p>

          <div class="hero__search">
            <el-icon class="hero__search-icon"><Search /></el-icon>
            <input class="hero__search-input" placeholder="搜索博客、项目、资源、文档…" @keyup.enter="goNotes" />
            <button class="hero__search-btn" type="button" @click="goNotes">搜索</button>
          </div>

          <!-- 内容类别徽章：标明系统里有什么资源 -->
          <div class="hero__kinds">
            <RouterLink v-for="k in contentKinds" :key="k.key" :to="k.to" class="hero__kind" :style="{ '--kind-tone': k.tone }">
              <span class="hero__kind-icon">
                <KhIcon :name="k.icon" :size="15" />
              </span>
              <span class="hero__kind-text">
                <span class="hero__kind-label">{{ k.label }}</span>
                <span class="hero__kind-desc">{{ k.desc }}</span>
              </span>
            </RouterLink>
          </div>
        </div>
      </div>
    </section>

    <!-- —— 公告轮播条 —— -->
    <section class="kh-container kh-container--wide notice-bar">
      <div class="notice-bar__icon">
        <KhIcon name="megaphone" :size="18" />
      </div>
      <el-carousel
        height="44px"
        direction="vertical"
        :autoplay="true"
        indicator-position="none"
        arrow="never"
        class="notice-bar__carousel"
      >
        <el-carousel-item v-for="n in pinnedNotices" :key="n.id">
          <div class="notice-bar__item">
            <KhTag
              size="sm"
              :type="n.type === '活动' ? 'warm' : n.type === '维护' ? 'warning' : n.type === '更新' ? 'primary' : 'info'"
            >{{ n.type }}</KhTag>
            <span class="notice-bar__title">{{ n.title }}</span>
            <span class="notice-bar__time">{{ n.publishTime }}</span>
          </div>
        </el-carousel-item>
      </el-carousel>
      <RouterLink to="/" class="notice-bar__more">全部公告 <el-icon><ArrowRight /></el-icon></RouterLink>
    </section>

    <!-- —— 主体两栏 —— -->
    <section class="kh-container kh-container--wide home__body">
      <!-- 左主列：内容 feed -->
      <div class="home__main">
        <!-- 最新笔记（横条列表） -->
        <section class="feed-section">
          <header class="feed-section__head">
            <div class="feed-section__title">
              <span class="feed-section__no" :style="{ color: sections[0]!.tone }">{{ sections[0]!.no }}</span>
              <KhIcon :name="sections[0]!.icon" :size="20" :style="{ color: sections[0]!.tone }" />
              <h2>{{ sections[0]!.title }}</h2>
            </div>
            <RouterLink to="/notes" class="feed-section__more">
              查看更多 <el-icon><ArrowRight /></el-icon>
            </RouterLink>
          </header>
          <div class="home__blog-list">
            <BlogRow v-for="b in latestBlogs" :key="b.id" :blog="b" />
          </div>
        </section>

        <!-- 活跃项目（网格，无横滚） -->
        <section class="feed-section">
          <header class="feed-section__head">
            <div class="feed-section__title">
              <span class="feed-section__no" :style="{ color: sections[1]!.tone }">{{ sections[1]!.no }}</span>
              <KhIcon :name="sections[1]!.icon" :size="20" :style="{ color: sections[1]!.tone }" />
              <h2>{{ sections[1]!.title }}</h2>
            </div>
            <RouterLink to="/projects" class="feed-section__more">
              查看更多 <el-icon><ArrowRight /></el-icon>
            </RouterLink>
          </header>
          <div class="home__project-grid">
            <ProjectCard v-for="p in hotProjects" :key="p.id" :project="p" />
          </div>
        </section>

        <!-- 资源推荐（网格） -->
        <section class="feed-section">
          <header class="feed-section__head">
            <div class="feed-section__title">
              <span class="feed-section__no" :style="{ color: sections[2]!.tone }">{{ sections[2]!.no }}</span>
              <KhIcon :name="sections[2]!.icon" :size="20" :style="{ color: sections[2]!.tone }" />
              <h2>{{ sections[2]!.title }}</h2>
            </div>
            <RouterLink to="/resources" class="feed-section__more">
              查看更多 <el-icon><ArrowRight /></el-icon>
            </RouterLink>
          </header>
          <div class="home__res-grid">
            <ResourceCard v-for="r in featuredResources" :key="r.id" :resource="r" />
          </div>
        </section>
      </div>

      <!-- 右侧栏 -->
      <aside class="home__aside">
        <!-- AI 日报 -->
        <KhCard gradient padding="lg" class="ai-card">
          <div class="ai-card__head">
            <span class="ai-card__tag"><KhIcon name="sparkles" :size="14" /> AI 日报</span>
            <span class="ai-card__date">{{ latestAiDaily.date }}</span>
          </div>
          <h3 class="ai-card__title">{{ latestAiDaily.title }}</h3>
          <p class="ai-card__summary kh-line-clamp-3">{{ latestAiDaily.summary }}</p>
          <div class="ai-card__topics">
            <span v-for="t in latestAiDaily.topics" :key="t">{{ t }}</span>
          </div>
          <button class="ai-card__more" type="button">
            阅读完整日报 <el-icon><ArrowRight /></el-icon>
          </button>
        </KhCard>

        <!-- 热门资源榜 -->
        <KhCard padding="md" class="home__rank">
          <div class="home__rank-head">
            <KhIcon name="trending" :size="16" />
            <h3>热门资源榜</h3>
          </div>
          <ol class="home__rank-list">
            <li
              v-for="(r, i) in hotResourceRank"
              :key="r.id"
              class="home__rank-item"
              @click="router.push(`/resource/${r.id}`)"
            >
              <span class="home__rank-no" :class="{ 'is-top': i < 3 }">{{ i + 1 }}</span>
              <div class="home__rank-icon" :style="{ background: r.cover }">
                <KhIcon :name="r.icon" :size="14" />
              </div>
              <div class="home__rank-text">
                <div class="home__rank-title kh-line-clamp-1">{{ r.title }}</div>
                <div class="home__rank-meta">{{ r.downloadCount }} 下载 · {{ r.size }}</div>
              </div>
            </li>
          </ol>
        </KhCard>

        <!-- 热门标签（紧凑榜，非大标签云） -->
        <KhCard padding="md" class="home__rank">
          <div class="home__rank-head">
            <KhIcon name="tag" :size="16" />
            <h3>热门标签</h3>
          </div>
          <ol class="home__tag-list">
            <li
              v-for="(t, i) in hotTags"
              :key="t.id"
              class="home__tag-item"
              @click="router.push('/notes')"
            >
              <span class="home__tag-no" :class="{ 'is-top': i < 3 }">{{ i + 1 }}</span>
              <span class="home__tag-name">{{ t.name }}</span>
              <span class="home__tag-bar">
                <span class="home__tag-bar-fill" :style="{ width: `${(t.count / (hotTags[0]?.count ?? 1)) * 100}%` }" />
              </span>
              <span class="home__tag-count">{{ t.count }}</span>
            </li>
          </ol>
        </KhCard>
      </aside>
    </section>
  </div>
</template>

<style scoped>
/* —— Hero（含生动微动画） —— */
.hero {
  position: relative;
  overflow: hidden;
  padding: var(--kh-space-16) 0 var(--kh-space-12);
  background: var(--kh-gradient-hero);
}
.hero__bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
}
.hero__blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(72px);
  opacity: 0.4;
  will-change: transform;
}
.hero__blob--1 {
  width: 320px;
  height: 320px;
  background: rgba(37, 99, 235, 0.3);
  top: -100px;
  right: 6%;
}
.hero__blob--2 {
  width: 260px;
  height: 260px;
  background: rgba(245, 158, 11, 0.22);
  bottom: -80px;
  left: 8%;
}
.hero__inner {
  position: relative;
  max-width: 760px;
}

.hero__badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 12px;
  border-radius: var(--kh-radius-pill);
  background: var(--kh-surface);
  border: 1px solid var(--kh-border-soft);
  color: var(--kh-text-secondary);
  font-size: 12px;
  font-weight: 500;
  box-shadow: var(--kh-shadow-xs);
}
.hero__title {
  margin-top: var(--kh-space-5);
  font-size: var(--kh-font-size-hero);
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--kh-text);
  line-height: 1.12;
}
.hero__title-grad {
  background: linear-gradient(120deg, var(--kh-primary), var(--kh-accent) 50%, var(--kh-warm), var(--kh-primary));
  background-size: 220% 100%;
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  animation: kh-grad-flow 7s linear infinite;
}
.hero__subtitle {
  margin-top: var(--kh-space-4);
  font-size: var(--kh-font-size-md);
  color: var(--kh-text-secondary);
  line-height: 1.7;
}
.hero__search {
  display: flex;
  align-items: center;
  gap: var(--kh-space-2);
  margin-top: var(--kh-space-6);
  height: 60px;
  padding: 0 6px 0 var(--kh-space-5);
  background: var(--kh-surface);
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius-pill);
  box-shadow: var(--kh-shadow-sm);
  transition: border-color var(--kh-transition), box-shadow var(--kh-transition);
}
.hero__search:focus-within {
  border-color: var(--kh-primary-border);
  box-shadow: var(--kh-focus-ring);
}
.hero__search-icon {
  color: var(--kh-text-tertiary);
  font-size: 20px;
}
.hero__search-input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: var(--kh-font-size-md);
  color: var(--kh-text);
}
.hero__search-btn {
  height: 48px;
  padding: 0 var(--kh-space-6);
  border: none;
  border-radius: var(--kh-radius-pill);
  background: linear-gradient(120deg, var(--kh-primary), var(--kh-primary-strong));
  color: #fff;
  font-weight: 600;
  font-size: var(--kh-font-size-md);
  cursor: pointer;
  transition: transform var(--kh-transition-fast), box-shadow var(--kh-transition-fast);
}
.hero__search-btn:hover {
  transform: translateY(-1px);
  box-shadow: var(--kh-shadow-primary);
}

/* 内容类别徽章：标明系统有什么资源 */
.hero__kinds {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--kh-space-3);
  margin-top: var(--kh-space-6);
  max-width: 680px;
}
.hero__kind {
  display: flex;
  align-items: center;
  gap: var(--kh-space-3);
  padding: var(--kh-space-3) var(--kh-space-4);
  background: var(--kh-surface);
  border: 1px solid var(--kh-border-soft);
  border-radius: var(--kh-radius);
  box-shadow: var(--kh-shadow-xs);
  cursor: pointer;
  transition:
    transform var(--kh-transition-fast),
    box-shadow var(--kh-transition-fast),
    border-color var(--kh-transition-fast);
}
.hero__kind:hover {
  transform: translateY(-3px);
  box-shadow: var(--kh-shadow-sm);
  border-color: var(--kind-tone, var(--kh-border-strong));
}
.hero__kind-icon {
  width: 32px;
  height: 32px;
  border-radius: var(--kh-radius-sm);
  display: grid;
  place-items: center;
  flex: none;
  color: var(--kind-tone, var(--kh-primary));
  background: color-mix(in srgb, var(--kind-tone, var(--kh-primary)) 12%, transparent);
}
.hero__kind-text {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
  min-width: 0;
}
.hero__kind-label {
  font-size: var(--kh-font-size-sm);
  font-weight: 600;
  color: var(--kh-text);
}
.hero__kind-desc {
  font-size: 11px;
  color: var(--kh-text-tertiary);
  margin-top: 1px;
}

/* —— Hero 微动效 ——
   1) blob 缓慢漂浮（错峰，纯 transform，不触发重排）
   2) 标题渐变流光（background-position 循环）
   3) Hero 内容依次淡入上浮（一次性，forwards 锁定终态）
*/
.hero__blob--1 {
  animation: kh-blob-1 16s ease-in-out infinite;
}
.hero__blob--2 {
  animation: kh-blob-2 20s ease-in-out infinite;
}
.hero__badge,
.hero__title,
.hero__subtitle,
.hero__search,
.hero__kinds {
  opacity: 0;
  transform: translateY(12px);
  animation: kh-hero-in 0.6s cubic-bezier(0.22, 1, 0.36, 1) forwards;
}
.hero__badge { animation-delay: 0.08s; }
.hero__title { animation-delay: 0.18s; }
.hero__subtitle { animation-delay: 0.30s; }
.hero__search { animation-delay: 0.42s; }
.hero__kinds { animation-delay: 0.54s; }

@keyframes kh-blob-1 {
  0%, 100% { transform: translate3d(0, 0, 0) scale(1); }
  50% { transform: translate3d(-18px, 22px, 0) scale(1.06); }
}
@keyframes kh-blob-2 {
  0%, 100% { transform: translate3d(0, 0, 0) scale(1); }
  50% { transform: translate3d(20px, -16px, 0) scale(1.05); }
}
@keyframes kh-grad-flow {
  0% { background-position: 0% 0; }
  100% { background-position: 220% 0; }
}
@keyframes kh-hero-in {
  from { opacity: 0; transform: translateY(12px); }
  to { opacity: 1; transform: translateY(0); }
}

/* 动画无障碍降级 */
@media (prefers-reduced-motion: reduce) {
  .hero__blob--1,
  .hero__blob--2,
  .hero__title-grad {
    animation: none;
  }
  .hero__badge,
  .hero__title,
  .hero__subtitle,
  .hero__search,
  .hero__kinds {
    opacity: 1;
    transform: none;
    animation: none;
  }
}

/* —— 公告条 —— */
.notice-bar {
  display: flex;
  align-items: center;
  gap: var(--kh-space-3);
  margin-top: -28px;
  padding: 10px var(--kh-space-5);
  background: var(--kh-surface);
  border: 1px solid var(--kh-border-soft);
  border-radius: var(--kh-radius);
  box-shadow: var(--kh-shadow-sm);
  position: relative;
  z-index: 2;
}
.notice-bar__icon {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: var(--kh-radius-sm);
  background: var(--kh-warm-soft);
  color: var(--kh-warm);
  flex: none;
}
.notice-bar__carousel {
  flex: 1;
  overflow: hidden;
}
.notice-bar__item {
  display: flex;
  align-items: center;
  gap: var(--kh-space-3);
  height: 100%;
}
.notice-bar__title {
  font-size: var(--kh-font-size-sm);
  color: var(--kh-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
}
.notice-bar__time {
  font-family: var(--kh-font-mono);
  font-size: 11px;
  color: var(--kh-text-tertiary);
  flex: none;
}
.notice-bar__more {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--kh-font-size-sm);
  color: var(--kh-primary);
  font-weight: 500;
  cursor: pointer;
  flex: none;
}

/* —— 主体两栏 —— */
.home__body {
  display: grid;
  grid-template-columns: 1fr 320px;
  gap: var(--kh-space-6);
  align-items: start;
  margin-top: var(--kh-space-10);
  padding-bottom: var(--kh-space-12);
}
.home__main {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-10);
  min-width: 0;
}

/* 内容分区（带序号+图标头，增强层次） */
.feed-section {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-5);
}
.feed-section__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: var(--kh-space-3);
  border-bottom: 1px solid var(--kh-border-soft);
}
.feed-section__title {
  display: flex;
  align-items: center;
  gap: var(--kh-space-3);
}
.feed-section__no {
  font-family: var(--kh-font-mono);
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.04em;
  opacity: 0.7;
}
.feed-section__title h2 {
  font-size: var(--kh-font-size-2xl);
  font-weight: 700;
  color: var(--kh-text);
  letter-spacing: -0.01em;
}
.feed-section__more {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--kh-font-size-sm);
  color: var(--kh-primary);
  font-weight: 500;
  cursor: pointer;
  transition: gap var(--kh-transition-fast);
}
.feed-section__more:hover {
  gap: 8px;
}

.home__blog-list {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-4);
}
.home__project-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--kh-space-5);
}
.home__res-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: var(--kh-space-4);
}

/* —— 右侧栏 —— */
.home__aside {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-5);
  position: sticky;
  top: calc(var(--kh-header-height) + var(--kh-space-4));
}

/* AI 日报卡 */
.ai-card {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-3);
}
.ai-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.ai-card__tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 12px;
  border-radius: var(--kh-radius-pill);
  background: linear-gradient(120deg, var(--kh-warm), #fbbf24);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
}
.ai-card__date {
  font-family: var(--kh-font-mono);
  font-size: 12px;
  color: var(--kh-text-tertiary);
}
.ai-card__title {
  font-size: var(--kh-font-size-md);
  font-weight: 600;
  line-height: 1.5;
}
.ai-card__summary {
  font-size: var(--kh-font-size-sm);
  color: var(--kh-text-secondary);
  line-height: 1.7;
}
.ai-card__topics {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.ai-card__topics span {
  font-size: 11px;
  padding: 2px 9px;
  border-radius: var(--kh-radius-pill);
  background: var(--kh-primary-soft);
  color: var(--kh-primary-strong);
}
.ai-card__more {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: auto;
  padding: 8px 0;
  border: none;
  background: transparent;
  color: var(--kh-primary);
  font-weight: 600;
  font-size: var(--kh-font-size-sm);
  cursor: pointer;
  align-self: flex-start;
  transition: gap var(--kh-transition-fast);
}
.ai-card__more:hover {
  gap: 10px;
}

/* 侧栏面板头 */
.home__rank-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: var(--kh-space-4);
  color: var(--kh-text-secondary);
}
.home__rank-head h3 {
  font-size: var(--kh-font-size-md);
  font-weight: 600;
  color: var(--kh-text);
}

/* 热门资源榜 */
.home__rank-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-3);
}
.home__rank-item {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  padding: 4px 2px;
  border-radius: var(--kh-radius-sm);
  transition: background var(--kh-transition-fast);
}
.home__rank-item:hover {
  background: var(--kh-surface-muted);
}
.home__rank-no {
  font-family: var(--kh-font-display);
  font-weight: 700;
  font-size: 13px;
  color: var(--kh-text-tertiary);
  width: 18px;
  text-align: center;
  flex: none;
}
.home__rank-no.is-top {
  color: var(--kh-warm);
}
.home__rank-icon {
  width: 30px;
  height: 30px;
  border-radius: var(--kh-radius-sm);
  display: grid;
  place-items: center;
  color: #fff;
  flex: none;
}
.home__rank-text {
  flex: 1;
  min-width: 0;
}
.home__rank-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--kh-text);
}
.home__rank-meta {
  font-size: 11px;
  color: var(--kh-text-tertiary);
  margin-top: 2px;
  font-family: var(--kh-font-mono);
}

/* 热门标签榜 */
.home__tag-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-3);
}
.home__tag-item {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 2px;
  border-radius: var(--kh-radius-sm);
  transition: background var(--kh-transition-fast);
}
.home__tag-item:hover {
  background: var(--kh-surface-muted);
}
.home__tag-no {
  font-family: var(--kh-font-display);
  font-weight: 700;
  font-size: 13px;
  color: var(--kh-text-tertiary);
  width: 18px;
  text-align: center;
  flex: none;
}
.home__tag-no.is-top {
  color: var(--kh-warm);
}
.home__tag-name {
  width: 64px;
  font-size: 13px;
  font-weight: 500;
  color: var(--kh-text);
  flex: none;
}
.home__tag-bar {
  flex: 1;
  height: 6px;
  border-radius: var(--kh-radius-pill);
  background: var(--kh-bg-soft);
  overflow: hidden;
}
.home__tag-bar-fill {
  display: block;
  height: 100%;
  border-radius: var(--kh-radius-pill);
  background: linear-gradient(90deg, var(--kh-primary), var(--kh-accent));
}
.home__tag-count {
  font-family: var(--kh-font-mono);
  font-size: 11px;
  color: var(--kh-text-tertiary);
  width: 24px;
  text-align: right;
  flex: none;
}

/* —— 响应式 —— */
@media (max-width: 1024px) {
  .home__body {
    grid-template-columns: 1fr;
  }
  .home__aside {
    position: static;
  }
  .home__project-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
@media (max-width: 768px) {
  .hero {
    padding: var(--kh-space-10) 0 var(--kh-space-8);
  }
  .hero__title {
    font-size: var(--kh-font-size-4xl);
  }
  .hero__search {
    height: 52px;
    padding-left: var(--kh-space-4);
  }
  .hero__search-btn {
    height: 40px;
    padding: 0 var(--kh-space-5);
    font-size: var(--kh-font-size-sm);
  }
  .hero__kinds {
    grid-template-columns: repeat(2, 1fr);
  }
  .home__project-grid {
    grid-template-columns: 1fr;
  }
  .notice-bar__more {
    display: none;
  }
}
</style>