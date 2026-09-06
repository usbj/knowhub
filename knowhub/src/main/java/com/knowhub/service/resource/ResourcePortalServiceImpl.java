package com.knowhub.service.resource;

import com.github.pagehelper.PageInfo;
import com.knowhub.config.PortalConfigReader;
import com.knowhub.enums.history.ViewBizType;
import com.knowhub.mapper.resource.ResourceCollectMapper;
import com.knowhub.mapper.resource.ResourceLikeMapper;
import com.knowhub.mapper.resource.ResourcePortalMapper;
import com.knowhub.mapper.resource.ResourceRatingMapper;
import com.knowhub.pojo.resource.entity.ResourceCollect;
import com.knowhub.pojo.resource.entity.ResourceLike;
import com.knowhub.pojo.resource.entity.ResourceRating;
import com.knowhub.pojo.resource.quarry.ResourcePortalSearchQuarry;
import com.knowhub.pojo.resource.vo.ResourcePortalDetailVo;
import com.knowhub.pojo.resource.vo.ResourcePortalVo;
import com.knowhub.service.history.impl.ViewHistoryService;
import com.knowhub.service.resource.impl.ResourcePortalService;
import com.knowhub.support.ResourcePermissionResolver;
import com.rookie.common.util.PageUtil;
import com.rookie.framework.security.pojo.UserInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 前台资源门户 Service 实现。
 * <p>
 * 前台读无 @PreAuthorize、走 /portal/** permitAll，登录态在此防御性获取（principal 非 UserInfo 视为未登录）。
 * <p>
 * 2026-08-18 权限大修：资源引入 level 分级（原 PUBLISHED 即全公开，无等级）。前台搜索范围放宽到
 * level &lt;= userViewLevel + 1（L1 搜 L1+L2 带 lock、L2 搜全部 L3 带 lock、L3 搜全部无锁），越级作品进列表
 * 带 locked=true + 摘要可见 + LINK 类型 linkUrl 置空（锁跳转）。详情越级锁态 = locked=true + lockReason +
 * description 仍下发（可见）+ FILE 锁下载（本就不在详情预填）+ LINK 置空 linkUrl（锁跳转）。
 * <p>
 * 推荐口径：资源无标签体系、无用户偏好源（unlike blog），推荐 feed 直接退化为全局热门兜底（按热度排）。
 * <p>
 * 互动计数由 mapper SQL inline 子查询回填（资源主表刻意不冗余 like/collect/rating 计数），
 * service 层仅回填当前用户互动态（hasLiked/hasCollected/myScore，登录态）。
 * 浏览量 = 主表 view_count 冗余列（统一浏览历史回写），service 仅在登录态达权时调 recordView 触发首次 +1。
 */
@Service
public class ResourcePortalServiceImpl implements ResourcePortalService {

    @Autowired
    ResourcePortalMapper resourcePortalMapper;

    @Autowired
    ResourceLikeMapper resourceLikeMapper;

    @Autowired
    ResourceCollectMapper resourceCollectMapper;

    @Autowired
    ResourceRatingMapper resourceRatingMapper;

    @Autowired
    PortalConfigReader portalConfigReader;

    @Autowired
    ViewHistoryService viewHistoryService;

    @Override
    public PageInfo<ResourcePortalVo> search(ResourcePortalSearchQuarry quarry) {
        quarry.setUserViewLevel(resolveUserViewLevel());
        PageUtil.startPage();
        List<ResourcePortalVo> list = resourcePortalMapper.searchResources(quarry);
        // 列表设越级 locked 标记（LINK 越级置空 linkUrl 锁跳转）
        fillLockedForList(list, quarry.getUserViewLevel());
        return new PageInfo<>(list);
    }

    @Override
    public List<ResourcePortalVo> recommend(int size, Long excludeResourceId) {
        // 资源无标签/无用户偏好：推荐 feed 等价热度榜（排除 excludeResourceId）
        Integer userViewLevel = resolveUserViewLevel();
        List<ResourcePortalVo> list = resourcePortalMapper.recommendHot(excludeResourceId, Collections.emptyList(), userViewLevel, size);
        fillLockedForList(list, userViewLevel);
        return list;
    }

    @Override
    public ResourcePortalDetailVo getDetail(Long resourceId) {
        Integer userViewLevel = resolveUserViewLevel();
        ResourcePortalDetailVo vo = resourcePortalMapper.getPortalResourceDetail(resourceId);
        if (vo == null) {
            return null; // 不存在或非 PUBLISHED，前台 404 语义由 controller 处理
        }
        // 越级锁态（2026-08-18 权限大修）：level > userViewLevel → locked=true + lockReason +
        // description 仍下发（可见）+ LINK 置空 linkUrl（锁跳转）+ FILE 锁下载（本就不在详情预填，保持）。
        // 越级不计浏览量（未达权限不算统计量，与博客"越级不计浏览量"同构）。
        Integer level = vo.getLevel();
        if (level != null && level > userViewLevel) {
            vo.setLocked(true);
            vo.setLockReason("需 L" + level + " 权限查看/下载该资源");
            // LINK 类型越级置空 linkUrl 锁跳转（FILE 类型下载链接本就不在详情预填，无需处理）
            if ("LINK".equals(vo.getResourceType())) {
                vo.setLinkUrl(null);
            }
            // 越级仍回填当前用户互动态（点赞/收藏/评分态对访客有意义，与博客同构）
            fillCurrentUserInteract(vo);
            return vo;
        }
        // 达权：locked=false + 计浏览量（仅登录态计，未登录不计）
        vo.setLocked(false);
        vo.setLockReason(null);
        UserInfo user = currentUserOrNull();
        if (user != null) {
            viewHistoryService.recordView(user.getUserId(), ViewBizType.RESOURCE.getCode(), resourceId);
        }
        fillCurrentUserInteract(vo);
        return vo;
    }

