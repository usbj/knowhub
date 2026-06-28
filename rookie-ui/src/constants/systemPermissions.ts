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
} as const
