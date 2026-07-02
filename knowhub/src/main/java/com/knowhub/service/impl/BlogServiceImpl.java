package com.knowhub.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.github.pagehelper.PageInfo;
import com.knowhub.config.BlogConfigReader;
import com.knowhub.enums.BlogStatus;
import com.knowhub.enums.ReviewStatus;
import com.knowhub.mapper.BlogCollectMapper;
import com.knowhub.mapper.BlogLikeMapper;
import com.knowhub.mapper.BlogMapper;
import com.knowhub.mapper.BlogTagMapper;
import com.knowhub.mapper.TagMapper;
import com.knowhub.pojo.quarry.BlogQuarry;
import com.knowhub.pojo.vo.BlogVo;
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
    BlogConfigReader blogConfigReader;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Value("${redis.base-key}")
    private String baseKey;

    /** 博客详情缓存 key：blog:detail:{id}（经 baseKey 前缀） */
    private static final String CACHE_DETAIL_PREFIX = "blog:detail:";
    /** 浏览计数 key：blog:view:{id}（经 baseKey 前缀，原子 incr） */
    private static final String CACHE_VIEW_PREFIX = "blog:view:";

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
        checkOwnerOrAdmin(exist);
        UserInfo userInfo = currentUser();
        Date now = new Date();
        // 经审核开关决定目标状态：开关关→直接发布；开→待审核
        boolean reviewEnabled = blogConfigReader.isReviewEnabled();
        Blog update = new Blog();
        update.setBlogId(blogId);
        update.setUpdateBy(userInfo.getUsername());
        if (reviewEnabled) {
            update.setStatus(BlogStatus.PENDING_REVIEW.getCode());
            update.setReviewStatus(ReviewStatus.PENDING.getCode());
        } else {
            update.setStatus(BlogStatus.PUBLISHED.getCode());
            update.setPublishTime(now);
            update.setReviewStatus(ReviewStatus.NONE.getCode());
        }
        blogMapper.editBlogInfo(update);
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
        checkOwnerOrAdmin(exist);
        Blog update = new Blog();
        update.setBlogId(blogId);
        update.setStatus(BlogStatus.REVOKED.getCode());
        update.setUpdateBy(currentUser().getUsername());
        blogMapper.editBlogInfo(update);
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
        UserInfo userInfo = currentUser();
        Date now = new Date();
        Blog update = new Blog();
        update.setBlogId(vo.getBlogId());
        update.setUpdateBy(userInfo.getUsername());
        update.setReviewer(userInfo.getUsername());
        update.setReviewTime(now);
        if (vo.getPass()) {
            update.setStatus(BlogStatus.PUBLISHED.getCode());
            update.setPublishTime(now);
            update.setReviewStatus(ReviewStatus.APPROVED.getCode());
        } else {
            if (vo.getAdvice() == null || vo.getAdvice().isEmpty()) {
                throw new ServiceException(500, "驳回需填写审核意见");
            }
            update.setStatus(BlogStatus.REJECTED.getCode());
            update.setReviewStatus(ReviewStatus.REJECTED.getCode());
            update.setReviewAdvice(vo.getAdvice());
        }
        blogMapper.editBlogInfo(update);
        evictDetail(vo.getBlogId());
        return true;
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