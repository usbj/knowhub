package com.knowhub.listener;

import com.knowhub.pojo.event.WorkReviewResultEvent;
import com.knowhub.pojo.event.WorkSubmittedForReviewEvent;
import com.knowhub.service.review.ReviewNotifyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 作品审核通知事件监听器：把审核流程与通知发送在执行层解耦。
 *
 * <p>audit service（blog/article/chapter/project/resource 的 publishXxx / reviewXxx /
 * submitChapter / editChapterInfo / publishChapter / takedownChapter）只
 * {@code applicationEventPublisher.publishEvent(...)} 发事件，不直接调通知；
 * 本监听器在审核事务 {@code AFTER_COMMIT} 后收到事件，调 {@link ReviewNotifyService} 落站内通知。
 *
 * <p>为何 AFTER_COMMIT：审核状态变更与流水写入提交后再发通知，避免"通知发了但审核回滚"的错配；
 * 通知失败由 {@code ReviewNotifyService} → {@code NotifySupport} 内部 try/catch 吞掉仅 warn，
 * 不向上抛——审核 tx 已 commit，通知丢失只 log，与改造前 best-effort 内联同步调等价、不会更差。
 *
 * <p>为何同步监听（不 @Async）：审核/发布是低频管理动作，同步监听够用，不引线程池；
 * 且 operator/targetUserId 已在 emit 时捕入事件载荷，监听器只读载荷不碰 SecurityContext，
 * 天然 async-safe，但少一层异步更简单可测。
 *
 * <p>reconcilePendingReview 对账自动放行不 emit 事件，保持静默不通知（批量放行不该轰炸作者），
 * 故本监听器不会被对账触发——行为与改造前一致。
 *
 * @author knowhub
 */
@Component
public class WorkReviewNotifyListener {

    @Autowired
    private ReviewNotifyService reviewNotifyService;

    /**
     * 作品进入 PENDING_REVIEW 时发提审通知给持配置角色的 reviewer。
     * <p>仅 blog/article/project/resource 的 publishXxx（审核开关开分支）emit 此事件。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSubmitted(WorkSubmittedForReviewEvent evt) {
        reviewNotifyService.notifyReviewers(evt);
    }

    /**
     * 审核/发布/章节协作动作完成时发审核结果通知给作者/章节提交者/文章作者。
     * <p>覆盖 blog/article/project/resource 的 publishXxx+reviewXxx，及 chapter 的
     * submitChapter/editChapterInfo/publishChapter/takedownChapter/reviewChapter。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onResult(WorkReviewResultEvent evt) {
        reviewNotifyService.notifyReviewResult(evt);
    }
}
