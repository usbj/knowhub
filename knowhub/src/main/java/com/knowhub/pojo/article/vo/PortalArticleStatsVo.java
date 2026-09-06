package com.knowhub.pojo.article.vo;

/**
 * 前台文库全量统计 VO（/portal/article/stats 出参）。
 * 固定口径：publishedDocCount=已发布且当前用户可见的文章数、totalChapters=这些文章下的已发布章节数、
 * tagCount=启用标签总数。与搜索/翻页/标签过滤无关——前端进入页面拉一次定盘展示，
 * 避免统计数字随筛选结果 total 变动（那是"当前结果数"不是"全库数"）。
 */
public class PortalArticleStatsVo {

    /** 已发布且当前用户可见等级内的文章数（前台铁律 deleted=0 AND status='PUBLISHED' AND level<=userViewLevel） */
    private Long publishedDocCount;

    /** 上述文章下的已发布章节数（chapter PUBLISHED AND 归属文章已发布且越权被过滤） */
    private Long totalChapters;

    /** 启用标签总数（tag.status 启用，跨 blog+article 共用，与 /portal/tag/hot 同口径但 hot 只取 Top-N） */
    private Long tagCount;

    public Long getPublishedDocCount() {
        return publishedDocCount;
    }

    public void setPublishedDocCount(Long publishedDocCount) {
        this.publishedDocCount = publishedDocCount;
    }

    public Long getTotalChapters() {
        return totalChapters;
    }

    public void setTotalChapters(Long totalChapters) {
        this.totalChapters = totalChapters;
    }

    public Long getTagCount() {
        return tagCount;
    }

    public void setTagCount(Long tagCount) {
        this.tagCount = tagCount;
    }
}