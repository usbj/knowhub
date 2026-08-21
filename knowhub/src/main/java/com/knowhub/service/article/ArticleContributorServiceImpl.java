package com.knowhub.service.article;

import com.github.pagehelper.PageInfo;
import com.knowhub.enums.article.ArticleContributorStatus;
import com.knowhub.enums.article.ArticleVisibility;
import com.knowhub.mapper.article.ArticleContributorMapper;
import com.knowhub.mapper.article.ArticleMapper;
import com.knowhub.pojo.article.entity.Article;
import com.knowhub.pojo.article.entity.ArticleContributor;
import com.knowhub.pojo.article.quarry.ArticleContributorQuarry;
import com.knowhub.pojo.article.vo.ArticleContributorVo;
import com.knowhub.service.article.impl.ArticleContributorService;
import com.knowhub.support.ArticlePermissionResolver;
import com.knowhub.support.NotifySupport;
import com.rookie.common.exception.ServiceException;
import com.rookie.common.pojo.entity.SysUser;
import com.rookie.common.util.PageUtil;
import com.rookie.framework.security.pojo.UserInfo;
import com.rookie.system.mapper.SysUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * 文章贡献者申请/授权 Service 实现。
 * <p>
 * 申请（PENDING）→ 作者审批（accept=APPROVED / reject=REJECTED+advice）。APPROVED 行由 ChapterServiceImpl
 * .canEditArticle 第三分支识别，贡献者可提交章节（不要求系统编辑级权限，与作者并列放行）。
 * <p>
 * 去重：同 (articleId, userId) 同一时间只允许一条 ACTIVE 行——PENDING/APPROVED 时不可重复申请；REJECTED
 * 行走重申，软删旧行（避 uk_article_contributor 冲突）再插新 PENDING。
 * 防越权：accept/reject 校验操作人是该文章作者（article.author_id==当前 userId）。
 * 通知 best-effort：由 NotifySupport 内部 try/catch 吞失败不阻断主流程（对齐评论通知哲学）。
 */
@Service
public class ArticleContributorServiceImpl implements ArticleContributorService {

    @Autowired
    private ArticleContributorMapper articleContributorMapper;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private NotifySupport notifySupport;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Override
    @Transactional
    public Boolean apply(Long articleId) {
        if (articleId == null) {
            throw new ServiceException(500, "需指定申请的文章 articleId");
        }
        Article article = articleMapper.getArticleInfoById(articleId);
        if (article == null) {
            throw new ServiceException(500, "文章不存在");
        }
        // 未公开（PRIVATE）文章不开放贡献申请——贡献者无章节可写（submitChapter 对非作者 PRIVATE 拒），申请无意义
        if (ArticleVisibility.PRIVATE.getCode().equals(article.getVisibility())) {
            throw new ServiceException(500, "未公开文章不开放贡献申请");
        }
        Long userId = currentUser().getUserId();
        String username = currentUser().getUsername();
        // 文章作者无需申请自己是贡献者（作者天然能写自己文章的章节）
        if (article.getAuthorId() != null && article.getAuthorId().equals(userId)) {
            throw new ServiceException(500, "文章作者无需申请贡献者");
        }
        // 申请人查看等级校验（2026-08-18 权限大修，决策#7）：申请人 view 等级 >= 文章 level 才能申请，
        // 比文章自身等级（非作者等级）——防止低权用户向高等级文章申请贡献者越权写章节。
        // admin 走 resolver 自然得 3 全过；未授权者得 0 只能申请 L1 文章。
        int userLevel = ArticlePermissionResolver.resolve().level();
        if (article.getLevel() != null && userLevel < article.getLevel()) {
            throw new ServiceException(500, "无权申请该等级文章的贡献者（自身查看等级不足）");
        }
        // 去重：查当前 ACTIVE 行
        ArticleContributor exist = articleContributorMapper.getActiveByArticleAndUser(articleId, userId);
        if (exist != null) {
            String st = exist.getStatus();
            if (ArticleContributorStatus.PENDING.getCode().equals(st)) {
                throw new ServiceException(500, "已申请过该文章贡献资格，待作者审核");
            }
            if (ArticleContributorStatus.APPROVED.getCode().equals(st)) {
                throw new ServiceException(500, "已是该文章贡献者，无需重复申请");
            }
            // REJECTED 允许重申：软删旧行（避 uk 唯一索引冲突）后插新 PENDING
            articleContributorMapper.softDeleteById(exist.getContributorId());
        }
        ArticleContributor c = new ArticleContributor();
        c.setArticleId(articleId);
        c.setUserId(userId);
        c.setApplyBy(username);
        c.setCreateBy(username);
        c.setUpdateBy(username);
        articleContributorMapper.addContributor(c);
        // 通知文章作者：有人申请成为贡献者，routePath 指向个人中心「我的协作」tab
        if (article.getAuthorId() != null) {
            String applicantNick = nicknameOf(userId);
            String who = applicantNick != null ? applicantNick : username;
            String title = "有新的文章贡献申请";
            String content = "用户「" + who + "」申请成为你的文章《" + article.getTitle() + "》的贡献者，请前往「我的协作」处理。";
            notifySupport.notifyUser(article.getAuthorId(), title, content,
                    "/profile?tab=collaboration", "system");
        }
        return true;
    }

    @Override
    public String myStatus(Long articleId) {
        if (articleId == null) {
            return null;
        }
        Article article = articleMapper.getArticleInfoById(articleId);
        if (article == null) {
            return null;
        }
        Long userId = currentUser().getUserId();
        // 作者不需申请态（自己文章天然可写章节）
        if (article.getAuthorId() != null && article.getAuthorId().equals(userId)) {
            return null;
        }
        ArticleContributor exist = articleContributorMapper.getActiveByArticleAndUser(articleId, userId);
        return exist == null ? null : exist.getStatus();
    }

