# knowhub-ui 开发文档

本文件面向后续在 `knowhub-ui`（前台用户端）上的开发者与协作者，沉淀协作约定、技术栈、鉴权链路与 UI 抄写规则。读取顺序建议：根 [`README.dev.md`](../README.dev.md) → 本文件 → 具体代码。

## 1. 定位

- `knowhub-ui` 是 knowhub 的**前台用户端**（面向访客/登录用户的展示站），与后台管理端 `rookie-ui` 同仓并存但工程独立。
- 后台 `rookie-ui` 已成熟（鉴权/动态菜单/字典/通知/系统配置全链路），前台处于**从 demo mock 逐步接后端**的过渡阶段。
- 端口固定 `5174`，避开后台 `rookie-ui` 的默认 `5173`（见 `vite.config.ts`）。

## 2. 技术栈

- Vue 3 + TypeScript（严格模式：`tsconfig.app.json` 开 `noUncheckedIndexedAccess`，数组/对象索引取值需留意可能为 `undefined`）
- Vite（当前为 v8/rolldown 构建）
- Pinia（状态管理）
- Vue Router
- Element Plus（全量注册，见 `main.ts`）
- axios（HTTP）
- @kangc/v-md-editor + highlight.js（Markdown 编辑/预览，与后台同款，见 §6）

> 前端依赖要求 Node `^22.18.0 || >=24.12.0`（见 `package.json` engines）。

## 2.5 本地启动与验证约定

**启动命令**（在 `knowhub-ui/` 目录下）：

| 用途 | 命令 | 说明 |
|---|---|---|
| 开发服务器 | `npm run dev` | Vite dev，端口固定 **5174**（见 `vite.config.ts`，避开后台 `rookie-ui` 的 5173） |
| 类型检查 | `npm run type-check` | `vue-tsc --build`，无副作用，改完 TS 必跑 |
| 生产构建 | `npm run build` | 出 `dist/` |

- **dev 代理**：`/api` → `localhost:8080`（后端，去前缀），见 `vite.config.ts`。前台单独跑只验证页面结构/路由/样式时后端可不起；验证真实接口需后端 + MySQL + Redis 就绪。
- **鉴权**：公开页（首页/博客/笔记/资源/文档/公告）不拦截；受保护页（`/profile`、`/history` 等带 `meta.requiresAuth`）无 token 跳 `/login?redirect=`。

**验证约定（重要，照根 `README.dev.md` §11 口径）**：

- **不要自己拉 dev server 进预览面板**做页面验证。需要看真实表现时，**把改了什么、去哪个路由看、预期表现是什么告诉用户，由用户本地启动并确认结果**。
- 静态校验（`npm run type-check` / `npm run build`、编译、类型检查这类本地无副作用检查）**可以自行运行**作为自查，不属于"启 preview 跑页面"范畴。
- 改完后自查清单：①`npm run type-check` 通过；②涉及新接口的页面，对照后端 VO 字段核对 mock 私加字段是否已清理（见 §11.1）；③teleported 浮层/深色模式按根 `README.dev.md` §12 检查。

## 3. 鉴权与 HTTP 链路

参考后台 `rookie-ui` 的写法，前台从简（不引字典/系统配置/动态菜单）。

- **vite 代理**：dev 下 `/api` → `localhost:8080`（去前缀），见 `vite.config.ts`；生产由 `VITE_API_BASE_URL` 或 nginx 转发。
- **http 工具**：[`src/utils/http.ts`](src/utils/http.ts)
  - `baseURL = VITE_API_BASE_URL || '/api'`，`Token` 请求头注入 JWT（无 Bearer 前缀，后端 `TokenVerifyFilter` 读取）。
  - 响应拦截：后端 `Result{code,msg,data}`，`code !== 200` 弹 `ElMessage.error(msg)`；`code === 401` 或 HTTP 401 视为登录态失效，跳 `/login?redirect=` 并清本地态。
  - **登录请求带 `skipAuthRedirect`**：登录页密码错误时后端返回 401"用户名或密码错误"，此标记阻止拦截器对 401 做"跳登录页"处理（否则登录页被无意义刷新），只弹后端真实原因。
  - localStorage key：`knowhub-user-token` / `knowhub-user-info`（与后台 `rookie-` 前缀区分）。
