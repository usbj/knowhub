<!--
  KhNoticeDetailDialog —— 全局通知详情弹窗
  ------------------------------------------------------------------
  从原 /notices 列表页内联弹窗提取，挂载在 AppLayout 供顶栏下拉、公告列表页等任何位置统一调用。
  入参 notice 兼容两套数据源：
  - SysNoticeRecord（/sys/notice/my，顶栏下拉源，含 hasRead，无 needConfirm/hasConfirmed）
  - NoticePortalRecord（/portal/notice/list，公告列表源，含 needConfirm/hasConfirmed，无 hasRead）
  展示字段 title/content/noticeType/isTop/createBy/publishTime 两类型兼容；
  "确认"按钮仅在 needConfirm=1 + 登录用户 + 未确认时露出（下拉源无 needConfirm，恒不露）。
  打开/关闭由父级 v-model:visible 双向驱动；notice 透传引用，确认后乐观置 hasConfirmed 同步回源数据。
-->
<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElImageViewer } from 'element-plus'
import KhTag from '@/components/common/KhTag.vue'
import KhIcon from '@/components/common/KhIcon.vue'
import { useMarkdownImageZoom } from '@/composables/useMarkdownImageZoom'
import { useMarkdownCodeBlock } from '@/composables/useMarkdownCodeBlock'
import { confirmNoticeApi } from '@/api/system/notice-portal'
import { useUserStore } from '@/stores/user'
import { useNoticeStore, type NoticeDetailRecord } from '@/stores/notice'
import { formatDateTime } from '@/utils/format'

const props = defineProps<{
  /** v-model:visible：弹窗可见性，父级双向绑定 */
  visible: boolean
  /** 当前展开详情的通知记录（下拉源 SysNoticeRecord 或列表源 NoticePortalRecord） */
  notice: NoticeDetailRecord | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
}>()

const userStore = useUserStore()
const noticeStore = useNoticeStore()
const router = useRouter()

const noticeTypeMap: Record<string, string> = { NOTICE: '公告', NOTIFY: '通知', REMIND: '提醒' }
const noticeTagType: Record<string, 'warm' | 'warning' | 'primary' | 'info' | 'success'> = {
  NOTICE: 'warm',
  NOTIFY: 'success',
  REMIND: 'primary',
}
const resolveNoticeType = (code: string) => noticeTypeMap[code] ?? code
const resolveTagType = (code: string) => noticeTagType[code] ?? 'info'

const showConfirmButton = computed(
  () =>
    userStore.isAuthenticated &&
    Number(props.notice?.needConfirm) === 1 &&
    !props.notice?.hasConfirmed,
)

const confirming = ref(false)

/** 确认公告：调公开确认接口，乐观置 hasConfirmed=true（透传引用同步回列表项），失败回滚由 http 拦截器提示 */
const handleConfirm = async () => {
  const n = props.notice
  if (!n?.noticeId || confirming.value) return
  confirming.value = true
  try {
    const res = await confirmNoticeApi(n.noticeId)
    n.hasConfirmed = res.data?.hasConfirmed ?? true
    ElMessage.success('已确认')
  } catch {
    // http 拦截器已弹错，此处不再重复提示
  } finally {
    confirming.value = false
  }
}

/**
 * el-dialog 双向可见性代理：用可写 computed 让 el-dialog 拥有真正的 v-model，
 * 避免单向 :model-value + 手动 @update 透传导致其内部过渡状态机错位
 * （曾出现 dialog-fade-enter-from 与 leave-from 类同存、动画卡死/不收尾）。
 * 关闭时同步 closeDetail 复位 store 态，再向父级 emit。
 */
const dialogVisible = computed<boolean>({
  get: () => props.visible,
  set: (v: boolean) => {
    if (!v) noticeStore.closeDetail()
    emit('update:visible', v)
  },
})

/**
 * 正文延迟渲染：v-md-preview 同步解析 markdown（约 200ms）若与弹窗 enter 动画同帧，
 * 争抢主线程会让动画一卡一卡。只在 @opened 之后渲染正文，让 enter 动画先轻量跑完，
 * 再挂载 markdown 把解析成本挪到动画结束后，从根上消除"出现卡顿"。
 * 关闭时立即清掉 bodyReady，下次开又是干净流程（先占位 → 动画 → 正文）。
 */
const bodyReady = ref(false)
/** 公告正文 DOM ref：v-md-preview（bodyReady 后挂载）在其内渲染，配图点击放大委托该容器的 <img> */
const contentRef = ref<HTMLElement | null>(null)
/** 公告正文配图点击放大（el-image-viewer 全屏画廊）——弹窗内，z-index 3000 叠在 dialog overlay 之上不被遮。 */
const { viewerVisible, viewerUrls, viewerIndex, onContentClick, closeViewer } = useMarkdownImageZoom(contentRef)
// 代码块增强（语言标签 + 复制按钮）：与配图放大共用同一 contentRef，正交不冲突
useMarkdownCodeBlock(contentRef)
const handleOpened = () => {
  bodyReady.value = true
}
const handleClose = () => {
  bodyReady.value = false
  dialogVisible.value = false
}

