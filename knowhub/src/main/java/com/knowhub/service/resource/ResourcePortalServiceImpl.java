package com.knowhub.service.resource;

import com.github.pagehelper.PageInfo;
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
 * 资源无 level 等级概念（与博客/文章 BlogPortalServiceImpl 的差异点）：无越级锁态，非 PUBLISHED 资源
 * 前台根本不下发（详情查不到返 null→controller 404）。
 * <p>
 * 推荐口径：资源无标签体系、无用户偏好源（unlike blog），推荐 feed 直接退化为全局热门兜底（按热度排）。
 * <p>
 * 互动计数由 mapper SQL inline 子查询回填（资源主表刻意不冗余 like/collect/rating 计数），
 * service 层仅回填当前用户互动态（hasLiked/hasCollected/myScore，登录态）+ FILE 下载链接（不计 download_count）。
 * 浏览量 = 主表 view_count 冗余列（统一浏览历史回写），service 仅在登录态调 recordView 触发首次 +1。
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
    ViewHistoryService viewHistoryService;

    @Override
    public PageInfo<ResourcePortalVo> search(ResourcePortalSearchQuarry quarry) {
        PageUtil.startPage();
        List<ResourcePortalVo> list = resourcePortalMapper.searchResources(quarry);
        return new PageInfo<>(list);
    }

    @Override
    public List<ResourcePortalVo> recommend(int size, Long excludeResourceId) {
        // 资源无标签/无用户偏好：推荐 feed 等价热度榜（排除 excludeResourceId）
        return resourcePortalMapper.recommendHot(excludeResourceId, Collections.emptyList(), size);
    }

    @Override
    public ResourcePortalDetailVo getDetail(Long resourceId) {
        ResourcePortalDetailVo vo = resourcePortalMapper.getPortalResourceDetail(resourceId);
        if (vo == null) {
            return null; // 不存在或非 PUBLISHED，前台 404 语义由 controller 处理
        }
        // 登录态回填当前用户互动态（hasLiked/hasCollected/myScore）
        fillCurrentUserInteract(vo);
        // FILE 下载链接回填（不计 download_count——下载点击才 +1）
        fillDownloadUrl(vo);
        // 登录态计浏览量（未登录不计，对齐第二条链路决策#3"未登录不计浏览量"）
        UserInfo user = currentUserOrNull();
        if (user != null) {
            viewHistoryService.recordView(user.getUserId(), ViewBizType.RESOURCE.getCode(), resourceId);
        }
        return vo;
    }

    @Override
    public List<ResourcePortalVo> related(Long resourceId, int size) {
        return resourcePortalMapper.relatedResources(resourceId, size);
    }

    /**
     * 前台"我的收藏"列表：取当前用户收藏资源ID（按收藏时间倒序），用 recommendHot 同口径 SQL
     * 取"收藏 ∩ 前台可见(PUBLISHED)"的 VO，再按收藏顺序排。
     * 与 ArticlePortalService.listMyCollected 同构：portal 铁律 SQL 兜底可见性，未发布/已删自然被过滤。
     */
    @Override
    public PageInfo<ResourcePortalVo> listMyCollected(int pageNum, int pageSize) {
        UserInfo user = currentUserOrNull();
        if (user == null) {
            return new PageInfo<>(Collections.emptyList());
        }
        List<Long> resourceIds = resourceCollectMapper.listCollectedResourceIds(user.getUserId());
        if (resourceIds == null || resourceIds.isEmpty()) {
            return new PageInfo<>(Collections.emptyList());
        }
        // 用 recommendHot 取"收藏 ID 集 ∩ 前台可见"的全量 VO（按热度 SQL 但 limit 放到收藏数），
        // 再按收藏时间倒序的 resourceIds 顺序排——还原"最近收藏在前"。
        List<ResourcePortalVo> all = resourcePortalMapper.recommendHot(
                null, Collections.emptyList(), resourceIds.size());
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
     * 详情仅回填已从 file_object 带出的 originalName/contentLength/contentType（mapper inline 已查），
     * 不在此调 fileService.getDownloadUrl 取下载链接。
     * <p>
     * 关键：fileService.getDownloadUrl 对 PRIVATE 文件会 checkOwnerOrAdmin→currentUser() 强转 principal 为
     * UserInfo；/portal/resource/** 是 permitAll，匿名访问时 principal 是 "anonymousUser"（String），强转即 CCE。
     * 该方法本只为带鉴权的下载场景设计，不应在 permitAll 的前台详情里预填下载 URL。
     * <p>
     * FILE 的真实下载链接在用户点"下载资源"按钮时现取：走 /authoring/resource/{id}/download（isAuthenticated 兜底，
     * principal 是 UserInfo），即 ResourceServiceImpl.downloadResource——它已校验 PUBLISHED+FILE 资源可见性，
     * 调 fileService.getDownloadUrl(fileObjectId, true)（bizAuthorized=true 跳过文件底座 owner 闸），并带 download_count +1
     * 业务语义。详情只负责展示，不预下发下载地址。
     */
    private void fillDownloadUrl(ResourcePortalDetailVo vo) {
        // no-op：originalName/contentLength 已由 mapper inline 带出；下载链接不在详情预填，避免 permitAll 区触发强转。
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