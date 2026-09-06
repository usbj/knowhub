<!--
  文件作用：
  借出归还弹窗，承接借出列表「归还」操作。可填损耗金额（>0 触发事务内插损耗花销流水走阈值审批）+ 备注。
  关键参数：
  - `visible`：弹窗显隐。
  - `loan`：待归还借出记录（loanId 必填，回传时承载）。
  关键交互：
  - 损耗金额 wearLossAmount 默认 0（无损耗）；>0 时后端事务内插 EXPENSE(WEAR) 流水并回指 related_flow_id。
  - 备注可填；提交时向父层 emit submit({ ...loan, wearLossAmount, note })。
  - 主题适配：--rookie-* 变量，深浅模式自动跟随。
-->
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElButton, ElDialog, ElForm, ElFormItem, ElInput, ElInputNumber, ElLink } from 'element-plus'
import { downloadVoucher } from '../shared/downloadVoucher'
import type { LoanRecord } from '@/types/api/knowhub/audit'

const props = defineProps<{
  visible: boolean
  loan: LoanRecord | null
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  submit: [payload: LoanRecord]
}>()

/** 损耗金额（元），0 或空表示无损耗；>0 触发后端插损耗花销流水 */
const wearLossAmount = ref<number | undefined>(0)
const note = ref('')

watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      wearLossAmount.value = 0
      note.value = ''
    }
  },
)

const dialogTitle = computed(() => '归还借出物品')

const canSubmit = computed(() => {
  const amount = Number(wearLossAmount.value ?? 0)
  return amount >= 0 && (!props.loan || props.loan.loanId != null)
})

const handleSubmit = () => {
  if (!props.loan || !props.loan.loanId || !canSubmit.value) {
    return
  }

  const amount = Number(wearLossAmount.value ?? 0)
  emit('submit', {
    ...props.loan,
    wearLossAmount: amount > 0 ? amount : undefined,
    note: note.value.trim() || props.loan.note || '',
  })
}
</script>

<template>
  <ElDialog
    :model-value="visible"
    :title="dialogTitle"
    width="560px"
    :close-on-click-modal="false"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
  >
    <section v-if="loan" class="audit-loan-return__content">
      <div class="audit-loan-return__row">
        <span><em>物品</em>{{ loan.itemName }}</span>
        <span><em>数量</em>{{ loan.quantity ?? 1 }}</span>
        <span v-if="loan.assetNo"><em>资产号</em>{{ loan.assetNo }}</span>
      </div>
      <div class="audit-loan-return__row">
        <span><em>借用人</em>{{ loan.borrowerName || '--' }}</span>
        <span><em>联系电话</em>{{ loan.borrowerPhone || '--' }}</span>
      </div>
      <div v-if="loan.voucherObjectId" class="audit-loan-return__voucher">
        <em>附件</em>
        <ElLink type="primary" :underline="false" @click="downloadVoucher(loan.voucherObjectId)">
          点击下载 (附件 #{{ loan.voucherObjectId }})
        </ElLink>
      </div>
    </section>

    <ElForm label-width="96px" class="audit-loan-return__form">
      <ElFormItem label="损耗金额(元)">
        <ElInputNumber
          v-model="wearLossAmount"
          :min="0"
          :precision="2"
          :step="1"
          controls-position="right"
          placeholder="0 表示无损耗"
          style="width: 100%"
        />
        <p class="audit-loan-return__hint">大于 0 时后端事务内插入一条损耗花销流水（分类=损耗）并走阈值审批。</p>
      </ElFormItem>

      <ElFormItem label="归还备注">
        <ElInput
          v-model="note"
          type="textarea"
          :rows="3"
          placeholder="可选，填写归还情况说明"
        />
      </ElFormItem>
    </ElForm>

    <template #footer>
      <div class="audit-loan-return__actions">
        <ElButton @click="emit('update:visible', false)">取消</ElButton>
        <ElButton type="primary" :disabled="!canSubmit" @click="handleSubmit">
          确认归还
        </ElButton>
      </div>
    </template>
  </ElDialog>
</template>

<style scoped>
.audit-loan-return__content {
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

.audit-loan-return__row {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
}

.audit-loan-return__row em {
  font-style: normal;
  color: var(--rookie-text-tertiary);
  margin-right: 8px;
}

.audit-loan-return__voucher {
  display: flex;
  align-items: center;
  gap: 8px;
}

.audit-loan-return__voucher em {
  font-style: normal;
  color: var(--rookie-text-tertiary);
  margin-right: 0;
}

.audit-loan-return__hint {
  margin: 4px 0 0;
  color: var(--rookie-text-tertiary);
  font-size: var(--rookie-font-size-sm);
  line-height: 1.5;
}

.audit-loan-return__form {
  margin-top: 4px;
}

.audit-loan-return__actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>