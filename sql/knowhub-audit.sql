-- =============================================================================
-- knowhub 审计模块（花销记录与事务管理） 数据库脚本
-- -----------------------------------------------------------------------------
-- 背景：
--   审计模块面向实验室内部管理，纯后台 admin（前台不接入）。三流合一记一张
--   流水表，对应初稿 §7/§10.2"经费支出记录/事项登记/台账归档"，初稿无表结构
--   设计，本脚本为首次落地。
--
--   用户实验室经费模型（设计核心）：
--   经费不是被动扣减的预算账户，而是有进有出的资金池主体(audit_subject)，进账
--   分"预算(BUDGET 计划额度未必到账)"与"收账(INCOME 实到钱，奖金/外部赞助)"
--   两线，出账是"花销(EXPENSE)"，当前结余只算实到钱不算计划预算。
--   - audit_subject.budget_total 累加 BUDGET；income_total 累加 INCOME。
--   - 不存 expense_total（已花费按当月聚合算，不存总计）；balance 查时算
--     = income_total − 历史已通过 EXPENSE 合计。
--   - 花销走阈值审批（低于 sys_config 阈值自动 APPROVED，高于走 PENDING→审核）；
--     BUDGET/INCOME 只记账留痕不走审批（status 恒 APPROVED）。
--   - 物品借出 audit_loan 独立表 + 审批流水；归还时若产生损耗则插一条
--     flow_type=EXPENSE category=损耗 走花销审批，related_flow_id 指回。
--   - 月度/周记报表 audit_period_report 合一表，定时任务生成，生成后通知负责人。
--     重算接口仅允许重算已结束期（当期 period_end >= now 拒绝）。
--
--   权限不分等级（内部使用）：按钮权限键 knowhub:audit:{action}，无 :l1-3，
--   不建 PermissionResolver。admin 登录全 perm_key 塞入。
--
-- 前置依赖：sql/rookie.sql、sql/knowhub-storage.sql（file_object 表 +
--   file_business_type 字典 dict_id=15）、sql/knowhub-blog-review-log.sql
--   （review_action 字典 dict_id=25 + ReviewAction 枚举）、sql/knowhub-resource.sql
--   （review_status 字典 dict_id=14）。
-- 运行库：knowhub（与 application.yml 中 url 一致）。
-- 幂等：建表 DROP IF EXISTS；字典/菜单/sys_config 用 INSERT IGNORE。
-- 编号（续编，避开已占段；实际数据库 MAX(menu_id)=161、MAX(dict_id)=34、
--   MAX(dict_data_id)=143、MAX(config_id)=12，本脚本从 162/35/144 起，config_id 自增）：
--   菜单 menu_id：162(审计目录) + 163(主体页)+164-168(5按钮)
--                  + 169(流水页)+170-179(10按钮:quarry/info/add/edit/delete/review
--                                       /reviewLog/approve/reject/revoke)
--                  + 180(借出页)+181-191(11按钮:quarry/info/add/edit/delete/review
--                                       /reviewLog/approve/reject/return)
--                  + 192(报表页)+193-195(3按钮:quarry/info/regenerate)
--                  共 34 条（162-195）
--   字典 dict_id：35(subject_scope) + 36(flow_type) + 37(flow_status)
--                + 38(expense_category) + 39(loan_item_type) + 40(loan_status)
--                + 41(period_type)
--   dict_data_id：144-145(subject_scope 2) + 146-148(flow_type 3)
--                + 149-153(flow_status 5) + 154-160(expense_category 7)
--                + 161-162(loan_item_type 2) + 163-167(loan_status 5)
--                + 168-169(period_type 2) + 170(AUDIT_VOUCHER 续编 file_business_type)
--   sys_config config_id 自增，config_key=knowhub.audit.* (5 项)
--  =============================================================================
SET NAMES utf8mb4;
USE `knowhub`;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------------------------------------------------------
-- 1. 花销主体表 audit_subject（资金池，LAB 实验室级 / PROJECT 项目赛事级）
--    budget_total/income_total 存累计冗余列（增长慢、看板常显）；不存 expense_total
--    （已花费按当月聚合算）；balance 不存列，查时算 income_total-历史APPROVED花销合计。
--    handler_id 为负责人 userId（通知接收人 + 模块内归属）。
--    项目赛事主体 scope=PROJECT 时 project_id 关联赛事项目（可空，有些预算不干项目）。
--    审计列 create_by/update_by 存 username 快照，handler_id 存 userId 稳定锁定。
--    软删 deleted 独立列（不在 BaseEntity，沿用 blog/resource/project 约定）。
--  ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `audit_subject`;
CREATE TABLE `audit_subject` (
  `subject_id`     bigint        NOT NULL AUTO_INCREMENT COMMENT '资金池主体主键',
  `name`           varchar(100)  NOT NULL COMMENT '主体名(实验室总账/某比赛子账)',
  `scope`          varchar(20)   NOT NULL DEFAULT 'LAB' COMMENT '主体范围:LAB实验室/PROJECT项目赛事(字典 audit_subject_scope)',
  `project_id`     bigint        DEFAULT NULL COMMENT '关联赛事项目ID(scope=PROJECT时填,可空,有些预算不干项目)',
  `budget_total`   decimal(14,2) NOT NULL DEFAULT 0.00 COMMENT '预算累计(计划额度,BUDGET写入时累加,未必到账)',
  `income_total`   decimal(14,2) NOT NULL DEFAULT 0.00 COMMENT '实到累计(INCOME写入时累加,真金白银)',
  `handler_id`     bigint        NOT NULL COMMENT '负责人userId(报表通知接收人+模块内归属)',
  `status`         varchar(20)   NOT NULL DEFAULT 'ACTIVE' COMMENT '主体状态:ACTIVE活跃/CLOSED关闭',
  `note`           varchar(255)  DEFAULT NULL COMMENT '备注说明',
  `create_by`      varchar(64)   NOT NULL COMMENT '创建人(username)',
  `create_time`    datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`      varchar(64)   NOT NULL COMMENT '更新人',
  `update_time`    datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        tinyint       NOT NULL DEFAULT 0 COMMENT '软删:0未删1已删',
  PRIMARY KEY (`subject_id`),
  KEY `idx_audit_subject_scope`   (`scope`),
  KEY `idx_audit_subject_handler` (`handler_id`),
  KEY `idx_audit_subject_status`  (`status`),
  KEY `idx_audit_subject_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='花销主体(资金池,LAB实验室级/PROJECT项目赛事级,预算+收入累计,花销聚合算)';

-- ----------------------------------------------------------------------------
-- 2. 资金流水表 audit_fund_flow（预算/收账/花销三流合一）
--    flow_type 区分 BUDGET/INCOME/EXPENSE，共用一张表便于时间线对齐与图表聚合。
--    BUDGET 写入事务内 subject.budget_total+=amount；INCOME 写入 income_total+=amount；
--    EXPENSE APPROVED 不动累计列（花销聚合算），仅记流水。
--    category 为分组列：EXPENSE 是耗材/差旅/奖金等花销分类，BUDGET/INCOME 复用记
--    来源/用途分组（赞助/赛事拨款/捐赠等），减少冗余列。
--    status 仅 EXPENSE 走审批(DRAFT/PENDING/APPROVED/REJECTED/REVOKED)，
--    BUDGET/INCOME 恒 APPROVED；review_status 复用 NONE/PENDING/APPROVED/REJECTED。
--    voucher_object_id 关联 file_object.object_id（票据附件，business_type=AUDIT_VOUCHER）。
--    审计列 + 软删 deleted。
--  ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `audit_fund_flow`;
CREATE TABLE `audit_fund_flow` (
  `flow_id`          bigint        NOT NULL AUTO_INCREMENT COMMENT '流水主键',
  `subject_id`       bigint        NOT NULL COMMENT 'FK→audit_subject(关联资金池主体)',
  `flow_type`        varchar(20)   NOT NULL COMMENT '流水类型:BUDGET预算/INCOME收账/EXPENSE花销(字典 audit_flow_type)',
  `amount`           decimal(14,2) NOT NULL COMMENT '金额(元,DECIMAL14,2)',
  `occur_date`       date          NOT NULL COMMENT '发生日期(预算注入日/收入到账日/花销发生日)',
  `category`         varchar(20)   DEFAULT NULL COMMENT '分组:EXPENSE花销分类(耗材/差旅/奖金/损耗...),BUDGET/INCOME来源用途(赞助/赛事拨款/捐赠),复用此列减少冗余',
  `handler_id`       bigint        NOT NULL COMMENT '经办人userId',
  `voucher_object_id` bigint       DEFAULT NULL COMMENT '票据附件file_object.object_id(business_type=AUDIT_VOUCHER)',
  `note`             varchar(255)  DEFAULT NULL COMMENT '相关记录说明(留痕对账用)',
  `status`           varchar(20)   NOT NULL DEFAULT 'APPROVED' COMMENT '流水状态:DRAFT草稿/PENDING待审/APPROVED已通过/REJECTED已驳回/REVOKED已撤回(字典 audit_flow_status;BUDGET/INCOME恒APPROVED)',
  `review_status`    varchar(16)   NOT NULL DEFAULT 'NONE' COMMENT '审核状态:NONE/PENDING/APPROVED/REJECTED(复用字典 review_status)',
  `create_by`        varchar(64)   NOT NULL COMMENT '创建人(username)',
  `create_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`        varchar(64)   NOT NULL COMMENT '更新人',
  `update_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`          tinyint       NOT NULL DEFAULT 0 COMMENT '软删:0未删1已删',
  PRIMARY KEY (`flow_id`),
  KEY `idx_audit_flow_subject`   (`subject_id`, `occur_date`),
  KEY `idx_audit_flow_type`      (`flow_type`),
  KEY `idx_audit_flow_status`    (`status`),
  KEY `idx_audit_flow_handler`   (`handler_id`),
  KEY `idx_audit_flow_deleted`   (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资金流水(预算/收账/花销三流合一,花销走阈值审批,票据附件关联file_object)';

-- ----------------------------------------------------------------------------
-- 3. 花销审批流水表 audit_flow_review_log（只追加不改不删）
--    照 blog_review_log/resource_review_log 范式，被审对象换 flow_id。
--    action 复用 ReviewAction 枚举 + review_action 字典(dict_id=25)：
--    SUBMIT(提交/AUTHOR)/APPROVE(通过/REVIEWER)/REJECT(驳回/REVIEWER)/
--    REVOKE(撤回/AUTHOR)。阈值审批低于阈值自动 APPROVED 时记 SUBMIT+APPROVE 两条。
--    operator_id 用 userId 锁定，operator 存 username 快照；role 按动作类型定。
--    不继承 BaseEntity（流水无 updateBy/updateTime，只有动作时间 createTime）。
--  ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `audit_flow_review_log`;
CREATE TABLE `audit_flow_review_log` (
  `review_log_id` bigint       NOT NULL AUTO_INCREMENT COMMENT '审核流水主键',
  `flow_id`       bigint       NOT NULL COMMENT '被审流水ID(FK→audit_fund_flow.flow_id)',
  `action`        varchar(32)  NOT NULL COMMENT '审核动作:SUBMIT提交/APPROVE通过/REJECT驳回/REVOKE撤回(字典 review_action,复用)',
  `operator_id`   bigint       NOT NULL COMMENT '操作人用户ID(userId,稳定锁定)',
  `operator`      varchar(64)  NOT NULL COMMENT '操作人用户名快照(username,便于直读)',
  `role`          varchar(16)  NOT NULL COMMENT '审核业务身份:AUTHOR提交者/REVIEWER审核员/SYSTEM直通(复用 ReviewAction)',
  `advice`        varchar(500) DEFAULT NULL COMMENT '审核意见(驳回必填,通过可选)',
  `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '动作时间',
  PRIMARY KEY (`review_log_id`),
  KEY `idx_afrl_flow_time`    (`flow_id`, `create_time`),
  KEY `idx_afrl_operator_time`(`operator_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='花销审批流水(不可变历史,复用 review_action 字典)';

-- ----------------------------------------------------------------------------
-- 4. 物品借出表 audit_loan
--    itemType 区分 ASSET 资产(有资产编号)/CONSUMABLE 耗材；borrower_id 借用人。
--    status 流转:REQUEST申请待审/BORROWED已借出/RETURNED已归还/OVERDUE逾期/REJECTED驳回。
--    走审批时 REQUEST→(approve)→BORROWED→(return)→RETURNED；免审时直接 BORROWED→RETURNED。
--    逾期由 AuditLoanOverdueTask 对账任务扫 expected_return_date < now AND status=BORROWED 置 OVERDUE。
--    related_flow_id 归还时若产生损耗扣费，指向 audit_fund_flow 那条 EXPENSE(损耗)流水。
--    审计列 + 软删 deleted。
--  ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `audit_loan`;
CREATE TABLE `audit_loan` (
  `loan_id`             bigint       NOT NULL AUTO_INCREMENT COMMENT '借出主键',
  `subject_id`          bigint       NOT NULL COMMENT 'FK→audit_subject(关联资金池主体,损耗扣费落该主体)',
  `item_name`           varchar(100) NOT NULL COMMENT '物品名称',
  `item_type`           varchar(20)  NOT NULL COMMENT '物品类型:ASSET资产(有编号)/CONSUMABLE耗材(字典 audit_loan_item_type)',
  `asset_no`            varchar(50)  DEFAULT NULL COMMENT '资产编号(ASSET类型填,耗材可空)',
  `quantity`            int          NOT NULL DEFAULT 1 COMMENT '数量',
  `borrower_id`         bigint       NOT NULL COMMENT '借用人userId',
  `borrow_date`         datetime     NOT NULL COMMENT '借出时间',
  `expected_return_date` datetime    DEFAULT NULL COMMENT '预计归还时间(逾期判定依据,可空=无限期)',
  `actual_return_date`  datetime     DEFAULT NULL COMMENT '实际归还时间(归还时回填)',
  `status`              varchar(20)  NOT NULL DEFAULT 'REQUEST' COMMENT '借出状态:REQUEST申请/BORROWED已借出/RETURNED已归还/OVERDUE逾期/REJECTED驳回(字典 audit_loan_status)',
  `related_flow_id`     bigint       DEFAULT NULL COMMENT '归还损耗扣费指向audit_fund_flow.flow_id(无损耗为空)',
  `voucher_object_id`   bigint       DEFAULT NULL COMMENT '附件file_object.object_id(借出凭证/损耗票据)',
  `note`                varchar(255) DEFAULT NULL COMMENT '备注说明',
  `create_by`           varchar(64)  NOT NULL COMMENT '创建人(username)',
  `create_time`         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`           varchar(64)  NOT NULL COMMENT '更新人',
  `update_time`         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`             tinyint      NOT NULL DEFAULT 0 COMMENT '软删:0未删1已删',
  PRIMARY KEY (`loan_id`),
  KEY `idx_audit_loan_subject` (`subject_id`),
  KEY `idx_audit_loan_borrower`(`borrower_id`),
  KEY `idx_audit_loan_status`  (`status`),
  KEY `idx_audit_loan_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='物品借出(资产/耗材,逾期对账,归还损耗落花销流水)';

-- ----------------------------------------------------------------------------
-- 5. 借出审批流水表 audit_loan_review_log（只追加不改不删，照 audit_flow_review_log 范式）
--    被审对象换 loan_id；action 复用 SUBMIT/APPROVE/REJECT 三值（借出无 revoke 直通语义）。
--  ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `audit_loan_review_log`;
CREATE TABLE `audit_loan_review_log` (
  `review_log_id` bigint       NOT NULL AUTO_INCREMENT COMMENT '审核流水主键',
  `loan_id`       bigint       NOT NULL COMMENT '被审借出ID(FK→audit_loan.loan_id)',
  `action`        varchar(32)  NOT NULL COMMENT '审核动作:SUBMIT提交/APPROVE通过/REJECT驳回(字典 review_action,复用三值)',
  `operator_id`   bigint       NOT NULL COMMENT '操作人用户ID(userId)',
  `operator`      varchar(64)  NOT NULL COMMENT '操作人用户名快照(username)',
  `role`          varchar(16)  NOT NULL COMMENT '审核业务身份:AUTHOR借用人/REVIEWER审核员(复用 ReviewAction)',
  `advice`        varchar(500) DEFAULT NULL COMMENT '审核意见',
  `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '动作时间',
  PRIMARY KEY (`review_log_id`),
  KEY `idx_alrl_loan_time`     (`loan_id`, `create_time`),
  KEY `idx_alrl_operator_time` (`operator_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='借出审批流水(不可变历史,复用 review_action 字典三值)';

-- ----------------------------------------------------------------------------
-- 6. 月度/周记报表表 audit_period_report（合一，定时任务生成）
--    period_type 区分 MONTH/WEEK；period_key MONTH:'2026-07'/WEEK:'2026-W32'。
--    UNIQUE(subject_id,period_type,period_key) 重算覆盖同一期。
--    budget/income/expense 按期内 flow 聚合；balance_end 期末结余；expense_by_category
--    存 JSON 按花销分类汇总；loan_out_count 本期借出笔数，loan_unreturned 期末未归还。
--    报表由定时任务生成后通知负责人(handler_id)；重算接口仅允许已结束期(period_end<now)。
--    审计列 + 软删 deleted。
--  ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `audit_period_report`;
CREATE TABLE `audit_period_report` (
  `report_id`         bigint        NOT NULL AUTO_INCREMENT COMMENT '报表主键',
  `subject_id`        bigint        NOT NULL COMMENT 'FK→audit_subject(关联资金池主体)',
  `period_type`       varchar(10)   NOT NULL COMMENT '周期类型:MONTH月度/WEEK周记(字典 audit_period_type)',
  `period_key`        varchar(20)   NOT NULL COMMENT '周期键:MONTH"2026-07"/WEEK"2026-W32"',
  `period_start`      date          NOT NULL COMMENT '周期开始日',
  `period_end`        date          NOT NULL COMMENT '周期结束日(重算校验依据:period_end<now 才允许重算)',
  `budget_amount`     decimal(14,2) NOT NULL DEFAULT 0.00 COMMENT '本期BUDGET合计(计划额度注入)',
  `income_amount`     decimal(14,2) NOT NULL DEFAULT 0.00 COMMENT '本期INCOME合计(实到)',
  `expense_amount`    decimal(14,2) NOT NULL DEFAULT 0.00 COMMENT '本期APPROVED EXPENSE合计(已花)',
  `expense_by_category` text        DEFAULT NULL COMMENT '本期花销按分类汇总JSON,如{"耗材":100,"差旅":200}',
  `balance_end`       decimal(14,2) NOT NULL DEFAULT 0.00 COMMENT '期末结余(income_total-历史APPROVED花销)',
  `loan_out_count`    int           NOT NULL DEFAULT 0 COMMENT '本期借出笔数',
  `loan_unreturned`   int           NOT NULL DEFAULT 0 COMMENT '本期末未归还笔数',
  `generate_time`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成/重算时间',
  `create_by`         varchar(64)   NOT NULL COMMENT '创建人(username,定时任务填system)',
  `create_time`       datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`         varchar(64)   NOT NULL COMMENT '更新人',
  `update_time`       datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`           tinyint       NOT NULL DEFAULT 0 COMMENT '软删:0未删1已删',
  PRIMARY KEY (`report_id`),
  UNIQUE KEY `uk_audit_report_subject_period` (`subject_id`, `period_type`, `period_key`),
  KEY `idx_audit_report_type`  (`period_type`),
  KEY `idx_audit_report_period`(`period_start`, `period_end`),
  KEY `idx_audit_report_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='月度/周记报表(合一,定时任务生成,UNIQUE覆盖重算,生成后通知负责人)';

-- ============================================================================
-- 菜单与权限：sys_menu（三层结构：目录→页面→按钮）
--    父目录复用已有 knowhub(menu_id=63)，审计目录(162)挂其下。
--    权限不分等级（内部使用），按钮权限键 knowhub:audit:{action}，无 :l1-3。
--    admin 登录时全 perm_key 塞入，零特判。
--  ============================================================================
-- 审计目录(menu_type=1),挂在 knowhub 目录(63)下。
--   注意：sys_menu 的 `path` 列在本框架里被前端 dynamicRoutes.resolveViewComponent 当"组件定位
--   字段"用（去前导斜杠后试 views/{path}.vue / views/{path}/index.vue，命中失败回退占位页），
--   而非路由 path（路由 path 用 `route` 列，目录是 'audit'）。`component` 列在此库存的是图标名
--   （Wallet/Money/Box/Document 等），不是组件路径——勿照字面误解。
--   目录菜单点开应是侧边栏分组（展开子页），不进页面：menu_type=1 且 path 为空时 component 取
--   RouteView（router-view 壳），故目录 162 的 path 置 NULL（勿指 /knowhub/audit/index，那会去找
--   views/knowhub/audit/index.vue，而该目录下只有 subject/flow/loan/report 子页，没有 index.vue，
--   命中失败回退 PlaceholderView 灰页）。子页 163/169/180/192 的 path 指各自 views 下的 index.vue 命中。
INSERT IGNORE INTO `sys_menu` VALUES (162,'审计管理','knowhub:audit',63,1,'audit',0,NULL,'Audit',1,'admin',NOW(),'admin',NOW(),0);
-- 主体管理页(menu_type=2) + 按钮(menu_type=3)
INSERT IGNORE INTO `sys_menu` VALUES (163,'花销主体','knowhub:audit:subject',162,2,'audit-subject',0,'/knowhub/audit/subject/index','Wallet',1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (164,'主体查询','knowhub:audit:subject:quarry',163,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (165,'主体详情','knowhub:audit:subject:info',163,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (166,'主体新增','knowhub:audit:subject:add',163,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (167,'主体编辑','knowhub:audit:subject:edit',163,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (168,'主体删除','knowhub:audit:subject:delete',163,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
-- 流水管理页(menu_type=2) + 按钮(menu_type=3,含审批四个动作)
INSERT IGNORE INTO `sys_menu` VALUES (169,'资金流水','knowhub:audit:flow',162,2,'audit-flow',0,'/knowhub/audit/flow/index','Money',1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (170,'流水查询','knowhub:audit:flow:quarry',169,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (171,'流水详情','knowhub:audit:flow:info',169,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (172,'流水新增','knowhub:audit:flow:add',169,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (173,'流水编辑','knowhub:audit:flow:edit',169,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (174,'流水删除','knowhub:audit:flow:delete',169,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (175,'流水提交','knowhub:audit:flow:review',169,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (176,'流水审核记录','knowhub:audit:flow:reviewLog',169,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (177,'流水通过','knowhub:audit:flow:approve',169,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (178,'流水驳回','knowhub:audit:flow:reject',169,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (179,'流水撤回','knowhub:audit:flow:revoke',169,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
-- 借出管理页(menu_type=2) + 按钮(menu_type=3,含归还动作)
INSERT IGNORE INTO `sys_menu` VALUES (180,'物品借出','knowhub:audit:loan',162,2,'audit-loan',0,'/knowhub/audit/loan/index','Box',1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (181,'借出查询','knowhub:audit:loan:quarry',180,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (182,'借出详情','knowhub:audit:loan:info',180,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (183,'借出新增','knowhub:audit:loan:add',180,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (184,'借出编辑','knowhub:audit:loan:edit',180,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (185,'借出删除','knowhub:audit:loan:delete',180,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (186,'借出提交','knowhub:audit:loan:review',180,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (187,'借出审核记录','knowhub:audit:loan:reviewLog',180,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (188,'借出通过','knowhub:audit:loan:approve',180,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (189,'借出驳回','knowhub:audit:loan:reject',180,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (190,'借出归还','knowhub:audit:loan:return',180,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (191,'借出逾期处理','knowhub:audit:loan:overdue',180,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
-- 报表管理页(menu_type=2) + 按钮(menu_type=3)
INSERT IGNORE INTO `sys_menu` VALUES (192,'周期报表','knowhub:audit:report',162,2,'audit-report',0,'/knowhub/audit/report/index','Document',1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (193,'报表查询','knowhub:audit:report:quarry',192,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (194,'报表详情','knowhub:audit:report:info',192,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (195,'报表重算','knowhub:audit:report:regenerate',192,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);

-- ============================================================================
-- 字典：sys_dict / sys_dict_data
--    续编 dict_id 从 35 起、dict_data_id 从 144 起（实际数据库 MAX(dict_id)=34、MAX(dict_data_id)=143）。
--    review_action(dict_id=25)/review_status(dict_id=14)/file_business_type(dict_id=15) 复用已有。
--    sys_dict 备注列是 remake（框架历史拼写），sys_dict_data 备注列是 remark。
--  ============================================================================
-- 花销主体范围（2 项）
INSERT IGNORE INTO `sys_dict` VALUES (35,'主体范围','audit_subject_scope',1,'资金池主体范围枚举(SubjectScope,LAB实验室级/PROJECT项目赛事级)',NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (144,35,'audit_subject_scope','实验室','LAB','实验室级总账(奖金池/外部捐赠汇总)',1,'primary','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (145,35,'audit_subject_scope','项目赛事','PROJECT','项目/比赛级子账(scope=PROJECT时project_id关联赛事)',2,'success','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');

-- 流水类型（3 项）
INSERT IGNORE INTO `sys_dict` VALUES (36,'流水类型','audit_flow_type',1,'资金流水类型枚举(FlowType,BUDGET预算/INCOME收账/EXPENSE花销)',NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (146,36,'audit_flow_type','预算','BUDGET','预算注入(计划额度,未必到账,累加budget_total,免审)',1,'info','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (147,36,'audit_flow_type','收账','INCOME','收账到账(实到钱,累加income_total,免审)',2,'success','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (148,36,'audit_flow_type','花销','EXPENSE','花销支出(走阈值审批,APPROVED时聚合计入余额扣减)',3,'warning','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');

-- 流水状态（5 项，仅 EXPENSE 用全状态；BUDGET/INCOME 恒 APPROVED）
INSERT IGNORE INTO `sys_dict` VALUES (37,'流水状态','audit_flow_status',1,'流水状态枚举(FlowStatus,DRAFT草稿/PENDING待审/APPROVED已通过/REJECTED已驳回/REVOKED已撤回)',NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (149,37,'audit_flow_status','草稿','DRAFT','花销草稿(未提交审批)',1,'info','light','',NULL,'1',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (150,37,'audit_flow_status','待审核','PENDING','高于阈值花销提交后待审',2,'primary','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (151,37,'audit_flow_status','已通过','APPROVED','审批通过(低阈值自动通过/审核员通过/BUDGET/INCOME恒此态)',3,'success','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (152,37,'audit_flow_status','已驳回','REJECTED','审核员驳回',4,'danger','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (153,37,'audit_flow_status','已撤回','REVOKED','作者撤回已通过花销',5,'warning','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');

-- 花销分类（7 项，EXPENSE 用；BUDGET/INCOME 复用此列时记来源/用途，枚举不强制）
INSERT IGNORE INTO `sys_dict` VALUES (38,'花销分类','audit_expense_category',1,'花销分类枚举(ExpenseCategory,耗材/差旅/报名费/奖金/赞助/损耗/其他)',NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (154,38,'audit_expense_category','耗材','CONSUMABLE','耗材采购',1,'info','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (155,38,'audit_expense_category','差旅','TRAVEL','差旅费',2,'info','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (156,38,'audit_expense_category','报名费','REGISTRATION','比赛报名费',3,'info','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (157,38,'audit_expense_category','奖金','PRIZE','奖金发放',4,'success','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (158,38,'audit_expense_category','赞助','SPONSORSHIP','赞助收支',5,'success','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (159,38,'audit_expense_category','损耗','WEAR','物品归还损耗扣费(借出归还产生)',6,'warning','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (160,38,'audit_expense_category','其他','OTHER','其他花销',7,'info','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');

-- 借出物品类型（2 项）
INSERT IGNORE INTO `sys_dict` VALUES (39,'借出物品类型','audit_loan_item_type',1,'借出物品类型枚举(LoanItemType,ASSET资产有编号/CONSUMABLE耗材)',NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (161,39,'audit_loan_item_type','资产','ASSET','资产类物品(有资产编号,可编号追溯)',1,'primary','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (162,39,'audit_loan_item_type','耗材','CONSUMABLE','耗材类物品(无编号,按数量管理)',2,'info','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');

-- 借出状态（5 项）
INSERT IGNORE INTO `sys_dict` VALUES (40,'借出状态','audit_loan_status',1,'借出状态枚举(LoanStatus,REQUEST申请/BORROWED已借出/RETURNED已归还/OVERDUE逾期/REJECTED驳回)',NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (163,40,'audit_loan_status','申请待审','REQUEST','借出申请待审核(走审批时)',1,'primary','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (164,40,'audit_loan_status','已借出','BORROWED','审批通过或免审,物品已借出',2,'success','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (165,40,'audit_loan_status','已归还','RETURNED','物品已归还',3,'info','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (166,40,'audit_loan_status','逾期','OVERDUE','超过预计归还时间未还(对账任务扫出)',4,'danger','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (167,40,'audit_loan_status','已驳回','REJECTED','借出申请被驳回',5,'danger','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');

-- 周期类型（2 项）
INSERT IGNORE INTO `sys_dict` VALUES (41,'周期类型','audit_period_type',1,'报表周期类型枚举(PeriodType,MONTH月度/WEEK周记)',NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (168,41,'audit_period_type','月度','MONTH','月度报表(每月1日生成上月,period_key"2026-07")',1,'primary','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (169,41,'audit_period_type','周记','WEEK','周记报表(每周一生成上周,period_key"2026-W32")',2,'success','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');

-- ----------------------------------------------------------------------------
-- file_business_type 字典(dict_id=15)续编:审计票据附件
--    全库 dict_data_id 续编位 170（实际数据库该字典现有 61-67+139,170 安全不撞）。
--  ----------------------------------------------------------------------------
INSERT IGNORE INTO `sys_dict_data` VALUES (170,15,'file_business_type','审计票据附件','AUDIT_VOUCHER','花销/借出票据附件,access=PRIVATE,对接 file_object',1,'info','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');

-- ============================================================================
-- 系统设置：sys_config（全局开关/阈值,走 SysConfigUtil 只读 Redis 缓存）
--    花销阈值审批：低于阈值自动 APPROVED（记 SUBMIT+APPROVE 两条流水），高于走 PENDING。
--    阈值是金额(可带小数2位)，用 STRING 类型存"500.00"，AuditConfigReader 用 getString +
--    new BigDecimal 解析(用 NUMBER/getNumber/Long.parseLong 会丢小数精度,金额阈值不可整数化)。
--    借出审批开关：开则借出需审 REQUEST→BORROWED，关则直接 BORROWED。
--    月度/周记报表开关：定时任务生成并通知负责人；关则不自动生成(人工可手动重算生成)。
--    对账间隔(yml 配)与定时任务 cron 走 application.yml(@Scheduled 注解 Bean 创建时解析,
--    只能读 yml/环境变量)，与博客/文章模块同套路。
--  ============================================================================
INSERT IGNORE INTO `sys_config` (`config_key`, `config_name`, `config_value`, `value_type`, `is_system`, `remark`, `status`, `create_time`, `create_by`, `update_time`, `update_by`)
VALUES ('knowhub.audit.expense_approval_enabled', '花销审批开关', 'true', 'BOOLEAN', 1, '花销是否需审批(true开启阈值审批/false免审直接APPROVED,AuditConfigReader.isExpenseApprovalEnabled读取)', 1, NOW(), 'admin', NOW(), 'admin');
INSERT IGNORE INTO `sys_config` (`config_key`, `config_name`, `config_value`, `value_type`, `is_system`, `remark`, `status`, `create_time`, `create_by`, `update_time`, `update_by`)
VALUES ('knowhub.audit.expense_approval_threshold', '花销审批阈值', '500.00', 'STRING', 1, '花销审批阈值(元,STRING存"500.00"字符串,AuditConfigReader.getExpenseThreshold用new BigDecimal解析),低于阈值自动通过高于走PENDING', 1, NOW(), 'admin', NOW(), 'admin');
INSERT IGNORE INTO `sys_config` (`config_key`, `config_name`, `config_value`, `value_type`, `is_system`, `remark`, `status`, `create_time`, `create_by`, `update_time`, `update_by`)
VALUES ('knowhub.audit.loan_approval_enabled', '借出审批开关', 'true', 'BOOLEAN', 1, '物品借出是否需审批(true开REQUEST→BORROWED/false直接BORROWED,AuditConfigReader.isLoanApprovalEnabled读取)', 1, NOW(), 'admin', NOW(), 'admin');
INSERT IGNORE INTO `sys_config` (`config_key`, `config_name`, `config_value`, `value_type`, `is_system`, `remark`, `status`, `create_time`, `create_by`, `update_time`, `update_by`)
VALUES ('knowhub.audit.monthly_report_enabled', '月度报表自动生成开关', 'true', 'BOOLEAN', 1, '每月1日是否自动生成上月月报并通知负责人(true开/false关,关后仍可手动重算)', 1, NOW(), 'admin', NOW(), 'admin');
INSERT IGNORE INTO `sys_config` (`config_key`, `config_name`, `config_value`, `value_type`, `is_system`, `remark`, `status`, `create_time`, `create_by`, `update_time`, `update_by`)
VALUES ('knowhub.audit.weekly_report_enabled', '周记报表自动生成开关', 'true', 'BOOLEAN', 1, '每周一是否自动生成上周周报并通知负责人(true开/false关,关后仍可手动重算)', 1, NOW(), 'admin', NOW(), 'admin');

-- 阈值项 value_type 补丁：首轮以 NUMBER 落库的旧值，在此强制改为 STRING(金额带小数，NUMBER/getNumber 会丢精度)。
--    INSERT IGNORE 不会改已存在键的 value_type，故单独 UPDATE 兜底(幂等，重跑安全)。
UPDATE `sys_config` SET `value_type`='STRING', `remark`='花销审批阈值(元,STRING存"500.00"字符串,AuditConfigReader.getExpenseThreshold用new BigDecimal解析),低于阈值自动通过高于走PENDING' WHERE `config_key`='knowhub.audit.expense_approval_threshold';

-- 审计目录菜单 path 复位补丁：首轮 162 的 path 曾写为 '/knowhub/audit/index'，前端 dynamicRoutes
--    会把它当组件定位字段去找 views/knowhub/audit/index.vue，但该目录下只有 subject/flow/loan/report
--    子页无 index.vue，命中失败回退占位页灰屏。目录菜单(menu_type=1)应作侧边栏分组节点，path 置 NULL
--    后组件回退 RouteView(router-view 壳)展开子页即可。INSERT IGNORE 不覆盖已存在行的 path，故 UPDATE
--    兜底(幂等，重跑安全)。子页 163/169/180/192 的 path 不动(各自命中 views 下的 index.vue)。
UPDATE `sys_menu` SET `path`=NULL WHERE `menu_id`=162 AND `perm_key`='knowhub:audit';

SET FOREIGN_KEY_CHECKS = 1;

-- ----------------------------------------------------------------------------
-- 验证提示（不自动执行，供人工核对）：
--    SHOW TABLES LIKE 'audit%';                          -- 应有 6 张表
--    SELECT dict_id,dict_key FROM sys_dict WHERE dict_id IN (35,36,37,38,39,40,41);
--    SELECT dict_data_value,dict_data_label FROM sys_dict_data
--      WHERE dict_id IN (35,36,37,38,39,40,41) ORDER BY dict_data_id;
--                         -- 应 26 行:2主体范围+3流水类型+5流水状态+7花销分类+2物品类型+5借出状态+2周期
--    SELECT dict_data_value,dict_data_label FROM sys_dict_data WHERE dict_id=15
--      AND dict_data_value='AUDIT_VOUCHER';              -- 应 1 行审计票据附件
--    SELECT config_key,config_value FROM sys_config WHERE config_key LIKE 'knowhub.audit%';
--                         -- 应 5 行
--    SELECT menu_id,menu_name,perm_key FROM sys_menu WHERE perm_key LIKE 'knowhub:audit%'
--      ORDER BY menu_id;   -- 应 35 行:162目录+主体5+流水11+借出12+报表4(192页+3按钮)
--    SELECT menu_id,menu_name,`path`,route FROM sys_menu WHERE menu_id=162;
--                         -- menu_id=162 的 path 应为 NULL，route 为 'audit'
--  ----------------------------------------------------------------------------