<!--
  个人中心 /profile
  ------------------------------------------------------------------
  从右上角用户下拉菜单进入（不在主导航）。
  两栏布局：左 sticky 用户卡（头像+昵称+简介+创作入口+统计）/ 右 Tab 内容管理列表。
  优化布局密度与层次，不新增功能。
-->
<script setup lang="ts">
import { computed, ref } from 'vue'
import {
  Plus,
  Edit,
  RefreshLeft,
  Delete,
  Collection,
  Bell,
  ChatDotRound,
} from '@element-plus/icons-vue'
import KhCard from '@/components/common/KhCard.vue'
import KhTag from '@/components/common/KhTag.vue'
import KhAvatar from '@/components/common/KhAvatar.vue'
import KhIcon from '@/components/common/KhIcon.vue'
import { useUserStore } from '@/stores/user'
import { currentUser } from '@/mock/user'
import { blogs } from '@/mock/blog'
import { projects } from '@/mock/project'
import { resources } from '@/mock/resource'
import { notices } from '@/mock/notice'
import { viewLevelTagType, getViewLevelLabel } from '@/utils/viewLevel'

const userStore = useUserStore()

type TabKey = 'blog' | 'article' | 'project' | 'resource' | 'collect' | 'message'
const activeTab = ref<TabKey>('blog')

/**
 * 当前登录用户展示信息。
 * 姓名 / 账号 / 角色优先取 /person 真实资料，缺失时退回 mock（bio / stats / 创作列表暂仍用 mock，待后续接口接入）。
 * 按本轮约定：仅顶栏与个人中心的"名字"动态化，其余字段保持 mock。
 */
const displayName = computed(
  () => userStore.userInfo?.nickName || userStore.userInfo?.username || currentUser.name,
)
const displayUsername = computed(() => userStore.userInfo?.username || currentUser.username)
const displayRole = computed(() => {
  const roles = userStore.userInfo?.userRole ?? []
  return roles.length > 0 ? roles[0]!.roleName : currentUser.role
})

/** 当前用户的博客（mock 取部分） */
const myBlogs = blogs.slice(0, 5).map((b) => ({
  id: b.blogId,
  title: b.title,
  status: b.status,
  views: b.viewCount,
  likes: b.likeCount,
  updateTime: b.publishTime,
}))
const myProjects = projects
  .filter(
    (p) =>
      p.authorNickname === currentUser.name ||
      p.members.some((m) => m.nickname === currentUser.name),
  )
  .slice(0, 4)
  .map((p) => ({
    id: p.projectId,
    title: p.title,
    type: p.type,
    status: p.status,
    level: p.level,
    updateTime: p.updateTime,
  }))
const myResources = resources.slice(0, 4).map((r) => ({
  id: r.resourceId,
  title: r.title,
  category: r.category,
  status: r.status,
  downloadCount: r.downloadCount,
  updateTime: r.createTime,
}))
const myCollects = [...blogs].slice(2, 5)
const myMessages = notices.slice(0, 3)

const tabs: { key: TabKey; label: string; count: number }[] = [
  { key: 'blog', label: '我的博客', count: currentUser.stats.blogs },
  { key: 'article', label: '我的文章', count: 8 },
  { key: 'project', label: '我的项目', count: currentUser.stats.projects },
  { key: 'resource', label: '我的资源', count: currentUser.stats.resources },
  { key: 'collect', label: '我的收藏', count: currentUser.stats.collections },
  { key: 'message', label: '消息通知', count: 3 },
]

/** 创作下拉 */
const createItems = [
  { icon: 'blog', label: '创作博客', tone: 'var(--kh-primary)' },
  { icon: 'doc', label: '创作文章', tone: 'var(--kh-accent)' },
  { icon: 'project', label: '创建项目', tone: 'var(--kh-warm)' },
  { icon: 'resource', label: '上传资源', tone: 'var(--kh-success)' },
] as const

