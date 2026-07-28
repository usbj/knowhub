package com.knowhub.pojo.project.quarry;

import java.util.Date;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 项目列表查询条件，作为 Mapper parameterType 与列表接口入参。
 * 由 query string 绑定（无 @RequestBody）；pageNum/pageSize 由 PageUtil 从请求读取。
 * 支持按标题模糊、类型、等级、状态、审核状态、创建人、时间区间过滤。
 *
 * 权限过滤透传字段（service 层从 ProjectPermissionResolver 算出后回填，Mapper SQL 用）：
 * - userViewLevel：当前用户查看等级（0=无系统查看权限，仅能看参与的项目）
 * - userId：当前用户 userId（用于 member 子查询：project_id IN (select ... where user_id=? and can_view=1))
 * 列表 SQL：level <= userViewLevel OR project_id IN (member 子查询)，无系统权限者 userViewLevel=0
 * 只走 member 分支 → 只看参与的项目。
 */
public class ProjectQuarry {

    /** 标题模糊 */
    private String title;

    /** 项目类型：COMPETITION 等（见 ProjectType 枚举） */
    private String type;

    /** 项目等级 1/2/3（见 ProjectLevel 枚举） */
    private Integer level;

    /** 项目状态；读者侧默认 PUBLISHED，管理台可传任意 */
    private String status;

    /** 审核状态过滤（管理台看待审用 PENDING） */
    private String reviewStatus;

    /** 创建人(负责人账号)过滤 */
    private String createBy;

    /** 创建时间区间起（含）；前端 daterange 传 yyyy-MM-dd，ISO.DATE 显式声明避免依赖 Spring 默认转换器 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private Date beginTime;

    /** 创建时间区间止（含）；前端 daterange 传 yyyy-MM-dd */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private Date endTime;

    // ---- 权限过滤透传字段（service 层回填，非前端入参） ----
    /** 当前用户查看等级（0=无系统查看权限） */
    private Integer userViewLevel;

    /** 当前用户 userId（member 子查询用） */
    private Long userId;

    public ProjectQuarry() {
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
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

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
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

    public Integer getUserViewLevel() {
        return userViewLevel;
    }

    public void setUserViewLevel(Integer userViewLevel) {
        this.userViewLevel = userViewLevel;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    @Override
    public String toString() {
        return "ProjectQuarry{" +
                "title='" + title + '\'' +
                ", type='" + type + '\'' +
                ", level=" + level +
                ", status='" + status + '\'' +
                ", reviewStatus='" + reviewStatus + '\'' +
                ", createBy='" + createBy + '\'' +
                ", beginTime=" + beginTime +
                ", endTime=" + endTime +
                ", userViewLevel=" + userViewLevel +
                ", userId=" + userId +
                '}';
    }
}
