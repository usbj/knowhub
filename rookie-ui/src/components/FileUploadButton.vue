<!--
  文件作用：
  文件管理页的「上传文件」入口公共组件。
  一个按钮 + 弹窗：选业务类型(file_business_type 字典) / 访问语义(file_access 字典) / 选文件，
  调预签名直传流程，显示上传进度，成功后 emit uploaded 让父页刷新列表。
  关键参数：
  - `permissionKey`：按钮权限键，缺省 knowhub:file:upload，无权限时按钮隐藏。
  - `buttonText`：触发按钮文案，缺省「上传文件」。
  关键事件：
  - `uploaded`：上传成功后回传 objectId/publicUrl，父页据此刷新列表。
  设计约定：
  - 这是**临时测试入口**（用户当前只需先有一个上传按钮验证链路），
    后期文件管理页正式化时可能整块移除。组件自包含、与父页仅通过 uploaded 事件耦合，
    删除时只需删本文件 + 父页引用 + 父页传入的权限键即可，无其他副作用。
  - 主题适配：弹窗、表单、进度条均用 base.css 的 --rookie-* 变量，深浅模式自动跟随。
-->
<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElOption,
  ElProgress,
  ElSelect,
  ElUpload,
  ElMessage,
} from 'element-plus'
import type { UploadFile, UploadRawFile } from 'element-plus'
import { useDict } from '@/composables/useDict'
import { usePermission } from '@/composables/usePermission'
import { presignedUploadFlow } from '@/utils/upload'
import type { PresignedUploadResult } from '@/utils/upload'

const props = withDefaults(
  defineProps<{
    /** 触发按钮权限键，无权限时按钮不渲染；缺省走文件上传权限 */
    permissionKey?: string | string[] | readonly string[]
    /** 触发按钮文案 */
    buttonText?: string
  }>(),
  {
    permissionKey: () => ['knowhub:file:upload'],
    buttonText: '上传文件',
  },
)

const emit = defineEmits<{
  /** 上传成功后回传结果，父页据此刷新列表 */
  uploaded: [result: PresignedUploadResult]
}>()

const { getDictOptions } = useDict()
const { hasPermission } = usePermission()

/** 业务类型下拉选项（来自字典 file_business_type，值用字符串 code） */
const businessTypeOptions = getDictOptions('file_business_type', 'string')
/** 访问语义下拉选项（来自字典 file_access） */
const accessOptions = getDictOptions('file_access', 'string')

/** 弹窗显隐 */
const dialogVisible = ref(false)
/** 上传中（直传+确认期间禁用关闭与重复提交） */
const uploading = ref(false)
/** 上传进度 0–100 */
const uploadPercent = ref(0)
/** 当前选中的文件（ElUpload 临时持有，确认提交时才真正上传） */
const pickedFile = ref<File | null>(null)

/** 表单模型：业务类型 / 访问语义。bizRefId 上传时业务行可能未建，这里不暴露给测试入口。 */
const form = reactive<{
  businessType: string | undefined
  access: string | undefined
}>({
  businessType: undefined,
  access: undefined,
})

const canShowButton = computed(() => hasPermission(props.permissionKey))
/** 未选业务类型或未选文件时禁用「开始上传」 */
const canSubmit = computed(() => Boolean(form.businessType) && Boolean(pickedFile.value) && !uploading.value)

/**
 * 方法效果：
 * 打开上传弹窗并重置表单与临时文件状态。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是切换弹窗显隐并重置内部状态。
 */
const openDialog = () => {
  form.businessType = undefined
  form.access = undefined
  pickedFile.value = null
  uploadPercent.value = 0
  dialogVisible.value = true
}

/**
 * 方法效果：
 * ElUpload 选择文件回调：只暂存文件、不立即上传（手动触发）。
 * 参数：
 * - `uploadFile`：ElUpload 传入的文件对象，取其 raw 即真实 File。
 * 返回值：
 * - false 阻止 ElUpload 自动上传（手动模式）。
 */
const handlePickFile = (uploadFile: UploadFile) => {
  if (uploadFile.raw) {
    pickedFile.value = uploadFile.raw
  }
  return false
}

/**
 * 方法效果：
 * 清除已选文件。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是清空暂存文件与进度。
 */
const clearPickedFile = () => {
  pickedFile.value = null
  uploadPercent.value = 0
}

/**
 * 方法效果：
 * 执行预签名直传三步流程，成功后 emit uploaded 并关闭弹窗。
 * 数据流转：
 * - 用 form.businessType / form.access + pickedFile 调 presignedUploadFlow；
 * - onProgress 回调更新进度条；
 * - 成功 emit uploaded、提示成功、关弹窗；失败提示并保留弹窗便于重试。
 * 参数：
 * - 无，直接读取当前表单与暂存文件。
 * 返回值：
 * - 无返回值；副作用是发起上传并在结束后刷新父页列表。
 */