    @Override
    public List<ResourcePortalVo> related(Long resourceId, int size) {
        Integer userViewLevel = resolveUserViewLevel();
        List<ResourcePortalVo> list = resourcePortalMapper.relatedResources(resourceId, userViewLevel, size);
        fillLockedForList(list, userViewLevel);
        return list;
    }

    /**
     * 前台"我的收藏"列表：取当前用户收藏资源ID（按收藏时间倒序），用 listByIds 同口径 SQL
     * 取"收藏 ∩ 前台可见(PUBLISHED AND level<=userViewLevel+1)"的 VO，再按收藏时间倒序的 resourceIds 顺序排。
     * 与 ArticlePortalService.listMyCollected 同构：portal 铁律 SQL 兜底可见性，未发布/已删自然被过滤。
     * 越级收藏资源进列表带 locked 标记（LINK 置空 linkUrl），与 search/recommend 同口径。
     * <p>
     * 2026-08-12 修正：原实现误调 recommendHot(全局热门 topN, size=收藏数)，再求交集——收藏资源不在
     * 全局热门 topN 里即被丢，导致资源收藏列表为空。改为 listByIds(IN 收藏 ID 集) 精确召回。
     */
    @Override
    public PageInfo<ResourcePortalVo> listMyCollected(int pageNum, int pageSize) {
        UserInfo user = currentUserOrNull();
        if (user == null) {
            return new PageInfo<>(Collections.emptyList());
        }
        Integer userViewLevel = resolveUserViewLevel();
        List<Long> resourceIds = resourceCollectMapper.listCollectedResourceIds(user.getUserId());
        if (resourceIds == null || resourceIds.isEmpty()) {
            return new PageInfo<>(Collections.emptyList());
        }
        // listByIds 取"收藏 ID 集 ∩ 前台可见"的 VO（前台铁律过滤未发布/已删/越级），不排序——
        // service 层按收藏时间倒序的 resourceIds 顺序拼装，还原"最近收藏在前"语义。
        List<ResourcePortalVo> all = resourcePortalMapper.listByIds(resourceIds, userViewLevel);
        fillLockedForList(all, userViewLevel);
        Map<Long, ResourcePortalVo> voMap = new HashMap<>();
        for (ResourcePortalVo vo : all) {
            voMap.put(vo.getResourceId(), vo);
        }
        List<ResourcePortalVo> ordered = new ArrayList<>();
        for (Long rid : resourceIds) {
            ResourcePortalVo vo = voMap.get(rid);
            if (vo != null) {
                ordered.add(vo);
            }
        }
        return new PageInfo<>(ordered);
    }

    // ============================ 私有辅助 ============================

    /**
     * 解析前台 userViewLevel（2026-08-18 权限大修，对齐博客 BlogPortalServiceImpl）：
     * 分级开关关 → 恒 1（二元闸，所有人只看 L1，搜索范围 <=2 搜 L1+L2 带 lock）；
     * 开 → max(1, ResourcePermissionResolver.resolve().level())（未登录/无权限 level=0→1 看 L1；有等级者看 L1~LN）。
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
        int level = ResourcePermissionResolver.resolve().level();
        return Math.max(1, level);
    }

    /**
     * 列表批量回填越级 locked 标记（2026-08-18 权限大修）：vo.level > userViewLevel → locked=true，
     * LINK 类型同时置空 linkUrl 锁跳转（FILE 类型下载链接本就不在列表下发，无需处理）。
     * 放在 service 层 for 循环（O(n) n=页大小，开销可忽略），不进 SQL 以保 where 片段纯净。
     */
    private void fillLockedForList(List<ResourcePortalVo> list, Integer userViewLevel) {
        if (list == null || list.isEmpty() || userViewLevel == null) {
            return;
        }
        for (ResourcePortalVo vo : list) {
            Integer level = vo.getLevel();
            if (level != null && level > userViewLevel) {
                vo.setLocked(true);
                if ("LINK".equals(vo.getResourceType())) {
                    vo.setLinkUrl(null); // 越级锁跳转
                }
            } else {
                vo.setLocked(false);
            }
        }
    }

    /**
     * 详情回填当前用户的点赞/收藏/评分态（登录态才有意义）。
     * 防御性取用户：未登录（principal 非 UserInfo）直接不回填，前端按 null 隐藏互动按钮或跳登录。
     */
    private void fillCurrentUserInteract(ResourcePortalDetailVo vo) {
        if (vo == null || vo.getResourceId() == null) {
            return;
        }
        UserInfo user = currentUserOrNull();
        if (user == null) {
            return; // 未登录不回填交互态
        }
        Long userId = user.getUserId();
        vo.setHasLiked(resourceLikeMapper.getResourceLike(new ResourceLike(vo.getResourceId(), userId)) != null);
        vo.setHasCollected(resourceCollectMapper.getResourceCollect(new ResourceCollect(vo.getResourceId(), userId)) != null);
        ResourceRating rating = resourceRatingMapper.getResourceRating(new ResourceRating(vo.getResourceId(), userId, 0));
        vo.setMyScore(rating != null ? rating.getScore() : 0);
    }

    /**
     * 防御性取当前登录用户：principal 是 UserInfo 才返回，否则 null（未登录/匿名）。
     * 前台 permitAll 区不能像后台那样直接强转（会 NPE）。
     */
    private UserInfo currentUserOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        Object p = auth.getPrincipal();
        return (p instanceof UserInfo) ? (UserInfo) p : null;
    }
}