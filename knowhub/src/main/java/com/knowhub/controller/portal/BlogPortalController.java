package com.knowhub.controller;

import com.github.pagehelper.PageInfo;
import com.knowhub.pojo.quarry.BlogPortalSearchQuarry;
import com.knowhub.pojo.vo.BlogPortalDetailVo;
import com.knowhub.pojo.vo.BlogPortalVo;
import com.knowhub.pojo.vo.HotTagVo;
import com.knowhub.pojo.vo.TagOptionVo;
import com.knowhub.service.BlogPortalService;
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
 * 前台博客门户公开接口（/portal/blog/* + /portal/tag/hot，全部无 @PreAuthorize，走 /portal/** permitAll）。
 * 铁律：所有 SQL 一律 status=PUBLISHED AND level<=userViewLevel（分级开关关恒 1），L2/L3 永不下发前台。
 */
@Tag(name = "博客门户", description = "前台公开搜索/推荐/详情/相关推荐/标签榜")
@RestController
@RequestMapping("/portal")
public class BlogPortalController {

    @Autowired
    BlogPortalService blogPortalService;

    @GetMapping("/blog/search")
    @Operation(summary = "前台博客搜索（全文+标签/作者复合过滤+排序，分页）")
    public Result<PageInfo<BlogPortalVo>> search(BlogPortalSearchQuarry quarry) {
        PageInfo<BlogPortalVo> page = blogPortalService.search(quarry);
        return Result.success(page);
    }

    @GetMapping("/blog/recommend")
    @Operation(summary = "前台博客个性化推荐 feed")
    public Result<List<BlogPortalVo>> recommend(
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long excludeBlogId) {
        List<BlogPortalVo> list = blogPortalService.recommend(size, excludeBlogId);
        return Result.success(list);
    }

    @GetMapping("/blog/{blogId}")
    @Operation(summary = "前台博客详情（越级锁态降级，不下发正文）")
    public Result<BlogPortalDetailVo> getDetail(@PathVariable Long blogId) {
        BlogPortalDetailVo vo = blogPortalService.getDetail(blogId);
        return Result.success(vo);
    }

    @GetMapping("/blog/{blogId}/related")
    @Operation(summary = "详情页相关推荐")
    public Result<List<BlogPortalVo>> related(@PathVariable Long blogId,
                                              @RequestParam(defaultValue = "10") int size) {
        List<BlogPortalVo> list = blogPortalService.related(blogId, size);
        return Result.success(list);
    }

    @GetMapping("/tag/hot")
    @Operation(summary = "标签热度榜（统计 blog_tag + article_tag）")
    public Result<List<HotTagVo>> hotTags(@RequestParam(defaultValue = "20") int size) {
        List<HotTagVo> list = blogPortalService.hotTags(size);
        return Result.success(list);
    }

    @GetMapping("/tag/list")
    @Operation(summary = "全部启用标签（创作页标签选择器数据源，仅 tagId+tagName）")
    public Result<List<TagOptionVo>> listEnabledTags() {
        List<TagOptionVo> list = blogPortalService.listEnabledTags();
        return Result.success(list);
    }
}
