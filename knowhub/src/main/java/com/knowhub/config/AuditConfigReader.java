package com.knowhub.config;

import com.rookie.common.util.SysConfigUtil;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 审计模块全局配置读取收口。
 * 与 {@link BlogConfigReader} / {@link StorageConfigReader} 同构：业务侧（AuditFundFlowServiceImpl/
 * AuditLoanServiceImpl/定时任务）唯一"知道阈值/开关从哪来"的地方，不直接使用 SysConfigUtil。
 * 当前实现走系统设置 sys_config['knowhub.audit.*']（见 sql/knowhub-audit.sql），
 * SysConfigUtil 只读 Redis 永久缓存，编辑设置项时由 SysConfigServiceImpl 重写缓存运行时生效。
 */
@Component
public class AuditConfigReader {

    /** 系统设置键：花销审批开关（BOOLEAN，true 开阈值审批 / false 免审直接 APPROVED） */
    public static final String CONFIG_KEY_EXPENSE_APPROVAL_ENABLED = "knowhub.audit.expense_approval_enabled";

    /** 系统设置键：花销审批阈值（STRING 存 "500.00"，元为单位带 2 位小数，低于阈值自动通过） */
    public static final String CONFIG_KEY_EXPENSE_THRESHOLD = "knowhub.audit.expense_approval_threshold";

    /** 系统设置键：物品借出审批开关（BOOLEAN，true 借出需审 REQUEST→BORROWED / false 直接 BORROWED） */
    public static final String CONFIG_KEY_LOAN_APPROVAL_ENABLED = "knowhub.audit.loan_approval_enabled";

    /** 系统设置键：月度报表自动生成开关（BOOLEAN，true 每月1日生成上月月报并通知负责人） */
    public static final String CONFIG_KEY_MONTHLY_REPORT_ENABLED = "knowhub.audit.monthly_report_enabled";

    /** 系统设置键：周记报表自动生成开关（BOOLEAN，true 每周一生成上周周报并通知负责人） */
    public static final String CONFIG_KEY_WEEKLY_REPORT_ENABLED = "knowhub.audit.weekly_report_enabled";

    /** 默认花销审批阈值（设置项缺失/不可解析时回退，与 sys_config 默认值同） */
    private static final BigDecimal DEFAULT_EXPENSE_THRESHOLD = new BigDecimal("500.00");

    /**
     * 花销审批开关是否开启。
     * 取系统设置 configValue="true" 即视为开（computer threshold 审批逻辑生效）；
     * 设置项缺失、停用、类型不符或读取异常时默认关闭（免审直接 APPROVED，避免配置缺失阻塞业务）。
     *
     * @return true 走阈值审批；false 免审（所有花销直接 APPROVED，但仍记 SUBMIT+APPROVE 双痕留痕）
     */
    public boolean isExpenseApprovalEnabled() {
        try {
            return Boolean.TRUE.equals(SysConfigUtil.getBoolean(CONFIG_KEY_EXPENSE_APPROVAL_ENABLED, false));
        } catch (Exception e) {
            // 缓存未加载等异常，降级为关闭，保证花销写入流程不中断
            return false;
        }
    }

    /**
     * 取花销审批阈值（元，BigDecimal）。阈值是金额，可能带 2 位小数，不能用 getNumber(Long.parseLong 会丢精度)，
     * 故 sys_config 把阈值存为 STRING "500.00"，此处用 getString 取原始串再 new BigDecimal 解析。
     * 设置项缺失、停用、空值或解析失败时回退 {@link #DEFAULT_EXPENSE_THRESHOLD}。
     *
     * @return 花销阈值元，低于等于阈值自动 APPROVED，高于走 PENDING 待审
     */
    public BigDecimal getExpenseThreshold() {
        try {
            String raw = SysConfigUtil.getString(CONFIG_KEY_EXPENSE_THRESHOLD, null);
            if (raw == null || raw.trim().isEmpty()) {
                return DEFAULT_EXPENSE_THRESHOLD;
            }
            return new BigDecimal(raw.trim());
        } catch (Exception e) {
            // 缓存未加载/数值不合法等异常，降级为默认阈值
            return DEFAULT_EXPENSE_THRESHOLD;
        }
    }

    /**
     * 判定一笔花销是否走自动通过。封装"开关 + 阈值"两步判断，供 AuditFundFlowServiceImpl 复用：
     *   - 未开审批 → 一律自动通过
     *   - 已开审批 → amount ≤ 阈值 才自动通过，高于则需人工审
     *
     * @param amount 花销金额
     * @return true 自动 APPROVED（记 SUBMIT+APPROVE 双痕）；false 走 PENDING 待审
     */
    public boolean shouldAutoApprove(BigDecimal amount) {
        if (!isExpenseApprovalEnabled()) {
            return true;
        }
        return getExpenseThreshold().compareTo(amount) >= 0;
    }

    /**
     * 物品借出审批开关是否开启。开则借出申请走 REQUEST→（approve）→BORROWED；关则直接 BORROWED 免审。
     * 设置项缺失/停用/异常时默认关闭（直接 BORROWED，不阻塞借出流程）。
     */
    public boolean isLoanApprovalEnabled() {
        try {
            return Boolean.TRUE.equals(SysConfigUtil.getBoolean(CONFIG_KEY_LOAN_APPROVAL_ENABLED, false));
        } catch (Exception e) {
            return false;
        }
    }

    /** 月度报表自动生成开关（每月1日定时任务据此决定是否生成上月月报）。关则不自动生成（人工仍可手动重算生成）。 */
    public boolean isMonthlyReportEnabled() {
        try {
            return Boolean.TRUE.equals(SysConfigUtil.getBoolean(CONFIG_KEY_MONTHLY_REPORT_ENABLED, false));
        } catch (Exception e) {
            return false;
        }
    }

    /** 周记报表自动生成开关（每周一定时任务据此决定是否生成上周周报）。关则不自动生成（人工仍可手动重算生成）。 */
    public boolean isWeeklyReportEnabled() {
        try {
            return Boolean.TRUE.equals(SysConfigUtil.getBoolean(CONFIG_KEY_WEEKLY_REPORT_ENABLED, false));
        } catch (Exception e) {
            return false;
        }
    }
}