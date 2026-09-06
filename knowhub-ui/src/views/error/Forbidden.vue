<!--
  403 Forbidden —— 无权访问
  ------------------------------------------------------------------
  专门命名路由页（router.push({ name: 'forbidden' }) 可达）。
  与 NotFound 同套风格，大号 403 + 锁态文案 + 回首页/返回按钮。
  bare:true 路由——无权访问页也无须顶栏导航，全屏居中防卡死。
-->
<script setup lang="ts">
import { useRouter } from 'vue-router'
import KhIcon from '@/components/common/KhIcon.vue'

const router = useRouter()
const goHome = () => router.push('/')
</script>

<template>
  <div class="kh-error kh-error--forbidden">
    <div class="kh-error__art" aria-hidden="true">
      <div class="kh-error__circle" />
      <div class="kh-error__code">403</div>
      <div class="kh-error__dot" />
    </div>
    <h1 class="kh-error__title">
      <KhIcon name="lock" :size="22" :stroke="1.6" /> 无权访问
    </h1>
    <p class="kh-error__desc">你没有访问此页面的权限，或登录态已失效。尝试重新登录，或回首页继续浏览公开内容。</p>
    <div class="kh-error__actions">
      <button class="kh-error__btn kh-error__btn--primary" type="button" @click="goHome">
        <KhIcon name="home" :size="14" /> 回首页
      </button>
      <button class="kh-error__btn" type="button" @click="router.push('/login')">去登录</button>
    </div>
  </div>
</template>

<style scoped>
.kh-error {
  min-height: calc(100vh - var(--kh-header-height));
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: var(--kh-space-12) var(--kh-space-6);
  background: var(--kh-bg);
}
.kh-error__art {
  position: relative;
  width: 200px;
  height: 140px;
  margin-bottom: var(--kh-space-6);
}
.kh-error__circle {
  position: absolute;
  left: 24px;
  bottom: 0;
  width: 96px;
  height: 96px;
  border-radius: 50%;
  background: var(--kh-gradient-warm);
  opacity: 0.5;
}
.kh-error__code {
  position: absolute;
  right: 8px;
  bottom: 10px;
  font-family: var(--kh-font-display);
  font-size: 88px;
  font-weight: 800;
  line-height: 1;
  letter-spacing: -0.02em;
  background: var(--kh-gradient-card);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  text-shadow: 0 4px 24px rgba(0, 0, 0, 0.08);
}
.kh-error__dot {
  position: absolute;
  right: 0;
  top: 6px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--kh-accent);
  opacity: 0.55;
}
.kh-error__title {
  display: inline-flex;
  align-items: center;
  gap: var(--kh-space-2);
  font-size: var(--kh-font-size-2xl);
  font-weight: 700;
  color: var(--kh-text);
  margin-bottom: var(--kh-space-2);
}
.kh-error__desc {
  font-size: var(--kh-font-size-sm);
  color: var(--kh-text-tertiary);
  max-width: 420px;
  line-height: 1.7;
  margin: 0 0 var(--kh-space-6);
}
.kh-error__actions {
  display: flex;
  gap: var(--kh-space-3);
}
.kh-error__btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 40px;
  padding: 0 var(--kh-space-5);
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius-pill);
  background: var(--kh-surface);
  color: var(--kh-text-secondary);
  font-size: var(--kh-font-size-sm);
  font-weight: 500;
  cursor: pointer;
  transition: all var(--kh-transition-fast);
}
.kh-error__btn:hover {
  border-color: var(--kh-primary-border);
  color: var(--kh-primary);
  background: var(--kh-primary-soft);
}
.kh-error__btn--primary {
  border: none;
  background: linear-gradient(120deg, var(--kh-primary), var(--kh-primary-strong));
  color: #fff;
  font-weight: 600;
  box-shadow: var(--kh-shadow-primary);
}
.kh-error__btn--primary:hover {
  color: #fff;
  background: linear-gradient(120deg, var(--kh-primary-strong), var(--kh-primary));
}
</style>