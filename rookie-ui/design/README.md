# Rookie Admin UI Draft

这个目录用于放置 `rookie-ui` 的后台 UI 设计草稿，当前阶段只做静态设计表达，不进入 Vue 代码实现。

## 文件说明

- `index.html`：设计稿目录入口
- `dashboard.html`：工作台
- `users.html`：用户管理
- `roles.html`：角色与权限
- `menus.html`：菜单配置
- `dict.html`：字典配置
- `settings.html`：系统界面设置
- `login.html`：登录页
- `styles.css`：共享样式文件

## 这版草稿的依据

结合仓库现有信息做了收敛：

1. `graphify-out/GRAPH_REPORT.md` 指向的核心域：
   - 用户
   - 角色
   - 菜单
   - 字典
   - 认证
   - 权限
2. `README.md` 对项目的定位：
   - Spring Boot 3 + Vue 3 的后台管理基础框架
   - 更偏系统管理与运营配置，而不是营销型产品前台
3. `ui-ux-pro-max` 检索结果：
   - 方向采用 data-dense dashboard
   - 保留高信息密度和列表效率
   - 颜色从工具默认的紫色系收敛为更克制的中性灰 + 功能蓝

## 页面结构

当前草稿覆盖了后续最适合优先实现的几个页面：

- 工作台 Dashboard
- 用户管理
- 角色与权限
- 菜单配置
- 字典配置
- 系统界面设置
- 登录页

## 视觉方向

- 主体色：中性深灰 + 蓝色强调
- 气质：专业、安静、偏企业后台
- 组件：侧栏固定、顶部导航、标签栏、通知入口、个人信息区、列表页强调筛选和抽屉
- 目标：先把信息架构和交互节奏定下来，再进入 Vue 实装

## 下一步建议

等你确认后，可以按这个顺序进入 Vue 开发：

1. 先拆布局壳子：`Sidebar + Topbar + TabsBar + AppMain + ProfileActions`
2. 再实现 Dashboard 和 Settings
3. 然后做用户/角色/菜单/字典四个核心系统页
4. 最后补登录页、权限守卫和路由配置
