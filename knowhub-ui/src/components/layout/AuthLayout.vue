<!--
  AuthLayout —— 登录/注册共用壳体
  ------------------------------------------------------------------
  左侧品牌叙事区（渐变 + 卖点 + 装饰） / 右侧表单卡。
  demo 阶段不接鉴权，表单仅做外形与校验占位。
-->
<script setup lang="ts">
import { RouterLink } from 'vue-router'

withDefaults(
  defineProps<{
    /** 右侧标题 */
    title?: string
    /** 标题下副文案 */
    subtitle?: string
    /** 底部切换链接文字（如"还没账号？去注册"） */
    switchText?: string
    /** 切换链接目标路由 */
    switchTo?: string
    /** 切换链接高亮文案（如"去注册"） */
    switchAction?: string
  }>(),
  {
    title: '欢迎回到知枢',
    subtitle: '登录后即可发布博客、管理项目、上传资源',
    switchText: '',
    switchTo: '/',
    switchAction: '',
  },
)

/** 左侧卖点：与前 4 个内容板块呼应 */
const points = [
  { icon: 'blog', title: '博客笔记', desc: '沉淀踩过的坑，分享走过的路' },
  { icon: 'project', title: '项目展示', desc: '把做过的项目摆上台面，让成果被看见' },
  { icon: 'resource', title: '资源推荐', desc: '精选好物与工具，实验室共建共享' },
  { icon: 'doc', title: '文档学习', desc: '系统化进阶，章节集合型文档导航' },
] as const
</script>

<template>
  <div class="auth">
    <!-- 左：品牌叙事 -->
    <aside class="auth__brand">
      <div class="auth__brand-bg">
        <div class="auth__blob auth__blob--1" />
        <div class="auth__blob auth__blob--2" />
      </div>
      <RouterLink to="/" class="auth__logo">
        <span class="auth__logo-mark">
          <img src="@/assets/image/logo.png" alt="知枢 logo" width="38" height="38" />
        </span>
        <span class="auth__logo-text">
          <span class="auth__logo-name">知枢</span>
          <span class="auth__logo-sub">knowhub</span>
        </span>
      </RouterLink>

      <div class="auth__brand-text">
        <h2 class="auth__brand-title">
          学以<span class="auth__grad">记之</span>，<br />研以<span class="auth__grad">展之</span>
        </h2>
        <p class="auth__brand-desc">
          面向高校实验室与中小组织的综合知识库。让实验室的智慧被看见、被复用、被传承。
        </p>

        <ul class="auth__points">
          <li v-for="p in points" :key="p.title" class="auth__point">
            <span class="auth__point-icon">
              <KhIcon :name="p.icon" :size="18" />
            </span>
            <div class="auth__point-text">
              <div class="auth__point-title">{{ p.title }}</div>
              <div class="auth__point-desc">{{ p.desc }}</div>
            </div>
          </li>
        </ul>
      </div>

      <div class="auth__brand-foot">© 2026 知枢 knowhub · 实验室知识沉淀与分享</div>
    </aside>

    <!-- 右：表单区 -->
    <main class="auth__panel">
      <div class="auth__card">
        <header class="auth__head">
          <h1 class="auth__title">{{ title }}</h1>
          <p class="auth__subtitle">{{ subtitle }}</p>
        </header>

        <slot />

        <footer v-if="switchText || switchAction" class="auth__switch">
          <span v-if="switchText">{{ switchText }}</span>
          <RouterLink v-if="switchAction" :to="switchTo" class="auth__switch-action">{{ switchAction }}</RouterLink>
        </footer>
      </div>
    </main>
  </div>
</template>

<style scoped>
.auth {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 1fr 1fr;
}

/* —— 左侧品牌区 —— */
.auth__brand {
  position: relative;
  display: flex;
  flex-direction: column;
  padding: var(--kh-space-10) var(--kh-space-12);
  background: var(--kh-gradient-hero);
  overflow: hidden;
}
.auth__brand-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
}
.auth__blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.45;
  will-change: transform;
}
.auth__blob--1 {
  width: 360px;
  height: 360px;
  background: rgba(37, 99, 235, 0.3);
  top: -120px;
  right: -60px;
  animation: kh-auth-blob-1 18s ease-in-out infinite;
}
.auth__blob--2 {
  width: 280px;
  height: 280px;
  background: rgba(245, 158, 11, 0.22);
  bottom: -100px;
  left: -40px;
  animation: kh-auth-blob-2 22s ease-in-out infinite;
}
@keyframes kh-auth-blob-1 {
  0%, 100% { transform: translate3d(0, 0, 0) scale(1); }
  50% { transform: translate3d(-20px, 24px, 0) scale(1.06); }
}
@keyframes kh-auth-blob-2 {
  0%, 100% { transform: translate3d(0, 0, 0) scale(1); }
  50% { transform: translate3d(22px, -18px, 0) scale(1.05); }
}

