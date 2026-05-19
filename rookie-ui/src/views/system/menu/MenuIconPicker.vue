<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElButton, ElEmpty, ElIcon, ElInput, ElPopover, ElScrollbar } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { menuIconOptions, resolveCanonicalMenuIconCode, resolveMenuIconComponent } from '@/utils/menu-icons'

const props = withDefaults(
  defineProps<{
    modelValue?: string
    placeholder?: string
    disabled?: boolean
  }>(),
  {
    modelValue: '',
    placeholder: '请选择图标',
    disabled: false,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const popoverVisible = ref(false)
const keyword = ref('')

const currentIconCode = computed(() => resolveCanonicalMenuIconCode(props.modelValue))
const currentIconComponent = computed(() => resolveMenuIconComponent(props.modelValue))

const filteredIconOptions = computed(() => {
  const normalizedKeyword = keyword.value.trim().toLowerCase()

  if (!normalizedKeyword) {
    return menuIconOptions
  }

  return menuIconOptions.filter((item) =>
    item.keywords.some((currentKeyword) => currentKeyword.includes(normalizedKeyword)),
  )
})

const handleSelectIcon = (iconCode: string) => {
  emit('update:modelValue', iconCode)
  popoverVisible.value = false
}

const handleClearIcon = () => {
  emit('update:modelValue', '')
}
</script>

<template>
  <ElPopover
    v-model:visible="popoverVisible"
    placement="bottom-start"
    :width="560"
    trigger="click"
    :disabled="disabled"
    popper-class="menu-icon-picker__popover"
  >
    <template #reference>
      <div class="menu-icon-picker">
        <ElButton class="menu-icon-picker__trigger" :disabled="disabled">
          <span class="menu-icon-picker__trigger-main">
            <ElIcon class="menu-icon-picker__preview">
              <component :is="currentIconComponent" />
            </ElIcon>
            <span class="menu-icon-picker__value">
              {{ currentIconCode }}
            </span>
          </span>
          <span class="menu-icon-picker__actions">
            <span class="menu-icon-picker__action-text">选择</span>
          </span>
        </ElButton>
      </div>
    </template>

    <div class="menu-icon-picker__panel">
      <ElInput
        v-model="keyword"
        class="menu-icon-picker__search"
        :placeholder="placeholder"
        clearable
      >
        <template #prefix>
          <ElIcon>
            <Search />
          </ElIcon>
        </template>
      </ElInput>

      <div class="menu-icon-picker__toolbar">
        <span class="menu-icon-picker__current">
          当前：{{ currentIconCode }}
        </span>
        <ElButton text @click="handleClearIcon">清空</ElButton>
      </div>

      <ElScrollbar max-height="320px">
        <div v-if="filteredIconOptions.length > 0" class="menu-icon-picker__grid">
          <button
            v-for="iconOption in filteredIconOptions"
            :key="iconOption.value"
            type="button"
            class="menu-icon-picker__item"
            :class="{ 'is-active': iconOption.value === currentIconCode }"
            @click="handleSelectIcon(iconOption.value)"
          >
            <ElIcon class="menu-icon-picker__item-icon">
              <component :is="iconOption.component" />
            </ElIcon>
            <span class="menu-icon-picker__item-label">{{ iconOption.label }}</span>
          </button>
        </div>

        <ElEmpty v-else description="未找到匹配图标" :image-size="72" />
      </ElScrollbar>
    </div>
  </ElPopover>
</template>

<style scoped>
.menu-icon-picker,
.menu-icon-picker__trigger {
  width: 100%;
}

.menu-icon-picker__trigger {
  justify-content: space-between;
  padding: 0 14px;
  height: 40px;
  border-color: var(--rookie-border);
  background: var(--rookie-surface);
  color: var(--rookie-text);
}

.menu-icon-picker__trigger-main {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.menu-icon-picker__preview,
.menu-icon-picker__item-icon {
  font-size: 18px;
}

.menu-icon-picker__preview {
  color: var(--rookie-text-secondary);
}

.menu-icon-picker__value {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.menu-icon-picker__action-text,
.menu-icon-picker__current {
  color: var(--rookie-text-secondary);
}

.menu-icon-picker__panel {
  display: grid;
  gap: 12px;
}

.menu-icon-picker__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.menu-icon-picker__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.menu-icon-picker__item {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 40px;
  padding: 8px 10px;
  border: 1px solid var(--rookie-border);
  border-radius: 8px;
  background: var(--rookie-surface);
  color: var(--rookie-text);
  cursor: pointer;
  text-align: left;
}

.menu-icon-picker__item:hover,
.menu-icon-picker__item.is-active {
  border-color: var(--rookie-primary);
  background: var(--rookie-primary-soft);
}

.menu-icon-picker__item-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:global(.menu-icon-picker__popover.el-popover) {
  padding: 14px;
  border: 1px solid var(--rookie-border);
  border-radius: 12px;
  background: var(--rookie-card-bg);
  box-shadow: var(--rookie-shadow);
}

:global(.menu-icon-picker__popover.el-popper .el-popper__arrow::before) {
  border-color: var(--rookie-border);
  background: var(--rookie-card-bg);
}

:global(.menu-icon-picker__popover .el-input__wrapper) {
  background: var(--rookie-surface-muted);
  box-shadow: 0 0 0 1px var(--rookie-border) inset;
}

:global(.menu-icon-picker__popover .el-input__inner),
:global(.menu-icon-picker__popover .el-input__prefix),
:global(.menu-icon-picker__popover .el-input__suffix),
:global(.menu-icon-picker__popover .el-button) {
  color: var(--rookie-text);
}

:global(.menu-icon-picker__popover .el-scrollbar__thumb) {
  background: color-mix(in srgb, var(--rookie-text-tertiary) 70%, transparent);
}

:global(.menu-icon-picker__popover .el-empty__description p) {
  color: var(--rookie-text-secondary);
}
</style>
