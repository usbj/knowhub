# 开发日志

本文件记录 rookie 项目中每次协作完成的代码开发任务。只有产生实际文件或代码变动的任务才记录；探讨规则、项目规划、文档框架搭建等不在此列。

**记录约定：**
- 只记录"已完成"的代码开发任务
- 记录文件路径和简要变更描述，不记录具体行号
- 同一轮对话完成的相关任务合并为一个条目
- 此约定后续可能变动，以文件内最新说明为准

---

## 2026-07-02
### 20:03 — 菜单管理 parentId 约定修复 + 公共表单校验提示去框

- `rookie-ui/src/views/system/menu/config.ts` — createDefaultMenuForm 默认 parentId 由 0 改为 -1，与后端 buildMenuTree 顶级约定对齐
- `rookie-ui/src/views/system/menu/index.vue` — parentMenuOptions「顶级目录」选项值由 0 改为 -1，避免选中顶级后存盘导致菜单从列表消失
- `rookie-system/src/main/java/com/rookie/system/service/impl/SysMenuServiceImpl.java` — buildMenuTree 顶级判定处补注释说明 parentId=-1 约定（不改逻辑）
- `rookie-ui/src/assets/main.css` — .el-form-item__error 去掉背景/边框/圆角/阴影/padding，改为纯文字提示（danger 色 + xs 字号）；错误提示用绝对定位脱离文档流（top:100%），校验出现/消失不改变 form-item 高度与 margin，下方输入框不跳动；文字在默认 18px 下间隙内垂直居中（行高 12px + padding-top 3px）

### 21:48 — 管理员直通权限加载去掉 system: 前缀限制

- `rookie-framework/src/main/resources/mapper/security/UserInfoMapper.xml` — selectAllPermKey 去掉 `perm_key like 'system:%:%'` 前缀过滤，改为 `menu_type = 3`（按钮型）+ status=1 + delete=0 + perm_key 非空，覆盖任意前缀模块的按钮权限键
- `rookie-framework/src/main/java/com/rookie/framework/security/mapper/UserInfoMapper.java` — selectAllPermKey 方法注释同步更新（不限前缀）

## 2026-06-20
### 18:30 — 消息通知模块 Service、Controller、Mapper 补全 + 实体清理

- `rookie-system/src/main/java/com/rookie/system/pojo/SysNoticeGroupMember.java` — 删除 memberType/memberCode/memberName 字段，memberId 改为 userId
- `rookie-system/src/main/java/com/rookie/system/mapper/SysNoticeGroupMemberMapper.java` — 同步删除 edit/多余方法
- `rookie-system/src/main/resources/mapper/system/SysNoticeGroupMemberMapper.xml` — 同步清理字段映射和 SQL
- `rookie-system/src/main/java/com/rookie/system/mapper/SysNoticeMapper.java` — 新增 softDeleteSysNotice、getNoticesForUser
- `rookie-system/src/main/java/com/rookie/system/mapper/SysNoticeReadMapper.java` — 新增 getByNoticeAndUser
- `rookie-system/src/main/java/com/rookie/system/mapper/SysNoticeGroupMapper.java` — 新增 quarrySysNoticeGroup
- `rookie-system/src/main/resources/mapper/system/SysNoticeMapper.xml` — 新增 softDeleteSysNotice、getNoticesForUser（ALL + GROUP 联合查询）
- `rookie-system/src/main/resources/mapper/system/SysNoticeReadMapper.xml` — 新增 getByNoticeAndUser
- `rookie-system/src/main/resources/mapper/system/SysNoticeGroupMapper.xml` — 新增 quarrySysNoticeGroup
- `rookie-system/src/main/java/com/rookie/system/pojo/vo/SysNoticeVo.java` — 新建，含 groupIds 和 noticeGroups 扩展字段
- `rookie-system/src/main/java/com/rookie/system/pojo/vo/SysNoticeGroupVo.java` — 新建，含 members 扩展字段
- `rookie-system/src/main/java/com/rookie/system/service/SysNoticeService.java` — 新建，10 个方法（CRUD + publish/revoke + markAsRead/confirmNotice + getMyNotices）
- `rookie-system/src/main/java/com/rookie/system/service/impl/SysNoticeServiceImpl.java` — 新建，级联 groupRel 增删、已读幂等、确认补齐逻辑
- `rookie-system/src/main/java/com/rookie/system/service/SysNoticeGroupService.java` — 新建，7 个方法（CRUD + addMembers/removeMembers）
- `rookie-system/src/main/java/com/rookie/system/service/impl/SysNoticeGroupServiceImpl.java` — 新建，分组增删时级联清理关联数据
- `rookie-system/src/main/java/com/rookie/system/controller/SysNoticeController.java` — 新建，10 个接口端点
- `rookie-system/src/main/java/com/rookie/system/controller/SysNoticeGroupController.java` — 新建，7 个接口端点
- `sql/sys_notice.sql` — sys_notice_group_member 表删除 member_type 等冗余列，唯一键调整为 (group_id, user_id)

### 18:50 — 通知模块问题修复：已读标记、事务、分页、分组列表改进

- `rookie-system/src/main/java/com/rookie/system/pojo/vo/SysNoticeVo.java` — 新增 hasRead、hasConfirmed 字段
- `rookie-system/src/main/java/com/rookie/system/service/SysNoticeService.java` — getMyNotices 签名改为接收 Long userId
- `rookie-system/src/main/java/com/rookie/system/service/impl/SysNoticeServiceImpl.java` — addSysNoticeInfo/editSysNoticeInfo/deleteSysNoticeInfo 加 @Transactional；getMyNotices 中注入 hasRead/hasConfirmed 到返回 VO（由 controller 提取 userId）
- `rookie-system/src/main/java/com/rookie/system/controller/SysNoticeController.java` — getMyNotices 改为从 SecurityContextHolder 获取当前用户并传入 service
- `rookie-system/src/main/java/com/rookie/system/pojo/quarry/NoticeGroupQuarry.java` — 新建，分组查询 DTO（groupName/groupCode/status）
- `rookie-system/src/main/java/com/rookie/system/mapper/SysNoticeGroupMapper.java` — quarrySysNoticeGroup 签名改为接收 NoticeGroupQuarry
- `rookie-system/src/main/resources/mapper/system/SysNoticeGroupMapper.xml` — quarrySysNoticeGroup SQL 改为动态查询
- `rookie-system/src/main/java/com/rookie/system/service/SysNoticeGroupService.java` — quarrySysNoticeGroup 返回 PageInfo 并接收 Quarry；removeMembers 签名改为 (Long groupId, List<Long> memberIds)
- `rookie-system/src/main/java/com/rookie/system/service/impl/SysNoticeGroupServiceImpl.java` — addSysNoticeGroupInfo/deleteSysNoticeGroupInfo 加 @Transactional；removeMembers 加同组校验 + 批量删除
- `rookie-system/src/main/java/com/rookie/system/controller/SysNoticeGroupController.java` — 分组列表返回 PageInfo 并接收 Quarry；移除成员改为 DELETE /{groupId}/members + @RequestBody List

## 2026-06-21
### 18:17 — 字典数据列表标签列改造：类型/风格/类名合并为单一标签展示列

