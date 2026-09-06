package com.knowhub.service.audit;

import com.knowhub.pojo.audit.vo.AuditFlowReviewLogVo;
import com.knowhub.mapper.audit.AuditFlowReviewLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 花销审批流水 Service 实现。
 * 仅 listByFlowId 透传 Mapper 查询（join sys_user 带出 operatorNickname）。
 * 流水写入在 AuditFundFlowService.writeReviewLog 内部直接调 Mapper，不经本 Service，
 * 避免循环依赖（AuditFundFlowService 注入本 Service 会成环）。
 * 本 Service 仅服务于独立的审核历史查询入口（详情折叠区可复用 AuditFundFlowService.listReviewLog，
 * 两者等价，Controller 可任选；保留本 Service 为后续借出/报表模块复用提供稳定入口）。
 */
@Service
public class AuditFlowReviewLogServiceImpl implements AuditFlowReviewLogService {

    @Autowired
    AuditFlowReviewLogMapper auditFlowReviewLogMapper;

    @Override
    public List<AuditFlowReviewLogVo> listByFlowId(Long flowId) {
        // 走 AuditFlowReviewLogMapper.listByFlowId 取实体（join sys_user 带出 operatorNickname），
        // 再拷贝到 VO。AuditFundFlowService.listReviewLog 走的是 AuditFundFlowMapper.listReviewLogByFlowId 直投 VO，
        // 两者等价；保留本方法为借出/报表模块提供独立查询入口，避免对 FlowMapper 的跨模块耦合。
        // （本批次未建借出/报表模块，本方法当前由 AuditController 审核历史接口复用，与 FlowService.listReviewLog 共存。）
        List<com.knowhub.pojo.audit.entity.AuditFlowReviewLog> logs = auditFlowReviewLogMapper.listByFlowId(flowId);
        if (logs == null || logs.isEmpty()) {
            return new ArrayList<>();
        }
        List<AuditFlowReviewLogVo> voList = new ArrayList<>(logs.size());
        for (com.knowhub.pojo.audit.entity.AuditFlowReviewLog log : logs) {
            AuditFlowReviewLogVo vo = new AuditFlowReviewLogVo();
            vo.setReviewLogId(log.getReviewLogId());
            vo.setFlowId(log.getFlowId());
            vo.setAction(log.getAction());
            vo.setOperatorId(log.getOperatorId());
            vo.setOperator(log.getOperator());
            vo.setOperatorNickname(log.getOperatorNickname());
            vo.setRole(log.getRole());
            vo.setAdvice(log.getAdvice());
            vo.setCreateTime(log.getCreateTime());
            voList.add(vo);
        }
        return voList;
    }
}