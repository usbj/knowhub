package com.knowhub.service.article;

import cn.hutool.core.bean.BeanUtil;
import com.github.pagehelper.PageInfo;
import com.knowhub.support.ArticlePermissionResolver;
import com.knowhub.support.ArticlePermissionResolver.ArticlePermissionLevel;
import com.knowhub.config.ArticleConfigReader;
import com.knowhub.enums.article.ArticleLevel;
import com.knowhub.enums.article.ArticleStatus;
import com.knowhub.enums.article.ArticleVisibility;
import com.knowhub.enums.storage.FileBusinessType;
import com.knowhub.enums.common.ReviewAction;
import com.knowhub.enums.common.ReviewStatus;
import com.knowhub.mapper.article.ArticleMapper;
import com.knowhub.mapper.article.ArticleContributorMapper;
import com.knowhub.mapper.article.ArticleTagMapper;
import com.knowhub.mapper.tag.TagMapper;
import com.knowhub.mapper.article.ArticleReviewLogMapper;
import com.knowhub.mapper.article.ChapterMapper;
import com.knowhub.mapper.storage.FileObjectMapper;
import com.knowhub.pojo.article.entity.Article;
import com.knowhub.pojo.article.entity.ArticleTag;
import com.knowhub.pojo.tag.entity.Tag;
import com.knowhub.pojo.article.entity.ArticleReviewLog;
import com.knowhub.pojo.article.quarry.ArticleQuarry;
import com.knowhub.pojo.article.vo.ArticleReviewLogVo;
import com.knowhub.pojo.article.vo.ArticleReviewVo;
import com.knowhub.pojo.article.vo.ArticleVo;
import com.knowhub.service.article.impl.ArticleService;
import com.knowhub.service.history.impl.ViewHistoryService;
import com.knowhub.service.review.ReviewNotifyService;
import com.knowhub.enums.history.ViewBizType;
import com.knowhub.support.NotifySupport;
import com.rookie.common.exception.ServiceException;
import com.rookie.common.util.PageUtil;
import com.rookie.framework.security.pojo.Permission;
import com.rookie.framework.security.pojo.UserInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文章管理 Service 实现。
 *
 * 权限模型（轻量，仅系统级 + 作者归属，无成员表）：
 * - 系统权限（全局、分等级、所有文章）：view/edit:lN，ArticlePermissionResolver 一次扫描
 *   List<Permission> 取最高等级（admin 零特判，登录时全 perm_key 已塞入）
 * - 作者归属：文章 author_id 是单一所有者，作者对自己的文章全权（不看等级/不看 visibility），
 *   类比项目 LEADER 的"所有者"但无成员表，由 canOp 在系统权限外补 author_id==userId 分支
 * 判定公式 canOp(U,A,op)：userLvl(op) >= A.level OR author_id == userId（作者全权）
 * delete：author 或 hasPerm('knowhub:article:delete')
 *
 * 审核流程复用博客/项目范式（状态机+回避+流水表+对账任务），代码模式与 ProjectServiceImpl 同构：
 * - 主表只存 status/review_status/publish_time，审核员/审核时间/审核意见全在 article_review_log
 * - publish 经审核开关决定 PENDING_REVIEW 或 PUBLISHED，进 PENDING_REVIEW 时 SET Redis 标记
 * - review 校验状态+回避（author_id 比对，作者不能审自己），写流水 APPROVE/REJECT
 * - reconcilePendingReview 逐条放行遗留待审文章，对账任务在开关关闭+标记存在时调用
 *
 * 删文章级联：chapter（softDeleteByArticleId）+ 封面 file_object（softDeleteByBizRef ARTICLE_COVER），
 * 对象本体由 FileGcTask 回收。文章表不加 project_id（单向关联：project.article_id → article）。
 */
@Service
public class ArticleServiceImpl implements ArticleService {

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private ArticleTagMapper articleTagMapper;

    @Autowired
    private ArticleContributorMapper articleContributorMapper;

    @Autowired
    private TagMapper tagMapper;

    @Autowired
    private ArticleReviewLogMapper articleReviewLogMapper;

    @Autowired
    private ChapterMapper chapterMapper;

    @Autowired
    private FileObjectMapper fileObjectMapper;

    @Autowired
    private ArticleConfigReader articleConfigReader;

    @Autowired
    private ViewHistoryService viewHistoryService;