.auth__logo {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: var(--kh-text);
  flex: none;
}
.auth__logo-mark {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
}
.auth__logo-text {
  display: flex;
  flex-direction: column;
  line-height: 1.05;
}
.auth__logo-name {
  font-family: var(--kh-font-display);
  font-weight: 700;
  font-size: var(--kh-font-size-xl);
}
.auth__logo-sub {
  font-family: var(--kh-font-mono);
  font-size: 11px;
  letter-spacing: 0.08em;
  color: var(--kh-text-tertiary);
  text-transform: uppercase;
}

.auth__brand-text {
  position: relative;
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: var(--kh-space-8) 0;
}
.auth__brand-title {
  font-family: var(--kh-font-display);
  font-size: var(--kh-font-size-5xl);
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1.1;
  color: var(--kh-text);
}
.auth__grad {
  background: linear-gradient(120deg, var(--kh-primary), var(--kh-accent) 50%, var(--kh-warm));
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.auth__brand-desc {
  margin-top: var(--kh-space-5);
  font-size: var(--kh-font-size-md);
  color: var(--kh-text-secondary);
  line-height: 1.7;
  max-width: 380px;
}

.auth__points {
  list-style: none;
  margin: var(--kh-space-8) 0 0;
  padding: 0;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--kh-space-4);
  max-width: 460px;
}
.auth__point {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: var(--kh-space-3);
  background: color-mix(in srgb, var(--kh-surface) 70%, transparent);
  border: 1px solid var(--kh-border-soft);
  border-radius: var(--kh-radius);
  backdrop-filter: blur(6px);
}
.auth__point-icon {
  width: 34px;
  height: 34px;
  border-radius: var(--kh-radius-sm);
  display: grid;
  place-items: center;
  flex: none;
  color: var(--kh-primary);
  background: var(--kh-primary-soft);
}
.auth__point:nth-child(2) .auth__point-icon {
  color: var(--kh-accent);
  background: var(--kh-accent-soft);
}
.auth__point:nth-child(3) .auth__point-icon {
  color: var(--kh-warm);
  background: var(--kh-warm-soft);
}
.auth__point:nth-child(4) .auth__point-icon {
  color: var(--kh-success);
  background: var(--kh-success-soft);
}
.auth__point-title {
  font-size: var(--kh-font-size-sm);
  font-weight: 600;
  color: var(--kh-text);
}
.auth__point-desc {
  margin-top: 2px;
  font-size: 11px;
  color: var(--kh-text-tertiary);
  line-height: 1.5;
}
.auth__brand-foot {
  position: relative;
  font-size: 11px;
  color: var(--kh-text-tertiary);
  font-family: var(--kh-font-mono);
}

/* —— 右侧表单区 —— */
.auth__panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--kh-space-8);
  background: var(--kh-bg);
}
.auth__card {
  width: 100%;
  max-width: 400px;
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-6);
}
.auth__head {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.auth__title {
  font-family: var(--kh-font-display);
  font-size: var(--kh-font-size-3xl);
  font-weight: 700;
  color: var(--kh-text);
  letter-spacing: -0.01em;
}
.auth__subtitle {
  font-size: var(--kh-font-size-sm);
  color: var(--kh-text-secondary);
}
.auth__switch {
  display: flex;
  align-items: center;
  gap: 6px;
  justify-content: center;
  font-size: var(--kh-font-size-sm);
  color: var(--kh-text-secondary);
}
.auth__switch-action {
  color: var(--kh-primary);
  font-weight: 600;
  cursor: pointer;
}
.auth__switch-action:hover {
  text-decoration: underline;
}

/* —— 响应式：窄屏隐藏左侧品牌区 —— */
@media (max-width: 960px) {
  .auth {
    grid-template-columns: 1fr;
  }
  .auth__brand {
    display: none;
  }
  .auth__panel {
    min-height: 100vh;
  }
}

@media (prefers-reduced-motion: reduce) {
  .auth__blob--1,
  .auth__blob--2 {
    animation: none;
  }
}
</style>