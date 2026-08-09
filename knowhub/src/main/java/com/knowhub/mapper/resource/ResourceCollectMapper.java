package com.knowhub.mapper.resource;

import com.knowhub.pojo.resource.entity.ResourceCollect;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 资源收藏明细 Mapper。结构与 ResourceLikeMapper 对称。
 * 计数不冗余主表，走 COUNT(*) 聚合（Service 层调 ResourceMapper.countCollectsByResourceIds）。
 */
@Mapper
public interface ResourceCollectMapper {

    /** 收藏（插入 ignore），主键冲突视为已收藏 */
    Boolean addResourceCollect(ResourceCollect resourceCollect);

    /** 取消收藏（删除） */
    Boolean deleteResourceCollect(ResourceCollect resourceCollect);

    /** 查询某用户是否已收藏某资源 */
    ResourceCollect getResourceCollect(ResourceCollect resourceCollect);

    /**
     * 取某用户已收藏的资源ID列表，按收藏时间倒序（前台"我的收藏"列表用，配合 portal 详情/列表 SQL 取可见资源 VO）。
     * 与 ArticleCollectMapper.listCollectedArticleIds 同口径。
     */
    List<Long> listCollectedResourceIds(@Param("userId") Long userId);
}
