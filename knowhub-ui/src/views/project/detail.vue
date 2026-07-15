<!--
  项目详情 /project/:id
  ------------------------------------------------------------------
  无封面项目头（标签 + 标题 + 摘要 + 负责人/评分/更新）+ 子页面切换（项目介绍 / 项目文件）
  + 右栏（项目信息 + 参与人员可滚动）。
  项目文件：GitHub 式文件树，文件叶子右侧展示上传时间/大小，可点击下载。
  “下载源码”按钮已去掉（并非所有项目都是编程项目，下载走文件树叶子项）。
-->
<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Download, Folder, Document } from '@element-plus/icons-vue'
import KhCard from '@/components/common/KhCard.vue'
import KhTag from '@/components/common/KhTag.vue'
import KhAvatar from '@/components/common/KhAvatar.vue'
import KhRating from '@/components/common/KhRating.vue'
import KhStatPill from '@/components/common/KhStatPill.vue'
import KhIcon from '@/components/common/KhIcon.vue'
import { getProjectById, projects, type MockProjectFile } from '@/mock/project'
import { formatDateTime } from '@/utils/format'
import { viewLevelTagType, getViewLevelLabel } from '@/utils/viewLevel'

const route = useRoute()
const router = useRouter()

const projectId = computed(() => Number(route.params.id))
const project = computed(() => getProjectById(projectId.value) ?? projects[0]!)

const typeLabel: Record<string, string> = { COMPETITION: '比赛项目', PRACTICE: '练习项目', OPS: '运维项目' }
const statusMeta: Record<string, { text: string; type: 'success' | 'warning' | 'danger' | 'neutral' | 'info' }> = {
  PUBLISHED: { text: '已发布', type: 'success' },
  PENDING_REVIEW: { text: '待审核', type: 'warning' },
  REJECTED: { text: '已驳回', type: 'danger' },
  DRAFT: { text: '草稿', type: 'neutral' },
  REVOKED: { text: '已撤回', type: 'neutral' },
  ARCHIVED: { text: '已归档', type: 'info' },
}
const memberRoleLabel: Record<string, string> = { LEADER: '负责人', MENTOR: '导师', MEMBER: '成员' }

const goBack = () => router.back()

/** 子页面切换：介绍 / 项目文件 */
type SubTab = 'intro' | 'files'
const activeTab = ref<SubTab>('intro')

/** 文件树展开状态（按 fileId 记录）；首屏根目录默认展开 */
const expanded = ref<Record<number, boolean>>({})
const isNodeOpen = (node: MockProjectFile, depth: number) => expanded.value[node.fileId] ?? depth === 0
const toggleNode = (node: MockProjectFile) => {
  if (node.isDir) expanded.value[node.fileId] = !expanded.value[node.fileId]
}

/** 拍平后的可视节点列表（含缩进层级），参考后台 ProjectFileTree 的 GitHub 式拍平渲染 */
const visibleNodes = computed(() => {
  const out: { node: MockProjectFile; depth: number }[] = []
  const walk = (nodes: MockProjectFile[], depth: number) => {
    for (const n of nodes) {
      out.push({ node: n, depth })
      if (n.isDir && isNodeOpen(n, depth) && n.children) walk(n.children, depth + 1)
    }
  }
  walk(project.value.files, 0)
  return out
})

/** 负责人：取成员表 LEADER 角色的 nickname（与项目模块负责人语义一致） */
const leader = computed(
  () => project.value.members.find((m) => m.role === 'LEADER')?.nickname ?? project.value.authorNickname,
)

/**
 * 方法效果：
 * 把字节大小格式化为 B/KB/MB/GB 字符串，用于文件叶子右侧元信息展示。
 * 对齐后台 ProjectFileTree.vue 的 formatSize 口径。
 */
const formatSize = (len?: number) => {
  if (len == null) return '--'
  if (len < 1024) return `${len} B`
  if (len < 1024 * 1024) return `${(len / 1024).toFixed(1)} KB`
  if (len < 1024 * 1024 * 1024) return `${(len / 1024 / 1024).toFixed(1)} MB`
  return `${(len / 1024 / 1024 / 1024).toFixed(2)} GB`
}

