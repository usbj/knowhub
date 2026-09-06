package com.knowhub.pojo.audit.quarry;

import java.util.Date;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 资金流水分页查询条件，作为 Mapper parameterType 与列表接口入参。
 * 由 query string 绑定（无 @RequestBody）；pageNum/pageSize 由 PageUtil 从请求读取。
 * 支持按主体 subjectId 精确（周期流水明细弹窗用）/ 主体名(subjectName 模糊，join audit_subject)、
 * 流水类型(flowType)、花销分类(category)、流水状态(status)、审核状态(reviewStatus)、
 * 经办人 handlerId 精确 / 经办人姓名(handlerName 模糊，join sys_user)、发生日期区间(occurDate) 过滤。
 * 筛选区用 subjectName/handlerName 模糊；周期流水弹窗用 subjectId 精确。
 * 不继承分页基类（PageUtil.startPage 从请求读 pageNum/pageSize，Quarry 只承条件）。
 */
public class AuditFundFlowQuarry {

    /** 关联主体 id 精确过滤（周期流水明细弹窗按主体拉本期 APPROVED 流水用） */
    private Long subjectId;

    /** 关联主体名模糊过滤（管理台筛选区按主体名搜） */
    private String subjectName;

    /** 流水类型：BUDGET 预算 / INCOME 收账 / EXPENSE 花销（见 FlowType 枚举） */
    private String flowType;

    /** 花销分类(EXPENSE 的耗材/差旅等；BUDGET/INCOME 复用此列记来源/用途) */
    private String category;

    /** 流水状态：DRAFT/PENDING/APPROVED/REJECTED/REVOKED（见 FlowStatus 枚举） */
    private String status;

    /** 审核状态过滤（管理台看待审用 PENDING） */
    private String reviewStatus;

    /** 经办人 userId 精确过滤（保留接口，目前前端筛选不暴露） */
    private Long handlerId;

    /** 经办人昵称模糊过滤（管理台筛选区按昵称搜） */
    private String handlerName;

    /** 发生日期区间起（含，按 audit_fund_flow.occur_date）；前端 daterange 传 yyyy-MM-dd，
     *  ISO.DATE 显式声明避免依赖 Spring 默认转换器 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private Date beginTime;

    /** 发生日期区间止（含）；前端 daterange 传 yyyy-MM-dd */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private Date endTime;

    public AuditFundFlowQuarry() {
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(Long subjectId) {
        this.subjectId = subjectId;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
    }

    public String getFlowType() {
        return flowType;
    }

    public void setFlowType(String flowType) {
        this.flowType = flowType;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
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

    public String getHandlerName() {
        return handlerName;
    }

    public void setHandlerName(String handlerName) {
        this.handlerName = handlerName;
    }

    public Long getHandlerId() {
        return handlerId;
    }

    public void setHandlerId(Long handlerId) {
        this.handlerId = handlerId;
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

    @Override
    public String toString() {
        return "AuditFundFlowQuarry{" +
                "subjectId=" + subjectId +
                ", subjectName='" + subjectName + '\'' +
                ", flowType='" + flowType + '\'' +
                ", category='" + category + '\'' +
                ", status='" + status + '\'' +
                ", reviewStatus='" + reviewStatus + '\'' +
                ", handlerId=" + handlerId +
                ", handlerName='" + handlerName + '\'' +
                ", beginTime=" + beginTime +
                ", endTime=" + endTime +
                '}';
    }
}