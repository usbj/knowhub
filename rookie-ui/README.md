# rookie-ui

## 项目说明

`rookie-ui` 是 `rookie` 基础管理系统的前端界面工程，技术栈为 Vue 3、TypeScript、Vite、Vue Router、Pinia，并可结合 Element Plus 作为交互与基础组件支撑。

当前界面设计目标不是做”展示型官网”，而是为后台管理系统建立一套现代、克制、稳定的工作台视觉语言，强调信息层级、操作效率与长时间使用时的舒适度。

## 项目架构（目录）说明

根目录文件：

- `index.html` — SPA 入口 HTML，声明 `<div id=”app”>` 挂载点和 Vue 应用启动脚本
- `package.json` — 项目依赖与脚本声明（`dev` / `build` / `type-check` / `preview`）
- `vite.config.ts` — Vite 构建配置，含 `@` 路径别名、`/api` 代理到 `http://localhost:8080` 并去除前缀
- `tsconfig.json` / `tsconfig.app.json` / `tsconfig.node.json` — TypeScript 编译配置分层（项目级 / 应用代码 / Vite 工具层）
- `env.d.ts` — 全局类型声明（Vite 环境变量、Vue SFC 模块类型等）
- `design/` — 设计稿与视觉参考资源
- `public/` — 直接复制到构建产物的静态文件（favicon、非模块化资源）

`src/` 源码目录结构：

```
src/
├── api/                  后端接口请求方法
│   └── system/           按后端模块划分，一个功能模块一个文件
│         notice.ts       通知管理相关接口（CRUD、发布/撤回、分组、当前用户”我的通知”）
│         dict.ts         字典相关接口
│         ...
├── assets/               静态资源与全局样式
│   ├── base.css          主题变量定义（字号、圆角、颜色、阴影、Element Plus 变量映射）
│   ├── main.css          Element Plus 组件全局覆写与主题适配（表格、按钮、输入框、弹窗等）
│   └── logo.svg          应用 Logo
├── components/           公共可复用组件
│   ├── BaseCard.vue      页面统一卡片容器（标题栏 + 内容区）
│   ├── DictTag.vue       根据字典键值和值渲染对应样式的标签（ElTag 封装）
│   ├── PageProgressBar.vue  页面切换顶部细线进度条
│   ├── SearchFilterPanel.vue  列表页筛选区组件（字段配置 + 搜索/重置/新建按钮）
│   ├── SharedFormPanel.vue    公共表单组件（筛选项 / 弹窗两种模式，按 schema 自动渲染字段）
│   └── SharedTablePanel.vue  公共表格组件（按 schema 渲染列、行操作按钮、分页、内置弹窗表单）
├── composables/          Vue 组合式函数
│   ├── useDict.ts        组件级字典读取（getDictData / getDictOptions / resolveDictLabel）
│   ├── usePageTransition.ts  页面切换进度条启动/结束
│   └── usePermission.ts  权限判断（基于后端菜单 perm_key 按钮权限集）
├── constants/
│   └── systemPermissions.ts  各模块操作权限 key 常量（双值数组，与后端菜单 perm_key 首个值对齐）
├── layout/               应用壳体布局
│   ├── index.vue         主布局（侧边栏 + 头导航 + 主内容区 + 提示面板 + 主题抽屉）
│   └── components/
│       ├── MainContentShell.vue  路由视图容器 + 页面切换过渡动效
│       ├── NavBar/        头导航（搜索框、面包屑、通知铃铛、主题切换、用户下拉、标签页栏）
│       ├── PromptPanel.vue  通用提示/通知详情弹窗
│       ├── SideBar/       侧边栏（递归渲染目录/菜单树，支持嵌套、折叠态 popover）
│       └── ThemeSettingsDrawer.vue  主题设置抽屉（明暗、主色、字号、圆角）
├── router/
│   ├── index.ts           路由实例 + 前置守卫（登录校验、用户资料恢复、菜单拉取、动态路由注册）
│   └── dynamicRoutes.ts   按后端菜单树注册动态路由，根据菜单 path 匹配 src/views 下的 Vue 组件
├── stores/               Pinia 状态管理
│   ├── user.ts            当前登录用户（token、个人资料、登录/退出）
│   ├── navigation.ts      布局导航（菜单树、展开目录、面包屑、标签页、祖先链）
│   ├── themePreference.ts 主题设置（明暗、主色、字号、圆角，CSS 变量注入）
│   ├── dict.ts            字典缓存（批量拉取、按 dictKey 查询标签/选项）
│   └── notice.ts          当前用户”我的通知”（未读计数、已读标记、详情查询）
├── types/                TypeScript 类型定义
│   ├── api/               对后端接口的类型（与后端 VO / DTO / Query 对齐）
│   │   └── system/        按后端模块划分
│   │         common.ts    通用返回结构（ApiResult / NormalizedPageResult / PageQueryParams）
│   │         notice.ts    通知与分组记录 / 查询参数 / 分页结果类型
│   │         ...
│   └── components/        前端组件相关类型
│         data-display/    SharedFieldSchemaMap 等表格/表单字段配置类型
│         navigation/      NavigationMenuItem / LayoutTabItem 等侧边栏与标签页类型
│         theme/           ThemeSettings / NotificationItem 等主题与通知展示类型
│         ...
├── utils/                工具函数
│   ├── http.ts            Axios 实例封装（baseURL / Token 注入 / 响应拦截 / 分页归一化 / get/post/put/del/getPage）
│   ├── format.ts          格式化工具（formatDateTime / formatDisplayValue / sanitizeDisplayText）
│   ├── object.ts          对象路径读取/写入（getValueByPath / setValueByPath）
│   └── menu-icons.ts      菜单图标解析（icon 字符串 → Element Plus 图标组件）
└── views/                页面视图组件
    ├── login.vue          登录页
    ├── dashboard/         系统首页
    ├── profile/           个人中心
    ├── PlaceholderView.vue  动态路由匹配失败的兜底占位页
    └── system/            系统模块页面（与侧边栏目录结构一致）
        ├── user/          用户管理（index.vue + config.ts）
        ├── role/          角色管理（含 components/RolePermissionTreeField.vue 权限树字段）
        ├── menu/          菜单管理（含 MenuIconPicker.vue 图标选择器）
        ├── dict/          字典管理
        ├── dict-data/     字典数据管理
        └── notice/        通知管理
            ├── notice-content/  内容管理（通知 CRUD、发布/撤回/详情）
            └── notice-group/    分组管理（分组 CRUD、成员管理弹窗）
```

