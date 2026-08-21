package com.knowhub.support;

import com.knowhub.enums.comment.CommentBizType;
import com.knowhub.mapper.article.ArticleMapper;
import com.knowhub.mapper.blog.BlogMapper;
import com.knowhub.mapper.project.ProjectMapper;
import com.knowhub.mapper.resource.ResourceMapper;
import com.knowhub.pojo.article.entity.Article;
import com.knowhub.pojo.blog.entity.Blog;
import com.knowhub.pojo.comment.entity.Comment;
import com.knowhub.pojo.project.entity.Project;
import com.knowhub.pojo.resource.entity.Resource;
import com.rookie.system.mapper.SysUserMapper;
import com.rookie.common.pojo.entity.SysUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 评论作品解析器：按 bizType+bizId 解析作品元数据（作者/评论开关），供评论 service 创作闸与
 * 列表权限谓词前置解析用。集中路由避免 comment/{Blog,Article,Project,Resource}ServiceMixin
 * 跨模块依赖各 PortalService（评论是横切模块，不与各内容模块业务逻辑耦合）。
 * <p>
 * 解析口径与各后台 mapper getXxxInfoById 一致（按主键、含 deleted=0 过滤）。
 * <ul>
 *   <li>{@link WorkMeta#exists()}：作品存在且未删；不存在时 create 直接拒。</li>
 *   <li>{@link WorkMeta#authorId()}：作者 userId（gate「能看作品才能评论」作者分支 + 列表谓词 workAuthorId）。</li>
 *   <li>{@link WorkMeta#commentEnabled()}：1 开 / 0 关；关闭时 create 拒、列表返空。</li>
 *   <li>{@link WorkMeta#commentCurated()}：1 开精选 / 0 关；create 时定评论 review_status（0→NONE 直接可见，1→PENDING 待精）。</li>
 *   <li>{@link WorkMeta#level()}：作品等级 1/2/3；create 时越级判定（越级用户能看作品但不能发评论，作者本人放行）。</li>
 * </ul>
 * 失败空时该方法返回 null（仅 NONE 字段 workAuthor 也 null 形式或空 WorkMeta）——这里给 exists=false 的 WorkMeta。
 */
@Component
public class CommentWorkResolver {

    @Autowired
    BlogMapper blogMapper;

    @Autowired
    ArticleMapper articleMapper;

    @Autowired
    ProjectMapper projectMapper;

    @Autowired
    ResourceMapper resourceMapper;

    @Autowired
    SysUserMapper sysUserMapper;

    /** 按 bizType+bizId 解析作品元数据；不存在/已删返回 exists=false 的 WorkMeta（authorId=null）。 */
    public WorkMeta resolve(String bizType, Long bizId) {
        CommentBizType type = parseBizType(bizType);
        if (type == null || bizId == null) {
            return WorkMeta.absent();
        }
        switch (type) {
            case BLOG: {
                Blog b = blogMapper.getBlogInfoById(bizId);
                if (b == null || (b.getDeleted() != null && b.getDeleted() == 1)) {
                    return WorkMeta.absent();
                }
                return new WorkMeta(true, b.getAuthorId(), b.getCommentEnabled(), b.getCommentCurated(), b.getLevel());
            }
            case ARTICLE: {
                Article a = articleMapper.getArticleInfoById(bizId);
                if (a == null || (a.getDeleted() != null && a.getDeleted() == 1)) {
                    return WorkMeta.absent();
                }
                return new WorkMeta(true, a.getAuthorId(), a.getCommentEnabled(), a.getCommentCurated(), a.getLevel());
            }
            case PROJECT: {
                Project p = projectMapper.getProjectInfoById(bizId);
                if (p == null || (p.getDeleted() != null && p.getDeleted() == 1)) {
                    return WorkMeta.absent();
                }
                return new WorkMeta(true, p.getAuthorId(), p.getCommentEnabled(), p.getCommentCurated(), p.getLevel());
            }
            case RESOURCE: {
                Resource r = resourceMapper.getResourceInfoById(bizId);
                if (r == null || (r.getDeleted() != null && r.getDeleted() == 1)) {
                    return WorkMeta.absent();
                }
                return new WorkMeta(true, r.getAuthorId(), r.getCommentEnabled(), r.getCommentCurated(), r.getLevel());
            }
            default:
                return WorkMeta.absent();
        }
    }

    /** 按 userId 取昵称快照（@人昵称写 reply_to_nickname；用户不存在返 null）。 */
    public String nicknameOf(Long userId) {
        if (userId == null) {
            return null;
        }
        SysUser u = sysUserMapper.getSysUserInfoById(userId);
        return u == null ? null : u.getNickName();
    }

    /** 取评论对应作品的作者 userId（列表权限谓词前置解析用）；评论不存在返回 null。 */
    public Long workAuthorIdOf(Comment comment) {
        if (comment == null) {
            return null;
        }
        WorkMeta meta = resolve(comment.getBizType(), comment.getBizId());
        return meta.exists() ? meta.authorId() : null;
    }

    private CommentBizType parseBizType(String code) {
        if (code == null) {
            return null;
        }
        for (CommentBizType t : CommentBizType.values()) {
            if (t.getCode().equals(code)) {
                return t;
            }
        }
        return null;
    }

    /**
     * 作品元数据快照。exists=false 表示作品不存在或已删（仅 gate Unsupported 时用，避免 NULL 回包）。
     * level 为作品等级（1/2/3），供评论创作闸做越级判定（越级用户能看作品但不能发评论，作者本人放行）。
     */
    public record WorkMeta(boolean exists, Long authorId, Integer commentEnabled, Integer commentCurated, Integer level) {
        public static WorkMeta absent() {
            return new WorkMeta(false, null, null, null, null);
        }
    }
}