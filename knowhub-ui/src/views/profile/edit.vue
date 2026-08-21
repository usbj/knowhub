<!--
  个人资料编辑 /profile/edit
  ------------------------------------------------------------------
  从个人中心「编辑资料」按钮进入。
  - 左侧账号只读卡：头像（可点击上传更换）+ 昵称 + 角色只读元信息。
  - 右侧资料修改表单：el-form 提交 PUT /person（昵称/手机号/性别三项）；
    头像上传独立走预签名直传 USER_AVATAR + PUT /person 持久化 avatar=URL，即时刷新不等保存按钮；
    密码重置独立表单走 PUT /person/password（oldPassword/newPassword，与资料编辑分离）。
  头像/密码/资料三者解耦：头像即时上传即时持久化，密码独立校验独立提交，资料走保存按钮。
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
import { updatePersonalProfileApi, modifyPersonalPasswordApi } from '@/api/system/user'
import { presignedUploadFlow } from '@/utils/upload'

const router = useRouter()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const passwordFormRef = ref<FormInstance>()
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

/** 头像上传状态：上传中禁用再次点击。 */
const avatarUploading = ref(false)
/** 隐藏的文件选择框引用，点击头像区时触发其选择文件。 */
const avatarFileInput = ref<HTMLInputElement>()

/** 点击头像区，触发隐藏文件选择框。 */
const handleClickAvatar = () => {
  if (avatarUploading.value) return
  avatarFileInput.value?.click()
}

/**
 * 校验并上传新头像：前端先做类型/大小预检（与后端口径一致），
 * 通过后走预签名直传 USER_AVATAR 拿 /file/resolve/{id}，再 PUT /person 持久化 avatar=URL，最后刷新 store。
 */
const handleAvatarFileChange = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  // 每次选择后清空 input，保证连续选择同一文件也能触发 change
  input.value = ''

  if (!file) return

  const ALLOWED_EXTS = ['png', 'jpg', 'jpeg', 'gif', 'webp']
  const ext = file.name.includes('.') ? file.name.split('.').pop()!.toLowerCase() : ''
  if (!ALLOWED_EXTS.includes(ext)) {
    ElMessage.warning('头像仅支持 png/jpg/jpeg/gif/webp 格式')
    return
  }
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.warning('头像大小不能超过2MB')
    return
  }

  avatarUploading.value = true
  try {
    const result = await presignedUploadFlow({
      file,
      businessType: 'USER_AVATAR',
      access: 'PUBLIC',
      bizRefId: userStore.userInfo?.userId,
    })
    if (!result.publicUrl) {
      throw new Error('头像上传未返回访问 URL')
    }
    // 持久化到 sys_user.avatar 列（PUT /person 带字段后端 editUserInfo 动态更新）
    await updatePersonalProfileApi({ avatar: result.publicUrl })
    // 刷新 store（fetchUserProfile 会重新拉 /person 含新 avatar URL）
    await userStore.fetchUserProfile()
    ElMessage.success('头像已更新')
  } catch {
    // http 拦截器已弹错；头像上传失败不阻断页面
  } finally {
    avatarUploading.value = false
  }
}

/** 密码重置表单 */
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})
const passwordSaving = ref(false)
const passwordRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    {
      validator: (_r, value: string, cb) => {
        if (!value || value.length < 6 || value.length > 20) cb(new Error('新密码长度 6-20 位'))
        else cb()
      },
      trigger: 'blur',
    },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_r, value: string, cb) => {
        if (value !== passwordForm.newPassword) cb(new Error('两次输入的密码不一致'))
        else cb()
      },
      trigger: 'blur',
    },
  ],
}

