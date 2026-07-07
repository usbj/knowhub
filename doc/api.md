# API 接口文档

**更新日志格式约定：**
- 每次接口更新在「接口更新日志」章节追记一条，一条日志推荐覆盖 5–8 个接口或一个模块的完整变更
- 不满记录门限时可先将变更记在中间暂存区，满足后再合并为正式日志记录
- 单条日志描述本次变更涉及的范围、接口清单和简要说明
- 此约定后续可能取消或调整

**文档格式约定：**
- 每个接口统一划分为四个子节：基本信息、请求头、请求体（含参数说明）、响应示例
- 若无请求头/请求体，写"无"
- 参数说明统一使用表格；若请求体为类接收，参数表格即该类属性的描述；若含嵌套类，在下方另起一表

---

## 接口更新日志

### 2026-07-07 — 系统设置模块补充前端加载接口

新增 `GET /sys/system-config/list-all` 接口（公共读取，需登录即可，不加按钮权限），返回全部启用设置项，供前端登录后全量加载到内存缓存（对标字典启动加载）。

### 2026-07-03 — 系统设置模块（system_config）

本批次新增系统设置模块的完整接口，包括分页查询、详情、新增、编辑、删除、刷新缓存。

- 系统设置（6 个）

系统设置为键值型，值类型由 `value_type` 标识（STRING/BOOLEAN/NUMBER/JSON）。数据库为唯一源，Redis 为永久缓存副本（key 前缀 `sys_config:`），工具类 `SysConfigUtil` 只读缓存、不走数据库，启动时由 `SysConfigWarmUpRunner` 预热。内置项（`is_system=1`）受保护：禁止删除、禁止修改 `configKey` 与 `valueType`、禁止停用，仅可改值/名称/备注。「刷新缓存」接口清空后立即从数据库重新预热全部启用项。所有接口均经 `@PreAuthorize` 鉴权，权限 key 与 `sql/sys_config.sql` 中的按钮权限对齐（`system:systemConfig:*`）。

### 2026-06-28 — 后端鉴权补齐：业务接口 @PreAuthorize + admin 直通兜底

本批次给此前仅有 `@PreAuthorize` 的日志模块之外的 7 个业务 controller 全量补齐 endpoint 级鉴权，并在权限加载层实现 admin 直通兜底。

- 用户管理（6 个）、角色管理（7 个）、菜单管理（6 个）、字典管理（5 个）、字典数据（5 个）、消息通知（7 个）、通知分组（7 个）

合计 43 个接口加注 `@PreAuthorize("hasAuthority('system:<module>:<action>')")`，`perm_key` 与 `sql/rookie.sql` 中 `sys_menu` 现网数据一致（字典数据为 `system:dictData:*`）。个接口（`GET /sys/dist/data/type/{dictKey}` 字典公共读取、`/sys/notice/my`、`/read`、`/confirm` 个人向）保持仅需登录、不加按钮权限。

