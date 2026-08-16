package com.knowhub.service.review;

import com.knowhub.config.ReviewNotifyConfigReader;
import com.knowhub.mapper.user.ReviewNotifyRoleMapper;
import com.knowhub.support.NotifySupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 提审通知服务：作品进入 PENDING_REVIEW 时，按系统设置 {@link ReviewNotifyConfigReader} 配置的
 * 角色 role_key，找持有该角色且有效的用户 list，逐一发站内通知。
 *
 * <p>调用方:BlogServiceImpl / ArticleServiceImpl / ProjectServiceImpl / ResourceServiceImpl 的
 * publishXxx 方法在 {@code reviewEnabled} 分支末尾调一次 {@link #notifyReviewers}。失败由
 * {@link NotifySupport#notifyUser} 内部 try/catch 吞掉（仅 warn），不阻断发布主流程——
 * 与 {@code notifyReviewResult} 通知作者的哲学同等。
 *
 * <p>四个模块共用一处实现,避免每个 ServiceImpl 各自重复"取 role_key → mapper 取 userIds → for loop"。
 * 通知文案统一格式:"《title》由 authorName 提交审核,请前往后台审核"。
 *
 * <p>routePath 走前台作品详情路由(/blog|/article|/project|/resource/{id}),与现有 NotifySupport
 * 通知作者用的 routePath 同口径——通知详情页"前往查看"跳前台详情,reviewer 看完进后台审核。
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