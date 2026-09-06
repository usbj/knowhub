package com.knowhub.service.blog.impl;

import com.github.pagehelper.PageInfo;
import com.knowhub.pojo.blog.quarry.BlogQuarry;
import com.knowhub.pojo.blog.vo.BlogVo;
import com.knowhub.pojo.common.vo.ReviewLogVo;
import com.knowhub.pojo.common.vo.ReviewVo;

import java.util.List;

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

    /** 编辑（校验归属 + 标签受控 + 先删后插；PUBLISHED 禁止编辑需先撤回） */
    Boolean editBlogInfo(BlogVo vo);

    /** 批量软删 */
    Boolean deleteBlogInfo(Long[] blogIds);

    /** 发布（经审核开关决定 PUBLISHED 或 PENDING_REVIEW；前置状态校验+写流水） */
    Boolean publishBlog(Long blogId);

    /** 撤回（→ REVOKED；前置状态校验+写流水） */
    Boolean revokeBlog(Long blogId);

    /** 审核（通过 → PUBLISHED；驳回 → REJECTED；前置状态校验+审核员回避+写流水） */
    Boolean reviewBlog(ReviewVo vo);

    /** 审核历史（按文章ID查审核流水时间线，前台详情/后台记录共用） */
    List<ReviewLogVo> listReviewLog(Long blogId);

    /**
     * 对账收口：审核开关关闭后，将所有遗留的待审核文章批量转为已发布。
     * 由定时任务在确认开关关闭且 Redis 待审标记存在时调用。逐条放行 + 写 PUBLISH/SYSTEM 流水，
     * 单条失败跳过不阻塞其它稿（状态优先、历史容错）。
     *
     * @return 实际放行条数
     */
    int reconcilePendingReview();

    /** 点赞 / 取消点赞 */
    Boolean toggleLike(Long blogId, Boolean liked);

    /** 收藏 / 取消收藏 */
    Boolean toggleCollect(Long blogId, Boolean collected);
}