package com.knowhub.pojo.vo;

/**
 * 标签热度榜 VO（GET /portal/tag/hot 出参）。
 * 统计 blog_tag + article_tag，按"关联的 PUBLISHED+公开内容数 + 总热度"排序。
 */
public class HotTagVo {

    private Long tagId;

    private String tagName;

    /** 关联的已发布+公开内容数（blog + article 合计） */
    private Long contentCount;

    /** 热度分（关联内容的热度聚合：like/collect/view 加权） */
    private Long hotScore;

    public Long getTagId() {
        return tagId;
    }

    public void setTagId(Long tagId) {
        this.tagId = tagId;
    }

    public String getTagName() {
        return tagName;
    }

    public void setTagName(String tagName) {
        this.tagName = tagName;
    }

    public Long getContentCount() {
        return contentCount;
    }

    public void setContentCount(Long contentCount) {
        this.contentCount = contentCount;
    }

    public Long getHotScore() {
        return hotScore;
    }

    public void setHotScore(Long hotScore) {
        this.hotScore = hotScore;
    }
}
