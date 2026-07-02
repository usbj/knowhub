package com.knowhub.service;

import com.github.pagehelper.PageInfo;
import com.knowhub.pojo.quarry.BlogQuarry;
import com.knowhub.pojo.vo.BlogVo;
import com.knowhub.pojo.vo.ReviewVo;

/**
 * 博客文章 Service。
 * 接口只暴露 DTO，不暴露实体。
 */
public interface BlogService {

    /** 列表查询（分页） */
    PageInfo<BlogVo> quarryBlog(BlogQuarry quarry);

    /** 详情（含标签回填、当前用户点赞/收藏状态） */
    BlogVo getBlogInfo(Long blogId);

    /** 新增（草稿） */
    Boolean addBlogInfo(BlogVo vo);

    /** 编辑（校验归属 + 标签受控 + 先删后插） */
    Boolean editBlogInfo(BlogVo vo);

    /** 批量软删 */
    Boolean deleteBlogInfo(Long[] blogIds);

    /** 发布（经审核开关决定 PUBLISHED 或 PENDING_REVIEW） */
    Boolean publishBlog(Long blogId);

    /** 撤回（→ REVOKED） */
    Boolean revokeBlog(Long blogId);

    /** 审核（通过 → PUBLISHED；驳回 → REJECTED） */
    Boolean reviewBlog(ReviewVo vo);

    /** 点赞 / 取消点赞 */
    Boolean toggleLike(Long blogId, Boolean liked);

    /** 收藏 / 取消收藏 */
    Boolean toggleCollect(Long blogId, Boolean collected);
}