- **user store**：[`src/stores/user.ts`](src/stores/user.ts) — `token` / `userInfo` / `isAuthenticated` / `displayName`(nickName‖username) / `avatarText`(首字) + `setLoginSession` / `fetchUserProfile`(GET /person) / `logout`(清态并 `resetNoticeState`)。
- **登录流程**：[`src/views/auth/Login.vue`](src/views/auth/Login.vue) — `loginApi`(POST /login) 写 token → `fetchUserProfile` 拉 /person → 顺带 `fetchMyNotices` → 按 `redirect` 回源。
- **路由守卫**：[`src/router/index.ts`](src/router/index.ts) — 仅 `/profile` 标 `requiresAuth`；公开页（首页/博客/项目/资源/文档/笔记/公告）不拦截；已登录进 `/login`、`/register` 回首页；受保护页恢复登录态时惰性 `fetchUserProfile` + `fetchMyNotices`。
- **后端配合**：登录失败的真实原因由后端 `GlobalExceptionHandler` 的 `AuthenticationException` 分支映射（见后端 `rookie-framework` `GlobalExceptionHandler`），前台 http 现逻辑直接展示后端 `msg`。

## 4. 公告（通知）

- **store/api/types**：[`src/stores/notice.ts`](src/stores/notice.ts) / [`src/api/system/notice.ts`](src/api/system/notice.ts) / [`src/types/api/notice.ts`](src/types/api/notice.ts)，与后台 `rookie-ui` 同源（`/sys/notice/my` + `/sys/notice/read/{id}`），前台从简：不接 `needConfirm` 确认弹窗交互、不接管理端 CRUD/分组。
- **顶栏下拉**：[`src/components/layout/AppHeader.vue`](src/components/layout/AppHeader.vue) 已登录显示铃铛 + 未读徽标 + 下拉列表（参考后台 `rookie-ui/src/layout/components/NavBar/index.vue` 的 `ElDropdown + ElDropdownMenu + ElDropdownItem` 形态，前台版用 `kh-*` 主题变量重写样式）。
  - 通知类型字典 `sys_notice_type`：`NOTICE/NOTIFY/REMIND`（公告/通知/提醒），前台做内联映射（不引字典缓存），见 AppHeader `noticeTypeMap`。
  - 点击公告项：`markAsRead`（乐观） + 跳 `/notices`。
  - **未登录隐藏公告铃铛**（前台公告对未登录访客暂未规划公开接口）。
- **首页公告轮播 / `/notices` 列表**：仍用 `src/mock/notice.ts`。待后端补"面向访客的公开公告接口"后再接（mock 文件首部注释已标记此缺口）。

## 5. 字典

照搬后台 `rookie-ui/src/stores/dict.ts` 的实现，仅 localStorage key 前缀改 `knowhub-`。

- **store/api/types**：[`src/stores/dict.ts`](src/stores/dict.ts) / [`src/api/system/dict.ts`](src/api/system/dict.ts) / [`src/types/api/dict.ts`](src/types/api/dict.ts)
  - 读取接口：`getSysDictAllApi`（GET /sys/dict/all，全量启用字典类型）、`getSysDictDataByTypeApi`（GET /sys/dist/data/type/{dictKey}，按 key 取数据项）。**注意后端字典数据路径拼写为 `/sys/dist/data`（历史 typo），前端必须与之对齐**，勿自作主张改成 `dict`。
  - 前台只接读取场景，字典类型/数据项的 CRUD 与分页（管理端用）暂未引入，按需再扩。
- **用法**：
  - 登录后预加载：`useDictStore().initializeDictionaries()`，在守卫恢复登录态分支与 Login.vue 登录成功后各调用一次（失败不阻塞），与后台守卫首屏初始化口径一致。
  - 把字典 code 翻成中文标签：`useDictStore().getDictLabel('sys_notice_type', noticeType)`（单值返回 string，数组返回 string[]）。
  - 渲染下拉：`getDictOptions('sys_xxx')` → `{label,value}[]` 喂 ElSelect；需要数字值传 `'number'`。
  - 标签样式：`normalizeTagType`/`normalizeTagEffect` 把后端 `tagType`/`tagEffect` 归一化成合法的 ElTag `type`/`effect`，非法值兜底 `info`/`plain`。
- **缓存语义**：内存按 `dictKey` 分组 + localStorage `knowhub-dict-cache` 持久化；`initializeDictionaries` 恒走 `force=true` 从后端拉最新（避免新增字典项被本地缓存挡住）。退出登录由 `userStore.logout` 调 `clearDictCache` 清内存与本地。
- **何时用字典 vs 内联映射**：AppHeader 公告通知类型当前是内联 `noticeTypeMap`（前台公告首轮接后端时字典尚未预加载完成，内联兜底更稳）；其余正式业务字段（博客状态/资源类别等）应优先走 `getDictLabel`，等字典 store 预加载完成即可统一翻译，避免硬编码。

