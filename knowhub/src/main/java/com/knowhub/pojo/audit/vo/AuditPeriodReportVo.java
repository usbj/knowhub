package com.knowhub.pojo.audit.vo;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 周期报表对外 VO，供 Controller 入参/出参。
 * 时间字段一律用 java.util.Date（不用 String），序列化由全局 jackson.date-format 统一格式化。
 * subjectName/handlerNickname 为非表展示字段，由 Mapper join 带出。
 * expenseByCategory 是 JSON 字符串（前端解析为对象展示花销分类汇总）。
 *
 * 重算接口 regenerate 入参仅用 subjectId/periodType/periodKey 三键定位
 * （Service 内部据此算 periodStart/periodEnd 再聚合覆盖，前端无需传周期起止）。
 */
public class AuditPeriodReportVo {

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

    /** 本期花销按分类汇总 JSON 字符串（前端解析展示） */
    private String expenseByCategory;

    private BigDecimal balanceEnd;

    private Integer loanOutCount;

    private Integer loanUnreturned;

    private Date generateTime;

    /** 关联主体名（非表字段，join audit_subject 带出） */
    private String subjectName;

    /** 负责人昵称（非表字段，join sys_user via subject 带出） */
    private String handlerNickname;

    /** 周期是否已结束（非表字段，Service 回填：period_end < now 时 true，前端据此禁用重算按钮） */
    private Boolean periodEnded;

    private String createBy;

    private Date createTime;

    private String updateBy;

    private Date updateTime;

    public AuditPeriodReportVo() {
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

    public Boolean getPeriodEnded() {
        return periodEnded;
    }

    public void setPeriodEnded(Boolean periodEnded) {
        this.periodEnded = periodEnded;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    @Override
    public String toString() {
        return "AuditPeriodReportVo{" +
                "reportId=" + reportId +
                ", subjectId=" + subjectId +
                ", periodType='" + periodType + '\'' +
                ", periodKey='" + periodKey + '\'' +
                ", periodStart=" + periodStart +
                ", periodEnd=" + periodEnd +
                ", balanceEnd=" + balanceEnd +
                ", subjectName='" + subjectName + '\'' +
                ", periodEnded=" + periodEnded +
                '}';
    }
}