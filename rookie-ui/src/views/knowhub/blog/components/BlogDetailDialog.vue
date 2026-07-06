<!--
  文件作用：
  博客文章详情只读弹窗，承接博客列表「详情」操作后的完整正文展示。
  关键参数：
  - `visible`：弹窗显隐，由父层双向绑定控制。
  - `blog`：当前展示的博客记录，含完整正文与审核信息。
  关键依赖：
  - 复用 ElDialog + MarkdownPreview（与通知详情同款排版与遮罩，视觉统一）；
  - 状态/审核状态走字典标签系统渲染为带色 ElTag；
  - 封面图 coverUrl 为 /file/public/{id} 相对路径，<img> 直引靠 /file 代理到后端中转回写字节流。
  - 审核历史折叠区：弹窗打开时按 blogId 拉取审核流水（getReviewLogApi），按时间线展示
    动作(DictTag 渲染 blog_review_action)/操作人昵称/时间/意见；无历史时折叠区不展示。
  - 主题适配：正文区、元信息条、标签、时间线均用 base.css 的 --rookie-* 变量，深浅模式自动跟随。
-->
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElButton, ElCollapse, ElCollapseItem, ElDialog, ElEmpty } from 'element-plus'
import DictTag from '@/components/DictTag.vue'
import MarkdownPreview from '@/components/MarkdownPreview.vue'
import { formatDateTime } from '@/utils/format'
import { getReviewLogApi } from '@/api/knowhub/blog'
import type { BlogRecord, ReviewLogRecord } from '@/types/api/knowhub/blog'

const props = defineProps<{
  visible: boolean
  blog: BlogRecord | null
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

const dialogTitle = computed(() => '博客详情')

/** 审核历史流水（弹窗打开时按 blogId 拉取） */
const reviewLogs = ref<ReviewLogRecord[]>([])
const reviewLogLoading = ref(false)

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
 * 关联标签展示为标签名拼接，空时回退占位文本。
 */
const tagText = computed(() => props.blog?.tagNames?.join('、') || '--')

/**
 * 方法效果：
 * 弹窗打开时按当前博客ID拉取审核历史流水；关闭/无 blogId 时清空。
 * 数据流转：watch visible + blogId，true 时调 getReviewLogApi，结果写入 reviewLogs。
 * 参数：
 * - 无，直接读 props.visible 与 props.blog.blogId。
 * 返回值：
 * - 无返回值；副作用是更新 reviewLogs。
 */
watch(
  () => [props.visible, props.blog?.blogId] as const,
  async ([visible, blogId]) => {
    if (!visible || !blogId) {
      reviewLogs.value = []
      return
    }
    reviewLogLoading.value = true
    try {
      const result = await getReviewLogApi(blogId)
      reviewLogs.value = result.data ?? []
    } catch {
      reviewLogs.value = []
    } finally {
      reviewLogLoading.value = false
    }
  },
  { immediate: true },
)
</script>

<template>
  <ElDialog
    :model-value="visible"
    :title="dialogTitle"
    width="820px"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
  >
    <article v-if="blog" class="blog-detail">
      <h1 class="blog-detail__title">{{ blog.title }}</h1>

      <div class="blog-detail__meta">
        <DictTag dict-key="blog_status" :value="blog.status" />
        <DictTag v-if="blog.reviewStatus" dict-key="review_status" :value="blog.reviewStatus" />
      </div>

      <div class="blog-detail__cover" v-if="blog.coverUrl">
        <img :src="blog.coverUrl" alt="封面图" />
      </div>

      <div class="blog-detail__info">
        <span><em>作者</em>{{ blog.createBy || '--' }}</span>
        <span><em>发布时间</em>{{ formatTime(blog.publishTime) }}</span>
        <span><em>创建时间</em>{{ formatTime(blog.createTime) }}</span>
        <span><em>关联标签</em>{{ tagText }}</span>
        <span><em>浏览/点赞/收藏</em>{{ blog.viewCount ?? 0 }} / {{ blog.likeCount ?? 0 }} / {{ blog.collectCount ?? 0 }}</span>
        <span v-if="blog.reviewer" class="blog-detail__info-full"><em>审核人</em>{{ blog.reviewer }}（{{ formatTime(blog.reviewTime) }}）</span>
        <span v-if="blog.reviewAdvice" class="blog-detail__info-full"><em>审核意见</em>{{ blog.reviewAdvice }}</span>
      </div>

      <p v-if="blog.summary" class="blog-detail__summary">{{ blog.summary }}</p>

      <MarkdownPreview
        class="blog-detail__content"
        :model-value="blog.content || ''"
      />

      <!-- 审核历史折叠区：有流水时展示，按时间线渲染动作/操作人/时间/意见 -->
      <ElCollapse v-if="reviewLogs.length > 0" class="blog-detail__review-log">
        <ElCollapseItem title="审核历史" name="review-log">
          <ul class="blog-detail__timeline">
            <li v-for="log in reviewLogs" :key="log.reviewLogId" class="blog-detail__timeline-item">
              <div class="blog-detail__timeline-head">
                <DictTag dict-key="blog_review_action" :value="log.action" />
                <span class="blog-detail__timeline-operator">{{ log.operatorNickname || log.operator }}</span>
                <span class="blog-detail__timeline-time">{{ formatTime(log.createTime) }}</span>
              </div>
              <p v-if="log.advice" class="blog-detail__timeline-advice">{{ log.advice }}</p>
            </li>
          </ul>
        </ElCollapseItem>
      </ElCollapse>
      <ElEmpty
        v-else-if="!reviewLogLoading && visible"
        class="blog-detail__review-empty"
        description="暂无审核历史"
        :image-size="48"
      />
    </article>

    <template #footer>
      <div class="blog-detail__footer">
        <ElButton @click="emit('update:visible', false)">关闭</ElButton>
      </div>
    </template>
  </ElDialog>
</template>

<style scoped>
.blog-detail {
  display: grid;
  gap: 18px;
}

.blog-detail__title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  line-height: 1.4;
  color: var(--rookie-text);
}

