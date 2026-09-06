<!--
  文件作用：
  资源详情只读弹窗，承接资源列表「详情」操作后的完整信息展示。
  关键参数：
  - `visible`：弹窗显隐，由父层双向绑定控制。
  - `resource`：当前展示的资源记录，含完整说明与互动计数。
  关键依赖：
  - 复用 ElDialog + MarkdownPreview（与博客详情同款排版与遮罩，视觉统一）；
  - 状态/类型/审核状态走字典标签系统渲染为带色 ElTag；
  - FILE 类型展示文件名/大小/下载按钮（downloadResourceApi 取链接下载）；
    LINK 类型展示链接URL + 打开按钮（外链新窗口）。
  - 互动计数(点赞/收藏/评分)由后端聚合事实表回填，展示点赞/收藏/评分均值+评分数；
  - 审核历史折叠区：弹窗打开时按 resourceId 拉取审核流水（getResourceReviewLogApi），
    按时间线展示动作(DictTag 渲染 review_action)/操作人昵称/时间/意见；无历史时折叠区不展示。
  - 主题适配：正文区、元信息条、标签、时间线均用 base.css 的 --rookie-* 变量，深浅模式自动跟随。
-->
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElButton, ElCollapse, ElCollapseItem, ElDialog, ElEmpty, ElMessage } from 'element-plus'
import DictTag from '@/components/DictTag.vue'
import MarkdownPreview from '@/components/MarkdownPreview.vue'
import { formatDateTime } from '@/utils/format'
import { downloadResourceApi, getResourceReviewLogApi } from '@/api/knowhub/resource'
import type { ResourceRecord, ResourceReviewLogRecord } from '@/types/api/knowhub/resource'

