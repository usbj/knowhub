package com.knowhub.mapper.article;

import com.knowhub.pojo.article.entity.ArticleLike;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文章点赞明细 Mapper。照搬 blog_like 范式，结构与 ArticleCollect 对称。
 */
@Mapper
public interface ArticleLikeMapper {

    /** 点赞（插入 ignore） */
    Boolean addArticleLike(ArticleLike articleLike);

    /** 取消点赞（删除） */
    Boolean deleteArticleLike(ArticleLike articleLike);

    /** 查询某用户是否已点赞某文章 */
    ArticleLike getArticleLike(ArticleLike articleLike);
}