<!--
  AppFooter —— knowhub 前台页脚
  ------------------------------------------------------------------
  品牌简介 + 板块导航 + 友链 + 版权。轻量信息区。
-->
<script setup lang="ts">
const year = 2026

const groups = [
  {
    title: '内容',
    links: [
      { label: '笔记导航', to: '/blogs' },
      { label: '项目展示', to: '/projects' },
      { label: '文档学习', to: '/articles' },
      { label: '资源推荐', to: '/resources' },
    ],
  },
  {
    title: '个人',
    links: [
      { label: '个人中心', to: '/profile' },
      { label: '我的收藏', to: '/profile' },
      { label: '创作博客', to: '/profile' },
      { label: '上传资源', to: '/profile' },
    ],
  },
  {
    title: '关于',
    links: [
      { label: '系统公告', to: '/' },
      { label: 'AI 日报', to: '/' },
      { label: '后台管理', to: '/' },
      { label: '帮助中心', to: '/' },
    ],
  },
] as const
</script>

<template>
  <footer class="kh-footer">
    <div class="kh-container kh-container--wide kh-footer__inner">
      <div class="kh-footer__brand">
        <div class="kh-footer__logo">知枢 knowhub</div>
        <p class="kh-footer__desc">
          面向高校实验室与中小组织的综合知识库博客系统，沉淀知识、展示项目、推荐资源。
        </p>
        <div class="kh-footer__tags">
          <span>知识沉淀</span>
          <span>项目展示</span>
          <span>资源推荐</span>
        </div>
      </div>

      <nav class="kh-footer__groups" aria-label="页脚导航">
        <div v-for="g in groups" :key="g.title" class="kh-footer__group">
          <h4>{{ g.title }}</h4>
          <RouterLink v-for="l in g.links" :key="l.label" :to="l.to">{{ l.label }}</RouterLink>
        </div>
      </nav>
    </div>

    <div class="kh-footer__bar">
      <div class="kh-container kh-container--wide kh-footer__bar-inner">
        <span>© {{ year }} 知枢 knowhub · 综合知识库博客系统</span>
        <span class="kh-footer__meta">静态演示 demo · 概念展示用</span>
      </div>
    </div>
  </footer>
</template>

<style scoped>
.kh-footer {
  margin-top: var(--kh-space-16);
  background: var(--kh-surface);
  border-top: 1px solid var(--kh-border-soft);
}
.kh-footer__inner {
  display: flex;
  gap: var(--kh-space-12);
  padding-top: var(--kh-space-12);
  padding-bottom: var(--kh-space-10);
}
.kh-footer__brand {
  flex: 1.4;
  max-width: 360px;
}
.kh-footer__logo {
  font-family: var(--kh-font-display);
  font-size: var(--kh-font-size-xl);
  font-weight: 700;
  color: var(--kh-text);
}
.kh-footer__desc {
  margin-top: var(--kh-space-3);
  color: var(--kh-text-secondary);
  font-size: var(--kh-font-size-sm);
  line-height: 1.7;
}
.kh-footer__tags {
  display: flex;
  gap: 8px;
  margin-top: var(--kh-space-4);
  flex-wrap: wrap;
}
.kh-footer__tags span {
  font-size: 11px;
  padding: 3px 10px;
  border-radius: var(--kh-radius-pill);
  background: var(--kh-primary-soft);
  color: var(--kh-primary-strong);
  font-weight: 500;
}
.kh-footer__groups {
  display: flex;
  gap: var(--kh-space-12);
  flex: 1;
  justify-content: flex-end;
}
.kh-footer__group {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 96px;
}
.kh-footer__group h4 {
  font-size: var(--kh-font-size-sm);
  color: var(--kh-text);
  margin-bottom: 4px;
}
.kh-footer__group a {
  font-size: var(--kh-font-size-sm);
  color: var(--kh-text-secondary);
  cursor: pointer;
  transition: color var(--kh-transition-fast);
}
.kh-footer__group a:hover {
  color: var(--kh-primary);
}
.kh-footer__bar {
  border-top: 1px solid var(--kh-border-soft);
  background: var(--kh-surface-muted);
}
.kh-footer__bar-inner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 52px;
  font-size: 12px;
  color: var(--kh-text-tertiary);
}
.kh-footer__meta {
  font-family: var(--kh-font-mono);
}

@media (max-width: 768px) {
  .kh-footer__inner {
    flex-direction: column;
  }
  .kh-footer__groups {
    justify-content: flex-start;
    flex-wrap: wrap;
  }
}
</style>
