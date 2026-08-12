<!--
  项目创作 /project/create（CSDN/掘进 风格 editorial 全屏创作页）
  ------------------------------------------------------------------
  设计：照搬博客/文章创作页范式 —— 顶部工具条 + 正文编辑区为主 + 元信息折叠面板（类型/等级/摘要）；
  项目特色：无标签选择器（项目无标签体系），有 type 选择（COMPETITION/PRACTICE/OPS）+ level 等级选择器
  （按 getMyProjectLevelApi 禁用不可选等级）+ summary 前言 + description 正文（KhMarkdownEditor，正文插图走
  预签名直传 businessType=PROJECT_DOC + PRIVATE，但本页编辑器默认 BLOG_BODY 通用，项目正文配图复用同口径）。
  正文配图复用 BLOG_BODY 通用口径，项目正文走 PROJECT_DOC）。
  状态机：
  - 新建：存草稿调 draftProjectApi 成功后跳 /profile?tab=project&t=<ts> 触发"我的项目"重拉，从列表进编辑拿到 projectId。
  - 编辑：路由 ?id=xxx，onMounted 回填 getProjectForEditApi。已发布态先撤回才能编辑。
  成员/文件管理已迁至项目详情页（/project/:id），创作页退回纯项目信息编辑，与博客/文章创作页同构。
-->
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElSelect, ElOption } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import KhIcon from '@/components/common/KhIcon.vue'
import KhMarkdownEditor from '@/components/common/KhMarkdownEditor.vue'
import {
  draftProjectApi,
  editProjectApi,
  getMyProjectLevelApi,
  getProjectForEditApi,
  publishProjectAuthoringApi,
  revokeProjectAuthoringApi,
} from '@/api/knowhub/project-authoring'
import type { ProjectAuthoringPayload } from '@/types/api/knowhub/project-authoring'

const route = useRoute()
const router = useRouter()

/** 表单模型（与 ProjectAuthoringPayload 对齐，只带创作相关字段） */
const form = ref<{
  projectId?: number
  title: string
  type: string
  level: number
  summary: string
  description: string
}>({
  title: '',
  type: 'PRACTICE',
  level: 1,
  summary: '',
  description: '',
})

/** 当前项目状态（编辑模式回填，驱动按钮态） */
const projectStatus = ref<string>('')
const isEdit = computed(() => route.query.id !== undefined)
const editId = computed(() => (route.query.id ? Number(route.query.id) : undefined))

/** 当前用户 view 等级（0/1/2/3），等级选项据此禁用 */
const myLevel = ref<number>(0)

/** 元信息折叠态（默认展开——项目类型/等级是必填项，不像博客 content-first 可后补） */
const metaExpanded = ref(true)

const saving = ref(false)
const publishing = ref(false)

/** 项目类型选项（前台内联，与项目展示页 typeLabel 同口径） */
const typeOptions = [
  { value: 'COMPETITION', label: '比赛项目', sub: '竞赛类项目' },
  { value: 'PRACTICE', label: '练习项目', sub: '日常练习/实训' },
  { value: 'OPS', label: '运维项目', sub: '运维实操/工具' },
]
const typeLabel = computed(
  () => typeOptions.find((t) => t.value === form.value.type)?.label ?? form.value.type,
)

/** 等级选项：禁用态按用户 view 等级算（与博客/文章创作页同构） */
const levelOptions = computed(() => [
  { value: 1, label: '公开', sub: '所有人可见', disabled: false },
  { value: 2, label: '内部', sub: '登录成员可见', disabled: myLevel.value < 2 },
  { value: 3, label: '机密', sub: '高权限可见', disabled: myLevel.value < 3 },
])

const isPublished = computed(() => projectStatus.value === 'PUBLISHED')
const canEditNow = computed(() =>
  ['DRAFT', 'REJECTED', 'REVOKED', ''].includes(projectStatus.value),
)

/**
 * 从 description（markdown 正文）剥出纯文本并取前 100 字符，用作摘要兜底。
 * 轻量 strip：去图片/链接标签、标题井号、强调符、引用/列表前缀、HTML/代码块，压空白。
 * 后端 summary 字段长度按 200 存（与摘要框 maxlength 同口径），这里取 100 字符留余量。
 */
