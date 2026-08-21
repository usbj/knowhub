<!--
  注册页 /register
  ------------------------------------------------------------------
  接后端 /register + /register/enabled。字段对齐后端 RegisterBody：
  - username（必填，≤12 位字母数字下划线）/ nickName（选填，空默认取 username）
  - phoneNumber（选填，11 位手机号）/ email（选填，仅前端收集+找回密码提示，不传后端）
  - password（6-20）/ confirm / agreement
  去掉性别（用户要求）。注册开关关时显「暂未开放」+ 禁用提交。
  注册不自动登录，成功后跳 /login。email 不落库（后端 RegisterBody/sys_user 无 email 列）。
-->
<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { User, Message, Lock, Postcard } from '@element-plus/icons-vue'
import AuthLayout from '@/components/layout/AuthLayout.vue'
import { getRegisterEnabledApi, registerApi } from '@/api/system/login'
import type { RegisterRequestData } from '@/types/api/login'

const router = useRouter()
const formRef = ref<FormInstance>()
const form = reactive({
  username: '',
  nickName: '',
  phoneNumber: '',
  email: '',
  password: '',
  confirm: '',
  agreement: false,
})

/** 注册是否开放：GET /register/enabled（后端读 sys.user.registerEnabled，默认 false）。失败按未开放处理。 */
const registerEnabled = ref(false)

const validateConfirm = (_rule: unknown, value: string, callback: (e?: Error) => void) => {
  if (value !== form.password) callback(new Error('两次输入的密码不一致'))
  else callback()
}

/** 手机号选填：空跳过，填则需 11 位（与后端 ^1\d{10}$ 对齐） */
const validatePhone = (_rule: unknown, value: string, callback: (e?: Error) => void) => {
  if (!value) {
    callback()
    return
  }
  if (!/^1\d{10}$/.test(value)) callback(new Error('请输入正确的 11 位手机号'))
  else callback()
}

/** email 选填：空跳过，填则校验格式（仅前端收集，不传后端） */
const validateEmail = (_rule: unknown, value: string, callback: (e?: Error) => void) => {
  if (!value) {
    callback()
    return
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) callback(new Error('邮箱格式不正确'))
  else callback()
}

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { max: 12, message: '用户名不超过 12 个字符', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]+$/, message: '用户名只能包含字母、数字和下划线', trigger: 'blur' },
  ],
  phoneNumber: [{ validator: validatePhone, trigger: 'blur' }],
  email: [{ validator: validateEmail, trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度 6-20 个字符', trigger: 'blur' },
  ],
  confirm: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' },
  ],
}

const loading = ref(false)
const submit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    if (!registerEnabled.value) {
      ElMessage.warning('注册功能暂未开放，请联系系统管理员')
      return
    }
    if (!form.agreement) {
      ElMessage.warning('请先阅读并同意用户协议与隐私政策')
      return
    }
    loading.value = true
    try {
      // payload 对齐后端 RegisterBody：不含 email（后端无此列）、不含 sex（已去性别）
      const payload: RegisterRequestData = {
        username: form.username.trim(),
        password: form.password,
        nickName: form.nickName.trim() || undefined,
        phoneNumber: form.phoneNumber.trim() || undefined,
      }
      await registerApi(payload)
      ElMessage.success('注册成功，请登录')
      await router.replace('/login')
    } catch (error) {
      // 后端错误文案（含「注册功能未开放」/重复提示）已由 http 工具统一弹出，这里只兜住 loading
      console.error('register failed', error)
    } finally {
      loading.value = false
    }
  })
}

const loadRegisterEnabled = async () => {
  try {
    const result = await getRegisterEnabledApi()
    registerEnabled.value = result.data === true
  } catch {
    registerEnabled.value = false
  }
}

onMounted(loadRegisterEnabled)
</script>

<template>
  <AuthLayout
    title="加入知枢"
    subtitle="创建账号，开始沉淀知识、展示项目、分享资源"
    switch-text="已有账号？"
    switch-to="/login"
    switch-action="去登录"
  >
    <!-- 注册未开放提示条 -->
    <div v-if="!registerEnabled" class="auth-form__disabled-tip">
      注册功能暂未开放，请联系系统管理员。
    </div>

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      size="large"
      label-position="top"
      class="auth-form"
      @submit.prevent="submit"
    >
      <el-form-item prop="username">
        <el-input
          v-model="form.username"
          placeholder="用户名（12 位以内字母、数字或下划线）"
          :prefix-icon="User"
          clearable
        />
      </el-form-item>
      <el-form-item prop="nickName">
        <el-input
          v-model="form.nickName"
          placeholder="昵称（选填，不填默认用用户名）"
          :prefix-icon="Postcard"
          clearable
        />
      </el-form-item>
      <el-form-item prop="phoneNumber">
        <el-input
          v-model="form.phoneNumber"
          placeholder="手机号（选填，用于账号找回等场景）"
          clearable
        />
      </el-form-item>
      <el-form-item prop="email">
        <el-input
          v-model="form.email"
          placeholder="邮箱（选填，便于后续找回密码）"
          :prefix-icon="Message"
          clearable
        />
      </el-form-item>
      <el-form-item prop="password">
        <el-input
          v-model="form.password"
          type="password"
          placeholder="密码（6-20 个字符）"
          :prefix-icon="Lock"
          show-password
        />
      </el-form-item>
      <el-form-item prop="confirm">
        <el-input
          v-model="form.confirm"
          type="password"
          placeholder="确认密码"
          :prefix-icon="Lock"
          show-password
          @keyup.enter="submit"
        />
      </el-form-item>

      <div class="auth-form__agree">
        <el-checkbox v-model="form.agreement">
          我已阅读并同意
          <button type="button" class="auth-form__link">《用户协议》</button>
          与
          <button type="button" class="auth-form__link">《隐私政策》</button>
        </el-checkbox>
      </div>

      <el-button
        type="primary"
        size="large"
        class="auth-form__submit"
        :loading="loading"
        :disabled="!registerEnabled"
        @click="submit"
      >
        注 册
      </el-button>
    </el-form>
  </AuthLayout>
</template>

<style scoped>
.auth-form :deep(.el-form-item) {
  margin-bottom: 16px;
}
.auth-form :deep(.el-input__wrapper) {
  border-radius: var(--kh-radius);
  padding: 4px 12px;
}
.auth-form__disabled-tip {
  margin-bottom: 16px;
  padding: 10px 14px;
  border: 1px solid var(--kh-warm-soft, var(--kh-border));
  border-radius: var(--kh-radius);
  background: var(--kh-bg-soft);
  color: var(--kh-text-secondary);
  font-size: var(--kh-font-size-sm);
  line-height: 1.6;
}
.auth-form__agree {
  margin: -4px 0 20px;
}
.auth-form__agree :deep(.el-checkbox__label) {
  font-size: var(--kh-font-size-sm);
  color: var(--kh-text-secondary);
  line-height: 1.6;
  white-space: normal;
}
.auth-form__link {
  border: none;
  background: transparent;
  color: var(--kh-primary);
  font-size: inherit;
  cursor: pointer;
  padding: 0;
}
.auth-form__link:hover {
  text-decoration: underline;
}
.auth-form__submit {
  width: 100%;
  height: 48px;
  border-radius: var(--kh-radius);
  font-weight: 600;
  font-size: var(--kh-font-size-md);
  letter-spacing: 0.04em;
}
</style>
