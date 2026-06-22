/**
 * 文件作用：
 * 为经 app.use 全局注册的第三方组件补充 vue GlobalComponents 类型，
 * 让模板里直接以标签形式使用这些组件时能通过 vue-tsc 校验。
 * 单独成文件，避免与 env.d.ts 的 ambient 模块声明互相干扰。
 */
import type { DefineComponent } from 'vue'

declare module 'vue' {
  export interface GlobalComponents {
    'v-md-editor': DefineComponent<
      Record<string, unknown>,
      Record<string, unknown>,
      unknown
    >
    'v-md-preview': DefineComponent<
      Record<string, unknown>,
      Record<string, unknown>,
      unknown
    >
  }
}