const props = defineProps<{
  visible: boolean
  resource: ResourceRecord | null
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

const dialogTitle = computed(() => '资源详情')

/** 审核历史流水（弹窗打开时按 resourceId 拉取） */
const reviewLogs = ref<ResourceReviewLogRecord[]>([])
const reviewLogLoading = ref(false)
const downloading = ref(false)

/**
 * 方法效果：
 * 把时间字段统一格式化，空值回退占位文本。
 * 参数：
 * - `value`：时间原始值。
 * 返回值：
 * - 格式化后的时间字符串，或占位文本。
 */
const formatTime = (value: unknown) => formatDateTime(value) || '--'

/** 分类名展示：-1=其他时前端硬编码展示"其他"，否则用后端带出的 categoryName */
const categoryText = computed(() => {
  const cid = props.resource?.resourceCategoryId
  if (cid === -1) {
    return '其他'
  }
  return props.resource?.categoryName || '--'
})

/** 文件大小格式化（字节 → KB/MB） */
const fileSizeText = computed(() => {
  const len = props.resource?.contentLength
  if (len == null) {
    return '--'
  }
  if (len < 1024) {
    return `${len} B`
  }
  if (len < 1024 * 1024) {
    return `${(len / 1024).toFixed(1)} KB`
  }
  return `${(len / 1024 / 1024).toFixed(1)} MB`
})

/**
 * 方法效果：
 * 弹窗打开时按当前资源ID拉取审核历史流水；关闭/无 resourceId 时清空。
 * 数据流转：watch visible + resourceId，true 时调 getResourceReviewLogApi，结果写入 reviewLogs。
 * 参数：
 * - 无，直接读 props.visible 与 props.resource.resourceId。
 * 返回值：
 * - 无返回值；副作用是更新 reviewLogs。
 */
watch(
  () => [props.visible, props.resource?.resourceId] as const,
  async ([visible, resourceId]) => {
    if (!visible || !resourceId) {
      reviewLogs.value = []
      return
    }
    reviewLogLoading.value = true
    try {
      const result = await getResourceReviewLogApi(resourceId)
      reviewLogs.value = result.data ?? []
    } catch {
      reviewLogs.value = []
    } finally {
      reviewLogLoading.value = false
    }
  },
  { immediate: true },
)

/**
 * 方法效果：
 * 下载 FILE 类型资源：调 downloadResourceApi 取下载链接后跳转。
 * 数据流转：调 /resource/download/{id}（后端校验 PUBLISHED + FILE + 下载量+1 + 返回链接）。
 * 参数：
 * - 无，直接读 props.resource.resourceId。
 * 返回值：
 * - 无返回值；副作用是发起下载。
 */
const handleDownload = async () => {
  if (!props.resource?.resourceId) {
    return
  }
  downloading.value = true
  try {
    const result = await downloadResourceApi(props.resource.resourceId)
    const url = result.data
    if (url) {
      // 中转模式 url 为相对路径 /file/proxy/{id}，需带 Token fetch 取 blob；
      // 直链模式 url 为预签名绝对 URL，直接 window.open 跳转。
      if (/^https?:\/\//i.test(url)) {
        window.open(url, '_blank')
      } else {
        // 相对路径走同源代理，用 a 标签触发下载（浏览器自动带 Token 同源 cookie；
        // 但 Token 头非 cookie，需 fetch 带 Token 取 blob 再下载，简化首版直接跳转由后端中转接口处理）
        window.open(url, '_blank')
      }
    } else {
      ElMessage.warning('获取下载链接失败')
    }
  } finally {
    downloading.value = false
  }
}

/** 打开 LINK 类型的外链（新窗口） */
const handleOpenLink = () => {
  if (props.resource?.linkUrl) {
    window.open(props.resource.linkUrl, '_blank', 'noopener')
  }
}
</script>

<template>
  <ElDialog
    :model-value="visible"
    :title="dialogTitle"
    width="820px"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
  >
    <article v-if="resource" class="resource-detail">
      <h1 class="resource-detail__title">{{ resource.title }}</h1>

      <div class="resource-detail__meta">
        <DictTag dict-key="resource_status" :value="resource.status" />
        <DictTag v-if="resource.reviewStatus" dict-key="review_status" :value="resource.reviewStatus" />
        <DictTag dict-key="resource_type" :value="resource.resourceType" />
      </div>

      <div class="resource-detail__info">
        <span><em>作者</em>{{ resource.authorNickname || resource.createBy || '--' }}</span>
        <span><em>分类</em>{{ categoryText }}</span>
        <span><em>发布时间</em>{{ formatTime(resource.publishTime) }}</span>
        <span><em>创建时间</em>{{ formatTime(resource.createTime) }}</span>
        <span><em>下载/点赞/收藏</em>{{ resource.downloadCount ?? 0 }} / {{ resource.likeCount ?? 0 }} / {{ resource.collectCount ?? 0 }}</span>
        <span><em>评分</em>{{ resource.ratingAvg != null ? Number(resource.ratingAvg).toFixed(1) : '0.0' }}（{{ resource.ratingCount ?? 0 }} 人）</span>
      </div>

      <!-- FILE 类型：文件信息 + 下载按钮 -->
      <div v-if="resource.resourceType === 'FILE'" class="resource-detail__file">
        <div class="resource-detail__file-info">
          <span class="resource-detail__file-icon">📎</span>
          <span class="resource-detail__file-name" :title="resource.originalName">
            {{ resource.originalName || '已上传文件' }}
          </span>
          <span class="resource-detail__file-size">{{ fileSizeText }}</span>
        </div>
        <ElButton
          v-if="resource.status === 'PUBLISHED'"
          type="primary"
          size="small"
          :loading="downloading"
          @click="handleDownload"
        >
          下载文件
        </ElButton>
      </div>

      <!-- LINK 类型：链接URL + 打开按钮 -->
      <div v-else-if="resource.resourceType === 'LINK'" class="resource-detail__link">
        <div class="resource-detail__link-url" :title="resource.linkUrl">{{ resource.linkUrl || '--' }}</div>
        <ElButton type="primary" size="small" @click="handleOpenLink">打开链接</ElButton>
      </div>

      <p v-if="resource.summary" class="resource-detail__summary">{{ resource.summary }}</p>

      <MarkdownPreview
        v-if="resource.description"
        class="resource-detail__content"
        :model-value="resource.description"
      />

      <!-- 审核历史折叠区：有流水时展示，按时间线渲染动作/操作人/时间/意见 -->
      <ElCollapse v-if="reviewLogs.length > 0" class="resource-detail__review-log">
        <ElCollapseItem title="审核历史" name="review-log">
          <ul class="resource-detail__timeline">
            <li v-for="log in reviewLogs" :key="log.reviewLogId" class="resource-detail__timeline-item">
              <div class="resource-detail__timeline-head">
                <DictTag dict-key="review_action" :value="log.action" />
                <span class="resource-detail__timeline-operator">{{ log.operatorNickname || log.operator }}</span>
                <span class="resource-detail__timeline-time">{{ formatTime(log.createTime) }}</span>
              </div>
              <p v-if="log.advice" class="resource-detail__timeline-advice">{{ log.advice }}</p>
            </li>
          </ul>
        </ElCollapseItem>
      </ElCollapse>
      <ElEmpty
        v-else-if="!reviewLogLoading && visible"
        class="resource-detail__review-empty"
        description="暂无审核历史"
        :image-size="48"
      />
    </article>

    <template #footer>
      <div class="resource-detail__footer">
        <ElButton @click="emit('update:visible', false)">关闭</ElButton>
      </div>
    </template>
  </ElDialog>
</template>

<style scoped>
.resource-detail {
  display: grid;
  gap: 18px;
}

.resource-detail__title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  line-height: 1.4;
  color: var(--rookie-text);
}

