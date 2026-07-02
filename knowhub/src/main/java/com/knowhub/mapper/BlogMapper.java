package com.knowhub.mapper;

import com.knowhub.pojo.quarry.BlogQuarry;
import com.knowhub.pojo.entity.Blog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 博客文章 Mapper。
 * 列表查询支持 fulltext(keyword) + 标签(tagIds) 复合筛选，详见 BlogMapper.xml。
 */
@Mapper
public interface BlogMapper {

    /** 列表查询（PageHelper 在 Service 层 startPage 拦截） */
    List<Blog> quarryBlog(BlogQuarry quarry);

    /** 详情：按主键取未删除文章 */
    Blog getBlogInfoById(Long blogId);

    /** 新增文章，回填主键 */
    Boolean addBlog(Blog blog);

    /** 编辑文章（动态列） */
    Boolean editBlogInfo(Blog blog);

    /** 软删文章 */
    Boolean softDeleteBlog(Long blogId);

    /** 浏览量 +n（Redis 累加后批量回写时调用） */
    Boolean incrViewCount(@Param("blogId") Long blogId, @Param("delta") long delta);

    /** 点赞量 +1/-1 */
    Boolean incrLikeCount(@Param("blogId") Long blogId, @Param("delta") long delta);

    /** 收藏量 +1/-1 */
    Boolean incrCollectCount(@Param("blogId") Long blogId, @Param("delta") long delta);
}