## 6. 系统设置

照搬后台 `rookie-ui/src/stores/system-config.ts`，仅 key 前缀改 `knowhub-`。与字典的「全量预加载」不同：系统设置可能含不宜整体暴露的关键信息，故按 `configKey` **单项异步拉取**，内存只缓存「已请求过的 key 的值」，未请求的 key 不进缓存、不会被下发。

- **store/api/types**：[`src/stores/system-config.ts`](src/stores/system-config.ts) / [`src/api/system/system-config.ts`](src/api/system/system-config.ts) / [`src/types/api/system-config.ts`](src/types/api/system-config.ts)
  - 只接按 key 取值：`getSysConfigValueApi`（GET /sys/system-config/configKey/{configKey}），命中返回值字符串，未命中/停用返回 `null`。
  - CRUD/分页/刷新缓存（管理端用）暂未引入。
- **用法**：在需要读取全局开关/配置的页面，`const v = await useSysConfigStore().fetchSysConfig('xxx_key')`；命中缓存复用，`force=true` 绕过缓存重拉。null 也会被缓存以避免重复请求未命中项。
- **缓存语义**：内存 `configMap` + localStorage `knowhub-system-config-cache`；退出登录由 `userStore.logout` 调 `clearSysConfigCache`。后台系统设置后端缓存在 Redis（`SysConfigUtil`），刷新走后端接口；前端只做按需取值缓存，不主动刷后端缓存。

## 7. 时间工具

[`src/utils/format.ts`](src/utils/format.ts) 照搬后台 `rookie-ui/src/utils/format.ts` 的 `formatDateTime` / `formatDate`：后端 Date 经 jackson 输出 `yyyy-MM-dd HH:mm:ss`，前端统一走这里；时分秒全 0 视为只记录到日，只返回 `YYYY-MM-DD`。新增页面展示时间一律用这两个工具，不要手写格式化。

## 8. Markdown 编辑/预览

- 依赖与后台同款：`@kangc/v-md-editor@^2.3.18` + `highlight.js@^11.11.1`。
- 注册：[`src/utils/markdown.ts`](src/utils/markdown.ts) `setupVmdEditor(app)`，在 `main.ts` 启动时调用一次；样式 import 见 `main.ts`。
- 页面直接用 `<v-md-editor>`（编辑）/ `<v-md-preview>`（预览）全局组件。
- **深色模式覆盖暂未做**：前台主题体系（design-tokens.css）目前未建立明暗切换；v-md-editor 的 github 主题是固定浅色。前台后续若引入明暗主题，需同步补 v-md-editor 深色覆盖（见根 `README.dev.md` §12 主题适配清单的 v-md-editor 条目，可照抄后台 `rookie-ui/src/assets/main.css` 的 `.v-md-editor` 深色覆写）。

## 9. 写 UI 时的抄写约定（重要）

**写前台 UI 时，若后台 `rookie-ui` 已有类似展示组件，优先抄过来用，再按前台主题变量（`--kh-*`）适配，不要从零另造。** 后台组件在交互细节、无障碍、边界处理上已踩过坑，复用能保持两套前端的认知一致性。

落地要点：

1. **先在 `rookie-ui/src` 搜同类组件**（`views/` 下的业务页、`layout/components/` 下的顶栏部件、`components/` 下的公用组件），找到对应实现后再动手。
2. **组件结构/交互逻辑照搬，样式从 `--rookie-*` 改为 `--kh-*`**（前后台主题变量前缀不同，见各自 `assets/` 下的 css）。
3. **teleported 浮层（dropdown/popover/tooltip/select）用全局 `<style>` 块 + `popper-class` 覆写**，scoped 命不中（参考本仓库 AppHeader 公告下拉的 `kh-notice-dropdown` 全局块写法，对照后台 NavBar 的 `nav-bar-notice-dropdown`）。
4. **能复用后台同款依赖就复用**：v-md-editor、highlight.js、format.ts、Element Plus 控件用法均与后台保持一致，不引入同类新库。
5. 抄过来后在本文件对应章节补一句"参考后台 `rookie-ui/...` 的 xxx 实现"，方便后续追溯。

## 10. 与根文档的关系

- 主题适配统一清单见根 [`README.dev.md`](../README.dev.md) §12，前台新增组件时同样按该清单检查（深浅模式、teleported 浮层、滚动条、空状态等）。前台主题体系建立前，至少保证浅色模式下不出现 Element Plus 默认白底残留。
- 协作约定（范围控制/尊重已有改动/修改前沟通）见根 `README.dev.md` §11，同样适用于本目录。
- 后端接口契约、`Token` 头、`Result` 结构见根 `README.dev.md` §5；前后端时间字段类型约定见 §5 末尾的"VO/实体时间类"说明。

