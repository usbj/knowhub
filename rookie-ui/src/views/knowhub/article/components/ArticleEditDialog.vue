<!--
  文件作用：
  文章新增/编辑弹窗（创建与编辑共用），单页编辑文章元信息（标题/等级/可见性/摘要/封面）。
  章节是文章子模块，独立路由页管理（从文章列表"章节"按钮跳转），不在此弹窗内嵌章节管理。
  关键参数：
  - `visible`：弹窗显隐，父层双向绑定。
  - `mode`：'create' | 'edit'，决定标题与按钮文案。
  - `articleId`：编辑态文章主键；创建态传 undefined，提交成功后父层回填。
  关键依赖：
  - 文章信息用 ElForm（title/level/visibility/summary/coverObjectKey）；
  - 等级用字典驱动的 select（article_level：公开/内部/机密）；
  - 可见性用字典驱动的 select（article_visibility：未公开/半公开/全公开，决定章节提交审不审）；
  - 封面用 ArticleCoverUploader（businessType=ARTICLE_COVER，access=PUBLIC）；
  - 权限态来自详情接口回填的 canEdit，控制编辑态是否可改（作者全权不看等级）。
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
  ElOption,
  ElSelect,
} from 'element-plus'
import { createArticleApi, getArticleDetailApi, updateArticleApi } from '@/api/knowhub/article'
import type { ArticleRecord } from '@/types/api/knowhub/article'
import { createDefaultArticleForm } from '../config'
import ArticleCoverUploader from './ArticleCoverUploader.vue'

const props = defineProps<{
  visible: boolean
  mode: 'create' | 'edit'
  /** 编辑态文章主键；创建态传 undefined，提交成功后父层回填 */
  articleId?: number
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  /** 创建/编辑成功后通知父层刷新列表 */
  saved: [articleId: number]
}>()

const formRef = ref()
const formModel = ref<ArticleRecord>(createDefaultArticleForm())
const submitting = ref(false)
const detailLoading = ref(false)
/** 权限态：编辑态从详情接口回填，控制是否可编辑（作者全权 OR 系统编辑权限够） */
const canEdit = ref(false)

const dialogTitle = computed(() => (props.mode === 'create' && !props.articleId ? '新增文章' : '编辑文章'))

const rules = {
  title: [
    { required: true, message: '请输入文章标题', trigger: 'blur' },
    { min: 2, max: 128, message: '标题长度需在 2 到 128 位之间', trigger: 'blur' },
  ],
  level: [{ required: true, message: '请选择文章等级', trigger: 'change' }],
  visibility: [{ required: true, message: '请选择文章可见性', trigger: 'change' }],
}

watch(
  () => [props.visible, props.articleId, props.mode] as const,
  async ([visible, articleId, mode]) => {
    if (!visible) {
      canEdit.value = false
      return
    }
    // 编辑态：拉详情回显 + 回填权限态
    if (mode === 'edit' && articleId) {
      detailLoading.value = true
      try {
        const result = await getArticleDetailApi(articleId)
        formModel.value = { ...createDefaultArticleForm(), ...result.data }
        canEdit.value = Boolean(result.data?.canEdit) || Boolean(result.data?.isAuthor)
      } finally {
        detailLoading.value = false
      }
    } else if (mode === 'create') {
      formModel.value = createDefaultArticleForm()
      canEdit.value = true // 创建态默认可编辑
    }
  },
  { immediate: true },
)

const submitInfo = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  submitting.value = true
  try {
    const payload: ArticleRecord = {
      ...formModel.value,
      title: formModel.value.title.trim(),
      summary: formModel.value.summary?.trim() || '',
      coverObjectKey: formModel.value.coverObjectKey?.trim() || '',
    }
    if (props.mode === 'create' && !props.articleId) {
      const result = await createArticleApi(payload)
      Object.assign(formModel.value, result.data ?? {})
      ElMessage.success('文章创建成功')
      emit('saved', Number(formModel.value.articleId ?? 0))
      // 创建成功后自动关闭弹窗（编辑保存只刷新不关，便于继续操作）
      emit('update:visible', false)
    } else {
      await updateArticleApi({ ...payload, articleId: props.articleId })
      ElMessage.success('文章信息已保存')
      emit('update:visible', false)
    }
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
      <ElFormItem label="文章标题" prop="title">
        <ElInput v-model="formModel.title" placeholder="请输入文章标题" maxlength="128" show-word-limit />
      </ElFormItem>
      <ElFormItem label="文章等级" prop="level">
        <ElSelect v-model="formModel.level" placeholder="请选择等级" style="width: 100%">
          <ElOption label="公开 (L1)" :value="1" />
          <ElOption label="内部 (L2)" :value="2" />
          <ElOption label="机密 (L3)" :value="3" />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="可见性" prop="visibility">
        <ElSelect v-model="formModel.visibility" placeholder="请选择可见性" style="width: 100%">
          <ElOption label="未公开（仅作者写章节，提交免审）" value="PRIVATE" />
          <ElOption label="半公开（有编辑权限者可提交，作者审核）" value="SEMIPUBLIC" />
          <ElOption label="全公开（有编辑权限者可提交，免审）" value="PUBLIC" />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="摘要" prop="summary">
        <ElInput v-model="formModel.summary" type="textarea" :rows="3" placeholder="摘要（可选，文章简介）" maxlength="500" show-word-limit />
      </ElFormItem>
      <ElFormItem label="封面图" prop="coverObjectKey">
        <ArticleCoverUploader
          :model-value="formModel.coverObjectKey ?? ''"
          @update:model-value="formModel.coverObjectKey = $event"
        />
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton @click="close">关闭</ElButton>
      <ElButton type="primary" :loading="submitting" :disabled="!canEdit && mode === 'edit'" @click="submitInfo">
        {{ mode === 'create' && !articleId ? '创建文章' : '保存信息' }}
      </ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
/* v-md-editor 在 ElFormItem 内默认按内容宽度撑开会溢出弹窗，显式约束 100%（预留，文章信息页当前无 markdown 编辑） */
</style>