package com.knowhub.mapper;

import com.knowhub.pojo.entity.ArticleTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 文章-标签关联 Mapper（多对多中间表 article_tag，照搬 blog_tag 范式）。
 * 文章复用既有 tag 表，仅建文章侧关联；编辑文章时先删后插重建。
 */
@Mapper
public interface ArticleTagMapper {

    /** 批量插入文章-标签关联 */
    Boolean insertArticleTags(@Param("list") List<ArticleTag> list);

    /** 删除某文章的全部标签关联（编辑时先删后插） */
    Boolean deleteArticleTagByArticleId(Long articleId);

    /** 删除某标签的全部关联（标签删除/禁用时级联清理） */
    Boolean deleteArticleTagByTagId(Long tagId);

    /** 查某文章的标签 id 列表 */
    List<Long> getTagIdsByArticleId(Long articleId);

    /** 批量查多篇文章的标签关联（列表回填标签用，避免 N+1） */
    List<ArticleTag> getArticleTagsByArticleIds(@Param("articleIds") List<Long> articleIds);
}
