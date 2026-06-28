<script setup lang="ts">
/**
 * 文件作用：
 * 承接个人中心页面的资料展示与编辑，
 * 并与当前登录用户的个人信息接口保持同步。
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import SharedFormPanel from '@/components/SharedFormPanel.vue'
import { updatePersonalProfileApi } from '@/api/system/user'
import { useUserStore } from '@/stores/user'
import type { UpdatePersonalProfilePayload } from '@/types/api/system/user'
import type { SharedFieldSchemaMap } from '@/types/components/data-display'

const userStore = useUserStore()
const saving = ref(false)

const form = reactive<UpdatePersonalProfilePayload>({
  nickName: '',
  phoneNumber: '',
  sex: '1',
  password: '',
})

const profileSummary = computed(() => userStore.profileSummary)

const profileFormSchema = computed<SharedFieldSchemaMap<UpdatePersonalProfilePayload>>(() => ({
  nickName: {
    label: '昵称',
    inputType: 'text',
    placeholder: '请输入昵称',
    formVisible: true,
    tableVisible: false,
    formOrder: 1,
  },
  phoneNumber: {
    label: '手机号',
    inputType: 'text',
    placeholder: '请输入手机号',
    formVisible: true,
    tableVisible: false,
    formOrder: 2,
  },
  sex: {
    label: '性别',
    inputType: 'select',
    placeholder: '请选择性别',
    formVisible: true,
    tableVisible: false,
    formOrder: 3,
    options: [
      { label: '男', value: '1' },
      { label: '女', value: '0' },
    ],
  },
  password: {
    label: '新密码',
    inputType: 'password',
    placeholder: '不修改可留空',
    formVisible: true,
    tableVisible: false,
    formOrder: 4,
    props: {
      autocomplete: 'new-password',
    },
  },
}))

/**
 * store 中的个人资料一旦更新，就同步回表单。
 * 这样页面首屏读取和保存成功后的回显都走同一条数据流。
 */
watch(
  profileSummary,
  (profile) => {
    if (!profile) {
      return
    }

    form.nickName = profile.nickName || ''
    form.phoneNumber = profile.phoneNumber || ''
    form.sex = profile.sex || '1'
    form.password = ''
  },
  { immediate: true },
)

onMounted(async () => {
  if (!userStore.userInfo) {
    await userStore.fetchUserProfile()
  }
})

/**
 * 方法效果：
 * 保存个人资料表单，并在接口成功后刷新当前登录用户资料。
 * 参数：
 * - 无，直接读取当前页面中的表单状态。
 * 返回值：
 * - 无返回值；副作用是更新后端个人资料并刷新 store 中的用户信息。
 */
const handleSaveProfile = async () => {
  if (!form.nickName.trim()) {
    ElMessage.warning('请输入昵称')
    return
  }

  saving.value = true

  try {
    const payload: UpdatePersonalProfilePayload = {
      nickName: form.nickName.trim(),
      phoneNumber: form.phoneNumber.trim(),
      sex: form.sex,
      password: form.password?.trim() || undefined,
    }

    const result = await updatePersonalProfileApi(payload)

    if (result.data) {
      await userStore.fetchUserProfile()
      ElMessage.success('个人资料已更新')
    }
  } finally {
    saving.value = false
  }
}

/**
 * 方法效果：
 * 接收公共表单组件提交的新模型，并同步回当前页面的响应式表单对象。
 * 参数：
 * - `nextFormValue`：公共表单组件回传的最新表单值。
 * 返回值：
 * - 无返回值；副作用是覆盖当前页的本地表单状态。
 */
const handleFormModelUpdate = (nextFormValue: Record<string, unknown>) => {
  /**
   * 这里显式逐项回写，而不是直接替换整个 reactive 对象，
   * 是为了保持当前页面中已有的响应式引用不丢失。
   */
  form.nickName = String(nextFormValue.nickName ?? '')
  form.phoneNumber = String(nextFormValue.phoneNumber ?? '')
  form.sex = String(nextFormValue.sex ?? '1')
  form.password = String(nextFormValue.password ?? '')
}
</script>

