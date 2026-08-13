package com.knowhub.task;

import com.knowhub.config.AuditConfigReader;
import com.knowhub.service.audit.AuditPeriodReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 月度报表生成定时任务。
 * <p>
 * 每月1日 00:10 生成上月月报（period_type=MONTH，period_key=上月 yyyy-MM），
 * 遍历所有 ACTIVE 主体聚合 flow+loan 写 audit_period_report（UNIQUE 覆盖同键），生成后通知负责人。
 * <p>
 * 仅在 sys_config['knowhub.audit.monthly_report_enabled']=true 时生成（AuditConfigReader.isMonthlyReportEnabled），
 * 关则跳过（人工仍可手动重算已结束期）。开关走 sys_config 可后台改即时生效；触发时刻走 yml/cron 注解硬编码
 * （@Scheduled 注解在 Bean 创建时解析，读不了 sys_config Redis 缓存），改触发时刻需重启。
 * <p>
 * 定时任务无登录态，报表的 create_by/update_by 填 "system"；通知走 SysNoticeMapper 直插（无登录态不走
 * addSysNoticeInfo）。单主体失败隔离不影响其他主体，整体异常 catch 仅 logger。
 */
@Component
public class AuditMonthlyReportTask {

    private static final Logger log = LoggerFactory.getLogger(AuditMonthlyReportTask.class);

    @Autowired
    private AuditPeriodReportService auditPeriodReportService;

    @Autowired
    private AuditConfigReader auditConfigReader;

    /**
     * 每月1日 00:10 触发。cron: 秒 分 时 日 月 周；"0 10 0 1 * *" = 每月1日0点10分。
     * 注意 Spring @Scheduled cron 表达式为 6 段（含秒），与 Unix cron 5 段不同。
     * cron 触发不支持 initialDelay（Spring 6.2 起校验拒绝），cron 本身启动时不会立即触发，
     * 故无需 initialDelay 延迟启动。
     */
    @Scheduled(cron = "0 10 0 1 * *")
    public void generateMonthlyReport() {
        try {
            if (!auditConfigReader.isMonthlyReportEnabled()) {
                log.debug("[AuditMonthlyReportTask] 月度报表自动生成关闭，跳过");
                return;
            }
            int count = auditPeriodReportService.generateForPreviousPeriod("MONTH");
            if (count > 0) {
                log.info("[AuditMonthlyReportTask] 月度报表生成完成，共 {} 份", count);
            }
        } catch (Exception e) {
            log.error("[AuditMonthlyReportTask] 月度报表生成执行异常", e);
        }
    }
}