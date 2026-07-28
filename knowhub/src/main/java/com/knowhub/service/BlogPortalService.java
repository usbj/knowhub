package com.knowhub.service;

import com.github.pagehelper.PageInfo;
import com.knowhub.pojo.quarry.BlogPortalSearchQuarry;
import com.knowhub.pojo.vo.BlogPortalDetailVo;
import com.knowhub.pojo.vo.BlogPortalVo;
import com.knowhub.pojo.vo.HotTagVo;
import com.knowhub.pojo.vo.TagOptionVo;

import java.util.List;

/**
 * 前台博客门户 Service（/portal/blog/* 五读接口的业务逻辑集中在此）。
 * 接口只暴露 DTO，不暴露实体。前台无 @PreAuthorize，登录态在 service 内防御性获取。
 */
public interface BlogPortalService {

    /** 全文搜索+复合过滤+排序，分页 */
    PageInfo<BlogPortalVo> search(BlogPortalSearchQuarry quarry);

    /**
     * 个性化推荐 feed（登录用户按偏好 tag，未登录/无行为/召回不足走全局热门兜底）。
     * @param size 召回条数
     * @param excludeBlogId 排除的博客ID（详情页相关推荐时排除当前；feed 可为 null）
     */
    List<BlogPortalVo> recommend(int size, Long excludeBlogId);

    /** 前台公开详情（含正文/越级锁态降级） */
    BlogPortalDetailVo getDetail(Long blogId);

    /** 详情页相关推荐 */
    List<BlogPortalVo> related(Long blogId, int size);

    /** 标签热度榜（统计 blog_tag + article_tag） */
    List<HotTagVo> hotTags(int size);

    /** 全部启用标签（创作页标签选择器数据源，仅 tagId+tagName，无热度） */
    List<TagOptionVo> listEnabledTags();
}
