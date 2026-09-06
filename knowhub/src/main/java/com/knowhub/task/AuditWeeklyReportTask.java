package com.knowhub.task;

import com.knowhub.config.AuditConfigReader;
import com.knowhub.service.audit.AuditPeriodReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 周记报表生成定时任务。
 * <p>
 * 每周一 00:10 生成上周周报（period_type=WEEK，period_key=上周 yyyy-'W'ww ISO 周历键），
 * 遍历所有 ACTIVE 主体聚合 flow+loan 写 audit_period_report（UNIQUE 覆盖同键），生成后通知负责人。
 * <p>
 * 仅在 sys_config['knowhub.audit.weekly_report_enabled']=true 时生成（AuditConfigReader.isWeeklyReportEnabled），
 * 关则跳过（人工仍可手动重算已结束期）。开关走 sys_config 可后台改即时生效；触发时刻由 rookie sys_job
 * 调度器按 cron 驱动（初始 cron 0 10 0 ? * MON，每周一 00:10，与原 @Scheduled cron 同款 6 段式），
 * 后台「系统监控→定时任务」可改 cron / 启停 / 立即执行，改 cron 即时生效无需重启。
 * <p>
 * 定时任务无登录态，create_by/update_by 填 "system"；通知走 SysNoticeMapper 直插（无登录态不走 addSysNoticeInfo）。
 * 单主体失败隔离不影响其他主体，整体异常 catch 仅 logger。
 */
@Component
public class AuditWeeklyReportTask {

    private static final Logger log = LoggerFactory.getLogger(AuditWeeklyReportTask.class);

    @Autowired
    private AuditPeriodReportService auditPeriodReportService;

    @Autowired
    private AuditConfigReader auditConfigReader;

    /**
     * 生成周报。由 sys_job 调度器按 cron 0 10 0 ? * MON 触发（每周一 00:10，无参方法，符合 rookie findTaskMethod 要求）。
     * 注意 Spring cron 表达式为 6 段（含秒），周位用 MON 英文，与 Unix cron 区分。
     */
    public void generateWeeklyReport() {
        try {
            if (!auditConfigReader.isWeeklyReportEnabled()) {
                log.debug("[AuditWeeklyReportTask] 周记报表自动生成关闭，跳过");
                return;
            }
            int count = auditPeriodReportService.generateForPreviousPeriod("WEEK");
            if (count > 0) {
                log.info("[AuditWeeklyReportTask] 周记报表生成完成，共 {} 份", count);
            }
        } catch (Exception e) {
            log.error("[AuditWeeklyReportTask] 周记报表生成执行异常", e);
        }
    }
}