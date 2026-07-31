package com.knowhub.mapper.article;

import com.knowhub.pojo.article.quarry.ArticlePortalSearchQuarry;
import com.knowhub.pojo.article.vo.ArticlePortalDetailVo;
import com.knowhub.pojo.article.vo.ArticlePortalVo;
import com.knowhub.pojo.article.vo.ChapterContentVo;
import com.knowhub.pojo.article.vo.ChapterOutlineVo;
import com.knowhub.pojo.article.vo.MatchedChapterVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 文章前台门户 Mapper。照搬 BlogPortalMapper 范式，全 SQL 带 deleted=0 AND status='PUBLISHED' AND level<=userViewLevel（前台铁律）。
 * 列表类 SQL 不 select chapter.content；章节正文单独走 chapter 专用查询。封面 URL 通过 file_object ARTICLE_COVER 反查拼接。
 */
@Mapper
public interface ArticlePortalMapper {

    /** 搜索：全文(标题/简介)+章节正文命中+标签/作者复合过滤，分页由 PageHelper 接管 */
    List<ArticlePortalVo> searchArticles(ArticlePortalSearchQuarry quarry);

    /** 按偏好 tag 召回同 tag 公开文章（排除 excludeArticleId、排除已浏览 viewedArticleIds），limit size */
    List<ArticlePortalVo> recommendByTags(@Param("userViewLevel") Integer userViewLevel,
                                          @Param("tagIds") List<Long> tagIds,
                                          @Param("excludeArticleId") Long excludeArticleId,
                                          @Param("viewedArticleIds") List<Long> viewedArticleIds,
                                          @Param("size") int size);

    /** 兜底全局热门文章（按热度，排除 excludeArticleIds），limit need */
    List<ArticlePortalVo> recommendHot(@Param("userViewLevel") Integer userViewLevel,
                                       @Param("excludeArticleId") Long excludeArticleId,
                                       @Param("excludeArticleIds") List<Long> excludeArticleIds,
                                       @Param("size") int size);

    /** 相关推荐：同 tag 文章排除自身，按热度，limit size */
    List<ArticlePortalVo> relatedArticles(@Param("articleId") Long articleId,
                                          @Param("userViewLevel") Integer userViewLevel,
                                          @Param("size") int size);

    /** 文章详情元数据（含 level，不含正文；仅 PUBLISHED，无 level 过滤，service 据此判越级锁态） */
    ArticlePortalDetailVo getPortalArticleMeta(@Param("articleId") Long articleId);

    /** 文章章节大纲（点章节跳阅读页；不含正文） */
    List<ChapterOutlineVo> listChapterOutline(@Param("articleId") Long articleId);

    /** 章节正文查询（校验 article PUBLISHED + chapter PUBLISHED，带 article level 供 service 判越级；不带正文越级锁态字段） */
    ChapterContentVo getChapterContent(@Param("articleId") Long articleId,
                                       @Param("chapterId") Long chapterId);

    /** 搜索命中章节（对给定的 articleId 集合，查 keyword 在 chapter.content 命中的章节，按 article 聚合） */
    List<MatchedChapterVo> matchedChapters(@Param("articleIds") List<Long> articleIds,
                                            @Param("keyword") String keyword);

    /** 批量回填列表标签 id（防 N+1，返回 {article_id, tag_id} 列表，service 层分组拼 tagIds/tagNames） */
    List<Map<String, Object>> getTagIdsByArticleIds(@Param("articleIds") List<Long> articleIds);

    /** 用户偏好 tag：article_collect(×3)+article_like(×1)+浏览过的文章 tag(×1) 反推 Top-N tagId */
    List<Long> preferTagsByUser(@Param("userId") Long userId, @Param("topN") int topN);

    /** 用户浏览过的文章ID（推荐召回排除已浏览） */
    List<Long> viewedArticleIdsByUser(@Param("userId") Long userId);

    /** 批量回填章节数量（防 N+1，service 层分组） */
    List<Map<String, Object>> getChapterCountByArticleIds(@Param("articleIds") List<Long> articleIds);
}