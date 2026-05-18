import './assets/main.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'

import App from './App.vue'
import router from './router'
import { useThemePreferenceStore } from './stores/themePreference'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)
app.use(ElementPlus)

/**
 * 主题 store 需要在应用挂载前先把 CSS 变量打到页面上，
 * 这样初次渲染就能拿到正确的明暗主题和尺寸参数。
 */
useThemePreferenceStore(pinia).applyThemeSettings()

app.mount('#app')
