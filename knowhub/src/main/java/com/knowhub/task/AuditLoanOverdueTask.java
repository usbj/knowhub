package com.knowhub.task;

import com.knowhub.mapper.audit.AuditLoanMapper;
import com.knowhub.pojo.audit.entity.AuditLoan;
import com.knowhub.service.audit.AuditLoanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 借出逾期对账定时任务。
 * <p>
 * 场景：物品借出时填了预计归还时间(expected_return_date)，但借用人未按时归还，
 * audit_loan 仍停在 BORROWED 态，台账/看板无法识别逾期。本任务定期扫表把
 * status=BORROWED 且 expected_return_date &lt; now 的借出批量置 OVERDUE，供管理台高亮与催还。
 * <p>
 * 不依赖 sys_config 开关（逾期是客观状态判定，非业务开关；loan_approval_enabled 只管新增态审批流，
 * 与是否逾期无关）。触发由 rookie sys_job 调度器（CronTrigger + 独立线程池）驱动，cron 见 sys_job 表
 * 对应行（初始 cron 0 * / 30 * * * ?，对应原 30 分钟间隔）。后台「系统监控→定时任务」可改 cron / 启停 /
 * 立即执行，改 cron 即时生效无需重启。原 @Scheduled fixedDelay + knowhub.audit.overdue-scan-interval-minutes
 * yml 项已不再驱动调度（yml 项保留未删，仅作历史）。
 * <p>
 * 对账走"扫表→过滤→批量置 OVERDUE"两段，Service.markOverdue 内部二次校验状态（仅 BORROWED 才置），
 * 防止扫描与置态之间借出被归还/已置 OVERDUE 造成的重复处理。无登录态，update_by 记 "system"。
 * 不写审核流水（逾期是系统判定非人工动作，loan_review_log 只记 SUBMIT/APPROVE/REJECT）。
 */
@Component
public class AuditLoanOverdueTask {

    private static final Logger log = LoggerFactory.getLogger(AuditLoanOverdueTask.class);

    @Autowired
    private AuditLoanMapper auditLoanMapper;

    @Autowired
    private AuditLoanService auditLoanService;

    /**
     * 逾期对账扫描。由 sys_job 调度器按 cron 触发（无参方法，符合 rookie findTaskMethod 要求）。
     */
    public void scanOverdue() {
        try {
            Date now = new Date();
            // 扫表：取所有 status=BORROWED 且 expected_return_date < now 且未删的借出
            List<AuditLoan> overdue = auditLoanMapper.listOverdueLoans(now);
            if (overdue == null || overdue.isEmpty()) {
                return;
            }
            List<Long> loanIds = overdue.stream()
                    .map(AuditLoan::getLoanId)
                    .collect(Collectors.toList());
            int marked = auditLoanService.markOverdue(loanIds);
            if (marked > 0) {
                log.info("[AuditLoanOverdueTask] 扫描到 {} 条逾期借出，置 OVERDUE {} 条", overdue.size(), marked);
            }
        } catch (Exception e) {
            log.error("[AuditLoanOverdueTask] 逾期对账执行异常", e);
        }
    }
}