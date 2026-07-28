package com.knowhub.mapper.resource;

import com.knowhub.pojo.resource.entity.ResourceCollect;
import org.apache.ibatis.annotations.Mapper;

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
}