    @Autowired
    private NotifySupport notifySupport;

    @Autowired
    private ReviewNotifyService reviewNotifyService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @org.springframework.beans.factory.annotation.Value("${redis.base-key}")
    private String baseKey;

    /** 待审核存在标记 key（经 baseKey 前缀）：作者提交进 PENDING_REVIEW 时 SET，对账任务消费后 DEL */
    private static final String CACHE_PENDING_FLAG = "article:review:pending-flag";

    @Override
    public PageInfo<ArticleVo> quarryArticle(ArticleQuarry quarry) {
        // 一次扫描 perms 取查看等级（admin 自然 3，无权限者 0）
        ArticlePermissionLevel lvl = ArticlePermissionResolver.resolve();
        UserInfo user = currentUser();
        quarry.setUserViewLevel(lvl.view());
        quarry.setUserId(user.getUserId());
        PageUtil.startPage();
        List<Article> list = articleMapper.quarryArticle(quarry);
        PageInfo<Article> page = PageUtil.packagedPageInfo(list);
        PageInfo<ArticleVo> voPage = PageUtil.copyPageInfo(page, ArticleVo.class);
        // 「我的作品」列表（/authoring/article/my，controller 注入 authorId=me）回填 myRole：作者=AUTHOR，被批准贡献者=CONTRIBUTOR。
        // 驱动前端行上「贡献者」标识（作者默认不显）。非 myList 场景（admin 后台不注入 authorId）不回填。
        if (quarry.getAuthorId() != null && user.getUserId().equals(quarry.getAuthorId())
                && voPage.getList() != null) {
            for (ArticleVo vo : voPage.getList()) {
                if (vo.getAuthorId() != null && vo.getAuthorId().equals(user.getUserId())) {
                    vo.setMyRole("AUTHOR");
                } else {
                    Boolean approved = articleContributorMapper.isApproved(vo.getArticleId(), user.getUserId());
                    if (Boolean.TRUE.equals(approved)) {
                        vo.setMyRole("CONTRIBUTOR");
                    }
                }
            }
        }
        return voPage;
    }

    @Override
    public ArticleVo getArticleInfo(Long articleId) {
        Article article = articleMapper.getArticleInfoById(articleId);
        if (article == null) {
            throw new ServiceException(500, "文章不存在");
        }
        // 二次权限校验：canOp(view)（作者能看自己的文章，不看等级）
        if (!canOp(article, "view")) {
            throw new ServiceException(500, "无权查看该文章");
        }
        ArticleVo vo = BeanUtil.toBean(article, ArticleVo.class);
        // 回填标签（照博客 getBlogInfo tag 回填范式：tagIds + tagNames）
        List<Long> tagIds = articleTagMapper.getTagIdsByArticleId(articleId);
        vo.setTagIds(tagIds);
        if (tagIds != null && !tagIds.isEmpty()) {
            List<Tag> tags = tagMapper.getEnabledTagsByIds(tagIds);
            List<String> names = tags.stream().map(Tag::getTagName).collect(Collectors.toList());
            vo.setTagNames(names);
        }
        // 回填当前用户对该文章的权限态（供前端控制编辑/发布按钮显隐）
        fillPermissionState(vo, article);
        // 记录浏览（登录态，防刷去重，主表 view_count 仅首次 +1）
        viewHistoryService.recordView(currentUser().getUserId(), ViewBizType.ARTICLE.getCode(), articleId);
        return vo;
    }

    @Override
    @Transactional
    public Boolean addArticleInfo(ArticleVo vo) {
        validateArticlePayload(vo);
        validateArticleTagIds(vo.getTagIds());
        Article article = BeanUtil.toBean(vo, Article.class);
        UserInfo userInfo = currentUser();
        article.setAuthorId(userInfo.getUserId());
        article.setCreateBy(userInfo.getUsername());
        article.setUpdateBy(userInfo.getUsername());
        article.setCreateTime(new Date());
        article.setUpdateTime(new Date());
        // 新建即草稿；审核相关字段初始化；level 缺省 L1 公开；visibility 缺省 PRIVATE 未公开
        article.setStatus(ArticleStatus.DRAFT.getCode());
        article.setReviewStatus(ReviewStatus.NONE.getCode());
        if (article.getLevel() == null) {
            article.setLevel(ArticleLevel.L1.getCode());
        }
        if (article.getVisibility() == null || article.getVisibility().isEmpty()) {
            article.setVisibility(ArticleVisibility.PRIVATE.getCode());
        }
        try {
            articleMapper.addArticle(article);
        } catch (Exception e) {
            throw new ServiceException(500, "文章添加失败", e.getMessage());
        }
        // 新建文章标签（仅插，无需先删）
        saveArticleTags(article.getArticleId(), vo.getTagIds());
        return true;
    }

