package com.knowhub.pojo.audit.entity;

import com.rookie.common.pojo.BaseEntity;

import java.math.BigDecimal;

/**
 * 花销主体实体，对应 audit_subject 表（资金池，LAB 实验室级 / PROJECT 项目赛事级）。
 *
 * 资金池模型（设计核心）：
 *   budget_total 累加 BUDGET（计划额度未必到账）；income_total 累加 INCOME（实到钱）；
 *   不存 expense_total（已花费按当月聚合算，不存总计）；balance 不存列，查时算
 *   = income_total − 历史已通过 EXPENSE 合计。
 *   subject 是有进有出的资金池主体，不是被动扣减的预算账户。
 *
 * 字段说明：
 *   scope 主体范围（LAB/PROJECT，见 SubjectScope 枚举）；scope=PROJECT 时 project_id 关联赛事。
 *   handler_id 负责人 userId（报表通知接收人 + 模块内归属）。
 *   status 主体状态（ACTIVE/CLOSED，见主表 status 列，并非 FlowStatus，仅主体用 ACTIVE/CLOSED 两值）。
 *
 * 审计列(create_by/update_by/create_time/update_time) 由 BaseEntity 承载；
 * create_by/update_by 存 username 快照，handler_id 存 userId 稳定锁定（username 可改，userId 不变）。
 * 软删 deleted 独立列（沿用 blog/resource 约定）。
 *
 * 非表展示字段（不入库，由 Mapper 列表/详情查询 join 带出或 Service 层回填）：
 *   balance     当前结余 = income_total − 历史已通过 EXPENSE 合计（Service 聚合回填）
 *   monthExpense 当月已花合计（Service 按年月聚合回填，看板用）
 *   handlerNickname 负责人昵称（join sys_user on user_id=handler_id 带出）
 *   projectName    关联项目名（join sys_menu? 项目赛事主体 scope=PROJECT 时带出；当前项目模块尚未对接，先留字段）
 */
public class AuditSubject extends BaseEntity {

    private Long subjectId;

    private String name;

    /** 主体范围：LAB 实验室 / PROJECT 项目赛事（见 SubjectScope 枚举，字典 audit_subject_scope） */
    private String scope;

    /** 关联赛事项目ID（scope=PROJECT 时填，可空，有些预算不干项目） */
    private Long projectId;

    /** 预算累计（计划额度，BUDGET 写入时累加，未必到账） */
    private BigDecimal budgetTotal;

    /** 实到累计（INCOME 写入时累加，真金白银） */
    private BigDecimal incomeTotal;

    /** 负责人 userId（报表通知接收人 + 模块内归属） */
    private Long handlerId;

    /** 主体状态：ACTIVE 活跃 / CLOSED 关闭 */
    private String status;

    private String note;

    private Integer deleted;

    // ---- 非表字段（列表/详情查询 join 带出或 Service 聚合回填，不入库） ----
    /** 当前结余 = income_total − 历史已通过 EXPENSE 合计（非表字段，Service 聚合回填） */
    private BigDecimal balance;

    /** 当月已花合计（非表字段，Service 按年月聚合回填，看板用） */
    private BigDecimal monthExpense;

    /** 负责人昵称（join sys_user on user_id=handler_id 带出，非表字段） */
    private String handlerNickname;

    /** 关联项目名（scope=PROJECT 时 join 带出；非表字段，项目模块对接后回填） */
    private String projectName;

    public AuditSubject() {
    }

    public AuditSubject(java.util.Date createTime, java.util.Date updateTime, String createBy, String updateBy,
                        Long subjectId, String name, String scope, Long projectId,
                        BigDecimal budgetTotal, BigDecimal incomeTotal, Long handlerId,
                        String status, String note, Integer deleted) {
        super(createTime, updateTime, createBy, updateBy);
        this.subjectId = subjectId;
        this.name = name;
        this.scope = scope;
        this.projectId = projectId;
        this.budgetTotal = budgetTotal;
        this.incomeTotal = incomeTotal;
        this.handlerId = handlerId;
        this.status = status;
        this.note = note;
        this.deleted = deleted;
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

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
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

    @Override
    public String toString() {
        return "AuditSubject{" +
                "subjectId=" + subjectId +
                ", name='" + name + '\'' +
                ", scope='" + scope + '\'' +
                ", projectId=" + projectId +
                ", budgetTotal=" + budgetTotal +
                ", incomeTotal=" + incomeTotal +
                ", handlerId=" + handlerId +
                ", status='" + status + '\'' +
                ", deleted=" + deleted +
                ", createTime=" + getCreateTime() +
                ", createBy='" + getCreateBy() + '\'' +
                '}';
    }
}