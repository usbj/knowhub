<!--
  文章介绍页 /docs/:id
  ------------------------------------------------------------------
  标题信息卡（封面色条 + 标题 + 摘要 + 作者/难度/章节/阅读/时间 + 标签 + 右"开始阅读"）。
  下方两栏：左文章简介正文（v-md-preview 渲染 summary——后端 summary 是 TEXT，可长可短，
  按长文本排版、短文本也不空）/ 右章节大纲卡（点章节进阅读页对应章）。
  返回按钮：回上一级 /docs（文档学习页），非历史页。
-->
<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Reading } from '@element-plus/icons-vue'
import KhCard from '@/components/common/KhCard.vue'
import KhTag from '@/components/common/KhTag.vue'
import KhAvatar from '@/components/common/KhAvatar.vue'
import KhStatPill from '@/components/common/KhStatPill.vue'
import KhIcon from '@/components/common/KhIcon.vue'
import KhSectionTitle from '@/components/common/KhSectionTitle.vue'
import { getDocById, docs } from '@/mock/doc'
import { viewLevelTagType, getViewLevelLabel } from '@/utils/viewLevel'

const route = useRoute()
const router = useRouter()

const docId = computed(() => Number(route.params.id))
const doc = computed(() => getDocById(docId.value) ?? docs[0]!)

/** 开始阅读：跳章节内容页，缺省第一章（带 ?ch=） */
const startReading = (chapterId?: number) => {
  const ch = chapterId ?? doc.value.chapters[0]?.id
  router.push({ path: `/docs/${docId.value}/read`, query: ch ? { ch: String(ch) } : undefined })
}

/** 返回上一级：文档学习页（非历史页） */
const goUp = () => router.push('/docs')
</script>

