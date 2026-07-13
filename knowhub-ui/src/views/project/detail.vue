<!--
  项目详情 /project/:id
  ------------------------------------------------------------------
  封面 + 标题 + 类型/状态/等级 + Markdown 介绍 + 参与人员卡（负责人/导师/成员）+ GitHub 式文件树 + 源码下载 + 版本说明 + 评分。
-->
<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Download, Star, Folder, Document, Plus } from '@element-plus/icons-vue'
import KhCard from '@/components/common/KhCard.vue'
import KhTag from '@/components/common/KhTag.vue'
import KhAvatar from '@/components/common/KhAvatar.vue'
import KhRating from '@/components/common/KhRating.vue'
import KhStatPill from '@/components/common/KhStatPill.vue'
import KhIcon from '@/components/common/KhIcon.vue'
import { getProjectById, projects, type MockProjectFile } from '@/mock/project'

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

/** 文件树展开状态（按 id 记录） */
const expanded = ref<Record<number, boolean>>({})
const toggleNode = (node: MockProjectFile) => {
  if (node.isDir) expanded.value[node.id] = !expanded.value[node.id]
}

/** 递归渲染文件树节点 */
const renderNode = (node: MockProjectFile, depth = 0): MockProjectFile[] => {
  if (!node.isDir) return [node]
  const isOpen = expanded.value[node.id] ?? depth === 0
  const result = [node]
  if (isOpen && node.children) {
    for (const child of node.children) {
      result.push(...renderNode(child, depth + 1))
    }
  }
  return result
}

