-- =============================================================================
-- knowhub 定时任务初始化：把 knowhub 现有 8 个定时任务登记进 sys_job 表，
-- 交由 rookie SysJobScheduler（CronTrigger + 独立线程池）统一调度，
-- 支持后台「系统监控→定时任务」注册/启停/立即执行/查日志。
-- -----------------------------------------------------------------------------
-- 前置：sys_job / sys_job_log 建表见 sql/sys_system_monitor.sql（已含菜单与权限点）。
-- 幂等：sys_job 表无唯一键，靠 NOT EXISTS 判 bean_name + method_name 是否已存在，可重复执行。
-- cron 折算（Spring 6 段式，秒 分 时 日 月 周）：
--   原 @Scheduled fixedDelay 折算为固定周期 cron：
--     10min -> 0 */10 * * * ?   5min -> 0 */5 * * * ?   30min -> 0 */30 * * * ?
--   周报原 cron 0 10 0 ? * MON、月报原 cron 0 10 0 1 * * 原样保留（语义完全一致）。
--   说明：fixedDelay 是「上次执行结束后等 N 分钟」，cron 是「固定每 N 分钟周期」，
--        两者语义近似；执行耗时超周期时受 SysJobScheduler 防重拦截，不会并发重入。
-- 字符集：文件 UTF-8 无 BOM，首行 SET NAMES utf8mb4 兜底（见 doc/README.dev.md）。
-- =============================================================================

SET NAMES utf8mb4;

-- 1. 文件对象 GC（原 FileGcTask.gc，fixedDelay 10min）
INSERT INTO `sys_job`
  (`job_name`, `bean_name`, `method_name`, `cron_expression`, `params`, `status`, `remark`,
   `create_by`, `create_time`, `update_by`, `update_time`)
SELECT '文件对象GC', 'fileGcTask', 'gc', '0 */10 * * * ?', NULL, 1,
       '扫描超时未确认 PENDING 行与已软删行，DeleteObject 清 RustFS 对象 + 物理删元数据（原 @Scheduled fixedDelay 10min）',
       'admin', NOW(), 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_job` WHERE `bean_name` = 'fileGcTask' AND `method_name` = 'gc');

-- 2. 博客审核对账（原 BlogReviewReconcileTask.reconcile，fixedDelay 5min）
INSERT INTO `sys_job`
  (`job_name`, `bean_name`, `method_name`, `cron_expression`, `params`, `status`, `remark`,
   `create_by`, `create_time`, `update_by`, `update_time`)
SELECT '博客审核对账', 'blogReviewReconcileTask', 'reconcile', '0 */5 * * * ?', NULL, 1,
       '审核开关关闭后扫描遗留待审博客批量放行（原 @Scheduled fixedDelay 5min）',
       'admin', NOW(), 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_job` WHERE `bean_name` = 'blogReviewReconcileTask' AND `method_name` = 'reconcile');

-- 3. 文章审核对账（原 ArticleReviewReconcileTask.reconcile，fixedDelay 5min）
INSERT INTO `sys_job`
  (`job_name`, `bean_name`, `method_name`, `cron_expression`, `params`, `status`, `remark`,
   `create_by`, `create_time`, `update_by`, `update_time`)
SELECT '文章审核对账', 'articleReviewReconcileTask', 'reconcile', '0 */5 * * * ?', NULL, 1,
       '审核开关关闭后扫描遗留待审文章批量放行（原 @Scheduled fixedDelay 5min）',
       'admin', NOW(), 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_job` WHERE `bean_name` = 'articleReviewReconcileTask' AND `method_name` = 'reconcile');

-- 4. 资源审核对账（原 ResourceReviewReconcileTask.reconcile，fixedDelay 5min）
INSERT INTO `sys_job`
  (`job_name`, `bean_name`, `method_name`, `cron_expression`, `params`, `status`, `remark`,
   `create_by`, `create_time`, `update_by`, `update_time`)
SELECT '资源审核对账', 'resourceReviewReconcileTask', 'reconcile', '0 */5 * * * ?', NULL, 1,
       '审核开关关闭后扫描遗留待审资源批量放行（原 @Scheduled fixedDelay 5min）',
       'admin', NOW(), 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_job` WHERE `bean_name` = 'resourceReviewReconcileTask' AND `method_name` = 'reconcile');

