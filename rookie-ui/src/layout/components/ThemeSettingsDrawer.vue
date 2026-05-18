<script setup lang="ts">
import { RefreshLeft } from '@element-plus/icons-vue'
import { ElButton, ElColorPicker, ElDrawer, ElSlider, ElSwitch } from 'element-plus'
import BaseCard from '@/components/BaseCard.vue'
import { useThemePreferenceStore } from '@/stores/themePreference'

const visible = defineModel<boolean>('visible', { required: true })
const themePreferenceStore = useThemePreferenceStore()

/**
 * 预设主题色按钮点击后，直接回写到主题 store。
 */
const applyPreset = (color: string) => {
  themePreferenceStore.settings.primaryColor = color
}
</script>

<template>
  <!-- 界面设置抽屉区域 -->
  <ElDrawer v-model="visible" title="界面设置" size="360px">
    <div class="theme-settings-drawer">
      <BaseCard title="基础主题" description="控制整体明暗风格">
        <div class="theme-settings-drawer__row">
          <span>深色模式</span>
          <ElSwitch
            v-model="themePreferenceStore.settings.mode"
            active-value="dark"
            inactive-value="light"
          />
        </div>
      </BaseCard>

      <BaseCard title="内部主题" description="影响菜单、标签和焦点颜色">
        <div class="theme-settings-drawer__presets">
          <button
            v-for="preset in themePreferenceStore.presets"
            :key="preset.name"
            class="theme-settings-drawer__preset"
            type="button"
            :class="{ 'is-active': themePreferenceStore.settings.primaryColor === preset.color }"
            @click="applyPreset(preset.color)"
          >
            <span :style="{ backgroundColor: preset.color }"></span>
            <strong>{{ preset.name }}</strong>
          </button>
        </div>

        <div class="theme-settings-drawer__field">
          <span>自定义主色</span>
          <ElColorPicker v-model="themePreferenceStore.settings.primaryColor" />
        </div>
      </BaseCard>

      <BaseCard title="阅读与圆角" description="控制字号和卡片圆角">
        <div class="theme-settings-drawer__field theme-settings-drawer__field--stack">
          <div class="theme-settings-drawer__field-label">
            <span>基础字号</span>
            <strong>{{ themePreferenceStore.settings.fontSize }}px</strong>
          </div>
          <ElSlider v-model="themePreferenceStore.settings.fontSize" :min="12" :max="18" />
        </div>

        <div class="theme-settings-drawer__field theme-settings-drawer__field--stack">
          <div class="theme-settings-drawer__field-label">
            <span>基础圆角</span>
            <strong>{{ themePreferenceStore.settings.radius }}px</strong>
          </div>
          <ElSlider v-model="themePreferenceStore.settings.radius" :min="8" :max="24" />
        </div>
      </BaseCard>

      <div class="theme-settings-drawer__footer">
        <ElButton :icon="RefreshLeft" @click="themePreferenceStore.resetThemeSettings()">
          恢复默认
        </ElButton>
      </div>
    </div>
  </ElDrawer>
</template>

<style scoped>
.theme-settings-drawer {
  display: grid;
  gap: 1rem;
}

.theme-settings-drawer__row,
.theme-settings-drawer__field {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}

.theme-settings-drawer__field--stack {
  display: grid;
  gap: 0.75rem;
}

.theme-settings-drawer__field-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}

.theme-settings-drawer__presets {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.75rem;
  margin-bottom: 1rem;
}

.theme-settings-drawer__preset {
  display: flex;
  align-items: center;
  gap: 0.625rem;
  min-height: 2.625rem;
  padding: 0 0.75rem;
  border: 1px solid var(--rookie-border);
  border-radius: var(--rookie-radius-md);
  background: var(--rookie-card-bg);
  color: var(--rookie-text-secondary);
  cursor: pointer;
}

.theme-settings-drawer__preset.is-active {
  border-color: var(--rookie-primary);
  color: var(--rookie-primary-strong);
}

.theme-settings-drawer__preset span {
  width: 1rem;
  height: 1rem;
  border-radius: 999px;
  flex: none;
}

.theme-settings-drawer__footer {
  display: flex;
  justify-content: flex-end;
}
</style>
