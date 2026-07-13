<!--
  登录页 /login
  ------------------------------------------------------------------
  账号密码登录 + 三方登录占位 + 记住我 + 忘记密码。
  调后端 /login 拿 token 后写入 userStore，再拉 /person 补用户资料，最后按 redirect 回源。
-->
<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import AuthLayout from '@/components/layout/AuthLayout.vue'
import { loginApi } from '@/api/system/login'
import { useUserStore } from '@/stores/user'
import { useNoticeStore } from '@/stores/notice'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const noticeStore = useNoticeStore()
const formRef = ref<FormInstance>()
const form = reactive({
  account: '',
  password: '',
  remember: true,
})

const rules: FormRules = {
  account: [
    { required: true, message: '请输入用户名或邮箱', trigger: 'blur' },
    { min: 3, message: '账号长度至少 3 个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度至少 6 个字符', trigger: 'blur' },
  ],
}

const loading = ref(false)
/**
 * 处理登录：校验通过后调后端 /login 拿 token、写入 store，
 * 再拉一次 /person 把当前用户资料补进 store（供顶栏与个人中心展示），
 * 最后按 redirect 查询参数回到来源页。
 * 后端 LoginBody 仅认 username/password，account 直接作为 username 提交。
 */
const submit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      const loginResult = await loginApi({
        username: form.account.trim(),
        password: form.password.trim(),
      })
      userStore.setLoginSession(loginResult.data)
      // 登录成功后拉个人资料，顶栏/个人中心即可立即展示真实昵称；
      // 顺带惰性拉公告，让铃铛第一时间显示未读
      await userStore.fetchUserProfile()
      noticeStore.fetchMyNotices().catch(() => undefined)
      ElMessage.success('登录成功')
      const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
      await router.replace(redirect)
    } catch (error) {
      // 通用报错提示已在 http 工具统一处理，这里只兜住异步流程并复位 loading
      console.error('login failed', error)
    } finally {
      loading.value = false
    }
  })
}

/** 三方登录占位 */
const socials = [
  { key: 'github', label: 'GitHub' },
  { key: 'gitee', label: 'Gitee' },
] as const
const onSocial = (key: string) => ElMessage.info(`${key} 登录占位（demo）`)
</script>

<template>
  <AuthLayout
    title="欢迎回到知枢"
    subtitle="登录后即可发布博客、管理项目、上传资源"
    switch-text="还没有账号？"
    switch-to="/register"
    switch-action="去注册"
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
      <el-form-item prop="account">
        <el-input
          v-model="form.account"
          placeholder="用户名 / 邮箱"
          :prefix-icon="User"
          clearable
        />
      </el-form-item>
      <el-form-item prop="password">
        <el-input
          v-model="form.password"
          type="password"
          placeholder="密码"
          :prefix-icon="Lock"
          show-password
          @keyup.enter="submit"
        />
      </el-form-item>

      <div class="auth-form__options">
        <el-checkbox v-model="form.remember">记住我</el-checkbox>
        <button type="button" class="auth-form__forgot" @click="ElMessage.info('找回密码占位（demo）')">
          忘记密码？
        </button>
      </div>

      <el-button
        type="primary"
        size="large"
        class="auth-form__submit"
        :loading="loading"
        @click="submit"
      >
        登 录
      </el-button>
    </el-form>

    <!-- 分隔线 -->
    <div class="auth-form__divider">
      <span>其他登录方式</span>
    </div>

    <!-- 三方登录占位 -->
    <div class="auth-form__socials">
      <button
        v-for="s in socials"
        :key="s.key"
        type="button"
        class="auth-form__social"
        @click="onSocial(s.key)"
      >
        <KhIcon :name="s.key" :size="18" />
        {{ s.label }}
      </button>
    </div>
  </AuthLayout>
</template>

<style scoped>
.auth-form :deep(.el-form-item) {
  margin-bottom: 18px;
}
.auth-form :deep(.el-input__wrapper) {
  border-radius: var(--kh-radius);
  padding: 4px 12px;
}
.auth-form__options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: -4px 0 20px;
}
.auth-form__forgot {
  border: none;
  background: transparent;
  color: var(--kh-text-secondary);
  font-size: var(--kh-font-size-sm);
  cursor: pointer;
  transition: color var(--kh-transition-fast);
}
.auth-form__forgot:hover {
  color: var(--kh-primary);
}
.auth-form__submit {
  width: 100%;
  height: 48px;
  border-radius: var(--kh-radius);
  font-weight: 600;
  font-size: var(--kh-font-size-md);
  letter-spacing: 0.04em;
}
.auth-form__divider {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--kh-text-tertiary);
  font-size: 12px;
}
.auth-form__divider::before,
.auth-form__divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--kh-border-soft);
}
.auth-form__socials {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--kh-space-3);
}
.auth-form__social {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 44px;
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius);
  background: var(--kh-surface);
  color: var(--kh-text-secondary);
  font-size: var(--kh-font-size-sm);
  font-weight: 500;
  cursor: pointer;
  transition:
    border-color var(--kh-transition-fast),
    color var(--kh-transition-fast),
    background var(--kh-transition-fast);
}
.auth-form__social:hover {
  border-color: var(--kh-border-strong);
  color: var(--kh-text);
  background: var(--kh-surface-hover);
}
</style>