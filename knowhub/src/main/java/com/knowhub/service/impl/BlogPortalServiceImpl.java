package com.knowhub.service.impl;

import com.github.pagehelper.PageInfo;
import com.knowhub.config.PortalConfigReader;
import com.knowhub.enums.ViewBizType;
import com.knowhub.mapper.BlogPortalMapper;
import com.knowhub.mapper.TagMapper;
import com.knowhub.pojo.entity.Tag;
import com.knowhub.pojo.quarry.BlogPortalSearchQuarry;
import com.knowhub.pojo.vo.BlogPortalDetailVo;
import com.knowhub.pojo.vo.BlogPortalVo;
import com.knowhub.pojo.vo.HotTagVo;
import com.knowhub.pojo.vo.TagOptionVo;
import com.knowhub.pojo.vo.TagVo;
import com.knowhub.service.BlogPortalService;
import com.knowhub.service.ViewHistoryService;
import com.knowhub.support.BlogPermissionResolver;
import com.rookie.common.util.PageUtil;
import com.rookie.framework.security.pojo.UserInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 前台博客门户 Service 实现。
 * <p>
 * 前台无 @PreAuthorize、走 /portal/** permitAll，登录态在此防御性获取（principal 非 UserInfo 视为未登录）。
 * 分级开关 knowhub.portal.hierarchical.enabled 关→userViewLevel 恒 1（二元闸）；开→max(1, resolver.view())（阶梯闸）。
 * 越级详情返回锁态 VO（content 置空 + lockReason），不抛 403、不泄正文。
 */
@Service
public class BlogPortalServiceImpl implements BlogPortalService {

    /** 偏好 tag 召回上限 */
    private static final int PREFER_TAG_TOP_N = 10;
    /** 推荐召回放大倍数（召回比 size 多，service 层算分后再截 size） */
    private static final int RECALL_MULTIPLIER = 3;

    @Autowired
    BlogPortalMapper blogPortalMapper;

    @Autowired
    TagMapper tagMapper;

    @Autowired
    PortalConfigReader portalConfigReader;

    @Autowired
    ViewHistoryService viewHistoryService;

    @Override
    public PageInfo<BlogPortalVo> search(BlogPortalSearchQuarry quarry) {
        quarry.setUserViewLevel(resolveUserViewLevel());
        PageUtil.startPage();
        List<BlogPortalVo> list = blogPortalMapper.searchBlogs(quarry);
        fillTagsForList(list);
        return new PageInfo<>(list);
    }

    @Override
    public List<BlogPortalVo> recommend(int size, Long excludeBlogId) {
        Integer userViewLevel = resolveUserViewLevel();
        UserInfo user = currentUserOrNull();
        List<Long> viewedBlogIds = Collections.emptyList();
        List<Long> preferTagIds = Collections.emptyList();

        if (user != null) {
            // 登录用户：算偏好 tag + 浏览历史排除
            preferTagIds = blogPortalMapper.preferTagsByUser(user.getUserId(), PREFER_TAG_TOP_N);
            viewedBlogIds = blogPortalMapper.viewedBlogIdsByUser(user.getUserId());
        }

        List<BlogPortalVo> result = new ArrayList<>();
        Set<Long> picked = new HashSet<>();
        if (excludeBlogId != null) {
            picked.add(excludeBlogId);
        }

        // 第 2 步：按偏好 tag 召回同 tag 公开博客（排除 excludeBlogId、排除已浏览）
        if (preferTagIds != null && !preferTagIds.isEmpty()) {
            List<BlogPortalVo> recalled = blogPortalMapper.recommendByTags(
                    userViewLevel, preferTagIds, excludeBlogId, viewedBlogIds, size * RECALL_MULTIPLIER);
            // Service 层算分 hotScore = tag命中数*5 + 收藏*3 + 点赞*2 + 浏览*1 + 时间衰减
            List<BlogPortalVo> scored = scoreAndSort(recalled, preferTagIds);
            for (BlogPortalVo vo : scored) {
                if (picked.contains(vo.getBlogId())) continue;
                result.add(vo);
                picked.add(vo.getBlogId());
                if (result.size() >= size) break;
            }
        }

        // 兜底：未登录/无行为/召回不足 → 全局热门补齐去重
        if (result.size() < size) {
            int need = size - result.size();
            List<BlogPortalVo> hot = blogPortalMapper.recommendHot(
                    userViewLevel, excludeBlogId, new ArrayList<>(picked), need);
            for (BlogPortalVo vo : hot) {
                if (picked.contains(vo.getBlogId())) continue;
                result.add(vo);
                picked.add(vo.getBlogId());
                if (result.size() >= size) break;
            }
        }

        fillTagsForList(result);
        return result;
    }

