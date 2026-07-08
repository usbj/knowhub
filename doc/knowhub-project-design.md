# knowhub 项目管理模块 设计文档

> 定稿于 2026-07-07。本文件为本地 `doc/` 协作文档（已 gitignore），后续会话可直接查阅。
> 后端实现见 `knowhub/src/main/java/com/knowhub/`（project support + service/controller/mapper/entity/enums）；
> 前端见 `rookie-ui/src/views/knowhub/project/`；SQL 见 `sql/knowhub-project.sql`。

## 1. 定位

项目管理偏向**项目归档记录**（后续可能融入代码版本管理）。记录项目介绍、相关文档、项目代码/安装包等文件存储（文件可下载，相当于开一个文件夹统一管理与项目相关的内容），展示项目负责人/参与者/导师。

严苛权限管理 + 权限分级：
- 查看权限分等级，权限拥有者能看等级及以下的项目内容；无权限者需是项目参与者，且只能看参与的项目
- 下载和更改类似查看：分等级 + 项目内权限双轨
- 权限分**系统权限**（全局、分等级、所有项目）和**项目内权限**（单项目、不分等级、与他项目无关），内容一致
- 项目分等级，与权限对标，权限不够不让看
- 项目类型当前仅比赛项目（练习/运维暂不做，后续加子表 + 字典扩展）

## 2. 权限模型（核心）

```
系统权限（全局·分等级·所有项目）
  knowhub:project:view:l1 / l2 / l3
  knowhub:project:download:l1 / l2 / l3
  knowhub:project:edit:l1 / l2 / l3
  用户等级 userLvl(op) = max(角色勾到的 knowhub:project:{op}:l{N} 里的 N)
  → 能对 所有 level ≤ userLvl 的项目执行 op

项目内权限（单项目·不分等级·与他项目无关）
  project_member 表: member_role + can_view/can_download/can_edit

判定公式（用户 U 对项目 P、操作 op∈{view,download,edit}）:
  userLvl(op) >= P.level → true
  member = findMember(P, U)
  member == null → false
  member.role == 'LEADER' → true          // 负责人强制全权,不看标志位
  member.can_{op} == 1 → true
  否则 → false

delete 单独: member.role == 'LEADER' 或 hasPerm('knowhub:project:delete')
```

### 2.1 权限等级获取 — ProjectPermissionResolver

`com.knowhub.project.support.ProjectPermissionResolver`，一次扫描 `UserInfo.getPermissions()`（`List<Permission>`）取 view/download/edit 三操作各自最高等级。**不是三个接口，是一个方法**。

关键事实（基于真实 API）：
- knowhub **没有** `SecurityUtils`/`LoginUser`；各 service 各自 `private UserInfo currentUser()` 从 `SecurityContextHolder` 取 `UserInfo`（com.rookie.framework.security.pojo.UserInfo）
- `getPermissions()` 返回 `List<Permission>`（不是 `Set<String>`），`Permission` 只有 `permKey` 字段
- **admin 零特判**：rookie 登录时 admin 角色已把 sys_menu 全部启用按钮 perm_key 物理塞入 List（见 `UserDetailServiceImpl.selectAllPermKey`），其中含 view:l1/l2/l3 三条，扫完 `Math.max` 自然得 3/3/3，无需 isAdmin（UserInfo 也没暴露）
- **同时持有 l1+l2 都能获取**：循环里 `Math.max` 累积，两条 perm 都在 List 里，最终取最高不覆盖
- 规避现有 `BlogServiceImpl`/`ResourceServiceImpl` 里 `List<Permission>.contains(String)` 永远 false 的隐坑（直接遍历取 permKey，不转 Set 省构造）

正则 `^knowhub:project:(view|download|edit):l([1-3])$` 一次匹配三操作，返回 `ProjectPermissionLevel(view, download, edit)` record。service 入口调一次，后续 `lvl.view()` 透传 Mapper SQL、`lvl.levelOf("download")` 详情校验。

### 2.2 性能

纯内存计算（正则+取 max），无 IO/锁/Redis 调用（perms 随 UserInfo 已在请求上下文）。n=100–200 perms（普通用户 10–50，admin 全量 ~100–200），单次 ~0.1–0.6ms。相比后续 DB IO（列表分页/详情 join/文件树查询 5–50ms）可忽略，一次请求一次 resolve 不随数据行数放大，**完全可接受，不需要缓存**。

