package com.knowhub.service.review;

import com.knowhub.config.ReviewNotifyConfigReader;
import com.knowhub.enums.common.ReviewAction;
import com.knowhub.mapper.user.ReviewNotifyRoleMapper;
import com.knowhub.pojo.event.WorkReviewResultEvent;
import com.knowhub.pojo.event.WorkSubmittedForReviewEvent;
import com.knowhub.support.NotifySupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 审核通知服务：作品审核流程的通知内容收口点。
 *
 * <p>两类通知：
 * <ul>
 *   <li><b>提审通知</b>：作品进入 PENDING_REVIEW 时，按系统设置 {@link ReviewNotifyConfigReader} 配置的
 *       角色 role_key 找持该角色且有效的用户 list，逐一发站内通知。仅 blog/article/project/resource 用
 *       （chapter 审核由文章作者审，不是后台 reviewer）。</li>
 *   <li><b>审核结果通知</b>：审核/发布/章节协作动作完成时，按 workType + action + subAction 拼文案/routePath，
 *       发给作者/章节提交者/文章作者。覆盖 blog/article/project/resource 的 APPROVE/REJECT/PUBLISH，
 *       及 chapter 的 CONTRIBUTION/EDIT_APPLY/REVIEW_RESULT/TAKEDOWN。</li>
 * </ul>
 *
 * <p>调用方为 {@link com.knowhub.listener.WorkReviewNotifyListener}（@TransactionalEventListener
 * AFTER_COMMIT），audit service 只 emit 事件不直接调本类——执行层解耦。文案集中本类，消除原 4 份私有
 * notifyReviewResult + chapter 3 个私有方法的 5x 重复。
 *
 * <p>失败由 {@link NotifySupport#notifyUser} 内部 try/catch 吞掉（仅 warn），不阻断已落库的审核状态变更
 * （与 writeReviewLog 同口径）。本类各方法再包一层 try/catch 兜底意外，保证监听器不向上抛。
 *
 * @author knowhub
 */
@Service
public class ReviewNotifyService {

    private static final Logger log = LoggerFactory.getLogger(ReviewNotifyService.class);

    @Autowired
    private ReviewNotifyConfigReader reviewNotifyConfigReader;

    @Autowired
    private ReviewNotifyRoleMapper reviewNotifyRoleMapper;

    @Autowired
    private NotifySupport notifySupport;

    // ---- chapter subAction 常量（与 WorkReviewResultEvent.subAction 对齐） ----
    /** 章节贡献：贡献者向 SEMIPUBLIC 文章提交/再提交章节，通知文章作者审核 */
    private static final String SUB_ACTION_CONTRIBUTION = "CONTRIBUTION";
    /** 章节编辑申请：贡献者编辑作者章节，通知文章作者审核 */
    private static final String SUB_ACTION_EDIT_APPLY = "EDIT_APPLY";
    /** 章节审核结果：文章作者审核贡献者章节，通知章节提交者通过/驳回 */
    private static final String SUB_ACTION_REVIEW_RESULT = "REVIEW_RESULT";
    /** 章节下架：文章作者下架贡献者章节，通知章节提交者 */
    private static final String SUB_ACTION_TAKEDOWN = "TAKEDOWN";

    /**
     * 作品进 PENDING_REVIEW 时按配置角色给持该角色的有效用户发提审通知（事件载荷版）。
     * <p>监听器透传事件，内部拆载荷调 5 参实现。
     *
     * @param evt 提审事件（workType/workId/workTitle/authorName/operator）
     */
    public void notifyReviewers(WorkSubmittedForReviewEvent evt) {
        if (evt == null) {
            return;
        }
        notifyReviewers(evt.workType(), evt.workId(), evt.workTitle(), evt.authorName(), evt.operator());
    }

    /**
     * 作品进 PENDING_REVIEW 时按配置角色给持该角色的有效用户发提审通知。
     *
     * @param workType    作品类型标识:blog / article / project / resource(决定 routePath 前缀 + 文案类型名)
     * @param workId      作品主键
     * @param workTitle   作品标题(拼通知标题/正文)
     * @param authorName  提交作者名(拼正文"由 xxx 提交审核")
     * @param operator    操作人名(填通知 createBy/updateBy,通常是提交者 username)
     */
    public void notifyReviewers(String workType, Long workId, String workTitle, String authorName, String operator) {
        try {
            if (!reviewNotifyConfigReader.isNotifyEnabled()) {
                return; // 总开关关 → 不通知
            }
            if (workId == null) {
                return;
            }
            String roleKey = reviewNotifyConfigReader.getNotifyRoleKey();
            if (roleKey == null || roleKey.trim().isEmpty()) {
                return;
            }
            List<Long> userIds = reviewNotifyRoleMapper.listActiveUserIdsByRoleKey(roleKey);
            if (userIds == null || userIds.isEmpty()) {
                return; // 该角色无有效用户 → 不通知
            }
            String routePath = buildRoutePath(workType, workId);
            String typeName = typeName(workType);
            String safeTitle = (workTitle == null || workTitle.trim().isEmpty())
                    ? "未命名" + typeName : workTitle.trim();
            String safeAuthor = (authorName == null || authorName.trim().isEmpty())
                    ? "匿名" : authorName.trim();
            String title = "有新" + typeName + "待审核:《" + safeTitle + "》";
            String content = "《" + safeTitle + "》由 " + safeAuthor + " 提交审核,请前往后台"
                    + typeName + "管理审核。";
            for (Long uid : userIds) {
                notifySupport.notifyUser(uid, title, content, routePath, operator);
            }
            log.info("[ReviewNotify] 已向 role_key={} 的 {} 个用户发送提审通知 workType={} workId={} title={}",
                    roleKey, userIds.size(), workType, workId, safeTitle);
        } catch (Exception e) {
            // 任何意外都降级为 warn(不抛),与 NotifySupport 同口径保证 publish 主流程不挂
            log.warn("[ReviewNotify] 提审通知发送异常 workType={} workId={}: {}", workType, workId, e.getMessage());
        }
    }

    /**
     * 审核/发布/章节协作动作完成时发审核结果通知（事件载荷版）。
     * <p>监听器透传事件，本方法按 workType + action + subAction 分派文案/routePath/收件人，
     * 调 {@link NotifySupport#notifyUser} 落站内通知。文案集中本方法，消除原各 service 私有 notifyReviewResult
     * 与 chapter 私有通知方法的 5x 重复。
     *
     * @param evt 审核结果事件（载荷字段见 {@link WorkReviewResultEvent}）
     */
    public void notifyReviewResult(WorkReviewResultEvent evt) {
        if (evt == null) {
            return;
        }
        try {
            if (evt.targetUserId() == null) {
                return;
            }
            if ("chapter".equals(evt.workType())) {
                notifyChapterResult(evt);
            } else {
                notifyWorkResult(evt);
            }
        } catch (Exception e) {
            // 兜底意外（NotifySupport 内部已吞异常，这里再兜一层防分派逻辑自身抛错），不阻断监听器
            log.warn("[ReviewNotify] 审核结果通知发送异常 workType={} workId={} action={}: {}",
                    evt.workType(), evt.workId(), evt.action(), e.getMessage());
        }
    }

    /**
     * blog/article/project/resource 的审核结果通知：按 action 拼"你的xx审核通过/被驳回/已发布"。
     * <p>SUBMIT/REVOKE 的 default 不发（作者是发起人，已知晓提交/撤回，无需自提醒）。
     * routePath = /{workType}/{workId}，收件人 = evt.targetUserId（emit 时填 authorId）。
     */
    private void notifyWorkResult(WorkReviewResultEvent evt) {
        String typeName = typeName(evt.workType());
        String safeTitle = (evt.workTitle() == null || evt.workTitle().trim().isEmpty())
                ? "未命名" + typeName : evt.workTitle().trim();
        String title;
        String content;
        ReviewAction action = evt.action();
        if (action == null) {
            return;
        }
        switch (action) {
            case APPROVE:
                title = "你的" + typeName + "审核通过";
                content = "《" + safeTitle + "》审核通过，已发布。"
                        + (evt.advice() != null && !evt.advice().isEmpty() ? "审核意见：" + evt.advice() : "");
                break;
            case REJECT:
                title = "你的" + typeName + "被驳回";
                content = "《" + safeTitle + "》被驳回，请修改后重新发布。"
                        + (evt.advice() != null && !evt.advice().isEmpty() ? "驳回原因：" + evt.advice() : "");
                break;
            case PUBLISH:
                title = "你的" + typeName + "已发布";
                content = "《" + safeTitle + "》已直接发布（审核未开启）。";
                break;
            default:
                // SUBMIT / REVOKE 不通知：作者主动行为，已知晓提交/撤回，无需自提醒。
                return;
        }
        notifySupport.notifyUser(evt.targetUserId(), title, content,
                "/" + evt.workType() + "/" + evt.workId(), evt.operator());
    }

    /**
     * chapter 的审核/协作通知：按 subAction 拼 4 种文案，收件人/routePath 随 subAction 变化。
     * <ul>
     *   <li>CONTRIBUTION：通知文章作者"有新章节贡献待你审核"（reSubmit 文案"重新提交"），
     *       routePath = /article/{parentWorkId}/chapters，收件人 = 文章作者。</li>
     *   <li>EDIT_APPLY：通知文章作者"有章节编辑申请待你审核"，routePath 同上。</li>
     *   <li>REVIEW_RESULT：按 action APPROVE/REJECT 通知章节提交者"你的章节贡献审核通过/被驳回"，
     *       routePath = /article/{parentWorkId}/read/{workId}，收件人 = 章节提交者。</li>
     *   <li>TAKEDOWN：通知章节提交者"你的章节被文章作者下架"（advice = 下架原因），
     *       routePath = /article/{parentWorkId}/chapters，收件人 = 章节提交者。</li>
     * </ul>
     * article 标题 null 兜底空串（对齐原 ChapterServiceImpl 私有方法 `article != null ? article.getTitle() : ""`）。
     */
    private void notifyChapterResult(WorkReviewResultEvent evt) {
        String articleTitle = evt.workTitle() != null ? evt.workTitle() : "";
        String chapterName = evt.subName() != null ? evt.subName() : "";
        Long articleId = evt.parentWorkId();
        Long chapterId = evt.workId();
        String subAction = evt.subAction();
        String title;
        String content;
        String routePath;
        if (SUB_ACTION_CONTRIBUTION.equals(subAction)) {
            String action = evt.reSubmit() ? "重新提交" : "提交";
            title = "有新章节贡献待你审核";
            content = evt.operator() + " 向你的文章《" + articleTitle
                    + "》" + action + "了章节《" + chapterName + "》，请你审核。";
            routePath = "/article/" + articleId + "/chapters";
        } else if (SUB_ACTION_EDIT_APPLY.equals(subAction)) {
            title = "有章节编辑申请待你审核";
            content = evt.operator() + " 申请修改你的文章《" + articleTitle
                    + "》的章节《" + chapterName + "》，请你前往章节管理页审核。";
            routePath = "/article/" + articleId + "/chapters";
        } else if (SUB_ACTION_REVIEW_RESULT.equals(subAction)) {
            ReviewAction action = evt.action();
            if (action == ReviewAction.APPROVE) {
                title = "你的章节贡献审核通过";
                content = "你向《" + articleTitle + "》贡献的章节《" + chapterName + "》审核通过，已发布。"
                        + (evt.advice() != null && !evt.advice().isEmpty() ? "审核意见：" + evt.advice() : "");
            } else if (action == ReviewAction.REJECT) {
                title = "你的章节贡献被驳回";
                content = "你向《" + articleTitle + "》贡献的章节《" + chapterName + "》被驳回。"
                        + (evt.advice() != null && !evt.advice().isEmpty() ? "驳回原因：" + evt.advice() : "");
            } else {
                return;
            }
            routePath = "/article/" + articleId + "/read/" + chapterId;
        } else if (SUB_ACTION_TAKEDOWN.equals(subAction)) {
            title = "你的章节被文章作者下架";
            content = "你在文章《" + articleTitle + "》中贡献的章节《" + chapterName + "》被文章作者下架。下架原因：" + evt.advice();
            routePath = "/article/" + articleId + "/chapters";
        } else {
            return;
        }
        notifySupport.notifyUser(evt.targetUserId(), title, content, routePath, "system");
    }

    /** 拼前台作品详情路由 */
    private String buildRoutePath(String workType, Long workId) {
        return "/" + workType + "/" + workId;
    }

    /** workType → 文案上的类型名 */
    private String typeName(String workType) {
        switch (workType) {
            case "blog":    return "博客";
            case "article": return "文章";
            case "project": return "项目";
            case "resource":return "资源";
            default:        return "作品";
        }
    }
}