- `rookie-ui/src/types/components/data-display/index.ts` — 新增 `SharedFieldRenderType`（`text`/`tag`）、`SharedFieldTagType`、`SharedFieldTagRenderOptions`（支持 `labelField`/`typeField`/`effectField`/`classField` 跨字段读取标签文字、颜色、风格、类名）；`SharedFieldSchemaItem` 新增 `renderType`/`tagRender` 字段
- `rookie-ui/src/components/SharedTablePanel.vue` — 新增 `tag` 渲染分支与 `resolveTagDisplay` 方法，按 schema 配置把单元格渲染为 `ElTag`（含跨字段取色、空值回落 info、占位文本）；导出 `ElTag` 及新类型
- `rookie-ui/src/views/system/dict-data/config.ts` — 抽出共享常量 `TAG_TYPE_MAP`；新增 `tagPreview` 虚拟列（仅表格展示），跨字段读取 dictDataLabel/tagType/tagEffect/cssClass 组合渲染标签；tagType/tagEffect/cssClass/extJson 四列 `tableVisible` 改为 false（表单保留），去掉表格专属配置并顺延 formOrder

### 19:48 — 通知管理（内容管理 + 分组管理）前端页面与菜单 SQL

- `rookie-ui/src/types/api/system/notice.ts` — 新建，定义 SysNoticeRecord/SysNoticeListQuery/SysNoticePageResult、SysNoticeGroupRecord/SysNoticeGroupMemberRecord/SysNoticeGroupListQuery/SysNoticeGroupPageResult，对齐后端 SysNoticeVo/SysNoticeGroupVo/SysNoticeGroupMember
- `rookie-ui/src/api/system/notice.ts` — 新建，封装通知 CRUD + 发布/撤回、分组 CRUD + 成员增删（addNoticeGroupMembersApi/removeNoticeGroupMembersApi，移除成员传成员记录 id）
- `rookie-ui/src/constants/systemPermissions.ts` — 新增 notice（create/edit/delete/publish/revoke）与 noticeGroup（create/edit/delete/member）权限 key 分组
- `rookie-ui/src/views/system/notice/config.ts` — 新建，通知类型/级别/范围/状态固定枚举与 tagTypeMap，createNoticeQuerySchema/createNoticeSchema（类型/级别/范围/状态列用 renderType:'tag' 渲染）、noticeFormRules
- `rookie-ui/src/views/system/notice/index.vue` — 新建，通知内容管理页，对齐 dict 页结构；行操作含编辑/发布（status≠PUBLISHED）/撤回（status=PUBLISHED）/详情/删除；关联分组用 field-groupIds 插槽（publishScope=GROUP 时多选分组）；详情用 ElDescriptions 只读弹窗展示正文
- `rookie-ui/src/views/system/notice-group/config.ts` — 新建，分组状态选项、createNoticeGroupQuerySchema/createNoticeGroupSchema（memberCount 列通过 formatter 读 row.members.length 计算）、noticeGroupFormRules
- `rookie-ui/src/views/system/notice-group/index.vue` — 新建，通知分组管理页，对齐 dict 页结构；行操作含编辑/管理成员/删除；用户数据源延迟到首次打开成员管理时按需拉取
- `rookie-ui/src/views/system/notice-group/components/GroupMemberTransfer.vue` — 新建，封装 ElTransfer 成员管理穿梭框，保存时按目标集合与原始集合做差集分别调用添加/移除接口，移除时按 userId 还原成员记录 id
- `rookie-ui/src/assets/main.css` — 新增 ElTransfer 主题覆盖（面板、表头、搜索框、项、按钮统一跟随主题变量，深浅模式适配）
- `README.md`（根） — 第 12 节主题适配清单"已完成适配区域"补充 ElTransfer、ElDescriptions
- `sql/sys_notice_menu.sql` — 新建，插入通知管理菜单（一级目录 36 + 通知内容 37 及 7 个按钮权限 + 通知分组 45 及 6 个按钮权限），perm_key 与前端权限 key 对齐，path 指向 src/views 组件，ON DUPLICATE KEY UPDATE 幂等
- `sql/sys_notice_menu_admin.sql` — 新建，给超级管理员（角色 1）授权通知管理菜单 36-51

### 20:39 — 侧边栏嵌套目录（目录套目录）UI 与祖先链逻辑优化

- `rookie-ui/src/layout/components/SideBar/components/SideBarMenuButton.vue` — 取消 `depth*18px` 内边距缩进（避免与父容器缩进叠加导致深层文字被挤），按钮 padding 固定；depth 改为层级弱化用途，新增 `is-nested` 修饰类弱化非顶层节点图标与字重
- `rookie-ui/src/layout/components/SideBar/components/SideBarSection.vue` — `__children` 去掉固定 margin/border，改为单一 padding-left 缩进；仅顶层目录（depth=0）展开时通过 `--root` 修饰类画一条贯通引导线，嵌套子目录不再重复画线避免平行双竖线；折叠态 popover 宽度 220→260、链接加省略号容纳"系统模块 / 通知管理 / 通知内容"长标签；展开过渡 max-height 520→760 容纳深嵌套
- `rookie-ui/src/stores/navigation.ts` — `activeDirectory`（单层父）替换为 `activeDirectoryTrail`（按 parentId 递归向上收集完整祖先目录链），`breadcrumbs` 改为 `[...祖先链, 当前菜单]` 正确支持三级嵌套，`syncByPath` 自动展开改为展开整条祖先链（刷新/直进深层菜单时上层目录不再丢失可见性）

### 21:27 — 头导航通知按钮完善 + 通知页面目录重构 + UI README 架构说明

- `rookie-ui/src/api/system/notice.ts` — 追加 `getMyNoticesApi` / `markAsReadApi` / `confirmNoticeApi` 当前用户侧接口
- `rookie-ui/src/stores/notice.ts` — 新建，管理"我的通知"列表、未读计数（`unreadCount`）、已读标记（乐观更新 `hasRead`）、详情查询（`getNoticeById`）、退出清理（`resetNoticeState`）
- `rookie-ui/src/router/index.ts` — 路由守卫首次加载完成段追加 `noticeStore.fetchMyNotices()`，退出登录 catch 分支追加 `noticeStore.resetNoticeState()`；导入 `useNoticeStore`
- `rookie-ui/src/layout/components/NavBar/index.vue` — 移除本地空 `notifications` ref，改用 `useNoticeStore` 的 `myNotices` 映射为 `NotificationItem` 展示（标题 / 正文截断摘要 / 类型中文标签 / 发布时间）；未读徽标改读 `noticeStore.unreadCount`
- `rookie-ui/src/layout/index.vue` — `openNoticePrompt` 改为从 notice store 取完整正文（`content`）展示详情，打开时调 `markAsRead` 标记已读
- `rookie-ui/src/layout/components/PromptPanel.vue` — notice 模式正文区加 `max-height: 42vh` + `overflow-y: auto` 支持超长正文滚动
- `rookie-ui/src/types/components/theme/index.ts` — `NotificationItem` 扩展 `content`（完整正文）和 `needConfirm` 字段
- `rookie-ui/src/views/system/notice/notice-content/` — 原 `views/system/notice/` 移动并重命名（内容管理）
- `rookie-ui/src/views/system/notice/notice-group/` — 原 `views/system/notice-group/` 移动至此，与 notice-content 平级
- `sql/sys_notice_menu.sql` — 菜单名称更新为"内容管理""分组管理"；`route` 更新为 `notice-content` / `notice-group`；`path` 同步指向新目录；`parent_id` 改为 1（挂到系统模块下）
- `rookie-ui/README.md` — 「项目说明」后新增「项目架构（目录）说明」章节，涵盖顶层文件与 `src/` 各子目录功能、特定名称文件约定

