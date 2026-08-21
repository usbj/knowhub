package com.knowhub.pojo.event;

import com.knowhub.enums.common.ReviewAction;

/**
 * 审核/发布/章节协作动作完成时发布的事件，承载审核结果通知所需数据。
 * <p>
 * 由 blog / article / project / resource 的 publishXxx 与 reviewXxx、chapter 的
 * submitChapter / editChapterInfo / publishChapter / takedownChapter / reviewChapter emit，
 * {@link com.knowhub.listener.WorkReviewNotifyListener} 收到后调
 * {@link com.knowhub.service.review.ReviewNotifyService#notifyReviewResult(WorkReviewResultEvent)}
 * 按 workType + action + subAction 三维分派文案 / routePath / 收件人，落站内通知。
 * <p>
 * 用一个事件 + workType/action/subAction 三维分派覆盖五类作品全部通知点，而非为每种通知建一个事件类
 * （chapter 4 种 + 四大作品 3 种 = 7 个事件类会爆炸）。非 chapter 路径下 subName/parentWorkId/subAction/reSubmit 全 null。
 * <p>
 * 载荷不可变、只读——收件人 targetUserId 与 operator 在 emit 时（仍在 SecurityContext 内）显式捕入载荷，
 * 监听器纯转发不碰 DB、不碰 SecurityContext，天然 async-safe。
 *
 * <h3>分派规则（ReviewNotifyService.notifyReviewResult 内实现）</h3>
 * <ul>
 *   <li>workType = blog/article/project/resource：按 action 拼"你的xx审核通过/被驳回/已发布"，
 *       routePath = /{workType}/{workId}，收件人 = targetUserId（emit 时填 authorId）。
 *       SUBMIT/REVOKE 的 default 不发（作者是发起人，已知晓）。</li>
 *   <li>workType = chapter：按 subAction 拼 4 种文案——
 *       <ul>
 *         <li>CONTRIBUTION：通知文章作者"有新章节贡献待你审核"（reSubmit=true 文案"重新提交"），
 *             routePath = /article/{parentWorkId}/chapters，收件人 = targetUserId（文章作者）。</li>
 *         <li>EDIT_APPLY：通知文章作者"有章节编辑申请待你审核"，routePath 同上。</li>
 *         <li>REVIEW_RESULT：按 action APPROVE/REJECT 通知章节提交者"你的章节贡献审核通过/被驳回"，
 *             routePath = /article/{parentWorkId}/read/{workId}，收件人 = targetUserId（章节提交者）。</li>
 *         <li>TAKEDOWN：通知章节提交者"你的章节被文章作者下架"（advice = 下架原因），
 *             routePath = /article/{parentWorkId}/chapters，收件人 = targetUserId（章节提交者）。</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * @param workType     作品类型：blog / article / chapter / project / resource
 * @param workId       作品主键（chapter 用 chapterId）
 * @param targetUserId 收件人 userId（emit 时显式填：作者 / 章节提交者 / 文章作者）
 * @param workTitle    主作品标题（拼正文，chapter 用所属文章标题）
 * @param subName      章节名（仅 chapter 用，其余 null）
 * @param parentWorkId 章节所属 articleId（仅 chapter routePath 拼 /article/{parentWorkId}/...，其余 null）
 * @param action       APPROVE / REJECT / PUBLISH / SUBMIT（章节协作用 SUBMIT 表"待审"语义；TAKEDOWN 用 REJECT）
 * @param subAction    chapter 细分：CONTRIBUTION / EDIT_APPLY / REVIEW_RESULT / TAKEDOWN；非 chapter 为 null
 * @param reSubmit     chapter CONTRIBUTION 再提交标记（文案"重新提交"）；非 CONTRIBUTION 为 false
 * @param advice       审核意见（驳回必填、通过可选、PUBLISH 直通为 null、TAKEDOWN 为下架原因）
 * @param operator     操作人名（填通知 createBy/updateBy：submit 场景传提交者 username，review/publish 场景传 "system"）
 */
public record WorkReviewResultEvent(String workType, Long workId, Long targetUserId, String workTitle,
                                    String subName, Long parentWorkId, ReviewAction action, String subAction,
                                    boolean reSubmit, String advice, String operator) {
}
