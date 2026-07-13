<!--
  ProjectCard —— 项目卡片
  ------------------------------------------------------------------
  封面图标 + 类型/状态/等级徽标 + 标题 + 简介 + 负责人 + 参与者头像组 + 评分 + 活跃度。
-->
<script setup lang="ts">
import { useRouter } from 'vue-router'
import KhCard from '@/components/common/KhCard.vue'
import KhTag from '@/components/common/KhTag.vue'
import KhAvatar from '@/components/common/KhAvatar.vue'
import KhRating from '@/components/common/KhRating.vue'
import KhStatPill from '@/components/common/KhStatPill.vue'
import KhIcon from '@/components/common/KhIcon.vue'
import type { MockProject } from '@/mock/project'

const props = defineProps<{ project: MockProject }>()
const router = useRouter()
const goDetail = () => router.push(`/project/${props.project.id}`)

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
  <KhCard clickable padding="none" class="proj-card" @click="goDetail">
    <div class="proj-card__cover" :style="{ background: project.cover }">
      <KhIcon :name="project.icon" :size="32" class="proj-card__cover-icon" />
      <div class="proj-card__badges">
        <span v-if="project.hot" class="proj-card__hot">
          <KhIcon name="fire" :size="12" /> 最活跃
        </span>
      </div>
      <div class="proj-card__level">L{{ project.level }}</div>
    </div>

    <div class="proj-card__body">
      <div class="proj-card__tags">
        <KhTag size="sm" type="primary">{{ typeLabel[project.type] }}</KhTag>
        <KhTag size="sm" :type="statusLabel[project.status]?.type ?? 'neutral'" dot>{{ statusLabel[project.status]?.text ?? '未知' }}</KhTag>
      </div>

      <h3 class="proj-card__title kh-line-clamp-2">{{ project.title }}</h3>
      <p class="proj-card__summary kh-line-clamp-2">{{ project.summary }}</p>

      <div class="proj-card__people">
        <div class="proj-card__leader">
          <KhAvatar :item="{ label: project.leader }" :size="28" />
          <div class="proj-card__leader-info">
            <span class="proj-card__leader-name">{{ project.leader }}</span>
            <span class="proj-card__leader-role">负责人</span>
          </div>
        </div>
        <KhAvatar
          :items="project.members.map((m) => ({ label: m.name }))"
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
    </div>
  </KhCard>
</template>

<style scoped>
.proj-card {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.proj-card__cover {
  position: relative;
  height: 110px;
  display: grid;
  place-items: center;
}
.proj-card__cover-icon {
  color: rgba(255, 255, 255, 0.92);
}
.proj-card__badges {
  position: absolute;
  top: var(--kh-space-3);
  left: var(--kh-space-3);
}
.proj-card__hot {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 3px 10px;
  border-radius: var(--kh-radius-pill);
  background: var(--kh-warm);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  box-shadow: var(--kh-shadow-xs);
}
.proj-card__level {
  position: absolute;
  top: var(--kh-space-3);
  right: var(--kh-space-3);
  width: 28px;
  height: 28px;
  border-radius: var(--kh-radius-sm);
  background: rgba(255, 255, 255, 0.92);
  color: var(--kh-text);
  font-family: var(--kh-font-mono);
  font-size: 12px;
  font-weight: 700;
  display: grid;
  place-items: center;
}
.proj-card__body {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-3);
  padding: var(--kh-space-5);
  flex: 1;
}
.proj-card__tags {
  display: flex;
  gap: 6px;
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