    @Override
    @Transactional
    public Boolean editArticleInfo(ArticleVo vo) {
        if (vo.getArticleId() == null) {
            throw new ServiceException(500, "文章ID不能为空");
        }
        Article exist = articleMapper.getArticleInfoById(vo.getArticleId());
        if (exist == null) {
            throw new ServiceException(500, "文章不存在");
        }
        // 状态机前置校验：仅 DRAFT/REJECTED/REVOKED 可编辑（PUBLISHED 须先撤回，PENDING_REVIEW 审核中不能改）
        String cur = exist.getStatus();
        if (ArticleStatus.PUBLISHED.getCode().equals(cur)) {
            throw new ServiceException(500, "已发布文章请先撤回再编辑");
        }
        if (ArticleStatus.PENDING_REVIEW.getCode().equals(cur)) {
            throw new ServiceException(500, "审核中文章不能编辑，如需修改请先驳回或撤回后操作");
        }
        // 权限校验：canOp(edit)（作者能编辑自己的文章，不看等级）
        if (!canOp(exist, "edit")) {
            throw new ServiceException(500, "无权编辑该文章");
        }
        validateArticlePayload(vo);
        validateArticleTagIds(vo.getTagIds());

        Article article = BeanUtil.toBean(vo, Article.class);
        UserInfo userInfo = currentUser();
        article.setUpdateBy(userInfo.getUsername());
        // 改 level 要校验：操作者自己的 edit 等级 >= 新 level（否则能把自己够不着的文章降级再让别人改）
        if (article.getLevel() != null && article.getLevel() > exist.getLevel()) {
            ArticlePermissionLevel lvl = ArticlePermissionResolver.resolve();
            // 作者改自己文章的 level 也要校验（防止作者把 PRIVATE 文章提到 L3 机密后自己又不想担）
            if (lvl.edit() < article.getLevel() && !isAuthor(exist, userInfo)) {
                throw new ServiceException(500, "无权提升文章到更高等级（自身编辑等级不足）");
            }
        }
        try {
            articleMapper.editArticleInfo(article);
        } catch (Exception e) {
            throw new ServiceException(500, "文章修改失败", e.getMessage());
        }
        // 标签先删后插（照博客 editBlogInfo 范式）
        articleTagMapper.deleteArticleTagByArticleId(vo.getArticleId());
        saveArticleTags(vo.getArticleId(), vo.getTagIds());
        return true;
    }

    @Override
    @Transactional
    public Boolean deleteArticleInfo(Long[] articleIds) {
        UserInfo userInfo = currentUser();
        for (Long id : articleIds) {
            Article article = articleMapper.getArticleInfoById(id);
            if (article == null) {
                continue;
            }
            // 删除权限：作者 或 knowhub:article:delete 按钮权限
            if (!isAuthor(article, userInfo) && !hasButtonPerm("knowhub:article:delete")) {
                throw new ServiceException(500, "无权删除该文章（仅作者或拥有删除权限）");
            }
            // 级联软删章节（章节正文随主表软删，对象无 file_object，正文在 DB）
            try {
                chapterMapper.softDeleteByArticleId(id);
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(ArticleServiceImpl.class)
                        .warn("级联软删章节失败 articleId={}: {}", id, e.getMessage());
            }
            // 级联软删封面 file_object（ARTICLE_COVER，biz_ref_id=article_id），对象本体由 FileGcTask 回收
            try {
                fileObjectMapper.softDeleteByBizRef(FileBusinessType.ARTICLE_COVER.getCode(), id, userInfo.getUsername());
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(ArticleServiceImpl.class)
                        .warn("级联软删封面失败 articleId={}: {}", id, e.getMessage());
            }
            articleMapper.softDeleteArticle(id);
        }
        return true;
    }