### 15:16 — 通知详情弹窗抽组件 + 枚举改字典系统

- `rookie-ui/src/components/NoticeDetailDialog.vue` — 新建，通知详情只读弹窗，复用 ElDialog（与公共表单弹窗同款遮罩 `--el-overlay-color`，统一背景视觉），ElDescriptions 展示元信息，枚举字段走字典系统翻译，正文区限高滚动
- `rookie-ui/src/layout/index.vue` — 通知详情改用 `NoticeDetailDialog`，移除 PromptPanel notice 模式相关状态（promptMode/promptTitle/promptContent/promptNoticeMeta），保留 markAsRead 即时已读逻辑
- `rookie-ui/src/layout/components/PromptPanel.vue` — 删除（notice 模式移至 NoticeDetailDialog，prompt 模式无消费方）
- `rookie-ui/src/types/components/prompt/index.ts` — 删除（随 PromptPanel 一并清理）
- `rookie-ui/src/layout/components/NavBar/index.vue` — 通知下拉分类标签改走 `resolveDictLabel('sys_notice_type', ...)` 字典翻译，移除内联 NOTICE_TYPE_LABEL 映射
- `rookie-ui/src/views/system/notice/notice-content/config.ts` — noticeType/level/publishScope/status 四个枚举字段移除硬编码 options 与 tagTypeMap，改用 `dictKey`（`sys_notice_type` / `sys_notice_level` / `sys_notice_scope` / `sys_notice_status`），表格自动渲染 DictTag、表单自动渲染字典选项
- `rookie-ui/src/views/system/notice/notice-content/index.vue` — 详情弹窗 `resolveEnumLabel` 改为接收 dictKey 走字典翻译，移除 noticeTypeOptions 等枚举导入，引入 useDict

### 20:51 — 通知正文 Markdown 编辑 + 详情改博客排版

- `rookie-ui/package.json` — 新增依赖 `@kangc/v-md-editor@next`（2.x，Vue 3 版 markdown 编辑器）与 `highlight.js`（代码块高亮，github 主题需显式注入 Hljs 实例）
- `rookie-ui/env.d.ts` — 追加 v-md-editor ambient 类型声明（库 package.json 的 types 字段指向不存在的目录，未真正发布类型）：主入口默认导出是带 `install`/`use`/`lang.use` 的编辑器插件对象（组件名 v-md-editor），`lib/preview.js` 导出带 `install` 的预览插件对象（组件名 v-md-preview），`lib/theme/github.js` 导出带 `install(app, config)` 的主题插件对象（config.Hljs 经 `use(plugin, config)` 透传），`lib/lang/zh-CN` 语言包
- `rookie-ui/src/utils/markdown.ts` — 新建，幂等注册：`VueMarkdownEditor.lang.use('zh-CN', zhCN)` + `VueMarkdownEditor.use(githubTheme, { Hljs: hljs })` 注入 highlight.js，再 `app.use(VueMarkdownEditor)` + `app.use(VueMarkdownPreview)` 全局注册编辑器/预览组件（@next 版无 VMdEditor/VMdPreview 具名导出，必须走 app.use 全局注册）
- `rookie-ui/src/main.ts` — 在 `createApp` 后调 `setupVmdEditor(app)`，按需引入 base-editor / preview / github 主题 + `highlight.js/styles/github.css` 配色
- `rookie-ui/src/types/vue-components.d.ts` — 新建，独立模块声明文件，用 `declare module 'vue'` 增强 `GlobalComponents` 注册 `v-md-editor`/`v-md-preview`（单独成文件避免与 env.d.ts 的 ambient 声明互相干扰，顶层 `import type` 让文件成模块从而正确生效为 augmentation）
- `rookie-ui/src/components/MarkdownEditor.vue` — 新建，直接用 `<v-md-editor>` 全局标签（由 vue-components.d.ts 提供类型），封装为受控组件供公共表单 `inputType:'markdown'` 复用
- `rookie-ui/src/components/MarkdownPreview.vue` — 新建，直接用 `<v-md-preview>` 全局标签，只读展示供通知详情等博客式正文渲染复用
- `rookie-ui/src/types/components/data-display/index.ts` — `SharedFieldInputType` 新增 `'markdown'`
- `rookie-ui/src/components/SharedFormPanel.vue` — dialog 与 panel 两种模式各加 `markdown` 渲染分支（复用 MarkdownEditor），追加 `.shared-form-panel__markdown` 全宽样式
- `rookie-ui/src/views/system/notice/notice-content/config.ts` — 正文 `content` 字段 `inputType` 由 `textarea` 改为 `markdown`，移除 rows 配置
- `rookie-ui/src/components/NoticeDetailDialog.vue` — 排版由 ElDescriptions 改为博客式：大标题 → 元信息标签条（类型/级别/范围/状态走 DictTag 渲染带色 ElTag，置顶/需确认走 flag 标记）→ 信息条（时间/分组/路由）→ MarkdownPreview 渲染正文 → 备注脚注
- `rookie-ui/src/views/system/notice/notice-content/index.vue` — 详情弹窗复用 `NoticeDetailDialog`，移除内联 ElDescriptions/ElDialog 块及随之失效的 useDict/resolveEnumLabel/formatDateTime 引入
- `rookie-ui/src/assets/main.css` — 追加 v-md-editor 主题覆写：编辑器外壳/工具栏/编辑区/预览区背景与文字、代码块/表格/引用/分隔线/行内代码/kbd 统一跟随 `--rookie-*` 主题变量；`[data-theme='dark']` 作用域覆盖 highlight.js 代码块背景为基础深色，深色模式不再白底刺眼；详情弹窗内预览区收紧默认左右大内边距
- `README.md`（根） — 第 12 节主题适配清单"已完成适配区域"补充 v-md-editor 编辑/预览 + highlight.js 代码块相关条目

### 22:40 — v-md-editor 预览崩溃修复 + 公共表单弹窗内容区滚动

- `rookie-ui/src/utils/markdown.ts` — 预览组件 created 读 `themeConfig.markdownParser` 为 undefined 崩溃根因：编辑器与预览是两个独立 vMdParser 实例，主题只 use 到编辑器上；补 `VueMarkdownPreview.use(githubTheme, { Hljs: hljs })` 让预览 parser 也拿到主题配置
- `rookie-ui/env.d.ts` — `lib/preview.js` ambient 声明补 `use` 方法
- `rookie-ui/src/components/SharedFormPanel.vue` — ElDialog 加 `shared-form-dialog` class，追加样式：弹窗 `max-height: calc(100vh - 80px)` 固定最大高、`display:flex` 纵向布局，body `flex:1 + overflow-y:auto` 内容区滚动，header/footer `flex:none` 固定不随内容滚动（长表单如带 markdown 编辑器的通知表单不再把弹窗撑出屏幕）

