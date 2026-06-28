-- rookie 操作日志建表脚本
-- 设计说明：
-- 1. 操作日志记录"谁、在哪个接口、什么设备环境、做了什么、结果如何、耗时"
-- 2. 日志只追加、不可改，故不继承 BaseEntity 的 update_* 语义，使用 oper_name/oper_time 作为审计列
-- 3. status 用 TINYINT 0/1（0正常 1异常），business_type/device_type 用 VARCHAR 字符串，统一走字典系统驱动前端标签映射
-- 4. 失败操作通过 sys_error_log.oper_log_id 反向关联，错误详情不在此表重复记录

CREATE TABLE IF NOT EXISTS `sys_oper_log` (
  `oper_id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '日志主键',
  `title`           VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '模块标题（@Log 的 title）',
  `business_type`   VARCHAR(20)  NOT NULL DEFAULT 'OTHER' COMMENT '业务类型：OTHER INSERT UPDATE DELETE GRANT EXPORT IMPORT CLEAN',
  `method`          VARCHAR(200) NOT NULL DEFAULT '' COMMENT '方法名（类名.方法名）',
  `request_method`  VARCHAR(10)  NOT NULL DEFAULT '' COMMENT '请求方式 GET/POST/PUT/DELETE',
  `oper_name`       VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '操作人员（用户名）',
  `oper_url`        VARCHAR(255) NOT NULL DEFAULT '' COMMENT '请求URL',
  `oper_ip`         VARCHAR(128) NOT NULL DEFAULT '' COMMENT '操作主机IP',
  `oper_os`         VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '操作系统（UA解析）',
  `oper_browser`    VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '浏览器（UA解析）',
  `device_type`     VARCHAR(20)  NOT NULL DEFAULT '' COMMENT '设备类型：PC/MOBILE/TABLET/UNKNOWN',
  `oper_param`      TEXT NULL COMMENT '请求参数（JSON）',
  `json_result`     TEXT NULL COMMENT '返回结果（JSON，失败时可留空）',
  `status`          TINYINT      NOT NULL DEFAULT 0 COMMENT '操作状态：0正常 1异常',
  `oper_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  `cost_time`       BIGINT      NOT NULL DEFAULT 0 COMMENT '耗时（毫秒）',
  PRIMARY KEY (`oper_id`),
  KEY `idx_oper_log_time`   (`oper_time`),
  KEY `idx_oper_log_name`   (`oper_name`),
  KEY `idx_oper_log_status` (`status`),
  KEY `idx_oper_log_biz`    (`business_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志记录表';
