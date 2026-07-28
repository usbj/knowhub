package com.knowhub.pojo.blog.quarry;

import java.util.List;

/**
 * 前台博客搜索查询条件（GET /portal/blog/search 入参）。
 * 支持全文关键字 + 标签/作者复合过滤 + 排序。
 * 权限透传字段 userViewLevel 由 service 层注入（分级开关关时恒 1，开时取 BlogPermissionResolver.view）。
 */
public class BlogPortalSearchQuarry {

    /** 全文搜索关键字（复用 ft_blog_title_content ngram 全文索引） */
    private String keyword;

    /** 标签 id 列表（多选，走 blog_tag join + IN 精确过滤） */
    private List<Long> tagIds;

    /** 作者过滤（create_by username 或 authorId，博客主表无 author_id 故按 create_by） */
    private Long authorId;

    /** 排序：RELEVANCE 相关度 / HOT 热度 / LATEST 最新；缺省 RELEVANCE */
    private String sort;

    // ---- 权限透传字段（service 层回填，非前端入参） ----
    /** 当前用户查看等级（分级开关关时恒 1，开时 BlogPermissionResolver.view；未登录=1） */
    private Integer userViewLevel;

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

    public Long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
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
}