## 2026-06-22
### 23:05 — 通知详情正文不显示修复 + 详情弹窗字段调整 + 确认按钮 + 发布者

- `rookie-ui/src/components/MarkdownPreview.vue` — 正文不显示根因：v-md-preview 组件的 prop 名是 `text` 而非 `modelValue`，`<v-md-preview :model-value="...">` 传不进去；改为对外收 `modelValue`、对内绑 `:text`，预览区正常渲染
- `rookie-system/src/main/java/com/rookie/system/pojo/vo/SysNoticeVo.java` — 新增 `createBy` 字段及 getter/setter（发布者，BeanUtil.toBean 自动从 SysNotice 复制，my 接口与列表接口均带上）
- `rookie-ui/src/types/api/system/notice.ts` — `SysNoticeRecord` 新增 `createBy?: string`（发布者）
- `rookie-ui/src/types/components/theme/index.ts` — `NotificationItem` 新增 `isTop?: boolean`（下拉项置顶标记）
- `rookie-ui/src/stores/notice.ts` — 新增 `confirmNotice(noticeId)` 方法调 `confirmNoticeApi`，乐观更新本地 `hasConfirmed=true`，已确认则跳过请求
- `rookie-ui/src/components/NoticeDetailDialog.vue` — 详情弹窗字段调整：移除状态 DictTag、跳转路由行、置顶/需确认 flag 标记；新增发布者行；关联分组行改为仅 `publishScope=GROUP` 时展示（整行跨列避免长分组名截断）；新增 footer，`needConfirm=1 且 !hasConfirmed` 时右下角展示"确认"按钮（已确认自动隐藏），点击向父层抛 `confirm` 事件
- `rookie-ui/src/layout/components/NavBar/index.vue` — `toNotificationItem` 映射补 `isTop`；下拉项标题行置顶通知展示"置顶"小标签（与未读小圆点并列）；新增 `.nav-bar__notice-top` 样式
- `rookie-ui/src/layout/index.vue` — 新增 `handleConfirmNotice` 调 `noticeStore.confirmNotice`，`NoticeDetailDialog` 监听 `@confirm` 事件接入

### 23:40 — 公共表单弹窗内部滚动修复（scoped 命不中 teleported 元素）

- `rookie-ui/src/components/SharedFormPanel.vue` — 上轮弹窗滚动样式没生效根因：`ElDialog` 的 `inheritAttrs:false` 把外部 `class="shared-form-dialog"` 经 `$attrs` 透传到 `DialogContent`，与 `el-dialog` 落在**同一元素**（非父子），且 `ElDialog` teleport 到 body，scoped 的 `data-v` 锚点不在 teleported 子树内，所以 scoped `:deep(.el-dialog)` 选择器匹配不到；改为把弹窗滚动样式从 `<style scoped>` 移到单独的非 scoped 全局 `<style>` 块，用同元素并集选择器 `.shared-form-dialog.el-dialog` 命中；`ElDialog` 加 `top="40px"` prop 生成 inline `top` 控制距顶 40px（覆盖默认 `15vh`，避免与 `max-height` 叠加超出底部），`max-height: calc(100vh - 80px)` 保证弹窗永不超过窗口，body `flex:1 + overflow-y:auto` 内容区内部滚动，header/footer 固定

### 23:55 — dict 缓存补刷 + 表单字段精简 + 关联分组按范围显隐 + 通知栏 hover 残留修复

- `rookie-system/src/main/java/com/rookie/system/service/impl/SysDictDataServiceImpl.java` — `addSysDictData` 新增字典数据后未刷新 Redis 缓存，导致通过字典管理页新增的项（如 `sys_notice_status` 的 REVOKED）数据库已有但前端拿不到（`getSysDictDataByDictKey` 优先走缓存命中旧数据），而 `editSysDictDataInfo` 有正确刷新；补 `DictUtil.setDictData` 与编辑对齐，新增后立即刷新缓存
- `rookie-ui/src/types/components/data-display/index.ts` — `SharedFieldSchemaItem` 新增 `visibleWhen?: (model) => boolean` 动态显隐回调，让表单字段可根据当前模型中其他字段值决定是否渲染整个表单项
- `rookie-ui/src/components/SharedFormPanel.vue` — `formFields` computed 在 `formVisible !== false` 后追加 `visibleWhen` 过滤，为关联分组按发布范围动态显隐提供基础
- `rookie-ui/src/views/system/notice/notice-content/config.ts` — 表单精简：删除 `routePath`、`expireTime` 字段配置；`status` 改为 `formVisible: false`（仅表格展示）；`publishTime` 改为 `formVisible: false`（仅表格展示，由发布接口确定）；`groupIds` 加 `visibleWhen: (model) => String(model.publishScope) === 'GROUP'` 全员时隐藏整个表单项；`createDefaultNoticeForm` 移除 `routePath`/`expireTime`/`publishTime`；`noticeFormRules` 移除 status 校验
- `rookie-ui/src/types/api/system/notice.ts` — `SysNoticeRecord` 删除 `routePath`/`expireTime` 字段，补回上轮误删的 `remark`
- `rookie-ui/src/views/system/notice/notice-content/index.vue` — `handleSubmitForm` 去掉 `routePath` 拼接；`field-groupIds` 插槽移除外层 `v-if`/`v-else`（由 `visibleWhen` 统一控制显隐）；移除 `.system-notice-view__hint` 样式
- `rookie-ui/src/components/NoticeDetailDialog.vue` — 移除 `expireTime` 展示行（字段已删除）
- `rookie-ui/src/layout/components/NavBar/index.vue` — 新增非 scoped 全局 `<style>` 块，`.nav-bar-notice-dropdown .el-dropdown-menu__item:not(:hover):not(:focus)` 覆写背景色为 transparent，防止鼠标移出下拉后最后划过的通知项残留 hover/focus 高亮

### 2026-06-23
### 00:10 — 通知表格"已撤回"标签回显修复：前端字典缓存跳过刷新

- `rookie-ui/src/stores/dict.ts` — `initializeDictionaries` 内部调 `fetchDictDataByKey(dictKey, force)` 时 `force` 原值 false：虽然后端 Redis 层已正确刷新，但前端 `loadedKeySet` 从 localStorage 恢复后使 `fetchDictDataByKey` 判定 key 已存在，直接返回旧缓存（缺少 REVOKED 项）而跳过后端请求；改为硬编码 `true`，初始化阶段始终从后端拉取最新字典数据，避免本地缓存导致新增字典项无法进入前端

### 18:40 — 分组成员管理弹窗重做：穿梭框改搜索+分页+标签区

