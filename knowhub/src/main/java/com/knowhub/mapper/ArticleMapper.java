package com.knowhub.mapper;

import com.knowhub.pojo.entity.Article;
import com.knowhub.pojo.quarry.ArticleQuarry;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 文章主表 Mapper。
 * 列表查询带 author_nickname(join sys_user on author_id)；权限过滤透传 userViewLevel/userId。
 * 列表查询不带 summary 大字段外的正文（文章主表本就不存正文，正文在 chapter），
 * summary 是前言可预览故列表带出。
 */
@Mapper
public interface ArticleMapper {

    /**
     * 列表查询（PageHelper 在 Service 层 startPage 拦截）。
     * 权限过滤（文章无成员表，轻量模型）：
     *   level <= userViewLevel OR author_id = userId（作者始终能看自己的文章，不看等级）
     * 返回行带 author_nickname（join sys_user），由 resultMap 映射。
     */
    List<Article> quarryArticle(ArticleQuarry quarry);

    /** 详情：按主键取未删除文章（带 author_nickname + summary 前言，文章主表无正文） */
    Article getArticleInfoById(Long articleId);

    /** 新增文章，回填主键 */
    Boolean addArticle(Article article);

    /** 编辑文章（动态列） */
    Boolean editArticleInfo(Article article);

    /** 软删文章 */
    Boolean softDeleteArticle(Long articleId);

    /** 对账用：查所有处于待审核且未删除的文章 ID（审核开关关闭后定时任务批量放行） */
    List<Long> listPendingReviewIds();
}
