package com.knowhub.controller.portal;

import com.github.pagehelper.PageInfo;
import com.knowhub.pojo.article.quarry.ArticlePortalSearchQuarry;
import com.knowhub.pojo.article.vo.ArticlePortalDetailVo;
import com.knowhub.pojo.article.vo.ArticlePortalVo;
import com.knowhub.pojo.article.vo.ChapterContentVo;
import com.knowhub.pojo.article.vo.PortalArticleStatsVo;
import com.knowhub.service.article.impl.ArticlePortalService;
import com.rookie.common.pojo.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 前台文章门户公开接口（/portal/article/*，全部无 @PreAuthorize，走 /portal/** permitAll）。
 * 照搬 BlogPortalController 范式。铁律：所有 SQL 一律 status='PUBLISHED' AND level<=userViewLevel
 * （分级开关关恒 1），L2/L3 永不下发前台；文章正文在 chapter，详情只给大纲，正文走章节接口按需拉取。
 * 标签热度榜不在本 controller（复用既有 /portal/tag/hot，跨 blog+article 统一聚合）。
 */
@Tag(name = "文章门户", description = "前台公开搜索（含章节内容）/推荐/详情/章节正文/相关推荐")
@RestController
@RequestMapping("/portal/article")
public class ArticlePortalController {

    @Autowired
    ArticlePortalService articlePortalService;

    @GetMapping("/search")
    @Operation(summary = "前台文章搜索（标题/简介/章节正文全检索+标签/作者复合过滤+排序，命中章节标出，分页）")
    public Result<PageInfo<ArticlePortalVo>> search(ArticlePortalSearchQuarry quarry) {
        PageInfo<ArticlePortalVo> page = articlePortalService.search(quarry);
        return Result.success(page);
    }

    @GetMapping("/stats")
    @Operation(summary = "前台文库全量统计（已发布文档数+章节数+标签数，固定口径，不受搜索/过滤影响）")
    public Result<PortalArticleStatsVo> getStats() {
        return Result.success(articlePortalService.getStats());
    }

    @GetMapping("/recommend")
    @Operation(summary = "前台文章个性化推荐 feed（标签命中×5+收藏×3+点赞×2+浏览×1+时间衰减多维打分）")
    public Result<List<ArticlePortalVo>> recommend(
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long excludeArticleId) {
        List<ArticlePortalVo> list = articlePortalService.recommend(size, excludeArticleId);
        return Result.success(list);
    }

    @GetMapping("/{articleId}")
    @Operation(summary = "前台文章详情（章节大纲+越级锁态降级，正文走章节接口）")
    public Result<ArticlePortalDetailVo> getDetail(@PathVariable Long articleId) {
        ArticlePortalDetailVo vo = articlePortalService.getDetail(articleId);
        return Result.success(vo);
    }

    @GetMapping("/{articleId}/chapter/{chapterId}")
    @Operation(summary = "前台章节正文（达权下发正文+计章节浏览量，越级锁态置空正文）")
    public Result<ChapterContentVo> getChapterContent(@PathVariable Long articleId,
                                                       @PathVariable Long chapterId) {
        ChapterContentVo vo = articlePortalService.getChapterContent(articleId, chapterId);
        return Result.success(vo);
    }

    @GetMapping("/{articleId}/related")
    @Operation(summary = "详情页相关推荐")
    public Result<List<ArticlePortalVo>> related(@PathVariable Long articleId,
                                                  @RequestParam(defaultValue = "10") int size) {
        List<ArticlePortalVo> list = articlePortalService.related(articleId, size);
        return Result.success(list);
    }
}