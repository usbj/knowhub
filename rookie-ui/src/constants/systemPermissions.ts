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
  // 查看等级权限(view:l1-l3)由后端 BlogPermissionResolver 扫 perms 取最高等级判定，
  // 前端 hasPermission 仅按钮显隐门槛；列表实际可见性由后端 SQL 过滤(level<=userViewLevel OR author_id=userId)。
  // 编辑/发布/撤回不分等级：仅作者本人 OR 超级管理员可改(后端 canEditBlog 强判 isAuthor||isAdmin)，
  //   前端编辑/发布/撤回按钮 visible 按 row.authorId===当前用户 OR isAdmin 显隐(permKey 仅作进页面门槛)。
  // 删除走独立 knowhub:blog:delete 按钮权限(admin 走框架短路全权)；review 走按钮权限+审核员回避。
  blog: {
    create: ['knowhub:blog:add'],
    edit: ['knowhub:blog:edit'],
    delete: ['knowhub:blog:delete'],
    publish: ['knowhub:blog:publish'],
    revoke: ['knowhub:blog:revoke'],
    review: ['knowhub:blog:review'],
    info: ['knowhub:blog:info'],
    // 查看等级权限（前端按需用 hasPermission 判断等级按钮显隐，实际可见性门控在后端 SQL）
    viewL1: ['knowhub:blog:view:l1'],
    viewL2: ['knowhub:blog:view:l2'],
    viewL3: ['knowhub:blog:view:l3'],
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
  // ---- 文章管理（章节集合，等级对标权限，无成员表/无下载）----
  // 权限键三段式 knowhub:article:动作，与 sys_menu 中 knowhub:article:* 行对齐。
  // 等级权限(view/edit:l1-l3)由后端 ArticlePermissionResolver 扫 perms 取最高等级判定，
  // 前端 hasPermission 仅按钮显隐门槛；实际可见性/可操作性由后端 SQL 过滤 + canOp 判定。
  // edit 不设非等级按钮(纯等级门控)；admin 登录时全 perm_key 已塞入自然得 l3 全权。
  // 编辑复用 add 权限键(与项目一致，无独立 edit 键)；无 download(文章无下载)；无 member(无成员表)。
  article: {
    create: ['knowhub:article:add'],
    delete: ['knowhub:article:delete'],
    publish: ['knowhub:article:publish'],
    revoke: ['knowhub:article:revoke'],
    review: ['knowhub:article:review'],
    reviewLog: ['knowhub:article:reviewLog'],
    info: ['knowhub:article:info'],
    quarry: ['knowhub:article:quarry'],
    // 等级权限（前端按需用 hasPermission 判断等级按钮显隐，实际门控在后端）
    viewL1: ['knowhub:article:view:l1'],
    viewL2: ['knowhub:article:view:l2'],
    viewL3: ['knowhub:article:view:l3'],
    editL1: ['knowhub:article:edit:l1'],
    editL2: ['knowhub:article:edit:l2'],
    editL3: ['knowhub:article:edit:l3'],
  },
  // ---- 章节管理（文章子模块，无独立菜单页，从文章列表点"章节"跳二级路由页）----
  // 权限键三段式 knowhub:chapter:动作，挂文章菜单(menu_id=136)下作隐形 menu_type=3（不渲染为菜单项）。
  // 章节编辑/审核实际可见性由后端按章节可见性=文章可见性 + 提交者/作者归属判定，前端仅按钮显隐门槛。
  chapter: {
    create: ['knowhub:chapter:add'],
    delete: ['knowhub:chapter:delete'],
    publish: ['knowhub:chapter:publish'],
    revoke: ['knowhub:chapter:revoke'],
    review: ['knowhub:chapter:review'],
    reviewLog: ['knowhub:chapter:reviewLog'],
    info: ['knowhub:chapter:info'],
    quarry: ['knowhub:chapter:quarry'],
  },
  // ---- 审计模块（花销/借出/经费核算，纯后台 admin，不分等级）----
  // 权限键三段式 knowhub:audit:{模块}:{动作}，与 sys_menu 中 knowhub:audit:* 行对齐
  // （菜单 menu_id 162-195，见 sql/knowhub-audit.sql）。
  // 审计内部使用无 :l1-3 等级；admin 登录时全 perm_key 已塞入，@PreAuthorize 兜底。
  // flow:review 键即 submit（提交审批同键复用）；loan:review/loan:overdue 键存在但后端路由用 approve/reject/return，
  //   仍在此声明以备按钮门控的进页面门槛对齐。
  audit: {
    subject: {
      quarry: ['knowhub:audit:subject:quarry'],
      info: ['knowhub:audit:subject:info'],
      add: ['knowhub:audit:subject:add'],
      edit: ['knowhub:audit:subject:edit'],
      delete: ['knowhub:audit:subject:delete'],
    },
    flow: {
      quarry: ['knowhub:audit:flow:quarry'],
      info: ['knowhub:audit:flow:info'],
      add: ['knowhub:audit:flow:add'],
      edit: ['knowhub:audit:flow:edit'],
      delete: ['knowhub:audit:flow:delete'],
      // review 键即 submit（提交审批同键复用）
      review: ['knowhub:audit:flow:review'],
      approve: ['knowhub:audit:flow:approve'],
      reject: ['knowhub:audit:flow:reject'],
      revoke: ['knowhub:audit:flow:revoke'],
      reviewLog: ['knowhub:audit:flow:reviewLog'],
    },
    loan: {
      quarry: ['knowhub:audit:loan:quarry'],
      info: ['knowhub:audit:loan:info'],
      add: ['knowhub:audit:loan:add'],
      edit: ['knowhub:audit:loan:edit'],
      delete: ['knowhub:audit:loan:delete'],
      approve: ['knowhub:audit:loan:approve'],
      reject: ['knowhub:audit:loan:reject'],
      return: ['knowhub:audit:loan:return'],
      reviewLog: ['knowhub:audit:loan:reviewLog'],
      // 声明性按钮键，后端路由未直接用，备前端按钮门控对齐
      review: ['knowhub:audit:loan:review'],
      overdue: ['knowhub:audit:loan:overdue'],
    },
    report: {
      quarry: ['knowhub:audit:report:quarry'],
      info: ['knowhub:audit:report:info'],
      regenerate: ['knowhub:audit:report:regenerate'],
    },
  },
  online: {
    quarry: ['system:online:quarry'],
    kick: ['system:online:kick'],
  },
  job: {
    quarry: ['system:job:quarry'],
    info: ['system:job:info'],
    create: ['system:job:add', 'sys:job:add'],
    edit: ['system:job:edit', 'sys:job:edit'],
    delete: ['system:job:delete', 'sys:job:delete'],
    status: ['system:job:status', 'sys:job:status'],
    run: ['system:job:run', 'sys:job:run'],
  },
  jobLog: {
    quarry: ['system:jobLog:quarry'],
  },
  monitor: {
    quarry: ['system:monitor:quarry'],
  },
} as const
