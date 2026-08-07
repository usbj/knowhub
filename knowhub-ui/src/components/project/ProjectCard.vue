<!--
  ProjectCard —— 项目卡片
  ------------------------------------------------------------------
  类型/等级徽标 + 标题 + 简介 + 负责人 + 浏览/下载量。
  数据源：后端 ProjectPortalVo（/portal/project/search|recommend）。
  说明：原本 mock 的评分 rating / 参与者头像组（members）/ "最活跃" 徽标均不在后端 VO 上
  （后端列表只回元数据 + 计数，无成员关系回填），按 README.dev §11.1 私加字段方向去除，
  卡片以"类型徽标 + 等级标签 + 负责人 + 下载/浏览"为主——与博客卡片同口径。
-->
<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import KhCard from '@/components/common/KhCard.vue'
import KhTag from '@/components/common/KhTag.vue'
import KhAvatar from '@/components/common/KhAvatar.vue'
import KhStatPill from '@/components/common/KhStatPill.vue'
import type { ProjectPortalRecord } from '@/types/api/knowhub/project-portal'
import { viewLevelTagType, getViewLevelLabel } from '@/utils/viewLevel'

const props = defineProps<{ project: ProjectPortalRecord }>()
const router = useRouter()
const goDetail = () => router.push(`/project/${props.project.projectId}`)

/** 负责人昵称（后端 join sys_user on author_id 带出，直接展示） */
const leader = computed(() => props.project.authorNickname ?? '未知负责人')

/** 类型文案映射 */
const typeLabel: Record<string, string> = { COMPETITION: '比赛项目', PRACTICE: '练习项目', OPS: '运维项目' }
const typeText = computed(() => typeLabel[props.project.type ?? ''] ?? props.project.type ?? '项目')
</script>

<template>
  <KhCard clickable padding="md" class="proj-card" @click="goDetail">
    <!-- 头部行：类型标签 + 等级 -->
    <div class="proj-card__header">
      <div class="proj-card__tags">
        <KhTag size="sm" type="primary">{{ typeText }}</KhTag>
      </div>
      <div class="proj-card__header-tail">
        <KhTag size="sm" :type="viewLevelTagType[project.level ?? 1] ?? 'neutral'">{{ getViewLevelLabel(project.level ?? 1) }}</KhTag>
      </div>
    </div>

    <h3 class="proj-card__title kh-line-clamp-2">{{ project.title }}</h3>
    <p class="proj-card__summary kh-line-clamp-2">{{ project.summary ?? '暂无简介' }}</p>

    <div class="proj-card__people">
      <div class="proj-card__leader">
        <KhAvatar :item="{ label: leader }" :size="28" />
        <div class="proj-card__leader-info">
          <span class="proj-card__leader-name">{{ leader }}</span>
          <span class="proj-card__leader-role">负责人</span>
        </div>
      </div>
    </div>

    <div class="proj-card__stats">
      <KhStatPill icon="eye" :value="project.viewCount ?? 0" />
      <span class="proj-card__spacer" />
      <KhStatPill icon="download" :value="project.downloadCount ?? 0" />
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