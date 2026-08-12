<!--
  个人资料编辑 /profile/edit
  ------------------------------------------------------------------
  从个人中心「编辑资料」按钮进入。el-form 提交 PUT /person（昵称/手机号/性别三项），
  保存成功后 userStore.fetchUserProfile() 刷新顶栏与个人中心昵称，再跳回 /profile。
  不放密码框、不放头像：后端 editUserInfo SQL 只持久化 username/nickName/phoneNumber/sex/status，
  password/avatar 不在 SQL 中（rookie-ui 那个随 PUT /person 提交的密码框是无效功能）。故只发这三项。
-->
<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import KhCard from '@/components/common/KhCard.vue'
import KhAvatar from '@/components/common/KhAvatar.vue'
import KhTag from '@/components/common/KhTag.vue'
import { useUserStore } from '@/stores/user'
import { updatePersonalProfileApi } from '@/api/system/user'

const router = useRouter()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const saving = ref(false)

const form = reactive({
  nickName: '',
  phoneNumber: '',
  sex: '1',
})

const rules: FormRules = {
  nickName: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    {
      validator: (_r, value: string, cb) => {
        if (!value || !value.trim()) cb(new Error('昵称不能为空'))
        else cb()
      },
      trigger: 'blur',
    },
  ],
  phoneNumber: [
    {
      validator: (_r, value: string, cb) => {
        // 可空；非空时需为 11 位手机号
        if (value && !/^1[3-9]\d{9}$/.test(value)) cb(new Error('请输入正确的 11 位手机号'))
        else cb()
      },
      trigger: 'blur',
    },
  ],
}

const displayUsername = () => userStore.userInfo?.username || ''
const displayRole = () => {
  const roles = userStore.userInfo?.userRole ?? []
  return roles.length > 0 ? roles[0]!.roleName : '访客'
}
const avatarLabel = () => userStore.userInfo?.nickName || userStore.userInfo?.username || '客'

/** 进入页：若 store 无资料先拉一次，再回填表单（昵称/手机号/性别，性别缺省'1'男） */
onMounted(async () => {
  if (!userStore.userInfo) {
    await userStore.fetchUserProfile()
  }
  const info = userStore.userInfo
  form.nickName = info?.nickName ?? ''
  form.phoneNumber = info?.phoneNumber ?? ''
  form.sex = info?.sex === '0' ? '0' : '1'
})

const handleCancel = () => {
  router.push('/profile')
}