    @Override
    @Transactional
    public Boolean publishArticle(Long articleId) {
        Article exist = articleMapper.getArticleInfoById(articleId);
        if (exist == null) {
            throw new ServiceException(500, "文章不存在");
        }
        // 状态机前置校验：仅 DRAFT/REJECTED/REVOKED 可发布
        String cur = exist.getStatus();
        if (ArticleStatus.PUBLISHED.getCode().equals(cur)) {
            throw new ServiceException(500, "文章已发布，无需重复发布");
        }
        if (ArticleStatus.PENDING_REVIEW.getCode().equals(cur)) {
            throw new ServiceException(500, "文章审核中，请勿重复提交");
        }
        // 权限校验：canOp(edit)（发布属编辑范畴；作者能发布自己的文章）
        if (!canOp(exist, "edit")) {
            throw new ServiceException(500, "无权发布该文章");
        }
        UserInfo userInfo = currentUser();
        Date now = new Date();
        boolean reviewEnabled = articleConfigReader.isReviewEnabled();
        Article update = new Article();
        update.setArticleId(articleId);
        update.setUpdateBy(userInfo.getUsername());
        ReviewAction action;
        if (reviewEnabled) {
            update.setStatus(ArticleStatus.PENDING_REVIEW.getCode());
            update.setReviewStatus(ReviewStatus.PENDING.getCode());
            action = ReviewAction.SUBMIT;
            redisTemplate.opsForValue().set(baseKey + CACHE_PENDING_FLAG, "1");
            // 提审通知：按系统设置 knowhub.review.notify_role_key 通知持该角色的有效用户（总开关缺省关）
            reviewNotifyService.notifyReviewers("article", articleId, exist.getTitle(),
                    userInfo.getUsername(), userInfo.getUsername());
        } else {
            update.setStatus(ArticleStatus.PUBLISHED.getCode());
            update.setPublishTime(now);
            update.setReviewStatus(ReviewStatus.NONE.getCode());
            action = ReviewAction.PUBLISH;
        }
        articleMapper.editArticleInfo(update);
        writeReviewLog(articleId, action, userInfo, null);
        // 审核结果通知作者（SUBMIT 不通知：作者是提交人；PUBLISH 直通发"已发布"通知）
        notifyReviewResult(exist, action, null);
        return true;
    }

    @Override
    @Transactional
    public Boolean revokeArticle(Long articleId) {
        Article exist = articleMapper.getArticleInfoById(articleId);
        if (exist == null) {
            throw new ServiceException(500, "文章不存在");
        }
        if (!ArticleStatus.PUBLISHED.getCode().equals(exist.getStatus())) {
            throw new ServiceException(500, "仅已发布文章可撤回");
        }
        if (!canOp(exist, "edit")) {
            throw new ServiceException(500, "无权撤回该文章");
        }
        UserInfo userInfo = currentUser();
        Article update = new Article();
        update.setArticleId(articleId);
        update.setStatus(ArticleStatus.REVOKED.getCode());
        update.setReviewStatus(ReviewStatus.NONE.getCode());
        update.setUpdateBy(userInfo.getUsername());
        articleMapper.editArticleInfo(update);
        writeReviewLog(articleId, ReviewAction.REVOKE, userInfo, null);
        return true;
    }

    @Override
    @Transactional
    public Boolean reviewArticle(ArticleReviewVo vo) {
        if (vo.getArticleId() == null || vo.getPass() == null) {
            throw new ServiceException(500, "审核参数不完整");
        }
        Article exist = articleMapper.getArticleInfoById(vo.getArticleId());
        if (exist == null) {
            throw new ServiceException(500, "文章不存在");
        }
        if (!ArticleStatus.PENDING_REVIEW.getCode().equals(exist.getStatus())) {
            throw new ServiceException(500, "仅待审核文章可审核");
        }
        UserInfo userInfo = currentUser();
        // 审核员回避：作者不能审自己文章（用 author_id 比对）
        if (exist.getAuthorId() != null && exist.getAuthorId().equals(userInfo.getUserId())) {
            throw new ServiceException(500, "不能审核自己提交的文章");
        }
        Date now = new Date();
        Article update = new Article();
        update.setArticleId(vo.getArticleId());
        update.setUpdateBy(userInfo.getUsername());
        ReviewAction action;
        String advice = null;
        if (vo.getPass()) {
            update.setStatus(ArticleStatus.PUBLISHED.getCode());
            update.setPublishTime(now);
            update.setReviewStatus(ReviewStatus.APPROVED.getCode());
            action = ReviewAction.APPROVE;
            advice = vo.getAdvice();
        } else {
            if (vo.getAdvice() == null || vo.getAdvice().isEmpty()) {
                throw new ServiceException(500, "驳回需填写审核意见");
            }
            update.setStatus(ArticleStatus.REJECTED.getCode());
            update.setReviewStatus(ReviewStatus.REJECTED.getCode());
            action = ReviewAction.REJECT;
            advice = vo.getAdvice();
        }
        articleMapper.editArticleInfo(update);
        writeReviewLog(vo.getArticleId(), action, userInfo, advice);
        // 审核结果通知作者（APPROVE/REJECT；avoid 已保障 author_id==userId 走不到这里）
        notifyReviewResult(exist, action, advice);
        return true;
    }

