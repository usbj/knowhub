package com.knowhub.service.article;

import cn.hutool.core.bean.BeanUtil;
import com.github.pagehelper.PageInfo;
import com.knowhub.support.ArticlePermissionResolver;
import com.knowhub.support.ArticlePermissionResolver.ArticlePermissionLevel;
import com.knowhub.enums.article.ArticleVisibility;
import com.knowhub.enums.article.ChapterStatus;
import com.knowhub.enums.common.ReviewAction;
import com.knowhub.enums.common.ReviewStatus;
import com.knowhub.mapper.article.ArticleMapper;
import com.knowhub.mapper.article.ChapterMapper;
import com.knowhub.mapper.article.ChapterReviewLogMapper;
import com.knowhub.pojo.article.entity.Article;
import com.knowhub.pojo.article.entity.Chapter;
import com.knowhub.pojo.article.entity.ChapterReviewLog;
import com.knowhub.pojo.article.quarry.ChapterQuarry;
import com.knowhub.pojo.article.vo.ChapterReviewLogVo;
import com.knowhub.pojo.article.vo.ChapterReviewVo;
import com.knowhub.pojo.article.vo.ChapterVo;
import com.knowhub.service.article.impl.ChapterService;
import com.knowhub.service.history.impl.ViewHistoryService;
import com.knowhub.enums.history.ViewBizType;
import com.rookie.common.exception.ServiceException;
import com.rookie.common.util.PageUtil;
import com.rookie.framework.security.pojo.Permission;
import com.rookie.framework.security.pojo.UserInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 章节管理 Service 实现（章节 ≈ 博客，正文走主表不分表）。
 *
 * 章节可见性 = 文章可见性（章节不分等级）。章节提交状态机由文章 visibility + 提交者是否文章作者决定：
 * - 作者本人提交（任意 visibility）   DRAFT → PUBLISHED 免审（作者不审自己）
 * - 非作者提交 PRIVATE 文章             拒绝提交（无章节编辑权，PRIVATE 仅作者能写）
 * - 非作者提交 SEMIPUBLIC 文章          DRAFT → PENDING_AUTHOR_REVIEW → 作者审 → PUBLISHED/REJECTED
 * - 非作者提交 PUBLIC 文章              DRAFT → PUBLISHED 免审
 * 章节编辑权限：章节作者 OR 文章作者 OR 系统编辑权限够（edit:lN≥文章level）。
 * 章节作者审核（仅 SEMIPUBLIC）：审核人必须是文章作者（article.author_id 比对），写 chapter_review_log，
 * 不受系统审核开关影响（visibility tier 固有机制，独立于文章系统审核）。
 * PUBLISHED 禁编须先 revoke 再改（对齐博客/文章 PUBLISHED 禁编范式，防绕审改已发布）。
 */
@Service
public class ChapterServiceImpl implements ChapterService {

    @Autowired
    private ChapterMapper chapterMapper;

    @Autowired
    private ChapterReviewLogMapper chapterReviewLogMapper;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private ViewHistoryService viewHistoryService;

    @Override
    public PageInfo<ChapterVo> quarryChapter(ChapterQuarry quarry) {
        // 章节 list 通常按 articleId 过滤；取所属文章作者 userId 回填 quarry 供 SQL 非PUBLISHED可见性判定
        if (quarry.getArticleId() == null) {
            throw new ServiceException(500, "章节列表需指定所属文章 articleId");
        }
        Article article = articleMapper.getArticleInfoById(quarry.getArticleId());
        if (article == null) {
            throw new ServiceException(500, "所属文章不存在");
        }
        UserInfo user = currentUser();
        ArticlePermissionLevel lvl = ArticlePermissionResolver.resolve();
        quarry.setUserViewLevel(lvl.view());
        quarry.setUserId(user.getUserId());
        quarry.setArticleAuthorId(article.getAuthorId());
        // 章节可见性=文章可见性：能看文章才进章节列表（canOp(view) 复用文章判定：系统级 OR 作者）
        if (!canViewArticle(article, lvl, user)) {
            throw new ServiceException(500, "无权查看该文章的章节");
        }
        PageUtil.startPage();
        List<Chapter> list = chapterMapper.quarryChapter(quarry);
        PageInfo<Chapter> page = PageUtil.packagedPageInfo(list);
        PageInfo<ChapterVo> voPage = PageUtil.copyPageInfo(page, ChapterVo.class);
        // 列表不回填 canEdit/canReview（详情接口才回填）；authorNickname/articleTitle 等由 join 带出经拷贝
        return voPage;
    }