    @Override
    public BlogPortalDetailVo getDetail(Long blogId) {
        Integer userViewLevel = resolveUserViewLevel();
        BlogPortalDetailVo meta = blogPortalMapper.getPortalBlogMeta(blogId);
        if (meta == null) {
            return null; // 不存在或非 PUBLISHED，前台 404 语义由 controller 处理
        }
        // 越级锁态：level > userViewLevel → 不下发正文，只给元数据 + lockReason
        Integer level = meta.getLevel();
        if (level != null && level > userViewLevel) {
            meta.setLocked(true);
            meta.setContent(null);
            meta.setLockReason("需 L" + level + " 权限查看完整正文");
            // 越级不计浏览量（未达权限不算统计量，与第二条链路决策#3"未登录不计浏览量"同构）
            fillTagsForOne(meta);
            return meta;
        }
        // 达权：取正文 + 计浏览量（仅登录态计，未登录不计）
        meta.setLocked(false);
        meta.setContent(blogPortalMapper.getBlogContent(blogId));
        meta.setLockReason(null);
        UserInfo user = currentUserOrNull();
        if (user != null) {
            viewHistoryService.recordView(user.getUserId(), ViewBizType.BLOG.getCode(), blogId);
        }
        fillTagsForOne(meta);
        return meta;
    }

    @Override
    public List<BlogPortalVo> related(Long blogId, int size) {
        Integer userViewLevel = resolveUserViewLevel();
        List<BlogPortalVo> list = blogPortalMapper.relatedBlogs(blogId, userViewLevel, size);
        fillTagsForList(list);
        return list;
    }

    @Override
    public List<HotTagVo> hotTags(int size) {
        return blogPortalMapper.hotTags(size == 0 ? 20 : size);
    }

    @Override
    public List<TagOptionVo> listEnabledTags() {
        // 复用 quarryTag（XML 已带 deleted=0 + 可选 status 过滤），传 status=1 取启用标签，
        // 按 sort/tag_id 排序已由 XML 保证。转 TagOptionVo 只取 tagId+tagName。
        TagVo quarry = new TagVo();
        quarry.setStatus(1);
        List<Tag> tags = tagMapper.quarryTag(quarry);
        return tags.stream().map(t -> {
            TagOptionVo vo = new TagOptionVo();
            vo.setTagId(t.getTagId());
            vo.setTagName(t.getTagName());
            return vo;
        }).collect(Collectors.toList());
    }

    // ============================ 私有辅助 ============================

    /**
     * 解析前台 userViewLevel：
     * 分级开关关 → 恒 1（二元闸，所有人只看 L1）；
     * 开 → max(1, BlogPermissionResolver.view())（未登录/无权限 view=0→1 看 L1；有等级者看 L1~LN）。
     */
    private Integer resolveUserViewLevel() {
        if (!portalConfigReader.isHierarchicalEnabled()) {
            return 1;
        }
        int view = BlogPermissionResolver.resolve().view();
        return Math.max(1, view);
    }

    /**
     * Service 层算分 + 排序：hotScore = tag命中数*5 + 收藏*3 + 点赞*2 + 浏览*1 + 时间衰减。
     * 时间衰减：越新分越高（按 publish_time 距当前的天数衰减，每 30 天 -1，最低 0）。
     */
    private List<BlogPortalVo> scoreAndSort(List<BlogPortalVo> list, List<Long> preferTagIds) {
        Set<Long> preferSet = new HashSet<>(preferTagIds);
        // tag 命中数需先回填 tagIds，但召回阶段还没回填——用 mapper 的批量 tag 查询补命中数
        fillTagsForList(list);
        Date now = new Date();
        Map<Long, Long> scoreMap = new HashMap<>();
        for (BlogPortalVo vo : list) {
            long tagHits = 0;
            if (vo.getTagIds() != null) {
                for (Long tid : vo.getTagIds()) {
                    if (preferSet.contains(tid)) tagHits++;
                }
            }
            long view = vo.getViewCount() == null ? 0 : vo.getViewCount();
            long like = vo.getLikeCount() == null ? 0 : vo.getLikeCount();
            long collect = vo.getCollectCount() == null ? 0 : vo.getCollectCount();
            long hot = tagHits * 5 + collect * 3 + like * 2 + view * 1;
            // 时间衰减：每 30 天 -1，最低 0（now 为 null 兜底不衰减）
            long days = vo.getPublishTime() == null ? 0
                    : (now.getTime() - vo.getPublishTime().getTime()) / (1000L * 60 * 60 * 24);
            long decay = Math.max(0, days / 30);
            scoreMap.put(vo.getBlogId(), hot - decay);
        }
        // 稳定排序：按 score desc，同分按 publish_time desc
        return list.stream()
                .sorted((a, b) -> {
                    long sa = scoreMap.getOrDefault(a.getBlogId(), 0L);
                    long sb = scoreMap.getOrDefault(b.getBlogId(), 0L);
                    if (sb != sa) return Long.compare(sb, sa);
                    Date pa = a.getPublishTime();
                    Date pb = b.getPublishTime();
                    if (pa == null && pb == null) return 0;
                    if (pa == null) return 1;
                    if (pb == null) return -1;
                    return pb.compareTo(pa);
                })
                .collect(Collectors.toList());
    }