const handleSave = async () => {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return // 校验失败，el-form 自带提示
  }
  saving.value = true
  try {
    await updatePersonalProfileApi({
      nickName: form.nickName.trim(),
      phoneNumber: form.phoneNumber.trim(),
      sex: form.sex,
    })
    // 成功后刷新顶栏与个人中心昵称（setUserProfile 同步更新 store+localStorage）
    await userStore.fetchUserProfile()
    ElMessage.success('资料已更新')
    router.push('/profile')
  } catch {
    // http 拦截器已弹错
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="profile-edit">
    <div class="kh-container kh-container--boxed profile-edit__body">
      <button class="profile-edit__back" type="button" @click="handleCancel">
        <el-icon><ArrowLeft /></el-icon> 返回个人中心
      </button>
      <h1 class="profile-edit__title">编辑资料</h1>

      <div class="profile-edit__grid">
        <!-- 左：账号信息只读卡 -->
        <KhCard padding="lg" class="profile-edit__aside">
          <div class="profile-edit__account">
            <KhAvatar :item="{ label: avatarLabel() }" :size="64" />
            <h2 class="profile-edit__account-name">{{ userStore.userInfo?.nickName || '—' }}</h2>
            <KhTag type="primary" dot>{{ displayRole() }}</KhTag>
          </div>
          <dl class="profile-edit__meta">
            <div><dt>登录账号</dt><dd>@{{ displayUsername() }}</dd></div>
            <div><dt>当前昵称</dt><dd>{{ userStore.userInfo?.nickName || '—' }}</dd></div>
            <div><dt>手机号</dt><dd>{{ userStore.userInfo?.phoneNumber || '—' }}</dd></div>
          </dl>
        </KhCard>

        <!-- 右：资料修改表单 -->
        <KhCard padding="lg" class="profile-edit__form">
          <h3 class="profile-edit__form-title">资料修改</h3>
          <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="profile-edit__el-form">
            <el-form-item label="昵称" prop="nickName">
              <el-input v-model="form.nickName" placeholder="请输入昵称" maxlength="20" show-word-limit clearable />
            </el-form-item>
            <el-form-item label="手机号" prop="phoneNumber">
              <el-input v-model="form.phoneNumber" placeholder="可选，11 位手机号" maxlength="11" clearable />
            </el-form-item>
            <el-form-item label="性别" prop="sex">
              <el-select v-model="form.sex" placeholder="请选择性别">
                <el-option label="男" value="1" />
                <el-option label="女" value="0" />
              </el-select>
            </el-form-item>
          </el-form>
          <div class="profile-edit__actions">
            <button class="profile-edit__btn profile-edit__btn--ghost" type="button" @click="handleCancel">取消</button>
            <button class="profile-edit__btn profile-edit__btn--primary" type="button" :disabled="saving" @click="handleSave">
              <span v-if="!saving">保存</span>
              <span v-else>保存中…</span>
            </button>
          </div>
        </KhCard>
      </div>
    </div>
  </div>
</template>

<style scoped>
.profile-edit {
  padding-top: var(--kh-space-6);
  padding-bottom: var(--kh-space-12);
  background: var(--kh-bg);
  min-height: 60vh;
}
.profile-edit__body {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-5);
}
.profile-edit__back {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 var(--kh-space-2);
  border: none;
  background: transparent;
  color: var(--kh-text-secondary);
  font-size: var(--kh-font-size-sm);
  cursor: pointer;
  align-self: flex-start;
}
.profile-edit__back:hover {
  color: var(--kh-primary);
}
.profile-edit__title {
  font-size: var(--kh-font-size-2xl);
  font-weight: 700;
}
.profile-edit__grid {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: var(--kh-space-6);
  align-items: start;
}

/* —— 左：账号信息只读卡 —— */
.profile-edit__aside {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-5);
}
.profile-edit__account {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--kh-space-3);
  text-align: center;
  padding-bottom: var(--kh-space-4);
  border-bottom: 1px solid var(--kh-border-soft);
}
.profile-edit__account-name {
  font-size: var(--kh-font-size-lg);
  font-weight: 700;
}
.profile-edit__meta {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-3);
  margin: 0;
}
.profile-edit__meta > div {
  display: flex;
  justify-content: space-between;
  font-size: var(--kh-font-size-sm);
}
.profile-edit__meta dt {
  color: var(--kh-text-tertiary);
}
.profile-edit__meta dd {
  color: var(--kh-text);
  margin: 0;
  font-family: var(--kh-font-mono);
}

/* —— 右：资料修改表单 —— */
.profile-edit__form {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-5);
}
.profile-edit__form-title {
  font-size: var(--kh-font-size-md);
  font-weight: 600;
}
.profile-edit__actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--kh-space-3);
  padding-top: var(--kh-space-4);
  border-top: 1px solid var(--kh-border-soft);
}
.profile-edit__btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 40px;
  padding: 0 var(--kh-space-6);
  border-radius: var(--kh-radius-pill);
  font-weight: 600;
  font-size: var(--kh-font-size-sm);
  cursor: pointer;
  transition: all var(--kh-transition-fast);
}
.profile-edit__btn--ghost {
  border: 1px solid var(--kh-border);
  background: var(--kh-surface);
  color: var(--kh-text-secondary);
}
.profile-edit__btn--ghost:hover {
  border-color: var(--kh-primary-border);
  color: var(--kh-primary);
}
.profile-edit__btn--primary {
  border: none;
  background: linear-gradient(120deg, var(--kh-primary), var(--kh-primary-strong));
  color: #fff;
  box-shadow: var(--kh-shadow-primary);
}
.profile-edit__btn--primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

@media (max-width: 900px) {
  .profile-edit__grid {
    grid-template-columns: 1fr;
  }
}
</style>