package com.knowhub.pojo.audit.vo;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 资金流水对外 VO，供 Controller 入参/出参。
 * 时间字段一律用 java.util.Date（不用 String），序列化由全局 jackson.date-format 统一格式化
 * （见 application.yml），前端用 formatDateTime 展示。
 * 金额 amount 用 BigDecimal，不用 Double。
 * subjectName/handlerNickname/categoryLabel 为非表展示字段，由 Mapper join 带出或字典翻译。
 */
public class AuditFundFlowVo {

    private Long flowId;

    private Long subjectId;

    /** 流水类型：BUDGET 预算 / INCOME 收账 / EXPENSE 花销（见 FlowType 枚举，字典 audit_flow_type） */
    private String flowType;

    /** 金额（元，DECIMAL14,2） */
    private BigDecimal amount;

    /** 发生日期（预算注入日/收入到账日/花销发生日，仅 date） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date occurDate;

    /** 分组：EXPENSE 花销分类 / BUDGET/INCOME 来源用途 */
    private String category;

    /** 经办人 userId */
    private Long handlerId;

    /** 票据附件 file_object.object_id(business_type=AUDIT_VOUCHER) */
    private Long voucherObjectId;

    private String note;

    /** 流水状态：DRAFT/PENDING/APPROVED/REJECTED/REVOKED（见 FlowStatus 枚举） */
    private String status;

    /** 审核状态：NONE/PENDING/APPROVED/REJECTED（见 ReviewStatus 枚举，复用） */
    private String reviewStatus;

    /** 关联主体名（非表字段，join audit_subject 带出） */
    private String subjectName;

    /** 经办人昵称（非表字段，join sys_user 带出） */
    private String handlerNickname;

    /** 花销分类 label（非表字段，字典翻译） */
    private String categoryLabel;

    private String createBy;

    private Date createTime;

    private String updateBy;

    private Date updateTime;

    public AuditFundFlowVo() {
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
        return "AuditFundFlowVo{" +
                "flowId=" + flowId +
                ", subjectId=" + subjectId +
                ", flowType='" + flowType + '\'' +
                ", amount=" + amount +
                ", occurDate=" + occurDate +
                ", category='" + category + '\'' +
                ", status='" + status + '\'' +
                ", reviewStatus='" + reviewStatus + '\'' +
                ", subjectName='" + subjectName + '\'' +
                '}';
    }
}