<template>
  <!-- 个人中心页面区域 -->
  <section class="profile-view">
    <header class="profile-view__header">
      <div>
        <h1>个人中心</h1>
        <p>维护当前登录账号的基础资料与登录密码。</p>
      </div>
      <div class="profile-view__avatar">
        {{ (profileSummary?.nickName || profileSummary?.username || 'U').slice(0, 1).toUpperCase() }}
      </div>
    </header>

    <div class="profile-view__content">
      <section class="profile-view__panel">
        <header class="profile-view__panel-head">
          <h2>账号信息</h2>
          <p>只保留基础系统里真正常用的信息。</p>
        </header>

        <dl class="profile-view__summary-list">
          <div class="profile-view__summary-row">
            <dt>登录账号</dt>
            <dd>{{ profileSummary?.username || '--' }}</dd>
          </div>
          <div class="profile-view__summary-row">
            <dt>当前昵称</dt>
            <dd>{{ profileSummary?.nickName || '--' }}</dd>
          </div>
          <div class="profile-view__summary-row">
            <dt>手机号</dt>
            <dd>{{ profileSummary?.phoneNumber || '--' }}</dd>
          </div>
          <div class="profile-view__summary-row">
            <dt>角色</dt>
            <dd>{{ profileSummary?.roleNames.join('、') || '未分配角色' }}</dd>
          </div>
        </dl>
      </section>

      <section class="profile-view__panel">
        <header class="profile-view__panel-head">
          <h2>资料修改</h2>
          <p>保存后会同步更新顶部当前用户展示信息。</p>
        </header>

        <SharedFormPanel
          :schema="profileFormSchema"
          :model-value="form as unknown as Record<string, unknown>"
          :loading="saving"
          :columns="2"
          @update:model-value="handleFormModelUpdate"
          @submit="handleSaveProfile"
        />
      </section>
    </div>
  </section>
</template>

<style scoped>
.profile-view {
  display: grid;
  gap: 18px;
}

.profile-view__header,
.profile-view__panel {
  border: 1px solid var(--rookie-border);
  border-radius: var(--rookie-radius-lg);
  background: var(--rookie-card-bg);
  box-shadow: var(--rookie-shadow);
}

.profile-view__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 22px 24px;
}

.profile-view__header h1,
.profile-view__panel-head h2 {
  margin: 0;
  color: var(--rookie-text);
}

.profile-view__header p,
.profile-view__panel-head p {
  margin: 0;
  color: var(--rookie-text-secondary);
}

.profile-view__avatar {
  width: 52px;
  height: 52px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: var(--rookie-avatar-bg);
  color: var(--rookie-primary-strong);
  font-size: var(--rookie-font-size-xl);
  font-weight: 700;
  flex: none;
}

.profile-view__content {
  display: grid;
  grid-template-columns: minmax(280px, 0.88fr) minmax(0, 1.12fr);
  gap: 18px;
}

.profile-view__panel {
  padding: 22px 24px;
  display: grid;
  gap: 18px;
}

.profile-view__panel-head {
  display: grid;
  gap: 8px;
}

.profile-view__summary-list {
  display: grid;
  gap: 14px;
  margin: 0;
}

.profile-view__summary-row {
  display: grid;
  grid-template-columns: 92px minmax(0, 1fr);
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid var(--rookie-border);
}

.profile-view__summary-row:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}

.profile-view__summary-row:first-child {
  padding-top: 0;
}

.profile-view__summary-row dt {
  color: var(--rookie-text-secondary);
  font-size: var(--rookie-font-size-sm);
  margin: 0;
}

.profile-view__summary-row dd {
  color: var(--rookie-text);
  margin: 0;
  word-break: break-word;
}

@media (max-width: 1024px) {
  .profile-view__header,
  .profile-view__content {
    display: grid;
    grid-template-columns: 1fr;
  }
}
</style>
