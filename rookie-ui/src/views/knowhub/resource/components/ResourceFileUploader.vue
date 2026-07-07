<!--
  文件作用：
  资源文件上传组件，在资源新增/编辑表单的 fileObjectId 字段插槽内使用（仅 FILE 类型）。
  关键参数：
  - `modelValue`：当前 file_object.objectId（数字，双向绑定）；undefined/0 视为未上传。
  - `originalName`：已上传文件的原始名（编辑回显时展示，由父层从详情带出）。
  关键交互：
  - 已有文件时展示文件名 + 「重新上传 / 清除」按钮；
  - 选文件后调预签名直传流程（businessType=RESOURCE_FILE，access=PRIVATE），传完回填 objectId；
  - 上传中显示进度条，失败提示（http.ts 已统一弹错，这里仅兜底）。
  设计约定：
  - 资源文件固定 RESOURCE_FILE + PRIVATE（100MB / 不限类型，见 sql/knowhub-storage.sql）；
  - PRIVATE 对象只回填 objectId（不回填 publicUrl），取用走下载接口 /resource/download/{resourceId}；
  - 上传 PUT 目标由后端按访问模式决定（中转→/file/proxy-upload/{id} 带 Token；直链→预签名绝对 URL），
    utils/upload.ts 的 putToPresignedUrl 按链接形态自动带不带 Token。
  - 主题适配：文件框、进度条均用 base.css 的 --rookie-* 变量，深浅模式自动跟随。
-->
<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElButton, ElProgress, ElMessage } from 'element-plus'
import { presignedUploadFlow } from '@/utils/upload'

const props = defineProps<{
  /** 当前 file_object.objectId；undefined/0 视为未上传 */
  modelValue?: number
  /** 已上传文件的原始名（编辑回显时由父层传入展示） */
  originalName?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: number | undefined]
  /** 上传成功后回传原始文件名，供父层展示（可选） */
  'update:originalName': [value: string]
}>()

const uploading = ref(false)
const uploadPercent = ref(0)
/** 隐藏的文件选择 input */
const fileInputRef = ref<HTMLInputElement | null>(null)
/** 本次上传的文件名（上传中展示） */
const currentFileName = ref('')

const hasFile = computed(() => Boolean(props.modelValue))
const displayName = computed(() => props.originalName || currentFileName.value)

/**
 * 方法效果：
  * 执行资源文件预签名直传，成功后回填 objectId。
 * 数据流转：
 * - businessType 固定 RESOURCE_FILE、access 固定 PRIVATE；
 * - 调 presignedUploadFlow（申请令牌 → PUT 上传 → confirm），onProgress 更新进度；
 * - 成功后 emit update:modelValue 回填 objectId，emit update:originalName 回传文件名。
 * 参数：
 * - `file`：用户选中的文件。
 * 返回值：
 * - 无返回值；副作用是发起上传并回填 objectId。
 */
const uploadFile = async (file: File) => {
  uploading.value = true
  uploadPercent.value = 0
  currentFileName.value = file.name

  try {
    const result = await presignedUploadFlow({
      file,
      businessType: 'RESOURCE_FILE',
      access: 'PRIVATE',
      onProgress: (percent) => {
        uploadPercent.value = percent
      },
    })

    emit('update:modelValue', result.objectId)
    emit('update:originalName', file.name)
    ElMessage.success('文件上传成功')
  } catch (error) {
    // http.ts 已统一弹错，这里仅兜底打印
    // eslint-disable-next-line no-console
    console.error('资源文件上传失败', error)
  } finally {
    uploading.value = false
    uploadPercent.value = 0
    currentFileName.value = ''
    if (fileInputRef.value) {
      fileInputRef.value.value = ''
    }
  }
}

/**
 * 方法效果：
 * 隐藏 input 的 change 回调：取到选中文件后调 uploadFile。
 * 参数：
 * - `event`：input change 事件。
 * 返回值：
 * - 无返回值；副作用是发起上传。
 */
const handleFileChange = (event: Event) => {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (file) {
    void uploadFile(file)
  }
}

/**
 * 方法效果：
 * 触发隐藏 input 的文件选择对话框。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是打开系统文件选择对话框。
 */
const triggerFilePicker = () => {
  fileInputRef.value?.click()
}

/**
 * 方法效果：
 * 清除当前文件（回填 undefined，不删 OSS 对象；旧文件由后端 FileGcTask 兜底清）。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是 emit undefined 清空文件。
 */
const clearFile = () => {
  emit('update:modelValue', undefined)
  emit('update:originalName', '')
}
</script>

<template>
  <div class="resource-file-uploader">
    <!-- 隐藏的文件选择 input，由按钮统一触发；资源文件不限类型故 accept 留空 -->
    <input
      ref="fileInputRef"
      type="file"
      class="resource-file-uploader__input"
      @change="handleFileChange"
    />

    <!-- 已有文件时展示文件名 + 重新上传/清除 -->
    <div v-if="hasFile && !uploading" class="resource-file-uploader__preview">
      <div class="resource-file-uploader__file">
        <span class="resource-file-uploader__file-icon">📎</span>
        <span class="resource-file-uploader__file-name" :title="displayName">{{ displayName || '已上传文件' }}</span>
      </div>
      <div class="resource-file-uploader__preview-actions">
        <ElButton link type="primary" @click="triggerFilePicker">重新上传</ElButton>
        <ElButton link type="danger" @click="clearFile">清除</ElButton>
      </div>
    </div>

    <!-- 上传中展示进度 -->
    <div v-if="uploading" class="resource-file-uploader__progress">
      <ElProgress :percentage="uploadPercent" :status="uploadPercent >= 100 ? 'success' : undefined" />
      <span class="resource-file-uploader__uploading-name">{{ currentFileName }}</span>
    </div>

    <!-- 未上传时展示点击上传占位区 -->
    <div v-if="!hasFile && !uploading" class="resource-file-uploader__picker" @click="triggerFilePicker">
      <span class="resource-file-uploader__hint">点击上传文件（≤100MB，不限类型）</span>
    </div>
  </div>
</template>

<style scoped>
.resource-file-uploader {
  display: grid;
  gap: 10px;
}

/* 隐藏原生 input，仅靠按钮触发其 click */
.resource-file-uploader__input {
  display: none;
}

.resource-file-uploader__preview {
  display: grid;
  gap: 8px;
  padding: 10px 14px;
  border: 1px solid var(--rookie-border);
  border-radius: var(--rookie-radius-md);
  background: var(--rookie-surface-weak);
}

.resource-file-uploader__file {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.resource-file-uploader__file-icon {
  flex-shrink: 0;
}

.resource-file-uploader__file-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--rookie-text);
  font-size: var(--rookie-font-size-sm);
}

.resource-file-uploader__preview-actions {
  display: flex;
  gap: 12px;
}

.resource-file-uploader__picker {
  display: grid;
  place-items: center;
  padding: 24px;
  border: 1px dashed var(--rookie-border);
  border-radius: var(--rookie-radius-md);
  background: var(--rookie-surface-muted);
  color: var(--rookie-text-secondary);
  cursor: pointer;
  transition: border-color 0.2s;
}

.resource-file-uploader__picker:hover {
  border-color: var(--rookie-primary);
  color: var(--rookie-primary);
}

.resource-file-uploader__hint {
  font-size: var(--rookie-font-size-sm);
}

.resource-file-uploader__progress {
  display: grid;
  gap: 6px;
  padding: 12px 0;
}

.resource-file-uploader__uploading-name {
  color: var(--rookie-text-secondary);
  font-size: var(--rookie-font-size-sm);
}
</style>
