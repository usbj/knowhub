<!--
  KhComment —— 单条评论/回复项
  ------------------------------------------------------------------
  KhAvatar + 昵称 + 时间 + @某人(回复) + 内容 + 点赞 + 回复按钮(顶级才有) + 自删按钮 + 状态标签。
  content 渲染走 v-md-preview（utils/markdown.ts setupVmdEditor 全局注册 app.use(VueMarkdownPreview)，零局部 import）：
  评论内容现含配图 ![](/file/resolve/{id}) inline markdown，纯文本插值会把 ![](url) 原样打印成文本，故统一换 v-md-preview。
  存量纯文本评论在新渲染器下原样展示无回归（v-md-preview 对纯文本原样输出），见 P4 决策不迁移历史。
  配图：取消等比缩放（之前 contain 留白致长方形图上下空、cover 又裁内容），现按原图大小只限宽度不裁，
  避免富文贴大图撑列或裁内容/留怪空白；点击配图事件委托到 .kh-comment__content 容器：判定点击源是 <img>
  即收该评论全部 img 的 src 组 url 列表 + 记被点图索引，打开 el-image-viewer 全屏画廊放大（缩放/旋转/切同评论其它配图）。
  该放大逻辑现抽到 useMarkdownImageZoom composable，与 blog/article/project/resource/notice 正文 v-md-preview 站点共用。
  长内容折叠：富文可能贴大图，单条评论把列表撑很长压后续评论。给 .kh-comment__content 统一最大高度（COLLAPSED_HEIGHT），
  挂载后量真实高度，超出即默认折叠（max-height + 底部渐隐 + 「展开」按钮），点按钮展开去掉高度限制；再点收起回折叠态。
  「展开」/「收起」与 KhCommentList 的「展开剩余 N 条回复」分两句文案与颜色不混——后者默认显前 2 条回复见下，
  且二者位置一上一下（内容下 vs 评论组尾部）不重叠。
  reviewStatus 标签：本人 PENDING→「待作者确认」、本人 REJECTED→「已拒绝展示」(附 advice)，NONE/APPROVED 不显示。
  作者 inline 审核：当 isAuthor(作品作者 flag) 且 comment 为 PENDING 时显「同意展示」/「拒绝」按钮 → 抛 review 事件。
  自删/点赞/回复 点击均向父级抛事件，父级调对应 api 并乐观更新。
-->
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElImageViewer } from 'element-plus'
import KhAvatar from '@/components/common/KhAvatar.vue'
import { useMarkdownImageZoom } from '@/composables/useMarkdownImageZoom'
import { useMarkdownCodeBlock } from '@/composables/useMarkdownCodeBlock'
import { formatDateTime } from '@/utils/format'
import type { CommentRecord, CommentReplyRecord } from '@/types/api/knowhub/comment'

type Commentish = CommentRecord | CommentReplyRecord

const props = defineProps<{
  /** 评论/回复数据（顶级 CommentRecord；回复 CommentReplyRecord） */
  comment: Commentish
  /** 当前登录用户 userId（判断自删 + 本人状态标签） */
  currentUserId?: number | null
  /** 该作品作者 flag（作者 inline 看到并操作 PENDING） */
  isAuthor?: boolean
  /** 是否回复项（控制回复按钮显隐——回复不嵌套回复） */
  isReply?: boolean
  /** 越级锁态时禁用回复按钮（看不了完整内容也不能发评论/回复；列表/点赞/删除照常，只锁发不锁看） */
  replyDisabled?: boolean
}>()

const emit = defineEmits<{
  (e: 'like', payload: { commentId: number; liked: boolean }): void
  (e: 'reply', payload: { commentId: number; replyToUserId: number; replyToNickname: string }): void
  (e: 'delete', payload: { commentId: number }): void
  (e: 'review', payload: { commentId: number; action: 'APPROVE' | 'REJECT' }): void
}>()