    /** 批量回填列表 tagIds + tagNames（防 N+1，照博客 fillTagNamesForList 范式） */
    @SuppressWarnings("unchecked")
    private void fillTagsForList(List<BlogPortalVo> list) {
        if (list == null || list.isEmpty()) return;
        List<Long> blogIds = list.stream().map(BlogPortalVo::getBlogId).collect(Collectors.toList());
        List<Map<String, Object>> rows = blogPortalMapper.getTagIdsByBlogIds(blogIds);
        // 分组：blogId -> tagId 列表
        Map<Long, List<Long>> blogTagMap = new HashMap<>();
        Set<Long> allTagIds = new HashSet<>();
        for (Map<String, Object> row : rows) {
            Long bid = toLong(row.get("blogId"));
            Long tid = toLong(row.get("tagId"));
            if (bid == null || tid == null) continue;
            blogTagMap.computeIfAbsent(bid, k -> new ArrayList<>()).add(tid);
            allTagIds.add(tid);
        }
        // 一次性查全部标签名（tagId -> tagName）
        Map<Long, String> tagNameMap = new HashMap<>();
        if (!allTagIds.isEmpty()) {
            List<Tag> tags = tagMapper.getEnabledTagsByIds(new ArrayList<>(allTagIds));
            for (Tag t : tags) {
                tagNameMap.put(t.getTagId(), t.getTagName());
            }
        }
        for (BlogPortalVo vo : list) {
            List<Long> tagIds = blogTagMap.get(vo.getBlogId());
            if (tagIds == null) {
                vo.setTagIds(Collections.emptyList());
                vo.setTagNames(Collections.emptyList());
                continue;
            }
            vo.setTagIds(tagIds);
            List<String> names = new ArrayList<>();
            for (Long tid : tagIds) {
                String n = tagNameMap.get(tid);
                if (n != null) names.add(n);
            }
            vo.setTagNames(names);
        }
    }

    /** 单条回填 tagIds + tagNames（详情用） */
    private void fillTagsForOne(BlogPortalVo vo) {
        if (vo == null) return;
        List<Map<String, Object>> rows = blogPortalMapper.getTagIdsByBlogIds(Collections.singletonList(vo.getBlogId()));
        List<Long> tagIds = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Long tid = toLong(row.get("tagId"));
            if (tid != null) tagIds.add(tid);
        }
        vo.setTagIds(tagIds);
        if (tagIds.isEmpty()) {
            vo.setTagNames(Collections.emptyList());
            return;
        }
        List<Tag> tags = tagMapper.getEnabledTagsByIds(tagIds);
        List<String> names = tags.stream().map(Tag::getTagName).collect(Collectors.toList());
        vo.setTagNames(names);
    }

    /** Map 取值转 Long（MyBatis 返回的 bigint 可能是 Long/Number） */
    private Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Long) return (Long) o;
        if (o instanceof Number) return ((Number) o).longValue();
        try { return Long.valueOf(o.toString()); } catch (Exception e) { return null; }
    }

    /**
     * 防御性取当前登录用户：principal 是 UserInfo 才返回，否则 null（未登录/匿名）。
     * 前台 permitAll 区不能像后台那样直接强转（会 NPE）。
     */
    private UserInfo currentUserOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        Object p = auth.getPrincipal();
        return (p instanceof UserInfo) ? (UserInfo) p : null;
    }
}
