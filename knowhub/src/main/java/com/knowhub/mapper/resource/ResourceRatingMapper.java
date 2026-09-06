package com.knowhub.mapper.resource;

import com.knowhub.pojo.resource.entity.ResourceRating;
import org.apache.ibatis.annotations.Mapper;

/**
 * 资源评分明细 Mapper。
 * 一人一资源可改分（UNIQUE(resource_id,user_id) 支撑 upsert）。
 * 评分均值/计数不冗余主表，走 AVG(score)/COUNT(*) 聚合（Service 层调 ResourceMapper.ratingStatsByResourceIds）。
 */
@Mapper
public interface ResourceRatingMapper {

    /** 查询某用户对某资源的评分（存在则改分用 update，不存在则 insert） */
    ResourceRating getResourceRating(ResourceRating resourceRating);

    /** 新增评分 */
    Boolean addResourceRating(ResourceRating resourceRating);

    /** 更新评分（改分） */
    Boolean updateResourceRating(ResourceRating resourceRating);
}
