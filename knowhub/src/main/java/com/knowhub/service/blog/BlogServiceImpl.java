package com.knowhub.service.blog;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.github.pagehelper.PageInfo;
import com.knowhub.support.BlogPermissionResolver;
import com.knowhub.support.BlogPermissionResolver.BlogPermissionLevel;
import com.knowhub.config.BlogConfigReader;
import com.knowhub.enums.blog.BlogLevel;
import com.knowhub.enums.blog.BlogStatus;
import com.knowhub.enums.common.ReviewAction;
import com.knowhub.enums.common.ReviewStatus;
import com.knowhub.mapper.blog.BlogCollectMapper;
import com.knowhub.mapper.blog.BlogLikeMapper;
import com.knowhub.mapper.blog.BlogMapper;
import com.knowhub.mapper.blog.BlogReviewLogMapper;
import com.knowhub.mapper.blog.BlogTagMapper;
import com.knowhub.mapper.tag.TagMapper;
import com.knowhub.pojo.blog.entity.BlogReviewLog;
import com.knowhub.pojo.blog.quarry.BlogQuarry;
import com.knowhub.pojo.blog.vo.BlogVo;
import com.knowhub.pojo.common.vo.ReviewLogVo;
import com.knowhub.pojo.common.vo.ReviewVo;
import com.knowhub.service.blog.impl.BlogService;
import com.knowhub.service.history.impl.ViewHistoryService;
import com.knowhub.service.review.ReviewNotifyService;
import com.knowhub.enums.history.ViewBizType;
import com.knowhub.support.NotifySupport;
import com.rookie.common.exception.ServiceException;
import com.knowhub.pojo.blog.entity.Blog;
import com.knowhub.pojo.blog.entity.BlogCollect;
import com.knowhub.pojo.blog.entity.BlogLike;
import com.knowhub.pojo.blog.entity.BlogTag;
import com.knowhub.pojo.tag.entity.Tag;
import com.rookie.common.util.PageUtil;
import com.rookie.framework.security.pojo.Permission;
import com.rookie.framework.security.pojo.UserInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 博客文章 Service 实现。
 *
 * 权限模型（轻量，无成员表）：
 * - 查看（系统级分等级）：view:lN，BlogPermissionResolver 一次扫描 List<Permission> 取最高等级
 *   （admin 零特判，登录时全 perm_key 已塞入）。列表按 level<=userViewLevel OR author_id=userId 过滤，
 *   详情 canOp(view)=userLvl(view)>=level OR 作者（作者能看自己的博客，不看等级）。
 * - 编辑/发布/撤回（非等级，作者+admin）：2026-07-11 收紧，不再走 edit:lN 等级。
 *   canEditBlog=author_id==userId OR currentUser().isAdmin()。admin 走 rookie 框架短路
 *   （UserInfo.isAdmin + AdminBypassExpressionRoot）放行，不扫 edit 等级键。
 *   Controller @PreAuthorize('knowhub:blog:edit') 按钮权限保留作进接口门槛，service 层强判作者+admin 兜底。
 * - 删除：author 或 hasButtonPerm('knowhub:blog:delete')（admin 走框架短路全权）。
 *
 * 审核流程复用博客/项目范式（状态机+回避+流水表+对账任务）：照搬旧逻辑，仅权限门改上述语义。
 */
@Service
public class BlogServiceImpl implements BlogService {

    @Autowired
    BlogMapper blogMapper;

    @Autowired
    BlogTagMapper blogTagMapper;

    @Autowired
    BlogLikeMapper blogLikeMapper;

    @Autowired
    BlogCollectMapper blogCollectMapper;

    @Autowired
    TagMapper tagMapper;

    @Autowired
    BlogReviewLogMapper blogReviewLogMapper;

    @Autowired
    BlogConfigReader blogConfigReader;

    @Autowired
    ViewHistoryService viewHistoryService;

    @Autowired
    NotifySupport notifySupport;

    @Autowired
    ReviewNotifyService reviewNotifyService;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Value("${redis.base-key}")
    private String baseKey;