const isMine = computed(() => props.currentUserId != null && props.comment.authorId === props.currentUserId)
const isPending = computed(() => props.comment.reviewStatus === 'PENDING')
const isRejected = computed(() => props.comment.reviewStatus === 'REJECTED')

const router = useRouter()

/**
 * 点击评论人昵称 / @某人 跳该用户公开主页（/user/{userId}，游客可读）。
 * authorId 后端必返（评论发起人 userId）；@某人跳 replyToUserId（回复项才有，顶级评论无）。
 * userId 非法（0/null）时不跳，避免跳到无效主页。
 */
const goUserHome = (uid?: number) => {
  if (uid == null || uid <= 0) return
  void router.push(`/user/${uid}`)
}
/** 作者 inline 审核按钮显隐：当前用户是作品作者且评论 PENDING */
const canReview = computed(() => Boolean(props.isAuthor) && isPending.value)
/** @某人信息（仅回复项才有，作者 inline 审核也仅在顶级评论项需显——以 props.isReply 控制） */
const replyToLabeled = computed(() => (props.isReply ? (props.comment as CommentReplyRecord).replyToNickname : null))

const onLike = () => {
  const next = !props.comment.hasLiked
  emit('like', { commentId: props.comment.commentId, liked: next })
}
const onReply = () => {
  // 回复某条评论时 @ 该评论作者（若回复的是别人；自己回复自己可省略 replyToUserId）
  emit('reply', {
    commentId: props.comment.commentId,
    replyToUserId: props.comment.authorId,
    replyToNickname: props.comment.authorNickname ?? '',
  })
}
const onDelete = () => emit('delete', { commentId: props.comment.commentId })
const onApprove = () => emit('review', { commentId: props.comment.commentId, action: 'APPROVE' })
const onReject = () => emit('review', { commentId: props.comment.commentId, action: 'REJECT' })

// ---- 配图放大查看（el-image-viewer 全屏画廊，复用 useMarkdownImageZoom composable）----
/** 正文 DOM ref：zoom 委托查 <img> 与折叠量高都复用它（composable 只读，不与折叠/TOC 等同 ref 上的逻辑冲突） */
const contentRef = ref<HTMLElement | null>(null)
/** 配图放大画廊：6 站 v-md-preview 站点同款——点 <img> 收同容器 img src 组 list + 被点索引起 viewer */
const { viewerVisible, viewerUrls, viewerIndex, onContentClick, closeViewer } = useMarkdownImageZoom(contentRef)
// 代码块增强（语言标签 + 复制按钮）：与配图放大共用同一 contentRef，正交不冲突
useMarkdownCodeBlock(contentRef)

// ---- 长内容折叠：统一最大高度，超限默认折叠 + 渐隐 +「展开」/「收起」----
/** 评论内容折叠态最大高度（px）。统一所有评论的"未展开首屏可见高度"，长评论不压后续评论。
 *  富文贴大图更高则被裁在该高度内并底部渐隐提示，点展开按需放高。240px ≈ 一段短评 + 一张缩略图的观感。 */
const COLLAPSED_HEIGHT = 240
/** 是否处于展开态：true 去掉高度限制显全文，false 常态（折叠或无需折叠）。
 *  仅在 needCollapse=true 时该态有折感：content--collapsed 持加持。 */
const expanded = ref(false)
/** 该评论内容是否超限须折叠（挂载后量 scrollHeight>COLLAPSED_HEIGHT 才置真）。
 *  仅超限才显「展开」按钮——短评论无按钮，避免每条评论都挂一个动态按钮增噪。 */
const needCollapse = ref(false)

/**
 * 量正文容器真实高度判定是否需折叠。v-md-preview 在 onMounted 前同步渲染好 markdown 文本骨架，
 * 但配图 img 异步解码——onMounted 量 scrollHeight 时图占行高可能为 0 或小，短文 + 大图会漏判"无需折叠"，
 * 图后来加载把内容推过 240px 却不再测、折叠不激活，用户失去中等图的高度保护。
 * 故 onMounted 初测后，再对所有 <img> 挂 load 重测一次（已加载的 img load 不再触发 → 兼容历史存量可见态）。
 * 重测只设 needCollapse 由 true→保持、false→凝出后变 true 单向放大"需要折叠"范围，不反向收缩——
 * 用户主动展开后即使图重测 needCollapse 也是 true，真值不影响已 expanded 态的渲染；切回收起再用真值折叠。
 */
