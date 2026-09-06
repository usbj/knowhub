package com.knowhub.pojo.article.quarry;

import java.util.Date;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 文章列表查询条件，作为 Mapper parameterType 与列表接口入参。
 * 由 query string 绑定（无 @RequestBody）；pageNum/pageSize 由 PageUtil 从请求读取。
 * 支持按标题模糊、等级、可见性、状态、审核状态、创建人、时间区间过滤。
 *
 * 权限过滤透传字段（service 层从 ArticlePermissionResolver 算出后回填，Mapper SQL 用）：
 * - userViewLevel：当前用户查看等级（0=无系统查看权限）
 * - userId：当前用户 userId（用于"作者能看自己所有状态的文章"分支：author_id = userId）
 * 文章无成员表（轻量权限模型），列表 SQL 过滤：
 *   level <= userViewLevel OR author_id = userId（作者始终能看自己的文章，不看等级）
 * 无系统查看权限者 userViewLevel=0，level<=0 永假，只走 author_id=userId 分支 → 只看自己写的。
 */
public class ArticleQuarry {

    /** 标题模糊 */
    private String title;

    /** 文章等级 1/2/3（见 ArticleLevel 枚举） */
    private Integer level;

    /** 文章可见性 PRIVATE/SEMIPUBLIC/PUBLIC（见 ArticleVisibility 枚举） */
    private String visibility;

    /** 文章状态；读者侧默认 PUBLISHED，管理台可传任意 */
    private String status;

    /** 审核状态过滤（管理台看待审用 PENDING） */
    private String reviewStatus;

    /** 创建人(作者账号)过滤 */
    private String createBy;

    /** 作者userId过滤（"我的文章"场景，前端按当前用户传） */
    private Long authorId;

    /** 标签 id 列表，走 article_tag join + IN 精确过滤（照博客 BlogQuarry.tagIds 范式） */
    private java.util.List<Long> tagIds;

    /** 创建时间区间起（含）；前端 daterange 传 yyyy-MM-dd，ISO.DATE 显式声明避免依赖 Spring 默认转换器 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private Date beginTime;

    /** 创建时间区间止（含）；前端 daterange 传 yyyy-MM-dd */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private Date endTime;

    // ---- 权限过滤透传字段（service 层回填，非前端入参） ----
    /** 当前用户查看等级（0=无系统查看权限） */
    private Integer userViewLevel;

    /** 当前用户 userId（作者能看自己所有状态的文章分支用） */
    private Long userId;

    /**
     * 标签命中门槛值 = tagIds.size()，service 层回填。HAVING count(distinct art.tag_id) = #{tagCount}。
     * 不能在 SQL 里写 #{tagIds.size()}：MyBatis createCacheKey 反射取 tagIds.size() 会走
     * CollectionWrapper.get("size") 抛 UnsupportedOperationException，故拆成独立 Integer 参数。
     */
    private Integer tagCount;

    public ArticleQuarry() {
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
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

    public Long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
    }

    public java.util.List<Long> getTagIds() {
        return tagIds;
    }

    public void setTagIds(java.util.List<Long> tagIds) {
        this.tagIds = tagIds;
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

    public Integer getTagCount() {
        return tagCount;
    }

    public void setTagCount(Integer tagCount) {
        this.tagCount = tagCount;
    }

    @Override
    public String toString() {
        return "ArticleQuarry{" +
                "title='" + title + '\'' +
                ", level=" + level +
                ", visibility='" + visibility + '\'' +
                ", status='" + status + '\'' +
                ", reviewStatus='" + reviewStatus + '\'' +
                ", createBy='" + createBy + '\'' +
                ", authorId=" + authorId +
                ", beginTime=" + beginTime +
                ", endTime=" + endTime +
                ", userViewLevel=" + userViewLevel +
                ", userId=" + userId +
                '}';
    }
}