<template>
  <div class="di">
    <!-- 面包屑 -->
    <div class="kh-container kh-container--wide di__crumb">
      <button class="di__back" type="button" @click="goUp">
        <el-icon><ArrowLeft /></el-icon> 返回
      </button>
      <RouterLink to="/">首页</RouterLink>
      <el-icon class="di__crumb-sep"><KhIcon name="chevron-right" :size="12" /></el-icon>
      <RouterLink to="/docs">文档学习</RouterLink>
      <el-icon class="di__crumb-sep"><KhIcon name="chevron-right" :size="12" /></el-icon>
      <span class="di__crumb-current">{{ doc.title }}</span>
    </div>

    <!-- 标题信息卡：左信息 + 右"开始阅读"按钮 -->
    <div class="kh-container kh-container--wide">
      <KhCard padding="none" class="di__head">
        <div class="di__head-cover" :style="{ background: doc.cover }">
          <KhIcon name="doc" :size="32" class="di__head-cover-icon" />
        </div>
        <div class="di__head-body">
          <div class="di__head-main">
            <h1 class="di__title">{{ doc.title }}</h1>
            <p class="di__head-summary">{{ doc.summary.replace(/[#>*|`]/g, '').replace(/\n+/g, ' ').trim().slice(0, 80) }}…</p>
            <div class="di__head-meta">
              <div class="di__head-author">
                <KhAvatar :item="{ label: doc.author }" :size="28" />
                <span>{{ doc.author }}</span>
              </div>
              <KhTag :type="viewLevelTagType[doc.level] ?? 'neutral'" size="sm">{{ getViewLevelLabel(doc.level) }}</KhTag>
              <KhStatPill icon="file" :value="doc.chapterCount" label="章" />
              <KhStatPill icon="eye" :value="doc.readCount" label="阅读" />
              <span class="di__head-time"><KhIcon name="clock" :size="12" /> {{ doc.updateTime }}</span>
            </div>
            <div class="di__head-tags">
              <KhTag v-for="t in doc.tags" :key="t" size="sm" type="primary">{{ t }}</KhTag>
            </div>
          </div>

          <!-- 右：开始阅读 -->
          <div class="di__head-action">
            <button class="di__read-btn" type="button" @click="startReading()">
              <el-icon><Reading /></el-icon> 开始阅读
            </button>
            <span class="di__read-hint">共 {{ doc.chapterCount }} 章</span>
          </div>
        </div>
      </KhCard>
    </div>

    <!-- 主体两栏：左简介正文 + 右章节大纲 -->
    <div class="kh-container kh-container--wide di__layout">
      <article class="di__main">
        <KhCard padding="lg" class="di__intro-card">
          <KhSectionTitle title="文章简介" />
          <!-- summary 是 TEXT，可能很长（像 README），用 v-md-preview 渲染承载长文；
               短 summary 时也按 markdown body 排版，不空。github 主题根类 github-markdown-body -->
          <div class="di__intro">
            <v-md-preview :text="doc.summary" />
          </div>
        </KhCard>
      </article>

      <aside class="di__aside">
        <!-- 章节大纲卡 -->
        <KhCard padding="md" class="di__outline-card">
          <div class="di__outline-head">
            <KhIcon name="doc" :size="16" />
            <h3>章节大纲</h3>
          </div>
          <p class="di__outline-sub">共 {{ doc.chapters.length }} 章 · 点章节进入阅读</p>
          <ol class="di__outline-list">
            <li
              v-for="c in doc.chapters"
              :key="c.id"
              class="di__outline-item"
              @click="startReading(c.id)"
            >
              <span class="di__outline-no">{{ String(c.id).padStart(2, '0') }}</span>
              <span class="di__outline-title kh-line-clamp-2">{{ c.title }}</span>
              <el-icon class="di__outline-arrow"><ArrowLeft /></el-icon>
            </li>
          </ol>

          <!-- 大纲卡底部开始阅读 -->
          <button class="di__read-btn di__read-btn--block" type="button" @click="startReading()">
            <el-icon><Reading /></el-icon> 开始阅读
          </button>
        </KhCard>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.di__crumb {
  display: flex;
  align-items: center;
  gap: var(--kh-space-2);
  padding-top: var(--kh-space-5);
  padding-bottom: var(--kh-space-4);
  font-size: var(--kh-font-size-sm);
  color: var(--kh-text-tertiary);
}
.di__back {
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
.di__back:hover {
  border-color: var(--kh-primary-border);
  color: var(--kh-primary);
}
.di__crumb a {
  color: var(--kh-text-secondary);
}
.di__crumb a:hover {
  color: var(--kh-primary);
}
.di__crumb-sep {
  color: var(--kh-text-tertiary);
}
.di__crumb-current {
  color: var(--kh-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 320px;
}

/* 标题信息卡 */
.di__head {
  overflow: hidden;
}
.di__head-cover {
  height: 6px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding-right: var(--kh-space-6);
}
.di__head-cover-icon {
  color: rgba(255, 255, 255, 0.9);
  position: relative;
  top: 14px;
}
.di__head-body {
  display: flex;
  align-items: center;
  gap: var(--kh-space-6);
  padding: var(--kh-space-5) var(--kh-space-6);
}
.di__head-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-3);
}
.di__title {
  font-size: var(--kh-font-size-3xl);
  font-weight: 700;
  letter-spacing: -0.01em;
}
/* 卡头摘要：从长 summary 取首段纯文本截断，避免把 markdown 源码塞进卡头 */
.di__head-summary {
  color: var(--kh-text-secondary);
  font-size: var(--kh-font-size-md);
  line-height: 1.7;
}
.di__head-meta {
  display: flex;
  align-items: center;
  gap: var(--kh-space-4);
  flex-wrap: wrap;
  font-size: 12px;
  color: var(--kh-text-tertiary);
}
.di__head-author {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--kh-text-secondary);
  font-weight: 500;
}
.di__head-time {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.di__head-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

/* 右：开始阅读按钮区 */
.di__head-action {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--kh-space-2);
  flex: none;
}
.di__read-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 44px;
  padding: 0 var(--kh-space-6);
  border: none;
  border-radius: var(--kh-radius-pill);
  background: linear-gradient(120deg, var(--kh-primary), var(--kh-primary-strong));
  color: #fff;
  font-weight: 600;
  font-size: var(--kh-font-size-md);
  cursor: pointer;
  box-shadow: var(--kh-shadow-primary);
  transition: transform var(--kh-transition-fast);
  white-space: nowrap;
}
.di__read-btn:hover {
  transform: translateY(-1px);
}
.di__read-btn--block {
  width: 100%;
  margin-top: var(--kh-space-4);
}
.di__read-hint {
  font-size: 11px;
  color: var(--kh-text-tertiary);
}

/* 主体两栏 */
.di__layout {
  display: grid;
  grid-template-columns: 1fr 300px;
  gap: var(--kh-space-6);
  align-items: start;
  margin-top: var(--kh-space-6);
  padding-bottom: var(--kh-space-12);
}
.di__main {
  min-width: 0;
}
.di__intro-card {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-4);
}
/* 简介正文：v-md-preview，清掉主题左右内边距对齐、去一二级标题下横线、行高加大承载长文 */
.di__intro :deep(.github-markdown-body) {
  background: transparent;
  padding: 0;
  font-family: var(--kh-font-body);
  font-size: var(--kh-font-size-md);
  line-height: 1.95;
  color: var(--kh-text);
}
.di__intro :deep(.github-markdown-body h1),
.di__intro :deep(.github-markdown-body h2) {
  border-bottom: none;
  margin-top: var(--kh-space-6);
}

/* 右栏：章节大纲卡 sticky */
.di__aside {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-5);
  position: sticky;
  top: calc(var(--kh-header-height) + var(--kh-space-4));
}
.di__outline-card {
  display: flex;
  flex-direction: column;
}
.di__outline-head {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--kh-text-secondary);
  margin-bottom: 2px;
}
.di__outline-head h3 {
  font-size: var(--kh-font-size-md);
  font-weight: 600;
  color: var(--kh-text);
}
.di__outline-sub {
  font-size: 11px;
  color: var(--kh-text-tertiary);
  margin: 0 0 var(--kh-space-3);
}
.di__outline-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-2);
}
.di__outline-item {
  display: flex;
  align-items: center;
  gap: var(--kh-space-3);
  padding: var(--kh-space-3) var(--kh-space-3);
  border-radius: var(--kh-radius-sm);
  cursor: pointer;
  transition: background var(--kh-transition-fast);
}
.di__outline-item:hover {
  background: var(--kh-primary-soft);
}
.di__outline-no {
  font-family: var(--kh-font-mono);
  font-size: 12px;
  font-weight: 700;
  color: var(--kh-primary);
  flex: none;
  width: 24px;
}
.di__outline-title {
  flex: 1;
  min-width: 0;
  font-size: var(--kh-font-size-sm);
  font-weight: 500;
  color: var(--kh-text);
  line-height: 1.4;
}
.di__outline-arrow {
  transform: rotate(180deg);
  color: var(--kh-text-tertiary);
  flex: none;
}
.di__outline-item:hover .di__outline-arrow {
  color: var(--kh-primary);
}

@media (max-width: 1024px) {
  .di__layout {
    grid-template-columns: 1fr;
  }
  .di__aside {
    position: static;
  }
}
@media (max-width: 768px) {
  .di__head-body {
    flex-direction: column;
    align-items: stretch;
    gap: var(--kh-space-4);
  }
  .di__head-action {
    flex-direction: row;
    justify-content: space-between;
  }
}
</style>