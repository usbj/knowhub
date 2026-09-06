<!--
  文件作用：
  OSS 数据迁移对话框（扩展点2），承接文件管理页「数据迁移」按钮。
  关键参数：
  - `visible`：弹窗显隐。
  关键交互：
  - 表单填源 OSS（endpoint/region/accessKey/secretKey/bucket/pathStyleAccess）+ 目标 OSS（同）。
  - 提交调 startMigrationApi 返 taskId，进入进度轮询（setInterval 3 秒调 getMigrationProgressApi）。
  - 进度条展示 done/total + 状态（PENDING/RUNNING/SUCCESS/FAILED/CANCELED），完成/失败停轮询。
  - 凭证（accessKey/secretKey）仅本会话内存用，提交后不持久化、不回显；关闭弹窗清空表单。
  - 主题适配：--rookie-* 变量，深浅模式自动跟随。
-->
<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElProgress,
  ElRadio,
  ElRadioGroup,
  ElSwitch,
} from 'element-plus'
import { startMigrationApi, getMigrationProgressApi } from '@/api/knowhub/file'
import type { MigrationApplyPayload, MigrationProgressRecord } from '@/types/api/knowhub/file'

const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

interface OssConnFields {
  endpoint: string
  region: string
  accessKey: string
  secretKey: string
  bucket: string
  pathStyleAccess: boolean
}

const createDefaultConn = (): OssConnFields => ({
  endpoint: '',
  region: '',
  accessKey: '',
  secretKey: '',
  bucket: '',
  pathStyleAccess: true,
})

/** 源端类型：'OSS'（源 OSS→目标 OSS）/ 'LOCAL'（本地→目标 OSS，源是后端本地磁盘，无需源 OSS 表单） */
const sourceType = ref<'OSS' | 'LOCAL'>('OSS')
const sourceConn = reactive<OssConnFields>(createDefaultConn())
const targetConn = reactive<OssConnFields>(createDefaultConn())
const submitting = ref(false)
/** 当前迁移任务 ID（提交后生成，用于轮询进度） */
const taskId = ref<number | null>(null)
/** 当前迁移进度（轮询更新） */
const progress = ref<MigrationProgressRecord | null>(null)
/** 轮询定时器句柄 */
let pollTimer: ReturnType<typeof setInterval> | null = null

watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      // 打开弹窗：重置表单 + 进度态（凭证不回显，每次重新填）
      sourceType.value = 'OSS'
      Object.assign(sourceConn, createDefaultConn())
      Object.assign(targetConn, createDefaultConn())
      taskId.value = null
      progress.value = null
    } else {
      // 关闭弹窗：停轮询
      stopPolling()
    }
  },
)

const connValid = (c: OssConnFields): boolean =>
  c.endpoint.trim() !== '' &&
  c.accessKey.trim() !== '' &&
  c.secretKey.trim() !== '' &&
  c.bucket.trim() !== ''

// LOCAL 源不需要源 OSS 表单（源是本地磁盘），只校验目标 OSS；OSS 源需源 + 目标都校验
const canSubmit = computed(() => {
  if (submitting.value || taskId.value !== null) return false
  if (sourceType.value === 'LOCAL') return connValid(targetConn)
  return connValid(sourceConn) && connValid(targetConn)
})

/** 进度百分比（done/total），total=0 时 0 */
const progressPercent = computed(() => {
  const p = progress.value
  if (!p || !p.totalCount) return 0
  return Math.min(100, Math.round((p.doneCount / p.totalCount) * 100))
})

/** 状态展示文案 */
const statusText = computed(() => {
  const s = progress.value?.status
  switch (s) {
    case 'PENDING': return '等待执行'
    case 'RUNNING': return '迁移中'
    case 'SUCCESS': return '已完成'
    case 'FAILED': return '失败'
    case 'CANCELED': return '已取消'
    default: return '未开始'
  }
})

/** 终态判断（停轮询用） */
const isTerminal = (status?: string) => status === 'SUCCESS' || status === 'FAILED' || status === 'CANCELED'

const buildPayload = (): MigrationApplyPayload => {
  const base: MigrationApplyPayload = {
    sourceType: sourceType.value,
    targetEndpoint: targetConn.endpoint.trim(),
    targetRegion: targetConn.region.trim() || undefined,
    targetAccessKey: targetConn.accessKey.trim(),
    targetSecretKey: targetConn.secretKey.trim(),
    targetBucket: targetConn.bucket.trim(),
    targetPathStyleAccess: targetConn.pathStyleAccess,
  }
  // LOCAL 源不传 source* 字段（后端按 sourceType=LOCAL 读本地磁盘，忽略 source*）
  if (sourceType.value === 'OSS') {
    base.sourceEndpoint = sourceConn.endpoint.trim()
    base.sourceRegion = sourceConn.region.trim() || undefined
    base.sourceAccessKey = sourceConn.accessKey.trim()
    base.sourceSecretKey = sourceConn.secretKey.trim()
    base.sourceBucket = sourceConn.bucket.trim()
    base.sourcePathStyleAccess = sourceConn.pathStyleAccess
  }
  return base
}