    /** 博客详情缓存 key：blog:detail:{id}（经 baseKey 前缀） */
    private static final String CACHE_DETAIL_PREFIX = "blog:detail:";
    /** 待审核存在标记 key：blog:review:pending-flag（经 baseKey 前缀）。
     *  作者提交进 PENDING_REVIEW 时 SET（不计数仅标记存在性，无过期）；
     *  对账定时任务消费后 DEL。flag 假阳（稿已被审核员手动批但 flag 未清）仅导致定时任务多扫一次空表，可接受。 */
    private static final String CACHE_PENDING_FLAG = "blog:review:pending-flag";

    @Override
    public PageInfo<BlogVo> quarryBlog(BlogQuarry quarry) {
        // 一次扫描 perms 取查看等级（admin 自然 3，无权限者 0）；回填 userId 走"作者能看自己博客"分支
        BlogPermissionLevel lvl = BlogPermissionResolver.resolve();
        UserInfo user = currentUser();
        quarry.setUserViewLevel(lvl.view());
        quarry.setUserId(user.getUserId());
        PageUtil.startPage();
        List<Blog> list = blogMapper.quarryBlog(quarry);
        PageInfo<Blog> page = PageUtil.packagedPageInfo(list);
        PageInfo<BlogVo> voPage = PageUtil.copyPageInfo(page, BlogVo.class);
        fillTagNamesForList(voPage.getList());
        // 列表不回填权限态（详情接口才回填），对齐文章模块
        return voPage;
    }

