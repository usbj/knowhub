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
} as const
