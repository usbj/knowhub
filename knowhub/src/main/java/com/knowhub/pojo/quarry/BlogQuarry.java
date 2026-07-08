package com.knowhub.pojo.quarry;

import java.util.Date;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 博客文章列表查询条件，作为 Mapper parameterType 与列表接口入参。
 * 由 query string 绑定（无 @RequestBody）；pageNum/pageSize 由 PageUtil 从请求读取。
 */
public class BlogQuarry {

    /** 标题模糊（不走 fulltext，作为简单过滤；正文检索用 keyword） */
    private String title;

    /** 全文检索关键词，命中 title/content 的 FULLTEXT(ngram) */
    private String keyword;

    /** 标签 id 列表，走 blog_tag join + IN 精确过滤 */
    private List<Long> tagIds;

    /** 文章状态；读者侧默认 PUBLISHED，管理台可传任意 */
    private String status;

    /** 审核状态过滤（管理台看待审用 PENDING） */
    private String reviewStatus;

    /** 创建人(作者)过滤 */
    private String createBy;

    /** 创建时间区间起（含）；前端 daterange 传 yyyy-MM-dd，ISO.DATE 显式声明避免依赖 Spring 默认转换器 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private Date beginTime;

    /** 创建时间区间止（含）；前端 daterange 传 yyyy-MM-dd */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private Date endTime;

    public BlogQuarry() {
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public List<Long> getTagIds() {
        return tagIds;
    }

    public void setTagIds(List<Long> tagIds) {
        this.tagIds = tagIds;
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

    @Override
    public String toString() {
        return "BlogQuarry{" +
                "title='" + title + '\'' +
                ", keyword='" + keyword + '\'' +
                ", tagIds=" + tagIds +
                ", status='" + status + '\'' +
                ", reviewStatus='" + reviewStatus + '\'' +
                ", createBy='" + createBy + '\'' +
                ", beginTime=" + beginTime +
                ", endTime=" + endTime +
                '}';
    }
}