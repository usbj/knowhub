package com.knowhub.pojo.audit.quarry;

import java.util.Date;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 物品借出分页查询条件，作为 Mapper parameterType 与列表接口入参。
 * 由 query string 绑定（无 @RequestBody）；pageNum/pageSize 由 PageUtil 从请求读取。
 * 支持按主体名(subjectName 模糊，join audit_subject)、物品类型(itemType)、状态(status)、借用人姓名(borrowerName 模糊)、
 * 借出日期区间(borrowDate)、预计归还日期区间(expectedReturnDate) 过滤。
 * 不继承分页基类（PageUtil.startPage 从请求读 pageNum/pageSize，Quarry 只承条件）。
 */
public class AuditLoanQuarry {

    /** 关联主体名模糊过滤（join audit_subject.name） */
    private String subjectName;

    /** 物品类型：ASSET 资产 / CONSUMABLE 耗材（见 LoanItemType 枚举） */
    private String itemType;

    /** 借出状态：REQUEST/BORROWED/RETURNED/OVERDUE/REJECTED（见 LoanStatus 枚举） */
    private String status;

    /** 借用人姓名模糊过滤（外部人员姓名） */
    private String borrowerName;

    /** 物品名模糊过滤 */
    private String itemName;

    /** 借出日期区间起（含，按 audit_loan.borrow_date） */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private Date beginTime;

    /** 借出日期区间止（含，按 audit_loan.borrow_date） */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private Date endTime;

    /** 是否仅看待归还（true: status IN (BORROWED, OVERDUE) 且未归还；false/空: 不过滤） */
    private Boolean pendingReturn;

    public AuditLoanQuarry() {
    }

    public String getSubjectName() {
        return subjectName;
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
    }

    public String getItemType() {
        return itemType;
    }

    public void setItemType(String itemType) {
        this.itemType = itemType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getBorrowerName() {
        return borrowerName;
    }

    public void setBorrowerName(String borrowerName) {
        this.borrowerName = borrowerName;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public Date getBeginTime() {
        return beginTime;
    }

    public void setBeginTime(Date beginTime) {
        this.beginTime = beginTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    public Boolean getPendingReturn() {
        return pendingReturn;
    }

    public void setPendingReturn(Boolean pendingReturn) {
        this.pendingReturn = pendingReturn;
    }

    @Override
    public String toString() {
        return "AuditLoanQuarry{" +
                "subjectName='" + subjectName + '\'' +
                ", itemType='" + itemType + '\'' +
                ", status='" + status + '\'' +
                ", borrowerName='" + borrowerName + '\'' +
                ", itemName='" + itemName + '\'' +
                ", beginTime=" + beginTime +
                ", endTime=" + endTime +
                ", pendingReturn=" + pendingReturn +
                '}';
    }
}