/** 文件下载（demo：暂未接后端，仅弹提示；真实接口落地后替换为 downloadProjectFileApi） */
const handleDownloadFile = (node: MockProjectFile) => {
  if (node.isDir) return
  ElMessage.info(`下载「${node.name}」（demo 占位，文件下载接口落地后接入）`)
}
</script>

<template>
  <div class="pd">
    <!-- 面包屑 -->
    <div class="kh-container kh-container--wide pd__crumb">
      <button class="pd__back" type="button" @click="goBack">
        <el-icon><ArrowLeft /></el-icon> 返回
      </button>
      <RouterLink to="/">首页</RouterLink>
      <el-icon class="pd__crumb-sep"><KhIcon name="chevron-right" :size="12" /></el-icon>
      <RouterLink to="/projects">项目展示</RouterLink>
      <el-icon class="pd__crumb-sep"><KhIcon name="chevron-right" :size="12" /></el-icon>
      <span class="pd__crumb-current">{{ project.title }}</span>
    </div>

    <!-- 项目头：无封面（项目不一定有封面），标签 + 标题 + 摘要 + 元信息 -->
    <div class="kh-container kh-container--wide">
      <KhCard padding="lg" class="pd__head">
        <div class="pd__head-tags">
          <KhTag type="primary">{{ typeLabel[project.type] }}</KhTag>
          <KhTag :type="statusMeta[project.status]?.type ?? 'neutral'" dot>{{ statusMeta[project.status]?.text ?? '未知' }}</KhTag>
          <KhTag :type="viewLevelTagType[project.level] ?? 'neutral'">{{ getViewLevelLabel(project.level) }}</KhTag>
          <KhTag v-if="project.competition" type="warm">{{ project.competition.awardLevel }}</KhTag>
        </div>
        <h1 class="pd__title">{{ project.title }}</h1>
        <p class="pd__summary">{{ project.summary }}</p>

        <div class="pd__head-meta">
          <div class="pd__head-leader">
            <KhAvatar :item="{ label: leader }" :size="40" />
            <div>
              <div class="pd__head-leader-name">{{ leader }}</div>
              <div class="pd__head-leader-role">负责人 · {{ project.members.length }} 人团队</div>
            </div>
          </div>
          <div class="pd__head-stats">
            <KhRating :value="project.rating" :size="16" show-value />
            <KhStatPill icon="download" :value="project.downloadCount" label="下载" />
            <KhStatPill icon="clock" :value="project.updateTime" />
          </div>
        </div>
      </KhCard>
    </div>

    <!-- 主体布局：左子页面切换（介绍 / 项目文件），右栏（项目信息 + 参与人员） -->
    <div class="kh-container kh-container--wide pd__layout">
      <!-- 左：子页面 -->
      <div class="pd__main">
        <!-- 子页面切换页签 -->
        <div class="pd__tabs">
          <button
            class="pd__tab"
            :class="{ 'is-active': activeTab === 'intro' }"
            type="button"
            @click="activeTab = 'intro'"
          >
            <KhIcon name="doc" :size="15" /> 项目介绍
          </button>
          <button
            class="pd__tab"
            :class="{ 'is-active': activeTab === 'files' }"
            type="button"
            @click="activeTab = 'files'"
          >
            <KhIcon name="file" :size="15" /> 项目文件
          </button>
        </div>

        <!-- 子页面：项目介绍 -->
        <KhCard v-show="activeTab === 'intro'" padding="lg" class="pd__section">
          <div class="pd__content">
            <v-md-preview :text="project.description" />
          </div>
        </KhCard>

        <!-- 子页面：项目文件（GitHub 式文件树，文件叶子右侧展示上传时间/大小 + 下载按钮） -->
        <section v-show="activeTab === 'files'" class="pd__files">
          <!-- 表头（仅桌面端） -->
          <div class="pd__files-head">
            <span class="pd__files-col pd__files-col--name">名称</span>
            <span class="pd__files-col pd__files-col--time">上传时间</span>
            <span class="pd__files-col pd__files-col--size">大小</span>
            <span class="pd__files-col pd__files-col--action" />
          </div>
          <div class="pd__files-body">
            <div
              v-for="item in visibleNodes"
              :key="item.node.fileId"
              class="pd__tree-node"
              :class="{ 'is-dir': item.node.isDir, 'is-file': !item.node.isDir }"
              :style="{ paddingLeft: `${item.depth * 18 + 12}px` }"
              @click="toggleNode(item.node)"
            >
              <span class="pd__tree-caret" :class="{ 'is-leaf': !item.node.isDir }">
                {{ item.node.isDir ? (isNodeOpen(item.node, item.depth) ? '▾' : '▸') : '' }}
              </span>
              <el-icon v-if="item.node.isDir" class="pd__tree-icon"><Folder /></el-icon>
              <el-icon v-else class="pd__tree-icon"><Document /></el-icon>
              <span class="pd__tree-name">{{ item.node.name }}</span>
              <span class="pd__tree-time">{{ item.node.isDir ? '' : (item.node.uploadTime ? formatDateTime(item.node.uploadTime) : '--') }}</span>
              <span class="pd__tree-size">{{ item.node.isDir ? '' : formatSize(item.node.contentLength) }}</span>
              <span class="pd__tree-action">
                <button
                  v-if="!item.node.isDir"
                  class="pd__tree-download"
                  type="button"
                  title="下载"
                  @click.stop="handleDownloadFile(item.node)"
                >
                  <el-icon><Download /></el-icon>
                </button>
              </span>
            </div>
            <div v-if="!visibleNodes.length" class="pd__tree-empty">暂无文件</div>
          </div>
        </section>
      </div>

      <!-- 右：项目信息 + 参与人员（参与人员卡内部可滚动，坐落项目信息下方） -->
      <aside class="pd__aside">
        <KhCard padding="md" class="pd__info">
          <h3 class="pd__info-title">项目信息</h3>
          <div class="pd__info-row">
            <span>类型</span><b>{{ typeLabel[project.type] }}</b>
          </div>
          <div class="pd__info-row">
            <span>等级</span><b>{{ getViewLevelLabel(project.level) }}</b>
          </div>
          <div class="pd__info-row">
            <span>状态</span><b>{{ statusMeta[project.status]?.text ?? '未知' }}</b>
          </div>
          <div class="pd__info-row">
            <span>评分</span><b>{{ project.rating.toFixed(1) }} / 5.0</b>
          </div>
          <div v-if="project.competition" class="pd__info-row">
            <span>比赛</span><b>{{ project.competition.name }}</b>
          </div>
          <div v-if="project.competition" class="pd__info-row">
            <span>获奖</span><b class="pd__info-warm">{{ project.competition.awardLevel }}</b>
          </div>
          <div class="pd__info-row">
            <span>更新</span><b>{{ project.updateTime }}</b>
          </div>
        </KhCard>

        <KhCard padding="md" class="pd__members-card">
          <h3 class="pd__members-title">参与人员 · {{ project.members.length }}</h3>
          <div class="pd__members-scroll">
            <div
              v-for="m in project.members"
              :key="m.userId"
              class="pd__member"
              :class="`pd__member--${m.role.toLowerCase()}`"
            >
              <KhAvatar :item="{ label: m.nickname }" :size="40" />
              <div class="pd__member-info">
                <div class="pd__member-name">{{ m.nickname }}</div>
                <div class="pd__member-role">{{ memberRoleLabel[m.role] }}</div>
              </div>
              <KhTag v-if="m.role === 'LEADER'" type="warm" size="sm">负责人</KhTag>
              <KhTag v-else-if="m.role === 'MENTOR'" type="info" size="sm">导师</KhTag>
            </div>
          </div>
        </KhCard>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.pd__crumb {
  display: flex;
  align-items: center;
  gap: var(--kh-space-2);
  padding-top: var(--kh-space-5);
  padding-bottom: var(--kh-space-4);
  font-size: var(--kh-font-size-sm);
  color: var(--kh-text-tertiary);
}
.pd__back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius-sm);
  background: var(--kh-surface);
  color: var(--kh-text-secondary);
  cursor: pointer;
  margin-right: var(--kh-space-3);
  font-size: 12px;
}
.pd__back:hover {
  border-color: var(--kh-primary-border);
  color: var(--kh-primary);
}
.pd__crumb a {
  color: var(--kh-text-secondary);
}
.pd__crumb a:hover {
  color: var(--kh-primary);
}
.pd__crumb-sep {
  color: var(--kh-text-tertiary);
}
.pd__crumb-current {
  color: var(--kh-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 280px;
}

.pd__head {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-3);
  overflow: hidden;
}
.pd__head-tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}
.pd__title {
  font-size: var(--kh-font-size-3xl);
  font-weight: 700;
  letter-spacing: -0.01em;
}
.pd__summary {
  margin-top: var(--kh-space-2);
  color: var(--kh-text-secondary);
  font-size: var(--kh-font-size-md);
  line-height: 1.7;
}
.pd__head-meta {
  display: flex;
  align-items: center;
  gap: var(--kh-space-5);
  margin-top: var(--kh-space-4);
  padding-top: var(--kh-space-4);
  border-top: 1px solid var(--kh-border-soft);
  flex-wrap: wrap;
}
.pd__head-leader {
  display: flex;
  align-items: center;
  gap: 10px;
}
.pd__head-leader-name {
  font-size: var(--kh-font-size-sm);
  font-weight: 600;
}
.pd__head-leader-role {
  font-size: 11px;
  color: var(--kh-text-tertiary);
}
.pd__head-stats {
  display: flex;
  align-items: center;
  gap: var(--kh-space-5);
}