**特定名称约定：**

- `config.ts` — 每个列表页的同目录配置文件，集中定义该页面的字段 schema（查询/表格/表单字段元数据、校验规则、枚举选项、标签颜色映射），让单一页面的字段逻辑集中可维护，而 `index.vue` 专注于状态与交互
- `components/` — 当前页面专属的子组件目录，复用度不够进入 `src/components/` 的组件放这里
- `stores/` 中的每个 store 文件对应一个独立状态域（用户 / 导航 / 主题 / 字典 / 通知），不混在一起
- `types/api/` 按后端模块划分，文件名与 `api/` 下的请求文件、后端 Controller 所属模块保持一一对应
- 页面目录尽量与菜单 `route` 片段保持一致，如菜单 route 为 `notice-content` 则组件在 `views/system/notice/notice-content/`

## 当前设计语言

### 1. 设计定位

- 面向基础管理系统与通用后台场景
- 风格关键词：现代、轻商务、克制、清晰、可信、耐看
- 界面气质：避免花哨装饰，突出结构秩序与状态反馈

### 2. 色彩策略

- 主色：低饱和蓝青系，用于导航激活、高亮边界、交互焦点
- 背景：大面积浅灰白，拉开页面层次，减少纯白刺眼感
- 文本：深灰蓝而非纯黑，兼顾可读性与柔和度
- 状态色：成功、警示、风险色只用于状态表达，不参与大面积装饰

推荐方向：

- 主色：`#0f766e` / `#0369a1`
- 强调色：`#14b8a6` / `#38bdf8`
- 页面背景：`#f4f7fb`
- 容器背景：`#ffffff`
- 主文本：`#172033`
- 次文本：`#5b6780`
- 分割线：`#dbe3ef`

### 3. 布局原则

- 使用“固定侧导航 + 顶部工作区 + 主内容区”的经典后台布局
- 侧导航承担模块切换与全局入口，不堆叠额外信息卡片
- 主内容区保持留白与稳定边距，提升扫描效率
- 圆角控制在小圆角范围，避免卡片感过重

### 4. 组件表达

- 导航组件以“图标 + 文本 + 状态”构成，激活态明确、悬停态轻盈
- 菜单分组通过标题、缩进、背景层级区分，而不是依赖重描边
- 收起态优先保留识别性图标与悬浮提示
- 按钮、下拉、徽标等控件统一使用平直、轻阴影、弱渐变策略

### 5. 交互原则

- 所有可点击区域具备清晰 hover / active / focus-visible 状态
- 展开收起动效短促稳定，避免位移过大
- 当前所在模块必须能被快速识别
- 键盘导航顺序应与视觉顺序一致

### 6. 需要避免的方向

- 不使用大面积紫色、荧光色或高对比撞色
- 不使用营销页式大卡片堆叠作为后台主结构
- 不使用过重阴影、厚描边或过圆润控件
- 不为了“炫”而牺牲菜单识别速度和内容密度

## 特定术语描述

- 页面：指当前浏览器展示的整个网站前端窗口区域
- 侧边栏：指页面最左边、可以收缩、用于展示用户可访问的目录菜单的区域
- 头导航：指页面去除侧边栏后最上方划分的区域
- 菜单内容展示区（主要内容区域）：指去掉头导航和侧边栏，展示菜单对应内容的区域

