<!--
  文件作用：
  章节详情/审核双用弹窗：
  - reviewMode=false：章节详情只读展示（元信息 + 正文 MarkdownPreview + 审核历史折叠区），不承担改数据职责。
  - reviewMode=true：章节作者审核弹窗（仅给通过/驳回+意见 + 正文预览），审核结果走 emit submit。
  关键参数：
  - `visible`：弹窗显隐，父层双向绑定。
  - `chapter`：当前章节记录（含正文 content + join 带出的所属文章信息）。
  - `reviewMode`：true=审核模式（显示审核表单+提交按钮），false=详情只读模式。
  - `reviewForm`：审核表单（pass/advice），仅在 reviewMode=true 时使用，父层双向绑定。
  - `reviewLoading`：审核提交中状态。
  关键依赖：
  - 复用 ElDialog + MarkdownPreview（正文只读渲染）；
  - 状态/可见性/审核状态走字典标签系统渲染为带色 ElTag；
  - 审核历史折叠区：弹窗打开时按 chapterId 拉取章节审核流水（仅 SEMIPUBLIC 场景有）。
  - 主题适配：正文区、元信息条、标签、时间线均用 base.css 的 --rookie-* 变量，深浅模式自动跟随。
-->
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  ElButton,
  ElCollapse,
  ElCollapseItem,
  ElDialog,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElRadio,
  ElRadioGroup,
} from 'element-plus'
import DictTag from '@/components/DictTag.vue'
import MarkdownPreview from '@/components/MarkdownPreview.vue'
import { formatDateTime } from '@/utils/format'
import { getChapterReviewLogApi } from '@/api/knowhub/chapter'
import type { ChapterRecord, ChapterReviewLogRecord } from '@/types/api/knowhub/chapter'

const props = defineProps<{
  visible: boolean
  chapter: ChapterRecord | null
  reviewMode?: boolean
  /** 审核表单（pass/advice），仅 reviewMode=true 时使用 */
  reviewForm?: { pass: boolean; advice: string }
  reviewLoading?: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  /** 审核提交（pass/advice），仅 reviewMode=true 时触发 */
  submit: []
}>()

const dialogTitle = computed(() => (props.reviewMode ? '章节审核' : '章节详情'))

const reviewLogs = ref<ChapterReviewLogRecord[]>([])
const reviewLogLoading = ref(false)

const formatTime = (value: unknown) => formatDateTime(value) || '--'

/**
 * 内部审查表单副本：当 reviewMode=true 时，从 props.reviewForm 透传，emit submit 让父层处理。
 * 这里用一个本地 ref 镜像，便于 v-model 双向绑定，submit 时同步回父层再 emit。
 */
const localPass = ref(true)
const localAdvice = ref('')

watch(
  () => [props.visible, props.chapter?.chapterId, props.reviewForm] as const,
  async ([visible, chapterId, reviewForm]) => {
    if (props.reviewMode) {
      // 审核模式：同步父层 reviewForm 到本地镜像
      localPass.value = reviewForm?.pass ?? true
      localAdvice.value = reviewForm?.advice ?? ''
    }
    if (!visible || !chapterId) {
      reviewLogs.value = []
      return
    }
    // 拉章节审核历史（仅 SEMIPUBLIC 场景有记录）
    reviewLogLoading.value = true
    try {
      const result = await getChapterReviewLogApi(chapterId)
      reviewLogs.value = result.data ?? []
    } catch {
      reviewLogs.value = []
    } finally {
      reviewLogLoading.value = false
    }
  },
  { immediate: true },
)

/** 同步本地审查表单回父层 reviewForm，再 emit submit */
const handleSubmit = () => {
  if (props.reviewForm) {
    props.reviewForm.pass = localPass.value
    props.reviewForm.advice = localAdvice.value
  }
  emit('submit')
}