let measuredImgs: Array<HTMLImageElement> = []
const measureCollapse = () => {
  const el = contentRef.value
  if (!el) return
  needCollapse.value = el.scrollHeight > COLLAPSED_HEIGHT
  // 给未加载完的配图挂 load 重测，匹比防止短文本+大图漏折叠。已 complete 的不再监听免冗余。
  el.querySelectorAll<HTMLImageElement>('img').forEach((img) => {
    if (img.complete) return
    img.addEventListener('load', measureCollapse, { once: true })
    measuredImgs.push(img)
  })
}
onMounted(measureCollapse)
onBeforeUnmount(() => {
  measuredImgs.forEach((img) => img.removeEventListener('load', measureCollapse))
  measuredImgs = []
})
/** 切换展开/收起：翻状态由 reactive 触发 content--collapsed 类增删，前者去高度限制、后者加回。 */
const toggleExpand = () => {
  expanded.value = !expanded.value
}
</script>

<template>
  <div class="kh-comment">
    <KhAvatar :item="{ label: comment.authorNickname || '?', src: comment.authorAvatar ?? undefined }" :size="36" />
    <div class="kh-comment__body">
      <div class="kh-comment__head">
        <span class="kh-comment__name" @click="goUserHome(comment.authorId)">{{ comment.authorNickname || '匿名用户' }}</span>
        <span v-if="replyToLabeled" class="kh-comment__arrow">回复</span>
        <span
          v-if="replyToLabeled"
          class="kh-comment__reply-to"
          @click="goUserHome((comment as CommentReplyRecord).replyToUserId)"
        >@{{ replyToLabeled }}</span>
        <span class="kh-comment__time">{{ formatDateTime(comment.createTime) }}</span>
        <!-- 本人状态标签 -->
        <span v-if="isMine && isPending" class="kh-comment__tag kh-comment__tag--pending">待作者确认</span>
        <span v-if="isMine && isRejected" class="kh-comment__tag kh-comment__tag--rejected">已拒绝展示</span>
      </div>
      <!--
        正文 v-md-preview 渲染 markdown（含配图 ![](/file/resolve/{id}) inline 图）。v-md-preview 全局注册
        （utils/markdown.ts setupVmdEditor app.use），无需局部 import。content--rejected 给外层灰字类（v-md-preview
        github 主题内用 :deep 覆盖颜色）。空 content 时传空串占位避免 v-md-preview 拿 null 报错。
        @click 事件委托：点配图缩略图（源是 <img>）放大查看——组画廊开 el-image-viewer。
        长内容折叠：needCollapse（挂载量出超 COLLAPSED_HEIGHT）且非 expanded 时 content--collapsed 持加持——
        max-height 裁 + overflow hidden + ::after 底部渐隐，提示"还有内容"；展开后类去掉自然铺满。
      -->
      <div
        ref="contentRef"
        class="kh-comment__content"
        :class="{
          'kh-comment__content--rejected': isMine && isRejected,
          'kh-comment__content--collapsed': needCollapse && !expanded,
        }"
        @click="onContentClick"
      >
        <v-md-preview :text="comment.content ?? ''" />
      </div>
      <!-- 折叠态展开/收起按钮（仅 needCollapse 时出，短评论无）。
           文案独立「展开」/「收起」，与 KhCommentList「展开剩余 N 条回复」分两句不混、位置一上一下不叠。 -->
      <div v-if="needCollapse" class="kh-comment__content-toggle">
        <button class="kh-comment__expand" type="button" @click="toggleExpand">
          {{ expanded ? '收起' : '展开' }}
        </button>
      </div>
      <!-- 本人看 REJECTED 原因（advice） -->
      <div v-if="isMine && isRejected && comment.reviewAdvice" class="kh-comment__advice">
        作者意见：{{ comment.reviewAdvice }}
      </div>
      <div class="kh-comment__bar">
        <button class="kh-comment__act" :class="{ 'kh-comment__act--on': comment.hasLiked }" @click="onLike">
          ❤ {{ comment.likeCount }}
        </button>
        <button v-if="!isReply && !replyDisabled" class="kh-comment__act" @click="onReply">回复</button>
        <!-- 自删（本人）或作者删任意 -->
        <button v-if="isMine || isAuthor" class="kh-comment__act kh-comment__act--danger" @click="onDelete">删除</button>
        <!-- 作者 inline 精选 -->
        <template v-if="canReview">
          <button class="kh-comment__act kh-comment__act--approve" @click="onApprove">同意展示</button>
          <button class="kh-comment__act kh-comment__act--reject" @click="onReject">拒绝</button>
        </template>
      </div>
    </div>
    <!-- 配图放大画廊：click 委托组出的本评论配图 src 列表 + 被点索引 -->
    <el-image-viewer
      v-if="viewerVisible"
      :url-list="viewerUrls"
      :initial-index="viewerIndex"
      :z-index="3000"
      hide-on-click-modal
      teleported
      @close="closeViewer"
    />
  </div>
