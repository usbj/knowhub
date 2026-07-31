package com.knowhub.mapper.article;

import com.knowhub.pojo.article.entity.ArticleCollect;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 文章收藏明细 Mapper。照搬 blog_collect 范式，结构与 ArticleLike 对称。
 */
@Mapper
public interface ArticleCollectMapper {

    /** 收藏（插入 ignore） */
    Boolean addArticleCollect(ArticleCollect articleCollect);

    /** 取消收藏（删除） */
    Boolean deleteArticleCollect(ArticleCollect articleCollect);

    /** 查询某用户是否已收藏某文章 */
    ArticleCollect getArticleCollect(ArticleCollect articleCollect);

    /** 查当前用户的收藏文章ID列表（分页用，父层 join article 带出） */
    java.util.List<Long> listCollectedArticleIds(@Param("userId") Long userId);
}