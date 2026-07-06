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
} as const
