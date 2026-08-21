package com.knowhub.pojo.project.quarry;

/**
 * 前台项目搜索查询条件（GET /portal/project/search 入参）。
 * <p>
 * 照博客 {@code BlogPortalSearchQuarry}：支持标题关键字 + 类型 + 等级过滤 + 排序；
 * 项目无标签体系，故无 tagIds 字段（按需求"不需要分类热度"——项目无标签，搜索不做标签复合过滤）。
 * 权限透传字段 {@code userViewLevel} 由 service 层注入（分级开关关时恒 1，开时取 ProjectPermissionResolver.view）。
 *
 * @author knowhub
 */
public class ProjectPortalSearchQuarry {

    /** 标题关键字（项目主表 title 无 FULLTEXT，走普通 LIKE %kw%，数据量小可接受） */
    private String keyword;

    /** 项目类型过滤：COMPETITION/PRACTICE/OPS（字典 project_type） */
    private String type;

    /** 项目等级过滤 1/2/3（可空；通常不传——前台按 userViewLevel 自动收窄，不暴露等级筛选项） */
    private Integer level;

    /** 排序：HOT 热度（默认）/ LATEST 最新；HOT 走打分公式 */
    private String sort;

    /** 作者 id 过滤（用户主页按作者筛作品，不传不过滤） */
    private Long authorId;

    // ---- 权限透传字段（service 层回填，非前端入参） ----
    /** 当前用户查看等级（分级开关关时恒 1，开时取 ProjectPermissionResolver.view；未登录=1） */
    private Integer userViewLevel;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
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

    public String getSort() {
        return sort;
    }

    public void setSort(String sort) {
        this.sort = sort;
    }

    public Integer getUserViewLevel() {
        return userViewLevel;
    }

    public void setUserViewLevel(Integer userViewLevel) {
        this.userViewLevel = userViewLevel;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
    }
}