-- 5. 项目审核对账（原 ProjectReviewReconcileTask.reconcile，fixedDelay 5min）
INSERT INTO `sys_job`
  (`job_name`, `bean_name`, `method_name`, `cron_expression`, `params`, `status`, `remark`,
   `create_by`, `create_time`, `update_by`, `update_time`)
SELECT '项目审核对账', 'projectReviewReconcileTask', 'reconcile', '0 */5 * * * ?', NULL, 1,
       '审核开关关闭后扫描遗留待审项目批量放行（原 @Scheduled fixedDelay 5min）',
       'admin', NOW(), 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_job` WHERE `bean_name` = 'projectReviewReconcileTask' AND `method_name` = 'reconcile');

-- 6. 借出逾期对账（原 AuditLoanOverdueTask.scanOverdue，fixedDelay 30min）
INSERT INTO `sys_job`
  (`job_name`, `bean_name`, `method_name`, `cron_expression`, `params`, `status`, `remark`,
   `create_by`, `create_time`, `update_by`, `update_time`)
SELECT '借出逾期对账', 'auditLoanOverdueTask', 'scanOverdue', '0 */30 * * * ?', NULL, 1,
       '扫描 status=BORROWED 且 expected_return_date < now 的借出置 OVERDUE（原 @Scheduled fixedDelay 30min）',
       'admin', NOW(), 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_job` WHERE `bean_name` = 'auditLoanOverdueTask' AND `method_name` = 'scanOverdue');

-- 7. 周记报表生成（原 AuditWeeklyReportTask.generateWeeklyReport，cron 每周一 00:10）
INSERT INTO `sys_job`
  (`job_name`, `bean_name`, `method_name`, `cron_expression`, `params`, `status`, `remark`,
   `create_by`, `create_time`, `update_by`, `update_time`)
SELECT '周记报表生成', 'auditWeeklyReportTask', 'generateWeeklyReport', '0 10 0 ? * MON', NULL, 1,
       '每周一 00:10 生成上周周报（knowhub.audit.weekly_report_enabled 开启时才生成，原 @Scheduled cron 0 10 0 ? * MON）',
       'admin', NOW(), 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_job` WHERE `bean_name` = 'auditWeeklyReportTask' AND `method_name` = 'generateWeeklyReport');

-- 8. 月度报表生成（原 AuditMonthlyReportTask.generateMonthlyReport，cron 每月1日 00:10）
INSERT INTO `sys_job`
  (`job_name`, `bean_name`, `method_name`, `cron_expression`, `params`, `status`, `remark`,
   `create_by`, `create_time`, `update_by`, `update_time`)
SELECT '月度报表生成', 'auditMonthlyReportTask', 'generateMonthlyReport', '0 10 0 1 * *', NULL, 1,
       '每月1日 00:10 生成上月月报（knowhub.audit.monthly_report_enabled 开启时才生成，原 @Scheduled cron 0 10 0 1 * *）',
       'admin', NOW(), 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_job` WHERE `bean_name` = 'auditMonthlyReportTask' AND `method_name` = 'generateMonthlyReport');

-- 验证：查刚插入的 8 条（人工核对）
SELECT job_id, job_name, bean_name, method_name, cron_expression, status, remark
FROM `sys_job`
WHERE `bean_name` IN ('fileGcTask','blogReviewReconcileTask','articleReviewReconcileTask',
                      'resourceReviewReconcileTask','projectReviewReconcileTask',
                      'auditLoanOverdueTask','auditWeeklyReportTask','auditMonthlyReportTask')
ORDER BY job_id;
