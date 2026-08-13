<!--
  文件作用：
  借出审核弹窗，承接借出列表「通过/驳回」操作。
  关键参数：
  - `visible`：弹窗显隐。
  - `loanId`：待审核借出主键（提交时回传，承 ReviewVo.blogId）。
  - `loan`：待审核借出详情（物品名/类型/数量/借用人），供审核员参考。
  - `decide`：approve 通过 / reject 驳回，决定弹窗默认决定与标题。
  关键交互：
  - 驳回时 advice 必填。
  - 主题适配：弹窗内容用 --rookie-* 变量，深浅模式自动跟随。
-->
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElLink,
  ElRadio,
  ElRadioGroup,
  ElEmpty,
} from 'element-plus'
import DictTag from '@/components/DictTag.vue'
import { downloadVoucher } from '../shared/downloadVoucher'
import type { LoanRecord, LoanReviewPayload } from '@/types/api/knowhub/audit'

const props = withDefaults(
  defineProps<{
    visible: boolean
    loanId: number | null
    loan: LoanRecord | null
    decide?: 'approve' | 'reject'
  }>(),
  {
    decide: 'approve',
  },
)

const emit = defineEmits<{
  'update:visible': [value: boolean]
  submit: [payload: LoanReviewPayload]
}>()

const pass = ref<boolean>(true)
const advice = ref('')

watch(
  () => [props.visible, props.decide] as const,
  ([visible, decide]) => {
    if (visible) {
      pass.value = decide !== 'reject'
      advice.value = ''
    }
  },
)

const dialogTitle = computed(() => (props.decide === 'reject' ? '驳回借出申请' : '审核通过借出'))

const canSubmit = computed(() => (pass.value ? true : advice.value.trim().length > 0))

const handleSubmit = () => {
  if (!props.loanId || !canSubmit.value) {
    return
  }

  emit('submit', {
    blogId: props.loanId,
    pass: pass.value,
    advice: pass.value ? advice.value.trim() || undefined : advice.value.trim(),
  })
}
</script>

<template>
  <ElDialog
    :model-value="visible"
    :title="dialogTitle"
    width="640px"
    :close-on-click-modal="false"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
  >
    <section v-if="loan" class="audit-loan-review__content">
      <div class="audit-loan-review__row">
        <span><em>物品</em>{{ loan.itemName }}</span>
        <span><em>类型</em><DictTag dict-key="audit_loan_item_type" :value="loan.itemType" /></span>
        <span><em>数量</em>{{ loan.quantity ?? 1 }}</span>
        <span v-if="loan.assetNo"><em>资产号</em>{{ loan.assetNo }}</span>
      </div>
      <div class="audit-loan-review__row">
        <span><em>主体</em>{{ loan.subjectName || loan.subjectId }}</span>
        <span><em>借用人</em>{{ loan.borrowerName || '--' }}</span>
        <span><em>联系电话</em>{{ loan.borrowerPhone || '--' }}</span>
        <span v-if="loan.borrowerOrg"><em>所属单位</em>{{ loan.borrowerOrg }}</span>
      </div>
      <p v-if="loan.note" class="audit-loan-review__note">{{ loan.note }}</p>
      <div v-if="loan.voucherObjectId" class="audit-loan-review__voucher">
        <em>附件</em>
        <ElLink type="primary" :underline="false" @click="downloadVoucher(loan.voucherObjectId)">
          点击下载 (附件 #{{ loan.voucherObjectId }})
        </ElLink>
      </div>
    </section>
    <ElEmpty v-else description="未加载到借出信息" :image-size="56" />

    <ElForm label-width="80px" class="audit-loan-review__form">
      <ElFormItem label="审核决定" required>
        <ElRadioGroup v-model="pass">
          <ElRadio :value="true">通过</ElRadio>
          <ElRadio :value="false">驳回</ElRadio>
        </ElRadioGroup>
      </ElFormItem>

      <ElFormItem :label="pass ? '审核意见' : '驳回原因'" :required="!pass">
        <ElInput
          v-model="advice"
          type="textarea"
          :rows="3"
          :placeholder="pass ? '可选，填写审核意见' : '请填写驳回原因（必填）'"
        />
      </ElFormItem>
    </ElForm>

    <template #footer>
      <div class="audit-loan-review__actions">
        <ElButton @click="emit('update:visible', false)">取消</ElButton>
        <ElButton type="primary" :disabled="!canSubmit" @click="handleSubmit">
          确认提交
        </ElButton>
      </div>
    </template>
  </ElDialog>
</template>

<style scoped>
.audit-loan-review__content {
  display: grid;
  gap: 10px;
  margin-bottom: 16px;
  padding: 12px 16px;
  border: 1px solid var(--rookie-border);
  border-radius: var(--rookie-radius-md);
  background: var(--rookie-surface-weak);
  font-size: var(--rookie-font-size-sm);
  color: var(--rookie-text-secondary);
}

.audit-loan-review__row {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
}

.audit-loan-review__row em {
  font-style: normal;
  color: var(--rookie-text-tertiary);
  margin-right: 8px;
}

.audit-loan-review__note {
  margin: 0;
  padding: 6px 10px;
  border-left: 2px solid var(--rookie-border);
  white-space: pre-wrap;
  word-break: break-word;
}

.audit-loan-review__voucher {
  display: flex;
  align-items: center;
  gap: 8px;
}

.audit-loan-review__voucher em {
  font-style: normal;
  color: var(--rookie-text-tertiary);
}

.audit-loan-review__form {
  margin-top: 4px;
}

.audit-loan-review__actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>