/**
 * 「前往查看」按钮：routePath 由后端通知 route_path 下发（评论回复通知拼 /blog/{id} 等作品详情路由）。
 * 点击后关闭弹窗 + 标记已读 + 路由跳该作品详情页。markAsRead 内部对已读/缺省态已短路（store L64-73），
 * 联合类型两分支（SysNoticeRecord / NoticePortalRecord）都可安全调，无需类型守卫。
 */
const handleGoToWork = async () => {
  const n = props.notice
  if (!n?.routePath) return
  if (n.noticeId) {
    await noticeStore.markAsRead(n.noticeId)
  }
  noticeStore.closeDetail()
  emit('update:visible', false)
  router.push(n.routePath)
}
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    width="720px"
    class="notice-detail-dialog"
    modal-class="notice-detail-overlay"
    align-center
    destroy-on-close
    @opened="handleOpened"
  >
    <article v-if="notice" class="notice-detail">
      <h1 class="notice-detail__title">{{ notice.title }}</h1>

      <!-- 元信息条：类型 + 置顶 + 发布人 + 发布时间 -->
      <div class="notice-detail__meta">
        <KhTag size="sm" :type="resolveTagType(notice.noticeType)">
          {{ resolveNoticeType(notice.noticeType) }}
        </KhTag>
        <span v-if="Number(notice.isTop) === 1" class="notice-detail__pin">
          <KhIcon name="star" :size="12" /> 置顶
        </span>
        <span class="notice-detail__author">
          <KhIcon name="user" :size="12" /> {{ notice.createBy ?? '系统' }}
        </span>
        <span class="notice-detail__time">
          <KhIcon name="clock" :size="12" /> {{ formatDateTime(notice.publishTime) }}
        </span>
      </div>

      <!-- 正文：v-md-preview 渲染 markdown。bodyReady 延迟到 @opened 后再挂载，
           避免 markdown 同步解析与弹窗 enter 动画同帧争抢主线程导致卡顿。
           未就绪时占位一个 min-height 防止弹窗尺寸从空态跳到满态闪一下。 -->
      <div ref="contentRef" class="notice-detail__content" @click="onContentClick">
        <div v-if="!bodyReady" class="notice-detail__placeholder">正在加载正文…</div>
        <v-md-preview v-else :text="notice.content ?? ''" />
      </div>
      <!-- 公告正文配图点击放大画廊：el-dialog 内，teleported 至 body 全屏。
           z-index 3000 叠在 dialog overlay 之上（el-dialog 默认 ~2000 起步自增）；若被遮实机改 3500。 -->
      <el-image-viewer
        v-if="viewerVisible"
        :url-list="viewerUrls"
        :initial-index="viewerIndex"
        :z-index="3000"
        hide-on-click-modal
        teleported
        @close="closeViewer"
      />
    </article>

    <template #footer>
      <div class="notice-detail__footer">
        <span v-if="showConfirmButton" class="notice-detail__hint">
          <KhIcon name="info" :size="13" /> 本公告需确认
        </span>
        <el-button
          v-if="notice?.routePath"
          type="primary"
          plain
          @click="handleGoToWork"
        >前往查看</el-button>
        <el-button @click="handleClose">关闭</el-button>
        <el-button
          v-if="showConfirmButton"
          type="primary"
          :loading="confirming"
          @click="handleConfirm"
        >确认</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
/* 详情弹窗：dialog title 固定"公告详情"，正文区另立大标题（md 一级同款加粗放大） */
.notice-detail-dialog :deep(.el-dialog) {
  border-radius: var(--kh-radius-lg);
  border: 1px solid var(--kh-border);
  box-shadow: 0 20px 60px rgba(15, 23, 42, 0.12);
  overflow: hidden;
}
.notice-detail-dialog :deep(.el-dialog__header) {
  padding: var(--kh-space-4) var(--kh-space-6);
  margin-right: 0;
  border-bottom: 1px solid var(--kh-border-soft);
  background: var(--kh-surface);
}
.notice-detail-dialog :deep(.el-dialog__title) {
  font-family: var(--kh-font-display);
  font-size: var(--kh-font-size-md);
  font-weight: 600;
  color: var(--kh-text-secondary);
}
/* body 加大左右留白，让正文与博客详情同等呼吸感 */
.notice-detail-dialog :deep(.el-dialog__body) {
  padding: var(--kh-space-8) var(--kh-space-10);
  color: var(--kh-text);
}
.notice-detail-dialog :deep(.el-dialog__footer) {
  padding: var(--kh-space-4) var(--kh-space-6);
  border-top: 1px solid var(--kh-border-soft);
}

/* 立标题：md 一级同款加粗放大，承载公告标题（与博客文章主标题观感一致） */
.notice-detail__title {
  margin: 0;
  font-family: var(--kh-font-display);
  font-size: var(--kh-font-size-2xl);
  font-weight: 700;
  line-height: 1.35;
  color: var(--kh-text);
}