- `rookie-ui/src/views/system/notice/notice-group/components/GroupMemberTransfer.vue` — 废弃 ElTransfer 穿梭框，重写为搜索+分页+标签区形态：顶部字段类型（昵称/用户名/手机号）select + 关键词输入框 + 搜索/重置按钮（复用 `getSysUserPageApi` 现有分页接口，无需后端改动）；中部已选成员 `ElTag` closable 标签区（空态"暂无成员"），`selectedUserMap` 缓存昵称、拿不到兜底 `用户#{userId}`；下部 `ElTable` 用户候选分页表格（昵称/用户名/手机号/状态/操作 5 列，操作列按 `selectedUserIds` Set 切"加入"primary / "已加入"disabled plain）+ `ElPagination`；加入/移除为本地乐观更新，保存时与原始 `props.members` 的 userId 集合做差集分别调 add/remove 接口（逻辑与原穿梭框一致），已选状态跨页跨搜索保持
- `rookie-ui/src/views/system/notice/notice-group/index.vue` — 移除 `userOptions` ref、`fetchUserOptions` 方法（原 `pageSize: 500` 一次性拉全量用户灌穿梭框，用户量大时 DOM 爆炸+全表 like 慢）、`getSysUserPageApi` import；`openMemberManage` 去掉"用户数据源未就绪则预拉"分支，用户候选数据延迟到弹窗内部按搜索条件分页拉取；模板 `<GroupMemberTransfer>` 去掉 `:user-options` prop；文件头与相关注释"穿梭框"改为"成员管理弹窗"
- `rookie-ui/src/assets/main.css` — 删除 `.el-transfer-*` 主题覆写块（穿梭框已废弃，全项目无其他消费方）
- `README.md`（根） — 第 12 节主题适配清单"已完成适配区域"移除 `ElTransfer` 条目
- `rookie-ui/README.md` — 目录说明中 main.css 描述去掉"穿梭框"、notice-group 目录描述"成员穿梭框"改为"成员管理弹窗"

### 19:30 — 分组成员管理改双弹窗结构 + 后端关联补全成员展示字段

- `rookie-system/src/main/java/com/rookie/system/pojo/SysNoticeGroupMember.java` — 新增 `username`/`nickName`/`phoneNumber`/`status` 展示字段（不对应表列，由关联查询填充）及 getter/setter
- `rookie-system/src/main/resources/mapper/system/SysNoticeGroupMemberMapper.xml` — `SysNoticeGroupMemberResultMap` 扩展 username/nickName/phoneNumber/status 映射；`getSysNoticeGroupMemberByGroupId` 改为 `left join sys_user` 返回展示字段
- `rookie-ui/src/types/api/system/notice.ts` — `SysNoticeGroupMemberRecord` 补 `phoneNumber?`/`status?`，注释改为说明展示字段由后端关联填充
- `rookie-ui/src/views/system/notice/notice-group/components/GroupMemberAddDialog.vue` — 新建，"添加成员"子弹窗：字段类型（昵称/用户名/手机号）select + 关键词搜索 + 用户分页表格（昵称/用户名/手机号/状态/操作），行内"加入"按钮向父层抛 `add`，`excludeUserIds` 控制已加入禁用态，`append-to-body` 叠在主弹窗之上，空态 `ElEmpty` 提示换词
- `rookie-ui/src/views/system/notice/notice-group/components/GroupMemberTransfer.vue` — 主弹窗由标签区改为成员表格（昵称/用户名/手机号/状态/移除 5 列）+ 工具栏（人数 + "添加成员"按钮开子弹窗）；`localMembers` 拷贝 props.members 本地乐观增删，`originalUserIds` 记录原始集合，保存时做差集调 add/remove 接口；昵称兜底 `用户#{userId}`；子弹窗 ref 嵌在主弹窗内（destroy-on-close 关闭时一并销毁）

### 20:05 — 分组成员移除改标记态：点移除先标记，保存才统一删除

- `rookie-ui/src/views/system/notice/notice-group/components/GroupMemberTransfer.vue` — 本地成员类型扩展 `pendingRemove` 标记；点"移除"不再从表格删行，改为 toggle `pendingRemove`（行 `is-pending-remove` 类变灰 + 删除线，按钮变"撤销"可恢复）；`handleSave` 差集逻辑改为：未标记移除且非原始 → addMembers，标记 pendingRemove 且原始（有记录 id）→ removeMembers；工具栏人数改读 `activeUserIds.size`（不含待移除），追加"（待移除 N）"提示；`excludeUserIds` 改用 `activeUserIds`，已标记移除的成员可在子弹窗重新"加入"触发撤销标记恢复

### 20:35 — 添加成员子弹窗重置 BUG 修复：重置不再拉全量用户

- `rookie-ui/src/views/system/notice/notice-group/components/GroupMemberAddDialog.vue` — `resetSearch` 原实现清空关键词后又调 `fetchUserPage()`，关键词为空等于无筛选拉全量用户第 1 页（与子弹窗"搜了才展示"的定位相悖）；改为只清空 keyword/searchField 与 `userPageState`（records 置空、分页归位），不调接口；`handleSearch` 加空关键词守卫，空关键词时走 `resetSearch` 清空表格，避免按回车/点搜索时拉全量

## 2026-06-27
### 16:50 — 日志管理模块（操作日志 + 错误日志）后端完整落地

新增操作日志与错误日志两套系统：操作日志通过 `@Log` 注解 + AOP 切面在请求线程同步采集、成功异步落库、失败同步落库拿主键；错误日志通过 `GlobalExceptionHandler` 统一采集请求来源异常，并预留定时任务/异步任务等多来源扩展。两表通过 `sys_error_log.oper_log_id` 关联，操作日志列表对失败行返回 `errorLogId` 供前端跳转。所有枚举字段进字典系统驱动前端标签映射。