    @Override
    public ChapterVo getChapterInfo(Long chapterId) {
        Chapter chapter = chapterMapper.getChapterInfoById(chapterId);
        if (chapter == null) {
            throw new ServiceException(500, "章节不存在");
        }
        // 二次权限校验：章节可见性=文章可见性
        Article article = articleMapper.getArticleInfoById(chapter.getArticleId());
        if (article == null) {
            throw new ServiceException(500, "所属文章不存在");
        }
        UserInfo user = currentUser();
        ArticlePermissionLevel lvl = ArticlePermissionResolver.resolve();
        // PUBLISHED 章节：能看文章即可看；非 PUBLISHED 章节：仅章节作者 OR 文章作者可见
        if (!ChapterStatus.PUBLISHED.getCode().equals(chapter.getStatus())) {
            boolean isChapterAuthor = chapter.getAuthorId() != null && chapter.getAuthorId().equals(user.getUserId());
            boolean isArticleAuthor = article.getAuthorId() != null && article.getAuthorId().equals(user.getUserId());
            if (!isChapterAuthor && !isArticleAuthor) {
                throw new ServiceException(500, "无权查看该章节");
            }
        } else if (!canViewArticle(article, lvl, user)) {
            // PUBLISHED 章节仍需能看文章（文章等级门控）
            throw new ServiceException(500, "无权查看该章节");
        }
        ChapterVo vo = BeanUtil.toBean(chapter, ChapterVo.class);
        // 回填当前用户对该章节的权限态（供前端控制编辑/审核按钮显隐）
        fillChapterPermissionState(vo, chapter, article, user, lvl);
        // 记录浏览（登录态，防刷去重，主表 view_count 仅首次 +1）
        viewHistoryService.recordView(user.getUserId(), ViewBizType.CHAPTER.getCode(), chapterId);
        return vo;
    }

