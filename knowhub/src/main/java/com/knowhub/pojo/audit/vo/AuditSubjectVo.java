package com.knowhub.pojo.audit.vo;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 花销主体对外 VO，供 Controller 入参/出参。
 * 时间字段一律用 java.util.Date（不用 String），序列化由全局 jackson.date-format 统一格式化
 * （见 application.yml），前端用 formatDateTime 展示。
 * 金额用 BigDecimal（java.math.BigDecimal），不用 Double，避免浮点误差。
 * balance/monthExpense/handlerNickname/projectName 为非表展示字段，由 Service 聚合或 Mapper join 带出。
 */
public class AuditSubjectVo {

    private Long subjectId;

    private String name;

    /** 主体范围：LAB 实验室 / PROJECT 项目赛事（见 SubjectScope 枚举，字典 audit_subject_scope） */
    private String scope;

    /** 关联赛事项目ID（scope=PROJECT 时填，可空） */
    private Long projectId;

    /** 预算累计（计划额度，BUDGET 写入时累加） */
    private BigDecimal budgetTotal;

    /** 实到累计（INCOME 写入时累加） */
    private BigDecimal incomeTotal;

    /** 负责人 userId */
    private Long handlerId;

    /** 主体状态：ACTIVE 活跃 / CLOSED 关闭 */
    private String status;

    private String note;

    /** 当前结余 = income_total − 历史已通过 EXPENSE 合计（非表字段，Service 聚合回填） */
    private BigDecimal balance;

    /** 当月已花合计（非表字段，Service 按年月聚合回填，看板用） */
    private BigDecimal monthExpense;

    /** 负责人昵称（非表字段，join sys_user 带出） */
    private String handlerNickname;

    /** 关联项目名（非表字段，scope=PROJECT 时回填） */
    private String projectName;

    private String createBy;

    private Date createTime;

    private String updateBy;

    private Date updateTime;

    public AuditSubjectVo() {
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(Long subjectId) {
        this.subjectId = subjectId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public BigDecimal getBudgetTotal() {
        return budgetTotal;
    }

    public void setBudgetTotal(BigDecimal budgetTotal) {
        this.budgetTotal = budgetTotal;
    }

    public BigDecimal getIncomeTotal() {
        return incomeTotal;
    }

    public void setIncomeTotal(BigDecimal incomeTotal) {
        this.incomeTotal = incomeTotal;
    }

    public Long getHandlerId() {
        return handlerId;
    }

    public void setHandlerId(Long handlerId) {
        this.handlerId = handlerId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public BigDecimal getMonthExpense() {
        return monthExpense;
    }

    public void setMonthExpense(BigDecimal monthExpense) {
        this.monthExpense = monthExpense;
    }

    public String getHandlerNickname() {
        return handlerNickname;
    }

    public void setHandlerNickname(String handlerNickname) {
        this.handlerNickname = handlerNickname;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
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
        return "AuditSubjectVo{" +
                "subjectId=" + subjectId +
                ", name='" + name + '\'' +
                ", scope='" + scope + '\'' +
                ", budgetTotal=" + budgetTotal +
                ", incomeTotal=" + incomeTotal +
                ", handlerId=" + handlerId +
                ", status='" + status + '\'' +
                ", balance=" + balance +
                ", monthExpense=" + monthExpense +
                '}';
    }
}