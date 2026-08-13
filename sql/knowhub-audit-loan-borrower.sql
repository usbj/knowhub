-- knowhub 审计模块批次5：借用人由系统用户改为外部人员内联记录
-- 作用：废弃 audit_loan.borrower_id（原 sys_user.user_id），新增借用人内联信息列
-- 执行：mysql --default-character-set=utf8mb4 < sql/knowhub-audit-loan-borrower.sql
-- 顺序：先改完后端代码与前端字段再跑此 SQL，避免旧代码读不存在的列报错

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

ALTER TABLE `audit_loan`
  DROP INDEX `idx_audit_loan_borrower`,
  DROP COLUMN `borrower_id`,
  ADD COLUMN `borrower_name` VARCHAR(64) NOT NULL COMMENT '借用人姓名（外部人员，非系统用户）' AFTER `expected_return_date`,
  ADD COLUMN `borrower_phone` VARCHAR(32) NOT NULL COMMENT '借用人联系电话' AFTER `borrower_name`,
  ADD COLUMN `borrower_org` VARCHAR(128) NULL DEFAULT NULL COMMENT '借用人所属单位/部门（实验室/班级/外单位，选填）' AFTER `borrower_phone`,
  ADD COLUMN `borrower_remark` VARCHAR(255) NULL DEFAULT NULL COMMENT '借用人补充备注（选填）' AFTER `borrower_org`,
  ADD INDEX `idx_audit_loan_borrower_name` (`borrower_name`) COMMENT '借用人姓名筛选索引';

SET FOREIGN_KEY_CHECKS = 1;