const statusMeta: Record<string, { text: string; type: 'success' | 'warning' | 'danger' | 'neutral' | 'info' }> = {
  PUBLISHED: { text: '已发布', type: 'success' },
  PENDING_REVIEW: { text: '待审核', type: 'warning' },
  REJECTED: { text: '已驳回', type: 'danger' },
  DRAFT: { text: '草稿', type: 'neutral' },
  REVOKED: { text: '已撤回', type: 'neutral' },
  ARCHIVED: { text: '已归档', type: 'info' },
}

const typeLabel: Record<string, string> = { COMPETITION: '比赛', PRACTICE: '练习', OPS: '运维' }
const catLabel: Record<string, string> = { WEBSITE: '网站', SOFTWARE: '软件', SCRIPT: '脚本', DOCUMENT: '文档', TOOL: '工具' }

/** 统计卡 */
const userStats = computed(() => [
  { label: '博客', value: currentUser.stats.blogs, icon: 'blog' as const, tone: 'var(--kh-primary)' },
  { label: '项目', value: currentUser.stats.projects, icon: 'project' as const, tone: 'var(--kh-accent)' },
  { label: '资源', value: currentUser.stats.resources, icon: 'resource' as const, tone: 'var(--kh-warm)' },
  { label: '获赞', value: currentUser.stats.likes, icon: 'heart' as const, tone: 'var(--kh-danger)' },
  { label: '获藏', value: currentUser.stats.collections, icon: 'bookmark' as const, tone: 'var(--kh-info)' },
])

/** 当前 Tab 标题 */
const activeTabLabel = computed(() => tabs.find((t) => t.key === activeTab.value)?.label ?? '')
</script>

