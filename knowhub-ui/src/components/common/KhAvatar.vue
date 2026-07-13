<!--
  KhAvatar —— 头像 + 头像组
  ------------------------------------------------------------------
  支持文字首字与图片两种；支持 group（重叠展示，+N 溢出）。
-->
<script setup lang="ts">
import { computed } from 'vue'

interface AvatarItem {
  /** 显示文字（首字）或图片 url */
  label?: string
  src?: string
  /** 头像底色，用于无图时区分 */
  color?: string
}

const props = withDefaults(
  defineProps<{
    /** 单头像：label/src；头像组：传数组 */
    item?: AvatarItem
    items?: AvatarItem[]
    size?: number
    /** 头像组最多展示几个，超出 +N */
    max?: number
    /** 头像组重叠间距（px） */
    overlap?: number
  }>(),
  {
    size: 32,
    max: 4,
    overlap: 8,
  },
)

const palette = ['#2563eb', '#0ea5e9', '#f59e0b', '#16a34a', '#6366f1', '#dc2626', '#0f766e', '#db2777']
const colorFor = (i: number) => palette[i % palette.length]

/** 头像组实际展示 + 溢出计数 */
const group = computed(() => {
  const list = props.items ?? []
  return {
    shown: list.slice(0, props.max),
    extra: Math.max(0, list.length - props.max),
  }
})
</script>

<template>
  <!-- 单头像 -->
  <span
    v-if="item && !items"
    class="kh-avatar"
    :style="{ width: `${size}px`, height: `${size}px`, fontSize: `${size * 0.42}px`, background: item.color ?? colorFor((item.label ?? '').charCodeAt(0) ?? 0) }"
  >
    <img v-if="item.src" :src="item.src" :alt="item.label ?? ''" />
    <template v-else>{{ (item.label ?? '?').slice(0, 1) }}</template>
  </span>

  <!-- 头像组 -->
  <span v-else class="kh-avatar-group">
    <span
      v-for="(it, idx) in group.shown"
      :key="idx"
      class="kh-avatar kh-avatar--group-item"
      :style="{
        width: `${size}px`,
        height: `${size}px`,
        fontSize: `${size * 0.42}px`,
        marginLeft: idx === 0 ? 0 : `-${overlap}px`,
        background: it.color ?? colorFor((it.label ?? '').charCodeAt(0) ?? 0),
        zIndex: group.shown.length - idx,
      }"
    >
      <img v-if="it.src" :src="it.src" :alt="it.label ?? ''" />
      <template v-else>{{ (it.label ?? '?').slice(0, 1) }}</template>
    </span>
    <span
      v-if="group.extra > 0"
      class="kh-avatar kh-avatar--more"
      :style="{ width: `${size}px`, height: `${size}px`, fontSize: `${size * 0.36}px`, marginLeft: `-${overlap}px` }"
    >+{{ group.extra }}</span>
  </span>
</template>

<style scoped>
.kh-avatar {
  display: inline-grid;
  place-items: center;
  border-radius: 50%;
  color: #fff;
  font-weight: 600;
  overflow: hidden;
  flex: none;
  border: 2px solid var(--kh-surface);
  box-shadow: var(--kh-shadow-xs);
  user-select: none;
}
.kh-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.kh-avatar-group {
  display: inline-flex;
  align-items: center;
}
.kh-avatar--more {
  background: var(--kh-bg-soft);
  color: var(--kh-text-secondary);
  border-color: var(--kh-border);
}
</style>
