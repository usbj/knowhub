<script setup lang="ts">
/**
 * 文件作用：
 * 为尚未接入真实业务组件的动态菜单提供最小承载页，
 * 并直接展示当前路由对应的页面标题和组件定位信息。
 */
import { useRoute } from 'vue-router'
import { computed } from 'vue'
import BaseCard from '@/components/BaseCard.vue'

const route = useRoute()

/**
 * 方法效果：
 * 读取当前路由标题，作为占位页主标题展示。
 * 参数：
 * - 无，直接从当前路由元信息中取值。
 * 返回值：
 * - 当前页面标题；如果未配置标题则返回默认值。
 */
const title = computed(() => (route.meta.title as string) || '页面')

/**
 * 方法效果：
 * 读取当前路由描述，作为占位页辅助说明展示。
 * 参数：
 * - 无，直接从当前路由元信息中取值。
 * 返回值：
 * - 当前页面描述；如果未配置描述则返回默认值。
 */
const description = computed(
  () => (route.meta.description as string) || '当前页面已接入动态路由，但业务内容尚未绑定真实组件。',
)

/**
 * 方法效果：
 * 读取当前动态路由绑定的组件定位字段，方便排查后端菜单配置是否正确。
 * 参数：
 * - 无，直接从当前路由元信息中取值。
 * 返回值：
 * - 当前页面的组件路径；如果未配置则返回提示文案。
 */
const componentPath = computed(
  () => (route.meta.componentPath as string) || '当前菜单未配置组件路径，系统已回退到占位页。',
)
</script>

<template>
  <!-- 页面占位内容区域 -->
  <section class="placeholder-view">
    <BaseCard class="placeholder-view__hero">
      <span class="placeholder-view__tag">Dynamic Route</span>
      <h2>{{ title }}</h2>
      <p>{{ description }}</p>
      <dl class="placeholder-view__meta">
        <div>
          <dt>组件路径</dt>
          <dd>{{ componentPath }}</dd>
        </div>
        <div>
          <dt>当前地址</dt>
          <dd>{{ route.path }}</dd>
        </div>
      </dl>
    </BaseCard>
  </section>
</template>

<style scoped>
.placeholder-view {
  display: grid;
  gap: 18px;
}

.placeholder-view__tag {
  display: inline-flex;
  margin-bottom: 10px;
  padding: 5px 10px;
  border-radius: 999px;
  background: var(--rookie-primary-soft);
  color: var(--rookie-primary-strong);
  font-size: var(--rookie-font-size-xs);
  font-weight: 700;
}

.placeholder-view__hero h2 {
  font-size: var(--rookie-font-size-2xl);
  line-height: 1.2;
  font-weight: 700;
  margin-bottom: 8px;
}

.placeholder-view__hero p {
  max-width: 620px;
  color: var(--rookie-text-secondary);
}

.placeholder-view__meta {
  margin: 18px 0 0;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.placeholder-view__meta dt {
  margin-bottom: 8px;
  color: var(--rookie-text-tertiary);
  font-size: var(--rookie-font-size-sm);
}

.placeholder-view__meta dd {
  margin: 0;
  line-height: 1.6;
  color: var(--rookie-text);
}

@media (max-width: 1024px) {
  .placeholder-view__meta {
    grid-template-columns: 1fr;
  }
}
</style>
