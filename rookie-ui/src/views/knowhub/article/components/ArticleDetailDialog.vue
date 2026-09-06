<!--
  文件作用：
  文章详情弹窗，承接文章列表「详情」操作后的完整信息展示 + 审核历史。
  文章主表不存正文（正文在章节），详情弹窗展示文章元信息（标题/等级/可见性/摘要/封面）+
  审核历史折叠区，不承担改数据职责（章节管理走独立路由页）。
  关键参数：
  - `visible`：弹窗显隐，由父层双向绑定控制。
  - `article`：当前展示的文章记录，含摘要与权限态。
  关键依赖：
  - 复用 ElDialog + MarkdownPreview（摘要用纯文本展示，封面用 img）；
  - 状态/等级/可见性/审核状态走字典标签系统渲染为带色 ElTag；
  - 审核历史折叠区：弹窗打开时按 articleId 拉取审核流水，按时间线展示动作/操作人昵称/时间/意见。
  - 主题适配：正文区、元信息条、标签、时间线均用 base.css 的 --rookie-* 变量，深浅模式自动跟随。
-->
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElButton, ElCollapse, ElCollapseItem, ElDialog, ElEmpty } from 'element-plus'
import DictTag from '@/components/DictTag.vue'
import { formatDateTime } from '@/utils/format'
import { getArticleReviewLogApi } from '@/api/knowhub/article'
import type { ArticleRecord, ArticleReviewLogRecord } from '@/types/api/knowhub/article'

const props = defineProps<{
  visible: boolean
  article: ArticleRecord | null
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

const dialogTitle = computed(() => '文章详情')

const reviewLogs = ref<ArticleReviewLogRecord[]>([])
const reviewLogLoading = ref(false)

const formatTime = (value: unknown) => formatDateTime(value) || '--'

watch(
  () => [props.visible, props.article?.articleId] as const,
  async ([visible, articleId]) => {
    if (!visible || !articleId) {
      reviewLogs.value = []
      return
    }
    reviewLogLoading.value = true
    try {
      const result = await getArticleReviewLogApi(articleId)
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
    width="760px"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
  >
    <article v-if="article" class="article-detail">
      <h1 class="article-detail__title">{{ article.title }}</h1>

      <div class="article-detail__meta">
        <DictTag dict-key="article_status" :value="article.status" />
        <DictTag v-if="article.reviewStatus" dict-key="review_status" :value="article.reviewStatus" />
        <DictTag dict-key="article_level" :value="String(article.level)" />
        <DictTag dict-key="article_visibility" :value="article.visibility" />
      </div>

      <div class="article-detail__info">
        <span><em>作者</em>{{ article.authorNickname || article.createBy || '--' }}</span>
        <span><em>发布时间</em>{{ formatTime(article.publishTime) }}</span>
        <span><em>创建时间</em>{{ formatTime(article.createTime) }}</span>
      </div>

      <!-- 封面图（如有） -->
      <img v-if="article.coverObjectKey" :src="article.coverObjectKey" alt="文章封面" class="article-detail__cover" />

      <!-- 摘要（文章简介） -->
      <div v-if="article.summary" class="article-detail__summary">
        <h3>摘要</h3>
        <p>{{ article.summary }}</p>
      </div>

      <!-- 审核历史折叠区 -->
      <section class="article-detail__review">
        <h3>审核历史</h3>
        <ElCollapse v-if="reviewLogs.length > 0" class="article-detail__review-log">
          <ElCollapseItem title="审核历史" name="review-log">
            <ul class="article-detail__timeline">
              <li v-for="log in reviewLogs" :key="log.reviewLogId" class="article-detail__timeline-item">
                <div class="article-detail__timeline-head">
                  <DictTag dict-key="review_action" :value="log.action" />
                  <span class="article-detail__timeline-operator">{{ log.operatorNickname || log.operator }}</span>
                  <span class="article-detail__timeline-time">{{ formatTime(log.createTime) }}</span>
                </div>
                <p v-if="log.advice" class="article-detail__timeline-advice">{{ log.advice }}</p>
              </li>
            </ul>
          </ElCollapseItem>
        </ElCollapse>
        <ElEmpty
          v-else-if="!reviewLogLoading"
          description="暂无审核历史"
          :image-size="48"
        />
      </section>
    </article>

    <template #footer>
      <div class="article-detail__footer">
        <ElButton @click="emit('update:visible', false)">关闭</ElButton>
      </div>
    </template>
  </ElDialog>
</template>

<style scoped>
.article-detail {
  display: grid;
  gap: 18px;
}

.article-detail__title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  line-height: 1.4;
  color: var(--rookie-text);
}

.article-detail__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.article-detail__info {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px 24px;
  padding: 12px 16px;
  border: 1px solid var(--rookie-border);
  border-radius: var(--rookie-radius-md);
  background: var(--rookie-surface-weak);
  font-size: var(--rookie-font-size-sm);
  color: var(--rookie-text-secondary);
}

.article-detail__info em {
  color: var(--rookie-text-tertiary);
  font-style: normal;
  margin-right: 8px;
}

.article-detail__cover {
  display: block;
  width: 100%;
  max-height: 280px;
  object-fit: cover;
  border-radius: var(--rookie-radius-md);
  border: 1px solid var(--rookie-border);
  background: var(--rookie-surface-muted);
}

.article-detail__summary {
  display: grid;
  gap: 6px;
  padding: 10px 14px;
  border-left: 3px solid var(--rookie-primary);
  background: var(--rookie-primary-soft);
  color: var(--rookie-text-secondary);
  font-size: var(--rookie-font-size-sm);
  line-height: 1.6;
  border-radius: 0 var(--rookie-radius-sm) var(--rookie-radius-sm) 0;
}

.article-detail__summary h3 {
  margin: 0;
  font-size: var(--rookie-font-size-base);
  color: var(--rookie-text);
}

.article-detail__summary p {
  margin: 0;
}

.article-detail__review h3 {
  margin: 0 0 10px;
  font-size: var(--rookie-font-size-base);
  color: var(--rookie-text);
}

.article-detail__timeline {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 12px;
}

.article-detail__timeline-item {
  padding: 8px 12px;
  border-left: 2px solid var(--rookie-border);
  background: var(--rookie-surface-weak);
  border-radius: 0 var(--rookie-radius-sm) var(--rookie-radius-sm) 0;
}

.article-detail__timeline-head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  font-size: var(--rookie-font-size-sm);
  color: var(--rookie-text-secondary);
}

.article-detail__timeline-operator {
  color: var(--rookie-text);
}

.article-detail__timeline-time {
  color: var(--rookie-text-tertiary);
}

.article-detail__timeline-advice {
  margin: 6px 0 0;
  color: var(--rookie-text-secondary);
  font-size: var(--rookie-font-size-sm);
  line-height: 1.6;
}

.article-detail__footer {
  display: flex;
  justify-content: flex-end;
}
</style>