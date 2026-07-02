<!--
  文件作用：
  文件对象详情只读弹窗，承接文件列表「详情」操作后的元数据展示。
  关键参数：
  - `visible`：弹窗显隐，由父层双向绑定控制。
  - `fileObject`：当前展示的文件对象记录。
  关键依赖：
  - 复用 ElDialog + ElDescriptions 展示元数据；
  - businessType / access / uploadStatus 走字典标签系统渲染为带色 ElTag；
  - PUBLIC 对象且为图片类型时展示预览缩略图（/file/public/{id} 直引）；
  - 主题适配：描述列表、预览框均用 base.css 的 --rookie-* 变量，深浅模式自动跟随。
-->
<script setup lang="ts">
import { computed } from 'vue'
import { ElButton, ElDescriptions, ElDescriptionsItem, ElDialog } from 'element-plus'
import DictTag from '@/components/DictTag.vue'
import { formatDateTime } from '@/utils/format'
import { formatFileSize } from '../config'
import { buildFilePublicUrl } from '@/api/knowhub/file'
import type { FileObjectRecord } from '@/types/api/knowhub/file'

const props = defineProps<{
  visible: boolean
  fileObject: FileObjectRecord | null
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

/**
 * 方法效果：
 * 把时间字段统一格式化，空值回退占位文本。
 * 参数：
 * - `value`：时间原始值。
 * 返回值：
 * - 格式化后的时间字符串，或占位文本。
 */
const formatTime = (value: unknown) => formatDateTime(value) || '--'

/**
 * 是否为可预览的图片：PUBLIC 对象且 contentType 以 image/ 开头时展示缩略图。
 */
const isImagePreviewable = computed(() => {
  const record = props.fileObject
  if (!record || record.access !== 'PUBLIC' || !record.objectId) {
    return false
  }
  return String(record.contentType ?? '').startsWith('image/')
})

/**
 * PUBLIC 图片预览地址：/file/public/{objectId} 相对路径，靠 /file 代理 302 到 RustFS。
 */
const previewUrl = computed(() =>
  props.fileObject?.objectId ? buildFilePublicUrl(props.fileObject.objectId) : '',
)
</script>

<template>
  <ElDialog
    :model-value="visible"
    title="文件详情"
    width="640px"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
  >
    <div v-if="fileObject" class="file-detail">
      <!-- PUBLIC 图片预览 -->
      <div v-if="isImagePreviewable" class="file-detail__preview">
        <img :src="previewUrl" alt="文件预览" />
      </div>

      <ElDescriptions :column="2" border>
        <ElDescriptionsItem label="对象编号">{{ fileObject.objectId ?? '--' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="文件名">{{ fileObject.originalName || '--' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="业务类型">
          <DictTag dict-key="file_business_type" :value="fileObject.businessType" />
        </ElDescriptionsItem>
        <ElDescriptionsItem label="访问语义">
          <DictTag dict-key="file_access" :value="fileObject.access" />
        </ElDescriptionsItem>
        <ElDescriptionsItem label="上传状态">
          <DictTag dict-key="upload_status" :value="fileObject.uploadStatus" />
        </ElDescriptionsItem>
        <ElDescriptionsItem label="大小">{{ formatFileSize(fileObject.contentLength) }}</ElDescriptionsItem>
        <ElDescriptionsItem label="MIME 类型">{{ fileObject.contentType || '--' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="业务关联">{{ fileObject.bizRefId ?? '--' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="校验值" :span="2">{{ fileObject.checksum || '--' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="对象 key" :span="2">{{ fileObject.objectKey || '--' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="上传人">{{ fileObject.createBy || '--' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="创建时间">{{ formatTime(fileObject.createTime) }}</ElDescriptionsItem>
      </ElDescriptions>
    </div>

    <template #footer>
      <div class="file-detail__footer">
        <ElButton @click="emit('update:visible', false)">关闭</ElButton>
      </div>
    </template>
  </ElDialog>
</template>

<style scoped>
.file-detail {
  display: grid;
  gap: 16px;
}

.file-detail__preview {
  border-radius: var(--rookie-radius-md);
  overflow: hidden;
  border: 1px solid var(--rookie-border);
  background: var(--rookie-surface-muted);
}

.file-detail__preview img {
  display: block;
  width: 100%;
  max-height: 280px;
  object-fit: contain;
}

.file-detail__footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