    @Override
    @Transactional
    public Boolean submitChapter(ChapterVo vo) {
        if (vo.getArticleId() == null) {
            throw new ServiceException(500, "章节需指定所属文章 articleId");
        }
        if (vo.getChapterName() == null || vo.getChapterName().isEmpty()) {
            throw new ServiceException(500, "章节名不能为空");
        }
        if (vo.getContent() == null || vo.getContent().isEmpty()) {
            throw new ServiceException(500, "章节正文不能为空");
        }
        Article article = articleMapper.getArticleInfoById(vo.getArticleId());
        if (article == null) {
            throw new ServiceException(500, "所属文章不存在");
        }
        UserInfo user = currentUser();
        boolean isAuthor = article.getAuthorId() != null && article.getAuthorId().equals(user.getUserId());
        // 章节提交权限：作者本人 OR 有文章编辑权限者（edit:lN≥level 或作者）
        if (!isAuthor && !canEditArticle(article)) {
            throw new ServiceException(500, "无权向该文章提交章节");
        }
        Chapter chapter = BeanUtil.toBean(vo, Chapter.class);
        chapter.setAuthorId(user.getUserId());
        chapter.setCreateBy(user.getUsername());
        chapter.setUpdateBy(user.getUsername());
        chapter.setCreateTime(new Date());
        chapter.setUpdateTime(new Date());
        if (chapter.getSortOrder() == null) {
            chapter.setSortOrder(0);
        }

        // 章节提交状态机：由 visibility + 提交者是否文章作者决定
        if (isAuthor) {
            // 作者提交：任意 visibility 都免审，直接 PUBLISHED（作者不审自己）
            chapter.setStatus(ChapterStatus.PUBLISHED.getCode());
            chapter.setReviewStatus(ReviewStatus.NONE.getCode());
            chapter.setPublishTime(new Date());
            chapterMapper.addChapter(chapter);
            // 作者提交不写 chapter_review_log（免审无审核动作可记；如需追溯可记 SUBMIT+ACTION 之外的标记，当前不记）
            return true;
        }
        // 非作者提交：按 visibility 分支
        String visibility = article.getVisibility();
        if (ArticleVisibility.PRIVATE.getCode().equals(visibility)) {
            // PRIVATE 未公开：仅作者能写章节，非作者无权（前面 canEditArticle 已挡，此处兜底）
            throw new ServiceException(500, "未公开文章仅作者可提交章节");
        }
        if (ArticleVisibility.PUBLIC.getCode().equals(visibility)) {
            // PUBLIC 全公开：非作者提交直接 PUBLISHED 免审
            chapter.setStatus(ChapterStatus.PUBLISHED.getCode());
            chapter.setReviewStatus(ReviewStatus.NONE.getCode());
            chapter.setPublishTime(new Date());
            chapterMapper.addChapter(chapter);
            return true;
        }
        // SEMIPUBLIC 半公开：非作者提交进 PENDING_AUTHOR_REVIEW 待文章作者审
        chapter.setStatus(ChapterStatus.PENDING_AUTHOR_REVIEW.getCode());
        chapter.setReviewStatus(ReviewStatus.PENDING.getCode());
        chapterMapper.addChapter(chapter);
        // 写章节审核流水 SUBMIT（role=AUTHOR 章节提交者）
        writeChapterReviewLog(chapter.getChapterId(), ReviewAction.SUBMIT, user, vo.getContent() != null ? "提交章节待文章作者审核" : null);
        return true;
    }

    @Override
    @Transactional
    public Boolean editChapterInfo(ChapterVo vo) {
        if (vo.getChapterId() == null) {
            throw new ServiceException(500, "章节ID不能为空");
        }
        Chapter exist = chapterMapper.getChapterInfoById(vo.getChapterId());
        if (exist == null) {
            throw new ServiceException(500, "章节不存在");
        }
        // 状态机前置校验：PUBLISHED 禁编须先撤回（对齐博客/文章 PUBLISHED 禁编范式）
        if (ChapterStatus.PUBLISHED.getCode().equals(exist.getStatus())) {
            throw new ServiceException(500, "已发布章节请先撤回再编辑");
        }
        if (ChapterStatus.PENDING_AUTHOR_REVIEW.getCode().equals(exist.getStatus())) {
            throw new ServiceException(500, "审核中章节不能编辑，如需修改请先撤回或等审核结果");
        }
        // 权限校验：章节作者 OR 文章作者 OR 系统编辑权限够
        Article article = articleMapper.getArticleInfoById(exist.getArticleId());
        if (article == null) {
            throw new ServiceException(500, "所属文章不存在");
        }
        if (!canEditChapter(exist, article, currentUser())) {
            throw new ServiceException(500, "无权编辑该章节");
        }
        Chapter chapter = BeanUtil.toBean(vo, Chapter.class);
        chapter.setUpdateBy(currentUser().getUsername());
        try {
            chapterMapper.editChapterInfo(chapter);
        } catch (Exception e) {
            throw new ServiceException(500, "章节修改失败", e.getMessage());
        }
        return true;
    }

