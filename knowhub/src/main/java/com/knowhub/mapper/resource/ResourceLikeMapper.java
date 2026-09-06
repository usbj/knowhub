package com.knowhub.mapper.resource;

import com.knowhub.pojo.resource.entity.ResourceLike;
import org.apache.ibatis.annotations.Mapper;

/**
 * 资源点赞明细 Mapper。
 * 计数不冗余主表，走 COUNT(*) 聚合（Service 层调 ResourceMapper.countLikesByResourceIds）。
 */
@Mapper
public interface ResourceLikeMapper {

    /** 点赞（插入 ignore），主键冲突视为已点赞 */
    Boolean addResourceLike(ResourceLike resourceLike);

    /** 取消点赞（删除） */
    Boolean deleteResourceLike(ResourceLike resourceLike);

    /** 查询某用户是否已点赞某资源 */
    ResourceLike getResourceLike(ResourceLike resourceLike);
}
