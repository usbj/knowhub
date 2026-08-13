package com.knowhub.pojo.audit.quarry;

import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

/**
 * 周期报表白分页查询条件，作为 Mapper parameterType 与列表接口入参。
 * 由 query string 绑定（无 @RequestBody）；pageNum/pageSize 由 PageUtil 从请求读取。
 * 支持按主体名(subjectName 模糊，join audit_subject)、周期类型(periodType)、周期键(periodKey)、生成时间区间(generateTime) 过滤。
 * 不继承分页基类（PageUtil.startPage 从请求读 pageNum/pageSize，Quarry 只承条件）。
 */
public class AuditPeriodReportQuarry {

    /** 关联主体名模糊过滤（join audit_subject.name） */
    private String subjectName;

    /** 周期类型：MONTH 月度 / WEEK 周记（见 PeriodType 枚举） */
    private String periodType;

    /** 周期键：MONTH "2026-07" / WEEK "2026-W32" */
    private String periodKey;

    /** 生成时间区间起（含，按 generate_time） */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private Date beginTime;

    /** 生成时间区间止（含，按 generate_time） */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private Date endTime;

    public AuditPeriodReportQuarry() {
    }

    public String getSubjectName() {
        return subjectName;
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
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
        return "AuditPeriodReportQuarry{" +
                "subjectName='" + subjectName + '\'' +
                ", periodType='" + periodType + '\'' +
                ", periodKey='" + periodKey + '\'' +
                ", beginTime=" + beginTime +
                ", endTime=" + endTime +
                '}';
    }
}