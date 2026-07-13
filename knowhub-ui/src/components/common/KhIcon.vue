<!--
  KhIcon —— knowhub 前台内容/品牌图标组件
  ------------------------------------------------------------------
  统一手写 SVG（Lucide 风格 24×24 stroke），避免用 emoji 当 UI 图标。
  用法：<KhIcon name="blog" :size="20" />
  UI 操作图标（搜索/铃铛/用户等）直接用 @element-plus/icons-vue，不走本组件。
  新增图标只需在下方 pathMap 补一条 svg path 内容。
-->
<script setup lang="ts">
import { computed } from 'vue'

/** 支持的图标名枚举，新增图标在此登记 */
type IconName =
  | 'blog'
  | 'project'
  | 'resource'
  | 'doc'
  | 'tool'
  | 'link'
  | 'file'
  | 'script'
  | 'software'
  | 'sparkles'
  | 'fire'
  | 'trending'
  | 'book'
  | 'code'
  | 'trophy'
  | 'users'
  | 'tag'
  | 'search'
  | 'star'
  | 'eye'
  | 'heart'
  | 'bookmark'
  | 'download'
  | 'share'
  | 'clock'
  | 'calendar'
  | 'arrow-right'
  | 'arrow-up-right'
  | 'chevron-right'
  | 'more'
  | 'github'
  | 'gitee'
  | 'megaphone'
  | 'graduation'
  | 'flask'
  | 'lightbulb'

const props = withDefaults(
  defineProps<{
    /** 图标名；传未登记的值时安全回退为空图标 */
    name: IconName | (string & {})
    size?: number | string
    /** stroke 粗细，默认 1.8（Lucide 风） */
    stroke?: number
  }>(),
  {
    size: 20,
    stroke: 1.8,
  },
)

