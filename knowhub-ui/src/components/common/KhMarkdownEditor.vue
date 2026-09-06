<!--
  KhMarkdownEditor —— 通用 Markdown 编辑器（封装 @kangc/v-md-editor）
  ------------------------------------------------------------------
  把博客创作页 create.vue 的正文编辑区抽成可复用组件，供博客正文 / 文章前言 / 章节正文
  等一切需要 md 编辑的场景复用。具备一定扩展性与自定义属性。
  - v-model（content）双向绑定正文字符串。
  - v-model:mode（可选受控，'edit'|'preview'|'editable'）控制编辑/预览/对比态；不传则组件
    内部维护，仅 'edit'/'preview' 二态切（'editable' 对比留给 v-md-editor 自带小眼睛按钮，
    库内部 action 切 edit↔editable——两个控件职责不重叠，错乱不显，是已接受取舍）。
  - 右下角浮层胶囊按钮组（编辑/预览）默认显示，showModeSwitch=false 可隐藏（纯受控场景）。
  - 正文插图走预签名直传，businessType/access 可定制（默认 BLOG_BODY + PUBLIC）。
  - 默认 height="100%" 占满父容器（父须给明确高度：固定值或 flex:1 + min-height:0 链）；
    也可传 calc 字串（如 "calc(100vh - 320px)"）让组件根与编辑器都贴合该高度。
  - :deep 样式照搬博客：overflow visible 让工具栏 tooltip 可向上溢出，内部 main 自带滚动，
    工具栏/正文自带圆角贴外框防漏背景，tooltip 提层避免被其它浮层遮住。
  拓展点：
  - 想加自定义工具栏按钮：v-md-editor 支持 v-md-editor 自带 toolbar 配置，将来可加 toolbar prop 透传。
  - 想换 FileBusinessType：传 businessType（如将来后端加 CHAPTER_BODY 即可切换，无需改本组件）。
  - 想限定正文图访问模式：传 access（默认 PUBLIC，博客/文章/章节正文图均公开读语义）。
-->
<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { ComponentPublicInstance } from 'vue'
import { ElMessage } from 'element-plus'
import { presignedUploadFlow } from '@/utils/upload'
import { useMarkdownCodeBlock } from '@/composables/useMarkdownCodeBlock'
import { useMarkdownImageZoom } from '@/composables/useMarkdownImageZoom'

