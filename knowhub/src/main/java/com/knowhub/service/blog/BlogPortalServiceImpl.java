package com.knowhub.service.blog;

import com.github.pagehelper.PageInfo;
import com.knowhub.config.PortalConfigReader;
import com.knowhub.enums.history.ViewBizType;
import com.knowhub.mapper.blog.BlogCollectMapper;
import com.knowhub.mapper.blog.BlogLikeMapper;
import com.knowhub.mapper.blog.BlogPortalMapper;
import com.knowhub.mapper.tag.TagMapper;
import com.knowhub.pojo.tag.entity.Tag;
import com.knowhub.pojo.blog.entity.BlogCollect;
import com.knowhub.pojo.blog.entity.BlogLike;
import com.knowhub.pojo.blog.quarry.BlogPortalSearchQuarry;
import com.knowhub.pojo.blog.vo.BlogPortalDetailVo;
import com.knowhub.pojo.blog.vo.BlogPortalVo;
import com.knowhub.pojo.blog.vo.PortalBlogStatsVo;
import com.knowhub.pojo.tag.vo.HotTagVo;
import com.knowhub.pojo.tag.vo.TagOptionVo;
import com.knowhub.pojo.tag.vo.TagVo;
import com.knowhub.service.blog.impl.BlogPortalService;
import com.knowhub.service.history.impl.ViewHistoryService;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 前台博客门户 Service 实现。
 * <p>
 * 前台无 @PreAuthorize、走 /portal/** permitAll，登录态在此防御性获取（principal 非 UserInfo 视为未登录）。
 * 分级开关 knowhub.portal.hierarchical.enabled 关→userViewLevel 恒 1（二元闸）；开→max(1, resolver.level())（阶梯闸）。
 * 2026-08-18 权限大修单键化 + 搜索范围 +1：resolver 改单键 knowhub:blog:lN（resolve().level() 取最高等级），
 * 列表 SQL where 片段 level<=userViewLevel+1（越级作品进列表带 locked=true，summary 可见），详情 meta 不带 level 过滤
 * 由 service 判越级 → 锁态 VO（content 置空 + locked + lockReason），不抛 403、不泄正文。
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
    BlogCollectMapper blogCollectMapper;

    @Autowired
    BlogLikeMapper blogLikeMapper;

    @Autowired
    TagMapper tagMapper;

    @Autowired
    PortalConfigReader portalConfigReader;

    @Autowired
    ViewHistoryService viewHistoryService;

    @Override
    public PageInfo<BlogPortalVo> search(BlogPortalSearchQuarry quarry) {
        quarry.setUserViewLevel(resolveUserViewLevel());
        // 标签命中门槛值：tagCount 必须在 service 层算好回填，不能在 SQL 里写 #{tagIds.size()}——
        // MyBatis createCacheKey 反射取 tagIds.size() 会走 CollectionWrapper.get("size") 抛 UnsupportedOperationException。
        quarry.setTagCount(quarry.getTagIds() == null ? 0 : quarry.getTagIds().size());
        PageUtil.startPage();
        List<BlogPortalVo> list = blogPortalMapper.searchBlogs(quarry);
        fillTagsForList(list);
        fillLockedForList(list, quarry.getUserViewLevel());
        return new PageInfo<>(list);
    }

    @Override
    public PortalBlogStatsVo getStats() {
        return blogPortalMapper.getPortalStats(resolveUserViewLevel());
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
        fillLockedForList(result, userViewLevel);
        return result;
    }

    @Override
    public BlogPortalDetailVo getDetail(Long blogId) {
        Integer userViewLevel = resolveUserViewLevel();
        BlogPortalDetailVo meta = blogPortalMapper.getPortalBlogMeta(blogId);
        if (meta == null) {
            return null; // 不存在或非 PUBLISHED，前台 404 语义由 controller 处理
        }
        // 越级锁态：level > userViewLevel → content 置空（不下发完整正文），previewContent 下发前 N 字符作预览
        // （预览式阅读锁：能看几行但后面被锁，PortalConfigReader.getLockPreviewLength 控制预览长度，默认 200，0=不预览回退整篇锁）
        Integer level = meta.getLevel();
        if (level != null && level > userViewLevel) {
            meta.setLocked(true);
            meta.setContent(null);
            int previewLen = portalConfigReader.getLockPreviewLength();
            if (previewLen > 0) {
                String full = blogPortalMapper.getBlogContent(blogId);
                meta.setPreviewContent(truncatePreview(full, previewLen));
            } else {
                meta.setPreviewContent(null);
            }
            meta.setLockReason("需 L" + level + " 权限查看完整正文");
            // 越级不计浏览量（未达权限不算统计量，与第二条链路决策#3"未登录不计浏览量"同构）
            fillCurrentUserInteract(meta, blogId);
            fillTagsForOne(meta);
            return meta;
        }
        // 达权：取正文 + 计浏览量（仅登录态计，未登录不计）
        meta.setLocked(false);
        meta.setContent(blogPortalMapper.getBlogContent(blogId));
        meta.setPreviewContent(null);
        meta.setLockReason(null);
        UserInfo user = currentUserOrNull();
        if (user != null) {
            viewHistoryService.recordView(user.getUserId(), ViewBizType.BLOG.getCode(), blogId);
        }
        fillCurrentUserInteract(meta, blogId);
        fillTagsForOne(meta);
        return meta;
    }

    @Override
    public List<BlogPortalVo> related(Long blogId, int size) {
        Integer userViewLevel = resolveUserViewLevel();
        List<BlogPortalVo> list = blogPortalMapper.relatedBlogs(blogId, userViewLevel, size);
        fillTagsForList(list);
        fillLockedForList(list, userViewLevel);
        return list;
    }

    @Override
    public PageInfo<BlogPortalVo> listMyCollected(int pageNum, int pageSize) {
        UserInfo user = currentUserOrNull();
        if (user == null) {
            // authoring controller 已 isAuthenticated 兜底，此处双保险
            return new PageInfo<>(Collections.emptyList());
        }
        List<Long> blogIds = blogCollectMapper.listCollectedBlogIds(user.getUserId());
        if (blogIds == null || blogIds.isEmpty()) {
            return new PageInfo<>(Collections.emptyList());
        }
        // listByIds 取"收藏 ID 集 ∩ 前台可见"全量 VO（前台铁律过滤未发布/越级），不排序——
        // service 按收藏时间倒序的 blogIds 顺序拼装，还原"最近收藏在前"语义。
        // 2026-08-12 修正：原实现误调 recommendHot(全局热门 topN, size=收藏数) 再求交集——收藏博客不在
        // 全局热门 topN 里即被丢，收藏列表为空。改为 listByIds 精确召回。
        Integer userViewLevel = resolveUserViewLevel();
        List<BlogPortalVo> all = blogPortalMapper.listByIds(userViewLevel, blogIds);
        Map<Long, BlogPortalVo> voMap = new HashMap<>();
        for (BlogPortalVo vo : all) {
            voMap.put(vo.getBlogId(), vo);
        }
        List<BlogPortalVo> ordered = new ArrayList<>();
        for (Long bid : blogIds) {
            BlogPortalVo vo = voMap.get(bid);
            if (vo != null) {
                ordered.add(vo);
            }
        }
        fillTagsForList(ordered);
        fillLockedForList(ordered, userViewLevel);
        return new PageInfo<>(ordered);
    }

    @Override
    public List<HotTagVo> hotTags(int size) {
        return blogPortalMapper.hotTags(size == 0 ? 20 : size, resolveUserViewLevel());
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
     * 分级开关关 → 恒 1（二元闸，所有人只看 L1+L2 带 locked）；
     * 开 → max(1, BlogPermissionResolver.level())（单键 knowhub:blog:lN，未登录/无权限 level=0→1 看 L1；有等级者看 L1~LN）。
     * 2026-08-18 权限大修单键化：resolve().view() → resolve().level()。
     * <p>
     * admin 短路兜底：admin 默认拥有所有权限、能看任何级别作品，**在分级开关判定之前**直接返回 3。
     * 不受 hierarchical 开关（关时普通用户恒 1）与 sys_role_menu 绑定（admin 角色未绑 l3 菜单也能得 3）影响。
     * UserInfo.isAdmin() 由 rookie 框架 UserDetailServiceImpl 依 roleKey="admin" 装载。
     */
    private Integer resolveUserViewLevel() {
        // admin 短路：admin 看任何级别，不受分级开关与角色菜单绑定影响
        UserInfo user = currentUserOrNull();
        if (user != null && user.isAdmin()) {
            return 3;
        }
        if (!portalConfigReader.isHierarchicalEnabled()) {
            return 1;
        }
        int level = BlogPermissionResolver.resolve().level();
        return Math.max(1, level);
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

    /**
     * 批量回填越级锁标记：vo.level > userViewLevel → locked=true（2026-08-18 搜索范围 +1 落地）。
     * 越级作品进列表带锁标记、summary 可见，前端据此渲染锁图标；达权 locked=false。O(n) n=页大小，开销可忽略。
     * 博客越级锁正文（content 在详情置空），列表层仅标 locked 不置空字段。
     */
    private void fillLockedForList(List<BlogPortalVo> list, Integer userViewLevel) {
        if (list == null || list.isEmpty()) return;
        for (BlogPortalVo vo : list) {
            Integer level = vo.getLevel();
            vo.setLocked(level != null && level > userViewLevel);
        }
    }

    /**
     * 详情回填当前用户的点赞/收藏态（登录态查 blog_like/blog_collect 事实表，未登录置 null 不查库）。
     * 与 ResourcePortalServiceImpl.fillCurrentUserInteract 同范式：Boolean 包装类型，未登录留 null
     * 让前端按"游客态"渲染按钮（不比已点亮的真值）。
     */
    private void fillCurrentUserInteract(BlogPortalDetailVo vo, Long blogId) {
        UserInfo user = currentUserOrNull();
        if (user == null) {
            return; // 未登录：hasLiked/hasCollected 留 null
        }
        vo.setHasLiked(blogLikeMapper.getBlogLike(new BlogLike(blogId, user.getUserId())) != null);
        vo.setHasCollected(blogCollectMapper.getBlogCollect(new BlogCollect(blogId, user.getUserId())) != null);
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

    /**
     * 越级预览截断：取正文前 max 字符作预览（超长追加 "…"，null/空返 null）。
     * 预览式阅读锁用——越级用户能看到开篇几行，其后内容锁遮罩。
     */
    private static String truncatePreview(String s, int max) {
        if (s == null || s.isEmpty() || max <= 0) {
            return null;
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "…";
    }
}