    @Override
    @Transactional
    public Boolean deleteChapterInfo(Long[] chapterIds) {
        UserInfo user = currentUser();
        for (Long id : chapterIds) {
            Chapter chapter = chapterMapper.getChapterInfoById(id);
            if (chapter == null) {
                continue;
            }
            Article article = articleMapper.getArticleInfoById(chapter.getArticleId());
            if (article == null) {
                continue;
            }
            // 删除权限：章节作者 OR 文章作者 OR delete 按钮权限
            boolean isChapterAuthor = chapter.getAuthorId() != null && chapter.getAuthorId().equals(user.getUserId());
            boolean isArticleAuthor = article.getAuthorId() != null && article.getAuthorId().equals(user.getUserId());
            if (!isChapterAuthor && !isArticleAuthor && !hasButtonPerm("knowhub:chapter:delete")) {
                throw new ServiceException(500, "无权删除该章节（仅章节作者/文章作者或拥有删除权限）");
            }
            chapterMapper.softDeleteChapter(id);
        }
        return true;
    }

    @Override
    @Transactional
    public Boolean publishChapter(Long chapterId) {
        // publishChapter = submitChapter 的显式语义别名：用于已存在的 DRAFT/REJECTED/REVOKED 章节再次提交
        Chapter exist = chapterMapper.getChapterInfoById(chapterId);
        if (exist == null) {
            throw new ServiceException(500, "章节不存在");
        }
        // 状态机前置：仅 DRAFT/REJECTED/REVOKED 可提交发布（PUBLISHED 已发布，PENDING_AUTHOR_REVIEW 审核中）
        String cur = exist.getStatus();
        if (ChapterStatus.PUBLISHED.getCode().equals(cur)) {
            throw new ServiceException(500, "章节已发布，无需重复提交");
        }
        if (ChapterStatus.PENDING_AUTHOR_REVIEW.getCode().equals(cur)) {
            throw new ServiceException(500, "章节审核中，请勿重复提交");
        }
        Article article = articleMapper.getArticleInfoById(exist.getArticleId());
        if (article == null) {
            throw new ServiceException(500, "所属文章不存在");
        }
        UserInfo user = currentUser();
        boolean isAuthor = article.getAuthorId() != null && article.getAuthorId().equals(user.getUserId());
        if (!isAuthor && !canEditArticle(article)) {
            throw new ServiceException(500, "无权提交该章节");
        }
        Date now = new Date();
        Chapter update = new Chapter();
        update.setChapterId(chapterId);
        update.setUpdateBy(user.getUsername());
        if (isAuthor) {
            // 作者提交：任意 visibility 免审直接 PUBLISHED
            update.setStatus(ChapterStatus.PUBLISHED.getCode());
            update.setReviewStatus(ReviewStatus.NONE.getCode());
            update.setPublishTime(now);
            chapterMapper.editChapterInfo(update);
            return true;
        }
        String visibility = article.getVisibility();
        if (ArticleVisibility.PUBLIC.getCode().equals(visibility)) {
            // PUBLIC 全公开：非作者提交直接 PUBLISHED 免审
            update.setStatus(ChapterStatus.PUBLISHED.getCode());
            update.setReviewStatus(ReviewStatus.NONE.getCode());
            update.setPublishTime(now);
            chapterMapper.editChapterInfo(update);
            return true;
        }
        if (ArticleVisibility.PRIVATE.getCode().equals(visibility)) {
            // PRIVATE 未公开：仅作者能写，非作者无权提交（兜底）
            throw new ServiceException(500, "未公开文章仅作者可提交章节");
        }
        // SEMIPUBLIC 半公开：非作者提交进 PENDING_AUTHOR_REVIEW 待文章作者审
        update.setStatus(ChapterStatus.PENDING_AUTHOR_REVIEW.getCode());
        update.setReviewStatus(ReviewStatus.PENDING.getCode());
        chapterMapper.editChapterInfo(update);
        writeChapterReviewLog(chapterId, ReviewAction.SUBMIT, user, "提交章节待文章作者审核");
        return true;
    }