/** 图标 path 映射表：每个值为 inner SVG（不含外层 <svg>） */
const pathMap: Record<string, string> = {
  blog: '<path d="M4 19V5a2 2 0 0 1 2-2h9l5 5v11a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2z"/><path d="M14 3v5h5"/><path d="M8 13h8"/><path d="M8 17h5"/>',
  project:
    '<path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>',
  resource:
    '<path d="M21 15V6a2 2 0 0 0-2-2h-7l-2 2H5a2 2 0 0 0-2 2v3"/><path d="M3 11h18"/><path d="M19 18a3 3 0 1 0-2.9-2.25"/><path d="M19 18v2"/><path d="M19 22h.01"/>',
  doc: '<path d="M4 4a2 2 0 0 1 2-2h7l5 5v13a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2z"/><path d="M13 2v5h5"/><path d="M8 12h8"/><path d="M8 16h8"/><path d="M8 8h3"/>',
  tool: '<path d="M14.7 6.3a4 4 0 0 0-5.4 5.4L3 18l3 3 6.3-6.3a4 4 0 0 0 5.4-5.4l-2.1 2.1-2.4-2.4z"/>',
  link: '<path d="M10 13a5 5 0 0 0 7.5.5l3-3a5 5 0 0 0-7-7l-1.5 1.5"/><path d="M14 11a5 5 0 0 0-7.5-.5l-3 3a5 5 0 0 0 7 7l1.5-1.5"/>',
  file: '<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><path d="M14 2v6h6"/>',
  script: '<path d="M4 4a2 2 0 0 1 2-2h7l5 5v13a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2z"/><path d="M13 2v5h5"/><path d="m9 13 2 2-2 2"/><path d="m13 13 2 2-2 2"/>',
  software:
    '<rect x="3" y="4" width="18" height="16" rx="2"/><path d="M3 9h18"/><path d="M7 14h4"/>',
  sparkles:
    '<path d="M12 3v4M12 17v4M3 12h4M17 12h4"/><path d="m6.3 6.3 2.4 2.4M15.3 15.3l2.4 2.4M17.7 6.3l-2.4 2.4M8.7 15.3l-2.4 2.4"/>',
  fire: '<path d="M12 2s4 4 4 8a4 4 0 0 1-8 0c0-1 .5-2 1-2.5C9 9 12 8 12 2z"/><path d="M12 22a6 6 0 0 0 6-6c0-3-2-5-3-6-1 2-2 3-3 3s-2-1-3-3c-1 1-3 3-3 6a6 6 0 0 0 6 6z"/>',
  trending: '<path d="m3 17 6-6 4 4 8-8"/><path d="M14 7h7v7"/>',
  book: '<path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>',
  code: '<path d="m8 6-6 6 6 6"/><path d="m16 6 6 6-6 6"/>',
  trophy:
    '<path d="M7 4h10v4a5 5 0 0 1-10 0z"/><path d="M7 4H4v2a3 3 0 0 0 3 3"/><path d="M17 4h3v2a3 3 0 0 1-3 3"/><path d="M12 13v4"/><path d="M8 21h8"/><path d="M10 17h4l1 4H9z"/>',
  users:
    '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>',
  tag: '<path d="M3 7v6l8 8 6-6-8-8z"/><circle cx="7.5" cy="11.5" r="1.5"/>',
  search: '<circle cx="11" cy="11" r="7"/><path d="m21 21-4.3-4.3"/>',
  star: '<path d="m12 3 2.9 5.9 6.5.9-4.7 4.6 1.1 6.5L12 18.9 6.2 21l1.1-6.5L2.6 9.8l6.5-.9z"/>',
  eye: '<path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7z"/><circle cx="12" cy="12" r="3"/>',
  heart:
    '<path d="M19 5.5a5 5 0 0 0-7 0L12 6l-.5-.5a5 5 0 0 0-7 7L12 20l7.5-7.5a5 5 0 0 0 0-7z"/>',
  bookmark: '<path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"/>',
  download: '<path d="M12 3v12"/><path d="m7 10 5 5 5-5"/><path d="M5 21h14"/>',
  share: '<circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><path d="m8.6 13.5 6.8 4M15.4 6.5l-6.8 4"/>',
  clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
  calendar:
    '<rect x="3" y="4" width="18" height="18" rx="2"/><path d="M3 9h18M8 2v4M16 2v4"/>',
  'arrow-right': '<path d="M5 12h14"/><path d="m13 6 6 6-6 6"/>',
  'arrow-up-right': '<path d="M7 17 17 7"/><path d="M8 7h9v9"/>',
  'chevron-right': '<path d="m9 6 6 6-6 6"/>',
  more: '<circle cx="5" cy="12" r="1.5"/><circle cx="12" cy="12" r="1.5"/><circle cx="19" cy="12" r="1.5"/>',
  github:
    '<path d="M9 19c-5 1.5-5-2.5-7-3m14 6v-3.87a3.37 3.37 0 0 0-.94-2.61c3.14-.35 6.44-1.54 6.44-7A5.44 5.44 0 0 0 20 4.77 5.07 5.07 0 0 0 19.91 1S18.73.65 16 2.48a13.38 13.38 0 0 0-7 0C6.27.65 5.09 1 5.09 1A5.07 5.07 0 0 0 5 4.77a5.44 5.44 0 0 0-1.5 3.78c0 5.42 3.3 6.61 6.44 7A3.37 3.37 0 0 0 9 18.13V22"/>',
  gitee:
    '<circle cx="12" cy="12" r="9"/><path d="M15.5 9H9a2 2 0 0 0 0 4h4a2 2 0 0 1 0 4H8"/>',
  megaphone:
    '<path d="m3 11 14-7v16L3 13z"/><path d="M3 11v2a2 2 0 0 0 2 2h2"/><path d="M17 8a4 4 0 0 1 0 8"/>',
  graduation:
    '<path d="M22 10 12 5 2 10l10 5z"/><path d="M6 12v5c0 1 2 3 6 3s6-2 6-3v-5"/>',
  flask:
    '<path d="M9 3h6"/><path d="M10 3v6L4 19a2 2 0 0 0 2 3h12a2 2 0 0 0 2-3l-6-10V3"/>',
  lightbulb:
    '<path d="M9 18h6"/><path d="M10 22h4"/><path d="M12 2a7 7 0 0 0-4 12.7c.6.5 1 1.3 1 2.1V17h6v-.2c0-.8.4-1.6 1-2.1A7 7 0 0 0 12 2z"/>',
}

const inner = computed(() => pathMap[props.name] ?? '')
const sizeStr = computed(() => (typeof props.size === 'number' ? `${props.size}px` : props.size))
</script>

<template>
  <svg
    class="kh-icon"
    :width="sizeStr"
    :height="sizeStr"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    :stroke-width="stroke"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    focusable="false"
    v-html="inner"
  />
</template>

<style scoped>
.kh-icon {
  display: inline-block;
  flex: none;
  vertical-align: middle;
}
</style>