    @Override
    public List<ArticleReviewLogVo> listReviewLog(Long articleId) {
        List<ArticleReviewLog> logs = articleReviewLogMapper.listByArticleId(articleId);
        if (logs == null || logs.isEmpty()) {
            return new ArrayList<>();
        }
        return logs.stream()
                .map(log -> BeanUtil.toBean(log, ArticleReviewLogVo.class))
                .collect(Collectors.toList());
    }

    @Override
    public int reconcilePendingReview() {
        List<Long> ids = articleMapper.listPendingReviewIds();
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        Date now = new Date();
        int released = 0;
        for (Long articleId : ids) {
            try {
                Article update = new Article();
                update.setArticleId(articleId);
                update.setStatus(ArticleStatus.PUBLISHED.getCode());
                update.setPublishTime(now);
                update.setReviewStatus(ReviewStatus.APPROVED.getCode());
                update.setUpdateBy("system");
                articleMapper.editArticleInfo(update);
                writeReviewLog(articleId, ReviewAction.PUBLISH, systemOperator(), "审核关闭后定时任务自动放行");
                released++;
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(ArticleServiceImpl.class)
                        .warn("对账放行失败 articleId={}: {}", articleId, e.getMessage());
            }
        }
        return released;
    }

    // ============================ 私有辅助 ============================

    /**
     * 审核结果通知作者（2026-08-15 落地，复用 NotifySupport 个人通道）。
     * 通知口径同博客：APPROVE/REJECT 发审核结果通知，PUBLISH（审核开关关直通）发"已发布"通知，
     * SUBMIT/REVOKE 不通知（作者主动行为）。reviewArticle 调用前 author_id==userId 回避已抛错，
     * 不会给作者自己发审核结果。失败由 NotifySupport 内部吞掉，不阻断审核状态已落库。
     */
    private void notifyReviewResult(Article article, ReviewAction action, String advice) {
        if (article == null || article.getAuthorId() == null) {
            return;
        }
        String title;
        String content;
        switch (action) {
            case APPROVE:
                title = "你的文章审核通过";
                content = "《" + article.getTitle() + "》审核通过，已发布。" + (advice != null && !advice.isEmpty() ? "审核意见：" + advice : "");
                break;
            case REJECT:
                title = "你的文章被驳回";
                content = "《" + article.getTitle() + "》被驳回，请修改后重新发布。" + (advice != null && !advice.isEmpty() ? "驳回原因：" + advice : "");
                break;
            case PUBLISH:
                title = "你的文章已发布";
                content = "《" + article.getTitle() + "》已直接发布（审核未开启）。";
                break;
            default:
                return;
        }
        notifySupport.notifyUser(article.getAuthorId(), title, content, "/article/" + article.getArticleId(), "system");
    }

    /**
     * 权限判定核心：用户对文章 A 是否有操作 op 权限。
     * 公式：userLvl(op) >= A.level OR author_id == userId（作者全权，不看等级/不看 visibility）。
     * admin 因 perms 含全 l3 自然 userLvl=3，对所有文章全权（系统权限分支）。
     */
    private boolean canOp(Article article, String op) {
        ArticlePermissionLevel lvl = ArticlePermissionResolver.resolve();
        // 系统权限等级够 → 直接通过
        if (article.getLevel() != null && lvl.levelOf(op) >= article.getLevel()) {
            return true;
        }
        // 作者归属：作者对自己的文章全权（类比项目 LEADER，但无成员表，直接 author_id 比对）
        return isAuthor(article, currentUser());
    }

