package com.knowhub.mapper.audit;

import com.knowhub.pojo.audit.entity.AuditFlowReviewLog;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 花销审批流水 Mapper。
 * 仅 insert（追加）+ 按 flow_id 查历史（升序，供详情折叠区展示）。
 * 流水表不改不删，故无 update/delete 方法（照 blog_review_log/resource_review_log 范式）。
 */
@Mapper
public interface AuditFlowReviewLogMapper {

    /** 追加一条审核流水（动作时间由 DB 默认 CURRENT_TIMESTAMP 填充） */
    Boolean insertAuditFlowReviewLog(AuditFlowReviewLog log);

    /** 按流水ID查审核历史（按动作时间升序，还原轨迹） */
    List<AuditFlowReviewLog> listByFlowId(Long flowId);
}