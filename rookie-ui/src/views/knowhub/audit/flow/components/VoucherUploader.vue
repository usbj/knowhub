<!--
  文件作用：
  审计流水票据上传组件，在资金流水新增/编辑弹窗表单的 voucherObjectId 字段插槽内使用。
  关键参数：
  - `modelValue`：当前 file_object.objectId（数字，双向绑定）；undefined/0 视为未上传。
  关键交互：
  - 支持两种选文件方式：点击占位区触发文件选择对话框；把文件拖拽到占位区（Drag&Drop）。
  - 已有票据时展示「已上传附件 #{objectId}」徽标 + 重新上传 / 清除；
  - 选文件后先校验类型（图片 png/jpeg/gif/webp + PDF）与体积（≤5MB），再调预签名直传流程
    （businessType=AUDIT_VOUCHER，access=PRIVATE），传完回填 objectId；
  - 上传中显示进度条，失败提示（http.ts 已统一弹错，这里仅兜底）。
  设计约定：
  - 票据固定 AUDIT_VOUCHER + PRIVATE（图片/PDF，5MB，见 sql/knowhub-audit-voucher-filetype.sql）；
  - PRIVATE 对象只回填 objectId（不回填 publicUrl），取用走下载接口 getDownloadUrl(objectId, bizAuthorized)；
  - 不展示图片缩略图（PRIVATE 无 publicUrl），仅以徽标形式标识「已上传」。
  - 类型校验以「文件名扩展名」为主信号（浏览器在拖拽或非典型环境可能给空/不一致的 content-type），
    content-type 仅作辅助兜底，确保 PDF/图片识别稳定。
  - 主题适配：用 base.css 的 --rookie-* 变量，深浅模式自动跟随。
-->
<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElButton, ElProgress, ElMessage } from 'element-plus'
import { presignedUploadFlow } from '@/utils/upload'

const props = defineProps<{
  /** 当前 file_object.objectId；undefined/0 视为未上传 */
  modelValue?: number
}>()

const emit = defineEmits<{
  'update:modelValue': [value: number | undefined]
}>()

/** 允许上传的文件扩展名（含前导点，比对文件名末尾） */
const ALLOWED_EXT = ['.png', '.jpg', '.jpeg', '.gif', '.webp', '.pdf']
/** accept 属性（图片 + PDF，原生文件选择对话框过滤） */
const ACCEPT = 'image/png,image/jpeg,image/gif,image/webp,application/pdf'
/** 单文件体积上限 5MB */
const MAX_SIZE = 5 * 1024 * 1024

const uploading = ref(false)
const uploadPercent = ref(0)
/** 隐藏的文件选择 input */
const fileInputRef = ref<HTMLInputElement | null>(null)
/** 本次上传的文件名（上传中展示） */
const currentFileName = ref('')
/** 拖拽悬停高亮态 */
const dragOver = ref(false)

const hasFile = computed(() => Boolean(props.modelValue))

/**
 * 方法效果：
 * 校验文件类型与体积：类型按文件名扩展名比对（content-type 仅辅助），体积 ≤5MB；不符合弹错并返回 false。
 * 参数：
 * - `file`：用户选中的文件。
 * 返回值：
 * - 校验通过返回 true；不通过返回 false（已弹错）。
 */
const validateFile = (file: File): boolean => {
  if (file.size > MAX_SIZE) {
    ElMessage.warning('票据附件不能超过 5MB')
    return false
  }
  const lowerName = file.name.toLowerCase()
  const ok = ALLOWED_EXT.some((ext) => lowerName.endsWith(ext))
  // 兜底：content-type 命中图片/PDF 也算通过（应对扩展名被刻意挖改/缺失的少数情况）
  const typeOk = !!file.type && (file.type.startsWith('image/') || file.type === 'application/pdf')
  if (!ok && !typeOk) {
    ElMessage.warning('仅支持上传图片（png/jpeg/gif/webp）或 PDF 文件')
    return false
  }
  return true
}

/**
 * 方法效果：
 * 执行票据预签名直传，成功后回填 objectId。
 * 数据流转：
 * - businessType 固定 AUDIT_VOUCHER、access 固定 PRIVATE；
 * - 调 presignedUploadFlow（申请令牌 → PUT 上传 → confirm），onProgress 更新进度；
 * - 成功后 emit update:modelValue 回填 objectId。
 * 参数：
 * - `file`：用户选中的文件（已通过 validateFile）。
 */
const uploadFile = async (file: File) => {
  uploading.value = true
  uploadPercent.value = 0
  currentFileName.value = file.name

  try {
    const result = await presignedUploadFlow({
      file,
      businessType: 'AUDIT_VOUCHER',
      access: 'PRIVATE',
      onProgress: (percent) => {
        uploadPercent.value = percent
      },
    })

    emit('update:modelValue', result.objectId)
    ElMessage.success('票据上传成功')
  } catch (error) {
    // eslint-disable-next-line no-console
    console.error('票据上传失败', error)
  } finally {
    uploading.value = false
    uploadPercent.value = 0
    currentFileName.value = ''
    if (fileInputRef.value) {
      fileInputRef.value.value = ''
    }
  }
}

/** 统一文件入口：校验通过后走上传（点击选择 / 拖拽共用） */
const handleFile = (file: File | undefined | null) => {
  if (!file) {
    return
  }
  if (!validateFile(file)) {
    return
  }
  void uploadFile(file)
}

