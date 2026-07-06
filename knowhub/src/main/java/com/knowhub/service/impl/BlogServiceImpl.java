package com.knowhub.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.github.pagehelper.PageInfo;
import com.knowhub.config.BlogConfigReader;
import com.knowhub.enums.BlogStatus;
import com.knowhub.enums.ReviewAction;
import com.knowhub.enums.ReviewStatus;
import com.knowhub.mapper.BlogCollectMapper;
import com.knowhub.mapper.BlogLikeMapper;
import com.knowhub.mapper.BlogMapper;
import com.knowhub.mapper.BlogReviewLogMapper;
import com.knowhub.mapper.BlogTagMapper;
import com.knowhub.mapper.TagMapper;
import com.knowhub.pojo.entity.BlogReviewLog;
import com.knowhub.pojo.quarry.BlogQuarry;
import com.knowhub.pojo.vo.BlogVo;
import com.knowhub.pojo.vo.ReviewLogVo;
import com.knowhub.pojo.vo.ReviewVo;
import com.knowhub.service.BlogService;
import com.rookie.common.exception.ServiceException;
import com.knowhub.pojo.entity.Blog;
import com.knowhub.pojo.entity.BlogCollect;
import com.knowhub.pojo.entity.BlogLike;
import com.knowhub.pojo.entity.BlogTag;
import com.knowhub.pojo.entity.Tag;
import com.rookie.common.util.PageUtil;
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
    StringRedisTemplate redisTemplate;

    @Value("${redis.base-key}")
    private String baseKey;

    /** 博客详情缓存 key：blog:detail:{id}（经 baseKey 前缀） */
    private static final String CACHE_DETAIL_PREFIX = "blog:detail:";
    /** 浏览计数 key：blog:view:{id}（经 baseKey 前缀，原子 incr） */
    private static final String CACHE_VIEW_PREFIX = "blog:view:";
    /** 待审核存在标记 key：blog:review:pending-flag（经 baseKey 前缀）。
     *  作者提交进 PENDING_REVIEW 时 SET（不计数仅标记存在性，无过期）；
     *  对账定时任务消费后 DEL。flag 假阳（稿已被审核员手动批但 flag 未清）仅导致定时任务多扫一次空表，可接受。 */
    private static final String CACHE_PENDING_FLAG = "blog:review:pending-flag";

    @Override
    public PageInfo<BlogVo> quarryBlog(BlogQuarry quarry) {
        PageUtil.startPage();
        List<Blog> list = blogMapper.quarryBlog(quarry);
        PageInfo<Blog> page = PageUtil.packagedPageInfo(list);
        PageInfo<BlogVo> voPage = PageUtil.copyPageInfo(page, BlogVo.class);
        fillTagNamesForList(voPage.getList());
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
            incrView(blogId);
            return cached;
        }

        // 2. 未命中查库
        Blog blog = blogMapper.getBlogInfoById(blogId);
        if (blog == null) {
            throw new ServiceException(500, "文章不存在");
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
        // 3. 回写缓存（带默认过期）
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(vo));
        redisTemplate.expire(key, java.time.Duration.ofMinutes(30));
        incrView(blogId);
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
        // 新建即草稿；审核相关字段初始化
        blog.setStatus(BlogStatus.DRAFT.getCode());
        blog.setReviewStatus(ReviewStatus.NONE.getCode());
        blog.setViewCount(0L);
        blog.setLikeCount(0L);
        blog.setCollectCount(0L);
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
        checkOwnerOrAdmin(exist);
        validateTagIds(vo.getTagIds());

        Blog blog = BeanUtil.toBean(vo, Blog.class);
        UserInfo userInfo = currentUser();
        blog.setUpdateBy(userInfo.getUsername());
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
        try {
            for (Long id : blogIds) {
                blogTagMapper.deleteBlogTagByBlogId(id);
                blogMapper.softDeleteBlog(id);
                evictDetail(id);
            }
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
        checkOwnerOrAdmin(exist);
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
        } else {
            update.setStatus(BlogStatus.PUBLISHED.getCode());
            update.setPublishTime(now);
            update.setReviewStatus(ReviewStatus.NONE.getCode());
            action = ReviewAction.PUBLISH;
        }
        blogMapper.editBlogInfo(update);
        // 写审核流水：作者提交(SUBMIT, AUTHOR) 或 系统直通(PUBLISH, SYSTEM)
        writeReviewLog(blogId, action, userInfo, null);
        // 审核结果通知作者（预留，待 rookie 支持个人通知后接入，签名零改动）
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
        checkOwnerOrAdmin(exist);
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

    /** 校验当前用户是作者本人或管理员（具备 knowhub:blog:review 权限视为管理员） */
    private void checkOwnerOrAdmin(Blog blog) {
        UserInfo userInfo = currentUser();
        boolean isAdmin = userInfo.getPermissions() != null
                && userInfo.getPermissions().contains("knowhub:blog:review");
        if (!userInfo.getUsername().equals(blog.getCreateBy()) && !isAdmin) {
            throw new ServiceException(500, "无权操作他人文章");
        }
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
     * 审核结果通知作者。**当前为预留空实现**——rookie 现只有分组通知、无个人通知通道，
     * 待 rookie 通知模块支持投递给单个 userId 后在此接入 SysNoticeService，签名零改动。
     * 当前后台审核记录+前台审核时间线展示已能闭环传达审核结果，通知为增强项非必需。
     *
     * @param blog   被审文章（用其 title/authorId 拼通知内容、定位收件人）
     * @param action 本次动作（SUBMIT/APPROVE/REJECT/REVOKE/PUBLISH）
     * @param advice 审核意见（驳回必填，通过可选）
     */
    private void notifyReviewResult(Blog blog, ReviewAction action, String advice) {
        // 预留：待 rookie 支持个人通知后实现，例如
        // sysNoticeService.addSysNoticeInfo(new SysNoticeVo(... 标题/内容/收件人 authorId ...));
        // 当前前台审核时间线展示已代替通知闭环，此处不报错不阻断。
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

    /** 浏览量 Redis 原子 +1（不直接 update 主表，防热点；后续可批量回写） */
    private void incrView(Long blogId) {
        try {
            redisTemplate.opsForValue().increment(baseKey + CACHE_VIEW_PREFIX + blogId);
        } catch (Exception ignored) {
        }
    }

    private void evictDetail(Long blogId) {
        redisTemplate.delete(baseKey + CACHE_DETAIL_PREFIX + blogId);
    }

    private UserInfo currentUser() {
        return (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}