const close = () => {
  emit('update:visible', false)
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
    <article v-if="chapter" class="chapter-detail">
      <h1 class="chapter-detail__title">{{ chapter.chapterName }}</h1>

      <div class="chapter-detail__meta">
        <DictTag dict-key="chapter_status" :value="chapter.status" />
        <DictTag v-if="chapter.reviewStatus" dict-key="review_status" :value="chapter.reviewStatus" />
        <DictTag v-if="chapter.articleVisibility" dict-key="article_visibility" :value="chapter.articleVisibility" />
      </div>

      <div class="chapter-detail__info">
        <span><em>章节作者</em>{{ chapter.authorNickname || chapter.createBy || '--' }}</span>
        <span><em>发布时间</em>{{ formatTime(chapter.publishTime) }}</span>
        <span><em>创建时间</em>{{ formatTime(chapter.createTime) }}</span>
      </div>

      <!-- 正文 MarkdownPreview 只读渲染 -->
      <section class="chapter-detail__content-section">
        <h3>正文</h3>
        <MarkdownPreview
          v-if="chapter.content"
          class="chapter-detail__content"
          :model-value="chapter.content"
        />
        <ElEmpty v-else description="暂无正文" :image-size="48" />
      </section>

      <!-- 审核历史折叠区（仅 SEMIPUBLIC 场景有记录） -->
      <section v-if="!reviewMode" class="chapter-detail__review">
        <h3>审核历史</h3>
        <ElCollapse v-if="reviewLogs.length > 0" class="chapter-detail__review-log">
          <ElCollapseItem title="章节审核历史" name="review-log">
            <ul class="chapter-detail__timeline">
              <li v-for="log in reviewLogs" :key="log.reviewLogId" class="chapter-detail__timeline-item">
                <div class="chapter-detail__timeline-head">
                  <DictTag dict-key="review_action" :value="log.action" />
                  <span class="chapter-detail__timeline-operator">{{ log.operatorNickname || log.operator }}</span>
                  <span class="chapter-detail__timeline-time">{{ formatTime(log.createTime) }}</span>
                </div>
                <p v-if="log.advice" class="chapter-detail__timeline-advice">{{ log.advice }}</p>
              </li>
            </ul>
          </ElCollapseItem>
        </ElCollapse>
        <ElEmpty
          v-else-if="!reviewLogLoading"
          description="暂无审核历史（仅半公开文章非作者提交的章节会有审核记录）"
          :image-size="48"
        />
      </section>

      <!-- 审核模式：通过/驳回 + 意见 -->
      <section v-if="reviewMode" class="chapter-detail__review-form">
        <h3>审核结果</h3>
        <ElForm label-width="80px">
          <ElFormItem label="审核结果" required>
            <ElRadioGroup v-model="localPass">
              <ElRadio :value="true">通过</ElRadio>
              <ElRadio :value="false">驳回</ElRadio>
            </ElRadioGroup>
          </ElFormItem>
          <ElFormItem label="审核意见" :required="!localPass">
            <ElInput
              v-model="localAdvice"
              type="textarea"
              :rows="3"
              :placeholder="localPass ? '通过意见（可选）' : '驳回意见（必填）'"
              maxlength="500"
              show-word-limit
            />
          </ElFormItem>
        </ElForm>
      </section>
    </article>

    <template #footer>
      <div class="chapter-detail__footer">
        <ElButton @click="close">{{ reviewMode ? '取消' : '关闭' }}</ElButton>
        <ElButton v-if="reviewMode" type="primary" :loading="reviewLoading" @click="handleSubmit">
          提交审核
        </ElButton>
      </div>
    </template>
  </ElDialog>
</template>

<style scoped>
.chapter-detail {
  display: grid;
  gap: 18px;
}

.chapter-detail__title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  line-height: 1.4;
  color: var(--rookie-text);
}

.chapter-detail__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.chapter-detail__info {
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

.chapter-detail__info em {
  color: var(--rookie-text-tertiary);
  font-style: normal;
  margin-right: 8px;
}

.chapter-detail__content-section h3,
.chapter-detail__review h3,
.chapter-detail__review-form h3 {
  margin: 0 0 10px;
  font-size: var(--rookie-font-size-base);
  color: var(--rookie-text);
}

.chapter-detail__content {
  max-height: 420px;
  overflow-y: auto;
  padding: 4px 0;
}

.chapter-detail__timeline {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 12px;
}

.chapter-detail__timeline-item {
  padding: 8px 12px;
  border-left: 2px solid var(--rookie-border);
  background: var(--rookie-surface-weak);
  border-radius: 0 var(--rookie-radius-sm) var(--rookie-radius-sm) 0;
}

.chapter-detail__timeline-head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  font-size: var(--rookie-font-size-sm);
  color: var(--rookie-text-secondary);
}

.chapter-detail__timeline-operator {
  color: var(--rookie-text);
}

.chapter-detail__timeline-time {
  color: var(--rookie-text-tertiary);
}

.chapter-detail__timeline-advice {
  margin: 6px 0 0;
  color: var(--rookie-text-secondary);
  font-size: var(--rookie-font-size-sm);
  line-height: 1.6;
}

.chapter-detail__footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>