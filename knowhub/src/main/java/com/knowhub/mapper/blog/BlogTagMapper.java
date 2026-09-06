package com.knowhub.mapper.blog;

import com.knowhub.pojo.blog.entity.BlogTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 博客-标签关联 Mapper（多对多中间表 blog_tag）。
 */
@Mapper
public interface BlogTagMapper {

    /** 批量插入博客-标签关联 */
    Boolean insertBlogTags(@Param("list") List<BlogTag> list);

    /** 删除某文章的全部标签关联（编辑时先删后插） */
    Boolean deleteBlogTagByBlogId(Long blogId);

    /** 删除某标签的全部关联（标签删除/禁用时级联清理） */
    Boolean deleteBlogTagByTagId(Long tagId);

    /** 查某文章的标签 id 列表 */
    List<Long> getTagIdsByBlogId(Long blogId);

    /** 批量查多篇文章的标签关联（列表回填标签用，避免 N+1） */
    List<BlogTag> getBlogTagsByBlogIds(@Param("blogIds") List<Long> blogIds);
}