</template>

<style scoped>
.kh-comment {
  display: flex;
  gap: var(--kh-space-3);
  padding: var(--kh-space-3) 0;
  border-bottom: 1px solid var(--kh-border);
}
.kh-comment:last-child {
  border-bottom: none;
}
.kh-comment__body {
  flex: 1;
  min-width: 0;
}
.kh-comment__head {
  display: flex;
  align-items: center;
  gap: var(--kh-space-2);
  flex-wrap: wrap;
}
.kh-comment__name {
  font-weight: 600;
  font-size: 13px;
  color: var(--kh-text);
  cursor: pointer;
  transition: color var(--kh-transition-fast);
}
.kh-comment__name:hover {
  color: var(--kh-primary);
  text-decoration: underline;
}
.kh-comment__arrow {
  font-size: 12px;
  color: var(--kh-text-muted);
}
.kh-comment__reply-to {
  font-size: 13px;
  color: var(--kh-primary);
  cursor: pointer;
}
.kh-comment__reply-to:hover {
  text-decoration: underline;
}
.kh-comment__time {
  font-size: 12px;
  color: var(--kh-text-muted);
}
.kh-comment__tag {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 4px;
}
.kh-comment__tag--pending {
  color: #b45309;
  background: rgba(245, 158, 11, 0.12);
}
.kh-comment__tag--rejected {
  color: #b91c1c;
  background: rgba(220, 38, 38, 0.1);
}
/* 正文容器：交给 v-md-preview github 主题接管排版，不再 pre-wrap（纯文本换行由 md 软换行/硬换行语义渲染）。
   保留 word-break 防超长无空格串/长 URL 撑爆列。字体行高用 kh token 与评论行对齐。
   position: relative 给折叠态 ::after 渐隐遮罩做定位锚（不影响正常流位置）。 */
.kh-comment__content {
  margin: var(--kh-space-2) 0;
  font-size: 14px;
  line-height: 1.7;
  color: var(--kh-text);
  word-break: break-word;
  position: relative;
}
.kh-comment__content--rejected {
  color: var(--kh-text-secondary);
}
/* 折叠态：限统一最大高度 + overflow hidden 裁掉超出，::after 在底部压一道由透明渐到卡片背景色的渐隐条，
   提示"下方还有内容"，克制不强裁（比硬 clip 图更柔和）。展开态该类不加持，content 自然铺满。 */