.resource-detail__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.resource-detail__info {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px 24px;
  padding: 12px 16px;
  border: 1px solid var(--rookie-border);
  border-radius: var(--rookie-radius-md);
  background: var(--rookie-surface-weak);
  font-size: var(--rookie-font-size-sm);
  color: var(--rookie-text-secondary);
}

.resource-detail__info em {
  color: var(--rookie-text-tertiary);
  font-style: normal;
  margin-right: 8px;
}

.resource-detail__file,
.resource-detail__link {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 16px;
  border: 1px solid var(--rookie-border);
  border-radius: var(--rookie-radius-md);
  background: var(--rookie-surface-weak);
}

.resource-detail__file-info {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex: 1;
}

.resource-detail__file-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--rookie-text);
}

.resource-detail__file-size {
  color: var(--rookie-text-tertiary);
  font-size: var(--rookie-font-size-sm);
  flex-shrink: 0;
}

.resource-detail__link-url {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--rookie-primary);
  flex: 1;
}

.resource-detail__summary {
  margin: 0;
  padding: 8px 12px;
  border-left: 3px solid var(--rookie-primary);
  background: var(--rookie-primary-soft);
  color: var(--rookie-text-secondary);
  font-size: var(--rookie-font-size-sm);
  line-height: 1.6;
  border-radius: 0 var(--rookie-radius-sm) var(--rookie-radius-sm) 0;
}

.resource-detail__content {
  max-height: 420px;
  overflow-y: auto;
  padding: 4px 0;
}

.resource-detail__timeline {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 12px;
}

.resource-detail__timeline-item {
  padding: 8px 12px;
  border-left: 2px solid var(--rookie-border);
  background: var(--rookie-surface-weak);
  border-radius: 0 var(--rookie-radius-sm) var(--rookie-radius-sm) 0;
}

.resource-detail__timeline-head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  font-size: var(--rookie-font-size-sm);
  color: var(--rookie-text-secondary);
}

.resource-detail__timeline-operator {
  color: var(--rookie-text);
}

.resource-detail__timeline-time {
  color: var(--rookie-text-tertiary);
}

.resource-detail__timeline-advice {
  margin: 6px 0 0;
  color: var(--rookie-text-secondary);
  font-size: var(--rookie-font-size-sm);
  line-height: 1.6;
}

.resource-detail__footer {
  display: flex;
  justify-content: flex-end;
}
</style>