## 11. 与后端 VO 对齐约定（重要）

**所有要展示数据内容的地方以后端的 VO 为准，不要在 mock/类型里新加后端 VO 没有的数据属性。** 就算从逻辑上认为"加这个字段展示更完善"，也**不要静默混进数据契约**——若确需补充，必须在该字段定义处用注释明确标记"`【前端私加】`：后端 VO 无此字段，mock 占位，接口接入前需推动后端补 VO 或在前端自算"，并在 `doc/目前已识别的缺口…md` 补一条缺口，不能让它看起来像后端已有的字段。

判定"是否私加"以最新版 `doc/knowhub-api.md` 各接口响应示例 / VO 字段表为准：
- 接入某模块前先翻 `knowhub-api.md` 对应章节，把 mock 字段逐个核对到 VO 字段；对不上的标 `【前端私加】`。
- 一种常见的私加陷阱：**展示字段名与后端 VO 名相近但语义不同**（如 mock `author` vs 后端 `createBy`(username) / `authorNickname`），接入时要按语义映射、不要只看名字。
- 另一种常见陷阱：**mock 字段是计数，后端 VO 没有这个计数**（如项目卡展示下载量/活跃度，BlogVo 展示评分），接入时要在缺口文档登记，由后端决定补字段 or 前端用既有字段推算。
- 前台 mock 现存私加字段见下节清单，接入对应模块时逐项处理。

### 11.1 当前 mock 私加字段清单（接入后端前对照处理）

> 以下字段是 mock 阶段为前端展示自行添加的，后端对应模块 VO 当前**没有**这些字段，逐项在 `doc/目前已识别的缺口…md` 登记为缺口：

| Mock 字段 | 位置 | 后端 VO 现状（截至 2026-07-13） | 处理方向 |
|---|---|---|---|
| `MockProject.cover` / `icon` | [mock/project.ts](src/mock/project.ts) | `ProjectVo` 无封面/图标字段 | 项目无封面是既定事实；`cover`/`icon` 不应进 VO，后续接入列表展示用类型/等级徽标 + 标题为主（已与 ProjectCard 去封面改动一致）|
| `MockProject.rating` / `downloadCount` | 同上 | `ProjectVo` 无评分/下载量字段（counts 也无 downloadCount） | 缺口已登记（项目活跃度/下载量排序接口 + 评分接口），接入时由后端决定是否补计数字段。原 `activity` 已从 mock 移除——前端"最活跃/按活跃度排序"改用 `downloadCount` 排序替代，活跃度综合分不进 VO |
| `MockProjectFile.uploadTime` | 同上 | `ProjectFileTreeVo` 仅 contentLength/contentType/businessType，**无时间字段** | 文件页"上传时间"列缺后端支撑，已登记为缺口；前端要么等后端补 `createTime` 到 VO，要么暂用 project_file/file_object 的 create_time（需后端 join） |
| `MockBlog.rating` | [mock/blog.ts](src/mock/blog.ts) | `BlogVo` 无 rating 字段（counts 只有 viewCount/likeCount/collectCount） | 评分缺后端接口，已登记为缺口 |
| `MockBlog.authorNickname` | 同上 | `BlogVo` 仅有 `createBy`(username)，无 join 出的昵称 | mock 已按目标 VO 字段名 `authorNickname` 占位（**前端私加**，后端未补），接入时推动后端 join sys_user 回填 authorNickname；项目/文章 VO 都已 join，博客未 join 是缺口 |
| 各 view 的内联映射（如 AppHeader noticeTypeMap） | 多处 | 后端有对应字典（`sys_notice_type` 等） | 不是私加数据；待字典预加载稳定后切到 `getDictLabel`，见 §5 |

新增 mock 字段时务必同步补这表 + 缺口登记，避免"看起来是后端字段"的静默私加污染数据契约。

## 12. 当前已接后端清单

- `POST /login` — 登录（返回 token）
- `GET /person` — 当前登录用户资料
- `GET /sys/notice/my` — 当前用户可见通知
- `POST /sys/notice/read/{noticeId}` — 标记已读
- `GET /sys/dict/all` — 全量启用字典类型（字典预加载用）
- `GET /sys/dist/data/type/{dictKey}` — 按 key 取字典数据项
- `GET /sys/system-config/configKey/{configKey}` — 按 key 取系统设置值

其余页面（博客/项目/资源/文档/笔记/首页）仍消费 `src/mock/*`，按后端模块接口就绪后逐步替换。