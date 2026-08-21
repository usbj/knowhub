package com.knowhub.mapper.blog;

import com.knowhub.pojo.blog.quarry.BlogPortalSearchQuarry;
import com.knowhub.pojo.blog.vo.BlogPortalDetailVo;
import com.knowhub.pojo.blog.vo.BlogPortalVo;
import com.knowhub.pojo.blog.vo.PortalBlogStatsVo;
import com.knowhub.pojo.tag.vo.HotTagVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 前台博客门户 Mapper（/portal/blog/* 五读接口 + /portal/tag/hot）。
 * <p>
 * 铁律：所有 SQL 一律 where deleted=0 and status='PUBLISHED' and level &lt;= #{userViewLevel}
 * （userViewLevel 由 service 注入：分级开关关恒 1，开取 BlogPermissionResolver.view，未登录=1）。
 * L2/L3 永不下发前台（开关关时）。列表类 SQL 不 select content。
 */
@Mapper
public interface BlogPortalMapper {

    /** 搜索（全文+标签+作者复合过滤+排序，分页由 PageHelper 接管） */
    List<BlogPortalVo> searchBlogs(BlogPortalSearchQuarry quarry);

    /** 推荐：按偏好 tag 召回同 tag 公开博客（排除 excludeBlogId、排除已浏览），列表回 Service 层算分 */
    List<BlogPortalVo> recommendByTags(@Param("userViewLevel") Integer userViewLevel,
                                       @Param("tagIds") List<Long> tagIds,
                                       @Param("excludeBlogId") Long excludeBlogId,
                                       @Param("viewedBlogIds") List<Long> viewedBlogIds,
                                       @Param("size") int size);

    /** 兜底：全局热门（未登录/无行为/召回不足时），按 (like*2+collect*3+view) desc, publish_time desc */
    List<BlogPortalVo> recommendHot(@Param("userViewLevel") Integer userViewLevel,
                                     @Param("excludeBlogId") Long excludeBlogId,
                                     @Param("excludeBlogIds") List<Long> excludeBlogIds,
                                     @Param("size") int size);

    /**
     * 按 ID 集合取前台可见博客 VO（前台铁律过滤：deleted=0 AND status='PUBLISHED' AND level<=userViewLevel）。
     * 供"我的收藏"列表用：service 传收藏博客 ID 集，取"收藏 ∩ 前台可见"全量 VO，再按收藏时间倒序排。
     */
    List<BlogPortalVo> listByIds(@Param("userViewLevel") Integer userViewLevel,
                                  @Param("blogIds") List<Long> blogIds);

    /** 详情元数据（含 level，不含 content，无 level 过滤——service 据此判越级锁态） */
    BlogPortalDetailVo getPortalBlogMeta(@Param("blogId") Long blogId);

    /** 取正文（仅 service 判定达权后调用，避免越级时白拉 longtext） */
    String getBlogContent(@Param("blogId") Long blogId);

    /** 相关推荐（同 tag，排除自身，按热度） */
    List<BlogPortalVo> relatedBlogs(@Param("blogId") Long blogId,
                                     @Param("userViewLevel") Integer userViewLevel,
                                     @Param("size") int size);

    /** 批量回填列表标签（防 N+1，照 blog_tag getBlogTagsByBlogIds 范式） */
    List<Map<String, Object>> getTagIdsByBlogIds(@Param("blogIds") List<Long> blogIds);

    /**
     * 标签热度榜：统计 blog_tag + article_tag 关联的 PUBLISHED+公开内容数 + 总热度。
     * 两类分支均按门户口径 level<=userViewLevel+1 过滤（与列表/统计同源，越级内容也计入）。
     * 注意：文章用 level 而非 visibility——visibility 是章节创作状态机字段，非门户可见性。
     */
    List<HotTagVo> hotTags(@Param("size") int size, @Param("userViewLevel") int userViewLevel);

    /**
     * 用户偏好 tag：blog_like ∪ blog_collect 反推 tag 频次（collect*3 + like*1 聚合）
     * + 浏览过的文章 tag（user_view_history biz_type=ARTICLE 关联 article_tag）。
     * 取 Top-N tagId 供推荐召回。未登录/无行为返回空列表。
     */
    List<Long> preferTagsByUser(@Param("userId") Long userId, @Param("topN") int topN);

    /** 用户浏览过的博客ID列表（推荐召回时排除已浏览，未登录返回空） */
    List<Long> viewedBlogIdsByUser(@Param("userId") Long userId);

    /**
     * 前台博客全量统计（/portal/blog/stats 出参）：已发布且越权过滤后的博客数 + 这些博客的累计阅读数 + 启用标签总数。
     * 固定口径，与搜索/翻页/标签过滤无关。totalReads 走 sum(b.view_count) 应用博客铁律。
     */
    PortalBlogStatsVo getPortalStats(@Param("userViewLevel") Integer userViewLevel);
}