## 3. 表结构

### 3.1 `project` — 主表

公共字段 + `level`(等级,对标权限) + `type`(类型,字典驱动:COMPETITION/PRACTICE/OPS 三种) + `status`(状态机) + `author_id`(=LEADER userId) + 审核状态机。审核快照全在 `project_review_log` 流水表。`description` 走 mediumtext，列表不带。`article_id` 关联文章管理模块（**非必填，TODO: 文章管理模块开发时关联**）。

> **关于类型子表**：原设计为每种类型建子表存特有字段，用户澄清"子表的东西主表都可以覆盖或者不需要这些属性"——PRACTICE/OPS 不再建子表，所需属性由主表 `description`/`summary` + 项目文件覆盖。`project_competition` 比赛子表已建（存比赛名/获奖等级/比赛/获奖时间等比赛特有字段），保留；后续若某类型确有不可被主表覆盖的特有字段再加子表，主表不动。`project_type` 字典已补齐三种（见 §4）。

### 3.2 `project_competition` — 比赛子表（1:1，主键兼外键）

只存比赛特有字段：competition_name / competition_level / award_level / award_time / competition_time。团队名单由 project_member 承载不重复。不继承审计列不软删——随主表。PRACTICE/OPS 无子表（主表字段+项目文件已覆盖所需属性）。

### 3.3 `project_member` — 团队名单 + 项目内权限

member_role(LEADER/MENTOR/MEMBER) + can_view/can_download/can_edit。LEADER 判定时全权不看标志位，每项目仅一个 LEADER（service 层事务校验）。换负责人 = 同步更新 `project.author_id` + member LEADER 行（事务保证一致）。角色默认标志位：LEADER→1/1/1、MENTOR→1/1/0、MEMBER→1/0/0，可微调。

### 3.4 `project_review_log` — 审核流水

结构与 `resource_review_log` 完全同构，`resource_id` 换 `project_id`。action/role 复用 `ReviewAction` 枚举 + `review_action` 字典(dict_id=25，博客/资源/项目共用)，不建新字典。不继承 BaseEntity（流水无审计列，只追加不改不删）。

### 3.5 `project_file` — 项目文件树（支撑 GitHub 式侧边栏）

目录骨架 + 叶子指向 file_object。is_dir=1 目录(object_id=null)/is_dir=0 文件(关联 file_object)。一个项目按 project_id 拉全树，前端内存组装 parent→children 递归渲染（展开/折叠）。

**与 file_object 分工**：file_object=对象存储元数据(扁平,对接 RustFS)；project_file=项目内目录树骨架,叶子 object_id 指向 file_object。文件本体复用 file_object（business_type ∈ PROJECT_SRC/PKG/DOC 已预留，biz_ref_id=project_id）。删项目事务内级联软删 project_file + fileService.softDeleteByBizRef 三类。

### 3.6 表关系

```
project (主表·level·type·author_id=负责人)
 ├─ project_competition  (1:1 按 type=COMPETITION 取)
 ├─ project_member (N·LEADER/MENTOR/MEMBER + can_*)  ← join sys_user 取 nick_name
 ├─ project_review_log (N·审核流水,复用 ReviewAction)
 └─ project_file (N·目录树) → file_object (N·对象元数据,business_type ∈ PROJECT_SRC/PKG/DOC)
```

## 4. 字典 & 菜单 & 配置

### 字典（dict_id 从 26 起；原脚本 dict_data 108-120，补丁脚本补 121-122）

| dict_id | dict_key | dict_data | 值 |
|---|---|---|---|
| 26 | project_type | 108 / 121 / 122 | COMPETITION 比赛项目 / PRACTICE 练习项目 / OPS 运维项目 |
| 27 | project_status | 109-114 | DRAFT/PUBLISHED/REVOKED/PENDING_REVIEW/REJECTED/ARCHIVED |
| 28 | project_level | 115-117 | 1 公开 / 2 内部 / 3 机密 |
| 29 | project_member_role | 118-120 | LEADER / MENTOR / MEMBER |

> `project_type` 原脚本只放了 COMPETITION，用户要求补齐三种 → `sql/knowhub-project-patch.sql` 追加 PRACTICE/OPS（dict_data 121/122），不改原 SQL、不动已建表。PRACTICE/OPS 无子表，所需属性由主表 description/summary + 项目文件覆盖。

