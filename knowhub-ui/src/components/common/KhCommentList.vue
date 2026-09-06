<!--
  KhCommentList —— 评论区容器
  ------------------------------------------------------------------
  props: bizType/bizId/commentEnabled/commentCurated/isAuthor。
  - commentEnabled===0：显「评论区已关闭」，不渲染发表条/列表。
  - 登录可发：调 createCommentApi +prepend；非作者且 commentCurated===1 时发表条提示需作者同意后展示。
  - 顶级列表：listCommentsApi 分页 + KhPagination；每条「展开 N 条回复」懒加载 listRepliesApi。
  - 作者身份时列表天然含 PENDING 待精评论（后端谓词下发），前端逐条渲染 KhComment 作者操作按钮，无独立待审 tab/页。
  - 点赞/删除/精选：乐观更新对应条目（likeCount/like状/reviewStatus/移除）。
-->
<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import KhSectionTitle from '@/components/common/KhSectionTitle.vue'
import KhPagination from '@/components/common/KhPagination.vue'
import KhEmpty from '@/components/common/KhEmpty.vue'
import KhLoading from '@/components/common/KhLoading.vue'
import KhComment from '@/components/common/KhComment.vue'
import KhCommentInput from '@/components/common/KhCommentInput.vue'
import KhIcon from '@/components/common/KhIcon.vue'
import {
  createCommentApi,
  deleteCommentApi,
  listCommentsApi,
  listRepliesApi,
  reviewCommentApi,
  toggleCommentLikeApi,
} from '@/api/knowhub/comment'
import type {
  CommentBizType,
  CommentCreatePayload,
  CommentRecord,
  CommentReplyRecord,
} from '@/types/api/knowhub/comment'
import { useImageInsert } from '@/composables/useImageInsert'
import { useUserStore } from '@/stores/user'
import { useRouter, useRoute } from 'vue-router'

const props = defineProps<{
  bizType: CommentBizType
  bizId: number
  /** 评论区开关 1开0关 */
  commentEnabled?: number
  /** 评论精选开关 0关1开 */
  commentCurated?: number
  /** 当前用户是否该作品作者（控制 inline 审核 + 删任意） */
  isAuthor?: boolean
  /**
   * 越级锁态 flag：作品 level 高于当前用户查看等级时后端返 locked=true，越级看不了完整内容也不能发评论。
   * locked=true 时发表条与回复都禁用（看不了内容却发评论不合常理），但评论列表/点赞/删除/作者 inline 精选照常可用
   * （只锁发，不锁看——与「评论锁只是不让发评论，没说不让看」口径一致）。
   */
  locked?: boolean
}>()

const userStore = useUserStore()
const router = useRouter()
const route = useRoute()

const isLoggedIn = computed(() => userStore.isAuthenticated)
const currentUserId = computed(() => userStore.userInfo?.userId ?? null)
const closed = computed(() => props.commentEnabled === 0)
/** 非作者 + 开精选时发表条提示需作者同意后展示 */
const curatedHintForUser = computed(() =>
  props.commentCurated === 1 && !props.isAuthor
    ? '作者已开启评论精选，你的评论需作者同意后展示'
    : undefined,
)

