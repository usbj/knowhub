package com.knowhub.service.article.impl;

import com.github.pagehelper.PageInfo;
import com.knowhub.pojo.article.quarry.ArticlePortalSearchQuarry;
import com.knowhub.pojo.article.vo.ArticlePortalDetailVo;
import com.knowhub.pojo.article.vo.ArticlePortalVo;
import com.knowhub.pojo.article.vo.ChapterContentVo;
import com.knowhub.pojo.article.vo.PortalArticleStatsVo;

import java.util.List;

/**
 * 前台文章门户 Service（/portal/article/* 读接口 + /authoring/article 点赞收藏 toggle 业务逻辑集中在此）。
 * 照搬 BlogPortalService 范式：接口只暴露 DTO，不暴露实体；前台读无 @PreAuthorize，
 * 登录态在 service 内防御性获取（principal 非 UserInfo 视为未登录）。
 * 标签热度榜不在此处（复用既有 /portal/tag/hot，由 BlogPortalService.hotTags 统一聚合 blog+article）。
 */
public interface ArticlePortalService {

    /** 全文搜索（标题/简介/章节正文）+复合过滤+排序，分页；命中章节由 matchedChapters 标出 */
    PageInfo<ArticlePortalVo> search(ArticlePortalSearchQuarry quarry);

    /**
     * 前台文库全量统计（/portal/article/stats）：已发布且当前用户可见的文章数 + 这些文章下的已发布章节数 + 启用标签总数。
     * 固定口径，与搜索/翻页/标签过滤无关——前端进入页面拉一次定盘展示，避免统计随筛选结果 total 变动。
     */
    PortalArticleStatsVo getStats();

    /**
     * 个性化推荐 feed（登录用户按偏好 tag，未登录/无行为/召回不足走全局热门兜底）。
     * @param size 召回条数
     * @param excludeArticleId 排除的文章ID（详情页相关推荐排除当前；feed 可为 null）
     */
    List<ArticlePortalVo> recommend(int size, Long excludeArticleId);

    /** 前台公开详情（越级锁态降级，不下发章节正文；章节大纲仍给） */
    ArticlePortalDetailVo getDetail(Long articleId);

    /** 详情页相关推荐 */
    List<ArticlePortalVo> related(Long articleId, int size);

    /** 章节正文（达权下发正文+计章节浏览量，越级锁态置空正文），登录态计浏览量（未登录不计） */
    ChapterContentVo getChapterContent(Long articleId, Long chapterId);

    /** 收藏/取消收藏（登录态，事务内 upsert 事实表 + 主表 collect_count 同步） */
    Boolean toggleCollect(Long articleId, Boolean collected);

    /** 点赞/取消点赞（登录态，事务内 upsert 事实表 + 主表 like_count 同步） */
    Boolean toggleLike(Long articleId, Boolean liked);

    /** 我的文章收藏列表（登录态，分页返回，命中前台可见口径的已发布文章） */
    PageInfo<ArticlePortalVo> listMyCollected(int pageNum, int pageSize);
}