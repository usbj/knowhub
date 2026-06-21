-- rookie 消息通知模块建表脚本（分组增强版）
-- 设计说明：
-- 1. 统一使用 sys_notice 作为消息主表，同时承载公告/通知/提醒等类型
-- 2. sys_notice_read 仅记录“已读”的用户，未读不落库，减轻数据量
-- 3. 使用“通知分组 + 分组成员 + 消息分组关联”实现灵活投递
-- 4. 一条消息可关联多个分组；一个分组内可混合用户、角色、群体等多种成员
-- 5. 如果消息是全员可见，则 publish_scope = 'ALL'，无需关联分组

CREATE TABLE IF NOT EXISTS `sys_notice` (
  `notice_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '消息主键',
  `title` VARCHAR(200) NOT NULL COMMENT '标题',
  `content` TEXT NOT NULL COMMENT '正文内容',
  `notice_type` VARCHAR(32) NOT NULL DEFAULT 'NOTICE' COMMENT '消息类型：NOTICE公告 NOTIFY通知 REMIND提醒',
  `level` VARCHAR(32) NOT NULL DEFAULT 'NORMAL' COMMENT '消息级别：NORMAL普通 IMPORTANT重要 URGENT紧急',
  `publish_scope` VARCHAR(32) NOT NULL DEFAULT 'ALL' COMMENT '发布范围：ALL全员 GROUP分组',
  `status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT草稿 PUBLISHED已发布 REVOKED已撤回',
  `is_top` TINYINT NOT NULL DEFAULT 0 COMMENT '是否置顶：0否 1是',
  `need_confirm` TINYINT NOT NULL DEFAULT 0 COMMENT '是否需要确认：0否 1是',
  `publish_time` DATETIME NULL COMMENT '发布时间',
  `expire_time` DATETIME NULL COMMENT '过期时间',
  `route_path` VARCHAR(255) NULL COMMENT '前端路由路径',
  `remark` VARCHAR(500) NULL COMMENT '备注',
  `create_by` VARCHAR(64) NOT NULL COMMENT '创建人',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` VARCHAR(64) NOT NULL COMMENT '更新人',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `delete` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记：0未删除 1已删除',
  PRIMARY KEY (`notice_id`),
  KEY `idx_notice_type` (`notice_type`),
  KEY `idx_notice_status` (`status`),
  KEY `idx_notice_scope` (`publish_scope`),
  KEY `idx_notice_publish_time` (`publish_time`),
  KEY `idx_notice_delete` (`delete`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息通知主表';


CREATE TABLE IF NOT EXISTS `sys_notice_read` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '已读记录主键',
  `notice_id` BIGINT NOT NULL COMMENT '消息主键',
  `user_id` BIGINT NOT NULL COMMENT '用户主键',
  `read_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '已读时间',
  `confirm_status` TINYINT NOT NULL DEFAULT 0 COMMENT '确认状态：0未确认 1已确认',
  `confirm_time` DATETIME NULL COMMENT '确认时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notice_user` (`notice_id`, `user_id`),
  KEY `idx_notice_read_user` (`user_id`),
  KEY `idx_notice_read_notice` (`notice_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息通知已读记录表';


CREATE TABLE IF NOT EXISTS `sys_notice_group` (
  `group_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '通知分组主键',
  `group_name` VARCHAR(100) NOT NULL COMMENT '通知分组名称',
  `group_code` VARCHAR(100) NOT NULL COMMENT '通知分组编码',
  `group_desc` VARCHAR(255) NULL COMMENT '通知分组描述',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0停用 1启用',
  `create_by` VARCHAR(64) NOT NULL COMMENT '创建人',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` VARCHAR(64) NOT NULL COMMENT '更新人',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`group_id`),
  UNIQUE KEY `uk_notice_group_code` (`group_code`),
  KEY `idx_notice_group_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息通知分组表';


CREATE TABLE IF NOT EXISTS `sys_notice_group_member` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '分组成员主键',
  `group_id` BIGINT NOT NULL COMMENT '通知分组主键',
  `user_id` BIGINT NOT NULL COMMENT '用户主键',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notice_group_member` (`group_id`, `user_id`),
  KEY `idx_notice_group_member_group` (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息通知分组成员表';


CREATE TABLE IF NOT EXISTS `sys_notice_group_rel` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '消息分组关联主键',
  `notice_id` BIGINT NOT NULL COMMENT '消息主键',
  `group_id` BIGINT NOT NULL COMMENT '通知分组主键',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notice_group_rel` (`notice_id`, `group_id`),
  KEY `idx_notice_group_rel_notice` (`notice_id`),
  KEY `idx_notice_group_rel_group` (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息通知与分组关联表';


-- 可选字典初始化（如你的字典体系已启用，可按需执行）
-- notice_type: NOTICE公告 NOTIFY通知 REMIND提醒
-- notice_status: DRAFT草稿 PUBLISHED已发布 REVOKED已撤回
-- publish_scope: ALL全员 GROUP分组
-- member_type: USER用户 ROLE角色 GROUP群体 POST岗位 TAG标签 TENANT租户等