/** 拍平后的可视节点列表（含缩进层级） */
const visibleNodes = computed(() => {
  const out: { node: MockProjectFile; depth: number; isOpen: boolean }[] = []
  const walk = (nodes: MockProjectFile[], depth: number) => {
    for (const n of nodes) {
      const isOpen = expanded.value[n.id] ?? depth === 0
      out.push({ node: n, depth, isOpen })
      if (n.isDir && isOpen && n.children) walk(n.children, depth + 1)
    }
  }
  walk(project.value.files, 0)
  return out
})
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

    <!-- 项目头 -->
    <div class="kh-container kh-container--wide">
      <KhCard padding="none" class="pd__head">
        <div class="pd__cover" :style="{ background: project.cover }">
          <KhIcon :name="project.icon" :size="48" class="pd__cover-icon" />
          <div class="pd__cover-tags">
            <span v-if="project.hot" class="pd__hot"><KhIcon name="fire" :size="12" /> 最活跃</span>
          </div>
        </div>
        <div class="pd__head-body">
          <div class="pd__head-tags">
            <KhTag type="primary">{{ typeLabel[project.type] }}</KhTag>
            <KhTag :type="statusMeta[project.status]?.type ?? 'neutral'" dot>{{ statusMeta[project.status]?.text ?? '未知' }}</KhTag>
            <KhTag type="neutral">等级 L{{ project.level }}</KhTag>
            <KhTag v-if="project.competition" type="warm">{{ project.competition.awardLevel }}</KhTag>
          </div>
          <h1 class="pd__title">{{ project.title }}</h1>
          <p class="pd__summary">{{ project.summary }}</p>

          <div class="pd__head-meta">
            <div class="pd__head-leader">
              <KhAvatar :item="{ label: project.leader }" :size="40" />
              <div>
                <div class="pd__head-leader-name">{{ project.leader }}</div>
                <div class="pd__head-leader-role">负责人 · {{ project.members.length }} 人团队</div>
              </div>
            </div>
            <div class="pd__head-stats">
              <KhRating :value="project.rating" :size="16" show-value />
              <KhStatPill icon="download" :value="project.downloadCount" label="下载" />
              <KhStatPill icon="clock" :value="project.updateTime" />
            </div>
            <div class="pd__head-actions">
              <button class="pd__download" type="button">
                <el-icon><Download /></el-icon> 下载源码
              </button>
              <button class="pd__feedback" type="button">
                <KhIcon name="megaphone" :size="14" /> 问题反馈
              </button>
            </div>
          </div>
        </div>
      </KhCard>
    </div>

    <!-- 主体布局 -->
    <div class="kh-container kh-container--wide pd__layout">
      <!-- 左：介绍 + 团队 + 版本 -->
      <div class="pd__main">
        <KhCard padding="lg" class="pd__section">
          <h2 class="pd__section-title">项目介绍</h2>
          <div class="pd__content">
            <pre class="pd__md">{{ project.description }}</pre>
          </div>
        </KhCard>

        <KhCard padding="lg" class="pd__section">
          <h2 class="pd__section-title">参与人员</h2>
          <div class="pd__members">
            <div
              v-for="m in project.members"
              :key="m.userId"
              class="pd__member"
              :class="`pd__member--${m.role.toLowerCase()}`"
            >
              <KhAvatar :item="{ label: m.name }" :size="44" />
              <div class="pd__member-info">
                <div class="pd__member-name">{{ m.name }}</div>
                <div class="pd__member-role">{{ memberRoleLabel[m.role] }}</div>
              </div>
              <KhTag v-if="m.role === 'LEADER'" type="warm" size="sm">负责人</KhTag>
              <KhTag v-else-if="m.role === 'MENTOR'" type="info" size="sm">导师</KhTag>
            </div>
          </div>
        </KhCard>

        <KhCard padding="lg" class="pd__section">
          <h2 class="pd__section-title">版本说明</h2>
          <ol class="pd__versions">
            <li v-for="v in project.versions" :key="v.ver" class="pd__version">
              <div class="pd__version-dot" />
              <div class="pd__version-body">
                <div class="pd__version-head">
                  <span class="pd__version-ver">{{ v.ver }}</span>
                  <span class="pd__version-time">{{ v.time }}</span>
                </div>
                <p class="pd__version-note">{{ v.note }}</p>
              </div>
            </li>
          </ol>
        </KhCard>
      </div>

      <!-- 右：文件树 + 信息卡 -->
      <aside class="pd__aside">
        <KhCard padding="md" class="pd__files">
          <div class="pd__files-head">
            <KhIcon name="file" :size="16" /> 项目文件
            <button class="pd__files-add" type="button"><el-icon><Plus /></el-icon></button>
          </div>
          <div class="pd__tree">
            <div
              v-for="item in visibleNodes"
              :key="item.node.id"
              class="pd__tree-node"
              :class="{ 'is-dir': item.node.isDir, 'is-file': !item.node.isDir }"
              :style="{ paddingLeft: `${item.depth * 16 + 10}px` }"
              @click="toggleNode(item.node)"
            >
              <el-icon v-if="item.node.isDir" class="pd__tree-icon"><Folder /></el-icon>
              <el-icon v-else class="pd__tree-icon"><Document /></el-icon>
              <span class="pd__tree-name">{{ item.node.name }}</span>
              <el-icon v-if="item.node.isDir" class="pd__tree-arrow">
                <KhIcon name="chevron-right" :size="12" />
              </el-icon>
            </div>
          </div>
        </KhCard>

        <KhCard padding="md" class="pd__info">
          <h3 class="pd__info-title">项目信息</h3>
          <div class="pd__info-row">
            <span>类型</span><b>{{ typeLabel[project.type] }}</b>
          </div>
          <div class="pd__info-row">
            <span>等级</span><b>L{{ project.level }}（{{ ['公开', '内部', '机密'][project.level - 1] }}）</b>
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
  overflow: hidden;
}
.pd__cover {
  position: relative;
  width: 240px;
  display: grid;
  place-items: center;
  flex: none;
}
.pd__cover-icon {
  color: rgba(255, 255, 255, 0.95);
}
.pd__cover-tags {
  position: absolute;
  top: var(--kh-space-3);
  left: var(--kh-space-3);
}
.pd__hot {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 3px 10px;
  border-radius: var(--kh-radius-pill);
  background: var(--kh-warm);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
}
.pd__head-body {
  flex: 1;
  padding: var(--kh-space-6);
  min-width: 0;
}
.pd__head-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: var(--kh-space-3);
}
.pd__title {
  font-size: var(--kh-font-size-3xl);
  font-weight: 700;
  letter-spacing: -0.01em;
}
.pd__summary {
  margin-top: var(--kh-space-3);
  color: var(--kh-text-secondary);
  font-size: var(--kh-font-size-md);
  line-height: 1.7;
}
.pd__head-meta {
  display: flex;
  align-items: center;
  gap: var(--kh-space-5);
  margin-top: var(--kh-space-5);
  padding-top: var(--kh-space-5);
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
.pd__head-actions {
  display: flex;
  gap: var(--kh-space-3);
  margin-left: auto;
}
.pd__download {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 40px;
  padding: 0 var(--kh-space-5);
  border: none;
  border-radius: var(--kh-radius-pill);
  background: linear-gradient(120deg, var(--kh-primary), var(--kh-primary-strong));
  color: #fff;
  font-weight: 600;
  font-size: var(--kh-font-size-sm);
  cursor: pointer;
  box-shadow: var(--kh-shadow-primary);
}
.pd__download:hover {
  transform: translateY(-1px);
}
.pd__feedback {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 40px;
  padding: 0 var(--kh-space-4);
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius-pill);
  background: var(--kh-surface);
  color: var(--kh-text-secondary);
  font-weight: 500;
  font-size: var(--kh-font-size-sm);
  cursor: pointer;
}
.pd__feedback:hover {
  border-color: var(--kh-warm);
  color: var(--kh-warm);
  background: var(--kh-warm-soft);
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
.pd__section-title {
  font-size: var(--kh-font-size-xl);
  font-weight: 700;
  margin-bottom: var(--kh-space-5);
}
.pd__content {
  color: var(--kh-text);
}
.pd__md {
  margin: 0;
  white-space: pre-wrap;
  word-wrap: break-word;
  font-family: var(--kh-font-body);
  font-size: var(--kh-font-size-md);
  line-height: 1.9;
}

.pd__members {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: var(--kh-space-4);
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

.pd__versions {
  list-style: none;
  margin: 0;
  padding: 0;
  position: relative;
}
.pd__version {
  display: flex;
  gap: var(--kh-space-4);
  padding-bottom: var(--kh-space-5);
  position: relative;
}
.pd__version::before {
  content: '';
  position: absolute;
  left: 5px;
  top: 14px;
  bottom: 0;
  width: 1px;
  background: var(--kh-border);
}
.pd__version:last-child::before {
  display: none;
}
.pd__version-dot {
  width: 11px;
  height: 11px;
  border-radius: 50%;
  background: var(--kh-primary);
  border: 2px solid var(--kh-surface);
  flex: none;
  margin-top: 4px;
  position: relative;
  z-index: 1;
}
.pd__version-body {
  flex: 1;
}
.pd__version-head {
  display: flex;
  align-items: center;
  gap: var(--kh-space-3);
}
.pd__version-ver {
  font-family: var(--kh-font-mono);
  font-size: var(--kh-font-size-sm);
  font-weight: 700;
  color: var(--kh-primary);
}
.pd__version-time {
  font-family: var(--kh-font-mono);
  font-size: 11px;
  color: var(--kh-text-tertiary);
}
.pd__version-note {
  margin-top: 6px;
  font-size: var(--kh-font-size-sm);
  color: var(--kh-text-secondary);
  line-height: 1.6;
}

/* 侧栏文件树 */
.pd__aside {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-5);
  position: sticky;
  top: calc(var(--kh-header-height) + var(--kh-space-4));
}
.pd__files-head {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--kh-text-tertiary);
  font-size: var(--kh-font-size-sm);
  font-weight: 600;
  margin-bottom: var(--kh-space-3);
}
.pd__files-add {
  margin-left: auto;
  width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius-sm);
  background: var(--kh-surface);
  color: var(--kh-text-tertiary);
  cursor: pointer;
}
.pd__files-add:hover {
  color: var(--kh-primary);
  border-color: var(--kh-primary-border);
}
.pd__tree {
  max-height: 360px;
  overflow: auto;
}
.pd__tree-node {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  border-radius: var(--kh-radius-sm);
  cursor: pointer;
  font-size: 13px;
  transition: background var(--kh-transition-fast);
}
.pd__tree-node:hover {
  background: var(--kh-surface-muted);
}
.pd__tree-icon {
  color: var(--kh-text-tertiary);
  font-size: 14px;
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
  color: var(--kh-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.pd__tree-arrow {
  color: var(--kh-text-tertiary);
  flex: none;
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
  .pd__head {
    flex-direction: column;
  }
  .pd__cover {
    width: 100%;
    height: 140px;
  }
  .pd__layout {
    grid-template-columns: 1fr;
  }
  .pd__aside {
    position: static;
  }
}
</style>
