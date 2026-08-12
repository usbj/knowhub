package com.knowhub.mapper.project;

import com.knowhub.pojo.project.entity.ProjectCollect;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 项目收藏明细 Mapper。照搬 article_collect/blog_collect 范式，结构与 ArticleCollectMapper 对称。
 */
@Mapper
public interface ProjectCollectMapper {

    /** 收藏（插入 ignore） */
    Boolean addProjectCollect(ProjectCollect projectCollect);

    /** 取消收藏（删除） */
    Boolean deleteProjectCollect(ProjectCollect projectCollect);

    /** 查询某用户是否已收藏某项目 */
    ProjectCollect getProjectCollect(ProjectCollect projectCollect);

    /** 查当前用户的收藏项目 ID 列表（按收藏时间倒序，service 据此 join 出 VO） */
    List<Long> listCollectedProjectIds(@Param("userId") Long userId);
}