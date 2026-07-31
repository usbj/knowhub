package com.knowhub.pojo.article.quarry;

import java.util.List;

/**
 * 前台文章搜索查询条件（GET /portal/article/search 入参）。照搬 BlogPortalSearchQuarry 范式。
 * 搜索覆盖 文章标题/简介（ft_article_title_summary ngram）+ 章节正文（ft_chapter_content ngram）。
 * 权限透传字段 userViewLevel 由 service 层注入（分级开关关时恒 1，开时取 ArticlePermissionResolver.view）。
 */
public class ArticlePortalSearchQuarry {

    /** 全文搜索关键字（命中 article 标题/简介 或 chapter 正文） */
    private String keyword;

    /** 标签 id 列表（多选，走 article_tag join + IN 精确过滤） */
    private List<Long> tagIds;

    /** 作者过滤（按 article.author_id） */
    private Long authorId;

    /** 排序：RELEVANCE 相关度 / HOT 热度 / LATEST 最新；缺省 RELEVANCE */
    private String sort;

    // ---- 权限透传字段（service 层回填，非前端入参） ----
    /** 当前用户查看等级（分级开关关时恒 1，开时 ArticlePermissionResolver.view；未登录=1） */
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