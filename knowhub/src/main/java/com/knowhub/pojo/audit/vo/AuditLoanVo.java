package com.knowhub.pojo.audit.vo;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.Date;

/**
 * 物品借出对外 VO，供 Controller 入参/出参。
 * 时间字段一律用 java.util.Date（不用 String），序列化由全局 jackson.date-format 统一格式化
 * （见 application.yml），前端用 formatDateTime 展示。
 * subjectName 为非表展示字段，由 Mapper join 带出。
 *
 * 归还时若产生损耗，调 /audit/loan/return 接口传 {@link #wearLossAmount}（>0 表示有损耗扣费），
 * Service 内事务先插一条 EXPENSE(category=WEAR 损耗) 流水再更新本借出 related_flow_id 指回，
 * 故 VO 单承一个 wearLossAmount 入参字段（不入库，仅用于归还时透传损耗金额给 Service）。
 */
public class AuditLoanVo {

    private Long loanId;

    private Long subjectId;

    private String itemName;

    /** 物品类型：ASSET 资产 / CONSUMABLE 耗材（见 LoanItemType 枚举，字典 audit_loan_item_type） */
    private String itemType;

    private String assetNo;

    private Integer quantity;

    /** 借用人姓名（外部人员，非系统用户） */
    private String borrowerName;

    /** 借用人联系电话 */
    private String borrowerPhone;

    /** 借用人所属单位/部门（实验室/班级/外单位，选填） */
    private String borrowerOrg;

    /** 借用人补充备注（选填） */
    private String borrowerRemark;

    /** 借出时间（仅 date，逾期判定起点） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date borrowDate;

    /** 预计归还时间（仅 date，逾期判定依据，空=无限期） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date expectedReturnDate;

    /** 实际归还时间（仅 date，归还时回填） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date actualReturnDate;

    /** 借出状态：REQUEST/BORROWED/RETURNED/OVERDUE/REJECTED（见 LoanStatus 枚举） */
    private String status;

    /** 归还损耗扣费指向 audit_fund_flow.flow_id（无损耗为空） */
    private Long relatedFlowId;

    /** 附件 file_object.object_id（借出凭证/损耗票据，business_type=AUDIT_VOUCHER） */
    private Long voucherObjectId;

    private String note;

    /**
     * 归还损耗金额（元，仅 return 接口入参用，不入库）。
     * >0 时 Service 事务内插一条 EXPENSE(category=损耗) 流水并回指 related_flow_id；
     * null/0 表示无损耗，仅置 RETURNED 回填 actual_return_date。
     * 用 java.math.BigDecimal 金额类型，避免浮点误差。
     */
    private java.math.BigDecimal wearLossAmount;

    /** 关联主体名（非表字段，join audit_subject 带出） */
    private String subjectName;

    private String createBy;

    private Date createTime;

    private String updateBy;

    private Date updateTime;

    public AuditLoanVo() {
    }

    public Long getLoanId() {
        return loanId;
    }

    public void setLoanId(Long loanId) {
        this.loanId = loanId;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(Long subjectId) {
        this.subjectId = subjectId;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public String getItemType() {
        return itemType;
    }

    public void setItemType(String itemType) {
        this.itemType = itemType;
    }

    public String getAssetNo() {
        return assetNo;
    }

    public void setAssetNo(String assetNo) {
        this.assetNo = assetNo;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getBorrowerName() {
        return borrowerName;
    }

    public void setBorrowerName(String borrowerName) {
        this.borrowerName = borrowerName;
    }

    public String getBorrowerPhone() {
        return borrowerPhone;
    }

    public void setBorrowerPhone(String borrowerPhone) {
        this.borrowerPhone = borrowerPhone;
    }

    public String getBorrowerOrg() {
        return borrowerOrg;
    }

    public void setBorrowerOrg(String borrowerOrg) {
        this.borrowerOrg = borrowerOrg;
    }

    public String getBorrowerRemark() {
        return borrowerRemark;
    }

    public void setBorrowerRemark(String borrowerRemark) {
        this.borrowerRemark = borrowerRemark;
    }

    public Date getBorrowDate() {
        return borrowDate;
    }

    public void setBorrowDate(Date borrowDate) {
        this.borrowDate = borrowDate;
    }

    public Date getExpectedReturnDate() {
        return expectedReturnDate;
    }

    public void setExpectedReturnDate(Date expectedReturnDate) {
        this.expectedReturnDate = expectedReturnDate;
    }

    public Date getActualReturnDate() {
        return actualReturnDate;
    }

    public void setActualReturnDate(Date actualReturnDate) {
        this.actualReturnDate = actualReturnDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getRelatedFlowId() {
        return relatedFlowId;
    }

    public void setRelatedFlowId(Long relatedFlowId) {
        this.relatedFlowId = relatedFlowId;
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

    public java.math.BigDecimal getWearLossAmount() {
        return wearLossAmount;
    }

    public void setWearLossAmount(java.math.BigDecimal wearLossAmount) {
        this.wearLossAmount = wearLossAmount;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
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
        return "AuditLoanVo{" +
                "loanId=" + loanId +
                ", subjectId=" + subjectId +
                ", itemName='" + itemName + '\'' +
                ", itemType='" + itemType + '\'' +
                ", status='" + status + '\'' +
                ", relatedFlowId=" + relatedFlowId +
                ", subjectName='" + subjectName + '\'' +
                '}';
    }
}