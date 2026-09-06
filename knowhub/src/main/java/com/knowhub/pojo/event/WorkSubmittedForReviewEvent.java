package com.knowhub.pojo.event;

/**
 * 作品进入 PENDING_REVIEW 时发布的事件。
 * <p>
 * 由 blog / article / project / resource 的 publishXxx 在审核开关开启分支 emit，
 * {@link com.knowhub.listener.WorkReviewNotifyListener} 收到后调
 * {@link com.knowhub.service.review.ReviewNotifyService#notifyReviewers(WorkSubmittedForReviewEvent)}
 * 按系统设置 {@code knowhub.review.notify_role_key} 通知持该角色的有效用户。
 * <p>
 * chapter 不用本事件（chapter 审核由文章作者审，不是后台 reviewer，走
 * {@link WorkReviewResultEvent} 的 subAction=CONTRIBUTION 分支）。
 * <p>
 * 事件载荷不可变、只读——operator/authorName 在 emit 时（仍在 SecurityContext 内）捕入载荷，
 * 监听器用时只读载荷，不碰 SecurityContext，天然 async-safe
 * （避开本仓库已踩的"异步 dispatch 不传播 SecurityContext"坑）。
 *
 * @param workType   作品类型标识：blog / article / project / resource（决定 routePath 前缀 + 文案类型名）
 * @param workId     作品主键
 * @param workTitle  作品标题（拼通知标题/正文，空时由 ReviewNotifyService 兜底"未命名xx"）
 * @param authorName 提交作者名（拼正文"由 xxx 提交审核"，空时兜底"匿名"）
 * @param operator   操作人名（填通知 createBy/updateBy，通常是提交者 username）
 */
public record WorkSubmittedForReviewEvent(String workType, Long workId, String workTitle,
                                          String authorName, String operator) {
}
