/**
 * 文件作用：
 * 通知详情只读弹窗，承接头导航通知下拉点击后的完整正文展示。
 * 关键参数：
 * - `visible`：弹窗显隐，由父层双向绑定控制。
 * - `notice`：当前展示的通知记录，含完整正文与枚举字段。
 * 关键依赖：
 * - 复用 ElDialog（与公共表单弹窗同款遮罩与动效，保持背景视觉统一）；
 * - 排版按博客文章习惯：大标题 → 元信息标签条 → 正文 Markdown 渲染 → 备注；
 * - 枚举字段（类型/级别/发布范围）走字典标签系统渲染为带色 ElTag，
 *   发布时间/发布者/关联分组（仅分组发布时）用文字行展示。
 * - needConfirm=1 且未确认时，弹窗右下角展示"确认"按钮，确认后隐藏。
 */
<script setup lang="ts">
import { computed } from 'vue'
import { ElButton, ElDialog } from 'element-plus'
import DictTag from '@/components/DictTag.vue'
import MarkdownPreview from '@/components/MarkdownPreview.vue'
import { formatDateTime } from '@/utils/format'
import type { SysNoticeRecord } from '@/types/api/system/notice'

const props = defineProps<{
  visible: boolean
  notice: SysNoticeRecord | null
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  confirm: [noticeId: number]
}>()

/**
 * 弹窗标题固定为"通知详情"，正文区另有大标题展示通知标题。
 */
const dialogTitle = computed(() => '通知详情')

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
 * 是否分组发布，决定关联分组行是否展示。
 */
const isGroupScope = computed(() => props.notice?.publishScope === 'GROUP')

/**
 * 关联分组展示为分组名拼接，空时回退占位文本。
 */
const groupText = computed(() => props.notice?.noticeGroups?.map((item) => item.groupName).join('、') || '--')

/**
 * 是否展示确认按钮：仅 needConfirm=1 且当前用户未确认时展示。
 * 已确认（hasConfirmed=true）后按钮隐藏。
 */
const showConfirmButton = computed(
  () => Number(props.notice?.needConfirm) === 1 && !props.notice?.hasConfirmed,
)

/**
 * 方法效果：
 * 点击确认按钮时，向父层抛出 confirm 事件，由父层调 store 确认接口。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是触发 `confirm` 事件。
 */
const handleConfirm = () => {
  if (props.notice?.noticeId != null) {
    emit('confirm', Number(props.notice.noticeId))
  }
}
</script>

<template>
  <ElDialog
    :model-value="visible"
    :title="dialogTitle"
    width="760px"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
  >
    <article v-if="notice" class="notice-detail">
      <h1 class="notice-detail__title">{{ notice.title }}</h1>

      <div class="notice-detail__meta">
        <DictTag dict-key="sys_notice_type" :value="notice.noticeType" />
        <DictTag dict-key="sys_notice_level" :value="notice.level" />
        <DictTag dict-key="sys_notice_scope" :value="notice.publishScope" />
      </div>

      <div class="notice-detail__info">
        <span><em>发布时间</em>{{ formatTime(notice.publishTime) }}</span>
        <span><em>发布者</em>{{ notice.createBy || '--' }}</span>
        <span v-if="isGroupScope" class="notice-detail__info-full"><em>关联分组</em>{{ groupText }}</span>
      </div>

      <MarkdownPreview
        class="notice-detail__content"
        :model-value="notice.content || ''"
      />

      <p v-if="notice.remark" class="notice-detail__remark">
        <em>备注</em>{{ notice.remark }}
      </p>
    </article>

    <template #footer>
      <div class="notice-detail__footer">
        <ElButton @click="emit('update:visible', false)">关闭</ElButton>
        <ElButton v-if="showConfirmButton" type="primary" @click="handleConfirm">
          确认
        </ElButton>
      </div>
    </template>
  </ElDialog>
</template>

<style scoped>
.notice-detail {
  display: grid;
  gap: 18px;
}

.notice-detail__title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  line-height: 1.4;
  color: var(--rookie-text);
}

.notice-detail__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.notice-detail__info {
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

/* 关联分组行整行展示，避免长分组名被两列挤压截断 */
.notice-detail__info-full {
  grid-column: 1 / -1;
}

.notice-detail__info em {
  font-style: normal;
  color: var(--rookie-text-tertiary);
  margin-right: 8px;
}

.notice-detail__content {
  padding: 4px 0;
  min-height: 80px;
  max-height: 52vh;
  overflow-y: auto;
}

.notice-detail__remark {
  margin: 0;
  padding-top: 12px;
  border-top: 1px dashed var(--rookie-border);
  font-size: var(--rookie-font-size-sm);
  color: var(--rookie-text-secondary);
}

.notice-detail__remark em {
  font-style: normal;
  color: var(--rookie-text-tertiary);
  margin-right: 8px;
}

.notice-detail__footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
