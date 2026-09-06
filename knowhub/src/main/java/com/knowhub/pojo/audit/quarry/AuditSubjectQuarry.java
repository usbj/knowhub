package com.knowhub.pojo.audit.quarry;

import java.util.Date;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 花销主体列表查询条件，作为 Mapper parameterType 与列表接口入参。
 * 由 query string 绑定（无 @RequestBody）；pageNum/pageSize 由 PageUtil 从请求读取。
 * 支持按主体名模糊、范围(scope)、状态、负责人姓名(handlerName 模糊，join sys_user nick_name)、创建时间区间过滤。
 * 不继承分页基类（PageUtil.startPage 从请求读 pageNum/pageSize，Quarry 只承条件）。
 */
public class AuditSubjectQuarry {

    /** 主体名模糊（name like %?%） */
    private String name;

    /** 主体范围：LAB 实验室 / PROJECT 项目赛事（见 SubjectScope 枚举） */
    private String scope;

    /** 主体状态：ACTIVE 活跃 / CLOSED 关闭 */
    private String status;

    /** 负责人昵称模糊过滤（join sys_user nick_name like） */
    private String handlerName;

    /** 创建时间区间起（含，按 audit_subject.create_time）；前端 daterange 传 yyyy-MM-dd，
     *  ISO.DATE 显式声明避免依赖 Spring 默认转换器 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private Date beginTime;

    /** 创建时间区间止（含）；前端 daterange 传 yyyy-MM-dd */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private Date endTime;

    public AuditSubjectQuarry() {
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getHandlerName() {
        return handlerName;
    }

    public void setHandlerName(String handlerName) {
        this.handlerName = handlerName;
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
        return "AuditSubjectQuarry{" +
                "name='" + name + '\'' +
                ", scope='" + scope + '\'' +
                ", status='" + status + '\'' +
                ", handlerName='" + handlerName + '\'' +
                ", beginTime=" + beginTime +
                ", endTime=" + endTime +
                '}';
    }
}