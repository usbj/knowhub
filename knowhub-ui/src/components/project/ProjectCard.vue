<!--
  ProjectCard —— 项目卡片
  ------------------------------------------------------------------
  类型/状态/等级徽标 + 标题 + 简介 + 负责人 + 参与者头像组 + 评分 + 下载量。
  项目无封面："最活跃"徽标已去掉，卡片头部行只留等级标记。
-->
<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import KhCard from '@/components/common/KhCard.vue'
import KhTag from '@/components/common/KhTag.vue'
import KhAvatar from '@/components/common/KhAvatar.vue'
import KhRating from '@/components/common/KhRating.vue'
import KhStatPill from '@/components/common/KhStatPill.vue'
import type { MockProject } from '@/mock/project'
import { viewLevelTagType, getViewLevelLabel } from '@/utils/viewLevel'

const props = defineProps<{ project: MockProject }>()
const router = useRouter()
const goDetail = () => router.push(`/project/${props.project.projectId}`)

/** 负责人：取成员表 LEADER 角色的 nickname（与项目模块负责人语义一致） */
const leader = computed(
  () => props.project.members.find((m) => m.role === 'LEADER')?.nickname ?? props.project.authorNickname,
)

/** 类型/状态文案映射 */
const typeLabel: Record<string, string> = { COMPETITION: '比赛项目', PRACTICE: '练习项目', OPS: '运维项目' }
const statusLabel: Record<string, { text: string; type: 'success' | 'warning' | 'danger' | 'neutral' | 'info' }> = {
  PUBLISHED: { text: '已发布', type: 'success' },
  PENDING_REVIEW: { text: '待审核', type: 'warning' },
  REJECTED: { text: '已驳回', type: 'danger' },
  DRAFT: { text: '草稿', type: 'neutral' },
  REVOKED: { text: '已撤回', type: 'neutral' },
  ARCHIVED: { text: '已归档', type: 'info' },
}
</script>

<template>
  <KhCard clickable padding="md" class="proj-card" @click="goDetail">
    <!-- 头部行：类型/状态标签 + 等级 -->
    <div class="proj-card__header">
      <div class="proj-card__tags">
        <KhTag size="sm" type="primary">{{ typeLabel[project.type] }}</KhTag>
        <KhTag size="sm" :type="statusLabel[project.status]?.type ?? 'neutral'" dot>{{ statusLabel[project.status]?.text ?? '未知' }}</KhTag>
      </div>
      <div class="proj-card__header-tail">
        <KhTag size="sm" :type="viewLevelTagType[project.level] ?? 'neutral'">{{ getViewLevelLabel(project.level) }}</KhTag>
      </div>
    </div>

    <h3 class="proj-card__title kh-line-clamp-2">{{ project.title }}</h3>
    <p class="proj-card__summary kh-line-clamp-2">{{ project.summary }}</p>

    <div class="proj-card__people">
      <div class="proj-card__leader">
        <KhAvatar :item="{ label: leader }" :size="28" />
        <div class="proj-card__leader-info">
          <span class="proj-card__leader-name">{{ leader }}</span>
          <span class="proj-card__leader-role">负责人</span>
        </div>
      </div>
      <KhAvatar
        :items="project.members.map((m) => ({ label: m.nickname }))"
        :size="26"
        :max="3"
        :overlap="6"
      />
    </div>

    <div class="proj-card__stats">
      <KhRating :value="project.rating" :size="12" show-value />
      <span class="proj-card__spacer" />
      <KhStatPill icon="download" :value="project.downloadCount" />
    </div>
  </KhCard>
</template>

<style scoped>
.proj-card {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-3);
  height: 100%;
}
.proj-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kh-space-2);
}
.proj-card__tags {
  display: flex;
  gap: 6px;
}
.proj-card__header-tail {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex: none;
}
.proj-card__title {
  font-size: var(--kh-font-size-lg);
  font-weight: 600;
  line-height: 1.45;
}
.proj-card__summary {
  font-size: var(--kh-font-size-sm);
  color: var(--kh-text-secondary);
  line-height: 1.6;
}
.proj-card__people {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kh-space-3);
  margin-top: auto;
  padding-top: var(--kh-space-2);
}
.proj-card__leader {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.proj-card__leader-info {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
  min-width: 0;
}
.proj-card__leader-name {
  font-size: var(--kh-font-size-sm);
  color: var(--kh-text);
  font-weight: 500;
}
.proj-card__leader-role {
  font-size: 10px;
  color: var(--kh-text-tertiary);
}
.proj-card__stats {
  display: flex;
  align-items: center;
  gap: var(--kh-space-3);
  padding-top: var(--kh-space-3);
  border-top: 1px solid var(--kh-border-soft);
}
.proj-card__spacer {
  flex: 1;
}
</style>
