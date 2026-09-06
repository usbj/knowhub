package com.knowhub.pojo.article.vo;

/**
 * 章节大纲项（文章详情的章节列表，仅含章节名+排序，不含正文）。
 * 正文走独立的 /portal/article/{id}/chapter/{chapterId} 接口按需拉取（避免详情一次拉全部 mediumtext）。
 */
public class ChapterOutlineVo {

    private Long chapterId;

    private String chapterName;

    private Integer sortOrder;

    public Long getChapterId() {
        return chapterId;
    }

    public void setChapterId(Long chapterId) {
        this.chapterId = chapterId;
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