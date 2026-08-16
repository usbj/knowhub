package com.knowhub.service.article.impl;

import com.github.pagehelper.PageInfo;
import com.knowhub.pojo.article.vo.ArticleContributorVo;

/**
 * 文章贡献者申请/授权 Service。
 * <p>
 * 读者申请成为某文章贡献者（PENDING）→ 作者审批（accept=APPROVED / reject=REJECTED+advice）→ APPROVED 行放行
 * ChapterServiceImpl.canEditArticle 第三分支（贡献者可不要求系统编辑级权限提交章节）。
 * <p>
 * 两类列出：
 * - listReceived：当前用户作为文章作者收到的申请（service 注入 articleAuthorId=当前用户 userId）；
 * - listMine：当前用户自己发过的申请（service 注入 userId=当前用户 userId）。
 * <p>
 * 通知：apply 时通知文章作者（routePath=/collaboration?tab=received-applications 提醒去协作页审批）；
 * accept/reject 时通知申请人（routePath=/collaboration?tab=mine 或 /article/{id} 提醒去看结果）。
 * 同意/拒绝操作放独立「我的协作」页，通知仅提醒+跳转——不改 NotifySupport（needConfirm 仍 0）。
 */
public interface ArticleContributorService {

    /** 提交一条贡献申请（articleId 来自路径，userId/currentUser 由 service 取登录态）。
     *  去重：若已有 PENDING/APPROVED 的 ACTIVE 行则直接返回提示（不重复建申请）；REJECTED 行允许重申（软删旧行再插新 PENDING）。 */
    Boolean apply(Long articleId);

    /** 查当前用户对该文章的贡献申请态（供 detail.vue 申请按钮态判定）。
     *  返回 null（未申请）或 PENDING/APPROVED/REJECTED；service 内对作者直接返 null（作者无需申请）。 */
    String myStatus(Long articleId);

    /** 作者侧：列出当前用户作为文章作者收到的申请（可按 status 过滤） */
    PageInfo<ArticleContributorVo> listReceived(String status);

    /** 申请人侧：列出当前用户自己发过的申请（可按 status 过滤） */
    PageInfo<ArticleContributorVo> listMine(String status);

    /** 作者同意申请（applicantId=contributor_id 主键）。校验：操作人是该文章作者 → APPROVED + 通知申请人。 */
    Boolean accept(Long applicantId);

    /** 作者驳回申请。校验同 accept；REJECTED + advice 必填 + 通知申请人。 */
    Boolean reject(Long applicantId, String advice);
}