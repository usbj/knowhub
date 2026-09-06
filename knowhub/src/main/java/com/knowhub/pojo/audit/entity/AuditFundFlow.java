package com.knowhub.pojo.audit.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.rookie.common.pojo.BaseEntity;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 资金流水实体，对应 audit_fund_flow 表（预算/收账/花销三流合一）。
 *
 * 三流合一记同一张流表，flow_type 区分资金进/出方向与审批策略：
 *   BUDGET  预算注入   累加 subject.budget_total，免审(status 恒 APPROVED)
 *   INCOME  收账到账   累加 subject.income_total，免审(status 恒 APPROVED)
 *   EXPENSE 花销支出   走阈值审批(低于阈值自动 APPROVED，高于走 PENDING→审核)，
 *                     APPROVED 时聚合计入余额扣减(income_total − 历史APPROVED EXPENSE 合计)
 *
 * 字段说明：
 *   amount 金额（BigDecimal，DECIMAL14,2，不用 Double 避免浮点误差）。
 *   occurDate 发生日期（预算注入日/收入到账日/花销发生日，仅 date 不带时分秒）。
 *   category 分组列：EXPENSE 是耗材/差旅等花销分类，BUDGET/INCOME 复用记来源/用途分组。
 *   handlerId 经办人 userId。
 *   voucherObjectId 票据附件 file_object.object_id（business_type=AUDIT_VOUCHER）。
 *   status 流水状态：DRAFT/PENDING/APPROVED/REJECTED/REVOKED（见 FlowStatus 枚举，仅 EXPENSE 用全状态）。
 *   reviewStatus 审核状态：NONE/PENDING/APPROVED/REJECTED（见 ReviewStatus 枚举）。
 *
 * 审计列(create_by/update_by/create_time/update_time) 由 BaseEntity 承载；软删 deleted 独立列。
 *
 * 非表展示字段（不入库，由 Mapper 列表/详情查询 join 带出）：
 *   subjectName     关联主体名（join audit_subject 带出）
 *   handlerNickname 经办人昵称（join sys_user on user_id=handler_id 带出）
 *   categoryLabel   花销分类 label（字典翻译，Service/前端翻译，非表字段）
 */
public class AuditFundFlow extends BaseEntity {

    private Long flowId;

    private Long subjectId;

    /** 流水类型：BUDGET 预算 / INCOME 收账 / EXPENSE 花销（见 FlowType 枚举，字典 audit_flow_type） */
    private String flowType;

    /** 金额（元，DECIMAL14,2） */
    private BigDecimal amount;

    /** 发生日期（预算注入日/收入到账日/花销发生日，仅 date） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date occurDate;

    /** 分组：EXPENSE 花销分类(耗材/差旅/奖金...),BUDGET/INCOME 来源用途(赞助/赛事拨款/捐赠),复用此列减少冗余 */
    private String category;

    /** 经办人 userId */
    private Long handlerId;

    /** 票据附件 file_object.object_id(business_type=AUDIT_VOUCHER) */
    private Long voucherObjectId;

    private String note;

    /** 流水状态：DRAFT/PENDING/APPROVED/REJECTED/REVOKED（见 FlowStatus 枚举；BUDGET/INCOME 恒 APPROVED） */
    private String status;

    /** 审核状态：NONE/PENDING/APPROVED/REJECTED（见 ReviewStatus 枚举，复用） */
    private String reviewStatus;

    private Integer deleted;

    // ---- 非表字段（列表/详情查询 join 带出或字典翻译，不入库） ----
    /** 关联主体名（join audit_subject on subject_id 带出，非表字段） */
    private String subjectName;

    /** 经办人昵称（join sys_user on user_id=handler_id 带出，非表字段） */
    private String handlerNickname;

    /** 花销分类 label（字典翻译，非表字段） */
    private String categoryLabel;

    public AuditFundFlow() {
    }

    public AuditFundFlow(Date createTime, Date updateTime, String createBy, String updateBy,
                         Long flowId, Long subjectId, String flowType, BigDecimal amount, Date occurDate,
                         String category, Long handlerId, Long voucherObjectId, String note,
                         String status, String reviewStatus, Integer deleted) {
        super(createTime, updateTime, createBy, updateBy);
        this.flowId = flowId;
        this.subjectId = subjectId;
        this.flowType = flowType;
        this.amount = amount;
        this.occurDate = occurDate;
        this.category = category;
        this.handlerId = handlerId;
        this.voucherObjectId = voucherObjectId;
        this.note = note;
        this.status = status;
        this.reviewStatus = reviewStatus;
        this.deleted = deleted;
    }

    public Long getFlowId() {
        return flowId;
    }

    public void setFlowId(Long flowId) {
        this.flowId = flowId;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(Long subjectId) {
        this.subjectId = subjectId;
    }

    public String getFlowType() {
        return flowType;
    }

    public void setFlowType(String flowType) {
        this.flowType = flowType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Date getOccurDate() {
        return occurDate;
    }

    public void setOccurDate(Date occurDate) {
        this.occurDate = occurDate;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Long getHandlerId() {
        return handlerId;
    }

    public void setHandlerId(Long handlerId) {
        this.handlerId = handlerId;
    }

    public Long getVoucherObjectId() {
        return voucherObjectId;
    }

    public void setVoucherObjectId(Long voucherObjectId) {
        this.voucherObjectId = voucherObjectId;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(String reviewStatus) {
        this.reviewStatus = reviewStatus;
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

    public String getCategoryLabel() {
        return categoryLabel;
    }

    public void setCategoryLabel(String categoryLabel) {
        this.categoryLabel = categoryLabel;
    }

    @Override
    public String toString() {
        return "AuditFundFlow{" +
                "flowId=" + flowId +
                ", subjectId=" + subjectId +
                ", flowType='" + flowType + '\'' +
                ", amount=" + amount +
                ", occurDate=" + occurDate +
                ", category='" + category + '\'' +
                ", handlerId=" + handlerId +
                ", status='" + status + '\'' +
                ", reviewStatus='" + reviewStatus + '\'' +
                ", deleted=" + deleted +
                ", createTime=" + getCreateTime() +
                ", createBy='" + getCreateBy() + '\'' +
                '}';
    }
}