## 当前实现约定

- 当用户在对话中补充新的“前端实现注意事项”时，需要在确认后第一时间同步到本 `README`
- 每次同步新的注意事项时，必须检查是否与旧规则冲突；如果冲突，以最新规则为准，并及时改写或删除旧规则，避免并存
- 一般情况下，菜单内容组件统一写在 `src/views` 下，组件路径尽量与侧边栏目录菜单保持一致，例如“系统模块 / 用户管理”对应 `src/views/system/user/index.vue`
- 一般情况下，菜单所属目录要与后端 `controller` 所在模块保持一致；当前项目主要按模块名称划分，必要时再结合包名判断
- 只能按照用户明确提出的需求修改；如果有扩展想法，必须先说明并获得同意后才能改；如果用户已调整过代码，想改回去也必须先说明
- 每次开始修改前，先检查是否存在不再需要的文件、组件或旧代码；确认无用后及时删除
- 当前内容区域专属组件，放在当前目录下的 `components` 文件夹内；可复用的公共组件，放在 `src/components`
- 如果要写某个类型的工具方法，统一放在 `src/utils`
- 如果某个页面或组件过长，需要优先评估是否可以拆分；能拆则拆，避免单文件承担过多结构和逻辑
- 所有需要定义的 TypeScript 接口类型统一收敛到 `src/types`
- 与后端对接的接口类型统一放到 `src/types/api/模块名称/功能模块名称.ts`
- 只要 store 中保存的数据存在对应后端接口来源，相关接口类型也统一放到 `src/types/api`
- 与后端对接的接口请求方法统一放到 `src/api/模块名称/功能模块.ts`，例如获取用户数据写在 `src/api/system/user.ts`
- 组件传参、布局交互等前端组件相关类型统一放到 `src/types/components/组件名称/index.ts`
- 封装组件前先评估 Element Plus 等现有三方组件库是否已有合适能力，可用则优先基于现成组件做二次封装
- 侧导航以可维护的数据结构驱动，优先保证后续接入真实菜单时可扩展
- Element Plus 作为辅助，不喧宾夺主，视觉风格以项目自身主题为主
- 代码中应补充相对详细、能帮助后来者理解设计意图与数据流向的注释，而不是只写表面行为说明
- 新创建的文件必须在前几行补充文件级注释，说明该文件的主要作用
- 每个脚本方法上方都要补充注释，至少说明方法效果、参数和返回值；如果是无返回值方法，也要明确说明副作用或状态变更
- 较为复杂的逻辑，尤其是涉及判断、循环和方法调用链的部分，必须补充解释性注释，说明为什么这样处理以及数据如何流转
- `base.css` 负责定义可被系统统一调整的基础变量，例如字号、背景色、边框色、选中色、圆角等；需要跟随主题设置联动的组件优先使用这些变量
- 字体大小以一个基础字号变量为起点，其他字号统一通过基础字号加减获得
- 公共卡片和其他用于区域划分的组件，统一使用 `base.css` 中的背景、字体、边框、圆角等基础变量
- 如果某类样式不需要跟随系统统一更改，例如图表自身配色，则不要使用 `base.css` 的全局变量
- Vue 注释规则：脚本部分的方法注释必须覆盖功能、参数、返回值和必要副作用，复杂逻辑要拆解说明；模板部分只在最外层标签上方标明区域；CSS 默认不写注释，除非特殊说明

## 12. 协作注意事项

以下事项适用于 AI 协作修改 `rookie-ui` 前端代码的场景：

- **不要随意启动 dev server 验证改动**：本项目以内置配置运行，dev server 的启动可能与其他进程冲突，或者占用端口而用户正在调试中。每次修改前端代码后只需做 type-check（`vue-tsc` 或项目内现有静态检查），前端改动是否正确由用户自行在合适时机验证
- **更新日志记录在根 `doc/devlog.md`**：前端代码开发任务也需要同步写入该文件，格式遵循 `doc/devlog.md` 中的"日期 → 时间-任务简介 → 变更清单"约定。非代码任务的讨论（规则、规划、设计探讨）不记入开发日志
- **接口文档记录在根 `doc/api.md`**：与后端联调或涉及接口对接时，关注该文件中是否正确反映了当前前后端约定（Token 头名称为 `Token` 无 Bearer，请求体字段与后端 VO 对齐等）
- **主题适配是默认验收项**：参考本文档第 11 节的详细清单，新增浮层/面板/滚动容器类组件时同时检查深浅模式
- **遵循分类约束**：`base.css` 中定义的变量优先使用；不需要跟随主题的样式（如图表自身配色）避免使用全局变量
- 上述约定同样写入根 `doc/devlog.md` 作为开发任务后同步更新；新增的前端注意事项也应在确认后第一时间同步到本文档