鉴权不通过时由 `AccessDeniedHandlerImpl` 统一返回 `{code:403, msg:"请求访问：<uri>，但没有权限，无法访问系统资源"；未登录访问由 `AuthenticationEntryPointImpl` 返回 `{code:401, msg:"...认证失败..."}`。超级管理员（`sys_role.role_key='admin'`）登录时在 `UserDetailServiceImpl` 中直通加载 `sys_menu` 全部按钮权限作为兜底，不再单纯依赖 `*_admin.sql` 逐菜单授权。

### 2026-06-27 — 日志管理模块（操作日志 + 错误日志）

本批次新增日志管理模块的完整接口，包括操作日志与错误日志的列表、详情、批量删除、清空。

- 操作日志（4 个）
- 错误日志（4 个）

合计 8 个接口。

操作日志列表对失败行返回关联的 `errorLogId`，供前端"错误日志"按钮跳转；错误日志详情返回 `operLogId`，供反向跳转操作日志。所有接口均经 `@PreAuthorize` 鉴权，权限 key 与 `sys_log_menu_init.sql` 中的按钮权限对齐。

### 2026-06-20 18:30 — 消息通知模块 + 通知分组

本批次新增消息通知模块的完整接口，包括消息主表的全生命周期管理、分组投递、个人消息查询、已读/确认。

- 消息通知（10 个）
- 通知分组（7 个）

合计 17 个接口。

消息删除采用软删除（`delete=1`）；`getMyNotices` 支持 ALL 全员 + GROUP 分组联合查询，返回值含 `hasRead`/`hasConfirmed` 字段。

### 2026-06-20 — 首版文档：登录、用户、角色、菜单、字典、字典数据

本批次补齐截至当前所有已完成的接口。

- 登录模块（4 个）、用户管理（6 个）、角色管理（7 个）、菜单管理（6 个）、字典管理（5 个）、字典数据（6 个）

合计 34 个接口。

---

## 通用响应说明

所有接口使用 `com.rookie.common.pojo.Result<T>` 包裹响应体：
- `code`: 200 成功，其他值失败（500 通用服务端错误）
- `msg`: 成功时固定 `"请求成功"`，失败时携带错误描述
- `data`: 成功时携带业务数据，失败时为 `null`

列表类接口使用 PageHelper 分页，返回 `PageInfo` 对象，内含 `list`、`total`、`pageNum`、`pageSize`。

需认证的接口通过 Spring Security + JWT 拦截，前端需在请求头中携带 `Token: <令牌值>`（无前缀，无 Bearer），由 `TokenVerifyFilter` (#34) 校验。

---

# 系统模块

## 一、认证管理

### 1. 登录

#### 1.1 基本信息
**请求接口：** `/login`
**请求方式：** POST
**所需权限：** 无
**基本信息：** 接收用户登录凭据，返回 JWT token 字符串

#### 1.2 请求头
无

#### 1.3 请求体

| 参数名   | 参数说明 | 参数类型 | 是否必填 |
| -------- | -------- | -------- | -------- |
| username | 登录账号 | string   | 是       |
| password | 账号密码 | string   | 是       |

**示例：**
```json
{
  "username": "admin",
  "password": "123456"
}
```

#### 1.4 响应示例

**成功示例：**
```json
{
  "code": 200,
  "msg": "请求成功",
  "data": "eyJhbGciOiJIUzI1NiJ9.xxx"
}
```

**失败示例：**
```json
{
  "code": 500,
  "msg": "请求失败",
  "data": null
}
```

---

### 2. 个人信息

#### 2.1 基本信息
**请求接口：** `/person`
**请求方式：** GET
**所需权限：** 需要登录
**基本信息：** 获取当前登录用户的个人信息

#### 2.2 请求头
无

#### 2.3 请求体
无

#### 2.4 响应示例

**成功示例：**
```json
{
  "code": 200,
  "msg": "请求成功",
  "data": {
    "userId": 1,
    "username": "admin",
    "nickName": "管理员",
    "phoneNumber": "13800138000",
    "sex": "1",
    "status": 1,
    "createTime": "2025-01-01T00:00:00",
    "userRole": [],
    "roleId": []
  }
}
```

| 响应字段     | 参数说明       | 参数类型                   |
| ------------ | -------------- | -------------------------- |
| userId       | 用户 ID        | long                       |
| username     | 登录账号       | string                     |
| nickName     | 昵称           | string                     |
| phoneNumber  | 手机号         | string                     |
| sex          | 性别           | string                     |
| status       | 状态           | integer                    |
| createTime   | 创建时间       | string                     |
| userRole     | 关联角色列表   | array\<SysRole\>           |
| roleId       | 关联角色 ID   | array\<long\>              |

---

### 3. 修改个人信息

#### 3.1 基本信息
**请求接口：** `/person`
**请求方式：** PUT
**所需权限：** 需要登录
**基本信息：** 修改当前登录用户的个人信息，userId 由服务端从 token 提取

#### 3.2 请求头
无

#### 3.3 请求体

| 参数名      | 参数说明 | 参数类型 | 是否必填 |
| ----------- | -------- | -------- | -------- |
| nickName    | 昵称     | string   | 否       |
| phoneNumber | 手机号   | string   | 否       |
| sex         | 性别     | string   | 否       |

**示例：**
```json
{
  "nickName": "管理员2",
  "phoneNumber": "13900139000",
  "sex": "0"
}
```

#### 3.4 响应示例

**成功示例：**
```json
{
  "code": 200,
  "msg": "请求成功",
  "data": true
}
```

---

### 4. 获取当前用户路由树

#### 4.1 基本信息
**请求接口：** `/person/routers`
**请求方式：** GET
**所需权限：** 需要登录
**基本信息：** 返回当前用户有权限访问的菜单树

#### 4.2 请求头
无

#### 4.3 请求体
无

#### 4.4 响应示例

**成功示例：**
```json
{
  "code": 200,
  "msg": "请求成功",
  "data": [
    {
      "menuId": 1,
      "menuName": "系统管理",
      "permKey": "system:manage",
      "parentId": 0,
      "menuType": 1,
      "route": "/system",
      "path": "system",
      "icon": "setting",
      "status": 1,
      "createTime": "2025-01-01T00:00:00",
      "sonMenus": []
    }
  ]
}
```

| 响应字段   | 参数说明   | 参数类型                   |
| ---------- | ---------- | -------------------------- |
| menuId     | 菜单 ID    | long                       |
| menuName   | 菜单名称   | string                     |
| permKey    | 权限标识   | string                     |
| parentId   | 父菜单 ID  | long                       |
| menuType   | 菜单类型   | integer                    |
| route      | 前端路由   | string                     |
| path       | 组件路径   | string                     |
| icon       | 图标       | string                     |
| status     | 状态       | integer                    |
| createTime | 创建时间   | string                     |
| sonMenus   | 子菜单列表 | array\<SysMenuVo\>（递归） |

---

## 二、用户管理

**模块前缀：** `/sys/user`

### 1. 获取用户列表

#### 1.1 基本信息
**请求接口：** `/sys/user/list`
**请求方式：** GET
**所需权限：** 需要登录
**基本信息：** 分页查询用户列表（Query String 传参）

#### 1.2 请求头
无

#### 1.3 请求体（查询参数）

| 参数名      | 参数说明 | 参数类型             | 是否必填 |
| ----------- | -------- | -------------------- | -------- |
| username    | 用户名   | string               | 否       |
| nickName    | 昵称     | string               | 否       |
| phoneNumber | 手机号   | string               | 否       |
| status      | 状态     | integer              | 否       |
| beginTime   | 起始时间 | string (ISO datetime) | 否       |
| endTime     | 截止时间 | string (ISO datetime) | 否       |

#### 1.4 响应示例
`Result<PageInfo<SysUserVo>>`

---

### 2. 获取用户详情

#### 2.1 基本信息
**请求接口：** `/sys/user/{userId}`
**请求方式：** GET
**所需权限：** 需要登录
**基本信息：** 获取指定用户的详细信息

#### 2.2 请求头
无

#### 2.3 请求体
无

#### 2.4 响应示例
`Result<SysUserVo>`

---

### 3. 添加用户

#### 3.1 基本信息
**请求接口：** `/sys/user`
**请求方式：** POST
**所需权限：** 需要登录
**基本信息：** 新增用户并关联角色

#### 3.2 请求头
无

#### 3.3 请求体

| 参数名      | 参数说明    | 参数类型      | 是否必填 |
| ----------- | ----------- | ------------- | -------- |
| username    | 登录账号    | string        | 是       |
| password    | 密码        | string        | 是       |
| nickName    | 昵称        | string        | 否       |
| phoneNumber | 手机号      | string        | 否       |
| sex         | 性别        | string        | 否       |
| roleId      | 关联角色 ID | array\<long\> | 否       |

**示例：**
```json
{
  "username": "newuser",
  "password": "123456",
  "nickName": "新用户",
  "phoneNumber": "13800138000",
  "sex": "1",
  "roleId": [1, 2]
}
```

#### 3.4 响应示例
`Result<Boolean>`

---

### 4. 编辑用户

#### 4.1 基本信息
**请求接口：** /sys/user
**请求方式：** PUT
**所需权限：** 需要登录
**基本信息：** 修改用户信息


#### 4.2 请求头
无

#### 4.3 请求体
同新增，必须携带 `userId`（Long）

#### 4.4 响应示例
`Result<Boolean>`

---

### 5. 删除用户

#### 5.1 基本信息
**请求接口：** /sys/user/{userIds}
**请求方式：** DELETE
**所需权限：** 需要登录
**基本信息：** 批量删除用户


#### 5.2 请求头
无

#### 5.3 请求体
无（路径参数：`userIds` — Long[]（逗号分隔） | **响应：** `Result<Boolean>`)

#### 5.4 响应示例
-

---

### 6. 更改用户状态

#### 6.1 基本信息
**请求接口：** /sys/user/status
**请求方式：** PUT
**所需权限：** 需要登录
**基本信息：** 更改用户状态


#### 6.2 请求头
无

#### 6.3 请求体
无（查询参数：`userId`(long)、`status`(integer) | **响应：** `Result<Boolean>`)

#### 6.4 响应示例
-

---

## 三、角色管理

**模块前缀：** `/sys/role`

### 1. 获取角色列表

#### 1.1 基本信息
**请求接口：** `/sys/role/list`
**请求方式：** GET
**所需权限：** 需要登录
**基本信息：** 分页查询角色列表

#### 1.2 请求头
无

#### 1.3 请求体（查询参数）

| 参数名    | 参数说明 | 参数类型 | 是否必填 |
| --------- | -------- | -------- | -------- |
| roleName  | 角色名称 | string   | 否       |
| status    | 状态     | integer  | 否       |
| beginTime | 起始时间 | string   | 否       |
| endTime   | 截止时间 | string   | 否       |

#### 1.4 响应示例
`Result<PageInfo<SysRoleVo>>`

---

### 2. 获取角色详情

#### 2.1 基本信息
**请求接口：** /sys/role/{roleId}
**请求方式：** GET
**所需权限：** 需要登录
**基本信息：** 获取角色详细信息（含权限列表）


#### 2.2 请求头
无

#### 2.3 请求体
无

#### 2.4 响应示例
`Result<SysRoleVo>`（含 `rolePerm` 权限列表和 `permId` 权限 ID）

---

### 3. 添加角色

#### 3.1 基本信息
**请求接口：** `/sys/role` | **请求方式：** POST

#### 3.2 请求头
无

#### 3.3 请求体

| 参数名    | 参数说明    | 参数类型      | 是否必填 |
| --------- | ----------- | ------------- | -------- |
| roleName  | 角色名称    | string        | 是       |
| roleLevel | 角色层级    | integer       | 否       |
| roleKey   | 角色标识    | string        | 否       |
| permId    | 权限菜单 ID | array\<long\> | 否       |

**示例：**
```json
{
  "roleName": "部门管理员",
  "roleLevel": 2,
  "roleKey": "dept_admin",
  "permId": [1, 2, 3]
}
```

#### 3.4 响应示例
`Result<Boolean>`

### 4. 编辑角色

#### 4.1 基本信息
**请求接口：** /sys/role
**请求方式：** PUT
**所需权限：** 需要登录
**基本信息：** 修改角色信息


#### 4.2 请求头
无

#### 4.3 请求体
同新增，必须携带 `roleId`（Long） | **响应：** `Result<Boolean>`

#### 4.4 响应示例
`Result<Boolean>`

---

### 5. 删除角色

#### 5.1 基本信息
**请求接口：** /sys/role/{roleIds}
**请求方式：** DELETE
**所需权限：** 需要登录
**基本信息：** 批量删除角色


#### 5.2 请求头
无

#### 5.3 请求体
无（路径参数：Long[] | **响应：** `Result<Boolean>`)

#### 5.4 响应示例
-

---

### 6. 更改角色状态

#### 6.1 基本信息
**请求接口：** /sys/role/status
**请求方式：** PUT
**所需权限：** 需要登录
**基本信息：** 更改角色状态


#### 6.2 请求头
无

#### 6.3 请求体
无（查询参数：`roleId`(long)、`status`(integer) | **响应：** `Result<Boolean>`)

#### 6.4 响应示例
-

---

### 7. 设置默认角色

#### 7.1 基本信息
**请求接口：** /sys/role/default/{roleId}
**请求方式：** PUT
**所需权限：** 需要登录
**基本信息：** 新注册用户自动关联该角色，同时取消旧默认


#### 7.2 请求头
无

#### 7.3 请求体
无

#### 7.4 响应示例
新注册用户自动关联，同时取消旧默认角色 | **响应：** `Result<Boolean>`

---

## 四、菜单管理

**模块前缀：** `/sys/menu`

### 1. 获取菜单列表

#### 1.1 基本信息
**请求接口：** `/sys/menu/list`
**请求方式：** GET
**所需权限：** 需要登录
**基本信息：** 获取菜单树（子菜单递归嵌套在 `sonMenus`）

#### 1.2 请求头
无

#### 1.3 请求体（查询参数）

| 参数名    | 参数说明 | 参数类型 | 是否必填 |
| --------- | -------- | -------- | -------- |
| menuName  | 菜单名称 | string   | 否       |
| permKey   | 权限标识 | string   | 否       |
| status    | 状态     | integer  | 否       |
| beginTime | 起始时间 | string   | 否       |
| endTime   | 截止时间 | string   | 否       |

#### 1.4 响应示例
`Result<List<SysMenuVo>>`

---

### 2. 获取菜单详情

#### 2.1 基本信息
**请求接口：** /sys/menu/{menuId}
**请求方式：** GET
**所需权限：** 需要登录
**基本信息：** 获取菜单详细信息


#### 2.2 请求头
无

#### 2.3 请求体
无（路径参数：Integer )

#### 2.4 响应示例
`Result<SysMenuVo>`

---

### 3. 添加菜单

#### 3.1 基本信息
**请求接口：** `/sys/menu` | **请求方式：** POST

#### 3.2 请求头
无

#### 3.3 请求体

| 参数名    | 参数说明 | 参数类型 | 是否必填 |
| --------- | -------- | -------- | -------- |
| menuName  | 菜单名称 | string   | 是       |
| parentId  | 父菜单 ID | long    | 否       |
| menuType  | 菜单类型 | integer  | 否       |
| route     | 前端路由 | string   | 否       |
| path      | 组件路径 | string   | 否       |
| icon      | 图标     | string   | 否       |
| permKey   | 权限标识 | string   | 否       |
| backlinks | 是否外链 | integer  | 否       |

**示例：**
```json
{
  "menuName": "用户管理",
  "permKey": "system:user:list",
  "parentId": 1,
  "menuType": 1,
  "route": "/system/user",
  "path": "system/user",
  "icon": "user",
  "backlinks": 0
}
```

#### 3.4 响应示例
`Result<Boolean>`

### 4. 编辑菜单

#### 4.1 基本信息
**请求接口：** /sys/menu
**请求方式：** PUT
**所需权限：** 需要登录
**基本信息：** 修改菜单信息


#### 4.2 请求头
无

#### 4.3 请求体
同新增，必须携带 `menuId`（Long） | **响应：** `Result<Boolean>`

#### 4.4 响应示例
`Result<Boolean>`

---

### 5. 删除菜单

#### 5.1 基本信息
**请求接口：** /sys/menu/{menuIds}
**请求方式：** DELETE
**所需权限：** 需要登录
**基本信息：** 批量删除菜单


#### 5.2 请求头
无

#### 5.3 请求体
无（路径参数：Integer[] )

#### 5.4 响应示例
`Result<Boolean>`

---

### 6. 更改菜单状态

#### 6.1 基本信息
**请求接口：** /sys/menu/status
**请求方式：** PUT
**所需权限：** 需要登录
**基本信息：** 更改菜单状态


#### 6.2 请求头
无

#### 6.3 请求体
无（查询参数：`menuId`(integer)、`status`(integer) | **响应：** `Result<Boolean>`)

#### 6.4 响应示例
-

---

## 五、字典管理

**模块前缀：** `/sys/dict`

### 1. 获取字典列表

#### 1.1 基本信息
**请求接口：** `/sys/dict/list`
**请求方式：** GET
**所需权限：** 需要登录
**基本信息：** 分页查询字典列表

#### 1.2 请求头
无

#### 1.3 请求体（查询参数）

| 参数名    | 参数说明 | 参数类型 | 是否必填 |
| --------- | -------- | -------- | -------- |
| dictName  | 字典名称 | string   | 否       |
| dictKey   | 字典键   | string   | 否       |
| status    | 状态     | integer  | 否       |
| beginTime | 起始时间 | string   | 否       |
| endTime   | 截止时间 | string   | 否       |

#### 1.4 响应示例
`Result<PageInfo<SysDictVO>>`

---

### 2. 获取字典详情

#### 2.1 基本信息
**请求接口：** /sys/dict/{dictId}
**请求方式：** GET
**所需权限：** 需要登录
**基本信息：** 获取字典详细信息


#### 2.2 请求头
无

#### 2.3 请求体
无

#### 2.4 响应示例
`Result<SysDictVO>`

---

### 3. 添加字典

#### 3.1 基本信息
**请求接口：** `/sys/dict` | **请求方式：** POST

#### 3.2 请求头
无

#### 3.3 请求体

| 参数名   | 参数说明 | 参数类型 | 是否必填 |
| -------- | -------- | -------- | -------- |
| dictName | 字典名称 | string   | 是       |
| dictKey  | 字典键   | string   | 是       |
| remake   | 备注     | string   | 否       |
| status   | 状态     | integer  | 否       |

**示例：**
```json
{
  "dictName": "用户性别",
  "dictKey": "user_sex",
  "remake": "用于用户表单性别选择",
  "status": 1
}
```

#### 3.4 响应示例
`Result<Boolean>`

---

### 4. 编辑字典

#### 4.1 基本信息
**请求接口：** /sys/dict
**请求方式：** PUT
**所需权限：** 需要登录
**基本信息：** 修改字典信息


#### 4.2 请求头
无

#### 4.3 请求体
同新增，必须携带 `dictId`（Long） | **响应：** `Result<Boolean>`

#### 4.4 响应示例
`Result<Boolean>`

---

### 5. 删除字典

#### 5.1 基本信息
**请求接口：** /sys/dict/{dictId}
**请求方式：** DELETE
**所需权限：** 需要登录
**基本信息：** 删除字典


#### 5.2 请求头
无

#### 5.3 请求体
无

#### 5.4 响应示例
`Result<Boolean>`

---

## 六、字典数据

**模块前缀：** `/sys/dist/data`
> 当前为 `/sys/dist/data`（疑似 `/sys/dict/data` 拼写，后续可能修正）

### 1. 获取字典数据列表

#### 1.1 基本信息
**请求接口：** `/sys/dist/data/list`
**请求方式：** GET
**所需权限：** 需要登录
**基本信息：** 分页查询字典数据列表

#### 1.2 请求头
无

#### 1.3 请求体（查询参数）

| 参数名        | 参数说明 | 参数类型 | 是否必填 |
| ------------- | -------- | -------- | -------- |
| dictId        | 字典 ID  | long     | 否       |
| dictKey       | 字典键   | string   | 否       |
| dictDataLabel | 数据标签 | string   | 否       |

#### 1.4 响应示例
`Result<PageInfo<SysDictDataVo>>`

---

### 2. 获取字典数据详情

#### 2.1 基本信息
**请求接口：** /sys/dist/data/{dictDataId}
**请求方式：** GET
**所需权限：** 需要登录
**基本信息：** 获取字典数据详细信息


#### 2.2 请求头
无

#### 2.3 请求体
无

#### 2.4 响应示例
`Result<SysDictDataVo>`

---

### 3. 添加字典数据

#### 3.1 基本信息
**请求接口：** `/sys/dist/data` | **请求方式：** POST

#### 3.2 请求头
无

#### 3.3 请求体

| 参数名        | 参数说明 | 参数类型 | 是否必填 |
| ------------- | -------- | -------- | -------- |
| dictId        | 字典 ID  | long     | 是       |
| dictKey       | 字典键   | string   | 否       |
| dictDataLabel | 数据标签 | string   | 是       |
| dictDataValue | 数据值   | string   | 是       |
| dictDataSort  | 排序     | string   | 否       |
| tagType       | 标签类型 | string   | 否       |
| tagEffect     | 标签效果 | string   | 否       |
| cssClass      | CSS 类名 | string   | 否       |

**示例：**
```json
{
  "dictId": 1,
  "dictKey": "user_sex",
  "dictDataLabel": "男",
  "dictDataValue": "1",
  "dictDataSort": "1",
  "tagType": "success"
}
```

#### 3.4 响应示例
`Result<Boolean>`

### 4. 编辑字典数据

#### 4.1 基本信息
**请求接口：** /sys/dist/data
**请求方式：** PUT
**所需权限：** 需要登录
**基本信息：** 修改字典数据信息


#### 4.2 请求头
无

#### 4.3 请求体
同新增，必须携带 `dictDataId`（Long） | **响应：** `Result<Boolean>`

#### 4.4 响应示例
`Result<Boolean>`

---

### 5. 删除字典数据

#### 5.1 基本信息
**请求接口：** /sys/dist/data/{dictDataId}
**请求方式：** DELETE
**所需权限：** 需要登录
**基本信息：** 删除字典数据


#### 5.2 请求头
无

#### 5.3 请求体
无

#### 5.4 响应示例
`Result<Boolean>`

---

### 6. 按字典键获取数据

#### 6.1 基本信息
**请求接口：** `/sys/dist/data/type/{dictKey}`
**请求方式：** GET
**所需权限：** 需要登录
**基本信息：** 根据字典键获取其下所有字典数据项（用于下拉框/单选框数据源）

#### 6.2 请求头
无

#### 6.3 请求体
无

#### 6.4 响应示例
`Result<List<SysDictDataVo>>`

---

# 通知模块

## 一、消息通知

**模块前缀：** `/sys/notice`

### 1. 获取消息通知列表

#### 1.1 基本信息
**请求接口：** `/sys/notice/list`
**请求方式：** GET
**所需权限：** 需要登录
**基本信息：** 分页查询消息通知列表（管理员视角）

#### 1.2 请求头
无

#### 1.3 请求体（查询参数）

| 参数名       | 参数说明 | 参数类型 | 是否必填 |
| ------------ | -------- | -------- | -------- |
| title        | 标题     | string   | 否       |
| noticeType   | 消息类型 | string   | 否       |
| level        | 消息级别 | string   | 否       |
| publishScope | 发布范围 | string   | 否       |
| status       | 状态     | string   | 否       |
| beginTime    | 起始时间 | string   | 否       |
| endTime      | 截止时间 | string   | 否       |

#### 1.4 响应示例
`Result<PageInfo<SysNoticeVo>>`

---

### 2. 获取消息通知详情

#### 2.1 基本信息
**请求接口：** `/sys/notice/{noticeId}`
**请求方式：** GET
**所需权限：** 需要登录
**基本信息：** 获取指定消息通知的详细信息（含已关联的分组 ID 列表）

#### 2.2 请求头
无

#### 2.3 请求体
无

#### 2.4 响应示例
`Result<SysNoticeVo>`

---

### 3. 添加消息通知

#### 3.1 基本信息
**请求接口：** `/sys/notice`
**请求方式：** POST
**所需权限：** 需要登录
**基本信息：** 新增消息通知，当 `publishScope=GROUP` 时可同时关联分组

#### 3.2 请求头
无

#### 3.3 请求体

| 参数名       | 参数说明   | 参数类型      | 是否必填 |
| ------------ | ---------- | ------------- | -------- |
| title        | 标题       | string        | 是       |
| content      | 正文     | string        | 是       |
| noticeType   | 消息类型   | string        | 否       |
| level        | 消息级别   | string        | 否       |
| publishScope | 发布范围   | string        | 否       |
| needConfirm  | 需确认     | integer       | 否       |
| expireTime   | 过期时间   | string        | 否       |
| routePath    | 前端路由   | string        | 否       |
| groupIds     | 关联分组 ID | array\<long\> | 否       |

**示例：**
```json
{
  "title": "系统升级通知",
  "content": "系统将于本周六凌晨 2:00-4:00 进行升级维护",
  "noticeType": "NOTICE",
  "level": "IMPORTANT",
  "publishScope": "GROUP",
  "needConfirm": 1,
  "expireTime": "2026-07-01T00:00:00",
  "groupIds": [1, 2]
}
```

#### 3.4 响应示例
`Result<Boolean>`（消息主表 + group 关联在同一事务内插入）

---

### 4. 编辑消息通知

#### 4.1 基本信息
**请求接口：** /sys/notice
**请求方式：** PUT
**所需权限：** 需要登录
**基本信息：** 修改消息通知，编辑时先清旧 group 关联再重新插入（同一事务）


#### 4.2 请求头
无

#### 4.3 请求体
同新增，必须携带 `noticeId`（Long）。编辑时先清旧 group 关联再重新插入（同一事务） | **响应：** `Result<Boolean>`

#### 4.4 响应示例
`Result<Boolean>`

---

### 5. 删除消息通知

#### 5.1 基本信息
**请求接口：** /sys/notice/{noticeIds}
**请求方式：** DELETE
**所需权限：** 需要登录
**基本信息：** 批量软删除（delete=1），同时级联清理 groupRel 和 read 表（同一事务）


#### 5.2 请求头
无

#### 5.3 请求体
无（路径参数：Long[]（逗号分隔）)

#### 5.4 响应示例
批量软删除（`delete=1`），同时级联清理 groupRel 和 read 表（同一事务） | **响应：** `Result<Boolean>`

---

### 6. 发布消息通知

#### 6.1 基本信息
**请求接口：** /sys/notice/publish/{noticeId}
**请求方式：** PUT
**所需权限：** 需要登录
**基本信息：** DRAFT → PUBLISHED，自动填入 publish_time


#### 6.2 请求头
无

#### 6.3 请求体
无

#### 6.4 响应示例
DRAFT → PUBLISHED，自动填入 `publish_time` | **响应：** `Result<Boolean>`

---

### 7. 撤回消息通知

#### 7.1 基本信息
**请求接口：** /sys/notice/revoke/{noticeId}
**请求方式：** PUT
**所需权限：** 需要登录
**基本信息：** PUBLISHED → REVOKED


#### 7.2 请求头
无

#### 7.3 请求体
无

#### 7.4 响应示例
PUBLISHED → REVOKED | **响应：** `Result<Boolean>`

---

### 8. 获取我的消息

#### 8.1 基本信息
**请求接口：** `/sys/notice/my`
**请求方式：** GET
**所需权限：** 需要登录
**基本信息：** 查询当前用户可见的消息列表。包含 `publishScope=ALL` 全员消息 + 通过所属 group 命中的消息。返回每条消息附带 `hasRead`（是否已读）和 `hasConfirmed`（是否已确认）字段

#### 8.2 请求头
无

#### 8.3 请求体
无

#### 8.4 响应示例

**成功示例：**
```json
{
  "code": 200,
  "msg": "请求成功",
  "data": [
    {
      "noticeId": 1,
      "title": "系统升级通知",
      "content": "系统将于本周六凌晨进行升级维护",
      "noticeType": "NOTICE",
      "level": "IMPORTANT",
      "publishScope": "ALL",
      "status": "PUBLISHED",
      "needConfirm": 0,
      "hasRead": true,
      "hasConfirmed": false,
      "createTime": "2026-06-20T10:00:00"
    }
  ]
}
```

| 响应字段     | 参数说明      | 参数类型             |
| ------------ | ------------- | -------------------- |
| hasRead      | 当前用户是否已读 | boolean            |
| hasConfirmed | 当前用户是否已确认 | boolean           |
| 其余字段     | 同 `SysNotice` 实体 | —                |

---

### 9. 标记已读

#### 9.1 基本信息
**请求接口：** /sys/notice/read/{noticeId}
**请求方式：** POST
**所需权限：** 需要登录
**基本信息：** 将当前用户对该消息标记为已读，重复调用幂等


#### 9.2 请求头
无

#### 9.3 请求体
无

#### 9.4 响应示例
将当前用户对该消息标记为已读，重复调用幂等 | **响应：** `Result<Boolean>`

---

### 10. 确认消息

#### 10.1 基本信息
**请求接口：** /sys/notice/confirm/{noticeId}
**请求方式：** POST
**所需权限：** 需要登录
**基本信息：** 对 needConfirm=1 的消息进行确认，若尚未标记已读则同时补齐


#### 10.2 请求头
无

#### 10.3 请求体
无

#### 10.4 响应示例
对 `needConfirm=1` 的消息进行确认。若尚未标记已读，同时补齐已读记录 | **响应：** `Result<Boolean>`

---

## 二、通知分组

**模块前缀：** `/sys/notice/group`

### 1. 获取分组列表

#### 1.1 基本信息
**请求接口：** `/sys/notice/group/list`
**请求方式：** GET
**所需权限：** 需要登录
**基本信息：** 分页查询通知分组列表，每条含 `members` 成员列表

#### 1.2 请求头
无

#### 1.3 请求体（查询参数）

| 参数名    | 参数说明 | 参数类型 | 是否必填 |
| --------- | -------- | -------- | -------- |
| groupName | 分组名称 | string   | 否       |
| groupCode | 分组编码 | string   | 否       |
| status    | 状态     | integer  | 否       |

#### 1.4 响应示例
`Result<PageInfo<SysNoticeGroupVo>>`（`list` 中每项含 `members` — `SysNoticeGroupMember` 的 `{ id, groupId, userId }`）

---

### 2. 获取分组详情

#### 2.1 基本信息
**请求接口：** /sys/notice/group/{groupId}
**请求方式：** GET
**所需权限：** 需要登录
**基本信息：** -

#### 2.2 请求头
无

#### 2.3 请求体
无

#### 2.4 响应示例
`Result<SysNoticeGroupVo>`（含 `members`）

---

### 3. 添加分组

#### 3.1 基本信息
**请求接口：** `/sys/notice/group` | **请求方式：** POST

#### 3.2 请求头
无

#### 3.3 请求体

| 参数名    | 参数说明 | 参数类型                       | 是否必填 |
| --------- | -------- | ------------------------------ | -------- |
| groupName | 分组名称 | string                         | 是       |
| groupCode | 分组编码 | string                         | 是       |
| groupDesc | 分组描述 | string                         | 否       |
| members   | 成员列表 | array\<SysNoticeGroupMember\> | 否       |

**示例：**
```json
{
  "groupName": "运维团队",
  "groupCode": "ops_team",
  "groupDesc": "运维值班人员",
  "members": [{ "userId": 1 }, { "userId": 2 }]
}
```

**`members` 中 SysNoticeGroupMember 属性说明：**

| 参数名  | 参数说明   | 参数类型 | 是否必填 |
| ------- | ---------- | -------- | -------- |
| userId  | 用户 ID    | long     | 是       |

#### 3.4 响应示例
`Result<Boolean>`（分组主表 + 成员在同一事务内插入）

### 4. 编辑分组

#### 4.1 基本信息
**请求接口：** /sys/notice/group
**请求方式：** PUT
**所需权限：** 需要登录
**基本信息：** 修改分组信息，不处理 members 变更


#### 4.2 请求头
无

#### 4.3 请求体
同新增，必须携带 `groupId`（Long）。不处理 members 变更 | **响应：** `Result<Boolean>`

#### 4.4 响应示例
`Result<Boolean>`

---

### 5. 删除分组

#### 5.1 基本信息
**请求接口：** /sys/notice/group/{groupIds}
**请求方式：** DELETE
**所需权限：** 需要登录
**基本信息：** 批量删除，级联清理成员和 groupRel（同一事务）


#### 5.2 请求头
无

#### 5.3 请求体
无（路径参数：Long[]（逗号分隔）)

#### 5.4 响应示例
批量删除，级联清理成员和 groupRel（同一事务） | **响应：** `Result<Boolean>`

---

### 6. 批量添加成员

#### 6.1 基本信息
**请求接口：** /sys/notice/group/{groupId}/members
**请求方式：** POST
**所需权限：** 需要登录
**基本信息：** 向分组批量添加成员


#### 6.2 请求头
无

#### 6.3 请求体
`[1, 2, 3]` — 用户 ID 数组（`List<Long>`） | **响应：** `Result<Boolean>`

#### 6.4 响应示例
-

---

### 7. 批量移除成员

#### 7.1 基本信息
**请求接口：** `/sys/notice/group/{groupId}/members`
**请求方式：** DELETE

#### 7.2 请求头
无

#### 7.3 请求体

| 参数名 | 参数说明         | 参数类型      | 是否必填 |
| ------ | ---------------- | ------------- | -------- |
| —      | 成员记录主键 ID | array\<long\> | 是       |

**示例：** `[10, 11, 12]`（分组成员记录主键 ID，非 userId）

#### 7.4 响应示例
`Result<Boolean>`（移除前校验每个 id 是否属于该分组，不匹配则拒绝全量）

---

## 一、操作日志

### 1. 获取操作日志列表

#### 1.1 基本信息
**请求接口：** `/sys/operLog/list`
**请求方式：** GET
**所需权限：** `system:operLog:quarry`
**基本信息：** 分页查询操作日志列表，失败行（`status=1`）返回关联的 `errorLogId`，供前端"错误日志"按钮跳转

#### 1.2 请求头
无

#### 1.3 请求体（查询参数）

| 参数名        | 参数说明                       | 参数类型 | 是否必填 |
| ------------- | ------------------------------ | -------- | -------- |
| title         | 模块标题（模糊）               | string   | 否       |
| businessType  | 业务类型（字典 sys_oper_business_type） | string   | 否       |
| operName      | 操作人员（模糊）               | string   | 否       |
| status        | 操作状态：0 正常 1 异常        | integer  | 否       |
| requestMethod | 请求方式                       | string   | 否       |
| beginTime     | 起始时间                       | string   | 否       |
| endTime       | 截止时间                       | string   | 否       |

#### 1.4 响应示例
`Result<PageInfo<SysOperLogVo>>`

---

### 2. 获取操作日志详情

#### 2.1 基本信息
**请求接口：** `/sys/operLog/{operId}`
**请求方式：** GET
**所需权限：** `system:operLog:info`
**基本信息：** 获取指定操作日志的详细信息

#### 2.2 请求头
无

#### 2.3 请求体
无

#### 2.4 响应示例
`Result<SysOperLogVo>`

---

### 3. 批量删除操作日志

#### 3.1 基本信息
**请求接口：** `/sys/operLog/{operIds}`
**请求方式：** DELETE
**所需权限：** `system:operLog:delete`
**基本信息：** 批量删除操作日志（物理删除）

#### 3.2 请求头
无

#### 3.3 请求体（路径参数）

| 参数名  | 参数说明       | 参数类型      | 是否必填 |
| ------- | -------------- | ------------- | -------- |
| operIds | 操作日志主键 ID | array\<long\> | 是       |

**示例：** `/sys/operLog/1,2,3`

#### 3.4 响应示例
`Result<Boolean>`

---

### 4. 清空操作日志

#### 4.1 基本信息
**请求接口：** `/sys/operLog/clean`
**请求方式：** DELETE
**所需权限：** `system:operLog:clean`
**基本信息：** 清空全部操作日志（TRUNCATE，不可恢复）

#### 4.2 请求头
无

#### 4.3 请求体
无

#### 4.4 响应示例
`Result<Boolean>`

---

## 二、错误日志

### 1. 获取错误日志列表

#### 1.1 基本信息
**请求接口：** `/sys/errorLog/list`
**请求方式：** GET
**所需权限：** `system:errorLog:quarry`
**基本信息：** 分页查询错误日志列表，请求来源的错误日志返回 `operLogId`，供前端"查看操作日志"按钮跳转

#### 1.2 请求头
无

#### 1.3 请求体（查询参数）

| 参数名       | 参数说明                           | 参数类型 | 是否必填 |
| ------------ | ---------------------------------- | -------- | -------- |
| sourceType   | 错误来源（字典 sys_error_source_type） | string   | 否       |
| title        | 错误简述（模糊）                   | string   | 否       |
| operName     | 操作人员（模糊）                   | string   | 否       |
| exceptionType | 异常类型（模糊）                  | string   | 否       |
| beginTime    | 起始时间                           | string   | 否       |
| endTime      | 截止时间                           | string   | 否       |

#### 1.4 响应示例
`Result<PageInfo<SysErrorLogVo>>`

---

### 2. 获取错误日志详情

#### 2.1 基本信息
**请求接口：** `/sys/errorLog/{errorId}`
**请求方式：** GET
**所需权限：** `system:errorLog:info`
**基本信息：** 获取指定错误日志的详细信息（含完整堆栈）

#### 2.2 请求头
无

#### 2.3 请求体
无

#### 2.4 响应示例
`Result<SysErrorLogVo>`

---

### 3. 批量删除错误日志

#### 3.1 基本信息
**请求接口：** `/sys/errorLog/{errorIds}`
**请求方式：** DELETE
**所需权限：** `system:errorLog:delete`
**基本信息：** 批量删除错误日志（物理删除）

#### 3.2 请求头
无

#### 3.3 请求体（路径参数）

| 参数名  | 参数说明       | 参数类型      | 是否必填 |
| ------- | -------------- | ------------- | -------- |
| errorIds | 错误日志主键 ID | array\<long\> | 是       |

**示例：** `/sys/errorLog/1,2,3`

#### 3.4 响应示例
`Result<Boolean>`

---

### 4. 清空错误日志

#### 4.1 基本信息
**请求接口：** `/sys/errorLog/clean`
**请求方式：** DELETE
**所需权限：** `system:errorLog:clean`
**基本信息：** 清空全部错误日志（TRUNCATE，不可恢复）

#### 4.2 请求头
无

#### 4.3 请求体
无

#### 4.4 响应示例
`Result<Boolean>`

---

# 系统设置模块

## 一、系统设置

### 1. 获取系统设置列表

#### 1.1 基本信息
**请求接口：** `/sys/system-config/list`
**请求方式：** GET
**所需权限：** `system:systemConfig:quarry`
**基本信息：** 分页查询系统设置列表，支持按设置键、设置名称、状态、创建时间范围筛选

#### 1.2 请求头
| 参数名 | 参数说明           | 参数类型 | 是否必填 |
| ------ | ------------------ | -------- | -------- |
| Token  | JWT 令牌（无前缀） | string   | 是       |

#### 1.3 请求体（查询参数）

| 参数名     | 参数说明                       | 参数类型 | 是否必填 |
| ---------- | ------------------------------ | -------- | -------- |
| configKey  | 设置键（模糊）                 | string   | 否       |
| configName | 设置名称（模糊）               | string   | 否       |
| status     | 状态：1 启用 0 停用            | integer  | 否       |
| beginTime  | 创建起始时间                   | string   | 否       |
| endTime    | 创建截止时间                   | string   | 否       |
| pageNum    | 页码                           | integer  | 否       |
| pageSize   | 每页条数                       | integer  | 否       |

#### 1.4 响应示例
`Result<PageInfo<SysConfigVo>>`

```json
{
  "code": 200,
  "msg": "请求成功",
  "data": {
    "list": [
      {
        "configId": 1,
        "configKey": "sys.user.initPassword",
        "configName": "用户初始密码",
        "configValue": "123456",
        "valueType": "STRING",
        "isSystem": 1,
        "remark": "新建用户与重置密码时的初始密码",
        "status": 1,
        "createTime": "2026-07-03 10:00:00",
        "updateTime": "2026-07-03 10:00:00"
      }
    ],
    "total": 3,
    "pageNum": 1,
    "pageSize": 10
  }
}
```

---

### 2. 获取系统设置详情

#### 2.1 基本信息
**请求接口：** `/sys/system-config/{configId}`
**请求方式：** GET
**所需权限：** `system:systemConfig:info`
**基本信息：** 获取指定系统设置的详细信息

#### 2.2 请求头
| 参数名 | 参数说明           | 参数类型 | 是否必填 |
| ------ | ------------------ | -------- | -------- |
| Token  | JWT 令牌（无前缀） | string   | 是       |

#### 2.3 请求体（路径参数）

| 参数名   | 参数说明       | 参数类型 | 是否必填 |
| -------- | -------------- | -------- | -------- |
| configId | 设置项主键 ID  | long     | 是       |

#### 2.4 响应示例
`Result<SysConfigVo>`

---

### 3. 新增系统设置

#### 3.1 基本信息
**请求接口：** `/sys/system-config`
**请求方式：** POST
**所需权限：** `system:systemConfig:add`
**基本信息：** 新增系统设置项并立即写入 Redis 缓存。新增项强制 `is_system=0`（内置项只能由初始化脚本写入）

#### 3.2 请求头
| 参数名 | 参数说明           | 参数类型 | 是否必填 |
| ------ | ------------------ | -------- | -------- |
| Token  | JWT 令牌（无前缀） | string   | 是       |

#### 3.3 请求体

| 参数名       | 参数说明                                              | 参数类型 | 是否必填 |
| ------------ | ----------------------------------------------------- | -------- | -------- |
| configKey    | 设置键（业务唯一，推荐「模块.子项.用途」点号分层）    | string   | 是       |
| configName   | 设置名称                                              | string   | 是       |
| configValue  | 设置值（按 valueType 解释）                           | string   | 否       |
| valueType    | 值类型：STRING/BOOLEAN/NUMBER/JSON                    | string   | 是       |
| remark       | 备注说明                                              | string   | 否       |
| status       | 状态：1 启用 0 停用                                   | integer  | 是       |

#### 3.4 响应示例
`Result<Boolean>`

---

### 4. 编辑系统设置

#### 4.1 基本信息
**请求接口：** `/sys/system-config`
**请求方式：** PUT
**所需权限：** `system:systemConfig:edit`
**基本信息：** 编辑系统设置并刷新缓存。内置项（is_system=1）禁止修改 configKey/valueType、禁止停用，仅可改值/名称/备注

#### 4.2 请求头
| 参数名 | 参数说明           | 参数类型 | 是否必填 |
| ------ | ------------------ | -------- | -------- |
| Token  | JWT 令牌（无前缀） | string   | 是       |

#### 4.3 请求体

| 参数名       | 参数说明                                              | 参数类型 | 是否必填 |
| ------------ | ----------------------------------------------------- | -------- | -------- |
| configId     | 设置项主键 ID                                         | long     | 是       |
| configKey    | 设置键（内置项不可改）                                | string   | 否       |
| configName   | 设置名称                                              | string   | 否       |
| configValue  | 设置值                                                | string   | 否       |
| valueType    | 值类型（内置项不可改）                                | string   | 否       |
| remark       | 备注说明                                              | string   | 否       |
| status       | 状态（内置项不可停用）                                | integer  | 否       |

#### 4.4 响应示例
`Result<Boolean>`

---

### 5. 删除系统设置

#### 5.1 基本信息
**请求接口：** `/sys/system-config/{configId}`
**请求方式：** DELETE
**所需权限：** `system:systemConfig:delete`
**基本信息：** 删除系统设置并清除缓存。内置项（is_system=1）禁止删除

#### 5.2 请求头
| 参数名 | 参数说明           | 参数类型 | 是否必填 |
| ------ | ------------------ | -------- | -------- |
| Token  | JWT 令牌（无前缀） | string   | 是       |

#### 5.3 请求体（路径参数）

| 参数名   | 参数说明       | 参数类型 | 是否必填 |
| -------- | -------------- | -------- | -------- |
| configId | 设置项主键 ID  | long     | 是       |

#### 5.4 响应示例
`Result<Boolean>`

---

### 6. 刷新系统设置缓存

#### 6.1 基本信息
**请求接口：** `/sys/system-config/refresh`
**请求方式：** POST
**所需权限：** `system:systemConfig:refresh`
**基本信息：** 清空 Redis 中全部系统设置缓存，并立即从数据库重新预热全部启用项。与字典刷新（前端本地缓存）不同，系统设置缓存在后端 Redis

#### 6.2 请求头
| 参数名 | 参数说明           | 参数类型 | 是否必填 |
| ------ | ------------------ | -------- | -------- |
| Token  | JWT 令牌（无前缀） | string   | 是       |

#### 6.3 请求体
无

#### 6.4 响应示例
`Result<Boolean>`

---

### 7. 获取全部启用系统设置（前端启动加载）

#### 7.1 基本信息
**请求接口：** `/sys/system-config/list-all`
**请求方式：** GET
**所需权限：** 需要登录（不加按钮权限，公共读取，对标字典 `GET /sys/dist/data/type/{dictKey}`）
**基本信息：** 返回全部启用状态（status=1）的系统设置项，不分页。供前端登录后全量加载到内存缓存，页面/组件按 configKey 读取，对标字典启动加载

#### 7.2 请求头
| 参数名 | 参数说明           | 参数类型 | 是否必填 |
| ------ | ------------------ | -------- | -------- |
| Token  | JWT 令牌（无前缀） | string   | 是       |

#### 7.3 请求体
无

#### 7.4 响应示例
`Result<List<SysConfigVo>>`

```json
{
  "code": 200,
  "msg": "请求成功",
  "data": [
    {
      "configId": 1,
      "configKey": "sys.user.initPassword",
      "configName": "用户初始密码",
      "configValue": "123456",
      "valueType": "STRING",
      "isSystem": 1,
      "remark": "新建用户与重置密码时的初始密码",
      "status": 1,
      "createTime": "2026-07-07 10:00:00",
      "updateTime": "2026-07-07 10:00:00"
    }
  ]
}
```
