<!--
  文件作用：
  资源封面图上传组件，在资源上传页 upload.vue 的封面字段使用。
  关键参数：
  - `modelValue`：当前封面地址（/file/resolve/{id} 形式或外链 URL），双向绑定。
  关键交互：
  - 已有封面时展示预览缩略图 + 「重新上传 / 清除」按钮（按钮触发隐藏 input 选图）；
  - 选图后调预签名直传流程（businessType=RESOURCE_COVER，access=PUBLIC），传完回填 coverUrl；
  - 上传中显示进度条，失败提示（http 已统一弹错，这里仅兜底）。
  设计约定：
  - 封面固定 RESOURCE_COVER + PUBLIC，PUBLIC 对象回填 /file/resolve/{id} 稳定解析引用供 <img> 直引
    （渲染时命中后端 resolve 接口按 access_mode 动态 302 分发）。
  - 封面 URL 落资源主表 link_icon 列（前端字段 linkIcon，复用该列作封面，FILE/LINK 两类通用）。
  - 克隆 BlogCoverUploader.vue 范式，唯一改 businessType=RESOURCE_COVER。
-->
<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElButton, ElProgress, ElMessage } from 'element-plus'
import { presignedUploadFlow } from '@/utils/upload'

const props = defineProps<{
  /** 当前封面地址（/file/resolve/{id} 或外链 URL） */
  modelValue?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const uploading = ref(false)
const uploadPercent = ref(0)
/** 隐藏的文件选择 input，供「点击/重新上传」按钮统一触发选图 */
const fileInputRef = ref<HTMLInputElement | null>(null)

const hasCover = computed(() => Boolean(props.modelValue))

/**
 * 执行封面预签名直传，成功后回填 coverUrl 为 /file/resolve/{objectId}。
 * businessType 固定 RESOURCE_COVER、access 固定 PUBLIC；调 presignedUploadFlow（申请令牌 → PUT 上传 →
 * confirm → 取稳定解析引用），onProgress 更新进度；成功后 emit update:modelValue 回填稳定解析引用。
 */
const uploadCover = async (file: File) => {
  uploading.value = true
  uploadPercent.value = 0

  try {
    const result = await presignedUploadFlow({
      file,
      businessType: 'RESOURCE_COVER',
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
    console.error('资源封面上传失败', error)
  } finally {
    uploading.value = false
    uploadPercent.value = 0
    // 清空 input value，使同一文件可重复选择触发 change
    if (fileInputRef.value) {
      fileInputRef.value.value = ''
    }
  }
}

/** 隐藏 input 的 change 回调：取到选中文件后调 uploadCover */
const handleFileChange = (event: Event) => {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (file) {
    void uploadCover(file)
  }
}

/** 触发隐藏 input 的文件选择对话框（供「点击上传/重新上传」按钮统一调用） */
const triggerFilePicker = () => {
  fileInputRef.value?.click()
}

/** 清除当前封面地址 */
const clearCover = () => {
  emit('update:modelValue', '')
}
</script>

<template>
  <div class="kh-cover-uploader">
    <!-- 隐藏的文件选择 input，由按钮统一触发 -->
    <input
      ref="fileInputRef"
      type="file"
      accept="image/png,image/jpeg,image/gif,image/webp"
      class="kh-cover-uploader__input"
      @change="handleFileChange"
    />

    <!-- 已有封面时展示预览 + 重新上传/清除 -->
    <div v-if="hasCover && !uploading" class="kh-cover-uploader__preview">
      <img :src="modelValue" alt="封面预览" />
      <div class="kh-cover-uploader__preview-actions">
        <ElButton link type="primary" @click="triggerFilePicker">重新上传</ElButton>
        <ElButton link type="danger" @click="clearCover">清除</ElButton>
      </div>
    </div>

    <!-- 上传中展示进度 -->
    <div v-if="uploading" class="kh-cover-uploader__progress">
      <ElProgress :percentage="uploadPercent" :status="uploadPercent >= 100 ? 'success' : undefined" />
    </div>

    <!-- 未上传时展示点击上传占位区 -->
    <div v-if="!hasCover && !uploading" class="kh-cover-uploader__picker" @click="triggerFilePicker">
      <span class="kh-cover-uploader__hint">点击上传资源封面图（PNG/JPEG/GIF/WebP，≤5MB）</span>
    </div>
  </div>
</template>

<style scoped>
.kh-cover-uploader {
  display: grid;
  gap: 10px;
}

/* 隐藏原生 input，仅靠按钮触发其 click */
.kh-cover-uploader__input {
  display: none;
}

.kh-cover-uploader__preview {
  display: grid;
  gap: 8px;
}

.kh-cover-uploader__preview img {
  display: block;
  width: 100%;
  max-height: 200px;
  object-fit: cover;
  border-radius: var(--kh-radius-lg);
  border: 1px solid var(--kh-border);
  background: var(--kh-surface-muted);
}

.kh-cover-uploader__preview-actions {
  display: flex;
  gap: 12px;
}

.kh-cover-uploader__picker {
  display: grid;
  place-items: center;
  padding: 24px;
  border: 1px dashed var(--kh-border);
  border-radius: var(--kh-radius-lg);
  background: var(--kh-surface-muted);
  color: var(--kh-text-secondary);
  cursor: pointer;
  transition: border-color var(--kh-transition-fast);
}

.kh-cover-uploader__picker:hover {
  border-color: var(--kh-primary);
  color: var(--kh-primary);
}

.kh-cover-uploader__hint {
  font-size: var(--kh-font-size-sm);
}

.kh-cover-uploader__progress {
  padding: 12px 0;
}
</style>