    @Override
    @Transactional
    public Boolean revokeChapter(Long chapterId) {
        Chapter exist = chapterMapper.getChapterInfoById(chapterId);
        if (exist == null) {
            throw new ServiceException(500, "章节不存在");
        }
        if (!ChapterStatus.PUBLISHED.getCode().equals(exist.getStatus())) {
            throw new ServiceException(500, "仅已发布章节可撤回");
        }
        Article article = articleMapper.getArticleInfoById(exist.getArticleId());
        if (article == null) {
            throw new ServiceException(500, "所属文章不存在");
        }
        if (!canEditChapter(exist, article, currentUser())) {
            throw new ServiceException(500, "无权撤回该章节");
        }
        UserInfo user = currentUser();
        Chapter update = new Chapter();
        update.setChapterId(chapterId);
        update.setStatus(ChapterStatus.REVOKED.getCode());
        update.setReviewStatus(ReviewStatus.NONE.getCode());
        update.setUpdateBy(user.getUsername());
        chapterMapper.editChapterInfo(update);
        // 章节撤回不写 chapter_review_log（章节流水只记 SUBMIT/APPROVE/REJECT，REVOKE 走状态机不入流水）
        return true;
    }

    @Override
    @Transactional
    public Boolean reviewChapter(ChapterReviewVo vo) {
        if (vo.getChapterId() == null || vo.getPass() == null) {
            throw new ServiceException(500, "审核参数不完整");
        }
        Chapter exist = chapterMapper.getChapterInfoById(vo.getChapterId());
        if (exist == null) {
            throw new ServiceException(500, "章节不存在");
        }
        if (!ChapterStatus.PENDING_AUTHOR_REVIEW.getCode().equals(exist.getStatus())) {
            throw new ServiceException(500, "仅待作者审核章节可审核");
        }
        Article article = articleMapper.getArticleInfoById(exist.getArticleId());
        if (article == null) {
            throw new ServiceException(500, "所属文章不存在");
        }
        UserInfo user = currentUser();
        // 章节作者审核：审核人必须是文章作者（article.author_id 比对）；
        // 或拥有 knowhub:chapter:review 系统权限（管理员代审，兜底作者不在线场景）
        boolean isArticleAuthor = article.getAuthorId() != null && article.getAuthorId().equals(user.getUserId());
        boolean hasReviewPerm = hasButtonPerm("knowhub:chapter:review");
        if (!isArticleAuthor && !hasReviewPerm) {
            throw new ServiceException(500, "仅文章作者可审核该章节");
        }
        // 章节提交者不能审自己提交的章节（非作者提交场景，提交者==审核人时回避）
        if (exist.getAuthorId() != null && exist.getAuthorId().equals(user.getUserId())) {
            throw new ServiceException(500, "不能审核自己提交的章节");
        }
        Date now = new Date();
        Chapter update = new Chapter();
        update.setChapterId(vo.getChapterId());
        update.setUpdateBy(user.getUsername());
        ReviewAction action;
        String advice = null;
        if (vo.getPass()) {
            update.setStatus(ChapterStatus.PUBLISHED.getCode());
            update.setPublishTime(now);
            update.setReviewStatus(ReviewStatus.APPROVED.getCode());
            action = ReviewAction.APPROVE;
            advice = vo.getAdvice();
        } else {
            if (vo.getAdvice() == null || vo.getAdvice().isEmpty()) {
                throw new ServiceException(500, "驳回需填写审核意见");
            }
            update.setStatus(ChapterStatus.REJECTED.getCode());
            update.setReviewStatus(ReviewStatus.REJECTED.getCode());
            action = ReviewAction.REJECT;
            advice = vo.getAdvice();
        }
        chapterMapper.editChapterInfo(update);
        writeChapterReviewLog(vo.getChapterId(), action, user, advice);
        return true;
    }

    @Override
    public List<ChapterReviewLogVo> listReviewLog(Long chapterId) {
        List<ChapterReviewLog> logs = chapterReviewLogMapper.listByChapterId(chapterId);
        if (logs == null || logs.isEmpty()) {
            return new ArrayList<>();
        }
        return logs.stream()
                .map(log -> BeanUtil.toBean(log, ChapterReviewLogVo.class))
                .collect(Collectors.toList());
    }

    // ============================ 私有辅助 ============================

