package com.knowhub.service.article;

import com.github.pagehelper.PageInfo;
import com.knowhub.config.PortalConfigReader;
import com.knowhub.enums.history.ViewBizType;
import com.knowhub.mapper.article.ArticleCollectMapper;
import com.knowhub.mapper.article.ArticleLikeMapper;
import com.knowhub.mapper.article.ArticleMapper;
import com.knowhub.mapper.article.ArticlePortalMapper;
import com.knowhub.mapper.tag.TagMapper;
import com.knowhub.pojo.article.entity.ArticleCollect;
import com.knowhub.pojo.article.entity.ArticleLike;
import com.knowhub.pojo.article.quarry.ArticlePortalSearchQuarry;
import com.knowhub.pojo.article.vo.ArticlePortalDetailVo;
import com.knowhub.pojo.article.vo.ArticlePortalVo;
import com.knowhub.pojo.article.vo.ChapterContentVo;
import com.knowhub.pojo.article.vo.ChapterOutlineVo;
import com.knowhub.pojo.article.vo.MatchedChapterVo;
import com.knowhub.pojo.article.vo.PortalArticleStatsVo;
import com.knowhub.pojo.tag.entity.Tag;
import com.knowhub.service.article.impl.ArticlePortalService;
import com.knowhub.service.history.impl.ViewHistoryService;
import com.knowhub.support.ArticlePermissionResolver;
import com.rookie.common.exception.ServiceException;
import com.rookie.common.util.PageUtil;
import com.rookie.framework.security.pojo.UserInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 * 前台文章门户 Service 实现。照搬 BlogPortalServiceImpl 范式，适配文章=章节集合（文档站）结构。
 * <p>
 * 前台读无 @PreAuthorize、走 /portal/** permitAll，登录态在此防御性获取（principal 非 UserInfo 视为未登录）。
 * 分级开关 knowhub.portal.hierarchical.enabled 关→userViewLevel 恒 1（二元闸）；开→max(1, resolver.view())（阶梯闸）。
 * 越级详情/章节正文返回锁态 VO（content 置空 + lockReason），不抛 403、不泄正文。
 * 收藏/点赞 toggle 走 /authoring/** authenticated，currentUser() 直接取。
 * coverUrl 由 SQL 用 file_object ARTICLE_COVER 反查后 concat 成 /file/resolve/{objectId}（service 不二次处理）。
 */
@Service
public class ArticlePortalServiceImpl implements ArticlePortalService {

    /** 偏好 tag 召回上限 */
    private static final int PREFER_TAG_TOP_N = 10;
    /** 推荐召回放大倍数（召回比 size 多，service 层算分后再截 size） */
    private static final int RECALL_MULTIPLIER = 3;

    @Autowired
    ArticlePortalMapper articlePortalMapper;

    @Autowired
    ArticleMapper articleMapper;

    @Autowired
    ArticleCollectMapper articleCollectMapper;

    @Autowired
    ArticleLikeMapper articleLikeMapper;

    @Autowired
    TagMapper tagMapper;

    @Autowired
    PortalConfigReader portalConfigReader;

    @Autowired
    ViewHistoryService viewHistoryService;

    @Override
    public PageInfo<ArticlePortalVo> search(ArticlePortalSearchQuarry quarry) {
        quarry.setUserViewLevel(resolveUserViewLevel());
        PageUtil.startPage();
        List<ArticlePortalVo> list = articlePortalMapper.searchArticles(quarry);
        fillTagsForList(list);
        fillChapterCountForList(list);
        // 仅当有 keyword 时回填命中章节（标出对应章节）
        if (quarry.getKeyword() != null && !quarry.getKeyword().isEmpty() && !list.isEmpty()) {
            fillMatchedChapters(list, quarry.getKeyword());
        }
        return new PageInfo<>(list);
    }

    @Override
    public PortalArticleStatsVo getStats() {
        return articlePortalMapper.getPortalStats(resolveUserViewLevel());
    }

