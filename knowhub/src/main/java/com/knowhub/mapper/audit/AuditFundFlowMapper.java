package com.knowhub.mapper.audit;

import com.knowhub.pojo.audit.entity.AuditFundFlow;
import com.knowhub.pojo.audit.quarry.AuditFundFlowQuarry;
import com.knowhub.pojo.audit.vo.AuditFlowReviewLogVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 资金流水 Mapper。
 * 列表/详情查询带 subject_name(join audit_subject on subject_id) + handler_nickname(join sys_user on handler_id)。
 * categoryLabel 字典翻译由 Service/前端做，Mapper 不涉（保持纯数据查询）。
 * 审核历史 listReviewLogByFlowId 直接返回 VO（join sys_user 带出 operatorNickname），Service 透传。
 */
@Mapper
public interface AuditFundFlowMapper {

    /** 列表查询（PageHelper 在 Service 层 startPage 拦截）；带 subject_name/handler_nickname */
    List<AuditFundFlow> quarryAuditFundFlow(AuditFundFlowQuarry quarry);

    /** 详情：按主键取未删除流水（带 subject_name/handler_nickname） */
    AuditFundFlow getAuditFundFlowInfo(Long flowId);

    /** 新增流水，回填主键 */
    Boolean insertAuditFundFlow(AuditFundFlow flow);

    /** 编辑流水（动态列，用于审批改 status/reviewStatus 等） */
    Boolean updateAuditFundFlow(AuditFundFlow flow);

    /** 软删流水（deleted=1） */
    Boolean deleteAuditFundFlow(@Param("flowId") Long flowId);

    /**
     * 按流水ID查审核历史（按动作时间升序还原轨迹），直接返回 VO（join sys_user 带出 operatorNickname）。
     * 前台详情审核记录折叠区 / 后台审核记录共用此查询。
     */
    List<AuditFlowReviewLogVo> listReviewLogByFlowId(Long flowId);
}