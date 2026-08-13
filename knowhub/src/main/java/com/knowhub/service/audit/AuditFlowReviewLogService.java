package com.knowhub.service.audit;

import com.knowhub.pojo.audit.vo.AuditFlowReviewLogVo;

import java.util.List;

/**
 * 花销审批流水 Service。
 * 仅暴露查询（流水表只追加不改不删，无 insert 对外接口——写入由 AuditFundFlowService 审批动作内部调用 Mapper）。
 * 详情接口审核记录折叠区用 listByFlowId 查时间线。
 */
public interface AuditFlowReviewLogService {

    /** 审核历史（按 flowId 查审核流水时间线，升序还原轨迹） */
    List<AuditFlowReviewLogVo> listByFlowId(Long flowId);
}