const buildFallbackSummary = (): string | undefined => {
  const desc = form.value.description?.trim()
  if (!desc) return undefined
  const plain = desc
    .replace(/```[\s\S]*?```/g, ' ')        // 代码块
    .replace(/`[^`]*`/g, ' ')               // 行内代码
    .replace(/!\[[^\]]*\]\([^)]*\)/g, ' ')  // 图片 ![alt](url)
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')// 链接 [text](url) → text
    .replace(/<[^>]+>/g, ' ')              // HTML 标签
    .replace(/^#{1,6}\s+/gm, '')            // 标题井号
    .replace(/^\s{0,3}>\s?/gm, '')          // 引用 >
    .replace(/^\s*[-*+]\s+/gm, '')          // 无序列表 - * +
    .replace(/^\s*\d+\.\s+/gm, '')          // 有序列表 1.
    .replace(/[*_~]{1,3}/g, '')             // 强调 * ** _ __ ~
    .replace(/\n+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
  if (!plain) return undefined
  return plain.slice(0, 100)
}

const buildPayload = (): ProjectAuthoringPayload => {
  const summary = form.value.summary.trim() || buildFallbackSummary()
  return {
    projectId: form.value.projectId,
    title: form.value.title.trim(),
    type: form.value.type,
    level: form.value.level,
    summary,
    description: form.value.description || undefined,
  }
}

const validate = (): boolean => {
  if (!form.value.title.trim()) {
    ElMessage.warning('请输入项目标题')
    return false
  }
  if (!form.value.type) {
    ElMessage.warning('请选择项目类型')
    return false
  }
  return true
}

/**
 * 存草稿：新建调 draftApi 成功后跳回 profile"我的项目"列表（带 tab=project + 时间戳触发重拉），
 * 从列表点进编辑即拿到 projectId——不需要 draft 返回 id。
 * 编辑态调 editApi，保存后留在当前页提示。
 */
const handleSaveDraft = async () => {
  if (!validate()) return
  saving.value = true
  try {
    const payload = buildPayload()
    if (form.value.projectId) {
      await editProjectApi(payload)
      ElMessage.success('草稿已保存')
    } else {
      await draftProjectApi(payload)
      ElMessage.success('草稿已保存，即将返回我的项目')
      router.push({ path: '/profile', query: { tab: 'project', t: String(Date.now()) } })
    }
  } finally {
    saving.value = false
  }
}

/** 发布：仅在编辑模式（已有 projectId）可用，先 edit 再 publish */
const handlePublish = async () => {
  if (!validate()) return
  if (!form.value.projectId) {
    ElMessage.warning('请先保存草稿，再从我的项目列表进入发布')
    return
  }
  publishing.value = true
  try {
    if (canEditNow.value) {
      await editProjectApi(buildPayload())
    }
    await publishProjectAuthoringApi(form.value.projectId)
    ElMessage.success('发布成功')
    projectStatus.value = 'PENDING_REVIEW'
  } finally {
    publishing.value = false
  }
}

/** 撤回：仅 PUBLISHED 可撤回 */
const handleRevoke = async () => {
  const projectId = form.value.projectId
  if (!projectId) return
  try {
    await ElMessageBox.confirm('撤回后将转为已撤回态，可继续编辑后重新发布', '确认撤回', {
      type: 'warning',
    })
  } catch {
    return
  }
  await revokeProjectAuthoringApi(projectId)
  ElMessage.success('已撤回，可继续编辑')
  projectStatus.value = 'REVOKED'
}

const statusText = computed(() => {
  const m: Record<string, string> = {
    DRAFT: '草稿',
    PUBLISHED: '已发布',
    REVOKED: '已撤回',
    PENDING_REVIEW: '待审核',
    REJECTED: '已驳回',
    ARCHIVED: '已归档',
  }
  return projectStatus.value ? (m[projectStatus.value] ?? projectStatus.value) : ''
})

const fetchForEdit = async (projectId: number) => {
  const res = await getProjectForEditApi(projectId)
  const p = res.data
  if (!p) {
    ElMessage.error('项目不存在或无权查看')
    router.back()
    return
  }
  form.value.projectId = p.projectId
  form.value.title = p.title
  form.value.type = p.type
  form.value.level = p.level
  form.value.summary = p.summary ?? ''
  form.value.description = p.description ?? ''
  projectStatus.value = p.status ?? ''
  if (isPublished.value) {
    ElMessage.info('该项目已发布，编辑需先撤回')
  }
}

onMounted(async () => {
  const levelRes = await getMyProjectLevelApi()
  myLevel.value = levelRes.data ?? 0
  const id = editId.value
  if (id) {
    await fetchForEdit(id)
  }
})
</script>

<template>
  <div class="pc">
    <!-- 顶部工具条（顶栏高度对齐 AppHeader，居中限宽） -->
    <header class="pc__bar">
      <div class="pc__bar-inner">
        <div class="pc__bar-left">
          <button class="pc__back" type="button" @click="router.back()">
            <el-icon><ArrowLeft /></el-icon> 返回
          </button>
          <input
            v-model="form.title"
            class="pc__title-input"
            placeholder="输入项目标题…"
            maxlength="100"
          />
          <span v-if="statusText" class="pc__status">{{ statusText }}</span>
        </div>
        <div class="pc__bar-right">
          <button
            class="pc__btn pc__btn--ghost"
            type="button"
            :disabled="saving || publishing"
            @click="handleSaveDraft"
          >
            {{ saving ? '保存中…' : '存草稿' }}
          </button>
          <button
            v-if="isPublished"
            class="pc__btn pc__btn--warn"
            type="button"
            :disabled="saving || publishing"
            @click="handleRevoke"
          >
            撤回
          </button>
          <button
            v-else
            class="pc__btn pc__btn--primary"
            type="button"
            :disabled="saving || publishing || isPublished"
            @click="handlePublish"
          >
            {{ publishing ? '发布中…' : '发布' }}
          </button>
        </div>
      </div>
    </header>

    <!-- 主体：居中限宽容器 -->
    <div class="pc__wrap">
      <!-- 正文编辑区（v-md-editor 封装到通用组件 KhMarkdownEditor） -->
      <section class="pc__editor">
        <KhMarkdownEditor v-model="form.description" height="100%" placeholder="撰写项目介绍正文…" />
      </section>

      <!-- 元信息折叠面板（正文下方） -->
      <section class="pc__meta">
        <button
          class="pc__meta-head"
          type="button"
          @click="metaExpanded = !metaExpanded"
        >
          <KhIcon name="tag" :size="14" />
          <span>项目信息</span>
          <span class="pc__meta-summary">
            {{ typeLabel }} · L{{ form.level }}
          </span>
          <span class="pc__meta-caret" :class="{ 'is-open': metaExpanded }">▾</span>
        </button>

        <div v-show="metaExpanded" class="pc__meta-body">
          <!-- 项目类型：el-select -->
          <div class="pc__field">
            <label class="pc__label">项目类型</label>
            <ElSelect v-model="form.type" placeholder="请选择项目类型" style="width: 100%; max-width: 320px">
              <ElOption
                v-for="opt in typeOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              >
                <span>{{ opt.label }}</span>
                <span class="pc__opt-sub">{{ opt.sub }}</span>
              </ElOption>
            </ElSelect>
          </div>

          <!-- 等级：按钮组 -->
          <div class="pc__field">
            <label class="pc__label">可见等级</label>
            <div class="pc__level-group">
              <button
                v-for="opt in levelOptions"
                :key="opt.value"
                class="pc__level-btn"
                :class="{ 'is-active': form.level === opt.value, 'is-disabled': opt.disabled }"
                type="button"
                :disabled="opt.disabled"
                :title="opt.disabled ? '当前等级不可创建此级别' : opt.sub"
                @click="form.level = opt.value"
              >
                <span class="pc__level-name">L{{ opt.value }} {{ opt.label }}</span>
                <span class="pc__level-sub">{{ opt.sub }}</span>
              </button>
            </div>
          </div>

          <!-- 摘要 -->
          <div class="pc__field">
            <label class="pc__label">摘要<span class="pc__label-hint">一句话概括项目内容（选填）</span></label>
            <textarea
              v-model="form.summary"
              class="pc__summary"
              placeholder="项目简介、目标、成果等"
              rows="3"
              maxlength="200"
            />
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.pc {
  display: flex;
  flex-direction: column;
  min-height: calc(100vh - var(--kh-header-height));
  background: var(--kh-bg);
}

/* —— 顶部工具条 —— */
.pc__bar {
  position: sticky;
  top: var(--kh-header-height);
  z-index: 10;
  background: color-mix(in srgb, var(--kh-surface) 92%, transparent);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid var(--kh-border-soft);
  flex: none;
}
.pc__bar-inner {
  max-width: 880px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kh-space-4);
  padding: var(--kh-space-3) var(--kh-space-5);
}
.pc__bar-left {
  display: flex;
  align-items: center;
  gap: var(--kh-space-3);
  flex: 1;
  min-width: 0;
}
.pc__back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius-sm);
  background: var(--kh-surface);
  color: var(--kh-text-secondary);
  font-size: var(--kh-font-size-sm);
  cursor: pointer;
  flex: none;
  transition: all var(--kh-transition-fast);
}
.pc__back:hover {
  border-color: var(--kh-primary-border);
  color: var(--kh-primary);
}
.pc__title-input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  font-size: var(--kh-font-size-xl);
  font-weight: 700;
  color: var(--kh-text);
}
.pc__title-input::placeholder {
  color: var(--kh-text-tertiary);
  font-weight: 600;
}
.pc__status {
  font-size: 12px;
  color: var(--kh-text-tertiary);
  padding: 2px 10px;
  border-radius: var(--kh-radius-pill);
  background: var(--kh-bg-soft);
  flex: none;
  font-family: var(--kh-font-mono);
}
.pc__bar-right {
  display: flex;
  align-items: center;
  gap: var(--kh-space-2);
  flex: none;
}
.pc__btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 38px;
  padding: 0 var(--kh-space-5);
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius-pill);
  background: var(--kh-surface);
  color: var(--kh-text-secondary);
  font-weight: 600;
  font-size: var(--kh-font-size-sm);
  cursor: pointer;
  transition: all var(--kh-transition-fast);
}
.pc__btn:hover:not(:disabled) {
  border-color: var(--kh-primary-border);
  color: var(--kh-primary);
}
.pc__btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.pc__btn--primary {
  background: var(--kh-primary);
  border-color: var(--kh-primary);
  color: #fff;
}
.pc__btn--primary:hover:not(:disabled) {
  background: var(--kh-primary-strong);
  color: #fff;
}
.pc__btn--warn {
  border-color: var(--kh-warm);
  color: var(--kh-warm);
}
.pc__btn--warn:hover:not(:disabled) {
  background: var(--kh-warm-soft);
}

/* —— 主体限宽容器 —— */
.pc__wrap {
  max-width: 880px;
  width: 100%;
  margin: 0 auto;
  padding: var(--kh-space-6) var(--kh-space-5) var(--kh-space-12);
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-6);
}

/* —— 正文编辑器 —— */
.pc__editor {
  position: relative;
  height: calc(100vh - var(--kh-header-height) - 220px);
  min-height: 420px;
}

/* —— 元信息折叠面板 —— */
.pc__meta {
  background: var(--kh-surface);
  border: 1px solid var(--kh-border-soft);
  border-radius: var(--kh-radius-lg);
  overflow: hidden;
}
.pc__meta-head {
  width: 100%;
  display: flex;
  align-items: center;
  gap: var(--kh-space-2);
  padding: var(--kh-space-4) var(--kh-space-5);
  border: none;
  background: transparent;
  color: var(--kh-text);
  font-size: var(--kh-font-size-md);
  font-weight: 600;
  cursor: pointer;
  transition: background var(--kh-transition-fast);
}
.pc__meta-head:hover {
  background: var(--kh-surface-muted);
}
.pc__meta-head > :first-child {
  color: var(--kh-primary);
}
.pc__meta-summary {
  margin-left: auto;
  font-size: 12px;
  font-weight: 500;
  color: var(--kh-text-tertiary);
  font-family: var(--kh-font-mono);
}
.pc__meta-caret {
  color: var(--kh-text-tertiary);
  font-size: 12px;
  transition: transform var(--kh-transition-fast);
}
.pc__meta-caret.is-open {
  transform: rotate(180deg);
}
.pc__meta-body {
  padding: var(--kh-space-4) var(--kh-space-5) var(--kh-space-6);
  border-top: 1px solid var(--kh-border-soft);
  display: grid;
  gap: var(--kh-space-5);
}
.pc__field {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-2);
}
.pc__label {
  font-size: var(--kh-font-size-sm);
  font-weight: 600;
  color: var(--kh-text);
  display: flex;
  align-items: center;
  gap: 6px;
}
.pc__label-hint {
  font-size: 12px;
  font-weight: 400;
  color: var(--kh-text-tertiary);
}
.pc__opt-sub {
  color: var(--kh-text-tertiary);
  font-size: 12px;
  margin-left: 6px;
}

/* —— 等级按钮组 —— */
.pc__level-group {
  display: flex;
  gap: 10px;
}
.pc__level-btn {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  padding: 10px 14px;
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius);
  background: var(--kh-surface);
  cursor: pointer;
  transition: all var(--kh-transition-fast);
  text-align: left;
}
.pc__level-btn:hover:not(.is-disabled) {
  border-color: var(--kh-primary-border);
  background: var(--kh-primary-soft);
}
.pc__level-btn.is-active {
  border-color: var(--kh-primary);
  background: var(--kh-primary-soft);
  box-shadow: var(--kh-shadow-xs);
}
.pc__level-btn.is-active .pc__level-name {
  color: var(--kh-primary-strong);
}
.pc__level-btn.is-disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.pc__level-name {
  font-size: var(--kh-font-size-sm);
  font-weight: 600;
  color: var(--kh-text);
}
.pc__level-sub {
  font-size: 11px;
  color: var(--kh-text-tertiary);
}

/* —— 摘要 —— */
.pc__summary {
  width: 100%;
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius);
  background: var(--kh-surface-muted);
  padding: 10px 14px;
  font-size: var(--kh-font-size-sm);
  color: var(--kh-text);
  resize: vertical;
  outline: none;
  font-family: var(--kh-font-body);
  line-height: 1.6;
  transition: border-color var(--kh-transition-fast);
}
.pc__summary:focus {
  border-color: var(--kh-primary-border);
  background: var(--kh-surface);
}

@media (max-width: 768px) {
  .pc__wrap {
    padding: var(--kh-space-4) var(--kh-space-3) var(--kh-space-10);
  }
  .pc__level-group {
    flex-direction: column;
  }
}
</style>