const startUpload = async () => {
  if (!canSubmit.value || !pickedFile.value || !form.businessType) {
    return
  }

  uploading.value = true
  uploadPercent.value = 0

  try {
    const result = await presignedUploadFlow({
      file: pickedFile.value,
      businessType: form.businessType,
      access: form.access,
      onProgress: (percent) => {
        uploadPercent.value = percent
      },
    })

    ElMessage.success('文件上传成功')
    emit('uploaded', result)
    dialogVisible.value = false
  } catch (error) {
    // http.ts 已统一弹错，这里仅兜底打印便于排查；保留弹窗便于用户重试
    // eslint-disable-next-line no-console
    console.error('文件上传失败', error)
  } finally {
    uploading.value = false
  }
}

/**
 * 方法效果：
 * 选择业务类型时，按枚举默认 access 自动回填访问语义，减少用户操作。
 * 数据流转：BLOG_COVER/BLOG_BODY 默认 PUBLIC，其余默认 PRIVATE；用户可手动改。
 * 参数：
 * - `value`：新选中的业务类型 code。
 * 返回值：
 * - 无返回值；副作用是更新表单 access 字段。
 */
const handleBusinessTypeChange = (value: string | number | boolean) => {
  const code = String(value)
  if (code === 'BLOG_COVER' || code === 'BLOG_BODY') {
    form.access = 'PUBLIC'
  } else {
    form.access = 'PRIVATE'
  }
}

/**
 * 方法效果：
 * 阻止 ElUpload 默认点击行为外的自动上传，并在用户点击上传区时触发文件选择。
 * 参数：
 * - `rawFile`：ElUpload 即将上传的原始文件。
 * 返回值：
 * - false 阻止自动上传。
 */
const beforeUpload = (rawFile: UploadRawFile) => {
  pickedFile.value = rawFile
  return false
}
</script>

<template>
  <ElButton v-if="canShowButton" type="primary" plain @click="openDialog">
    {{ buttonText }}
  </ElButton>

  <!-- 上传弹窗：选业务类型/访问语义 + 选文件 + 进度 -->
  <ElDialog
    v-model="dialogVisible"
    title="上传文件"
    width="520px"
    :close-on-click-modal="false"
    class="file-upload-dialog"
    destroy-on-close
  >
    <ElForm label-width="96px" :disabled="uploading">
      <ElFormItem label="业务类型" required>
        <ElSelect
          v-model="form.businessType"
          placeholder="请选择业务类型"
          style="width: 100%"
          @change="handleBusinessTypeChange"
        >
          <ElOption
            v-for="item in businessTypeOptions"
            :key="String(item.value)"
            :label="item.label"
            :value="item.value"
          />
        </ElSelect>
      </ElFormItem>

      <ElFormItem label="访问语义">
        <ElSelect v-model="form.access" placeholder="请选择访问语义" clearable style="width: 100%">
          <ElOption
            v-for="item in accessOptions"
            :key="String(item.value)"
            :label="item.label"
            :value="item.value"
          />
        </ElSelect>
      </ElFormItem>

      <ElFormItem label="选择文件" required>
        <ElUpload
          :auto-upload="false"
          :show-file-list="false"
          :before-upload="beforeUpload"
          :on-change="handlePickFile"
          drag
          class="file-upload-dialog__picker"
        >
          <div class="file-upload-dialog__picker-text">
            <template v-if="pickedFile">{{ pickedFile.name }}</template>
            <template v-else>点击或拖拽文件到此处</template>
          </div>
        </ElUpload>
        <ElButton
          v-if="pickedFile"
          link
          type="primary"
          class="file-upload-dialog__clear"
          @click="clearPickedFile"
        >
          清除选择
        </ElButton>
      </ElFormItem>

      <ElFormItem v-if="uploading || uploadPercent > 0" label="上传进度">
        <ElProgress :percentage="uploadPercent" :status="uploadPercent >= 100 ? 'success' : undefined" />
      </ElFormItem>
    </ElForm>

    <template #footer>
      <div class="file-upload-dialog__actions">
        <ElButton :disabled="uploading" @click="dialogVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="uploading" :disabled="!canSubmit" @click="startUpload">
          开始上传
        </ElButton>
      </div>
    </template>
  </ElDialog>
</template>

<style scoped>
.file-upload-dialog__picker {
  width: 100%;
}

.file-upload-dialog__picker :deep(.el-upload-dragger) {
  width: 100%;
  border-color: var(--rookie-border);
  background: var(--rookie-surface-muted);
  color: var(--rookie-text-secondary);
  border-radius: var(--rookie-radius-md);
  padding: 18px;
}

.file-upload-dialog__picker :deep(.el-upload-dragger:hover) {
  border-color: var(--rookie-primary);
}

.file-upload-dialog__picker-text {
  color: var(--rookie-text);
  font-size: var(--rookie-font-size-base, 14px);
}

.file-upload-dialog__clear {
  margin-top: 4px;
}

.file-upload-dialog__actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