- `sql/sys_oper_log.sql` — 新建 sys_oper_log 操作日志表（含 IP/OS/浏览器/设备类型等环境字段，status 用 TINYINT 0/1，不继承 BaseEntity）
- `sql/sys_error_log.sql` — 新建 sys_error_log 错误日志表（只记来源/关联/异常三件套/时间/操作人，HTTP 环境信息归操作日志；含 source_type 区分错误来源）
- `sql/sys_log_dict_init.sql` — 新建 4 个字典初始化脚本：sys_oper_business_type、sys_oper_device_type、sys_oper_status、sys_error_source_type（幂等写法，对齐 sys_dict/sys_dict_data 真实表结构）
- `sql/sys_log_menu_init.sql` — 新建日志管理菜单脚本（menu_id 52~62：一级目录"日志管理" + 操作日志/错误日志两个菜单 + 各 4 个按钮权限，INSERT...ON DUPLICATE KEY UPDATE 幂等）
- `sql/sys_log_menu_admin.sql` — 新建超级管理员（role_id=1）日志菜单授权脚本
- `rookie-common/src/main/java/com/rookie/common/annotation/Log.java` — 新建操作日志注解（title/businessType/isSaveRequestData/isSaveResponseData）
- `rookie-common/src/main/java/com/rookie/common/enums/BusinessType.java` — 新建业务类型枚举（OTHER/INSERT/UPDATE/DELETE/GRANT/EXPORT/IMPORT/CLEAN，code 对齐字典值）
- `rookie-common/src/main/java/com/rookie/common/enums/DeviceType.java` — 新建设备类型枚举（PC/MOBILE/TABLET/UNKNOWN）
- `rookie-common/src/main/java/com/rookie/common/enums/ErrorSourceType.java` — 新建错误来源枚举（REQUEST/SCHEDULED/ASYNC/EVENT/INIT/OTHER）
- `rookie-common/src/main/java/com/rookie/common/pojo/entity/SysOperLog.java` — 新建操作日志实体（不继承 BaseEntity，日志只追加）
- `rookie-common/src/main/java/com/rookie/common/pojo/entity/SysErrorLog.java` — 新建错误日志实体（不继承 BaseEntity，operLogId/operName 可空）
- `rookie-framework/pom.xml` — 新增 spring-boot-starter-aop 依赖（支撑 @Log 切面）
- `rookie-framework/src/main/java/com/rookie/framework/aspectj/LogAspect.java` — 新建操作日志切面，@Around 单切面：请求线程同步采集注解/方法/请求上下文/UA 解析/操作人，成功异步 saveAsync、失败同步 saveAndGetId 拿 oper_id 塞 request attribute 供错误日志关联，异常继续上抛交 GlobalExceptionHandler
- `rookie-framework/src/main/java/com/rookie/framework/config/AsyncConfig.java` — 新建异步配置，logExecutor 线程池（DiscardOldestPolicy 拒绝策略，日志可丢不拖业务）+ AsyncUncaughtExceptionHandler 将 @Async 异常记入错误日志（ASYNC 来源）
- `rookie-framework/src/main/java/com/rookie/framework/filter/RequestCachingFilter.java` — 新建请求体缓存过滤器，ContentCachingRequestWrapper 包装 request 解决 @RequestBody 只能读一次问题，HIGHEST_PRECEDENCE 保证最先执行
- `rookie-framework/src/main/java/com/rookie/framework/service/OperLogService.java` — 新建操作日志服务接口（依赖倒置：framework 定义，system 实现），saveAndGetId 同步拿主键 + saveAsync 异步落库
- `rookie-framework/src/main/java/com/rookie/framework/service/ErrorLogService.java` — 新建错误日志服务接口，统一错误采集落库入口
- `rookie-framework/src/main/java/com/rookie/framework/handle/GlobalExceptionHandler.java` — 增强：新增 recordErrorLog 方法，ServiceException 与 Exception 两个 handler 均异步写错误日志（REQUEST 来源），title 取请求路径，oper_name 从 SecurityContext 取（无登录态置空），oper_log_id 从 request attribute 取关联
- `rookie-system/src/main/java/com/rookie/system/pojo/quarry/OperLogQuarry.java` — 新建操作日志查询条件
- `rookie-system/src/main/java/com/rookie/system/pojo/quarry/ErrorLogQuarry.java` — 新建错误日志查询条件
- `rookie-system/src/main/java/com/rookie/system/pojo/vo/SysOperLogVo.java` — 新建操作日志 VO，含 errorLogId 关联字段供前端跳转
- `rookie-system/src/main/java/com/rookie/system/pojo/vo/SysErrorLogVo.java` — 新建错误日志 VO，含 operLogId 关联字段
- `rookie-system/src/main/java/com/rookie/system/mapper/SysOperLogMapper.java` — 新建操作日志 Mapper（分页查询/新增回写主键/详情/批量删除/清空）
- `rookie-system/src/main/java/com/rookie/system/mapper/SysErrorLogMapper.java` — 新建错误日志 Mapper，含 getErrorLogByOperLogIds 支持操作日志列表两段式关联查询
- `rookie-system/src/main/resources/mapper/system/SysOperLogMapper.xml` — 新建操作日志 Mapper XML，列表直接映射 VO、insert 用 trim+if 动态列、清空用 truncate
- `rookie-system/src/main/resources/mapper/system/SysErrorLogMapper.xml` — 新建错误日志 Mapper XML，getErrorLogByOperLogIds 只取 error_id/oper_log_id 两列
- `rookie-system/src/main/java/com/rookie/system/service/SysOperLogService.java` — 新建操作日志管理服务接口（列表/详情/删除/清空）
- `rookie-system/src/main/java/com/rookie/system/service/SysErrorLogService.java` — 新建错误日志管理服务接口
- `rookie-system/src/main/java/com/rookie/system/service/impl/SysOperLogServiceImpl.java` — 新建，同时实现 SysOperLogService 与 framework 的 OperLogService；quarryOperLog 用两段式查询（操作日志单表分页 + IN 查关联错误日志）拼装 errorLogId，与表体量解耦；saveAsync 标 @Async("logExecutor")
- `rookie-system/src/main/java/com/rookie/system/service/impl/SysErrorLogServiceImpl.java` — 新建，同时实现 SysErrorLogService 与 framework 的 ErrorLogService；saveAsync 标 @Async("logExecutor")
- `rookie-system/src/main/java/com/rookie/system/controller/SysOperLogController.java` — 新建操作日志 Controller（4 个接口，均带 @PreAuthorize 对齐菜单 perm_key）
- `rookie-system/src/main/java/com/rookie/system/controller/SysErrorLogController.java` — 新建错误日志 Controller（4 个接口，均带 @PreAuthorize）
- `rookie-system/src/main/java/com/rookie/system/controller/SysNoticeController.java` — 给 5 个写操作（新增/编辑/删除/发布/撤回）加 @Log 注解，作为操作日志切面的验证接入点
- `doc/api.md` — 接口更新日志追加 2026-06-27 日志管理模块条目；末尾新增"一、操作日志""二、错误日志"两章共 8 个接口的四段式文档
- `doc/devlog.md` — 追加本条开发日志

### 22:36 — 业务接口 @Log 注解全覆盖 + 错误日志链路验证

为全部 7 个业务 Controller 的写操作补齐 @Log 注解（读操作不加，避免日志噪声），共 31 处，使操作日志覆盖系统的增删改类重要业务操作；同时新建临时测试接口验证错误日志采集与操作日志-错误日志关联链路，验证通过后删除。

- `rookie-system/src/main/java/com/rookie/system/controller/SysUserController.java` — 4 个写操作加 @Log：添加用户(INSERT)/编辑用户(UPDATE)/删除用户(DELETE)/更改用户状态(UPDATE)，title 统一"用户管理"
- `rookie-system/src/main/java/com/rookie/system/controller/SysRoleController.java` — 5 个写操作加 @Log：添加角色(INSERT)/编辑角色(UPDATE)/删除角色(DELETE)/更改角色状态(UPDATE)/设置默认角色(GRANT)，title 统一"角色管理"
- `rookie-system/src/main/java/com/rookie/system/controller/SysMenuController.java` — 4 个写操作加 @Log：添加菜单(INSERT)/编辑菜单(UPDATE)/删除菜单(DELETE)/更改菜单状态(UPDATE)，title 统一"菜单管理"
- `rookie-system/src/main/java/com/rookie/system/controller/SysDictController.java` — 3 个写操作加 @Log：添加字典(INSERT)/编辑字典(UPDATE)/删除字典(DELETE)，title 统一"字典管理"（该 Controller 原无 @Operation 注解，仅加 @Log 未补 swagger 注解）
- `rookie-system/src/main/java/com/rookie/system/controller/SysDictDataController.java` — 3 个写操作加 @Log：添加字典数据(INSERT)/编辑字典数据(UPDATE)/删除字典数据(DELETE)，title 统一"字典数据"
- `rookie-system/src/main/java/com/rookie/system/controller/SysNoticeGroupController.java` — 5 个写操作加 @Log：添加分组(INSERT)/编辑分组(UPDATE)/删除分组(DELETE)/添加成员(INSERT)/移除成员(DELETE)，title 统一"通知分组"
- `rookie-system/src/main/java/com/rookie/system/controller/SysLoginController.java` — 2 个写操作加 @Log：登录(OTHER，title"登录管理")/更改个人数据(UPDATE，title"个人信息")；登录接口未认证时切面采集 oper_name 为空属预期（LoginBody 在 oper_param 中可查）
- `rookie-system/src/main/java/com/rookie/system/controller/SysLogTestController.java` — 临时新建错误日志测试接口 GET /sys/logTest/error?type=business|unknown，带 @Log 触发 ServiceException/RuntimeException 验证错误日志落库与 oper_log_id 关联链路；验证通过后已删除（净效果为零，仅作记录）