.kh-comment__content--collapsed {
  max-height: 240px;
  overflow: hidden;
}
.kh-comment__content--collapsed::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 40px;
  background: linear-gradient(to bottom, transparent, var(--kh-surface));
  pointer-events: none;
}
/* v-md-preview github 主题覆盖：清掉自带左右内边距/默认字号/h1h2 下划线，统一到 kh token（照 KhNoticeDetailDialog 同款口径）。
   rejected 态正文颜色随容器 content--rejected 类下调次要色。 */
.kh-comment__content :deep(.github-markdown-body) {
  background: transparent;
  padding: 0;
  font-family: var(--kh-font-body);
  font-size: 14px;
  line-height: 1.7;
  color: inherit;
}
.kh-comment__content :deep(.github-markdown-body) > :first-child {
  margin-top: 0;
}
.kh-comment__content :deep(.github-markdown-body) > :last-child {
  margin-bottom: 0;
}
/*
  评论配图渲染：取消等比缩放（之前 contain 留白致长方形图上下空、cover 又裁内容），按原图大小只限宽度。
  max-width: 100% 让图不撑爆评论宽列；height auto 保真实比例不裁——图就按本身高显示，
  超出统一高度由上层 content--collapsed 折叠处理（图本身不留白不裁）。
  display: block 单图独占一行、竖排贴齐评论正文；多图自动换行；圆角克制不抢行，
  cursor zoom-in 与 @click 委托放大呼应；light border 描出图缘。
*/
.kh-comment__content :deep(.github-markdown-body img) {
  max-width: 100%;
  height: auto;
  display: block;
  margin: var(--kh-space-2) 0;
  border: 1px solid var(--kh-border-soft);
  border-radius: var(--kh-radius-sm);
  cursor: zoom-in;
  transition: box-shadow 0.15s ease;
}
.kh-comment__content :deep(.github-markdown-body img:hover) {
  box-shadow: var(--kh-shadow-sm);
}
/* 展开/收起按钮条：独立在内容下、advice 与操作 bar 之上，与 KhCommentList「展开剩余 N 条回复」分置不混。
   居右弱化主次（footer 类微小操作色），不抢点赞回复的主操作注意力。
   margin-top 负值收纳：把渐隐条尾部与按钮微贴、视觉连贯；margin-bottom 维持与下方 advice/bar 同一节距。 */
.kh-comment__content-toggle {
  display: flex;
  justify-content: flex-end;
  margin-top: calc(var(--kh-space-2) * -1);
  margin-bottom: var(--kh-space-2);
}
.kh-comment__expand {
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 12px;
  color: var(--kh-text-secondary);
  padding: 2px 6px;
  border-radius: var(--kh-radius-sm);
  transition: color 0.15s;
}
.kh-comment__expand:hover {
  color: var(--kh-primary);
}
.kh-comment__advice {
  font-size: 12px;
  color: var(--kh-text-muted);
  padding: var(--kh-space-1) var(--kh-space-2);
  background: var(--kh-bg-soft);
  border-radius: var(--kh-radius-sm);
  margin-bottom: var(--kh-space-2);
}
.kh-comment__bar {
  display: flex;
  align-items: center;
  gap: var(--kh-space-3);
}
.kh-comment__act {
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 12px;
  color: var(--kh-text-secondary);
  padding: 2px 4px;
  border-radius: var(--kh-radius-sm);
  transition: color 0.15s, background 0.15s;
}
.kh-comment__act:hover {
  color: var(--kh-primary);
}
.kh-comment__act--on {
  color: #dc2626;
}
.kh-comment__act--danger {
  color: var(--kh-text-muted);
}
.kh-comment__act--danger:hover {
  color: #dc2626;
}
.kh-comment__act--approve {
  color: var(--kh-text-secondary);
}
.kh-comment__act--approve:hover {
  color: #16a34a;
  background: rgba(22, 163, 74, 0.08);
}
.kh-comment__act--reject {
  color: var(--kh-text-secondary);
}
.kh-comment__act--reject:hover {
  color: #dc2626;
  background: rgba(220, 38, 38, 0.08);
}
</style>