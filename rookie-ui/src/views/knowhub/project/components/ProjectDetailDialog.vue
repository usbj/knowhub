<!--
  文件作用：
  项目详情弹窗，承接项目列表「详情」操作后的完整信息展示 + 成员管理 + GitHub 式文件树 + 审核历史。
  关键参数：
  - `visible`：弹窗显隐，由父层双向绑定控制。
  - `project`：当前展示的项目记录，含详细介绍与权限态。
  关键依赖：
  - 复用 ElDialog + MarkdownPreview（与资源详情同款排版与遮罩，视觉统一）；
  - 状态/类型/等级/审核状态走字典标签系统渲染为带色 ElTag；
  - 权限态 canEdit/canDownload 控制成员面板与文件树的操作按钮显隐；
  - 成员区用 ProjectMemberPanel；文件区用 ProjectFileTree（GitHub 式侧边栏）；
  - 审核历史折叠区：弹窗打开时按 projectId 拉取审核流水，按时间线展示动作/操作人昵称/时间/意见。
  - 主题适配：正文区、元信息条、标签、时间线均用 base.css 的 --rookie-* 变量，深浅模式自动跟随。
-->
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElCollapse, ElCollapseItem, ElDialog, ElEmpty, ElTabPane, ElTabs } from 'element-plus'
import DictTag from '@/components/DictTag.vue'
import MarkdownPreview from '@/components/MarkdownPreview.vue'
import { formatDateTime } from '@/utils/format'
import { getProjectReviewLogApi } from '@/api/knowhub/project'
import type { ProjectRecord, ProjectReviewLogRecord } from '@/types/api/knowhub/project'
import ProjectFileTree from './ProjectFileTree.vue'
import ProjectMemberPanel from './ProjectMemberPanel.vue'