const props = withDefaults(
  defineProps<{
    /** 正文内容，v-model */
    modelValue: string
    /** 编辑器模式：'edit' 编辑 | 'preview' 预览 | 'editable' 对比（左右分屏） */
    mode?: 'edit' | 'preview' | 'editable'
    /** 占位提示 */
    placeholder?: string
    /**
     * 编辑器高度：传给 v-md-editor 的 height，同时作为组件根容器高度。
     * 默认 '100%' 占满父（父须有明确高度）；可传 calc 字串贴合视口余量。
     */
    height?: string
    /**
     * 正文插图上传的业务类型（FileBusinessType 枚举 code）。
     * 默认 'BLOG_BODY'（PUBLIC 公开读，Markdown 正文内插图通用语义）；
     * 将来后端加 CHAPTER_BODY 等可传值切换，无需改本组件。
     */
    businessType?: string
    /** 正文图访问模式，默认 'PUBLIC'（正文图需公开读，渲染时 <img> 直引） */
    access?: 'PUBLIC' | 'PRIVATE'
    /** 是否显示右下角编辑/预览浮层切换，默认 true */
    showModeSwitch?: boolean
    /** 透传给 v-md-editor 的 disabled-menus，默认空 */
    disabledMenus?: string[]
    /** 透传给 v-md-editor 的 upload-image-config，默认图片任意 + 10MB 上限 */
    uploadImageConfig?: { accept: string; maxFileSize: number }
  }>(),
  {
    mode: undefined,
    placeholder: '',
    height: '100%',
    businessType: 'BLOG_BODY',
    access: 'PUBLIC',
    showModeSwitch: true,
    disabledMenus: () => [],
    uploadImageConfig: () => ({ accept: 'image/*', maxFileSize: 10 * 1024 * 1024 }),
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'update:mode': [value: 'edit' | 'preview' | 'editable']
}>()

/**
 * 内部模式状态：mode 受控（外部传 mode）优先；否则内部自维护（仅 toggle edit/preview）。
 * 监听外部 mode 变化同步给内部；内部切时 emit update:mode 回外。
 */
const innerMode = ref<'edit' | 'preview' | 'editable'>(props.mode ?? 'edit')
watch(
  () => props.mode,
  (m) => {
    if (m && m !== innerMode.value) innerMode.value = m
  },
)

/** 实际传给 v-md-editor 的模式（拿内部 ref） */
const editorMode = computed(() => innerMode.value)

const switchMode = (m: 'edit' | 'preview') => {
  innerMode.value = m
  emit('update:mode', m)
}

/** 根容器高度样式（同时约束组件根与传给编辑器的 height） */
const rootStyle = computed(() => ({ height: props.height }))

const uploadCfg = computed(() =>
  props.uploadImageConfig ?? { accept: 'image/*', maxFileSize: 10 * 1024 * 1024 },
)

/**
 * v-md-editor upload-image 回调：串行预签名直传（businessType/access 可定制），
 * 成功 insertImage 插入 ![name](/file/resolve/{id})。
 */
const handleUploadImage = async (
  _event: Event,
  insertImage: (imageConfig: { name?: string; url: string }) => void,
  files: File[],
) => {
  if (!files || files.length === 0) return
  for (const file of files) {
    try {
      const result = await presignedUploadFlow({
        file,
        businessType: props.businessType,
        access: props.access,
      })
      if (result.publicUrl) {
        insertImage({ name: file.name, url: result.publicUrl })
      }
    } catch (error) {
      // eslint-disable-next-line no-console
      console.error('正文图片上传失败', file.name, error)
    }
  }
}

/** v-model 处理 */
const onInput = (val: string) => {
  emit('update:modelValue', val)
}

// ---- 编辑器预览区代码块工具栏/配图放大（与详情页同款 composable 接管 v-md-preview 站点）----
// v-md-editor 自带编辑/预览/对比模式，预览态/对比态右侧渲染 .v-md-editor__preview-wrapper > .github-markdown-body。
// 详情页用的是 <v-md-preview>（独立组件），其 .github-markdown-body 由 7 处详情页 contentRef 上的 composable 接管；
// 编辑器 <v-md-editor> 是另一条渲染链路，详情页 composable 不覆盖它 → 预览区代码块无工具栏、容器样式因 markdown.css
// 期望 .kh-code-header 而塌（fix：把 composable 也接到编辑器预览体上）。
// 预览体只在 preview/editable 模式存在（edit 模式是 textarea 无预览 DOM），且模式切换时 wrapper 会挂卸——
// 故 resolvePreviewBody 在挂载 + 模式切换 + 编辑器子树变化时反复查；contentRef watch + MutationObserver（composable 内）
// 已能响应「预览体出现后其内部 v-md-preview 异步渲染代码块」，这里只负责把 previewBodyRef 指到正确的预览 DOM。
const editorRef = ref<ComponentPublicInstance | null>(null)
/** 预览渲染体 ref：喂给 useMarkdownCodeBlock / useMarkdownImageZoom（与详情页 contentRef 同角色） */
const previewBodyRef = ref<HTMLElement | null>(null)

/** 从编辑器实例根查预览渲染体（.v-md-editor__preview-wrapper 内首个 .github-markdown-body）。
 *  edit 模式无预览 DOM → 返回 null（composable 收到 null 会断 observer，等预览体再出现时重接）。 */
const resolvePreviewBody = () => {
  const root = (editorRef.value?.$el as HTMLElement | undefined) ?? null
  if (!root) {
    previewBodyRef.value = null
    return
  }
  const body = root.querySelector<HTMLElement>('.v-md-editor__preview-wrapper .github-markdown-body')
  previewBodyRef.value = body ?? null
}

useMarkdownImageZoom(previewBodyRef)
useMarkdownCodeBlock(previewBodyRef)

// 模式切到 preview/editable 才有预览 DOM；切时等渲染后重解预览体
watch([innerMode, () => props.mode], () => {
  nextTick(resolvePreviewBody)
})

onMounted(() => {
  nextTick(resolvePreviewBody)
})

onBeforeUnmount(() => {
  previewBodyRef.value = null
})

// 提示 ElMessage 已引入以防 lint 误报未用（组件内暂未直接调用，留拓展用）
void ElMessage
</script>

<template>
  <div class="kh-md-editor" :style="rootStyle">
    <!-- 右下角编辑/预览浮层切换 -->
    <div v-if="showModeSwitch" class="kh-md-editor__mode-switch">
      <button
        v-for="m in [{ v: 'edit', t: '编辑' }, { v: 'preview', t: '预览' }] as const"
        :key="m.v"
        class="kh-md-editor__mode-btn"
        :class="{ 'is-active': editorMode === m.v }"
        type="button"
        @click="switchMode(m.v)"
      >{{ m.t }}</button>
    </div>
    <v-md-editor
      ref="editorRef"
      :model-value="modelValue"
      :mode="editorMode"
      :height="height"
      :placeholder="placeholder"
      :upload-image-config="uploadCfg"
      :disabled-menus="disabledMenus"
      @update:model-value="onInput"
      @upload-image="handleUploadImage"
    />
  </div>
</template>

<style scoped>
.kh-md-editor {
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: 0;
  min-width: 0;
  border: 1px solid var(--kh-border-soft);
  background: var(--kh-surface);
  border-radius: var(--kh-radius-lg);
  /* overflow: visible 让 v-md-editor 工具栏 tooltip 可向上溢出到本组件之外；
     正文滚动由内部 .v-md-editor__main 自带 overflow:auto 负责，不受此处影响。 */
  overflow: visible;
}

/* 右下角浮层胶囊按钮组：不占独立行、不抢自带工具条位置。z-index 提层避免被内部元素遮住。 */
.kh-md-editor__mode-switch {
  position: absolute;
  right: 12px;
  bottom: 12px;
  z-index: 5;
  display: flex;
  gap: 4px;
  padding: 4px;
  border-radius: var(--kh-radius-pill);
  background: color-mix(in srgb, var(--kh-surface) 92%, transparent);
  backdrop-filter: blur(8px);
  border: 1px solid var(--kh-border-soft);
  box-shadow: var(--kh-shadow-sm);
}
.kh-md-editor__mode-btn {
  height: 28px;
  padding: 0 14px;
  border: 1px solid transparent;
  border-radius: var(--kh-radius-pill);
  background: transparent;
  color: var(--kh-text-secondary);
  font-size: var(--kh-font-size-sm);
  font-weight: 500;
  cursor: pointer;
  transition: all var(--kh-transition-fast);
}
.kh-md-editor__mode-btn:hover {
  color: var(--kh-primary);
  background: var(--kh-surface-muted);
}
.kh-md-editor__mode-btn.is-active {
  background: var(--kh-primary-soft);
  border-color: var(--kh-primary-border);
  color: var(--kh-primary-strong);
}

/* 编辑器主体填满：去掉自身边框（外框由组件根负责），贴外框圆角防漏背景。 */
.kh-md-editor :deep(.v-md-editor) {
  height: 100%;
  border: none;
  border-radius: var(--kh-radius-lg);
  overflow: visible;
  box-shadow: var(--kh-shadow-sm);
}
.kh-md-editor :deep(.v-md-editor__toolbar-wrapper),
.kh-md-editor :deep(.v-md-editor__toolbar),
.kh-md-editor :deep(.v-md-editor__main) {
  border-radius: var(--kh-radius-lg);
}
.kh-md-editor :deep(.v-md-editor__main) {
  overflow: auto;
}
.kh-md-editor :deep(.v-md-editor__toolbar) {
  overflow: visible;
}
.kh-md-editor :deep(.v-md-editor__tooltip) {
  z-index: 3000;
}
</style>