    /** 当前用户是否该文章作者（author_id 比对，userId 稳定锁定） */
    private boolean isAuthor(Article article, UserInfo user) {
        return article.getAuthorId() != null && article.getAuthorId().equals(user.getUserId());
    }

    /** 判断当前用户是否拥有某按钮权限（非等级，如 knowhub:article:delete）。
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

    /** 详情接口回填当前用户对该文章的权限态（供前端控制编辑/发布按钮显隐） */
    private void fillPermissionState(ArticleVo vo, Article article) {
        ArticlePermissionLevel lvl = ArticlePermissionResolver.resolve();
        UserInfo user = currentUser();
        boolean author = isAuthor(article, user);
        vo.setIsAuthor(author);
        boolean systemEdit = article.getLevel() != null && lvl.edit() >= article.getLevel();
        // canView/canEdit：系统权限够 OR 作者全权（canEdit 不含贡献者分支，此处是文章元信息编辑权限，与章节创建区分）
        vo.setCanView(author || (article.getLevel() != null && lvl.view() >= article.getLevel()));
        vo.setCanEdit(author || systemEdit);
        // canManageChapters：能否像作者那样自由管理章节（排序/改文章元信息/发布/撤回/删除）。
        // = 作者 OR 系统编辑级，不含被作者批准的贡献者。贡献者走 canEditArticle 第三分支可提交新章节/编辑自己章节，
        // 但不可改章节顺序、不可发布/撤回/删除文章、不可删任意章节。chapters.vue 拖拽手柄/发布撤回按钮据此显隐。
        vo.setCanManageChapters(author || systemEdit);
    }

    /** 校验文章载体字段：title 必填、level 必填在 1-3、visibility 必填合法 */
    private void validateArticlePayload(ArticleVo vo) {
        if (vo.getTitle() == null || vo.getTitle().isEmpty()) {
            throw new ServiceException(500, "文章标题不能为空");
        }
        if (vo.getLevel() == null || vo.getLevel() < 1 || vo.getLevel() > 3) {
            throw new ServiceException(500, "文章等级需为 1-3");
        }
        if (vo.getVisibility() == null || vo.getVisibility().isEmpty()) {
            throw new ServiceException(500, "文章可见性不能为空");
        }
        // visibility 合法性校验
        boolean validVisibility = false;
        for (ArticleVisibility v : ArticleVisibility.values()) {
            if (v.getCode().equals(vo.getVisibility())) {
                validVisibility = true;
                break;
            }
        }
        if (!validVisibility) {
            throw new ServiceException(500, "文章可见性非法");
        }
    }

    /** 标签合法性校验（照博客 validateTagIds：比对启用标签数量一致，防非法/已禁用标签） */
    private void validateArticleTagIds(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return; // 标签非必填
        }
        List<Tag> enabled = tagMapper.getEnabledTagsByIds(tagIds);
        if (enabled.size() != tagIds.size()) {
            throw new ServiceException(500, "存在非法或已禁用的标签");
        }
    }

    /** 保存文章-标签关联（仅插，删除由调用方 deleteArticleTagByArticleId 完成，先删后插范式） */
    private void saveArticleTags(Long articleId, List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        List<ArticleTag> rels = new ArrayList<>();
        for (Long tagId : tagIds) {
            rels.add(new ArticleTag(articleId, tagId));
        }
        articleTagMapper.insertArticleTags(rels);
    }

    /**
     * 追加一条审核流水。role 由 ReviewAction 自带，operator_id 用 userId 稳定锁定，
     * operator 存 username 快照。流水表只追加不改不删，写失败不阻断主流程（catch 吞异常仅 log）。
     */
    private void writeReviewLog(Long articleId, ReviewAction action, UserInfo operator, String advice) {
        try {
            ArticleReviewLog log = new ArticleReviewLog(articleId, action.getCode(),
                    operator.getUserId(), operator.getUsername(), action.getRole(), advice);
            articleReviewLogMapper.insertReviewLog(log);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(ArticleServiceImpl.class)
                    .warn("写审核流水失败 articleId={} action={}: {}", articleId, action.getCode(), e.getMessage());
        }
    }

    /** 构造一个 system 操作者 UserInfo，用于对账放行时写流水（operator_id=0, operator=system） */
    private UserInfo systemOperator() {
        UserInfo sys = new UserInfo();
        sys.setUserId(0L);
        sys.setUsername("system");
        return sys;
    }

    private UserInfo currentUser() {
        return (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
