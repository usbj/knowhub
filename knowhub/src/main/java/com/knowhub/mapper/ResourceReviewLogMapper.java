package com.knowhub.mapper;

import com.knowhub.pojo.entity.ResourceReviewLog;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 资源审核流水 Mapper。
 * 仅 insert（追加）+ 按 resource_id 查历史（升序，供前台时间线/后台审核记录展示）。
 * 流水表不改不删，故无 update/delete 方法。结构与 BlogReviewLogMapper 同构。
 */
@Mapper
public interface ResourceReviewLogMapper {

    /** 追加一条审核流水（动作时间由 DB 默认 CURRENT_TIMESTAMP 填充） */
    Boolean insertReviewLog(ResourceReviewLog log);

    /** 按资源ID查审核历史（按动作时间升序，还原轨迹） */
    List<ResourceReviewLog> listByResourceId(Long resourceId);
}