const startPolling = (id: number) => {
  stopPolling()
  pollTimer = setInterval(async () => {
    try {
      const result = await getMigrationProgressApi(id)
      const p = result.data
      if (p) {
        progress.value = p
        if (isTerminal(p.status)) {
          stopPolling()
          if (p.status === 'SUCCESS') {
            ElMessage.success(`迁移完成：成功 ${p.doneCount} / 失败 ${p.failedCount} / 总计 ${p.totalCount}`)
          } else if (p.status === 'FAILED') {
            ElMessage.error(`迁移失败：${p.errorMessage ?? '未知错误'}`)
          }
        }
      }
    } catch {
      // 单次轮询失败不中断，下次重试
    }
  }, 3000)
}

const stopPolling = () => {
  if (pollTimer !== null) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

const handleSubmit = async () => {
  if (!canSubmit.value) return
  submitting.value = true
  try {
    const result = await startMigrationApi(buildPayload())
    const id = result.data
    if (id == null) {
      ElMessage.error('启动迁移失败：未返回任务 ID')
      return
    }
    taskId.value = id
    progress.value = {
      taskId: id,
      status: 'PENDING',
      totalCount: 0,
      doneCount: 0,
      failedCount: 0,
    }
    ElMessage.success('迁移任务已启动，正在执行...')
    startPolling(id)
  } catch {
    ElMessage.error('启动迁移失败')
  } finally {
    submitting.value = false
  }
}

const handleClose = () => {
  stopPolling()
  emit('update:visible', false)
}

onBeforeUnmount(() => {
  stopPolling()
})
</script>

<template>
  <ElDialog
    :model-value="visible"
    title="OSS 数据迁移"
    width="680px"
    :close-on-click-modal="false"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
  >
    <div class="migration-dialog__hint">
      数据迁移支持两种方向：OSS→OSS（换 OSS 时按源/目标地址完全迁移）、本地→OSS（把后端本地磁盘文件批量迁移到目标 OSS）。
      目录结构保持一致（源 objectKey 原样作目标 objectKey）。
      凭证（accessKey/secretKey）仅本会话内存用、提交后不落库，请从安全来源填写。
    </div>

    <ElForm label-width="120px" class="migration-dialog__form">
      <ElFormItem label="迁移方向" required>
        <ElRadioGroup v-model="sourceType">
          <ElRadio value="OSS">OSS → OSS</ElRadio>
          <ElRadio value="LOCAL">本地 → OSS</ElRadio>
        </ElRadioGroup>
      </ElFormItem>

      <!-- 源端：OSS→OSS 时填源 OSS 连接参数；本地→OSS 时源是后端本地磁盘，无需填写 -->
      <template v-if="sourceType === 'OSS'">
        <div class="migration-dialog__section-title">源 OSS</div>
        <ElFormItem label="Endpoint" required>
          <ElInput v-model="sourceConn.endpoint" placeholder="如 http://192.168.1.10:9000" />
        </ElFormItem>
        <ElFormItem label="AccessKey" required>
          <ElInput v-model="sourceConn.accessKey" placeholder="源 OSS 访问键" show-password />
        </ElFormItem>
        <ElFormItem label="SecretKey" required>
          <ElInput v-model="sourceConn.secretKey" placeholder="源 OSS 密钥" show-password />
        </ElFormItem>
        <ElFormItem label="Bucket" required>
          <ElInput v-model="sourceConn.bucket" placeholder="源 OSS 桶名" />
        </ElFormItem>
        <ElFormItem label="Region">
          <ElInput v-model="sourceConn.region" placeholder="可空，RustFS 不校验" />
        </ElFormItem>
        <ElFormItem label="Path-Style">
          <ElSwitch v-model="sourceConn.pathStyleAccess" />
          <span class="migration-dialog__inline-hint">RustFS 默认开启</span>
        </ElFormItem>
      </template>
      <template v-else>
        <div class="migration-dialog__section-title">源：本地磁盘</div>
        <div class="migration-dialog__local-hint">
          源为后端本地磁盘（storage.local.base-path 配置的根目录），无需填写连接参数。
          迁移将遍历该目录下所有文件，按相对路径（objectKey）原样写入目标 OSS，目录结构保持一致。
        </div>
      </template>

      <div class="migration-dialog__section-title">目标 OSS</div>
      <ElFormItem label="Endpoint" required>
        <ElInput v-model="targetConn.endpoint" placeholder="如 http://192.168.1.20:9000" />
      </ElFormItem>
      <ElFormItem label="Region">
        <ElInput v-model="targetConn.region" placeholder="可空，RustFS 不校验" />
      </ElFormItem>
      <ElFormItem label="AccessKey" required>
        <ElInput v-model="targetConn.accessKey" placeholder="目标 OSS 访问键" show-password />
      </ElFormItem>
      <ElFormItem label="SecretKey" required>
        <ElInput v-model="targetConn.secretKey" placeholder="目标 OSS 密钥" show-password />
      </ElFormItem>
      <ElFormItem label="Bucket" required>
        <ElInput v-model="targetConn.bucket" placeholder="目标 OSS 桶名" />
      </ElFormItem>
      <ElFormItem label="Path-Style">
        <ElSwitch v-model="targetConn.pathStyleAccess" />
        <span class="migration-dialog__inline-hint">RustFS 默认开启</span>
      </ElFormItem>
    </ElForm>

    <!-- 进度区（提交后展示） -->
    <div v-if="taskId !== null" class="migration-dialog__progress">
      <div class="migration-dialog__progress-head">
        <span>任务 #{{ taskId }}</span>
        <span class="migration-dialog__status">{{ statusText }}</span>
      </div>
      <ElProgress :percentage="progressPercent" :status="progress?.status === 'FAILED' ? 'exception' : progress?.status === 'SUCCESS' ? 'success' : undefined" />
      <div class="migration-dialog__progress-detail">
        <span>总计 {{ progress?.totalCount ?? 0 }}</span>
        <span>成功 {{ progress?.doneCount ?? 0 }}</span>
        <span>失败 {{ progress?.failedCount ?? 0 }}</span>
      </div>
      <div v-if="progress?.errorMessage" class="migration-dialog__error">
        {{ progress.errorMessage }}
      </div>
    </div>

    <template #footer>
      <div class="migration-dialog__actions">
        <ElButton @click="handleClose">关闭</ElButton>
        <ElButton
          v-if="taskId === null"
          type="primary"
          :loading="submitting"
          :disabled="!canSubmit"
          @click="handleSubmit"
        >
          启动迁移
        </ElButton>
      </div>
    </template>
  </ElDialog>
</template>

<style scoped>
.migration-dialog__hint {
  margin-bottom: 16px;
  padding: 10px 14px;
  border: 1px solid var(--rookie-border);
  border-radius: var(--rookie-radius-md);
  background: var(--rookie-surface-weak);
  font-size: var(--rookie-font-size-sm);
  color: var(--rookie-text-secondary);
  line-height: 1.6;
}

.migration-dialog__section-title {
  margin: 8px 0 12px;
  padding-left: 8px;
  border-left: 3px solid var(--rookie-primary);
  font-size: var(--rookie-font-size-base);
  font-weight: 600;
  color: var(--rookie-text-primary);
}

.migration-dialog__form {
  margin-top: 4px;
}

.migration-dialog__inline-hint {
  margin-left: 8px;
  color: var(--rookie-text-tertiary);
  font-size: var(--rookie-font-size-sm);
}

.migration-dialog__local-hint {
  margin: 4px 0 12px;
  padding: 10px 14px;
  border: 1px dashed var(--rookie-border);
  border-radius: var(--rookie-radius-md);
  background: var(--rookie-surface-weak);
  font-size: var(--rookie-font-size-sm);
  color: var(--rookie-text-secondary);
  line-height: 1.6;
}

.migration-dialog__progress {
  margin-top: 20px;
  padding: 14px 16px;
  border: 1px solid var(--rookie-border);
  border-radius: var(--rookie-radius-md);
  background: var(--rookie-surface-weak);
}

.migration-dialog__progress-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  font-size: var(--rookie-font-size-sm);
  color: var(--rookie-text-secondary);
}

.migration-dialog__status {
  font-weight: 600;
  color: var(--rookie-primary);
}

.migration-dialog__progress-detail {
  display: flex;
  gap: 24px;
  margin-top: 10px;
  font-size: var(--rookie-font-size-sm);
  color: var(--rookie-text-tertiary);
}

.migration-dialog__error {
  margin-top: 10px;
  padding: 8px 12px;
  border-radius: var(--rookie-radius-sm);
  background: var(--rookie-danger-bg, rgba(245, 108, 108, 0.1));
  color: var(--rookie-danger, #f56c6c);
  font-size: var(--rookie-font-size-sm);
  line-height: 1.5;
  word-break: break-all;
}

.migration-dialog__actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