    @Override
    public PageInfo<ArticleContributorVo> listReceived(String status) {
        ArticleContributorQuarry quarry = new ArticleContributorQuarry();
        quarry.setArticleAuthorId(currentUser().getUserId());
        quarry.setStatus(status);
        PageUtil.startPage();
        List<ArticleContributor> list = articleContributorMapper.listReceived(quarry);
        return toVoPage(list);
    }

    @Override
    public PageInfo<ArticleContributorVo> listMine(String status) {
        ArticleContributorQuarry quarry = new ArticleContributorQuarry();
        quarry.setUserId(currentUser().getUserId());
        quarry.setStatus(status);
        PageUtil.startPage();
        List<ArticleContributor> list = articleContributorMapper.listMine(quarry);
        return toVoPage(list);
    }

    @Override
    @Transactional
    public Boolean accept(Long applicantId) {
        ArticleContributor exist = loadAndAuthorize(applicantId);
        if (!ArticleContributorStatus.PENDING.getCode().equals(exist.getStatus())) {
            throw new ServiceException(500, "仅待审核申请可同意");
        }
        UserInfo me = currentUser();
        Date now = new Date();
        exist.setStatus(ArticleContributorStatus.APPROVED.getCode());
        exist.setHandleBy(me.getUsername());
        exist.setHandleTime(now);
        exist.setUpdateBy(me.getUsername());
        // advice 仅 REJECTED 填，APPROVED 不清旧 advice（重申场景旧 REJECTED 行已软删，新行 advice 本就 null）
        articleContributorMapper.updateStatus(exist);
        // 通知申请人：申请通过，routePath 指向文章详情即可去贡献章节
        notifyResult(exist, true, null);
        return true;
    }

    @Override
    @Transactional
    public Boolean reject(Long applicantId, String advice) {
        if (advice == null || advice.isEmpty()) {
            throw new ServiceException(500, "驳回需填写原因");
        }
        ArticleContributor exist = loadAndAuthorize(applicantId);
        if (!ArticleContributorStatus.PENDING.getCode().equals(exist.getStatus())) {
            throw new ServiceException(500, "仅待审核申请可驳回");
        }
        UserInfo me = currentUser();
        Date now = new Date();
        exist.setStatus(ArticleContributorStatus.REJECTED.getCode());
        exist.setAdvice(advice);
        exist.setHandleBy(me.getUsername());
        exist.setHandleTime(now);
        exist.setUpdateBy(me.getUsername());
        articleContributorMapper.updateStatus(exist);
        notifyResult(exist, false, advice);
        return true;
    }

    // ============================ 私有辅助 ============================

    /** 载入申请并校验操作人是该文章作者（防越权审批别人文章的申请） */
    private ArticleContributor loadAndAuthorize(Long applicantId) {
        if (applicantId == null) {
            throw new ServiceException(500, "需指定申请记录 id");
        }
        ArticleContributor exist = articleContributorMapper.getById(applicantId);
        if (exist == null || (exist.getDeleted() != null && exist.getDeleted() == 1)) {
            throw new ServiceException(500, "申请记录不存在");
        }
        Article article = articleMapper.getArticleInfoById(exist.getArticleId());
        if (article == null) {
            throw new ServiceException(500, "文章不存在");
        }
        Long me = currentUser().getUserId();
        if (article.getAuthorId() == null || !article.getAuthorId().equals(me)) {
            throw new ServiceException(500, "仅文章作者可处理该申请");
        }
        // 补回填文章标题：getById 不 join article，entity 上 articleTitle 恒 null；notifyResult 拼《title》会显示《null》。
        // 此处已查得 article，把 title 灌回 exist，accept/reject 调本方法后通知文案即正确。
        exist.setArticleTitle(article.getTitle());
        return exist;
    }

    /** 通知申请人审批结果（通过跳文章去贡献章节；驳回跳个人中心「我的协作」tab 看我的申请列表） */
    private void notifyResult(ArticleContributor c, boolean pass, String advice) {
        if (c.getUserId() == null) {
            return;
        }
        String title = pass ? "你的文章贡献申请已通过" : "你的文章贡献申请未通过";
        String content = pass
                ? "你已成为文章《" + c.getArticleTitle() + "》的贡献者，可前往该文章提交章节。"
                : "你申请文章《" + c.getArticleTitle() + "》的贡献者未通过。原因：" + advice;
        notifySupport.notifyUser(c.getUserId(), title, content,
                pass ? "/article/" + c.getArticleId() : "/profile?tab=collaboration", "system");
    }

    /** entity 列表 → VO 分页（join 带出的 articleTitle/nickname/articleNickname 经 BeanUtil 同名拷贝带出） */
    private PageInfo<ArticleContributorVo> toVoPage(List<ArticleContributor> list) {
        PageInfo<ArticleContributor> page = PageUtil.packagedPageInfo(list);
        return PageUtil.copyPageInfo(page, ArticleContributorVo.class);
    }

    /** 当前登录用户 UserInfo（/authoring/** 已 authenticated 兜底） */
    private UserInfo currentUser() {
        return ((UserInfo)
                SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    /** 按 userId 取昵称快照（通知文案用；用户不存在返 null） */
    private String nicknameOf(Long userId) {
        if (userId == null) {
            return null;
        }
        SysUser u = sysUserMapper.getSysUserInfoById(userId);
        return u == null ? null : u.getNickName();
    }
}