.pd__layout {
  display: grid;
  grid-template-columns: 1fr 320px;
  gap: var(--kh-space-6);
  align-items: start;
  margin-top: var(--kh-space-6);
  padding-bottom: var(--kh-space-12);
}
.pd__main {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-5);
  min-width: 0;
}

/* —— 子页面切换页签 —— */
.pd__tabs {
  display: flex;
  gap: 4px;
  padding: 4px;
  background: var(--kh-surface-muted);
  border-radius: var(--kh-radius-pill);
  align-self: flex-start;
}
.pd__tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 18px;
  border: none;
  background: transparent;
  border-radius: var(--kh-radius-pill);
  color: var(--kh-text-secondary);
  font-size: var(--kh-font-size-sm);
  font-weight: 500;
  cursor: pointer;
  transition: all var(--kh-transition-fast);
}
.pd__tab:hover {
  color: var(--kh-primary);
}
.pd__tab.is-active {
  background: var(--kh-surface);
  color: var(--kh-primary);
  box-shadow: var(--kh-shadow-xs);
}

.pd__section {
  /* v-show 控制显隐，无额外样式占位 */
}

/* 项目介绍：v-md-preview github 主题根类是 github-markdown-body（见 blog/detail.vue 同款约定），
   清掉主题给 body 的左右内边距让正文与上方标签/标题左对齐、去一二级标题下横线 */
