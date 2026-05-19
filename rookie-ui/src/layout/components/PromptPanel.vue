<script setup lang="ts">
import { Bell, CloseBold, Warning } from '@element-plus/icons-vue'
import type { PromptPanelProps } from '@/types/components/prompt'

withDefaults(defineProps<PromptPanelProps>(), {
  cancelAction: undefined,
  confirmAction: undefined,
  noticeMeta: undefined,
})

const emit = defineEmits<{
  close: []
  action: [value: string]
}>()

const handleAction = (value: string) => {
  emit('action', value)
}
</script>

<template>
  <Teleport to="body">
    <div v-if="visible" class="prompt-panel">
      <div class="prompt-panel__backdrop" @click="emit('close')" />
      <section class="prompt-panel__dialog" role="dialog" aria-modal="true" :aria-label="title">
        <header class="prompt-panel__header">
          <div class="prompt-panel__header-copy">
            <span class="prompt-panel__icon">
              <Bell v-if="mode === 'notice'" />
              <Warning v-else />
            </span>
            <div class="prompt-panel__title-wrap">
              <h3>{{ title }}</h3>
              <p v-if="mode === 'notice' && noticeMeta" class="prompt-panel__notice-meta">
                <span>{{ noticeMeta.publisher }}</span>
                <span>{{ noticeMeta.publishTime }}</span>
                <span v-if="noticeMeta.category">{{ noticeMeta.category }}</span>
              </p>
            </div>
          </div>

          <button class="prompt-panel__close" type="button" aria-label="关闭提示" @click="emit('close')">
            <CloseBold />
          </button>
        </header>

        <div class="prompt-panel__body">
          <p>{{ content }}</p>
        </div>

        <footer v-if="mode === 'prompt'" class="prompt-panel__footer">
          <button
            v-if="cancelAction"
            class="prompt-panel__button"
            type="button"
            @click="handleAction(cancelAction.value)"
          >
            {{ cancelAction.label }}
          </button>

          <button
            v-if="confirmAction"
            class="prompt-panel__button"
            :class="{ 'is-primary': confirmAction.tone === 'primary', 'is-danger': confirmAction.tone === 'danger' }"
            type="button"
            @click="handleAction(confirmAction.value)"
          >
            {{ confirmAction.label }}
          </button>
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.prompt-panel {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: grid;
  place-items: center;
  padding: 24px;
}

.prompt-panel__backdrop {
  position: absolute;
  inset: 0;
  background: color-mix(in srgb, var(--rookie-bg) 34%, rgba(7, 12, 24, 0.82));
  backdrop-filter: blur(4px);
}

.prompt-panel__dialog {
  position: relative;
  z-index: 1;
  width: min(560px, calc(100vw - 32px));
  display: grid;
  gap: 20px;
  padding: 24px;
  border: 1px solid var(--rookie-border);
  border-radius: var(--rookie-radius-lg);
  background: var(--rookie-card-bg);
  box-shadow: var(--rookie-shadow);
}

.prompt-panel__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.prompt-panel__header-copy {
  min-width: 0;
  display: flex;
  align-items: flex-start;
  gap: 14px;
}

.prompt-panel__icon {
  width: 40px;
  height: 40px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--rookie-radius-md);
  background: var(--rookie-primary-soft);
  color: var(--rookie-primary);
  flex: none;
}

.prompt-panel__icon :deep(svg) {
  width: 18px;
  height: 18px;
}

.prompt-panel__title-wrap {
  display: grid;
  gap: 6px;
}

.prompt-panel__title-wrap h3 {
  margin: 0;
  color: var(--rookie-text);
  font-size: var(--rookie-font-size-xl);
  line-height: 1.25;
}

.prompt-panel__notice-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin: 0;
  color: var(--rookie-text-tertiary);
  font-size: var(--rookie-font-size-sm);
}

.prompt-panel__close {
  width: 36px;
  height: 36px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid transparent;
  border-radius: var(--rookie-radius-md);
  background: transparent;
  color: var(--rookie-text-tertiary);
  cursor: pointer;
}

.prompt-panel__close:hover {
  background: var(--rookie-hover-bg);
  color: var(--rookie-text);
}

.prompt-panel__close :deep(svg) {
  width: 16px;
  height: 16px;
}

.prompt-panel__body p {
  margin: 0;
  color: var(--rookie-text-secondary);
  font-size: var(--rookie-font-size-md);
  line-height: 1.8;
  white-space: pre-wrap;
}

.prompt-panel__footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.prompt-panel__button {
  min-width: 88px;
  height: 40px;
  padding: 0 16px;
  border: 1px solid var(--rookie-border);
  border-radius: var(--rookie-radius-md);
  background: var(--rookie-surface-weak);
  color: var(--rookie-text-secondary);
  cursor: pointer;
}

.prompt-panel__button:hover {
  background: var(--rookie-hover-bg);
  color: var(--rookie-text);
}

.prompt-panel__button.is-primary {
  border-color: var(--rookie-primary-border);
  background: var(--rookie-primary-soft);
  color: var(--rookie-primary-strong);
}

.prompt-panel__button.is-danger {
  border-color: var(--rookie-danger-border);
  background: var(--rookie-danger-soft);
  color: var(--rookie-danger);
}
</style>
