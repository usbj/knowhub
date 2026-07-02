package com.knowhub.mapper;

import com.knowhub.pojo.entity.BlogLike;
import org.apache.ibatis.annotations.Mapper;

/**
 * 博客点赞明细 Mapper。
 */
@Mapper
public interface BlogLikeMapper {

    /** 点赞（插入 ignore），主键冲突视为已点赞 */
    Boolean addBlogLike(BlogLike blogLike);

    /** 取消点赞（删除） */
    Boolean deleteBlogLike(BlogLike blogLike);

    /** 查询某用户是否已点赞某文章 */
    BlogLike getBlogLike(BlogLike blogLike);
}