.pd__content {
  color: var(--kh-text);
}
.pd__content :deep(.github-markdown-body) {
  background: transparent;
  padding: 0;
  font-family: var(--kh-font-body);
  font-size: var(--kh-font-size-md);
  line-height: 1.9;
  color: var(--kh-text);
}
.pd__content :deep(.github-markdown-body h1),
.pd__content :deep(.github-markdown-body h2) {
  border-bottom: none;
}

/* —— 项目文件：GitHub 式文件树独立区块（参考后台 ProjectFileTree） —— */
.pd__files {
  background: var(--kh-surface);
  border: 1px solid var(--kh-border-soft);
  border-radius: var(--kh-radius-lg);
  overflow: hidden;
  max-height: 640px;
  display: flex;
  flex-direction: column;
}
.pd__files-head {
  display: flex;
  align-items: center;
  padding: 10px 16px;
  border-bottom: 1px solid var(--kh-border-soft);
  font-size: 12px;
  font-weight: 600;
  color: var(--kh-text-tertiary);
  background: var(--kh-surface-muted);
  flex: none;
}
.pd__files-col--name {
  flex: 1;
  padding-left: 28px; /* 对齐 caret+icon 的宽度 */
}
.pd__files-col--time {
  width: 120px;
  flex: none;
}
.pd__files-col--size {
  width: 70px;
  flex: none;
}
.pd__files-col--action {
  width: 44px;
  flex: none;
}
.pd__files-body {
  padding: 8px 0;
  overflow: auto;
  flex: 1;
}
.pd__tree-node {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  font-size: 13px;
  cursor: pointer;
  transition: background var(--kh-transition-fast);
}
.pd__tree-node:hover {
  background: var(--kh-surface-muted);
}
.pd__tree-node.is-file:hover .pd__tree-download {
  opacity: 1;
}
.pd__tree-caret {
  width: 14px;
  text-align: center;
  color: var(--kh-text-tertiary);
  user-select: none;
  flex: none;
}
.pd__tree-caret.is-leaf {
  cursor: default;
}
.pd__tree-icon {
  font-size: 15px;
  flex: none;
}
.pd__tree-node.is-dir .pd__tree-icon {
  color: var(--kh-warm);
}
.pd__tree-node.is-file .pd__tree-icon {
  color: var(--kh-accent);
}
.pd__tree-name {
  flex: 1;
  min-width: 0;
  color: var(--kh-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.pd__tree-time {
  width: 120px;
  flex: none;
  color: var(--kh-text-tertiary);
  font-family: var(--kh-font-mono);
  font-size: 12px;
  white-space: nowrap;
}
.pd__tree-size {
  width: 70px;
  flex: none;
  color: var(--kh-text-tertiary);
  font-family: var(--kh-font-mono);
  font-size: 12px;
  text-align: right;
}
.pd__tree-action {
  width: 44px;
  flex: none;
  display: flex;
  justify-content: flex-end;
}
.pd__tree-download {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius-sm);
  background: var(--kh-surface);
  color: var(--kh-text-secondary);
  cursor: pointer;
  opacity: 0;
  transition: all var(--kh-transition-fast);
}
.pd__tree-download:hover {
  color: var(--kh-primary);
  border-color: var(--kh-primary-border);
  background: var(--kh-primary-soft);
}
.pd__tree-empty {
  padding: var(--kh-space-8);
  text-align: center;
  color: var(--kh-text-tertiary);
  font-size: var(--kh-font-size-sm);
}

/* —— 右栏：参与人员卡（内部可滚动 sticky） —— */
.pd__members-card {
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.pd__members-title {
  font-size: var(--kh-font-size-md);
  font-weight: 600;
  margin-bottom: var(--kh-space-4);
}
.pd__members-scroll {
  max-height: 360px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-3);
  padding-right: 2px;
}
.pd__member {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: var(--kh-space-3);
  border: 1px solid var(--kh-border-soft);
  border-radius: var(--kh-radius);
  transition: all var(--kh-transition-fast);
}
.pd__member:hover {
  border-color: var(--kh-border-strong);
  background: var(--kh-surface-muted);
}
.pd__member--leader {
  border-color: var(--kh-warm);
  background: var(--kh-warm-soft);
}
.pd__member-info {
  flex: 1;
  min-width: 0;
}
.pd__member-name {
  font-size: var(--kh-font-size-sm);
  font-weight: 600;
  color: var(--kh-text);
}
.pd__member-role {
  font-size: 11px;
  color: var(--kh-text-tertiary);
  margin-top: 2px;
}

/* 侧栏 */
.pd__aside {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-5);
  position: sticky;
  top: calc(var(--kh-header-height) + var(--kh-space-4));
}
.pd__info-title {
  font-size: var(--kh-font-size-md);
  font-weight: 600;
  margin-bottom: var(--kh-space-3);
}
.pd__info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  font-size: 12px;
  border-bottom: 1px dashed var(--kh-border-soft);
}
.pd__info-row:last-child {
  border-bottom: none;
}
.pd__info-row span {
  color: var(--kh-text-tertiary);
}
.pd__info-row b {
  color: var(--kh-text);
  font-weight: 500;
  text-align: right;
  max-width: 180px;
}
.pd__info-warm {
  color: var(--kh-warm) !important;
  font-weight: 600;
}

@media (max-width: 1024px) {
  .pd__layout {
    grid-template-columns: 1fr;
  }
  .pd__aside {
    position: static;
  }
  /* 文件树在窄屏隐藏表头，行右侧元信息列收紧 */
  .pd__files-col--time,
  .pd__tree-time {
    width: 100px;
  }
}
@media (max-width: 640px) {
  .pd__files-col--time,
  .pd__tree-time,
  .pd__files-col--size,
  .pd__tree-size {
    display: none;
  }
}
</style>