    @Override
    public BlogVo getBlogInfo(Long blogId) {
        // 1. 缓存命中直接返回
        String key = baseKey + CACHE_DETAIL_PREFIX + blogId;
        String json = redisTemplate.opsForValue().get(key);
        if (json != null && !json.isEmpty()) {
            BlogVo cached = JSONUtil.toBean(json, BlogVo.class);
            // 缓存命中仍实时刷新点赞/收藏状态与浏览计数
            fillCurrentUserInteract(cached);
            // 权限态随当前登录用户变，不信任缓存里的 canView/canEdit/isAuthor，实时按当前用户重算
            fillPermissionState(cached, blogId);
            viewHistoryService.recordView(currentUser().getUserId(), ViewBizType.BLOG.getCode(), blogId);
            return cached;
        }

        // 2. 未命中查库
        Blog blog = blogMapper.getBlogInfoById(blogId);
        if (blog == null) {
            throw new ServiceException(500, "文章不存在");
        }
        // 二次权限校验：canOp(view)（作者能看自己的博客，不看等级；防止越权遍历 ID 看不可见博客）
        if (!canOp(blog, "view")) {
            throw new ServiceException(500, "无权查看该博客");
        }
        BlogVo vo = BeanUtil.toBean(blog, BlogVo.class);
        List<Long> tagIds = blogTagMapper.getTagIdsByBlogId(blogId);
        vo.setTagIds(tagIds);
        if (tagIds != null && !tagIds.isEmpty()) {
            List<Tag> tags = tagMapper.getEnabledTagsByIds(tagIds);
            List<String> names = tags.stream().map(Tag::getTagName).collect(Collectors.toList());
            vo.setTagNames(names);
        }
        fillCurrentUserInteract(vo);
        // 回填当前用户对该博客的权限态（供前端控制编辑/发布按钮显隐）
        fillPermissionState(vo, blog);
        // 3. 回写缓存（带默认过期）
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(vo));
        redisTemplate.expire(key, java.time.Duration.ofMinutes(30));
        viewHistoryService.recordView(currentUser().getUserId(), ViewBizType.BLOG.getCode(), blogId);
        return vo;
    }

    @Override
    @Transactional
    public Boolean addBlogInfo(BlogVo vo) {
        if (vo.getTitle() == null || vo.getTitle().isEmpty()
                || vo.getContent() == null || vo.getContent().isEmpty()) {
            throw new ServiceException(500, "标题与正文不能为空");
        }
        validateTagIds(vo.getTagIds());
        Blog blog = BeanUtil.toBean(vo, Blog.class);
        UserInfo userInfo = currentUser();
        blog.setAuthorId(userInfo.getUserId());
        blog.setCreateBy(userInfo.getUsername());
        blog.setUpdateBy(userInfo.getUsername());
        blog.setCreateTime(new Date());
        blog.setUpdateTime(new Date());
        // 新建即草稿；审核相关字段初始化；level 缺省 L1 公开
        blog.setStatus(BlogStatus.DRAFT.getCode());
        blog.setReviewStatus(ReviewStatus.NONE.getCode());
        blog.setViewCount(0L);
        blog.setLikeCount(0L);
        blog.setCollectCount(0L);
        if (blog.getLevel() == null) {
            blog.setLevel(BlogLevel.L1.getCode());
        }
        // 分级创作闸（决策#11）：能创作的内容等级上限 <= 自身 view 等级；非 L2 级成员不能建 L2 博客。
        // admin 走 resolver 自然得 3（登录时全 perm_key 已塞入）；未授权者得 0 只能建 L1。
        assertCanCreateLevel(BlogPermissionResolver.resolve().view(), blog.getLevel());
        try {
            blogMapper.addBlog(blog);
        } catch (Exception e) {
            throw new ServiceException(500, "文章添加失败", e.getMessage());
        }
        saveBlogTags(blog.getBlogId(), vo.getTagIds());
        return true;
    }

    @Override
    @Transactional
    public Boolean editBlogInfo(BlogVo vo) {
        if (vo.getBlogId() == null) {
            throw new ServiceException(500, "文章ID不能为空");
        }
        Blog exist = blogMapper.getBlogInfoById(vo.getBlogId());
        if (exist == null) {
            throw new ServiceException(500, "文章不存在");
        }
        // 状态机前置校验：仅 DRAFT/REJECTED/REVOKED 可编辑。
        // - PUBLISHED 禁止原地编辑：内容改了状态仍 PUBLISHED 等于绕过审核，须先撤回再编辑。
        // - PENDING_REVIEW 禁止编辑：审核员审的是提交时的快照，作者此时改动会污染审核依据，
        //   如需修改应先驳回(审核员)或等审核结果，不可在审核中改内容。
        String cur = exist.getStatus();
        if (BlogStatus.PUBLISHED.getCode().equals(cur)) {
            throw new ServiceException(500, "已发布文章请先撤回再编辑");
        }
        if (BlogStatus.PENDING_REVIEW.getCode().equals(cur)) {
            throw new ServiceException(500, "审核中文章不能编辑，如需修改请先驳回或撤回后操作");
        }
        // 权限校验：canEditBlog（仅作者 OR 超级管理员，不看等级）
        if (!canEditBlog(exist)) {
            throw new ServiceException(500, "无权编辑该博客（仅作者或超级管理员）");
        }
        validateTagIds(vo.getTagIds());

        Blog blog = BeanUtil.toBean(vo, Blog.class);
        UserInfo userInfo = currentUser();
        blog.setUpdateBy(userInfo.getUsername());
        // 编辑不再分等级：作者改自己博客全权（含改 level L1→L3），admin 全权；
        // 非作者非 admin 在 canEditBlog 已被拒，无需 level 升级校验（旧 edit:lN 等级门已废）。
        try {
            blogMapper.editBlogInfo(blog);
        } catch (Exception e) {
            throw new ServiceException(500, "文章修改失败", e.getMessage());
        }
        // 标签先删后插
        blogTagMapper.deleteBlogTagByBlogId(vo.getBlogId());
        saveBlogTags(vo.getBlogId(), vo.getTagIds());
        evictDetail(vo.getBlogId());
        return true;
    }

    @Override
    @Transactional
    public Boolean deleteBlogInfo(Long[] blogIds) {
        UserInfo userInfo = currentUser();
        try {
            for (Long id : blogIds) {
                Blog exist = blogMapper.getBlogInfoById(id);
                if (exist == null) {
                    continue;
                }
                // 删除权限：作者 或 knowhub:blog:delete 按钮权限
                if (!isAuthor(exist, userInfo) && !hasButtonPerm("knowhub:blog:delete")) {
                    throw new ServiceException(500, "无权删除该博客（仅作者或拥有删除权限）");
                }
                blogTagMapper.deleteBlogTagByBlogId(id);
                blogMapper.softDeleteBlog(id);
                evictDetail(id);
            }
        } catch (ServiceException se) {
            throw se;
        } catch (Exception e) {
            throw new ServiceException(500, "文章删除失败", e.getMessage());
        }
        return true;
    }

    @Override
    @Transactional
    public Boolean publishBlog(Long blogId) {
        Blog exist = blogMapper.getBlogInfoById(blogId);
        if (exist == null) {
            throw new ServiceException(500, "文章不存在");
        }
        // 状态机前置校验：仅 DRAFT/REJECTED/REVOKED 可发布。
        // PUBLISHED 无需重复发布；PENDING_REVIEW 已在审不可重复提交。
        String cur = exist.getStatus();
        if (BlogStatus.PUBLISHED.getCode().equals(cur)) {
            throw new ServiceException(500, "文章已发布，无需重复发布");
        }
        if (BlogStatus.PENDING_REVIEW.getCode().equals(cur)) {
            throw new ServiceException(500, "文章审核中，请勿重复提交");
        }
        // 权限校验：canEditBlog（发布属编辑范畴；仅作者 OR 超级管理员）
        if (!canEditBlog(exist)) {
            throw new ServiceException(500, "无权发布该博客（仅作者或超级管理员）");
        }
        UserInfo userInfo = currentUser();
        Date now = new Date();
        // 经审核开关决定目标状态：开关关→直接发布；开→待审核
        boolean reviewEnabled = blogConfigReader.isReviewEnabled();
        Blog update = new Blog();
        update.setBlogId(blogId);
        update.setUpdateBy(userInfo.getUsername());
        ReviewAction action;
        if (reviewEnabled) {
            update.setStatus(BlogStatus.PENDING_REVIEW.getCode());
            update.setReviewStatus(ReviewStatus.PENDING.getCode());
            action = ReviewAction.SUBMIT;
            // 标记存在待审核文章，供对账定时任务快速判断是否需要扫表收口（不计数仅标记存在性）
            redisTemplate.opsForValue().set(baseKey + CACHE_PENDING_FLAG, "1");
            // 提审通知：按系统设置 knowhub.review.notify_role_key 通知持该角色的有效用户（总开关缺省关）
            reviewNotifyService.notifyReviewers("blog", blogId, exist.getTitle(),
                    userInfo.getUsername(), userInfo.getUsername());
        } else {
            update.setStatus(BlogStatus.PUBLISHED.getCode());
            update.setPublishTime(now);
            update.setReviewStatus(ReviewStatus.NONE.getCode());
            action = ReviewAction.PUBLISH;
        }
        blogMapper.editBlogInfo(update);
        // 写审核流水：作者提交(SUBMIT, AUTHOR) 或 系统直通(PUBLISH, SYSTEM)
        writeReviewLog(blogId, action, userInfo, null);
        // 审核结果通知作者
        notifyReviewResult(exist, action, null);
        evictDetail(blogId);
        return true;
    }

    @Override
    @Transactional
    public Boolean revokeBlog(Long blogId) {
        Blog exist = blogMapper.getBlogInfoById(blogId);
        if (exist == null) {
            throw new ServiceException(500, "文章不存在");
        }
        // 状态机前置校验：仅 PUBLISHED 可撤回。
        // 草稿/待审/驳回/已撤回 无撤回意义（待审应走驳回而非撤回）。
        if (!BlogStatus.PUBLISHED.getCode().equals(exist.getStatus())) {
            throw new ServiceException(500, "仅已发布文章可撤回");
        }
        // 权限校验：canEditBlog（撤回属编辑范畴；仅作者 OR 超级管理员）
        if (!canEditBlog(exist)) {
            throw new ServiceException(500, "无权撤回该博客（仅作者或超级管理员）");
        }
        UserInfo userInfo = currentUser();
        Blog update = new Blog();
        update.setBlogId(blogId);
        update.setStatus(BlogStatus.REVOKED.getCode());
        update.setReviewStatus(ReviewStatus.NONE.getCode());
        update.setUpdateBy(userInfo.getUsername());
        blogMapper.editBlogInfo(update);
        // 写审核流水：作者撤回(REVOKE, AUTHOR)
        writeReviewLog(blogId, ReviewAction.REVOKE, userInfo, null);
        evictDetail(blogId);
        return true;
    }

    @Override
    @Transactional
    public Boolean reviewBlog(ReviewVo vo) {
        if (vo.getBlogId() == null || vo.getPass() == null) {
            throw new ServiceException(500, "审核参数不完整");
        }
        Blog exist = blogMapper.getBlogInfoById(vo.getBlogId());
        if (exist == null) {
            throw new ServiceException(500, "文章不存在");
        }
        // 状态机前置校验：仅 PENDING_REVIEW 可审核，防止对草稿/已发布等误调审核接口改状态
        if (!BlogStatus.PENDING_REVIEW.getCode().equals(exist.getStatus())) {
            throw new ServiceException(500, "仅待审核文章可审核");
        }
        UserInfo userInfo = currentUser();
        // 审核员回避：作者不能审自己文章（用 author_id 比对，比 username 更准）
        if (exist.getAuthorId() != null && exist.getAuthorId().equals(userInfo.getUserId())) {
            throw new ServiceException(500, "不能审核自己提交的文章");
        }
        Date now = new Date();
        Blog update = new Blog();
        update.setBlogId(vo.getBlogId());
        update.setUpdateBy(userInfo.getUsername());
        update.setReviewer(userInfo.getUsername());
        update.setReviewTime(now);
        ReviewAction action;
        String advice = null;
        if (vo.getPass()) {
            update.setStatus(BlogStatus.PUBLISHED.getCode());
            update.setPublishTime(now);
            update.setReviewStatus(ReviewStatus.APPROVED.getCode());
            action = ReviewAction.APPROVE;
            advice = vo.getAdvice(); // 通过时意见可选
        } else {
            if (vo.getAdvice() == null || vo.getAdvice().isEmpty()) {
                throw new ServiceException(500, "驳回需填写审核意见");
            }
            update.setStatus(BlogStatus.REJECTED.getCode());
            update.setReviewStatus(ReviewStatus.REJECTED.getCode());
            update.setReviewAdvice(vo.getAdvice());
            action = ReviewAction.REJECT;
            advice = vo.getAdvice();
        }
        blogMapper.editBlogInfo(update);
        // 写审核流水：通过(APPROVE, REVIEWER) 或 驳回(REJECT, REVIEWER)
        writeReviewLog(vo.getBlogId(), action, userInfo, advice);
        // 审核结果通知作者（预留，待 rookie 支持个人通知后接入，签名零改动）
        notifyReviewResult(exist, action, advice);
        evictDetail(vo.getBlogId());
        return true;
    }

    @Override
    public List<ReviewLogVo> listReviewLog(Long blogId) {
        List<BlogReviewLog> logs = blogReviewLogMapper.listByBlogId(blogId);
        if (logs == null || logs.isEmpty()) {
            return new ArrayList<>();
        }
        // BeanUtil 拷贝（Date→String），operatorNickname 字段名一致自动带出
        return logs.stream()
                .map(log -> BeanUtil.toBean(log, ReviewLogVo.class))
                .collect(Collectors.toList());
    }

    @Override
    public int reconcilePendingReview() {
        List<Long> ids = blogMapper.listPendingReviewIds();
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        Date now = new Date();
        int released = 0;
        // 逐条放行，单条失败跳过不阻塞其它稿（对齐 writeReviewLog "状态优先、历史容错"哲学）。
        // 不加 @Transactional：批量放行一条坏数据不该回滚已放行的其它稿。
        for (Long blogId : ids) {
            try {
                Blog update = new Blog();
                update.setBlogId(blogId);
                update.setStatus(BlogStatus.PUBLISHED.getCode());
                update.setPublishTime(now);
                update.setReviewStatus(ReviewStatus.APPROVED.getCode());
                update.setReviewer("system");
                update.setReviewTime(now);
                update.setUpdateBy("system");
                blogMapper.editBlogInfo(update);
                // 写 PUBLISH/SYSTEM 流水，advice 标注场景以区分作者直通发布
                writeReviewLog(blogId, ReviewAction.PUBLISH, systemOperator(), "审核关闭后定时任务自动放行");
                evictDetail(blogId);
                released++;
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(BlogServiceImpl.class)
                        .warn("对账放行失败 blogId={}: {}", blogId, e.getMessage());
            }
        }
        return released;
    }

    /** 构造一个 system 操作者 UserInfo，用于对账放行时写流水（operator_id=0, operator=system）。 */
    private UserInfo systemOperator() {
        UserInfo sys = new UserInfo();
        sys.setUserId(0L);
        sys.setUsername("system");
        return sys;
    }

    @Override
    @Transactional
    public Boolean toggleLike(Long blogId, Boolean liked) {
        Blog exist = blogMapper.getBlogInfoById(blogId);
        if (exist == null) {
            throw new ServiceException(500, "文章不存在");
        }
        Long userId = currentUser().getUserId();
        BlogLike record = new BlogLike(blogId, userId);
        if (Boolean.TRUE.equals(liked)) {
            BlogLike old = blogLikeMapper.getBlogLike(record);
            blogLikeMapper.addBlogLike(record);
            if (old == null) {
                blogMapper.incrLikeCount(blogId, 1L);
            }
        } else {
            BlogLike old = blogLikeMapper.getBlogLike(record);
            blogLikeMapper.deleteBlogLike(record);
            if (old != null) {
                blogMapper.incrLikeCount(blogId, -1L);
            }
        }
        evictDetail(blogId);
        return true;
    }

    @Override
    @Transactional
    public Boolean toggleCollect(Long blogId, Boolean collected) {
        Blog exist = blogMapper.getBlogInfoById(blogId);
        if (exist == null) {
            throw new ServiceException(500, "文章不存在");
        }
        Long userId = currentUser().getUserId();
        BlogCollect record = new BlogCollect(blogId, userId);
        if (Boolean.TRUE.equals(collected)) {
            BlogCollect old = blogCollectMapper.getBlogCollect(record);
            blogCollectMapper.addBlogCollect(record);
            if (old == null) {
                blogMapper.incrCollectCount(blogId, 1L);
            }
        } else {
            BlogCollect old = blogCollectMapper.getBlogCollect(record);
            blogCollectMapper.deleteBlogCollect(record);
            if (old != null) {
                blogMapper.incrCollectCount(blogId, -1L);
            }
        }
        evictDetail(blogId);
        return true;
    }

    // ============================ 私有辅助 ============================

    private void validateTagIds(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return; // 标签非必填
        }
        List<Tag> enabled = tagMapper.getEnabledTagsByIds(tagIds);
        if (enabled.size() != tagIds.size()) {
            throw new ServiceException(500, "存在非法或已禁用的标签");
        }
    }

    /**
     * 分级创作闸（决策#11，前后台创作共用）：能创作的内容等级上限 <= 自身 view 等级。
     * 非 L2 级成员不能创建 L2 级博客（文章同理）。userViewLevel 由 BlogPermissionResolver.resolve().view() 给出
     * （admin 自然 3，未授权 0）。targetLevel<=1 恒放行（L1 公开人人可建）。
     */
    private void assertCanCreateLevel(int userViewLevel, Integer targetLevel) {
        if (targetLevel == null || targetLevel <= 1) {
            return;
        }
        if (userViewLevel < targetLevel) {
            throw new ServiceException(500, "无权创建 L" + targetLevel + " 级内容（自身查看等级不足）");
        }
    }

    private void saveBlogTags(Long blogId, List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        List<BlogTag> rels = new ArrayList<>();
        for (Long tagId : tagIds) {
            rels.add(new BlogTag(blogId, tagId));
        }
        blogTagMapper.insertBlogTags(rels);
    }

    /**
     * 查看权限判定：用户对博客 B 是否有 view 权限。
     * 公式：userLvl(view) >= B.level OR author_id == userId（作者全权，不看等级）。
     * admin 因 perms 含全 l3 自然 userLvl=3，对所有博客可查（系统权限分支）。
     * <p>
     * 仅用于 getBlogInfo 二次校验与详情 canView 回填；编辑/发布/撤回走 {@link #canEditBlog}。
     */
    private boolean canOp(Blog blog, String op) {
        BlogPermissionLevel lvl = BlogPermissionResolver.resolve();
        // 系统权限等级够 → 直接通过
        if (blog.getLevel() != null && lvl.levelOf(op) >= blog.getLevel()) {
            return true;
        }
        // 作者归属：作者对自己的博客全权（无成员表，直接 author_id 比对）
        return isAuthor(blog, currentUser());
    }

    /**
     * 编辑权限判定（2026-07-11 收紧，非等级）：仅作者本人 OR 超级管理员可编辑/发布/撤回。
     * 不看 level、不扫 edit:lN 等级键（已废）。admin 走 rookie 框架短路，currentUser().isAdmin() 直接放行。
     */
    private boolean canEditBlog(Blog blog) {
        UserInfo user = currentUser();
        return isAuthor(blog, user) || user.isAdmin();
    }

    /** 当前用户是否该博客作者（author_id 比对，userId 稳定锁定，username 可改不影响） */
    private boolean isAuthor(Blog blog, UserInfo user) {
        return blog.getAuthorId() != null && blog.getAuthorId().equals(user.getUserId());
    }

    /** 判断当前用户是否拥有某按钮权限（非等级，如 knowhub:blog:delete）。
     *  注意：UserInfo.getPermissions() 是 List<Permission>，需遍历比 permKey，不能用 contains(String)
     *  （旧 checkOwnerOrAdmin 用 contains(String) 对 List<Permission> 永远 false 的隐坑已修） */
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

    /** 详情接口回填当前用户对该博客的权限态（供前端控制编辑/发布按钮显隐）。
     *  缓存命中与未命中两路都调（不信任缓存里的权限态字段，按当前登录用户实时算）。
     *  缓存命中分支无 Blog 实体，按 blogId 重取一次轻量级实体取 level/author_id（详情已带，开销可忽略）。
     *  <p>
     *  canView：系统 view 等级够 OR 作者（查看仍走等级+作者）。
     *  canEdit：作者 OR 超级管理员（编辑不再分等级，2026-07-11 收紧）。 */
    private void fillPermissionState(BlogVo vo, Blog blog) {
        BlogPermissionLevel lvl = BlogPermissionResolver.resolve();
        UserInfo user = currentUser();
        boolean author = isAuthor(blog, user);
        vo.setIsAuthor(author);
        vo.setCanView(author || (blog.getLevel() != null && lvl.view() >= blog.getLevel()));
        vo.setCanEdit(author || user.isAdmin());
    }

    /** 缓存命中分支重载：仅有 BlogVo（无 Blog 实体），按 blogId 取 level/author_id 再算权限态。
     *  与 fillPermissionState(BlogVo,Blog) 共用判定逻辑，避免缓存命中时权限态串用户。 */
    private void fillPermissionState(BlogVo vo, Long blogId) {
        Blog blog = blogMapper.getBlogInfoById(blogId);
        if (blog == null) {
            return;
        }
        fillPermissionState(vo, blog);
    }

    /**
     * 追加一条审核流水。role 由 ReviewAction 自带（AUTHOR/REVIEWER/SYSTEM），
     * operator_id 用 userId 稳定锁定，operator 存 username 快照便于直读。
     * 流水表只追加不改不删，写失败不阻断主流程（catch 吞异常仅 log，保证审核状态变更已落库）。
     */
    private void writeReviewLog(Long blogId, ReviewAction action, UserInfo operator, String advice) {
        try {
            BlogReviewLog log = new BlogReviewLog(blogId, action.getCode(),
                    operator.getUserId(), operator.getUsername(), action.getRole(), advice);
            blogReviewLogMapper.insertReviewLog(log);
        } catch (Exception e) {
            // 流水写入失败不回滚审核状态变更（主表已改），仅记录日志便于事后补录
            // 如需强一致可改为抛异常让事务回滚，当前选择"状态优先、历史容错"
            org.slf4j.LoggerFactory.getLogger(BlogServiceImpl.class)
                    .warn("写审核流水失败 blogId={} action={}: {}", blogId, action.getCode(), e.getMessage());
        }
    }

    /**
     * 审核结果通知作者。2026-08-15 落地个人通知通道（NotifySupport）——反转旧策略"前后台展示代替通知"。
     *
     * 通知范围：
     * - APPROVE/REJECT 发审核结果通知（作者通过顶栏铃铛得知作品过了没过）；
     * - PUBLISH（审核开关关时直通发布）发"已发布"通知，告知作者作品已直接发布无需审核；
     * - SUBMIT（作者自己提交进 PENDING_REVIEW）不发——作者是动作发起人，已知晓提交结果；
     * - REVOKE 不发（撤回是作者主动行为，无需自通知自己）。
     * 回避保障：reviewBlog 在调用前已对 author_id==userId 抛"不能审核自己提交的文章"，
     *           因此通知分支不会给作者自己发审核结果通知；publish 的 PUBLISH 直通分支无回避顾虑。
     * 失败由 NotifySupport 内部 try/catch 吞掉，不阻断已落库的审核状态变更（与 writeReviewLog 同口径）。
     *
     * @param blog   被审文章（用其 title/authorId 拼通知内容、定位收件人）
     * @param action 本次动作（SUBMIT/APPROVE/REJECT/REVOKE/PUBLISH）
     * @param advice 审核意见（驳回必填，通过可选；PUBLISH 直通为 null）
     */
    private void notifyReviewResult(Blog blog, ReviewAction action, String advice) {
        if (blog == null || blog.getAuthorId() == null) {
            return;
        }
        String title;
        String content;
        switch (action) {
            case APPROVE:
                title = "你的博客审核通过";
                content = "《" + blog.getTitle() + "》审核通过，已发布。" + (advice != null && !advice.isEmpty() ? "审核意见：" + advice : "");
                break;
            case REJECT:
                title = "你的博客被驳回";
                content = "《" + blog.getTitle() + "》被驳回，请修改后重新发布。" + (advice != null && !advice.isEmpty() ? "驳回原因：" + advice : "");
                break;
            case PUBLISH:
                title = "你的博客已发布";
                content = "《" + blog.getTitle() + "》已直接发布（审核未开启）。";
                break;
            default:
                // SUBMIT / REVOKE 不通知：作者主动行为，已知晓提交/撤回，无需自提醒。
                return;
        }
        notifySupport.notifyUser(blog.getAuthorId(), title, content, "/blog/" + blog.getBlogId(), "system");
    }

    private void fillTagNamesForList(List<BlogVo> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        List<Long> ids = list.stream().map(BlogVo::getBlogId).collect(Collectors.toList());
        List<BlogTag> rels = blogTagMapper.getBlogTagsByBlogIds(ids);
        if (rels == null || rels.isEmpty()) {
            return;
        }
        Set<Long> allTagIds = rels.stream().map(BlogTag::getTagId).collect(Collectors.toSet());
        List<Tag> tags = tagMapper.getEnabledTagsByIds(new ArrayList<>(allTagIds));
        Map<Long, String> idToName = tags.stream()
                .collect(Collectors.toMap(Tag::getTagId, Tag::getTagName, (a, b) -> a));
        Map<Long, List<Long>> blogToTagIds = new HashMap<>();
        for (BlogTag rel : rels) {
            blogToTagIds.computeIfAbsent(rel.getBlogId(), k -> new ArrayList<>()).add(rel.getTagId());
        }
        for (BlogVo vo : list) {
            List<Long> tids = blogToTagIds.get(vo.getBlogId());
            if (tids != null) {
                vo.setTagIds(tids);
                vo.setTagNames(tids.stream().map(idToName::get).filter(java.util.Objects::nonNull)
                        .collect(Collectors.toList()));
            }
        }
    }

    /** 回填当前用户的点赞/收藏状态 */
    private void fillCurrentUserInteract(BlogVo vo) {
        if (vo == null || vo.getBlogId() == null) {
            return;
        }
        try {
            Long userId = currentUser().getUserId();
            vo.setHasLiked(blogLikeMapper.getBlogLike(new BlogLike(vo.getBlogId(), userId)) != null);
            vo.setHasCollected(blogCollectMapper.getBlogCollect(new BlogCollect(vo.getBlogId(), userId)) != null);
        } catch (Exception ignored) {
            // 未登录等场景不回填状态
        }
    }

    private void evictDetail(Long blogId) {
        redisTemplate.delete(baseKey + CACHE_DETAIL_PREFIX + blogId);
    }

    private UserInfo currentUser() {
        return (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}