// ---- 顶级评论列表 ----
const comments = ref<CommentRecord[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const loading = ref(false)

const fetchComments = async () => {
  loading.value = true
  try {
    const page = await listCommentsApi(props.bizType, props.bizId, {
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      order: 'new',
    })
    comments.value = page.records
    total.value = page.total
    // 默认展示前 2 条回复：顶级评论拉完后，对每个 replyCount>0 的评论预取回复（跳过 0 回复免空拉），
    // repliesMap 缓存好后模板切片前 2 条立即可见，「展开剩余」只控第 3 条及以后，无再触发二次请求。
    // 折中：一页 ≤10 顶级评论最多 +10 次小回复请求，JSON 体小可接受；换得"不点即见前 2 条"的默认体验。
    await Promise.all(
      page.records
        .filter((c) => (c.replyCount ?? 0) > 0 && !repliesMap.value[c.commentId])
        .map((c) => fetchReplies(c.commentId, 1)),
    )
  } finally {
    loading.value = false
  }
}
const onPageChange = (p: number) => {
  pageNum.value = p
  fetchComments()
}

// ---- 发评论 ----
const submitting = ref(false)
const onCreate = async (payload: { content: string }) => {
  if (!isLoggedIn.value) {
    ElMessage.warning('请先登录后再评论')
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  if (submitting.value) return
  submitting.value = true
  try {
    const vo: CommentCreatePayload = {
      bizType: props.bizType,
      bizId: props.bizId,
      content: payload.content,
    }
    const res = await createCommentApi(vo)
    const newId = res.data
    // 乐观前端插入：未刷新列表的近似展示。后端其实不返回完整 VO——刷新首页更准。这里 prepend 一条本地近似，刷新承载真实态
    ElMessage.success('评论已发送')
    // 若开精选 PENDING 态：本人可见；否则直接可见。重新拉首页确保状态/排序准确
    await fetchComments()
    void newId
  } catch {
    /* toast 已由 http 拦截器统一弹 */
  } finally {
    submitting.value = false
  }
}

// ---- 回复展开（默认显前 2 条 +「展开剩余 N 条回复」）----
/**
 * 回复展示语义（非二元开/空——与顶级评论的「展开/收起」长短文案与位置错开，避免用户看混两套"展开"按钮）：
 * - collapsed（openMap=false，默认）：reply-thread 仅渲染 repliesMap[c] 前 2 条；「展开剩余 N 条回复」按钮显第 3+ 条。
 * - expanded（openMap=true）：reply-thread 渲染全部 repliesMap[c]；按钮文案转「收起」。
 * repliesMap 在 fetchComments 阶段已预取缓存（replyCount>0 的），切换只切片不再发请求——零延迟。 */
const REPLY_PREVIEW_COUNT = 2
const openMap = ref<Record<number, boolean>>({})
const repliesMap = ref<Record<number, CommentReplyRecord[]>>({})
const repliesTotal = ref<Record<number, number>>({})
const replyLoading = ref<Record<number, boolean>>({})

const toggleReplies = (commentId: number) => {
  openMap.value[commentId] = !openMap.value[commentId]
}
/** 第 N 条回复之后还剩多少条未展示（collapsed 态：总 - 预显；expanded 态：0）。模板用它判「展开剩余」按钮显隐 + 文案计数。 */
const remainingReplies = (c: CommentRecord): number => {
  if (openMap.value[c.commentId]) return 0
  const count = c.replyCount ?? 0
  return Math.max(0, count - REPLY_PREVIEW_COUNT)
}
/** 模板 v-for 用的回复展示切片：collapsed 取前 REPLY_PREVIEW_COUNT；expanded 取全部。 */
const visibleReplies = (commentId: number): CommentReplyRecord[] => {
  const list = repliesMap.value[commentId] ?? []
  if (openMap.value[commentId]) return list
  return list.slice(0, REPLY_PREVIEW_COUNT)
}
const fetchReplies = async (commentId: number, p = 1) => {
  replyLoading.value[commentId] = true
  try {
    const page = await listRepliesApi(commentId, { pageNum: p, pageSize: 50 })
    repliesMap.value[commentId] = page.records
    repliesTotal.value[commentId] = page.total
  } finally {
    replyLoading.value[commentId] = false
  }
}

// ---- 回复某条评论（在评论项内联输入框，简化：复用 KhCommentInput） ----
/** 当前正在回复的目标：{ parentId, replyToUserId, replyToNickname }；null 表示不处于回复态 */
const replyingTo = ref<{ parentId: number; replyToUserId?: number; replyToNickname?: string } | null>(null)
const replyDraft = ref('')
/** 回复态 textarea 元素 ref：useImageInsert 读其 selectionStart/End 在光标处插配图 markdown */
const replyTextareaRef = ref<HTMLTextAreaElement | null>(null)
/**
 * 回复态配图（仅轻量 + 图按钮，P2 决策——富文切换仅在顶级 KhCommentInput）。
 * 复用 useImageInsert：选图→白名单 COMMENT_IMAGE+2MB+9 张上限→presignedUploadFlow→光标处插 ![](url) 进 replyDraft。
 */
const { uploading: replyUploading, pickFiles: pickReplyFiles } = useImageInsert({
  textareaRef: replyTextareaRef,
  contentRef: replyDraft,
  businessType: 'COMMENT_IMAGE',
  access: 'PUBLIC',
  maxImages: 9,
  maxSizeMB: 2,
})
const onPickReplyImage = () => {
  if (!isLoggedIn.value) {
    ElMessage.warning('请先登录后再传图')
    return
  }
  pickReplyFiles()
}
const onReplyClick = (p: { commentId: number; replyToUserId: number; replyToNickname: string }) => {
  // 越级锁态兜底拦回复（reply-disabled 已隐藏回复按钮，此处双保险防绕过）
  if (props.locked) {
    ElMessage.warning('等级不足，无法评论该作品')
    return
  }
  replyingTo.value = {
    parentId: p.commentId,
    replyToUserId: p.replyToUserId,
    replyToNickname: p.replyToNickname,
  }
  replyDraft.value = ''
}
const cancelReply = () => {
  replyingTo.value = null
  replyDraft.value = ''
}
const onReplySubmit = async () => {
  if (!replyingTo.value || !replyDraft.value.trim()) return
  if (!isLoggedIn.value) {
    ElMessage.warning('请先登录后再回复')
    return
  }
  submitting.value = true
  try {
    const target = replyingTo.value
    // 作者自己回复自己评价的某条时 replyToUserId 可省（=自己），后端会判 replyToUserId=楼主 留空
    let replyToUserId = target.replyToUserId
    if (replyToUserId === currentUserId.value) replyToUserId = undefined
    await createCommentApi({
      bizType: props.bizType,
      bizId: props.bizId,
      content: replyDraft.value,
      parentId: target.parentId,
      replyToUserId,
    })
    ElMessage.success('回复已发送')
    cancelReply()
    await fetchReplies(target.parentId, 1)
    // 刷新顶级列表以更新 replyCount
    await fetchComments()
  } catch {
    /* toast by interceptor */
  } finally {
    submitting.value = false
  }
}

// ---- 点赞（顶级 or 回复） ----
const onLikeTop = async (p: { commentId: number; liked: boolean }) => {
  if (!isLoggedIn.value) {
    ElMessage.warning('请先登录后再点赞')
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  try {
    await toggleCommentLikeApi(p.commentId, p.liked)
    const c = comments.value.find((x) => x.commentId === p.commentId)
    if (c) {
      c.hasLiked = p.liked
      c.likeCount = Math.max(0, (c.likeCount ?? 0) + (p.liked ? 1 : -1))
    }
  } catch {
    /* noop */
  }
}
const onLikeReply = async (parentId: number, p: { commentId: number; liked: boolean }) => {
  if (!isLoggedIn.value) {
    ElMessage.warning('请先登录后再点赞')
    return
  }
  try {
    await toggleCommentLikeApi(p.commentId, p.liked)
    const list = repliesMap.value[parentId]
    const r = list?.find((x) => x.commentId === p.commentId)
    if (r) {
      r.hasLiked = p.liked
      r.likeCount = Math.max(0, (r.likeCount ?? 0) + (p.liked ? 1 : -1))
    }
  } catch {
    /* noop */
  }
}

// ---- 删除（顶级 or 回复） ----
const onDeleteTop = async (p: { commentId: number }) => {
  try {
    await ElMessageBox.confirm('确定删除该评论？删除顶级评论会连带其下回复一并删除。', '删除确认', {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await deleteCommentApi(p.commentId)
    ElMessage.success('已删除')
    await fetchComments()
  } catch {
    /* noop */
  }
}
const onDeleteReply = async (parentId: number, p: { commentId: number }) => {
  try {
    await ElMessageBox.confirm('确定删除该回复？', '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteCommentApi(p.commentId)
    ElMessage.success('已删除')
    await fetchReplies(parentId, 1)
    await fetchComments()
  } catch {
    /* noop */
  }
}

// ---- 作者 inline 精选 ----
const onReview = async (p: { commentId: number; action: 'APPROVE' | 'REJECT' }) => {
  try {
    await reviewCommentApi({ commentId: p.commentId, action: p.action })
    ElMessage.success(p.action === 'APPROVE' ? '已同意展示' : '已拒绝')
    await fetchComments()
  } catch {
    /* noop */
  }
}

// ---- 初始化 ----
fetchComments()
</script>

<template>
  <div class="kh-comment-list">
    <KhSectionTitle title="评论" subtitle="登录后参与讨论" />

    <div v-if="closed" class="kh-comment-list__closed">评论区已关闭</div>

    <template v-else>
      <!-- 发表条（顶级）：越级锁态时不渲染发表条，改显锁态占位（评论列表照常可看，只锁发不锁看） -->
      <div v-if="locked" class="kh-comment-list__locked">
        <KhIcon name="lock" :size="16" :stroke="1.5" />
        <span>等级不足，无法评论该作品（需更高权限查看完整内容后再参与讨论）</span>
      </div>
      <KhCommentInput v-else :hint="curatedHintForUser" placeholder="写下你的评论…" @submit="onCreate" />

      <!-- 顶级评论列表：越级锁态与正常态都渲染（只锁发不锁看） -->
      <div class="kh-comment-list__items">
        <KhLoading v-if="loading" />
        <KhEmpty v-else-if="!comments.length" text="还没有评论，来发表第一条吧" />
        <template v-else>
          <template v-for="c in comments" :key="c.commentId">
            <div class="kh-comment-list__row">
              <KhComment
                :comment="c"
                :current-user-id="currentUserId"
                :is-author="isAuthor"
                :reply-disabled="locked"
                @like="onLikeTop"
                @reply="onReplyClick"
                @delete="onDeleteTop"
                @review="onReview"
              />

              <!-- 当前评论的回复内联输入框：仅正在回复这条时显示，紧跟该评论（不再统一堆到列表底部） -->
              <div
                v-if="replyingTo?.parentId === c.commentId"
                class="kh-comment-list__reply-input"
              >
                <div class="kh-comment-list__reply-head">
                  回复 @{{ replyingTo?.replyToNickname }}：
                  <button class="kh-comment-list__cancel" @click="cancelReply">取消</button>
                </div>
                <textarea
                  ref="replyTextareaRef"
                  v-model="replyDraft"
                  class="kh-comment-list__reply-textarea"
                  rows="2"
                  :placeholder="`@${replyingTo?.replyToNickname ?? ''} …`"
                />
                <div class="kh-comment-list__reply-bar">
                  <button
                    class="kh-comment-list__img"
                    type="button"
                    :disabled="!isLoggedIn || replyUploading"
                    :title="'配图（最多 9 张，单图 ≤2MB）'"
                    @click="onPickReplyImage"
                  >{{ replyUploading ? '上传中…' : '📎 图片' }}</button>
                  <button
                    class="kh-comment-list__send"
                    :disabled="!replyDraft.trim() || submitting"
                    @click="onReplySubmit"
                  >
                    发送回复
                  </button>
                </div>
              </div>

              <!-- 回复线程（默认显前 2 条）+「展开剩余 N 条回复」/「收起」：
                   与顶级评论的「展开/收起」长短文案 + 位置分别（内容下 vs 评论组尾）错开不重混。
                   线程始终渲染（collapsed 显前 REPLY_PREVIEW_COUNT 条），fetchComments 阶段已预取 repliesMap。 -->
              <div
                v-if="(c.replyCount ?? 0) > 0"
                class="kh-comment-list__reply-section"
              >
                <div class="kh-comment-list__reply-thread">
                  <KhLoading v-if="replyLoading[c.commentId]" />
                  <template v-else>
                    <KhComment
                      v-for="r in visibleReplies(c.commentId)"
                      :key="r.commentId"
                      :comment="r"
                      :is-reply="true"
                      :current-user-id="currentUserId"
                      :is-author="isAuthor"
                      @like="onLikeReply(c.commentId, $event)"
                      @delete="onDeleteReply(c.commentId, $event)"
                      @review="onReview"
                    />
                  </template>
                </div>
                <button
                  v-if="remainingReplies(c) > 0 || openMap[c.commentId]"
                  class="kh-comment-list__toggle"
                  @click="toggleReplies(c.commentId)"
                >
                  {{ openMap[c.commentId] ? '收起' : `展开剩余 ${remainingReplies(c)} 条回复` }}
                </button>
              </div>
            </div>
          </template>
        </template>
      </div>

      <!-- 分页 -->
      <KhPagination
        v-if="total > pageSize"
        :total="total"
        :page-size="pageSize"
        :current="pageNum"
        @current-change="onPageChange"
      />
    </template>
  </div>
</template>

<style scoped>
.kh-comment-list {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-4);
}
.kh-comment-list__closed {
  padding: var(--kh-space-6);
  text-align: center;
  color: var(--kh-text-muted);
  background: var(--kh-bg-soft);
  border-radius: var(--kh-radius-md);
}
/* 越级锁态发表条占位：锁图标 + 提示，置灰替代发表条（评论列表照常可看，只锁发不锁看） */
.kh-comment-list__locked {
  display: flex;
  align-items: center;
  gap: var(--kh-space-2);
  padding: var(--kh-space-4) var(--kh-space-5);
  color: var(--kh-text-muted);
  background: var(--kh-bg-soft);
  border: 1px solid var(--kh-border-soft);
  border-radius: var(--kh-radius-md);
  font-size: 13px;
}
.kh-comment-list__items {
  display: flex;
  flex-direction: column;
}
/* 每条顶级评论 + 其回复输入框 + 展开回复区 整组一个 row，分隔线画在 row 底部
   （摘出的回复线程不再落到评论区下方，且分隔线在回复之后而非之前）。 */
.kh-comment-list__row {
  border-bottom: 1px solid var(--kh-border);
}
.kh-comment-list__row:last-of-type {
  border-bottom: none;
}
/* 顶级 KhComment 自带 border-bottom 摘掉交由 row 统一画，避免展开回复时双重线 */
.kh-comment-list__row > :deep(.kh-comment) {
  border-bottom: none;
}
.kh-comment-list__reply-input {
  margin-left: calc(var(--kh-space-3) + 36px);
  padding: var(--kh-space-2) var(--kh-space-4) var(--kh-space-3);
  background: var(--kh-bg-soft);
  border-radius: var(--kh-radius-md);
}
.kh-comment-list__reply-head {
  font-size: 13px;
  color: var(--kh-text-secondary);
  margin-bottom: var(--kh-space-2);
}
.kh-comment-list__cancel {
  border: none;
  background: transparent;
  color: var(--kh-text-muted);
  cursor: pointer;
  font-size: 12px;
}
.kh-comment-list__reply-textarea {
  width: 100%;
  box-sizing: border-box;
  padding: var(--kh-space-2) var(--kh-space-3);
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius-sm);
  background: var(--kh-surface);
  color: var(--kh-text);
  font-size: 13px;
  line-height: 1.6;
  resize: vertical;
  outline: none;
}
.kh-comment-list__reply-bar {
  display: flex;
  align-items: center;
  gap: var(--kh-space-2);
  justify-content: flex-end;
  margin-top: var(--kh-space-2);
}
.kh-comment-list__img {
  margin-right: auto;
  padding: 4px 12px;
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius-sm);
  background: var(--kh-surface);
  color: var(--kh-text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s;
}
.kh-comment-list__img:hover:not(:disabled) {
  color: var(--kh-primary);
  border-color: var(--kh-primary);
}
.kh-comment-list__img:disabled {
  background: var(--kh-bg-soft);
  color: var(--kh-text-muted);
  cursor: not-allowed;
}
.kh-comment-list__send {
  padding: 4px 14px;
  border: none;
  border-radius: var(--kh-radius-sm);
  background: var(--kh-primary);
  color: #fff;
  font-size: 12px;
  cursor: pointer;
}
.kh-comment-list__send:disabled {
  background: var(--kh-bg-soft);
  color: var(--kh-text-muted);
  cursor: not-allowed;
}
.kh-comment-list__reply-section {
  margin-left: calc(var(--kh-space-3) + 36px);
  margin-top: var(--kh-space-1);
}
.kh-comment-list__toggle {
  border: none;
  background: transparent;
  color: var(--kh-primary);
  font-size: 13px;
  cursor: pointer;
  padding: var(--kh-space-1) 0;
  margin-top: var(--kh-space-1);
  text-align: left;
}
.kh-comment-list__reply-thread {
  margin-top: var(--kh-space-2);
  padding-left: var(--kh-space-4);
  border-left: 2px solid var(--kh-border);
}
</style>