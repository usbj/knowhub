<!--
  KhCommentInput —— 发表评论输入条（双模式）
  ------------------------------------------------------------------
  P1 决策：默认轻量态（头像 + textarea + 图按钮 + 字数 + 发送），条上「富文」按钮切到 KhMarkdownEditor
  （工具栏配图/拖拽/粘贴三入口 + 右下角编辑预览切），再点切回轻量。两种模式都拼 markdown 入 content，
  都走同一条 presignedUploadFlow（轻量走 useImageInsert composable，富文走 KhMarkdownEditor 内置 handleUploadImage）。
  切换态 richMode ref 不持久化，默认轻量；切换保留 content 文本互通不丢已写。
  配图：轻量 9 张硬上限（composable 计 content 里 ![](..) 张数到顶拦截）、单图 ≤2MB、仅图片（checkFileAllowed COMMENT_IMAGE）。
  富文无 9 张拦截，靠后端 2000 字符兜底（超过落库失败前端提示）。
  未登录 disabled「登录后评论」+ 图按钮禁用（isLoggedIn 守卫）。
  发送后向父级 submit 抛 { content }，父级负责调 createCommentApi + prepend。
-->
<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import KhAvatar from '@/components/common/KhAvatar.vue'
import KhMarkdownEditor from '@/components/common/KhMarkdownEditor.vue'
import { useImageInsert } from '@/composables/useImageInsert'
import { useUserStore } from '@/stores/user'

const props = defineProps<{
  /** 提示文案（如作者开精选时「你的评论需作者同意后展示」） */
  hint?: string
  /** 占位文案 */
  placeholder?: string
  /** 是否禁用发表（如评论区已关——父级通常直接不渲染本组件，此 prop 仅作计划外场景） */
  disabled?: boolean
}>()

const emit = defineEmits<{
  (e: 'submit', payload: { content: string }): void
}>()

const userStore = useUserStore()
const isLoggedIn = computed(() => userStore.isAuthenticated)
const content = ref('')

const MAX = 2000
/** 配图张数上限（与 useImageInsert 默认一致，这里同步用于轻量态字数提示语义说明，富文态不拦） */
const MAX_IMAGES = 9
/** 单图体积上限 MB，透传给 KhMarkdownEditor 富文态 maxFileSize，与轻量态校验对齐 */
const IMG_MAX_BYTES = 2 * 1024 * 1024

/** 富文模式开关：默认 false 轻量，切换不持久化（P1 决策）；切换时 content ref 不变，两种模式互通不丢已写 */
const richMode = ref(false)

/** 轻量态 textarea 元素 ref：useImageInsert 读其 selectionStart/End 在光标处插 markdown */
const textareaRef = ref<HTMLTextAreaElement | null>(null)

const { uploading, pickFiles } = useImageInsert({
  textareaRef,
  contentRef: content,
  businessType: 'COMMENT_IMAGE',
  access: 'PUBLIC',
  maxImages: MAX_IMAGES,
  maxSizeMB: 2,
})

/** 图按钮/富文切换按钮的禁用集合：未登录或禁用或上传中都不让动（发送同理在底部拼） */
const imageDisabled = computed(() => props.disabled || !isLoggedIn.value || uploading.value)

const onPickImage = () => {
  if (!isLoggedIn.value) {
    ElMessage.warning('请先登录后再传图')
    return
  }
  if (!props.disabled) pickFiles()
}

const sending = ref(false)
const onSubmit = async () => {
  const c = content.value.trim()
  if (!c) {
    ElMessage.warning('评论内容不能为空')
    return
  }
  if (!isLoggedIn.value) {
    ElMessage.warning('请先登录后再评论')
    return
  }
  sending.value = true
  // 父级实际调 api；此处先 emit，完成后清空由父反馈再清（乐观立即清让 input 更跟手）
  emit('submit', { content: c })
  content.value = ''
  sending.value = false
}
</script>