    @Override
    public List<ArticlePortalVo> recommend(int size, Long excludeArticleId) {
        Integer userViewLevel = resolveUserViewLevel();
        UserInfo user = currentUserOrNull();
        List<Long> viewedArticleIds = Collections.emptyList();
        List<Long> preferTagIds = Collections.emptyList();

        if (user != null) {
            preferTagIds = articlePortalMapper.preferTagsByUser(user.getUserId(), PREFER_TAG_TOP_N);
            viewedArticleIds = articlePortalMapper.viewedArticleIdsByUser(user.getUserId());
        }

        List<ArticlePortalVo> result = new ArrayList<>();
        Set<Long> picked = new HashSet<>();
        if (excludeArticleId != null) {
            picked.add(excludeArticleId);
        }

        // 第 2 步：按偏好 tag 召回同 tag 公开文章（排除 excludeArticleId、排除已浏览）
        if (preferTagIds != null && !preferTagIds.isEmpty()) {
            List<ArticlePortalVo> recalled = articlePortalMapper.recommendByTags(
                    userViewLevel, preferTagIds, excludeArticleId, viewedArticleIds, size * RECALL_MULTIPLIER);
            // Service 层算分 hotScore = tag命中数*5 + 收藏*3 + 点赞*2 + 浏览*1 + 时间衰减
            List<ArticlePortalVo> scored = scoreAndSort(recalled, preferTagIds);
            for (ArticlePortalVo vo : scored) {
                if (picked.contains(vo.getArticleId())) continue;
                result.add(vo);
                picked.add(vo.getArticleId());
                if (result.size() >= size) break;
            }
        }

        // 兜底：未登录/无行为/召回不足 → 全局热门补齐去重
        if (result.size() < size) {
            int need = size - result.size();
            List<ArticlePortalVo> hot = articlePortalMapper.recommendHot(
                    userViewLevel, excludeArticleId, new ArrayList<>(picked), need);
            for (ArticlePortalVo vo : hot) {
                if (picked.contains(vo.getArticleId())) continue;
                result.add(vo);
                picked.add(vo.getArticleId());
                if (result.size() >= size) break;
            }
        }

        fillTagsForList(result);
        fillChapterCountForList(result);
        return result;
    }

    @Override
    public ArticlePortalDetailVo getDetail(Long articleId) {
        Integer userViewLevel = resolveUserViewLevel();
        ArticlePortalDetailVo meta = articlePortalMapper.getPortalArticleMeta(articleId);
        if (meta == null) {
            return null; // 不存在或非 PUBLISHED，前台 404 语义由 controller 处理
        }
        // 章节大纲无论是否越级都下发（只含章节名，不含正文）
        meta.setChapterList(articlePortalMapper.listChapterOutline(articleId));

        // 越级锁态：level > userViewLevel → 不计浏览量，仅给元数据+章节大纲+lockReason（正文走章节接口时也会锁态）
        Integer level = meta.getLevel();
        if (level != null && level > userViewLevel) {
            meta.setLocked(true);
            meta.setLockReason("需 L" + level + " 权限查看完整内容");
            fillCurrentUserInteract(meta, articleId);
            fillTagsForOne(meta);
            fillChapterCountForOne(meta);
            return meta;
        }
        // 达权：计浏览量（仅登录态计，未登录不计）
        meta.setLocked(false);
        meta.setLockReason(null);
        UserInfo user = currentUserOrNull();
        if (user != null) {
            viewHistoryService.recordView(user.getUserId(), ViewBizType.ARTICLE.getCode(), articleId);
        }
        fillCurrentUserInteract(meta, articleId);
        fillTagsForOne(meta);
        fillChapterCountForOne(meta);
        return meta;
    }

    @Override
    public List<ArticlePortalVo> related(Long articleId, int size) {
        Integer userViewLevel = resolveUserViewLevel();
        List<ArticlePortalVo> list = articlePortalMapper.relatedArticles(articleId, userViewLevel, size);
        fillTagsForList(list);
        fillChapterCountForList(list);
        return list;
    }