/** 提交密码重置：调 PUT /person/password，成功后清空表单并提示。 */
const handlePasswordSave = async () => {
  if (!passwordFormRef.value) return
  try {
    await passwordFormRef.value.validate()
  } catch {
    return // 校验失败，el-form 自带提示
  }
  passwordSaving.value = true
  try {
    await modifyPersonalPasswordApi({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
    })
    ElMessage.success('密码已更新')
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
  } catch {
    // http 拦截器已弹错（原密码错误/新密码长度不合法由后端抛业务错误）
  } finally {
    passwordSaving.value = false
  }
}

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
        <!-- 左：账号信息只读卡（含头像上传） -->
        <KhCard padding="lg" class="profile-edit__aside">
          <div class="profile-edit__account">
            <button
              class="profile-edit__avatar"
              type="button"
              aria-label="更换头像"
              :title="avatarUploading ? '头像上传中…' : '点击更换头像'"
              :disabled="avatarUploading"
              @click="handleClickAvatar"
            >
              <KhAvatar :item="{ label: avatarLabel(), src: userStore.avatarUrl ?? undefined }" :size="64" />
              <span class="profile-edit__avatar-hint">{{ avatarUploading ? '上传中…' : '更换头像' }}</span>
            </button>
            <input
              ref="avatarFileInput"
              class="profile-edit__avatar-input"
              type="file"
              accept=".png,.jpg,.jpeg,.gif,.webp,image/png,image/jpeg,image/gif,image/webp"
              @change="handleAvatarFileChange"
            />
            <h2 class="profile-edit__account-name">{{ userStore.userInfo?.nickName || '—' }}</h2>
            <KhTag type="primary" dot>{{ displayRole() }}</KhTag>
          </div>
          <dl class="profile-edit__meta">
            <div><dt>登录账号</dt><dd>@{{ displayUsername() }}</dd></div>
            <div><dt>当前昵称</dt><dd>{{ userStore.userInfo?.nickName || '—' }}</dd></div>
            <div><dt>手机号</dt><dd>{{ userStore.userInfo?.phoneNumber || '—' }}</dd></div>
          </dl>
        </KhCard>

        <!-- 右：资料修改 + 密码重置（竖排两卡） -->
        <div class="profile-edit__right">
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

          <!-- 密码重置（独立表单，走 PUT /person/password，与资料编辑解耦） -->
          <KhCard padding="lg" class="profile-edit__form">
            <h3 class="profile-edit__form-title">修改密码</h3>
            <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-position="top" class="profile-edit__el-form">
              <el-form-item label="原密码" prop="oldPassword">
                <el-input v-model="passwordForm.oldPassword" type="password" placeholder="请输入原密码" show-password />
              </el-form-item>
              <el-form-item label="新密码" prop="newPassword">
                <el-input v-model="passwordForm.newPassword" type="password" placeholder="6-20 位新密码" show-password />
              </el-form-item>
              <el-form-item label="确认新密码" prop="confirmPassword">
                <el-input v-model="passwordForm.confirmPassword" type="password" placeholder="请再次输入新密码" show-password />
              </el-form-item>
            </el-form>
            <div class="profile-edit__actions">
              <button
                class="profile-edit__btn profile-edit__btn--primary"
                type="button"
                :disabled="passwordSaving"
                @click="handlePasswordSave"
              >
                <span v-if="!passwordSaving">更新密码</span>
                <span v-else>更新中…</span>
              </button>
            </div>
          </KhCard>
        </div>
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
/* 头像上传按钮：包 KhAvatar + 悬停"更换头像"遮罩；点击触发隐藏 file input */
.profile-edit__avatar {
  position: relative;
  border: none;
  background: transparent;
  padding: 0;
  cursor: pointer;
  border-radius: 50%;
  overflow: hidden;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.profile-edit__avatar:disabled {
  cursor: not-allowed;
  opacity: 0.7;
}
.profile-edit__avatar-hint {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.5);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  opacity: 0;
  transition: opacity var(--kh-transition-fast);
  border-radius: 50%;
}
.profile-edit__avatar:hover .profile-edit__avatar-hint,
.profile-edit__avatar:disabled .profile-edit__avatar-hint {
  opacity: 1;
}
/* 隐藏的文件选择框：不占布局、不可见，由头像按钮触发 */
.profile-edit__avatar-input {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
  pointer-events: none;
}
/* 右侧列：资料修改 + 密码重置两卡竖排 */
.profile-edit__right {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-6);
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