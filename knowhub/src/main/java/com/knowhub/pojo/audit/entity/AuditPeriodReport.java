package com.knowhub.pojo.audit.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.rookie.common.pojo.BaseEntity;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 月度/周记报表实体，对应 audit_period_report 表（合一，定时任务生成）。
 *
 * 周期模型：
 *   periodType   MONTH 月度 / WEEK 周记（见 PeriodType 枚举，字典 audit_period_type）
 *   periodKey    MONTH "yyyy-MM"（如 "2026-07"） / WEEK "yyyy-'W'ww"（如 "2026-W32"）
 *   periodStart/periodEnd  周期起止日期（DATE，period_end < now 才允许重算）
 *   UNIQUE(subject_id, period_type, period_key) 重算走唯一键覆盖同一期
 *
 * 聚合字段：
 *   budgetAmount   本期 BUDGET 合计（计划额度注入）
 *   incomeAmount   本期 INCOME 合计（实到）
 *   expenseAmount  本期 APPROVED EXPENSE 合计（已花，仅 status=APPROVED 计入）
 *   expenseByCategory 本期花销按分类汇总 JSON（如 {"耗材":100.00,"差旅":200.00}），存 TEXT
 *   balanceEnd     期末结余（income_total − 历史已通过 EXPENSE 合计，截至 periodEnd）
 *   loanOutCount   本期借出笔数（borrow_date 落在期内）
 *   loanUnreturned 本期末未归还笔数（status IN BORROWED/OVERDUE 且 borrow_date <= periodEnd）
 *   generateTime   生成/重算时间
 *
 * 审计列(create_by/update_by/create_time/update_time) 由 BaseEntity 承载；
 * 定时任务生成时 create_by/update_by 填 "system"（无登录态）；软删 deleted 独立列。
 *
 * 非表展示字段（不入库，Mapper join 带出）：
 *   subjectName    关联主体名（join audit_subject 带出）
 *   handlerNickname 负责人昵称（join sys_user on user_id=handler_id via subject 带出）
 */
public class AuditPeriodReport extends BaseEntity {

    private Long reportId;

    private Long subjectId;

    /** 周期类型：MONTH 月度 / WEEK 周记（见 PeriodType 枚举） */
    private String periodType;

    private String periodKey;

    /** 周期起（仅 date，后端解析 periodKey 得 MONDAY/月初） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date periodStart;

    /** 周期止（仅 date，period_end < now 才允许重算） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date periodEnd;

    private BigDecimal budgetAmount;

    private BigDecimal incomeAmount;

    private BigDecimal expenseAmount;

    /** 本期花销按分类汇总 JSON（如 {"耗材":100.00,"差旅":200.00}），存 TEXT */
    private String expenseByCategory;

    private BigDecimal balanceEnd;

    private Integer loanOutCount;

    private Integer loanUnreturned;

    private Date generateTime;

    private Integer deleted;

    // ---- 非表字段（列表/详情查询 join 带出，不入库） ----
    private String subjectName;

    private String handlerNickname;

    public AuditPeriodReport() {
    }

    public AuditPeriodReport(Date createTime, Date updateTime, String createBy, String updateBy,
                              Long reportId, Long subjectId, String periodType, String periodKey,
                              Date periodStart, Date periodEnd, BigDecimal budgetAmount, BigDecimal incomeAmount,
                              BigDecimal expenseAmount, String expenseByCategory, BigDecimal balanceEnd,
                              Integer loanOutCount, Integer loanUnreturned, Date generateTime, Integer deleted) {
        super(createTime, updateTime, createBy, updateBy);
        this.reportId = reportId;
        this.subjectId = subjectId;
        this.periodType = periodType;
        this.periodKey = periodKey;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.budgetAmount = budgetAmount;
        this.incomeAmount = incomeAmount;
        this.expenseAmount = expenseAmount;
        this.expenseByCategory = expenseByCategory;
        this.balanceEnd = balanceEnd;
        this.loanOutCount = loanOutCount;
        this.loanUnreturned = loanUnreturned;
        this.generateTime = generateTime;
        this.deleted = deleted;
    }

    public Long getReportId() {
        return reportId;
    }

    public void setReportId(Long reportId) {
        this.reportId = reportId;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(Long subjectId) {
        this.subjectId = subjectId;
    }

    public String getPeriodType() {
        return periodType;
    }

    public void setPeriodType(String periodType) {
        this.periodType = periodType;
    }

    public String getPeriodKey() {
        return periodKey;
    }

    public void setPeriodKey(String periodKey) {
        this.periodKey = periodKey;
    }

    public Date getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(Date periodStart) {
        this.periodStart = periodStart;
    }

    public Date getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(Date periodEnd) {
        this.periodEnd = periodEnd;
    }

    public BigDecimal getBudgetAmount() {
        return budgetAmount;
    }

    public void setBudgetAmount(BigDecimal budgetAmount) {
        this.budgetAmount = budgetAmount;
    }

    public BigDecimal getIncomeAmount() {
        return incomeAmount;
    }

    public void setIncomeAmount(BigDecimal incomeAmount) {
        this.incomeAmount = incomeAmount;
    }

    public BigDecimal getExpenseAmount() {
        return expenseAmount;
    }

    public void setExpenseAmount(BigDecimal expenseAmount) {
        this.expenseAmount = expenseAmount;
    }

    public String getExpenseByCategory() {
        return expenseByCategory;
    }

    public void setExpenseByCategory(String expenseByCategory) {
        this.expenseByCategory = expenseByCategory;
    }

    public BigDecimal getBalanceEnd() {
        return balanceEnd;
    }

    public void setBalanceEnd(BigDecimal balanceEnd) {
        this.balanceEnd = balanceEnd;
    }

    public Integer getLoanOutCount() {
        return loanOutCount;
    }

    public void setLoanOutCount(Integer loanOutCount) {
        this.loanOutCount = loanOutCount;
    }

    public Integer getLoanUnreturned() {
        return loanUnreturned;
    }

    public void setLoanUnreturned(Integer loanUnreturned) {
        this.loanUnreturned = loanUnreturned;
    }

    public Date getGenerateTime() {
        return generateTime;
    }

    public void setGenerateTime(Date generateTime) {
        this.generateTime = generateTime;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
    }

    public String getHandlerNickname() {
        return handlerNickname;
    }

    public void setHandlerNickname(String handlerNickname) {
        this.handlerNickname = handlerNickname;
    }

    @Override
    public String toString() {
        return "AuditPeriodReport{" +
                "reportId=" + reportId +
                ", subjectId=" + subjectId +
                ", periodType='" + periodType + '\'' +
                ", periodKey='" + periodKey + '\'' +
                ", periodStart=" + periodStart +
                ", periodEnd=" + periodEnd +
                ", budgetAmount=" + budgetAmount +
                ", incomeAmount=" + incomeAmount +
                ", expenseAmount=" + expenseAmount +
                ", balanceEnd=" + balanceEnd +
                ", loanOutCount=" + loanOutCount +
                ", loanUnreturned=" + loanUnreturned +
                ", generateTime=" + generateTime +
                ", deleted=" + deleted +
                '}';
    }
}