    @Override
    public ChapterContentVo getChapterContent(Long articleId, Long chapterId) {
        Integer userViewLevel = resolveUserViewLevel();
        // 先查文章 meta 判越级（越级不泄整章正文）
        ArticlePortalDetailVo meta = articlePortalMapper.getPortalArticleMeta(articleId);
        if (meta == null) {
            return null; // 文章不存在或非 PUBLISHED
        }
        Integer level = meta.getLevel();
        if (level != null && level > userViewLevel) {
            ChapterContentVo locked = new ChapterContentVo();
            locked.setChapterId(chapterId);
            locked.setArticleId(articleId);
            locked.setLocked(true);
            locked.setLockReason("需 L" + level + " 权限查看完整内容");
            locked.setContent(null);
            return locked;
        }
        // 达权：取章节正文（SQL 已校验 article PUBLISHED + chapter PUBLISHED + 归属）
        ChapterContentVo vo = articlePortalMapper.getChapterContent(articleId, chapterId);
        if (vo == null) {
            return null; // 章节不存在/非 PUBLISHED/不属于该文章
        }
        vo.setLocked(false);
        vo.setLockReason(null);
        UserInfo user = currentUserOrNull();
        if (user != null) {
            viewHistoryService.recordView(user.getUserId(), ViewBizType.CHAPTER.getCode(), chapterId);
        }
        return vo;
    }

    @Override
    @Transactional
    public Boolean toggleCollect(Long articleId, Boolean collected) {
        if (articleMapper.getArticleInfoById(articleId) == null) {
            throw new ServiceException(500, "文章不存在");
        }
        Long userId = currentUser().getUserId();
        ArticleCollect record = new ArticleCollect(articleId, userId);
        if (Boolean.TRUE.equals(collected)) {
            ArticleCollect old = articleCollectMapper.getArticleCollect(record);
            articleCollectMapper.addArticleCollect(record);
            if (old == null) {
                articleMapper.incrCollectCount(articleId, 1L);
            }
        } else {
            ArticleCollect old = articleCollectMapper.getArticleCollect(record);
            articleCollectMapper.deleteArticleCollect(record);
            if (old != null) {
                articleMapper.incrCollectCount(articleId, -1L);
            }
        }
        return true;
    }

    @Override
    @Transactional
    public Boolean toggleLike(Long articleId, Boolean liked) {
        if (articleMapper.getArticleInfoById(articleId) == null) {
            throw new ServiceException(500, "文章不存在");
        }
        Long userId = currentUser().getUserId();
        ArticleLike record = new ArticleLike(articleId, userId);
        if (Boolean.TRUE.equals(liked)) {
            ArticleLike old = articleLikeMapper.getArticleLike(record);
            articleLikeMapper.addArticleLike(record);
            if (old == null) {
                articleMapper.incrLikeCount(articleId, 1L);
            }
        } else {
            ArticleLike old = articleLikeMapper.getArticleLike(record);
            articleLikeMapper.deleteArticleLike(record);
            if (old != null) {
                articleMapper.incrLikeCount(articleId, -1L);
            }
        }
        return true;
    }

    @Override
    public PageInfo<ArticlePortalVo> listMyCollected(int pageNum, int pageSize) {
        UserInfo user = currentUserOrNull();
        if (user == null) {
            // authoring controller 已 isAuthenticated 兜底，此处防御
            return new PageInfo<>(Collections.emptyList());
        }
        List<Long> articleIds = articleCollectMapper.listCollectedArticleIds(user.getUserId());
        if (articleIds == null || articleIds.isEmpty()) {
            return new PageInfo<>(Collections.emptyList());
        }
        // listByIds 按"收藏 ID 集 ∩ 前台可见"取全量 VO（前台铁律过滤未发布/越级），不排序——
        // service 按收藏时间倒序的 articleIds 顺序拼装，还原"最近收藏在前"语义。
        // 2026-08-12 修正：原实现误调 recommendHot(全局热门 topN, size=收藏数) 再求交集——收藏文章不在
        // 全局热门 topN 里即被丢，越级/冷门收藏列表为空。改为 listByIds 精确召回。
        Integer userViewLevel = resolveUserViewLevel();
        List<ArticlePortalVo> all = articlePortalMapper.listByIds(userViewLevel, articleIds);
        Map<Long, ArticlePortalVo> voMap = new HashMap<>();
        for (ArticlePortalVo vo : all) {
            voMap.put(vo.getArticleId(), vo);
        }
        List<ArticlePortalVo> ordered = new ArrayList<>();
        for (Long aid : articleIds) {
            ArticlePortalVo vo = voMap.get(aid);
            if (vo != null) {
                ordered.add(vo);
            }
        }
        fillTagsForList(ordered);
        fillChapterCountForList(ordered);
        return new PageInfo<>(ordered);
    }