## 2026-06-28
### 10:40 — 日志管理前端页面（操作日志 + 错误日志）

承接日志管理后端模块，落地操作日志与错误日志两个管理页面，覆盖列表分页、字典驱动筛选、只读详情、批量删除、清空五类操作，并打通「操作日志失败行 → 错误日志详情」「错误日志请求来源 → 操作日志详情」的双向跳转。

- `rookie-ui/src/types/api/system/log.ts` — 新建，定义 SysOperLogRecord / SysErrorLogRecord（对齐后端 SysOperLogVo / SysErrorLogVo，含 errorLogId / operLogId 关联字段）与分页查询参数类型
- `rookie-ui/src/api/system/log.ts` — 新建，封装操作日志 4 接口（list/详情/批量删除/清空）与错误日志 4 接口；批量删除走 `/{ids}` 逗号拼接路径参数对齐后端 @PathVariable Long[]
- `rookie-ui/src/constants/systemPermissions.ts` — 新增 operLog / errorLog 两个权限组（quarry/info/delete/clean，对齐 sys_log_menu_init.sql 的 perm_key）
- `rookie-ui/src/views/system/log/oper-log/config.ts` — 新建，操作日志筛选+表格字段配置；businessType/deviceType/status 均走字典系统（dictKey），requestMethod 为固定取值使用内置选项，无新增编辑表单字段
- `rookie-ui/src/views/system/log/oper-log/index.vue` — 新建操作日志页面：筛选分页、字典标签渲染、只读详情弹窗（ElDescriptions 展示请求参数/返回结果 JSON）、表格多选+批量删除、清空；失败行展示「错误日志」按钮跳转错误日志详情
- `rookie-ui/src/views/system/log/error-log/config.ts` — 新建，错误日志筛选+表格字段配置；sourceType 走字典系统（dictKey），无新增编辑表单字段
- `rookie-ui/src/views/system/log/error-log/index.vue` — 新建错误日志页面：筛选分页、字典标签渲染、只读详情弹窗（ElDescriptions + 完整堆栈 pre 展示）、表格多选+批量删除、清空；支持从操作日志页 query 携带 errorId 自动打开详情，请求来源错误日志详情弹窗内提供「查看操作日志」反向跳转
- `doc/devlog.md` — 追加本条开发日志
- `rookie-system/src/main/java/com/rookie/system/controller/SysLogTestController.java` — 临时新建错误日志测试接口 GET /sys/logTest/error?type=business|unknown，带 @Log 触发 ServiceException/RuntimeException 验证错误日志落库与 oper_log_id 关联链路；验证通过后已删除（净效果为零，仅作记录）

### 14:20 — 日志详情弹窗 UI 优化

承接日志管理前端页面，重构操作日志与错误日志的只读详情弹窗：弃用边框式 ElDescriptions，改用连贯定义表（dl/dt/dd 共享细边线、单容器统一圆角收边）+ 标题栏徽标聚合 + 左侧色条提示条跳转入口 + 可复制代码块，让信息层级克制连贯、深浅模式协调，并统一两个页面的详情视觉语言。

- `rookie-ui/src/views/system/log/components/LogCodeBlock.vue` — 新建，日志详情弹窗复用的代码/长文本块；等宽字体展示 + 限高滚动 + 一键复制（空内容不渲染复制按钮，复制态主色高亮短切反馈）
- `rookie-ui/src/views/system/log/components/LogDetailField.vue` — 过程中临时新建的「标签+值」信息单元，后因独立成盒的碎片感回调改回连贯表格方案，已删除（净效果为零，仅作记录）
- `rookie-ui/src/views/system/log/oper-log/index.vue` — 详情弹窗重构：标题栏聚合模块标题与业务类型/状态徽标；基础信息改用连贯定义表（标签列固定 140px、请求地址/方法等长字段整行等宽横向滚动）；失败操作以左侧色条提示条形式给出「查看错误日志」入口；请求参数/返回结果改用 LogCodeBlock，支持复制
- `rookie-ui/src/views/system/log/error-log/index.vue` — 详情弹窗重构：标题栏聚合错误来源徽标；基础信息/异常类型/异常消息改用连贯定义表；请求来源错误以提示条给出「查看操作日志」入口；完整堆栈改用 LogCodeBlock，支持复制
- `doc/devlog.md` — 追加本条开发日志

### 15:10 — 登录页「记住本次登录」移除与「忘记密码」兜底提示

按 token 后端自带过期时间、前端切换存储范围无意义，以及账号回填本地浏览器存在安全隐患的判断，删除登录页「记住本次登录」复选框；并因后端无任何找回/重置密码接口、无邮件短信通道，将「忘记密码」做成前端兜底提示，避免引导用户进入提交后会报错的自助找回表单。

- `rookie-ui/src/views/login.vue` — 删除 `form.remember` 字段与「记住本次登录」复选框及相关样式；`__options` 容器由两端对齐改为右对齐保留忘记密码按钮原视觉位置；新增 `handleForgotPassword`，点击「忘记密码」弹出 ElMessageBox 提示"本系统暂未开放自助找回密码通道，请联系系统管理员重置密码"
- `doc/devlog.md` — 追加本条开发日志

### 16:00 — 系统首页落地页重构

重构 dashboard 落地页第一个欢迎卡片，并解决「快捷入口 / 模块概览」一栏内容偏多、另一栏偏少的失衡问题。

- `rookie-ui/src/views/dashboard/index.vue` — hero 欢迎区由「左侧文案 + 右排两个散落身份盒」收敛为「问候语（按时段动态生成） + 一行 pill 账户信息」，结构更克制有重心；移除原冗长约落地页定位的描述段；快捷入口不再从菜单再取一遍以避免与模块概览抢同一批项，改为收拢 3 个前端闭环的高频动作（个人中心/刷新概览/退出登录）；模块概览改为展示全部一级模块且不再截断到 6 个，每项带模块图标 + 页面入口数；两栏改用统一「小卡 + auto-fill minmax(160px,1fr) 网格」渲染，项多则多排、项少则少排，密度天然对齐；删除底部「使用提示」冗余文案卡；KPI 概览卡图标开关并补深浅变量一致；`vue-tsc` type-check 通过
- `doc/devlog.md` — 追加本条开发日志

### 16:40 — README 拆分为对外展示版与开发文档版

为兼顾远程仓库对外展示与开发协作，将根目录与 rookie-ui 内的 README 拆为对外精简 README 与开发文档 README.dev.md 两份。

