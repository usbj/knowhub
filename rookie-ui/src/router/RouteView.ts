/**
 * 文件作用：
 * 作为动态目录路由的中间承载组件，
 * 让多级菜单目录也能继续向下渲染自己的子路由内容。
 */
import { defineComponent, h } from 'vue'
import { RouterView } from 'vue-router'

export default defineComponent({
  name: 'RouteView',
  setup() {
    return () => h(RouterView)
  },
})
