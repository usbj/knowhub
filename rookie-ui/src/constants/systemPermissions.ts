export const SYSTEM_PERMISSION_KEYS = {
  user: {
    create: ['system:user:add', 'sys:user:add'],
    edit: ['system:user:edit', 'sys:user:edit'],
    status: ['system:user:status', 'sys:user:status'],
    delete: ['system:user:delete', 'sys:user:delete'],
  },
  role: {
    create: ['system:role:add', 'sys:role:add'],
    edit: ['system:role:edit', 'sys:role:edit'],
    status: ['system:role:status', 'sys:role:status'],
    delete: ['system:role:delete', 'sys:role:delete'],
    setDefault: ['system:role:default', 'sys:role:default'],
  },
  menu: {
    create: ['system:menu:add', 'sys:menu:add'],
    edit: ['system:menu:edit', 'sys:menu:edit'],
    status: ['system:menu:status', 'sys:menu:status'],
    delete: ['system:menu:delete', 'sys:menu:delete'],
  },
  dict: {
    create: ['system:dict:add', 'sys:dict:add'],
    edit: ['system:dict:edit', 'sys:dict:edit'],
    delete: ['system:dict:delete', 'sys:dict:delete'],
  },
  notice: {
    create: ['system:notice:add', 'sys:notice:add'],
    edit: ['system:notice:edit', 'sys:notice:edit'],
    delete: ['system:notice:delete', 'sys:notice:delete'],
    publish: ['system:notice:publish', 'sys:notice:publish'],
    revoke: ['system:notice:revoke', 'sys:notice:revoke'],
  },
  noticeGroup: {
    create: ['system:noticeGroup:add', 'sys:noticeGroup:add'],
    edit: ['system:noticeGroup:edit', 'sys:noticeGroup:edit'],
    delete: ['system:noticeGroup:delete', 'sys:noticeGroup:delete'],
    member: ['system:noticeGroup:member', 'sys:noticeGroup:member'],
  },
  operLog: {
    quarry: ['system:operLog:quarry'],
    info: ['system:operLog:info'],
    delete: ['system:operLog:delete'],
    clean: ['system:operLog:clean'],
  },
  errorLog: {
    quarry: ['system:errorLog:quarry'],
    info: ['system:errorLog:info'],
    delete: ['system:errorLog:delete'],
    clean: ['system:errorLog:clean'],
  },
  systemConfig: {
    create: ['system:systemConfig:add', 'sys:systemConfig:add'],
    edit: ['system:systemConfig:edit', 'sys:systemConfig:edit'],
    delete: ['system:systemConfig:delete', 'sys:systemConfig:delete'],
    refresh: ['system:systemConfig:refresh', 'sys:systemConfig:refresh'],
  },
  // ---- knowhub 二开新增业务模块（博客文章 / 受控标签 / 文件存储）----
  // 权限键三段式 knowhub:模块:动作，与 sys_menu 中 knowhub:* 行的 perm_key 首值对齐。
  blog: {
    create: ['knowhub:blog:add'],
    edit: ['knowhub:blog:edit'],
    delete: ['knowhub:blog:delete'],
    publish: ['knowhub:blog:publish'],
    revoke: ['knowhub:blog:revoke'],
    review: ['knowhub:blog:review'],
    info: ['knowhub:blog:info'],
  },
  tag: {
    create: ['knowhub:tag:add'],
    edit: ['knowhub:tag:edit'],
    delete: ['knowhub:tag:delete'],
    info: ['knowhub:tag:info'],
  },
  file: {
    upload: ['knowhub:file:upload'],
    download: ['knowhub:file:download'],
    delete: ['knowhub:file:delete'],
    info: ['knowhub:file:info'],
  },
  // ---- 资源管理（资源推荐）----
  // 权限键三段式 knowhub:resource:动作，与 sys_menu 中 knowhub:resource:* 行对齐。
  // 资源分类管理独立菜单，权限键 knowhub:resource:category:动作。
  resource: {
    create: ['knowhub:resource:add'],
    edit: ['knowhub:resource:edit'],
    delete: ['knowhub:resource:delete'],
    publish: ['knowhub:resource:publish'],
    revoke: ['knowhub:resource:revoke'],
    review: ['knowhub:resource:review'],
    reviewLog: ['knowhub:resource:reviewLog'],
    download: ['knowhub:resource:download'],
    info: ['knowhub:resource:info'],
  },
  resourceCategory: {
    create: ['knowhub:resource:category:add'],
    edit: ['knowhub:resource:category:edit'],
    delete: ['knowhub:resource:category:delete'],
  },
  // ---- 项目管理（归档记录，等级对标权限）----
  // 权限键三段式 knowhub:project:动作，与 sys_menu 中 knowhub:project:* 行对齐。
  // 等级权限(view/download/edit:l1-l3)由后端 ProjectPermissionResolver 扫 perms 取最高等级判定，
  // 前端 hasPermission 仅用于按钮显隐的进页面门槛；实际可见性/可操作性由后端 SQL 过滤 + canOp 判定。
  // edit/download 不设非等级按钮(纯等级门控)；admin 登录时全 perm_key 已塞入自然得 l3 全权。
  project: {
    create: ['knowhub:project:add'],
    delete: ['knowhub:project:delete'],
    member: ['knowhub:project:member'],
    publish: ['knowhub:project:publish'],
    revoke: ['knowhub:project:revoke'],
    review: ['knowhub:project:review'],
    reviewLog: ['knowhub:project:reviewLog'],
    info: ['knowhub:project:info'],
    // 等级权限（前端按需用 hasPermission 判断等级按钮显隐，实际门控在后端）
    viewL1: ['knowhub:project:view:l1'],
    viewL2: ['knowhub:project:view:l2'],
    viewL3: ['knowhub:project:view:l3'],
    downloadL1: ['knowhub:project:download:l1'],
    downloadL2: ['knowhub:project:download:l2'],
    downloadL3: ['knowhub:project:download:l3'],
    editL1: ['knowhub:project:edit:l1'],
    editL2: ['knowhub:project:edit:l2'],
    editL3: ['knowhub:project:edit:l3'],
  },
} as const