- `README.md` → `README.dev.md`（根）—原 README 经 `git mv` 重命名后，又因 dev 文档不进 git 的约定改用 `git rm --cached -f` 移出索引并加入 `.gitignore`（本地文件保留）；标题改「开发文档」并在文档说明段补充与对外 README 的关系；仓库结构树、doc 整理约定、graphify 阅读顺序里的自指改指向 `README.dev.md`
- `rookie-ui/README.md` → `rookie-ui/README.dev.md`（前端）—同上流程移出 git 索引并加入 `.gitignore`（本地文件保留）；第 12 节自指「本 README」改为「本 `README.dev.md`」
- `.gitignore` —「Local documentation scratch」段下新增 `/README.dev.md` 与 `/rookie-ui/README.dev.md` 两行，使两份 dev 文档被 git 忽略、不进仓库历史
- `README.md`（根，新建·进仓库）—新写对外展示版 README：项目简介、功能特性、技术栈、仓库结构、快速开始（后端编译启动 + 前端 dev/build/type-check）、JWT 鉴权约定（Token 头无 Bearer）、文档指引表指向 README.dev.md 与 rookie-ui/README.dev.md；不含 DB 密码等敏感信息
- `doc/devlog.md` — 追加本条开发日志

### 17:00 — 项目版本统一升级到 1.0.0

将全仓后端 Maven 多模块与前端工程的项目版本从 0.0.1-SNAPSHOT / 0.0.0 统一升到 1.0.0。

- `pom.xml`（根）—`<version>0.0.1-SNAPSHOT</version>` 改为 `1.0.0`；`<rookie.version>0.0.1-SNAPSHOT</rookie.version>` 改为 `1.0.0`（dependencyManagement 用该属性统一管理模块间依赖版本，子模块自动同步）
- `rookie-admin/pom.xml` / `rookie-common/pom.xml` / `rookie-framework/pom.xml` / `rookie-system/pom.xml` — `<parent>` 中 `<version>0.0.1-SNAPSHOT</version>` 改为 `1.0.0`，与根 pom 版本一致以正确解析父项目
- `rookie-ui/package.json` — `version` 由 `0.0.0` 改为 `1.0.0`，与后端项目版本对齐
- `doc/devlog.md` — 追加本条开发日志

- `rookie-system/src/main/java/com/rookie/system/controller/SysLogTestController.java` — 临时新建错误日志测试接口 GET /sys/logTest/error?type=business|unknown，带 @Log 触发 ServiceException/RuntimeException 验证错误日志落库与 oper_log_id 关联链路；验证通过后已删除（净效果为零，仅作记录）

### 17:40 — 后端鉴权补齐：业务接口 @PreAuthorize + admin 直通兜底

此前除 `SysOperLogController`/`SysErrorLogController` 已加 `@PreAuthorize` 外，用户/角色/菜单/字典/字典数据/通知/通知分组 7 个业务 controller 共约 43 个接口无任何鉴权，任意登录用户即可增删改用户、角色、菜单、字典，属严重缺口；同时 `UserDetailServiceImpl` 留有「设置管理员 admin 获取全部权限」的 TODO，admin 全权限仅靠 `*_admin.sql` 逐菜单授权维护、缺兜底。本次补齐 endpoint 级鉴权并实现 admin 直通。

- `rookie-framework/src/main/java/com/rookie/framework/security/mapper/UserInfoMapper.java` + `rookie-framework/src/main/resources/mapper/security/UserInfoMapper.xml` — 新增 `selectAllPermKey()`：查询 `sys_menu` 中启用且未删除的按钮型权限（`perm_key LIKE 'system:%:%'`），供 admin 直通兜底加载
- `rookie-framework/src/main/java/com/rookie/framework/security/service/UserDetailServiceImpl.java` — 移除原 TODO；加载角色后判断是否含启用且 `roleKey=='admin'` 的角色，若是则 `selectAllPermKey()` 全量加载按钮权限（admin 直通），否则走原「角色 → 已启用角色的 menuId → perm_key」常规链路；补注释说明两条加载路径的数据流
- `rookie-system/.../controller/SysUserController.java`（6 接口）、`SysRoleController.java`（7 接口）、`SysMenuController.java`（6 接口）、`SysDictController.java`（5 接口）、`SysDictDataController.java`（5 接口，`/type/{dictKey}` 公共读取不加）、`SysNoticeController.java`（7 接口，`/my`、`/read`、`/confirm` 个人向不加）、`SysNoticeGroupController.java`（7 接口，加/删成员均用 `system:noticeGroup:member`）— 每个方法加 `@PreAuthorize("hasAuthority('system:<module>:<action>')")`，`perm_key` 与 `sys_menu_init.sql`/`sys_notice_menu.sql` 现网数据一致；用户状态接口 DB 无独立 `system:user:status`，复用 `system:user:edit`
- `doc/api.md` — 接口更新日志新增 2026-06-28 一条，说明本次鉴权补齐范围与 401/403 返回约定
- `doc/devlog.md` — 追加本条开发日志

### 21:25 — 抽导数据库为单一初始化脚本 rookie.sql，清理旧 init 脚本

此前 sql/ 目录散落 11 个分场景建表/初始化脚本（菜单、字典、通知、日志各一批 + admin 授权），既与现网库结构易产生漂移、也难一次性落地。本次由当前运行库抽导为单一脚本，保留菜单/字典/角色/用户角色关联等关键数据，业务运行期产生的日志/通知数据只留表结构。

- `sql/rookie.sql`（新建）— 由 `mysqldump --no-data` 出 14 张表 DDL（每表带 `DROP TABLE IF EXISTS`），再以 `--no-create-info --skip-extended-insert` 导出关键数据 INSERT 合并而成；头部含 `CREATE DATABASE IF NOT EXISTS rookie` + `SET FOREIGN_KEY_CHECKS=0/1` 包裹；保留 `sys_menu`(56) / `sys_dict`(11) / `sys_dict_data`(37) / `sys_role`(admin+visitor 2) / `sys_role_menu`(67) / `sys_user`(仅 admin#1 + rookie#2) / `sys_user_role`(仅 (1,1)+(2,5)) 全量数据；`sys_notice` / `sys_notice_group` / `sys_notice_group_member` / `sys_notice_group_rel` / `sys_notice_read` / `sys_oper_log` / `sys_error_log` 七张表只保留表结构不导数据；用户密码为 BCrypt 哈希原样保留
- 验证：sed 替换库名到 `rookie_test_dump` 临时库整脚本执行零报错，校验 14 表 + 关键数据量与预期吻合后删除临时库
- 删除旧的 11 个脚本：`dict.sql` / `sys_error_log.sql` / `sys_log_dict_init.sql` / `sys_log_menu_admin.sql` / `sys_log_menu_init.sql` / `sys_menu_init.sql` / `sys_notice.sql` / `sys_notice_menu.sql` / `sys_notice_menu_admin.sql` / `sys_oper_log.sql` / `sys_role_menu_admin.sql`
- `README.md` / `README.dev.md` —仓库结构树与快速开始段把「sql/ 下脚本」描述改为「单文件 `rookie.sql`：建库 + 14 张表 + 关键数据初始化」
- `doc/api.md` — 鉴权补齐条目中 `sql/sys_menu_init.sql`、`sys_notice_menu.sql` 旧路径引用改为 `sql/rookie.sql` 的 `sys_menu` 现网数据
- `doc/devlog.md` — 追加本条开发日志

> 历史日志条目中提及的旧脚本文件名（如 `sys_menu_init.sql`、`sys_notice_menu_admin.sql` 等）保留原样，作为当时事实记录，不改写