.blog-detail__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.blog-detail__cover {
  border-radius: var(--rookie-radius-md);
  overflow: hidden;
  border: 1px solid var(--rookie-border);
  background: var(--rookie-surface-muted);
}

.blog-detail__cover img {
  display: block;
  width: 100%;
  max-height: 320px;
  object-fit: cover;
}

.blog-detail__info {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px 24px;
  padding: 12px 16px;
  border-radius: var(--rookie-radius-md);
  background: var(--rookie-surface-weak);
  border: 1px solid var(--rookie-border);
  font-size: var(--rookie-font-size-sm);
  color: var(--rookie-text-secondary);
}

/* 审核人/审核意见整行展示，避免长文本被两列挤压截断 */
.blog-detail__info-full {
  grid-column: 1 / -1;
}

.blog-detail__info em {
  font-style: normal;
  color: var(--rookie-text-tertiary);
  margin-right: 8px;
}

.blog-detail__summary {
  margin: 0;
  padding: 10px 14px;
  border-left: 3px solid var(--rookie-primary);
  background: var(--rookie-primary-soft);
  color: var(--rookie-text-secondary);
  font-size: var(--rookie-font-size-sm);
  line-height: 1.6;
  border-radius: 0 var(--rookie-radius-sm) var(--rookie-radius-sm) 0;
}

.blog-detail__content {
  padding: 4px 0;
  min-height: 80px;
  max-height: 52vh;
  overflow-y: auto;
}

/* 审核历史折叠区：时间线样式，动作标签 + 操作人 + 时间 + 意见 */
.blog-detail__review-log {
  border-top: 1px solid var(--rookie-border);
  padding-top: 8px;
}

.blog-detail__timeline {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 12px;
}

.blog-detail__timeline-item {
  padding: 8px 12px;
  border-radius: var(--rookie-radius-sm);
  background: var(--rookie-surface-weak);
  border: 1px solid var(--rookie-border);
}

.blog-detail__timeline-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  font-size: var(--rookie-font-size-sm);
}

.blog-detail__timeline-operator {
  color: var(--rookie-text);
  font-weight: 500;
}

.blog-detail__timeline-time {
  color: var(--rookie-text-tertiary);
}

.blog-detail__timeline-advice {
  margin: 6px 0 0;
  padding: 6px 10px;
  border-left: 2px solid var(--rookie-border);
  color: var(--rookie-text-secondary);
  font-size: var(--rookie-font-size-sm);
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
}

.blog-detail__review-empty {
  padding: 12px 0;
}

.blog-detail__footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