<template>
  <div class="profile">
    <div class="kh-container kh-container--wide profile__body">
      <!-- 左 sticky 侧栏：用户卡 + 创作入口 + 统计 -->
      <aside class="profile__side">
        <KhCard padding="lg" class="profile__card">
          <div class="profile__head">
            <KhAvatar :item="{ label: displayName }" :size="72" />
            <div class="profile__info">
              <div class="profile__name-row">
                <h1 class="profile__name">{{ displayName }}</h1>
                <KhTag type="primary" dot>{{ displayRole }}</KhTag>
              </div>
              <div class="profile__username">@{{ displayUsername }}</div>
            </div>
          </div>
          <p class="profile__bio">{{ currentUser.bio }}</p>

          <div class="profile__actions">
            <el-dropdown trigger="click">
              <button class="profile__create" type="button">
                <el-icon><Plus /></el-icon> 创作
                <el-icon class="profile__create-caret"><KhIcon name="chevron-right" :size="12" /></el-icon>
              </button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-for="c in createItems" :key="c.label">
                    <KhIcon :name="c.icon" :size="14" :style="{ color: c.tone }" /> {{ c.label }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <button class="profile__edit" type="button">
              <el-icon><Edit /></el-icon> 编辑资料
            </button>
          </div>

          <div class="profile__stats">
            <div v-for="s in userStats" :key="s.label" class="profile__stat">
              <div class="profile__stat-icon" :style="{ color: s.tone, background: `${s.tone}1a` }">
                <KhIcon :name="s.icon" :size="16" />
              </div>
              <div>
                <div class="profile__stat-value">{{ s.value }}</div>
                <div class="profile__stat-label">{{ s.label }}</div>
              </div>
            </div>
          </div>
        </KhCard>
      </aside>

      <!-- 右：Tab + 内容管理 -->
      <section class="profile__main">
        <KhCard padding="none" class="profile__tabs-card">
          <div class="profile__tabs">
            <button
              v-for="t in tabs"
              :key="t.key"
              class="profile__tab"
              :class="{ 'is-active': activeTab === t.key }"
              type="button"
              @click="activeTab = t.key"
            >
              {{ t.label }}
              <span class="profile__tab-count">{{ t.count }}</span>
            </button>
          </div>

          <div class="profile__tab-content">
            <div class="profile__tab-head">
              <h2 class="profile__tab-title">{{ activeTabLabel }}</h2>
              <div class="profile__tab-tools">
                <button class="profile__tab-tool" type="button"><el-icon><Plus /></el-icon> 新建</button>
              </div>
            </div>

            <!-- 我的博客 -->
            <div v-if="activeTab === 'blog'" class="profile__list">
              <div v-for="b in myBlogs" :key="b.id" class="profile__row">
                <div class="profile__row-main">
                  <div class="profile__row-title">{{ b.title }}</div>
                  <div class="profile__row-meta">
                    <KhTag size="sm" :type="statusMeta[b.status]?.type ?? 'neutral'">{{ statusMeta[b.status]?.text ?? '未知' }}</KhTag>
                    <span>{{ b.views }} 阅读</span>
                    <span>·</span>
                    <span>{{ b.likes }} 赞</span>
                    <span>·</span>
                    <span>{{ b.updateTime }}</span>
                  </div>
                </div>
                <div class="profile__row-actions">
                  <button class="profile__row-btn" type="button" title="编辑"><el-icon><Edit /></el-icon></button>
                  <button class="profile__row-btn" type="button" title="撤回"><el-icon><RefreshLeft /></el-icon></button>
                  <button class="profile__row-btn profile__row-btn--danger" type="button" title="删除"><el-icon><Delete /></el-icon></button>
                </div>
              </div>
            </div>

            <!-- 我的文章（占位同结构） -->
            <div v-else-if="activeTab === 'article'" class="profile__list">
              <div class="profile__placeholder">
                <KhIcon name="doc" :size="40" :stroke="1.4" />
                <p>文章管理（章节集合型）—— 内容同博客列表结构，正式制作时接入文章模块接口</p>
              </div>
            </div>

            <!-- 我的项目 -->
            <div v-else-if="activeTab === 'project'" class="profile__list">
              <div v-for="p in myProjects" :key="p.id" class="profile__row">
                <div class="profile__row-main">
                  <div class="profile__row-title">{{ p.title }}</div>
                  <div class="profile__row-meta">
                    <KhTag size="sm" type="primary">{{ typeLabel[p.type] }}</KhTag>
                    <KhTag size="sm" :type="statusMeta[p.status]?.type ?? 'neutral'">{{ statusMeta[p.status]?.text ?? '未知' }}</KhTag>
                    <KhTag size="sm" :type="viewLevelTagType[p.level] ?? 'neutral'">{{ getViewLevelLabel(p.level) }}</KhTag>
                    <span>·</span>
                    <span>{{ p.updateTime }}</span>
                  </div>
                </div>
                <div class="profile__row-actions">
                  <button class="profile__row-btn" type="button" title="编辑"><el-icon><Edit /></el-icon></button>
                  <button class="profile__row-btn profile__row-btn--danger" type="button" title="删除"><el-icon><Delete /></el-icon></button>
                </div>
              </div>
            </div>

            <!-- 我的资源 -->
            <div v-else-if="activeTab === 'resource'" class="profile__list">
              <div v-for="r in myResources" :key="r.id" class="profile__row">
                <div class="profile__row-main">
                  <div class="profile__row-title">{{ r.title }}</div>
                  <div class="profile__row-meta">
                    <KhTag size="sm" type="accent">{{ catLabel[r.category] }}</KhTag>
                    <KhTag size="sm" :type="statusMeta[r.status]?.type ?? 'neutral'">{{ statusMeta[r.status]?.text ?? '未知' }}</KhTag>
                    <span>·</span>
                    <span>{{ r.downloadCount }} 下载</span>
                    <span>·</span>
                    <span>{{ r.updateTime }}</span>
                  </div>
                </div>
                <div class="profile__row-actions">
                  <button class="profile__row-btn" type="button" title="编辑"><el-icon><Edit /></el-icon></button>
                  <button class="profile__row-btn profile__row-btn--danger" type="button" title="删除"><el-icon><Delete /></el-icon></button>
                </div>
              </div>
            </div>

            <!-- 我的收藏 -->
            <div v-else-if="activeTab === 'collect'" class="profile__list">
              <div v-for="c in myCollects" :key="c.blogId" class="profile__row">
                <div class="profile__row-main">
                  <div class="profile__row-title">{{ c.title }}</div>
                  <div class="profile__row-meta">
                    <KhTag size="sm" type="info"><el-icon><Collection /></el-icon> 博客</KhTag>
                    <span>{{ c.authorNickname }}</span>
                    <span>·</span>
                    <span>{{ c.viewCount }} 阅读</span>
                  </div>
                </div>
                <div class="profile__row-actions">
                  <button class="profile__row-btn profile__row-btn--danger" type="button" title="取消收藏"><el-icon><Delete /></el-icon></button>
                </div>
              </div>
            </div>

            <!-- 消息通知 -->
            <div v-else class="profile__list">
              <div v-for="m in myMessages" :key="m.id" class="profile__row">
                <div class="profile__row-main">
                  <div class="profile__row-title">
                    <el-icon class="profile__msg-icon"><ChatDotRound /></el-icon>
                    {{ m.title }}
                  </div>
                  <div class="profile__row-meta">
                    <KhTag size="sm" :type="m.type === '活动' ? 'warm' : m.type === '维护' ? 'warning' : 'primary'">{{ m.type }}</KhTag>
                    <span>{{ m.publisher }}</span>
                    <span>·</span>
                    <span>{{ m.publishTime }}</span>
                  </div>
                </div>
                <div class="profile__row-actions">
                  <button class="profile__row-btn" type="button" title="标记已读"><el-icon><Bell /></el-icon></button>
                </div>
              </div>
            </div>
          </div>
        </KhCard>
      </section>
    </div>
  </div>
</template>

<style scoped>
.profile {
  padding-top: var(--kh-space-8);
  padding-bottom: var(--kh-space-12);
  background: var(--kh-bg);
  min-height: 60vh;
}
.profile__body {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: var(--kh-space-6);
  align-items: start;
}

/* —— 左侧用户卡 —— */
.profile__side {
  position: sticky;
  top: calc(var(--kh-header-height) + var(--kh-space-4));
}
.profile__card {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-5);
}
.profile__head {
  display: flex;
  align-items: center;
  gap: var(--kh-space-4);
}
.profile__info {
  flex: 1;
  min-width: 0;
}
.profile__name-row {
  display: flex;
  align-items: center;
  gap: var(--kh-space-2);
  flex-wrap: wrap;
}
.profile__name {
  font-size: var(--kh-font-size-2xl);
  font-weight: 700;
}
.profile__username {
  font-family: var(--kh-font-mono);
  font-size: var(--kh-font-size-sm);
  color: var(--kh-text-tertiary);
  margin-top: 2px;
}
.profile__bio {
  color: var(--kh-text-secondary);
  font-size: var(--kh-font-size-sm);
  line-height: 1.7;
  padding-top: var(--kh-space-3);
  border-top: 1px solid var(--kh-border-soft);
  margin: 0;
}
.profile__actions {
  display: flex;
  gap: var(--kh-space-3);
}
.profile__create {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex: 1;
  justify-content: center;
  height: 40px;
  padding: 0 var(--kh-space-4);
  border: none;
  border-radius: var(--kh-radius-pill);
  background: linear-gradient(120deg, var(--kh-primary), var(--kh-primary-strong));
  color: #fff;
  font-weight: 600;
  font-size: var(--kh-font-size-sm);
  cursor: pointer;
  box-shadow: var(--kh-shadow-primary);
}
.profile__create-caret {
  margin-left: 2px;
}
.profile__edit {
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
.profile__edit:hover {
  border-color: var(--kh-primary-border);
  color: var(--kh-primary);
}
.profile__stats {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-3);
  padding-top: var(--kh-space-4);
  border-top: 1px solid var(--kh-border-soft);
}
.profile__stat {
  display: flex;
  align-items: center;
  gap: var(--kh-space-3);
}
.profile__stat-icon {
  width: 36px;
  height: 36px;
  border-radius: var(--kh-radius);
  display: grid;
  place-items: center;
  flex: none;
}
.profile__stat-value {
  font-family: var(--kh-font-display);
  font-size: var(--kh-font-size-lg);
  font-weight: 700;
  color: var(--kh-text);
  font-variant-numeric: tabular-nums;
  line-height: 1.1;
}
.profile__stat-label {
  font-size: 12px;
  color: var(--kh-text-tertiary);
}

