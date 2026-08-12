package com.knowhub.pojo.blog.vo;

/**
 * 前台博客全量统计 VO（/portal/blog/stats 出参）。
 * 固定口径：publishedBlogCount=已发布且当前用户可见的博客数、totalReads=这些博客的累计阅读数、
 * tagCount=启用标签总数。与搜索/翻页/标签过滤无关——前端进入页面拉一次定盘展示，
 * 避免统计数字随筛选结果 total 变动（那是"当前结果数"不是"全库数"）。
 */
public class PortalBlogStatsVo {

    /** 已发布且当前用户可见等级内的博客数（前台铁律 deleted=0 AND status='PUBLISHED' AND level<=userViewLevel） */
    private Long publishedBlogCount;

    /** 上述博客的累计阅读数（sum(view_count)，view_count 主表冗余列，浏览事实表同步累加） */
    private Long totalReads;

    /** 启用标签总数（tag.status 启用，跨 blog+article 共用，与 /portal/tag/hot 同口径但 hot 只取 Top-N） */
    private Long tagCount;

    public Long getPublishedBlogCount() {
        return publishedBlogCount;
    }

    public void setPublishedBlogCount(Long publishedBlogCount) {
        this.publishedBlogCount = publishedBlogCount;
    }

    public Long getTotalReads() {
        return totalReads;
    }

    public void setTotalReads(Long totalReads) {
        this.totalReads = totalReads;
    }

    public Long getTagCount() {
        return tagCount;
    }

    public void setTagCount(Long tagCount) {
        this.tagCount = tagCount;
    }
}