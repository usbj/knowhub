-- rookie 错误日志建表脚本
-- 设计说明：
-- 1. 错误日志只记"来源 + 关联 + 异常本身 + 时间 + 操作人"；HTTP 环境信息(IP/OS/浏览器/请求参数)统一归操作日志记录，此处不重复
-- 2. source_type 区分错误来源：请求触发/定时任务/异步任务/事件监听/启动初始化/其他，非 REQUEST 来源的请求类字段天然为空
-- 3. oper_log_id 关联到 sys_oper_log.oper_id，仅 REQUEST 来源且接口带 @Log 时可能有值；通过该列可从操作日志跳转到错误日志
-- 4. exception_stack 记完整堆栈(LONGTEXT)，是错误日志核心价值

CREATE TABLE IF NOT EXISTS `sys_error_log` (
  `error_id`        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '错误日志主键',
  `source_type`     VARCHAR(20)  NOT NULL DEFAULT 'REQUEST' COMMENT '错误来源：REQUEST请求触发 SCHEDULED定时任务 ASYNC异步任务 EVENT事件监听 INIT启动初始化 OTHER其他',
  `oper_log_id`     BIGINT       NULL COMMENT '关联操作日志ID（仅REQUEST来源且接口带@Log时可能有值）',
  `title`           VARCHAR(255) NOT NULL DEFAULT '' COMMENT '错误简述：请求来源填URL，定时任务填任务名，异步任务填方法名等',
  `oper_name`       VARCHAR(50)  NULL COMMENT '操作人员（请求来源且有登录态时填；其他来源为空）',
  `exception_type`  VARCHAR(255) NOT NULL DEFAULT '' COMMENT '异常类全名',
  `exception_msg`   TEXT NULL COMMENT '异常消息',
  `exception_stack` LONGTEXT NULL COMMENT '完整堆栈',
  `error_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '错误时间',
  PRIMARY KEY (`error_id`),
  KEY `idx_error_log_time`   (`error_time`),
  KEY `idx_error_log_type`   (`exception_type`),
  KEY `idx_error_log_source` (`source_type`),
  KEY `idx_error_log_name`   (`oper_name`),
  KEY `idx_error_log_oper`   (`oper_log_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统错误日志记录表';
