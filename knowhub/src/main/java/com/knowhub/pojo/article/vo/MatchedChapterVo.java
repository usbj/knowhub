package com.knowhub.pojo.article.vo;

/**
 * 搜索命中的章节标识（仅章节级，正文命中时由搜索结果带出，前端据此标"命中章节：x章"）。
 * 不带正文片段（后置），仅给章节名 + chapterId（可点击跳章节阅读页）。
 */
public class MatchedChapterVo {

    private Long chapterId;

    /** 所属文章ID（service 分组用，对外保留便于前端跳转拼路由） */
    private Long articleId;

    private String chapterName;

    private Integer sortOrder;

    public Long getChapterId() {
        return chapterId;
    }

    public void setChapterId(Long chapterId) {
        this.chapterId = chapterId;
    }

    public Long getArticleId() {
        return articleId;
    }

    public void setArticleId(Long articleId) {
        this.articleId = articleId;
    }

    public String getChapterName() {
        return chapterName;
    }

    public void setChapterName(String chapterName) {
        this.chapterName = chapterName;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}