    // ============================ 私有辅助 ============================

    /**
     * 解析前台 userViewLevel：分级开关关→恒 1（二元闸）；开→max(1, ArticlePermissionResolver.view())。
     */
    private Integer resolveUserViewLevel() {
        if (!portalConfigReader.isHierarchicalEnabled()) {
            return 1;
        }
        int view = ArticlePermissionResolver.resolve().view();
        return Math.max(1, view);
    }

    /**
     * Service 层算分 + 排序：hotScore = tag命中数*5 + 收藏*3 + 点赞*2 + 浏览*1 + 时间衰减。
     * 时间衰减：每 30 天 -1，最低 0（越新分越高）。
     */
    private List<ArticlePortalVo> scoreAndSort(List<ArticlePortalVo> list, List<Long> preferTagIds) {
        Set<Long> preferSet = new HashSet<>(preferTagIds);
        fillTagsForList(list);
        Date now = new Date();
        Map<Long, Long> scoreMap = new HashMap<>();
        for (ArticlePortalVo vo : list) {
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
            long days = vo.getPublishTime() == null ? 0
                    : (now.getTime() - vo.getPublishTime().getTime()) / (1000L * 60 * 60 * 24);
            long decay = Math.max(0, days / 30);
            scoreMap.put(vo.getArticleId(), hot - decay);
        }
        return list.stream()
                .sorted((a, b) -> {
                    long sa = scoreMap.getOrDefault(a.getArticleId(), 0L);
                    long sb = scoreMap.getOrDefault(b.getArticleId(), 0L);
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

    /** 批量回填列表 tagIds + tagNames（防 N+1） */
    @SuppressWarnings("unchecked")
    private void fillTagsForList(List<ArticlePortalVo> list) {
        if (list == null || list.isEmpty()) return;
        List<Long> articleIds = list.stream().map(ArticlePortalVo::getArticleId).collect(Collectors.toList());
        List<Map<String, Object>> rows = articlePortalMapper.getTagIdsByArticleIds(articleIds);
        Map<Long, List<Long>> articleTagMap = new HashMap<>();
        Set<Long> allTagIds = new HashSet<>();
        for (Map<String, Object> row : rows) {
            Long aid = toLong(row.get("articleId"));
            Long tid = toLong(row.get("tagId"));
            if (aid == null || tid == null) continue;
            articleTagMap.computeIfAbsent(aid, k -> new ArrayList<>()).add(tid);
            allTagIds.add(tid);
        }
        Map<Long, String> tagNameMap = new HashMap<>();
        if (!allTagIds.isEmpty()) {
            List<Tag> tags = tagMapper.getEnabledTagsByIds(new ArrayList<>(allTagIds));
            for (Tag t : tags) {
                tagNameMap.put(t.getTagId(), t.getTagName());
            }
        }
        for (ArticlePortalVo vo : list) {
            List<Long> tagIds = articleTagMap.get(vo.getArticleId());
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

    /**
     * 详情回填当前用户的点赞/收藏态（登录态查 article_like/article_collect 事实表，未登录置 null 不查库）。
     * 与 ResourcePortalServiceImpl.fillCurrentUserInteract / BlogPortalServiceImpl 同范式：
     * Boolean 包装类型，未登录留 null 让前端按游客态渲染按钮。
     */
    private void fillCurrentUserInteract(ArticlePortalDetailVo vo, Long articleId) {
        UserInfo user = currentUserOrNull();
        if (user == null) {
            return; // 未登录：hasLiked/hasCollected 留 null
        }
        vo.setHasLiked(articleLikeMapper.getArticleLike(new ArticleLike(articleId, user.getUserId())) != null);
        vo.setHasCollected(articleCollectMapper.getArticleCollect(new ArticleCollect(articleId, user.getUserId())) != null);
    }

    /** 单条回填 tagIds + tagNames（详情用） */
    private void fillTagsForOne(ArticlePortalVo vo) {
        if (vo == null) return;
        List<Map<String, Object>> rows = articlePortalMapper.getTagIdsByArticleIds(
                Collections.singletonList(vo.getArticleId()));
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
        vo.setTagNames(tags.stream().map(Tag::getTagName).collect(Collectors.toList()));
    }

    /** 批量回填列表章节数量（防 N+1） */
    private void fillChapterCountForList(List<ArticlePortalVo> list) {
        if (list == null || list.isEmpty()) return;
        List<Long> articleIds = list.stream().map(ArticlePortalVo::getArticleId).collect(Collectors.toList());
        List<Map<String, Object>> rows = articlePortalMapper.getChapterCountByArticleIds(articleIds);
        Map<Long, Integer> cntMap = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Long aid = toLong(row.get("articleId"));
            Integer cnt = toInt(row.get("cnt"));
            if (aid != null && cnt != null) cntMap.put(aid, cnt);
        }
        for (ArticlePortalVo vo : list) {
            vo.setChapterCount(cntMap.getOrDefault(vo.getArticleId(), 0));
        }
    }

    /** 单条回填章节数量（详情用） */
    private void fillChapterCountForOne(ArticlePortalVo vo) {
        if (vo == null) return;
        List<Map<String, Object>> rows = articlePortalMapper.getChapterCountByArticleIds(
                Collections.singletonList(vo.getArticleId()));
        int cnt = 0;
        if (!rows.isEmpty()) {
            Integer c = toInt(rows.get(0).get("cnt"));
            cnt = c == null ? 0 : c;
        }
        vo.setChapterCount(cnt);
    }

    /** 批量回填命中章节（搜索结果标出 keyword 命中的章节，按 articleId 分组拼 matchedChapters） */
    private void fillMatchedChapters(List<ArticlePortalVo> list, String keyword) {
        if (list == null || list.isEmpty()) return;
        List<Long> articleIds = list.stream().map(ArticlePortalVo::getArticleId).collect(Collectors.toList());
        List<MatchedChapterVo> rows = articlePortalMapper.matchedChapters(articleIds, keyword);
        Map<Long, List<MatchedChapterVo>> groupMap = new HashMap<>();
        for (MatchedChapterVo mc : rows) {
            groupMap.computeIfAbsent(mc.getArticleId(), k -> new ArrayList<>()).add(mc);
        }
        for (ArticlePortalVo vo : list) {
            List<MatchedChapterVo> matched = groupMap.get(vo.getArticleId());
            // 每文章命中章节至多展示前 3 个（避免列表卡过宽）
            if (matched == null || matched.isEmpty()) {
                vo.setMatchedChapters(Collections.emptyList());
            } else {
                vo.setMatchedChapters(matched.size() > 3 ? matched.subList(0, 3) : matched);
            }
        }
    }

    /** Map 取值转 Long（MyBatis 返回的 bigint 可能是 Long/Number） */
    private Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Long) return (Long) o;
        if (o instanceof Number) return ((Number) o).longValue();
        try { return Long.valueOf(o.toString()); } catch (Exception e) { return null; }
    }

    /** Map 取值转 Integer */
    private Integer toInt(Object o) {
        if (o == null) return null;
        if (o instanceof Integer) return (Integer) o;
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.valueOf(o.toString()); } catch (Exception e) { return null; }
    }

    /**
     * 防御性取当前登录用户：principal 是 UserInfo 才返回，否则 null（未登录/匿名）。
     * 前台 permitAll 区不能像后台那样直接强转（会 NPE）。authoring 区用 currentUser()。
     */
    private UserInfo currentUserOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        Object p = auth.getPrincipal();
        return (p instanceof UserInfo) ? (UserInfo) p : null;
    }

    private UserInfo currentUser() {
        return (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}