const props = defineProps<{
  visible: boolean
  project: ProjectRecord | null
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

const dialogTitle = computed(() => '项目详情')
const activeTab = ref<'info' | 'members' | 'files' | 'review'>('info')

const reviewLogs = ref<ProjectReviewLogRecord[]>([])
const reviewLogLoading = ref(false)

const formatTime = (value: unknown) => formatDateTime(value) || '--'

/** 权限态：详情接口回填，供子组件控制操作按钮显隐 */
/** 详情弹窗只读：编辑/加成员/加文件一律走编辑弹窗，详情不承担改数据职责（用户明确要求）。
 *  下载属查看行为，仍按 canDownload/LEADER 判定显隐下载按钮。 */
const canEdit = false
const canDownload = computed(() => Boolean(props.project?.canDownload) || props.project?.myMemberRole === 'LEADER')

watch(
  () => [props.visible, props.project?.projectId] as const,
  async ([visible, projectId]) => {
    if (!visible || !projectId) {
      reviewLogs.value = []
      activeTab.value = 'info'
      return
    }
    reviewLogLoading.value = true
    try {
      const result = await getProjectReviewLogApi(projectId)
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
    width="900px"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
  >
    <article v-if="project" class="project-detail">
      <h1 class="project-detail__title">{{ project.title }}</h1>

      <div class="project-detail__meta">
        <DictTag dict-key="project_status" :value="project.status" />
        <DictTag v-if="project.reviewStatus" dict-key="review_status" :value="project.reviewStatus" />
        <DictTag dict-key="project_type" :value="project.type" />
        <DictTag dict-key="project_level" :value="String(project.level)" />
      </div>

      <div class="project-detail__info">
        <span><em>负责人</em>{{ project.authorNickname || project.createBy || '--' }}</span>
        <span><em>我的角色</em>{{ project.myMemberRole || '非成员' }}</span>
        <span><em>发布时间</em>{{ formatTime(project.publishTime) }}</span>
        <span><em>创建时间</em>{{ formatTime(project.createTime) }}</span>
      </div>

      <p v-if="project.summary" class="project-detail__summary">{{ project.summary }}</p>

      <!-- 标签页：详情正文 / 成员 / 文件 / 审核历史 -->
      <ElTabs v-model="activeTab" class="project-detail__tabs">
        <ElTabPane label="项目介绍" name="info">
          <MarkdownPreview
            v-if="project.description"
            class="project-detail__content"
            :model-value="project.description"
          />
          <ElEmpty v-else description="暂无详细介绍" :image-size="48" />
        </ElTabPane>

        <ElTabPane label="团队成员" name="members">
          <ProjectMemberPanel :project-id="project.projectId" :can-edit="canEdit" />
        </ElTabPane>

        <ElTabPane label="项目文件" name="files">
          <ProjectFileTree :project-id="project.projectId" :can-edit="canEdit" :can-download="canDownload" />
        </ElTabPane>

        <ElTabPane label="审核历史" name="review">
          <ElCollapse v-if="reviewLogs.length > 0" class="project-detail__review-log">
            <ElCollapseItem title="审核历史" name="review-log">
              <ul class="project-detail__timeline">
                <li v-for="log in reviewLogs" :key="log.reviewLogId" class="project-detail__timeline-item">
                  <div class="project-detail__timeline-head">
                    <DictTag dict-key="review_action" :value="log.action" />
                    <span class="project-detail__timeline-operator">{{ log.operatorNickname || log.operator }}</span>
                    <span class="project-detail__timeline-time">{{ formatTime(log.createTime) }}</span>
                  </div>
                  <p v-if="log.advice" class="project-detail__timeline-advice">{{ log.advice }}</p>
                </li>
              </ul>
            </ElCollapseItem>
          </ElCollapse>
          <ElEmpty
            v-else-if="!reviewLogLoading"
            description="暂无审核历史"
            :image-size="48"
          />
        </ElTabPane>
      </ElTabs>
    </article>

    <template #footer>
      <div class="project-detail__footer">
        <ElButton @click="emit('update:visible', false)">关闭</ElButton>
      </div>
    </template>
  </ElDialog>
</template>

<style scoped>
.project-detail {
  display: grid;
  gap: 18px;
}

.project-detail__title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  line-height: 1.4;
  color: var(--rookie-text);
}

.project-detail__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.project-detail__info {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px 24px;
  padding: 12px 16px;
  border: 1px solid var(--rookie-border);
  border-radius: var(--rookie-radius-md);
  background: var(--rookie-surface-weak);
  font-size: var(--rookie-font-size-sm);
  color: var(--rookie-text-secondary);
}

.project-detail__info em {
  color: var(--rookie-text-tertiary);
  font-style: normal;
  margin-right: 8px;
}

.project-detail__summary {
  margin: 0;
  padding: 8px 12px;
  border-left: 3px solid var(--rookie-primary);
  background: var(--rookie-primary-soft);
  color: var(--rookie-text-secondary);
  font-size: var(--rookie-font-size-sm);
  line-height: 1.6;
  border-radius: 0 var(--rookie-radius-sm) var(--rookie-radius-sm) 0;
}

.project-detail__tabs {
  min-height: 200px;
}

.project-detail__content {
  max-height: 420px;
  overflow-y: auto;
  padding: 4px 0;
}

.project-detail__timeline {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 12px;
}

.project-detail__timeline-item {
  padding: 8px 12px;
  border-left: 2px solid var(--rookie-border);
  background: var(--rookie-surface-weak);
  border-radius: 0 var(--rookie-radius-sm) var(--rookie-radius-sm) 0;
}

.project-detail__timeline-head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  font-size: var(--rookie-font-size-sm);
  color: var(--rookie-text-secondary);
}

.project-detail__timeline-operator {
  color: var(--rookie-text);
}

.project-detail__timeline-time {
  color: var(--rookie-text-tertiary);
}

.project-detail__timeline-advice {
  margin: 6px 0 0;
  color: var(--rookie-text-secondary);
  font-size: var(--rookie-font-size-sm);
  line-height: 1.6;
}

.project-detail__footer {
  display: flex;
  justify-content: flex-end;
}
</style>