`review_action`(dict_id=25)/`review_status`(dict_id=14) 复用已有，不新建。

### 菜单（menu_id 117–135，共 19 条；MAX(menu_id)=116）

117 项目管理页 / 118-122 quarry·info·add·delete·member / 123-126 publish·revoke·review·reviewLog / 127-129 view:l1-l3 / 130-132 download:l1-l3 / 133-135 edit:l1-l3。

### sys_config

config_id=9 `knowhub.project.review_enabled`=true（**项目审核默认开启**，与资源默认关闭不同——用户明确要求加审核）。对账间隔走 yml `knowhub.project.reconcile-interval-minutes: 5`（@Scheduled 注解在 Bean 创建时解析，读不了 sys_config Redis 缓存）。

## 5. 整体流程

- **创建**：有 `knowhub:project:add` → 新增弹窗只填项目信息（title/type/level/summary/description）→ 提交创建拿 projectId → 自动切编辑态解锁"团队成员/项目文件"标签页 → 在弹窗内继续加成员、上传文件
- **查看**：列表 SQL 透传 userViewLevel/userId，`level<=userViewLevel OR project_id IN (member can_view=1 子查询)`；详情弹窗**只读**（不承担改数据职责），仅展示信息+下载（下载属查看行为）
- **下载**：canOp(download) 校验 → 下发链接（中转 /file/proxy/{objectId} 或预签名）；文件树叶子点击下载
- **更改**：编辑弹窗内操作，canOp(edit) 或 LEADER；改 level 需自身 edit 等级 >= 新 level（防降级再让别人改）
- **删除**：LEADER 或 `knowhub:project:delete`；事务级联 member + project_file + file_object 三类 softDeleteByBizRef，对象本体 FileGcTask 回收
- **成员管理**（参考通知分组 UX）：编辑弹窗"团队成员"页 → "添加成员"打开搜用户子弹窗（昵称/用户名/手机号搜索，调 `GET /sys/user/list`）→ 行内"加入"调 `POST /project/member/batch/{projectId}` 批量加（默认 MEMBER 角色，已存在跳过）→ 主表格行内"编辑"改角色/权限标志位（单点 `PUT /project/member`）、"删除"（LEADER 不可直接删）。LEADER 唯一性 + 换负责人同步 author_id（事务）
- **文件管理**：编辑弹窗"项目文件"页 = GitHub 式文件树；canOp(edit) 可新建文件夹/上传/重命名/删除；上传走 presignedUploadFlow（PROJECT_SRC/PKG/DOC）→ addProjectFileNode 挂树 + bindBizRef
- **审核**：审核弹窗**只给通过/驳回+意见**（不展示其它数据、不承担改数据职责）；publish 经审核开关决定 PENDING_REVIEW 或 PUBLISHED；review 校验状态+回避（author_id 比对，负责人不能审自己）；对账任务在开关关闭+标记存在时批量放行遗留待审项目

> **弹窗职责分离**（用户明确要求）：新增/编辑弹窗 = 改数据（项目信息+成员+文件）；详情弹窗 = 只读查看；审核弹窗 = 只给审核结果。详情和审核不承担改数据职责。

## 6. 复用与扩展点

**复用**：file_object（PROJECT_SRC/PKG/DOC 三类 business_type 已预留）；ReviewAction 枚举 + review_action 字典；审核范式（状态机+回避+流水表+对账任务，照资源模块抄）；用户搜索接口 `GET /sys/user/list`（复用 rookie，添加成员子弹窗用，无需新建）。

**扩展点**：
- 后续某项目类型确有不可被主表覆盖的特有字段：加子表 + 补 project_type 字典 + 前端表单配置，主表不动
- 代码版本管理融入：`project` 加 `repo_url` 或新建 `project_repo` 子表（多仓库），不影响现有结构
- article_id 关联文章管理模块（文章管理待开发，主表已预留非必填字段，开发时关联）

## 7. 遵守约定

未修改任何 `rookie-*` 模块代码（application.yml 仅追加 `knowhub.project` 配置段，属已解除禁令的追加）；新模块产物全在 knowhub 模块内（com.knowhub.* 同包）+ rookie-ui knowhub 二开目录；不新建 Maven 模块；编号续编前已查实际数据库 MAX(menu_id)=116/MAX(dict_id)=25/MAX(dict_data)=107，避开已占段。
