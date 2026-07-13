<!--
  注册页 /register
  ------------------------------------------------------------------
  demo 阶段：表单外形 + 校验占位，不接鉴权。
  用户名/邮箱/密码/确认密码 + 协议复选 + 注册占位。
-->
<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { User, Message, Lock } from '@element-plus/icons-vue'
import AuthLayout from '@/components/layout/AuthLayout.vue'

const router = useRouter()
const formRef = ref<FormInstance>()
const form = reactive({
  username: '',
  email: '',
  password: '',
  confirm: '',
  agreement: false,
})

const validateConfirm = (_rule: unknown, value: string, callback: (e?: Error) => void) => {
  if (value !== form.password) callback(new Error('两次输入的密码不一致'))
  else callback()
}

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 16, message: '用户名长度 3-16 个字符', trigger: 'blur' },
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度至少 6 个字符', trigger: 'blur' },
  ],
  confirm: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' },
  ],
}

const loading = ref(false)
const submit = async () => {
  if (!formRef.value) return
  await formRef.value.validate((valid) => {
    if (!valid) return
    if (!form.agreement) {
      ElMessage.warning('请先阅读并同意用户协议与隐私政策')
      return
    }
    loading.value = true
    // demo：模拟注册跳转登录
    setTimeout(() => {
      loading.value = false
      ElMessage.success('注册成功，请登录（demo）')
      router.push('/login')
    }, 600)
  })
}
</script>

<template>
  <AuthLayout
    title="加入知枢"
    subtitle="创建账号，开始沉淀知识、展示项目、分享资源"
    switch-text="已有账号？"
    switch-to="/login"
    switch-action="去登录"
  >
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
          placeholder="用户名（3-16 个字符）"
          :prefix-icon="User"
          clearable
        />
      </el-form-item>
      <el-form-item prop="email">
        <el-input
          v-model="form.email"
          placeholder="邮箱"
          :prefix-icon="Message"
          clearable
        />
      </el-form-item>
      <el-form-item prop="password">
        <el-input
          v-model="form.password"
          type="password"
          placeholder="密码（至少 6 个字符）"
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