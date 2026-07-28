package com.knowhub.service.article.impl;

import com.github.pagehelper.PageInfo;
import com.knowhub.pojo.article.quarry.ArticleQuarry;
import com.knowhub.pojo.article.vo.ArticleReviewLogVo;
import com.knowhub.pojo.article.vo.ArticleReviewVo;
import com.knowhub.pojo.article.vo.ArticleVo;

import java.util.List;

/**
 * 文章管理 Service。
 * 权限模型（轻量，仅系统级 + 作者归属，无成员表）：
 * - 系统权限（全局、分等级、所有文章）：view/edit:lN，ArticlePermissionResolver 一次扫描
 *   List<Permission> 取最高等级（admin 零特判，登录时全 perm_key 已塞入）
 * - 作者归属：文章 author_id 是单一所有者，作者对自己的文章全权（不看等级/不看 visibility），
 *   类比项目 LEADER 的"所有者"，但无成员表承载，由 canOp 在系统权限外补 author_id==userId 分支
 * 审核流程复用博客/项目范式（状态机+回避+流水表+对账任务），ReviewAction 枚举复用。
 * 主表不冗余审核快照（reviewer/review_time/review_advice 全在 article_review_log）。
 */
public interface ArticleService {

    /** 列表查询（权限过滤：level<=userViewLevel OR author_id=userId 作者能看自己的文章） */
    PageInfo<ArticleVo> quarryArticle(ArticleQuarry quarry);

    /** 详情（二次权限校验 canOp(view)，回填权限态/作者标识） */
    ArticleVo getArticleInfo(Long articleId);

    /** 新增文章（作者=当前用户，新建即 DRAFT） */
    Boolean addArticleInfo(ArticleVo vo);

    /** 编辑文章（状态机前置 + canOp(edit) 校验，PUBLISHED 禁编须先撤回） */
    Boolean editArticleInfo(ArticleVo vo);

    /** 删除文章（作者或 delete 权限，级联软删 chapter + 封面 file_object） */
    Boolean deleteArticleInfo(Long[] articleIds);

    /** 发布文章（审核开关决定 PENDING_REVIEW 或 PUBLISHED，写流水 SUBMIT/PUBLISH） */
    Boolean publishArticle(Long articleId);

    /** 撤回文章（仅 PUBLISHED 可撤回，写流水 REVOKE） */
    Boolean revokeArticle(Long articleId);

    /** 审核文章（回避：作者不能审自己，写流水 APPROVE/REJECT） */
    Boolean reviewArticle(ArticleReviewVo vo);

    /** 审核历史流水（按时间升序） */
    List<ArticleReviewLogVo> listReviewLog(Long articleId);

    /** 对账：审核开关关闭后批量放行遗留待审文章 */
    int reconcilePendingReview();
}