/* 元信息条：标签 + 置顶 + 发布人 + 发布时间，发布人与时间右靠、与标题留白分隔 */
.notice-detail__meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--kh-space-3);
  margin-top: var(--kh-space-4);
  padding-bottom: var(--kh-space-4);
  border-bottom: 1px solid var(--kh-border-soft);
  font-size: var(--kh-font-size-sm);
  color: var(--kh-text-secondary);
}
.notice-detail__pin {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 11px;
  font-weight: 600;
  color: var(--kh-warm);
  padding: 2px 8px;
  border-radius: var(--kh-radius-pill);
  background: var(--kh-warm-soft);
}
.notice-detail__author {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--kh-text-secondary);
}
.notice-detail__time {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--kh-text-tertiary);
  font-family: var(--kh-font-mono);
}

/* 正文 —— 完整复刻博客详情对 v-md-preview github 主题的覆盖，
   清掉主题自带左右内边距/默认字体字号/h1h2 下横线，统一到 kh token。 */
.notice-detail__content {
  margin-top: var(--kh-space-6);
  max-height: 56vh;
  overflow-y: auto;
  padding-right: 4px;
}
/* 正文延迟挂载时的占位：固定 min-height 让弹窗尺寸先稳定，v-md-preview 挂载后无缝替换，
   避免 reveal 时高度从 0 跳到满态再撑开的二次抖动。 */
.notice-detail__placeholder {
  min-height: 120px;
  display: grid;
  place-items: center;
  color: var(--kh-text-tertiary);
  font-size: var(--kh-font-size-sm);
}
.notice-detail__content :deep(.github-markdown-body) {
  background: transparent;
  padding: 0;
  font-family: var(--kh-font-body);
  font-size: var(--kh-font-size-md);
  line-height: 1.9;
  color: var(--kh-text);
}
/* 公告正文配图可点放大：cursor zoom-in 视觉提示，点击由 .notice-detail__content @click 委托 onContentClick 开 el-image-viewer */
.notice-detail__content :deep(.github-markdown-body img) {
  cursor: zoom-in;
}
.notice-detail__content :deep(.github-markdown-body) > :first-child {
  margin-top: 0;
}
.notice-detail__content :deep(.github-markdown-body) > :last-child {
  margin-bottom: 0;
}

/* 底部按钮区：左侧需确认提示 + 右侧关闭/确认按钮 */
.notice-detail__footer {
  display: flex;
  align-items: center;
  gap: var(--kh-space-3);
}
.notice-detail__hint {
  margin-right: auto;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--kh-font-size-sm);
  color: var(--kh-warm);
}
</style>

<!--
  动画治理：el-dialog 的 dialog-fade 进出动画走 CSS @keyframes（modal-fade-in /
  dialog-fade-in），其中 dialog-fade-in 会给 .el-overlay-dialog 做 scale/位移，配合
  align-center 居中，enter 期间随内容高度变化不断重算居中位置 → "出现一卡一卡"。
  overlay 被 teleport 到 body，scoped 不生效，单挂一个全局块。
  modal-class="notice-detail-overlay" 注入到 .el-overlay 上，以此为锚把 EP 自带的两段
  位移型 keyframes 换成自定的"纯淡入淡出"（只动 opacity 不做 transform）：
  - 无 transform → 居中位置不再随内容尺寸重算 → enter 不再卡；
  - 0.25s ease 既保留"弹窗逐步浮现"的动画感、又不拖沓；
  - enter 用 0→1、leave 用 1→0 两段各自方向的关键帧——之前共用一段 0→1 的 keyframe 会让
    leave 时弹窗先跳到 0 再跳回 1，"闪两下"，分开方向后 leave 才是干净淡出。
  叠加 v-md-preview 延迟 @opened 挂载（把 markdown 解析挪到动画结束之后避免争帧）+
  destroy-on-close，enter 阶段是空壳弹窗轻量浮现，体感顺。
-->
<style>
/* enter 走 0→1 淡入；leave 走 1→0 淡出，方向各自独立，避免共用一段 0→1 致 leave 闪跳 */
.notice-detail-overlay.dialog-fade-enter-active,
.notice-detail-overlay.dialog-fade-enter-active .el-overlay-dialog {
  animation: notice-detail-fade-in var(--kh-duration-dialog, 0.25s) ease both !important;
}
.notice-detail-overlay.dialog-fade-leave-active,
.notice-detail-overlay.dialog-fade-leave-active .el-overlay-dialog {
  animation: notice-detail-fade-out var(--kh-duration-dialog, 0.25s) ease both !important;
}
/* 强制位移为单位，封住 EP 默认 .dialog-fade-enter-from / leave-to 上带 translate/scale */
.notice-detail-overlay.dialog-fade-enter-from,
.notice-detail-overlay.dialog-fade-leave-to {
  transform: none !important;
}
@keyframes notice-detail-fade-in {
  from { opacity: 0; transform: none; }
  to { opacity: 1; transform: none; }
}
@keyframes notice-detail-fade-out {
  from { opacity: 1; transform: none; }
  to { opacity: 0; transform: none; }
}
</style>