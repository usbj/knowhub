<script setup lang="ts">
import type { BaseCardProps } from '@/types/components/card'

defineProps<BaseCardProps>()
</script>

<template>
  <!-- 通用卡片区域 -->
  <section class="base-card">
    <header v-if="title || description" class="base-card__header">
      <strong v-if="title" class="base-card__title">{{ title }}</strong>
      <p v-if="description" class="base-card__description">{{ description }}</p>
    </header>

    <div class="base-card__body">
      <slot />
    </div>
  </section>
</template>

<style scoped>
.base-card {
  border: 1px solid var(--rookie-border);
  border-radius: var(--rookie-radius-lg);
  background: var(--rookie-card-bg);
  box-shadow: var(--rookie-shadow);
}

.base-card__header {
  display: grid;
  gap: 0.5rem;
  padding: 1.5rem 1.5rem 0;
}

.base-card__title {
  color: var(--rookie-text);
  font-size: var(--rookie-font-size-lg);
  line-height: 1.3;
}

.base-card__description {
  color: var(--rookie-text-secondary);
  font-size: var(--rookie-font-size-sm);
  line-height: 1.5;
}

.base-card__body {
  padding: 1.5rem;
}
</style>