<template>
  <div class="kh-comment-input">
    <KhAvatar :item="{ label: isLoggedIn ? userStore.avatarText : '?' }" :size="36" />
    <div class="kh-comment-input__body">
      <!-- 轻量态：原 textarea + 图按钮（composable 在光标处插 markdown） -->
      <textarea
        v-if="!richMode"
        ref="textareaRef"
        v-model="content"
        class="kh-comment-input__textarea"
        :disabled="disabled || !isLoggedIn"
        :placeholder="isLoggedIn ? (placeholder ?? '写下你的评论…') : '登录后评论'"
        rows="3"
      />
      <!-- 富文态：KhMarkdownEditor，工具栏图片按钮/拖拽/粘贴三入口均走 presignedUploadFlow COMMENT_IMAGE -->
      <div v-else class="kh-comment-input__rich">
        <KhMarkdownEditor
          v-model="content"
          :show-mode-switch="false"
          height="220"
          business-type="COMMENT_IMAGE"
          access="PUBLIC"
          :placeholder="placeholder ?? '写下你的评论…'"
          :upload-image-config="{ accept: 'image/*', maxFileSize: IMG_MAX_BYTES }"
        />
      </div>
      <div class="kh-comment-input__bar">
        <span v-if="hint" class="kh-comment-input__hint">{{ hint }}</span>
        <span v-else class="kh-comment-input__hint">{{ isLoggedIn ? '' : '登录后参与讨论' }}</span>
        <span class="kh-comment-input__count">{{ content.length }}/{{ MAX }}</span>
        <!-- 轻量态图按钮：走 useImageInsert composable（2MB+9 张+白名单校验+光标插入 markdown） -->
        <button
          v-if="!richMode"
          class="kh-comment-input__img"
          type="button"
          :disabled="imageDisabled"
          :title="`配图（最多 ${MAX_IMAGES} 张，单图 ≤2MB）`"
          @click="onPickImage"
        >{{ uploading ? '上传中…' : '📎 图片' }}</button>
        <!-- 富文切换按钮：切到/切出 KhMarkdownEditor 双向；上传中禁用避免切丢光标上下文 -->
        <button
          class="kh-comment-input__mode"
          type="button"
          :disabled="uploading"
          :title="richMode ? '切回轻量输入' : '切到富文编辑器（工具栏配图+拖拽+粘贴）'"
          @click="richMode = !richMode"
        >{{ richMode ? '轻量' : '富文' }}</button>
        <button
          class="kh-comment-input__send"
          :disabled="disabled || !isLoggedIn || !content.trim() || sending"
          @click="onSubmit"
        >
          发送
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.kh-comment-input {
  display: flex;
  gap: var(--kh-space-3);
  align-items: flex-start;
}
.kh-comment-input__body {
  flex: 1;
  min-width: 0;
}
.kh-comment-input__textarea {
  width: 100%;
  box-sizing: border-box;
  padding: var(--kh-space-2) var(--kh-space-3);
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius-md);
  background: var(--kh-surface);
  color: var(--kh-text);
  font-size: 14px;
  line-height: 1.6;
  resize: vertical;
  outline: none;
  transition: border-color 0.15s;
}
.kh-comment-input__textarea:focus {
  border-color: var(--kh-primary);
}
.kh-comment-input__textarea:disabled {
  background: var(--kh-bg-soft);
  color: var(--kh-text-secondary);
  cursor: not-allowed;
}
/* 富文态容器：给 KhMarkdownEditor 一个固定高度盒（其 height=220 已自带）并留底部 bar 间距 */
.kh-comment-input__rich {
  min-width: 0;
}
.kh-comment-input__bar {
  display: flex;
  align-items: center;
  gap: var(--kh-space-3);
  margin-top: var(--kh-space-2);
  flex-wrap: wrap;
}
.kh-comment-input__hint {
  flex: 1;
  font-size: 12px;
  color: var(--kh-text-secondary);
  min-width: 0;
}
.kh-comment-input__count {
  font-size: 12px;
  color: var(--kh-text-muted);
}
/* 图按钮 / 富文切换按钮：轻量中性边框胶囊，hover 提色，禁用置灰（上传中/未登录/评论区关） */
.kh-comment-input__img,
.kh-comment-input__mode {
  padding: 4px 12px;
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius-md);
  background: var(--kh-surface);
  color: var(--kh-text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s, background 0.15s;
}
.kh-comment-input__img:hover:not(:disabled),
.kh-comment-input__mode:hover:not(:disabled) {
  color: var(--kh-primary);
  border-color: var(--kh-primary);
}
.kh-comment-input__img:disabled,
.kh-comment-input__mode:disabled {
  background: var(--kh-bg-soft);
  color: var(--kh-text-muted);
  border-color: var(--kh-border);
  cursor: not-allowed;
}
.kh-comment-input__send {
  padding: 6px 16px;
  border: none;
  border-radius: var(--kh-radius-md);
  background: var(--kh-primary);
  color: #fff;
  font-size: 13px;
  cursor: pointer;
  transition: opacity 0.15s;
}
.kh-comment-input__send:disabled {
  background: var(--kh-bg-soft);
  color: var(--kh-text-muted);
  cursor: not-allowed;
}
.kh-comment-input__send:not(:disabled):hover {
  opacity: 0.88;
}
</style>