/* —— 右侧 Tab 区 —— */
.profile__main {
  min-width: 0;
}
.profile__tabs-card {
  overflow: hidden;
}
.profile__tabs {
  display: flex;
  gap: 2px;
  padding: var(--kh-space-3) var(--kh-space-3) 0;
  border-bottom: 1px solid var(--kh-border-soft);
  overflow-x: auto;
}
.profile__tab {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 12px 18px;
  border: none;
  background: transparent;
  color: var(--kh-text-secondary);
  font-size: var(--kh-font-size-sm);
  font-weight: 500;
  cursor: pointer;
  border-bottom: 2px solid transparent;
  white-space: nowrap;
  transition: color var(--kh-transition-fast);
}
.profile__tab:hover {
  color: var(--kh-text);
}
.profile__tab.is-active {
  color: var(--kh-primary);
  border-bottom-color: var(--kh-primary);
  font-weight: 600;
}
.profile__tab-count {
  padding: 1px 7px;
  border-radius: var(--kh-radius-pill);
  background: var(--kh-bg-soft);
  color: var(--kh-text-tertiary);
  font-size: 11px;
  font-weight: 500;
}
.profile__tab.is-active .profile__tab-count {
  background: var(--kh-primary-soft);
  color: var(--kh-primary-strong);
}
.profile__tab-content {
  padding: 0 var(--kh-space-5);
}
.profile__tab-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--kh-space-5) 0 var(--kh-space-4);
}
.profile__tab-title {
  font-size: var(--kh-font-size-lg);
  font-weight: 700;
}
.profile__tab-tools {
  display: flex;
  gap: var(--kh-space-2);
}
.profile__tab-tool {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 7px 14px;
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius-pill);
  background: var(--kh-surface);
  color: var(--kh-text-secondary);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--kh-transition-fast);
}
.profile__tab-tool:hover {
  border-color: var(--kh-primary-border);
  color: var(--kh-primary);
  background: var(--kh-primary-soft);
}

