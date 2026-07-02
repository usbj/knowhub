<!--
  文件作用：
  博客封面图上传组件，在博客新增/编辑表单的 coverUrl 字段插槽内使用。
  关键参数：
  - `modelValue`：当前封面地址（/file/public/{id} 形式或外链 URL），双向绑定。
  关键交互：
  - 已有封面时展示预览缩略图 + 「重新上传 / 清除」按钮；
  - 选图后调预签名直传流程（businessType=BLOG_COVER，access=PUBLIC），传完回填 coverUrl；
  - 上传中显示进度条，失败提示（http.ts 已统一弹错，这里仅兜底）。
  设计约定：
  - 封面固定 BLOG_COVER + PUBLIC，PUBLIC 对象回填 /file/public/{objectId} 供 <img> 直引。
  - 主题适配：预览框、拖拽区、进度条均用 base.css 的 --rookie-* 变量，深浅模式自动跟随。
-->
<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElButton, ElProgress, ElUpload, ElMessage } from 'element-plus'
import type { UploadRawFile } from 'element-plus'
import { presignedUploadFlow } from '@/utils/upload'

const props = defineProps<{
  /** 当前封面地址（/file/public/{id} 或外链 URL） */
  modelValue?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const uploading = ref(false)
const uploadPercent = ref(0)

const hasCover = computed(() => Boolean(props.modelValue))

/**
 * 方法效果：
 * 执行封面预签名直传，成功后回填 coverUrl 为 /file/public/{objectId}。
 * 数据流转：
  - businessType 固定 BLOG_COVER、access 固定 PUBLIC；
 * - 调 presignedUploadFlow（申请令牌 → PUT 直传 → confirm），onProgress 更新进度；
 * - 成功后 emit update:modelValue 回填 /file/public/{objectId}。
 * 参数：
 * - `file`：用户选中的图片文件。
 * 返回值：
 * - 无返回值；副作用是发起上传并回填封面地址。
 */
const uploadCover = async (file: File) => {
  uploading.value = true
  uploadPercent.value = 0

  try {
    const result = await presignedUploadFlow({
      file,
      businessType: 'BLOG_COVER',
      access: 'PUBLIC',
      onProgress: (percent) => {
        uploadPercent.value = percent
      },
    })

    if (result.publicUrl) {
      emit('update:modelValue', result.publicUrl)
      ElMessage.success('封面上传成功')
    }
  } catch (error) {
    // http.ts 已统一弹错，这里仅兜底打印
    // eslint-disable-next-line no-console
    console.error('封面上传失败', error)
  } finally {
    uploading.value = false
    uploadPercent.value = 0
  }
}

/**
 * 方法效果：
 * ElUpload before-upload 钩子：阻止自动上传，手动调 uploadCover。
 * 参数：
 * - `rawFile`：ElUpload 即将上传的原始文件。
 * 返回值：
 * - false 阻止自动上传。
 */
const handleBeforeUpload = (rawFile: UploadRawFile) => {
  void uploadCover(rawFile)
  return false
}

/**
 * 方法效果：
 * 清除当前封面地址。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是 emit 空字符串清空封面。
 */
const clearCover = () => {
  emit('update:modelValue', '')
}
</script>

<template>
  <div class="blog-cover-uploader">
    <!-- 已有封面时展示预览 -->
    <div v-if="hasCover && !uploading" class="blog-cover-uploader__preview">
      <img :src="modelValue" alt="封面预览" />
      <div class="blog-cover-uploader__preview-actions">
        <ElButton link type="primary">重新上传</ElButton>
        <ElButton link type="danger" @click="clearCover">清除</ElButton>
      </div>
    </div>

    <!-- 上传中展示进度 -->
    <div v-if="uploading" class="blog-cover-uploader__progress">
      <ElProgress :percentage="uploadPercent" :status="uploadPercent >= 100 ? 'success' : undefined" />
    </div>

    <!-- 未上传或重新上传时展示拖拽区；已有封面时隐藏（点重新上传触发选择） -->
    <ElUpload
      v-show="!hasCover || uploading"
      :show-file-list="false"
      :auto-upload="false"
      :before-upload="handleBeforeUpload"
      accept="image/png,image/jpeg,image/gif,image/webp"
      drag
      class="blog-cover-uploader__picker"
    >
      <span class="blog-cover-uploader__hint">点击或拖拽图片上传（PNG/JPEG/GIF/WebP，≤5MB）</span>
    </ElUpload>
  </div>
</template>

<style scoped>
.blog-cover-uploader {
  display: grid;
  gap: 10px;
}

.blog-cover-uploader__preview {
  display: grid;
  gap: 8px;
}

.blog-cover-uploader__preview img {
  display: block;
  width: 100%;
  max-height: 200px;
  object-fit: cover;
  border-radius: var(--rookie-radius-md);
  border: 1px solid var(--rookie-border);
  background: var(--rookie-surface-muted);
}

.blog-cover-uploader__preview-actions {
  display: flex;
  gap: 12px;
}

.blog-cover-uploader__picker :deep(.el-upload-dragger) {
  width: 100%;
  border-color: var(--rookie-border);
  background: var(--rookie-surface-muted);
  color: var(--rookie-text-secondary);
  border-radius: var(--rookie-radius-md);
  padding: 24px;
  display: grid;
  gap: 8px;
  justify-items: center;
}

.blog-cover-uploader__picker :deep(.el-upload-dragger:hover) {
  border-color: var(--rookie-primary);
}

.blog-cover-uploader__hint {
  font-size: var(--rookie-font-size-sm);
  color: var(--rookie-text-secondary);
}

.blog-cover-uploader__progress {
  padding: 12px 0;
}
</style>