const handleFileChange = (event: Event) => {
  const target = event.target as HTMLInputElement
  handleFile(target.files?.[0])
}

const triggerFilePicker = () => {
  fileInputRef.value?.click()
}

/**
 * 方法效果：
 * 拖拽悬停止默认（防浏览器直接打开文件），并高亮占位区。
 */
const handleDragOver = (event: DragEvent) => {
  event.preventDefault()
  if (uploading.value) {
    return
  }
  dragOver.value = true
}

/**
 * 方法效果：
 * 拖拽离开取消高亮。
 */
const handleDragLeave = () => {
  dragOver.value = false
}

/**
 * 方法效果：
 * 拖拽放下时取第一个文件上传（单选），并取消高亮。多文件只取首个并提示。
 */
const handleDrop = (event: DragEvent) => {
  event.preventDefault()
  dragOver.value = false
  if (uploading.value) {
    return
  }
  const dropped = event.dataTransfer?.files
  if (!dropped || dropped.length === 0) {
    return
  }
  if (dropped.length > 1) {
    ElMessage.warning('一次仅支持上传 1 个票据附件，已取第一个文件')
  }
  handleFile(dropped[0])
}

/**
 * 方法效果：
 * 清除当前票据（回填 undefined，不删 OSS 对象；旧文件由后端 FileGcTask 兜底清）。
 */
const clearFile = () => {
  emit('update:modelValue', undefined)
}
</script>

<template>
  <div class="voucher-uploader">
    <!-- 隐藏的文件选择 input，限制 accept 为图片/PDF -->
    <input
      ref="fileInputRef"
      type="file"
      class="voucher-uploader__input"
      :accept="ACCEPT"
      @change="handleFileChange"
    />

    <!-- 已有票据：徽标 + 重新上传/清除 -->
    <div v-if="hasFile && !uploading" class="voucher-uploader__preview">
      <div class="voucher-uploader__file">
        <span class="voucher-uploader__file-icon">🧾</span>
        <span class="voucher-uploader__file-name">已上传附件 #{{ props.modelValue }}</span>
      </div>
      <div class="voucher-uploader__preview-actions">
        <ElButton link type="primary" @click="triggerFilePicker">重新上传</ElButton>
        <ElButton link type="danger" @click="clearFile">清除</ElButton>
      </div>
    </div>

    <!-- 上传中展示进度 -->
    <div v-if="uploading" class="voucher-uploader__progress">
      <ElProgress :percentage="uploadPercent" :status="uploadPercent >= 100 ? 'success' : undefined" />
      <span class="voucher-uploader__uploading-name">{{ currentFileName }}</span>
    </div>

    <!-- 未上传：拖拽 + 点击上传占位区 -->
    <div
      v-if="!hasFile && !uploading"
      class="voucher-uploader__picker"
      :class="{ 'is-dragover': dragOver }"
      @click="triggerFilePicker"
      @dragover="handleDragOver"
      @dragleave="handleDragLeave"
      @drop="handleDrop"
    >
      <span class="voucher-uploader__picker-icon">⬆</span>
      <span class="voucher-uploader__hint">将票据拖拽到此处，或点击上传</span>
      <span class="voucher-uploader__sub">支持图片（png/jpeg/gif/webp）或 PDF，≤5MB</span>
    </div>
  </div>
</template>

<style scoped>
.voucher-uploader {
  display: grid;
  gap: 10px;
}

/* 隐藏原生 input，仅靠点击/拖拽触发 */
.voucher-uploader__input {
  display: none;
}

.voucher-uploader__preview {
  display: grid;
  gap: 8px;
  padding: 10px 14px;
  border: 1px solid var(--rookie-border);
  border-radius: var(--rookie-radius-md);
  background: var(--rookie-surface-weak);
}

.voucher-uploader__file {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.voucher-uploader__file-icon {
  flex-shrink: 0;
}

.voucher-uploader__file-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--rookie-text);
  font-size: var(--rookie-font-size-sm);
}

.voucher-uploader__preview-actions {
  display: flex;
  gap: 12px;
}

.voucher-uploader__picker {
  display: grid;
  place-items: center;
  gap: 4px;
  padding: 24px;
  border: 1px dashed var(--rookie-border);
  border-radius: var(--rookie-radius-md);
  background: var(--rookie-surface-muted);
  color: var(--rookie-text-secondary);
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s, color 0.2s;
}

.voucher-uploader__picker:hover {
  border-color: var(--rookie-primary);
  color: var(--rookie-primary);
}

/* 拖拽悬停高亮：主色边框 + 弱背景，明确「此刻放开即可上传」反馈 */
.voucher-uploader__picker.is-dragover {
  border-color: var(--rookie-primary);
  background: var(--rookie-surface-weak);
  color: var(--rookie-primary);
}

.voucher-uploader__picker-icon {
  font-size: 22px;
  line-height: 1;
}

.voucher-uploader__hint {
  font-size: var(--rookie-font-size-sm);
  color: var(--rookie-text);
}

.voucher-uploader__sub {
  font-size: var(--rookie-font-size-sm);
  color: var(--rookie-text-tertiary);
}

.voucher-uploader__progress {
  display: grid;
  gap: 6px;
  padding: 12px 0;
}

.voucher-uploader__uploading-name {
  color: var(--rookie-text-secondary);
  font-size: var(--rookie-font-size-sm);
}
</style>