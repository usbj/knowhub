package com.knowhub.mapper.blog;

import com.knowhub.pojo.blog.entity.BlogCollect;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

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

    /** 查当前用户的收藏博客 ID 列表（按收藏时间倒序，service 据此 join 出 VO） */
    List<Long> listCollectedBlogIds(@Param("userId") Long userId);
}