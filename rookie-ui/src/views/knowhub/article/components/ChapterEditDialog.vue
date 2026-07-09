<!--
  文件作用：
  章节新增/编辑弹窗（创建与编辑共用），编辑章节名/排序 + 正文 Markdown。
  章节正文改参考博客布局：弹窗内正文位用「编辑正文」按钮 + 摘要预览，
  点击打开全屏双栏编辑器（ChapterContentEditor，左编辑右预览），保存回写 formModel.content。
  关键参数：
  - `visible`：弹窗显隐，父层双向绑定。
  - `mode`：'create' | 'edit'，决定标题与提交后端走 add 还是 edit。
  - `articleId`：所属文章 ID（新建时带上，章节按文章维度归属）。
  - `chapterId`：编辑态章节主键；创建态传 undefined。
  关键依赖：
  - 章节名/排序用 ElInput；
  - 正文用 ChapterContentEditor（照搬博客 BlogContentEditor 范式，全屏双栏）；
  - 权限态来自详情接口回填的 canEdit，控制编辑态是否可改。
  注意：章节提交状态机由后端按文章 visibility + 提交者是否作者决定（submitChapter/publishChapter），
        此弹窗只负责存章节内容，提交发布动作由列表页"提交"按钮调 publishChapterApi。
-->
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
} from 'element-plus'
import ChapterContentEditor from './ChapterContentEditor.vue'
import { getChapterDetailApi, submitChapterApi, updateChapterApi } from '@/api/knowhub/chapter'
import type { ChapterRecord } from '@/types/api/knowhub/chapter'
import { createDefaultChapterForm } from '../chapters/config'

const props = defineProps<{
  visible: boolean
  mode: 'create' | 'edit'
  articleId: number
  chapterId?: number
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  /** 创建/编辑成功后通知父层刷新列表 */
  saved: []
}>()

const formRef = ref()
const formModel = ref<ChapterRecord>(createDefaultChapterForm())
const submitting = ref(false)
const detailLoading = ref(false)
/** 权限态：编辑态从详情接口回填，控制是否可编辑（章节作者 OR 文章作者 OR 系统编辑权限够） */
const canEdit = ref(false)
/** 正文全屏编辑器显隐 */
const contentEditorVisible = ref(false)

const dialogTitle = computed(() => (props.mode === 'create' ? '新增章节' : '编辑章节'))

const rules = {
  chapterName: [
    { required: true, message: '请输入章节名', trigger: 'blur' },
    { min: 2, max: 128, message: '章节名长度需在 2 到 128 位之间', trigger: 'blur' },
  ],
}

watch(
  () => [props.visible, props.chapterId, props.mode] as const,
  async ([visible, chapterId, mode]) => {
    if (!visible) {
      canEdit.value = false
      return
    }
    // 编辑态：拉详情回显（带正文 content + 权限态 canEdit）
    if (mode === 'edit' && chapterId) {
      detailLoading.value = true
      try {
        const result = await getChapterDetailApi(chapterId)
        formModel.value = { ...createDefaultChapterForm(), ...result.data }
        canEdit.value = Boolean(result.data?.canEdit)
      } finally {
        detailLoading.value = false
      }
    } else if (mode === 'create') {
      formModel.value = { ...createDefaultChapterForm(), articleId: props.articleId }
      canEdit.value = true
    }
  },
  { immediate: true },
)

/** 正文预览摘要：取正文前 60 字符（去 markdown 符号粗略清洗）作为「编辑正文」按钮旁的提示 */
const contentPreview = computed(() => {
  const raw = formModel.value.content ?? ''
  const plain = raw.replace(/[#*`>\-\[\]()!]/g, '').replace(/\s+/g, ' ').trim()
  if (!plain) {
    return ''
  }
  return plain.length > 60 ? `${plain.slice(0, 60)}…` : plain
})

const submitInfo = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  // 正文必填校验（参考博客范式）
  if (!formModel.value.content?.trim()) {
    ElMessage.warning('请先编辑章节正文')
    contentEditorVisible.value = true
    return
  }
  submitting.value = true
  try {
    const payload: ChapterRecord = {
      ...formModel.value,
      chapterName: formModel.value.chapterName.trim(),
      content: formModel.value.content.trim(),
    }
    if (props.mode === 'create' && !props.chapterId) {
      // 新建：调 submitChapterApi（后端按文章 visibility + 提交者是否作者决定状态机分支）
      await submitChapterApi({ ...payload, articleId: props.articleId })
      ElMessage.success('章节已提交')
    } else {
      // 编辑：仅保存章节内容，状态机不变（后端 PUBLISHED 禁编校验）
      await updateChapterApi({ ...payload, chapterId: props.chapterId })
      ElMessage.success('章节已保存')
    }
    emit('saved')
    emit('update:visible', false)
  } finally {
    submitting.value = false
  }
}

const close = () => {
  emit('update:visible', false)
}
</script>

<template>
  <ElDialog
    :model-value="visible"
    :title="dialogTitle"
    width="720px"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
  >
    <ElForm v-loading="detailLoading" ref="formRef" :model="formModel" :rules="rules" label-width="90px" :disabled="!canEdit && mode === 'edit'">
      <ElFormItem label="章节名" prop="chapterName">
        <ElInput v-model="formModel.chapterName" placeholder="请输入章节名（文档页面标题）" maxlength="128" show-word-limit />
      </ElFormItem>
      <ElFormItem label="排序" prop="sortOrder">
        <ElInput v-model="formModel.sortOrder" type="number" placeholder="同级排序（数字，asc，缺省 0）" />
      </ElFormItem>
      <ElFormItem label="正文" required>
        <div class="chapter-edit__content-entry">
          <ElButton type="primary" plain @click="contentEditorVisible = true">编辑正文</ElButton>
          <span v-if="contentPreview" class="chapter-edit__content-preview">{{ contentPreview }}</span>
          <span v-else class="chapter-edit__content-empty">未填写正文</span>
        </div>
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton @click="close">取消</ElButton>
      <ElButton type="primary" :loading="submitting" :disabled="!canEdit && mode === 'edit'" @click="submitInfo">
        {{ mode === 'create' ? '提交章节' : '保存章节' }}
      </ElButton>
    </template>
  </ElDialog>

  <!-- 正文全屏编辑器（CSDN 风格双栏 + 图片预签名直传，照搬博客范式） -->
  <ChapterContentEditor
    :visible="contentEditorVisible"
    :model-value="formModel.content ?? ''"
    @update:visible="contentEditorVisible = $event"
    @update:model-value="formModel.content = $event"
  />
</template>

<style scoped>
.chapter-edit__content-entry {
  display: flex;
  align-items: center;
  gap: 12px;
}

.chapter-edit__content-preview {
  color: var(--rookie-text-secondary);
  font-size: var(--rookie-font-size-sm);
}

.chapter-edit__content-empty {
  color: var(--rookie-text-tertiary);
  font-size: var(--rookie-font-size-sm);
}
</style>