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
import com.knowhub.mapper.article.ArticleContributorMapper;
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
import com.knowhub.support.NotifySupport;
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
    private ArticleContributorMapper articleContributorMapper;

    @Autowired
    private ViewHistoryService viewHistoryService;

    @Autowired
    private NotifySupport notifySupport;

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
        // 列表回填 canReview + canEdit（chapters.vue 章节审核/编辑/发布/撤回/下架按钮就靠它）：
        // canReview = 文章作者 OR knowhub:chapter:review，且仅 PENDING_AUTHOR_REVIEW 章节且不能审自己提交的；
        // canEdit = 章节 canEditChapter（章节作者 OR 文章作者 OR 系统编辑级）——作者可编辑任意章节直接在行内编辑，
        // 贡献者仅对自己章节 canEdit=true（编辑作者章节走 editChapterInfo 内的「编辑申请」分支，列表态保持不可编辑）。
        boolean isArticleAuthor = article.getAuthorId() != null && article.getAuthorId().equals(user.getUserId());
        boolean hasReviewPerm = hasButtonPerm("knowhub:chapter:review");
        if (voPage.getList() != null) {
            for (ChapterVo vo : voPage.getList()) {
                boolean pending = ChapterStatus.PENDING_AUTHOR_REVIEW.getCode().equals(vo.getStatus());
                boolean notOwn = vo.getAuthorId() == null || !vo.getAuthorId().equals(user.getUserId());
                vo.setCanReview(pending && (isArticleAuthor || hasReviewPerm) && notOwn);
                // canEdit 按章节维度：复用 canEditChapter（章节作者/文章作者/系统编辑级），贡献者只在自己章节为 true。
                // 注意：列表不把 canEdit 加进 isArticleAuthor 短路——作者在已发布章节 canEdit 仍 false（须先撤回），
                // canEditChapter 不含状态过滤，这里额外挡：PUBLISHED/PENDING_AUTHOR_REVIEW 不可编辑（须先撤回/等审核）。
                boolean statusEditable = !ChapterStatus.PUBLISHED.getCode().equals(vo.getStatus())
                        && !ChapterStatus.PENDING_AUTHOR_REVIEW.getCode().equals(vo.getStatus());
                if (statusEditable && vo.getChapterId() != null) {
                    // lazy build chapter 只为 canEditChapter 比对（vo 已含 authorId，但 canEditChapter 要 Chapter 入参）
                    Chapter c = new Chapter();
                    c.setAuthorId(vo.getAuthorId());
                    c.setArticleId(vo.getArticleId());
                    vo.setCanEdit(canEditChapter(c, article, user));
                }
            }
        }
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
        // 通知文章作者：有新章节贡献待审核（routePath 指向章节管理页，作者就地审核）
        notifyChapterContribution(article, chapter, user, false);
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
        Article article = articleMapper.getArticleInfoById(exist.getArticleId());
        if (article == null) {
            throw new ServiceException(500, "所属文章不存在");
        }
        UserInfo user = currentUser();
        boolean isArticleAuthor = article.getAuthorId() != null && article.getAuthorId().equals(user.getUserId());
        boolean canEdit = canEditChapter(exist, article, user);
        // 贡献者编辑作者章节：走「编辑申请」进 PENDING_AUTHOR_REVIEW 待作者审（用户拍板，内联复用审核流水，不建表）。
        // 贡献者非作者、canEditChapter 不放行（章节作者/文章作者/系统编辑级），但被作者批准的 APPROVED 贡献者可对
        // 「作者本人写的章节」提编辑申请——新版本直接落库覆盖原内容 + 置 PENDING 待作者审。贡献者自己提交的章节走
        // 上面 canEdit 章节作者分支放行编辑，不进这条。此分支不撞 PUBLISHED/PENDING 禁编前置（贡献者编辑作者已发布
        // 章节本就是申请重审，作者审通过→PUBLISHED 回来，驳回→REJECTED 进已驳回态，作者可后续介入恢复）。
        if (!isArticleAuthor && !canEdit) {
            Boolean approved = articleContributorMapper.isApproved(article.getArticleId(), user.getUserId());
            boolean authorChapter = exist.getAuthorId() != null && article.getAuthorId() != null
                    && exist.getAuthorId().equals(article.getAuthorId());
            if (Boolean.TRUE.equals(approved) && authorChapter) {
                Chapter chapter = BeanUtil.toBean(vo, Chapter.class);
                chapter.setUpdateBy(user.getUsername());
                chapter.setStatus(ChapterStatus.PENDING_AUTHOR_REVIEW.getCode());
                chapter.setReviewStatus(ReviewStatus.PENDING.getCode());
                try {
                    chapterMapper.editChapterInfo(chapter);
                } catch (Exception e) {
                    throw new ServiceException(500, "章节修改失败", e.getMessage());
                }
                writeChapterReviewLog(exist.getChapterId(), ReviewAction.SUBMIT, user, "贡献者提交编辑申请待作者审核");
                // 通知文章作者：有编辑申请待审（文案明确「编辑申请」而非「提交新章节」，区别于新章节贡献通知）
                if (article.getAuthorId() != null) {
                    String title = "有章节编辑申请待你审核";
                    String content = user.getUsername() + " 申请修改你的文章《" + article.getTitle()
                            + "》的章节《" + exist.getChapterName() + "》，请你前往章节管理页审核。";
                    notifySupport.notifyUser(article.getAuthorId(), title, content,
                            "/article/" + article.getArticleId() + "/chapters", "system");
                }
                return true;
            }
            throw new ServiceException(500, "无权编辑该章节（贡献者编辑作者章节需申请，作者审核后才生效）");
        }
        // 作者/章节作者/系统编辑路径：保留 PUBLISHED/PENDING 禁编前置（防绕审改已发布）
        if (ChapterStatus.PUBLISHED.getCode().equals(exist.getStatus())) {
            throw new ServiceException(500, "已发布章节请先撤回再编辑");
        }
        if (ChapterStatus.PENDING_AUTHOR_REVIEW.getCode().equals(exist.getStatus())) {
            throw new ServiceException(500, "审核中章节不能编辑，如需修改请先撤回或等审核结果");
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
    public Boolean reorderChapters(List<ChapterVo> orders) {
        // 前台长按拖拽重排持久化：入参每项带 chapterId + 新 sortOrder + articleId。
        // 校验：非空；所有 chapterId 归属同一 articleId（前端传文章视角的整列顺序，归属一致才合法）；
        // 逐章按 canEditChapter 鉴权（同编辑态权限口径，章节作者 OR 文章作者 OR 系统编辑权限够），
        // 仅改 sort_order + 审计列，不动正文/状态。中途任一章越权/不存在即抛错回滚（事务保证一致性）。
        if (orders == null || orders.isEmpty()) {
            throw new ServiceException(500, "重排列表不能为空");
        }
        // 取第一项 articleId 作为基准，校验所有项归属一致
        Long baseArticleId = orders.get(0).getArticleId();
        if (baseArticleId == null) {
            throw new ServiceException(500, "章节归属文章不能为空");
        }
        Article article = articleMapper.getArticleInfoById(baseArticleId);
        if (article == null) {
            throw new ServiceException(500, "所属文章不存在");
        }
        UserInfo user = currentUser();
        for (ChapterVo o : orders) {
            if (o.getChapterId() == null || o.getSortOrder() == null) {
                throw new ServiceException(500, "章节ID与排序值不能为空");
            }
            // 归属一致：每项 articleId 必须与基准相符（防跨文章拖拽串改）
            if (!baseArticleId.equals(o.getArticleId())) {
                throw new ServiceException(500, "重排章节必须属于同一文章");
            }
            Chapter exist = chapterMapper.getChapterInfoById(o.getChapterId());
            if (exist == null) {
                throw new ServiceException(500, "章节不存在：" + o.getChapterId());
            }
            if (!canEditChapter(exist, article, user)) {
                throw new ServiceException(500, "无权重排该章节：" + o.getChapterId());
            }
            chapterMapper.updateSortOrder(o.getChapterId(), o.getSortOrder(), user.getUsername());
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
        // 通知文章作者：有新章节贡献待审核（publishChapter 是再提交存稿分支，reSubmit=true）
        notifyChapterContribution(article, exist, user, true);
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
    public Boolean takedownChapter(Long chapterId, String advice) {
        // 作者下架贡献者章节（拆自 revokeChapter 与 reject 的交集）：advice 必填，状态置 REVOKED，
        // 写 chapter_review_log REJECT 流水记下架原因，通知章节作者（贡献者）带原因。不硬删可追溯。
        if (advice == null || advice.isEmpty()) {
            throw new ServiceException(500, "下架需说明原因");
        }
        Chapter exist = chapterMapper.getChapterInfoById(chapterId);
        if (exist == null) {
            throw new ServiceException(500, "章节不存在");
        }
        Article article = articleMapper.getArticleInfoById(exist.getArticleId());
        if (article == null) {
            throw new ServiceException(500, "所属文章不存在");
        }
        UserInfo user = currentUser();
        // 鉴权：文章作者 OR knowhub:chapter:delete 按钮权限（与 deleteChapterInfo 同口径；章节作者自己不能下架自己——
        // 章节作者撤自己走 revokeChapter，takedown 是「别人下架你」，仅作者级权限能用）。
        boolean isArticleAuthor = article.getAuthorId() != null && article.getAuthorId().equals(user.getUserId());
        if (!isArticleAuthor && !hasButtonPerm("knowhub:chapter:delete")) {
            throw new ServiceException(500, "仅文章作者可下架该章节");
        }
        Chapter update = new Chapter();
        update.setChapterId(chapterId);
        update.setStatus(ChapterStatus.REVOKED.getCode());
        update.setReviewStatus(ReviewStatus.NONE.getCode());
        update.setUpdateBy(user.getUsername());
        chapterMapper.editChapterInfo(update);
        // 写 REJECT 流水记下架原因（复用驳回流水语义；区分点：takedown 章节原状态可能 PUBLISHED，reviewChapter 只处理 PENDING_AUTHOR_REVIEW）。
        writeChapterReviewLog(chapterId, ReviewAction.REJECT, user, advice);
        // 通知章节作者（贡献者）：你的章节被作者下架，原因 advice。命中 avoid（章节作者==下架者则无需通知：作者下架自己写的章节无意义但可能发生）
        notifyChapterTakedown(exist, article, advice);
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
        // 通知章节提交者审核结果（routePath=章节阅读页）：avoid 已保障 chapter.author_id==userId 走不到这里
        notifyChapterReviewResult(exist, article, action, advice);
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

    /** 当前用户能否编辑文章（系统 edit 等级够 OR 文章作者 OR 被作者批准的 CONTRIBUTOR），用于判定非作者能否提交章节。
     *  第三分支新增：article_contributor 表里 status=APPROVED 且 deleted=0 的贡献者也放行——aisle 不要求系统编辑级权限，
     *  由作者就地审批授权。与作者并列放行，不冲突既有系统编辑级分支。 */
    private boolean canEditArticle(Article article) {
        ArticlePermissionLevel lvl = ArticlePermissionResolver.resolve();
        if (article.getLevel() != null && lvl.edit() >= article.getLevel()) {
            return true;
        }
        if (article.getAuthorId() != null && article.getAuthorId().equals(currentUser().getUserId())) {
            return true;
        }
        // 被作者批准的贡献者：放行（不要求系统编辑级权限），供非作者读者经申请-审批后接力写章节
        Boolean approved = articleContributorMapper.isApproved(article.getArticleId(), currentUser().getUserId());
        return Boolean.TRUE.equals(approved);
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

    /**
     * 通知文章作者：有新章节贡献待审核（SEMIPUBLIC 非作者 submit/publish 进 PENDING_AUTHOR_REVIEW 时调用）。
     * 收件人=article.author_id；routePath 指向章节管理页 /article/{articleId}/chapters，作者就地审核。
     * 通知 best-effort，由 NotifySupport 内部吞失败，不阻断章节落库。
     */
    private void notifyChapterContribution(Article article, Chapter chapter, UserInfo submitter, boolean reSubmit) {
        if (article == null || article.getAuthorId() == null) {
            return;
        }
        String action = reSubmit ? "重新提交" : "提交";
        String title = "有新章节贡献待你审核";
        String content = submitter.getUsername() + " 向你的文章《" + article.getTitle()
                + "》" + action + "了章节《" + chapter.getChapterName() + "》，请你审核。";
        notifySupport.notifyUser(article.getAuthorId(), title, content,
                "/article/" + article.getArticleId() + "/chapters", "system");
    }

    /**
     * 通知章节提交者审核结果（reviewChapter APPROVE/REJECT 后调用）。
     * 收件人=chapter.author_id（提交者）；routePath 指向章节阅读页 /article/{articleId}/read/{chapterId}。
     * 回避保障：reviewChapter 已对 chapter.author_id==userId 抛"不能审核自己提交的章节"，不会自通知。
     */
    private void notifyChapterReviewResult(Chapter chapter, Article article, ReviewAction action, String advice) {
        if (chapter == null || chapter.getAuthorId() == null) {
            return;
        }
        String title;
        String content;
        switch (action) {
            case APPROVE:
                title = "你的章节贡献审核通过";
                content = "你向《" + (article != null ? article.getTitle() : "") + "》贡献的章节《"
                        + chapter.getChapterName() + "》审核通过，已发布。"
                        + (advice != null && !advice.isEmpty() ? "审核意见：" + advice : "");
                break;
            case REJECT:
                title = "你的章节贡献被驳回";
                content = "你向《" + (article != null ? article.getTitle() : "") + "》贡献的章节《"
                        + chapter.getChapterName() + "》被驳回。"
                        + (advice != null && !advice.isEmpty() ? "驳回原因：" + advice : "");
                break;
            default:
                return;
        }
        notifySupport.notifyUser(chapter.getAuthorId(), title, content,
                "/article/" + chapter.getArticleId() + "/read/" + chapter.getChapterId(), "system");
    }

    /**
     * 通知章节作者（贡献者）其章节被文章作者下架（takedownChapter 后调用）。
     * 收件人=chapter.author_id；routePath 指向章节管理页 /article/{articleId}/chapters。
     * 命中章节 author_id==null（作者写自己章节、无贡献者身态）不通知。
     */
    private void notifyChapterTakedown(Chapter chapter, Article article, String advice) {
        if (chapter == null || chapter.getAuthorId() == null) {
            return;
        }
        String title = "你的章节被文章作者下架";
        String content = "你在文章《" + (article != null ? article.getTitle() : "") + "》中贡献的章节《"
                + chapter.getChapterName() + "》被文章作者下架。下架原因：" + advice;
        notifySupport.notifyUser(chapter.getAuthorId(), title, content,
                "/article/" + chapter.getArticleId() + "/chapters", "system");
    }

    private UserInfo currentUser() {
        return (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
