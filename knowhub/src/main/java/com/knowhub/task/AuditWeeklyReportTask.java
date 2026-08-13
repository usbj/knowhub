package com.knowhub.task;

import com.knowhub.config.AuditConfigReader;
import com.knowhub.service.audit.AuditPeriodReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 周记报表生成定时任务。
 * <p>
 * 每周一 00:10 生成上周周报（period_type=WEEK，period_key=上周 yyyy-'W'ww ISO 周历键），
 * 遍历所有 ACTIVE 主体聚合 flow+loan 写 audit_period_report（UNIQUE 覆盖同键），生成后通知负责人。
 * <p>
 * 仅在 sys_config['knowhub.audit.weekly_report_enabled']=true 时生成（AuditConfigReader.isWeeklyReportEnabled），
 * 关则跳过（人工仍可手动重算已结束期）。开关走 sys_config 可后台改即时生效；触发时刻走注解 cron 硬编码
 * （@Scheduled 注解在 Bean 创建时解析，读不了 sys_config Redis 缓存），改触发时刻需重启。
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
     * 每周一 00:10 触发。cron: "0 10 0 ? * MON" = 每周一0点10分（日位 ? 不限、周位 MON）。
     * 注意 Spring @Scheduled cron 表达式为 6 段（含秒），周位用 MON 英文，与 Unix cron 区分。
     * cron 触发不支持 initialDelay（Spring 6.2 起校验拒绝），cron 本身启动时不会立即触发，
     * 故无需 initialDelay 延迟启动。
     */
    @Scheduled(cron = "0 10 0 ? * MON")
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