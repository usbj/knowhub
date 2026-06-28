<script setup lang="ts">
defineProps<{
  viewKey: string
}>()
</script>

<template>
  <!-- 主内容展示区域 -->
  <main id="rookie-main" class="main-content-shell">
    <RouterView v-slot="{ Component }">
      <Transition name="main-content-switch" mode="out-in">
        <div :key="viewKey" class="main-content-shell__stage">
          <component :is="Component" />
        </div>
      </Transition>
    </RouterView>
  </main>
</template>

<style scoped>
.main-content-shell {
  position: relative;
  flex: 1;
  min-width: 0;
  min-height: 0;
  padding: 16px 24px 24px;
  overflow: auto;
}

.main-content-shell__stage {
  min-width: 0;
  min-height: 100%;
}

.main-content-switch-enter-active,
.main-content-switch-leave-active {
  transition:
    opacity 0.26s ease,
    transform 0.26s ease;
}

.main-content-switch-enter-from {
  opacity: 0;
  transform: translateX(-20px);
}

.main-content-switch-leave-to {
  opacity: 0;
  transform: translateX(20px);
}

@media (max-width: 1024px) {
  .main-content-shell {
    padding: 18px;
  }
}
</style>
