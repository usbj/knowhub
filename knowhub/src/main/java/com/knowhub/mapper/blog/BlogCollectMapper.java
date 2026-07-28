package com.knowhub.mapper.blog;

import com.knowhub.pojo.blog.entity.BlogCollect;
import org.apache.ibatis.annotations.Mapper;

/**
 * 博客收藏明细 Mapper。结构与点赞对称。
 */
@Mapper
public interface BlogCollectMapper {

    /** 收藏（插入 ignore） */
    Boolean addBlogCollect(BlogCollect blogCollect);

    /** 取消收藏（删除） */
    Boolean deleteBlogCollect(BlogCollect blogCollect);

    /** 查询某用户是否已收藏某文章 */
    BlogCollect getBlogCollect(BlogCollect blogCollect);
}