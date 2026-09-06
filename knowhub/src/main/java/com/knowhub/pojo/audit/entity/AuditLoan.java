package com.knowhub.pojo.audit.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.rookie.common.pojo.BaseEntity;

import java.util.Date;

/**
 * 物品借出实体，对应 audit_loan 表（资产/耗材借出与归还，含损耗扣费回指）。
 *
 * 字段说明：
 *   subjectId         关联资金池主体（损耗扣费落该主体，scope=LAB/PROJECT 均可）
 *   itemName          物品名称
 *   itemType          ASSET 资产(有 asset_no)/CONSUMABLE 耗材（见 LoanItemType 枚举，字典 audit_loan_item_type）
 *   assetNo           资产编号（ASSET 填、CONSUMABLE 可空）
 *   quantity          数量（耗材按数量管理，资产一般 1）
 *   borrowerName      借用人姓名（外部人员，非系统用户）
 *   borrowerPhone     借用人联系电话
 *   borrowerOrg       借用人所属单位/部门（实验室/班级/外单位，选填）
 *   borrowerRemark    借用人补充备注（选填）
 *   borrowDate        借出时间（datetime）
 *   expectedReturnDate 预计归还时间（逾期判定依据，空=无限期）
 *   actualReturnDate  实际归还时间（归还时回填）
 *   status            REQUEST/BORROWED/RETURNED/OVERDUE/REJECTED（见 LoanStatus 枚举）
 *   relatedFlowId     归还损耗扣费指向 audit_fund_flow.flow_id（无损耗为空）
 *   voucherObjectId   附件 file_object.object_id（借出凭证/损耗票据，business_type=AUDIT_VOUCHER）
 *   note              备注
 *
 * 审计列(create_by/update_by/create_time/update_time) 由 BaseEntity 承载；软删 deleted 独立列。
 *
 * 非表展示字段（不入库，由 Mapper 列表/详情查询 join 带出）：
 *   subjectName       关联主体名（join audit_subject 带出）
 */
public class AuditLoan extends BaseEntity {

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

    private Integer deleted;

    // ---- 非表字段（列表/详情查询 join 带出，不入库） ----
    /** 关联主体名（join audit_subject on subject_id 带出，非表字段） */
    private String subjectName;

    public AuditLoan() {
    }

    public AuditLoan(Date createTime, Date updateTime, String createBy, String updateBy,
                     Long loanId, Long subjectId, String itemName, String itemType, String assetNo,
                     Integer quantity, String borrowerName, String borrowerPhone, String borrowerOrg,
                     String borrowerRemark, Date borrowDate, Date expectedReturnDate,
                     Date actualReturnDate, String status, Long relatedFlowId, Long voucherObjectId,
                     String note, Integer deleted) {
        super(createTime, updateTime, createBy, updateBy);
        this.loanId = loanId;
        this.subjectId = subjectId;
        this.itemName = itemName;
        this.itemType = itemType;
        this.assetNo = assetNo;
        this.quantity = quantity;
        this.borrowerName = borrowerName;
        this.borrowerPhone = borrowerPhone;
        this.borrowerOrg = borrowerOrg;
        this.borrowerRemark = borrowerRemark;
        this.borrowDate = borrowDate;
        this.expectedReturnDate = expectedReturnDate;
        this.actualReturnDate = actualReturnDate;
        this.status = status;
        this.relatedFlowId = relatedFlowId;
        this.voucherObjectId = voucherObjectId;
        this.note = note;
        this.deleted = deleted;
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

    @Override
    public String toString() {
        return "AuditLoan{" +
                "loanId=" + loanId +
                ", subjectId=" + subjectId +
                ", itemName='" + itemName + '\'' +
                ", itemType='" + itemType + '\'' +
                ", borrowerName='" + borrowerName + '\'' +
                ", status='" + status + '\'' +
                ", relatedFlowId=" + relatedFlowId +
                ", deleted=" + deleted +
                ", createTime=" + getCreateTime() +
                ", createBy='" + getCreateBy() + '\'' +
                '}';
    }
}