.profile__list {
  display: flex;
  flex-direction: column;
}
.profile__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kh-space-4);
  padding: var(--kh-space-4) var(--kh-space-2);
  border-bottom: 1px solid var(--kh-border-soft);
  transition: background var(--kh-transition-fast);
}
.profile__row:hover {
  background: var(--kh-surface-muted);
}
.profile__row:last-child {
  border-bottom: none;
}
.profile__row-main {
  min-width: 0;
  flex: 1;
}
.profile__row-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--kh-font-size-md);
  font-weight: 500;
  color: var(--kh-text);
  cursor: pointer;
}
.profile__row-title:hover {
  color: var(--kh-primary);
}
.profile__msg-icon {
  color: var(--kh-warm);
}
.profile__row-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  font-size: 12px;
  color: var(--kh-text-tertiary);
  flex-wrap: wrap;
}
.profile__row-actions {
  display: flex;
  gap: 6px;
  flex: none;
}
.profile__row-btn {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius-sm);
  background: var(--kh-surface);
  color: var(--kh-text-secondary);
  cursor: pointer;
  transition: all var(--kh-transition-fast);
}
.profile__row-btn:hover {
  border-color: var(--kh-primary-border);
  color: var(--kh-primary);
  background: var(--kh-primary-soft);
}
.profile__row-btn--danger:hover {
  border-color: var(--kh-danger);
  color: var(--kh-danger);
  background: var(--kh-danger-soft);
}
.profile__placeholder {
  text-align: center;
  color: var(--kh-text-tertiary);
  padding: var(--kh-space-10);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--kh-space-3);
}

@media (max-width: 900px) {
  .profile__body {
    grid-template-columns: 1fr;
  }
  .profile__side {
    position: static;
  }
}
</style>
