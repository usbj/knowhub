<!--
  文件作用：
  文件对象详情只读弹窗，承接文件列表「详情」操作后的元数据展示。
  关键参数：
  - `visible`：弹窗显隐，由父层双向绑定控制。
  - `fileObject`：当前展示的文件对象记录。
  关键依赖：
  - 复用 ElDialog + ElDescriptions 展示元数据；
  - businessType / access / uploadStatus 走字典标签系统渲染为带色 ElTag；
  - PUBLIC 对象且为图片类型时展示预览缩略图（/file/resolve/{id} 稳定解析引用，后端按模式 302 分发）；
  - 主题适配：描述列表、预览框均用 base.css 的 --rookie-* 变量，深浅模式自动跟随。
-->
<script setup lang="ts">
import { computed } from 'vue'
import { ElButton, ElDescriptions, ElDescriptionsItem, ElDialog } from 'element-plus'
import DictTag from '@/components/DictTag.vue'
import { formatDateTime } from '@/utils/format'
import { formatFileSize } from '../config'
import { buildFileResolveUrl } from '@/api/knowhub/file'
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
 * PUBLIC 图片预览地址：/file/resolve/{objectId} 稳定解析引用，渲染时后端按当前访问模式 302 分发
 * （中转→/file/public/{id} 字节流回显；直链→OSS 公开读直链或私有预签名）。
 */
const previewUrl = computed(() =>
  props.fileObject?.objectId ? buildFileResolveUrl(props.fileObject.objectId) : '',
)
</script>

<template>
  <ElDialog
    :model-value="visible"
    title="文件详情"
    width="640px"
    class="file-detail-dialog"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
  >
    <div v-if="fileObject" class="file-detail">
      <!-- PUBLIC 图片预览 -->
      <div v-if="isImagePreviewable" class="file-detail__preview">
        <img :src="previewUrl" alt="文件预览" />
      </div>

      <!--
        元数据描述列表：
        - :column="2" 两列布局，短字段一行两个；长字段（文件名/校验值/对象 key/MIME）用 :span="2" 独占整行
          并加 class-name="file-detail__cell-break" 强制长字符串换行，避免撑破弹窗宽度。
        - 长字段独占整行而非两列，是因为 objectKey（含日期/uuid 路径）、checksum（32 位 hash）、
          contentType（如 image/png; charset=...）等连续字符串不可自然断词，两列下会更窄更易溢出。
      -->
      <ElDescriptions :column="2" border>
        <ElDescriptionsItem label="对象编号">{{ fileObject.objectId ?? '--' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="大小">{{ formatFileSize(fileObject.contentLength) }}</ElDescriptionsItem>
        <ElDescriptionsItem label="业务类型">
          <DictTag dict-key="file_business_type" :value="fileObject.businessType" />
        </ElDescriptionsItem>
        <ElDescriptionsItem label="访问语义">
          <DictTag dict-key="file_access" :value="fileObject.access" />
        </ElDescriptionsItem>
        <ElDescriptionsItem label="上传状态">
          <DictTag dict-key="upload_status" :value="fileObject.uploadStatus" />
        </ElDescriptionsItem>
        <ElDescriptionsItem label="业务关联">{{ fileObject.bizRefId ?? '--' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="上传人">{{ fileObject.createBy || '--' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="创建时间">{{ formatTime(fileObject.createTime) }}</ElDescriptionsItem>
        <ElDescriptionsItem label="文件名" :span="2" class-name="file-detail__cell-break">
          {{ fileObject.originalName || '--' }}
        </ElDescriptionsItem>
        <ElDescriptionsItem label="MIME 类型" :span="2" class-name="file-detail__cell-break">
          {{ fileObject.contentType || '--' }}
        </ElDescriptionsItem>
        <ElDescriptionsItem label="对象 key" :span="2" class-name="file-detail__cell-break">
          {{ fileObject.objectKey || '--' }}
        </ElDescriptionsItem>
        <ElDescriptionsItem label="校验值" :span="2" class-name="file-detail__cell-break">
          {{ fileObject.checksum || '--' }}
        </ElDescriptionsItem>
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
/*
 * 弹窗体限高 + 滚动：内容超长（长 objectKey / 大预览图）时不撑出视口，
 * 滚动条跟随主题变量。ElDialog body 由 :deep() 命中 Element Plus 内部类。
 */
.file-detail-dialog :deep(.el-dialog__body) {
  max-height: 70vh;
  overflow-y: auto;
}

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

/*
 * 长字符串单元格强制换行：
 * word-break: break-all 允许在任意字符间断行（连续 hash / 路径无自然断词点），
 * overflow-wrap: break-all 兜底极长单词；配合 td 自身布局防溢出。
 * class-name 加在 ElDescriptionsItem 内容 td 上，scoped 下需 :deep() 穿透。
 */
.file-detail-dialog :deep(.file-detail__cell-break) {
  word-break: break-all;
  overflow-wrap: break-anywhere;
}

.file-detail__footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