    /** 当前用户能否查看文章（系统 view 等级够 OR 文章作者） */
    private boolean canViewArticle(Article article, ArticlePermissionLevel lvl, UserInfo user) {
        if (article.getAuthorId() != null && article.getAuthorId().equals(user.getUserId())) {
            return true;
        }
        return article.getLevel() != null && lvl.view() >= article.getLevel();
    }

    /** 当前用户能否编辑文章（系统 edit 等级够 OR 文章作者），用于判定非作者能否提交章节 */
    private boolean canEditArticle(Article article) {
        ArticlePermissionLevel lvl = ArticlePermissionResolver.resolve();
        if (article.getLevel() != null && lvl.edit() >= article.getLevel()) {
            return true;
        }
        return article.getAuthorId() != null && article.getAuthorId().equals(currentUser().getUserId());
    }

    /** 当前用户能否编辑章节（章节作者 OR 文章作者 OR 系统编辑权限够） */
    private boolean canEditChapter(Chapter chapter, Article article, UserInfo user) {
        // 章节作者能编辑自己提交的章节
        if (chapter.getAuthorId() != null && chapter.getAuthorId().equals(user.getUserId())) {
            return true;
        }
        // 文章作者能编辑其文章下任意章节
        if (article.getAuthorId() != null && article.getAuthorId().equals(user.getUserId())) {
            return true;
        }
        // 系统编辑权限够（edit:lN≥文章level）
        ArticlePermissionLevel lvl = ArticlePermissionResolver.resolve();
        return article.getLevel() != null && lvl.edit() >= article.getLevel();
    }

    /** 判断当前用户是否拥有某按钮权限（非等级，如 knowhub:chapter:delete/review）。
     *  注意：UserInfo.getPermissions() 是 List<Permission>，需遍历比 permKey，不能用 contains(String) */
    private boolean hasButtonPerm(String permKey) {
        UserInfo u = currentUser();
        if (u.getPermissions() == null) {
            return false;
        }
        for (Permission p : u.getPermissions()) {
            if (permKey.equals(p.getPermKey())) {
                return true;
            }
        }
        return false;
    }

    /** 详情接口回填当前用户对该章节的权限态（供前端控制编辑/审核按钮显隐） */
    private void fillChapterPermissionState(ChapterVo vo, Chapter chapter, Article article,
                                            UserInfo user, ArticlePermissionLevel lvl) {
        boolean isChapterAuthor = chapter.getAuthorId() != null && chapter.getAuthorId().equals(user.getUserId());
        boolean isArticleAuthor = article.getAuthorId() != null && article.getAuthorId().equals(user.getUserId());
        boolean systemEdit = article.getLevel() != null && lvl.edit() >= article.getLevel();
        vo.setCanEdit(isChapterAuthor || isArticleAuthor || systemEdit);
        // canReview：仅 PENDING_AUTHOR_REVIEW 章节有意义；文章作者 OR 系统审权限，且不能审自己提交的
        boolean canReview = ChapterStatus.PENDING_AUTHOR_REVIEW.getCode().equals(chapter.getStatus())
                && (isArticleAuthor || hasButtonPerm("knowhub:chapter:review"))
                && !isChapterAuthor;
        vo.setCanReview(canReview);
    }

    /**
     * 追加一条章节审核流水。role 由 ReviewAction 自带，operator_id 用 userId 稳定锁定，
     * operator 存 username 快照。流水表只追加不改不删，写失败不阻断主流程（catch 吞异常仅 log）。
     */
    private void writeChapterReviewLog(Long chapterId, ReviewAction action, UserInfo operator, String advice) {
        try {
            ChapterReviewLog log = new ChapterReviewLog(chapterId, action.getCode(),
                    operator.getUserId(), operator.getUsername(), action.getRole(), advice);
            chapterReviewLogMapper.insertReviewLog(log);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(ChapterServiceImpl.class)
                    .warn("写章节审核流水失败 chapterId={} action={}: {}", chapterId, action.getCode(), e.getMessage());
        }
    }

    private UserInfo currentUser() {
        return (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
