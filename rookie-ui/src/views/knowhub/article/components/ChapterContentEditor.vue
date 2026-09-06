<!--
  文件作用：
  章节正文全屏编辑器（CSDN 风格双栏：左编辑右预览），在章节新增/编辑弹窗的「编辑正文」按钮点击后打开。
  照搬博客 BlogContentEditor 范式，章节正文配图复用 BLOG_BODY（章节≈博客正文，access=PUBLIC），
  不新增 ARTICLE_BODY 业务类型以避免再动 FileBusinessType 枚举与字典。
  关键参数：
  - `visible`：弹窗显隐，由父层双向绑定控制。
  - `modelValue`：正文 markdown 字符串，双向绑定。
  关键交互：
  - 左栏 v-md-editor（mode=edit）编辑，右栏 v-md-preview 实时预览；
  - 工具栏「上传本地图片」子项 / 拖拽 / 粘贴图片均触发 v-md-editor 的 upload-image 事件，
    对每个 file 调预签名直传（businessType=BLOG_BODY，access=PUBLIC），
    成功后调 insertImage({ name, url: /file/public/{id} }) 在光标处插入；
  - 顶部工具栏「保存返回」回写正文并关闭弹窗。
  - 本地编辑缓冲：进入弹窗从父层 modelValue 拷贝一份，编辑期间只改本地，点保存才 emit 回父层。
  设计约定：
  - 不修改 rookie-ui 原 MarkdownEditor.vue，直接用全局注册的 v-md-editor / v-md-preview；
  - 主题适配：外壳用 --rookie-* 变量；v-md-editor/v-md-preview 主题已全局注入并适配深浅。
-->
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElButton, ElDialog, ElMessage } from 'element-plus'
import { presignedUploadFlow } from '@/utils/upload'

const props = defineProps<{
  /** 弹窗显隐 */
  visible: boolean
  /** 正文 markdown 字符串 */
  modelValue: string
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  'update:modelValue': [value: string]
}>()

/**
 * 本地编辑缓冲：进入弹窗时从父层 modelValue 拷贝一份，编辑期间只在本地改，
 * 点「保存返回」才 emit 回父层；点「取消」丢弃本地改动。
 */
const localContent = ref('')

/** 上传中标记（用于禁用保存按钮与提示） */
const uploading = ref(false)

watch(
  () => props.visible,
  (value) => {
    if (value) {
      localContent.value = props.modelValue ?? ''
    }
  },
  { immediate: true },
)

const canSave = computed(() => !uploading.value)

/**
 * 方法效果：
 * v-md-editor 的 upload-image 事件回调：对每个图片文件走预签名直传，
 * 成功后调 insertImage 在光标处插入 ![name](/file/public/{id})。
 * 章节正文配图复用 BLOG_BODY（章节≈博客正文，access=PUBLIC）。
 * 数据流转：
 * - 回调签名 (event, insertImage, files)；
 * - 遍历 files，逐个调 presignedUploadFlow（BLOG_BODY + PUBLIC）；
 * - 成功 insertImage({ name: file.name, url: result.publicUrl })；
 * - 任一文件失败 http.ts 已弹错，跳过该文件继续处理后续（不中断整体）。
 * 参数：
 * - `_event`：触发事件（工具栏/拖拽/粘贴），本回调不使用。
 * - `insertImage`：v-md-editor 提供的插入回调。
 * - `files`：待上传的图片文件数组。
 */
const handleUploadImage = async (
  _event: Event,
  insertImage: (imageConfig: { name?: string; url: string }) => void,
  files: File[],
) => {
  if (!files || files.length === 0) {
    return
  }

  uploading.value = true

  for (const file of files) {
    try {
      const result = await presignedUploadFlow({
        file,
        businessType: 'BLOG_BODY',
        access: 'PUBLIC',
      })

      if (result.publicUrl) {
        insertImage({ name: file.name, url: result.publicUrl })
      }
    } catch (error) {
      // http.ts 已统一弹错，这里仅兜底打印并跳过该文件
      // eslint-disable-next-line no-console
      console.error('章节正文图片上传失败', file.name, error)
    }
  }

  uploading.value = false
}

/**
 * 方法效果：
 * 保存本地编辑缓冲回父层 modelValue 并关闭弹窗。
 */
const handleSave = () => {
  if (!canSave.value) {
    return
  }
  emit('update:modelValue', localContent.value)
  emit('update:visible', false)
  ElMessage.success('正文已保存')
}

/**
 * 方法效果：
 * 放弃本地编辑并关闭弹窗（不回写父层）。
 */
const handleCancel = () => {
  emit('update:visible', false)
}
</script>

<template>
  <ElDialog
    :model-value="visible"
    title="编辑章节正文"
    fullscreen
    class="chapter-content-editor"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
  >
    <div class="chapter-content-editor__body">
      <!-- 左栏：markdown 编辑 -->
      <section class="chapter-content-editor__edit">
        <v-md-editor
          v-model="localContent"
          mode="edit"
          height="100%"
          :upload-image-config="{ accept: 'image/*', maxFileSize: 10 * 1024 * 1024 }"
          :disabled-menus="[]"
          @upload-image="handleUploadImage"
        />
      </section>

      <!-- 右栏：实时预览 -->
      <section class="chapter-content-editor__preview">
        <v-md-preview :text="localContent" />
      </section>
    </div>

    <template #footer>
      <div class="chapter-content-editor__footer">
        <span v-if="uploading" class="chapter-content-editor__uploading">图片上传中…</span>
        <ElButton @click="handleCancel">取消</ElButton>
        <ElButton type="primary" :disabled="!canSave" @click="handleSave">保存返回</ElButton>
      </div>
    </template>
  </ElDialog>
</template>

<style scoped>
.chapter-content-editor__body {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  height: calc(100vh - 200px);
  min-height: 400px;
}

.chapter-content-editor__edit {
  min-width: 0;
  border: 1px solid var(--rookie-border);
  border-radius: var(--rookie-radius-md);
  overflow: hidden;
}

.chapter-content-editor__preview {
  min-width: 0;
  border: 1px solid var(--rookie-border);
  border-radius: var(--rookie-radius-md);
  background: var(--rookie-card-bg);
  overflow: auto;
  padding: 12px 16px;
}

.chapter-content-editor__footer {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 12px;
}

.chapter-content-editor__uploading {
  margin-right: auto;
  color: var(--rookie-primary);
  font-size: var(--rookie-font-size-sm);
}
</style>